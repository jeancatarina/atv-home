package dev.jean.atvhome.social.games.twotruths

import dev.jean.atvhome.social.game.*
import dev.jean.atvhome.social.kit.ContentDeck
import dev.jean.atvhome.social.kit.Votes
import kotlinx.serialization.json.jsonPrimitive

enum class TStage { PREP, TELL, VOTE, REVEAL, DONE }

data class TwoTruthsState(
    val players: List<Player>,
    val themes: List<String>,
    val turn: Int = 0,
    val stage: TStage = TStage.PREP,
    /** Which of the teller's three statements (0–2) is the lie; set by the teller during PREP. */
    val lie: Int? = null,
    val votes: Votes<Int> = Votes(emptySet()),
    val scores: Map<PlayerId, Int> = emptyMap(),
    val fooled: Map<PlayerId, Int> = emptyMap(),
    val deadline: Long? = null,
) {
    val teller: Player get() = players[turn]
    val theme: String get() = themes[turn % themes.size]
}

/**
 * Duas Verdades e Uma Mentira: the teller says three statements aloud, one false, and marks which
 * number is the lie on the phone. Everyone else votes. Right guesses score; so does fooling people.
 */
object TwoTruths : Game<TwoTruthsState> {
    private const val PREP_MS = 60_000L
    private const val TELL_MS = 90_000L
    private const val VOTE_MS = 25_000L
    private const val REVEAL_MS = 9_000L

    override val info = GameInfo(
        id = "twotruths", name = "Duas Verdades e Uma Mentira", tagline = "Quem aqui mente melhor?",
        category = Category.TALK, minPlayers = 3, maxPlayers = 10, idealMin = 4, idealMax = 8, minutes = 14, difficulty = 1,
        style = "Um toque por rodada",
        howTo = listOf(
            "Na sua vez, pense em três coisas sobre você: duas de verdade e uma mentira. Marque no celular qual é a mentira",
            "Conta as três em voz alta, numeradas, com cara de paisagem",
            "O resto vota na mentira. Quem acerta pontua. Quem engana também",
        ),
    )

    override fun setup(ctx: Setup) = TwoTruthsState(
        ctx.players.shuffled(ctx.random), ContentDeck.draw(ctx.content.ifEmpty { listOf("Qualquer assunto") }, ctx.players.size, ctx.random),
        deadline = ctx.now + PREP_MS,
    )

    private fun next(s: TwoTruthsState, now: Long): TwoTruthsState =
        if (s.turn + 1 >= s.players.size) s.copy(stage = TStage.DONE, deadline = null)
        else s.copy(turn = s.turn + 1, stage = TStage.PREP, lie = null, votes = Votes(emptySet()), deadline = now + PREP_MS)

    private fun startVote(s: TwoTruthsState, now: Long) =
        s.copy(stage = TStage.VOTE, votes = Votes(s.players.map { it.id }.toSet() - s.teller.id), deadline = now + VOTE_MS)

    private fun reveal(s: TwoTruthsState, now: Long): TwoTruthsState {
        val lie = s.lie ?: return next(s, now)
        var scores = s.scores
        s.votes.cast.forEach { (who, v) -> if (v == lie) scores = scores + (who to (scores[who] ?: 0) + 1) }
        val fooled = s.votes.cast.values.count { it != lie }
        scores = scores + (s.teller.id to (scores[s.teller.id] ?: 0) + fooled)
        return s.copy(stage = TStage.REVEAL, scores = scores, fooled = s.fooled + (s.teller.id to fooled), deadline = now + REVEAL_MS)
    }

    override fun act(s: TwoTruthsState, by: PlayerId, a: Action, now: Long): Step<TwoTruthsState> {
        val n = a.data["key"]?.jsonPrimitive?.content?.toIntOrNull()
        return when (s.stage) {
            TStage.PREP -> {
                if (by != s.teller.id) return Step.Rejected("Agora é a vez de ${s.teller.name}")
                if (a.type == Act.BUTTON && a.data["key"]?.jsonPrimitive?.content == "go" && s.lie != null) return Step.Ok(s.copy(stage = TStage.TELL, deadline = now + TELL_MS))
                if (a.type != Act.CHOOSE || n == null || n !in 0..2) return Step.Rejected("Escolhe 1, 2 ou 3")
                Step.Ok(s.copy(lie = n))
            }
            TStage.TELL -> if (by == s.teller.id && a.type == Act.BUTTON) Step.Ok(startVote(s, now)) else Step.Rejected("Escuta a história")
            TStage.VOTE -> {
                if (by !in s.votes.voters) return Step.Rejected("O narrador não vota")
                if (a.type != Act.CHOOSE || n == null || n !in 0..2) return Step.Rejected("Escolhe 1, 2 ou 3")
                val next = s.copy(votes = s.votes.vote(by, n))
                Step.Ok(if (next.votes.complete) reveal(next, now) else next)
            }
            else -> Step.Rejected("Olha pra TV")
        }
    }

    override fun host(s: TwoTruthsState, cmd: HostCmd, now: Long) = when (s.stage) {
        TStage.PREP -> if (cmd == HostCmd.SKIP || s.lie == null) next(s, now) else s.copy(stage = TStage.TELL, deadline = now + TELL_MS)
        TStage.TELL -> startVote(s, now)
        TStage.VOTE -> reveal(s, now)
        TStage.REVEAL -> next(s, now)
        TStage.DONE -> s
    }

    override fun timeout(s: TwoTruthsState, now: Long) = host(s, HostCmd.CONTINUE, now)
    override fun tvAction(s: TwoTruthsState) = when (s.stage) {
        TStage.PREP -> TvAction(if (s.lie == null) "Pular a vez" else "Ir para a contagem", hold = true)
        TStage.TELL -> TvAction("Abrir a votação")
        TStage.VOTE -> TvAction("Revelar", hold = true)
        TStage.REVEAL -> TvAction("Próximo narrador")
        TStage.DONE -> null
    }

    override fun deadline(s: TwoTruthsState) = s.deadline
    override fun phase(s: TwoTruthsState) = "${s.stage.name.lowercase()}:${s.turn}"
    override fun mood(s: TwoTruthsState) = when (s.stage) { TStage.VOTE -> Mood.FOCUS; TStage.TELL -> Mood.FOCUS; else -> Mood.TALK }

    private val numbers = listOf(Option("0", "1"), Option("1", "2"), Option("2", "3"))

    override fun phone(s: TwoTruthsState, p: PlayerId): PhoneView {
        val ph = phase(s)
        val me = p == s.teller.id
        return when (s.stage) {
            TStage.PREP -> if (me) PhoneView(
                ph, "Sua vez · tema: ${s.theme}", body = Widget.Choose("Qual número vai ser a mentira?", numbers, s.lie?.toString()),
                endsAt = s.deadline, buttons = if (s.lie != null) listOf(Button("go", "Vou contar")) else emptyList(),
            ) else PhoneView(ph, body = Widget.Wait("${s.teller.name} tá pensando…", "Tema: ${s.theme}"), endsAt = s.deadline)
            TStage.TELL -> if (me) PhoneView(ph, "Conte as três", body = Widget.Wait("Conta as três em voz alta", "A mentira é a de número ${(s.lie ?: 0) + 1}"), buttons = listOf(Button("vote", "Terminei: pode votar")), endsAt = s.deadline)
            else PhoneView(ph, body = Widget.Wait("Escuta ${s.teller.name}", "Qual é a mentira?"), endsAt = s.deadline)
            TStage.VOTE -> if (me) PhoneView(ph, body = Widget.Wait("Cara de paisagem…", "Os outros estão votando"), endsAt = s.deadline)
            else PhoneView(ph, "Qual é a mentira?", body = Widget.Choose("Vota no número da mentira", numbers, s.votes.cast[p]?.toString()), endsAt = s.deadline)
            TStage.REVEAL -> PhoneView(ph, body = Widget.Wait("Olha pra TV", react = true))
            TStage.DONE -> PhoneView(ph, body = Widget.Wait("Acabou a partida", "Olha pra TV"))
        }
    }

    override fun left(s: TwoTruthsState, p: PlayerId, now: Long): TwoTruthsState {
        val idx = s.players.indexOfFirst { it.id == p }
        val players = s.players.filterNot { it.id == p }
        if (players.size < 3 || idx < 0) return s.copy(players = players, stage = TStage.DONE, deadline = null)
        val rest = s.copy(players = players, votes = s.votes.without(p), turn = if (idx < s.turn) s.turn - 1 else s.turn)
        return when {
            idx == s.turn && rest.stage != TStage.REVEAL -> if (rest.turn >= players.size) rest.copy(stage = TStage.DONE, deadline = null) else rest.copy(stage = TStage.PREP, lie = null, votes = Votes(emptySet()), deadline = now + PREP_MS)
            rest.stage == TStage.VOTE && rest.votes.complete -> reveal(rest, now)
            else -> rest
        }
    }

    override fun over(s: TwoTruthsState): Outcome? {
        if (s.stage != TStage.DONE) return null
        val best = s.fooled.maxByOrNull { it.value }
        return Outcome(
            s.scores, best?.takeIf { it.value > 0 }?.let { (id, n) -> "${s.players.firstOrNull { p -> p.id == id }?.name ?: "Alguém"} enganou $n de uma vez" } ?: "Ninguém conseguiu enganar ninguém",
            listOfNotNull(best?.takeIf { it.value > 0 }?.let { Highlight("FOOLED", it.key, it.value) }),
        )
    }

    override fun secrets(s: TwoTruthsState, p: PlayerId): Set<String> =
        if (p != s.teller.id && s.lie != null && s.stage in listOf(TStage.PREP, TStage.TELL, TStage.VOTE)) setOf("A mentira é a de número ${s.lie + 1}") else emptySet()
}
