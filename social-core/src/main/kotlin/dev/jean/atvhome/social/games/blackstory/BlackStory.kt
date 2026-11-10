package dev.jean.atvhome.social.games.blackstory

import dev.jean.atvhome.social.game.*
import kotlinx.serialization.json.jsonPrimitive

enum class BStage { PLAY, REVEAL, DONE }

data class Mystery(val riddle: String, val solution: String, val hints: List<String>) {
    companion object {
        fun parse(line: String) = line.split("|").takeIf { it.size >= 2 }?.let { Mystery(it[0], it[1], it.drop(2)) }
    }
}

data class BlackStoryState(
    val players: List<Player>,
    val mystery: Mystery,
    val guardian: PlayerId,
    val stage: BStage = BStage.PLAY,
    /** Answers given so far, oldest first: yes / no / irrelevant / almost. */
    val history: List<String> = emptyList(),
    val hintsShown: Int = 0,
    val solved: Boolean = false,
    val deadline: Long? = null,
)

/**
 * Mistério Sim ou Não: the TV shows a strange riddle; the Guardian has the full story on the phone
 * and answers the group's yes/no questions. A cooperative game: everybody wins when it is solved.
 */
object BlackStory : Game<BlackStoryState> {
    private const val PLAY_MS = 900_000L
    private const val REVEAL_MS = 30_000L

    override val info = GameInfo(
        id = "blackstory", name = "Mistério Sim ou Não", tagline = "Uma situação esquisita. Descubram o que rolou",
        category = Category.COOP, minPlayers = 2, maxPlayers = 6, idealMin = 2, idealMax = 5, minutes = 15, difficulty = 1,
        style = "Um responde, o resto pergunta",
        howTo = listOf(
            "A TV mostra a situação esquisita. Só o Guardião vê a solução, no celular dele",
            "O resto faz perguntas em voz alta e o Guardião responde: sim, não, tanto faz ou quase",
            "Travou? O Guardião solta uma dica (até 3). Quando descobrirem, ele toca em “Resolveram!”",
        ),
        coop = true,
    )

    override fun setup(ctx: Setup): BlackStoryState {
        val m = ctx.content.mapNotNull(Mystery::parse).ifEmpty { listOf(Mystery("Um homem entra num bar e pede água.", "Ele estava com soluço.", listOf("É sobre saúde."))) }.random(ctx.random)
        return BlackStoryState(ctx.players, m, ctx.players.random(ctx.random).id, deadline = ctx.now + PLAY_MS)
    }

    private fun reveal(s: BlackStoryState, solved: Boolean, now: Long) = s.copy(stage = BStage.REVEAL, solved = solved, deadline = now + REVEAL_MS)

    override fun act(s: BlackStoryState, by: PlayerId, a: Action, now: Long): Step<BlackStoryState> {
        if (s.stage != BStage.PLAY || by != s.guardian || a.type != Act.BUTTON) return Step.Rejected("Só o Guardião responde")
        return when (a.data["key"]?.jsonPrimitive?.content) {
            "yes", "no", "irrelevant", "almost" -> Step.Ok(s.copy(history = (s.history + a.data["key"]!!.jsonPrimitive.content).takeLast(60)))
            "hint" -> if (s.hintsShown >= s.mystery.hints.size) Step.Rejected("Acabaram as dicas") else Step.Ok(s.copy(hintsShown = s.hintsShown + 1))
            "solved" -> Step.Ok(reveal(s, true, now))
            else -> Step.Rejected("Hmm, não entendi essa")
        }
    }

    override fun host(s: BlackStoryState, cmd: HostCmd, now: Long) = when (s.stage) {
        BStage.PLAY -> if (cmd == HostCmd.SKIP) reveal(s, false, now) else if (s.hintsShown < s.mystery.hints.size) s.copy(hintsShown = s.hintsShown + 1) else reveal(s, false, now)
        BStage.REVEAL -> s.copy(stage = BStage.DONE, deadline = null)
        BStage.DONE -> s
    }

    override fun timeout(s: BlackStoryState, now: Long) = when (s.stage) {
        BStage.PLAY -> reveal(s, false, now)
        else -> host(s, HostCmd.CONTINUE, now)
    }

    override fun tvAction(s: BlackStoryState) = when (s.stage) {
        BStage.PLAY -> TvAction(if (s.hintsShown < s.mystery.hints.size) "Liberar uma dica" else "Revelar a solução", hold = s.hintsShown >= s.mystery.hints.size)
        BStage.REVEAL -> TvAction("Concluir")
        BStage.DONE -> null
    }

    override fun deadline(s: BlackStoryState) = s.deadline
    override fun phase(s: BlackStoryState) = s.stage.name.lowercase()
    override fun mood(s: BlackStoryState) = Mood.TALK

    override fun phone(s: BlackStoryState, p: PlayerId): PhoneView {
        val ph = phase(s)
        return when (s.stage) {
            BStage.PLAY -> if (p == s.guardian) PhoneView(
                ph, "Você é o Guardião", Badge("A solução", s.mystery.solution), Widget.Secret("A solução", s.mystery.solution), s.deadline,
                listOf(Button("yes", "Sim"), Button("no", "Não"), Button("irrelevant", "Irrelevante"), Button("almost", "Quase"), Button("hint", "Liberar dica"), Button("solved", "Resolveram!")),
            ) else PhoneView(ph, "Só perguntas de sim ou não", body = Widget.Wait("Descubram o que rolou", "Perguntem ao Guardião em voz alta", react = true), endsAt = s.deadline)
            BStage.REVEAL -> PhoneView(ph, body = Widget.Wait("Olha pra TV", react = true))
            BStage.DONE -> PhoneView(ph, body = Widget.Wait("Acabou a partida", "Olha pra TV"))
        }
    }

    override fun left(s: BlackStoryState, p: PlayerId, now: Long): BlackStoryState {
        val rest = s.copy(players = s.players.filterNot { it.id == p })
        return if (s.stage == BStage.DONE) rest
        else if (p == s.guardian || rest.players.size < 2) rest.copy(stage = BStage.DONE, deadline = null) else rest
    }

    override fun over(s: BlackStoryState): Outcome? {
        if (s.stage != BStage.DONE) return null
        val askers = s.players.filter { it.id != s.guardian }
        return if (s.solved) Outcome(
            askers.associate { it.id to 3 + if (s.hintsShown == 0) 1 else 0 } + (s.guardian to 2),
            "Resolvido com ${s.hintsShown} dica${if (s.hintsShown == 1) "" else "s"} e ${s.history.size} respostas", listOf(Highlight("SOLVED", null, s.hintsShown)), coopWon = true,
        ) else Outcome(mapOf(s.guardian to 3), "Ninguém matou a charada. A resposta tava com o Guardião", coopWon = false)
    }

    override fun secrets(s: BlackStoryState, p: PlayerId): Set<String> =
        if (p == s.guardian || s.stage != BStage.PLAY) emptySet() else setOf(s.mystery.solution) + s.mystery.hints.drop(s.hintsShown)
}
