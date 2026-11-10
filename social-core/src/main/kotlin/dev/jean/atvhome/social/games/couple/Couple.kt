package dev.jean.atvhome.social.games.couple

import dev.jean.atvhome.social.game.*
import dev.jean.atvhome.social.kit.ContentDeck
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

enum class CStage { ANSWER, REVEAL, DONE }

/** A question: "C|texto com {nome}|op1|op2|op3" (choices) or "S|texto|polo baixo|polo alto" (0–10 scale). */
data class Question(val scale: Boolean, val text: String, val options: List<String>) {
    companion object {
        fun parse(line: String): Question? = line.split("|").let {
            if (it.size < 4 || it[0] !in listOf("C", "S")) null else Question(it[0] == "S", it[1], it.drop(2))
        }
    }
}

data class CoupleState(
    val players: List<Player>,
    val questions: List<Question>,
    val round: Int = 0,
    val stage: CStage = CStage.ANSWER,
    /** Answers by player: a choice index or a scale value. */
    val answers: Map<PlayerId, Int> = emptyMap(),
    val points: Int = 0,
    val lastPoints: Int = 0,
    val deadline: Long? = null,
) {
    val subject: Player get() = players[round % players.size]
    val guesser: Player get() = players[(round + 1) % players.size]
    val q: Question get() = questions[round]
    val text: String get() = q.text.replace("{nome}", subject.name)
    val maxPoints: Int get() = questions.indices.sumOf { 3 * if (it >= questions.size - 1) 2 else 1 }
}

/**
 * Sintonia de Casal: a question about one of the two, answered by both at once: the subject says
 * what is true, the other predicts it. Matching scores; the last question counts double.
 */
object Couple : Game<CoupleState> {
    private const val ANSWER_MS = 45_000L
    private const val REVEAL_MS = 12_000L
    private val ROUNDS = listOf(8, 12, 16)

    override val info = GameInfo(
        id = "couple", name = "Sintonia de Casal", tagline = "Quanto vocês dois se conhecem?",
        category = Category.DUO, minPlayers = 2, maxPlayers = 2, idealMin = 2, idealMax = 2, minutes = 13, difficulty = 1,
        style = "Um toque por pergunta",
        howTo = listOf(
            "A TV pergunta uma coisa sobre um de vocês",
            "Os dois respondem juntos: um fala a verdade, o outro tenta adivinhar",
            "Bateu? Ponto. A última vale em dobro",
        ),
        coop = true,
        settings = listOf(Setting("rounds", "Perguntas", ROUNDS.map { "$it" }, default = 1)),
    )

    override fun setup(ctx: Setup): CoupleState {
        val n = ROUNDS[(ctx.settings["rounds"] ?: 1).coerceIn(0, ROUNDS.lastIndex)]
        val qs = ContentDeck.draw(ctx.content, n, ctx.random).mapNotNull(Question::parse)
            .ifEmpty { listOf(Question(false, "Qual é o melhor programa de {nome}?", listOf("Casa", "Rua"))) }
        return CoupleState(ctx.players, qs, deadline = ctx.now + ANSWER_MS)
    }

    private fun score(s: CoupleState): Int {
        val a = s.answers[s.subject.id] ?: return 0
        val b = s.answers[s.guesser.id] ?: return 0
        val base = if (s.q.scale) maxOf(0, 3 - abs(a - b)) else if (a == b) 3 else 0
        return base * if (s.round == s.questions.lastIndex) 2 else 1
    }

    private fun reveal(s: CoupleState, now: Long): CoupleState {
        val got = score(s)
        return s.copy(stage = CStage.REVEAL, points = s.points + got, lastPoints = got, deadline = now + REVEAL_MS)
    }

    private fun next(s: CoupleState, now: Long) =
        if (s.round + 1 >= s.questions.size) s.copy(stage = CStage.DONE, deadline = null)
        else s.copy(stage = CStage.ANSWER, round = s.round + 1, answers = emptyMap(), deadline = now + ANSWER_MS, lastPoints = 0)

    override fun act(s: CoupleState, by: PlayerId, a: Action, now: Long): Step<CoupleState> {
        if (s.stage != CStage.ANSWER) return Step.Rejected("Essa pergunta já passou")
        val v = (if (s.q.scale) a.takeIf { it.type == Act.SCALE }?.data?.get("value") else a.takeIf { it.type == Act.CHOOSE }?.data?.get("key"))
            ?.jsonPrimitive?.content?.toIntOrNull()
        if (v == null || v !in 0..(if (s.q.scale) 10 else s.q.options.lastIndex)) return Step.Rejected("Essa resposta não vale")
        val next = s.copy(answers = s.answers + (by to v))
        return Step.Ok(if (next.answers.size >= s.players.size) reveal(next, now) else next)
    }

    override fun host(s: CoupleState, cmd: HostCmd, now: Long) = when (s.stage) {
        CStage.ANSWER -> if (cmd == HostCmd.SKIP) next(s, now) else reveal(s, now)
        CStage.REVEAL -> next(s, now)
        CStage.DONE -> s
    }

    override fun timeout(s: CoupleState, now: Long) = host(s, HostCmd.CONTINUE, now)
    override fun tvAction(s: CoupleState) = when (s.stage) {
        CStage.ANSWER -> TvAction("Revelar agora", hold = true)
        CStage.REVEAL -> TvAction("Próxima pergunta")
        CStage.DONE -> null
    }

    override fun deadline(s: CoupleState) = s.deadline
    override fun phase(s: CoupleState) = "${s.stage.name.lowercase()}:${s.round}"
    override fun mood(s: CoupleState) = if (s.stage == CStage.ANSWER) Mood.FOCUS else Mood.TALK

    override fun phone(s: CoupleState, p: PlayerId): PhoneView {
        if (s.stage == CStage.DONE) return PhoneView(phase(s), body = Widget.Wait("Acabou a partida", "Olha pra TV"))
        if (s.stage == CStage.REVEAL) return PhoneView(phase(s), body = Widget.Wait("Olha pra TV", react = true))
        val mine = s.answers[p]
        val about = if (p == s.subject.id) "Responde sobre você" else "Como ${s.subject.name} responderia?"
        val body = if (s.q.scale) Widget.Scale(s.text, s.q.options[0], s.q.options[1], mine)
        else Widget.Choose(s.text, s.q.options.mapIndexed { i, o -> Option("$i", o) }, mine?.toString())
        return PhoneView(phase(s), "Pergunta ${s.round + 1} de ${s.questions.size} · $about", body = body, endsAt = s.deadline)
    }

    override fun left(s: CoupleState, p: PlayerId, now: Long) = s.copy(players = s.players.filterNot { it.id == p }, stage = CStage.DONE, deadline = null)

    override fun over(s: CoupleState): Outcome? {
        if (s.stage != CStage.DONE) return null
        val pct = s.points * 100 / maxOf(1, s.maxPoints)
        val title = when { pct >= 85 -> "Almas gêmeas"; pct >= 65 -> "Afinadíssimos"; pct >= 45 -> "Já se conhecem bem"; pct >= 25 -> "Ainda tem muita coisa pra descobrir"; else -> "Parecem dois estranhos" }
        return Outcome(s.players.associate { it.id to pct / 10 }, "Sintonia $pct% · $title", listOf(Highlight("SYNC", null, pct)), coopWon = pct >= 50)
    }
}
