package dev.jean.atvhome.social.games.mostlikely

import dev.jean.atvhome.social.game.Act
import dev.jean.atvhome.social.game.Action
import dev.jean.atvhome.social.game.Category
import dev.jean.atvhome.social.game.Game
import dev.jean.atvhome.social.game.GameInfo
import dev.jean.atvhome.social.game.Highlight
import dev.jean.atvhome.social.game.HostCmd
import dev.jean.atvhome.social.game.Mood
import dev.jean.atvhome.social.game.Outcome
import dev.jean.atvhome.social.game.PhoneView
import dev.jean.atvhome.social.game.Player
import dev.jean.atvhome.social.game.PlayerId
import dev.jean.atvhome.social.game.Setting
import dev.jean.atvhome.social.game.Setup
import dev.jean.atvhome.social.game.Step
import dev.jean.atvhome.social.game.TvAction
import dev.jean.atvhome.social.game.Widget
import dev.jean.atvhome.social.kit.ContentDeck
import dev.jean.atvhome.social.kit.Scores
import dev.jean.atvhome.social.kit.Votes
import kotlinx.serialization.json.jsonPrimitive

enum class Stage { VOTE, REVEAL, DONE }

/** The result of one question, shown on the TV. */
data class Reveal(val tally: Map<PlayerId, Int>, val leaders: List<PlayerId>, val unanimous: PlayerId?)

data class MostLikelyState(
    val players: List<Player>,
    val questions: List<String>,
    val round: Int,
    val stage: Stage,
    val votes: Votes<PlayerId>,
    val deadline: Long?,
    val scores: Map<PlayerId, Int> = emptyMap(),
    /** Votes each person received over the whole game. */
    val received: Map<PlayerId, Int> = emptyMap(),
    val reveal: Reveal? = null,
    val unanimous: List<Highlight> = emptyList(),
) {
    val question: String get() = questions.getOrElse(round) { "" }
    /** The last two questions count double, so the lead can still change. */
    val double: Boolean get() = round >= questions.size - 2 && questions.size > 2
}

/**
 * Quem é Mais Provável?: a question about the room, everybody votes for someone (voting for
 * yourself is allowed), the TV reveals who. Voting with the majority scores, and the last two
 * questions count double.
 */
object MostLikely : Game<MostLikelyState> {

    private const val VOTE_MS = 20_000L
    private const val REVEAL_MS = 9_000L
    private val ROUNDS = listOf(5, 10, 15)

    override val info = GameInfo(
        id = "mostlikely",
        name = "Quem é Mais Provável?",
        tagline = "A TV pergunta, todo mundo aponta o dedo",
        category = Category.TALK,
        minPlayers = 3, maxPlayers = 12, idealMin = 4, idealMax = 10,
        minutes = 9, difficulty = 1,
        style = "Um toque por pergunta",
        howTo = listOf(
            "A TV solta uma pergunta sobre a turma",
            "Cada um vota no celular em quem acha mais provável. Pode votar em si mesmo, ninguém julga",
            "A TV mostra quem levou mais votos. As duas últimas valem em dobro",
        ),
        settings = listOf(Setting("rounds", "Perguntas", ROUNDS.map { "$it" }, default = 1)),
    )

    override fun setup(ctx: Setup): MostLikelyState {
        val n = ROUNDS[(ctx.settings["rounds"] ?: info.settings[0].default).coerceIn(0, ROUNDS.lastIndex)]
        val pack = ctx.content.ifEmpty { listOf("Quem é mais provável de chegar atrasado?") }
        return vote(ctx.players, ContentDeck.draw(pack, n, ctx.random), 0, ctx.now, emptyMap(), emptyMap(), emptyList())
    }

    private fun vote(
        players: List<Player>, questions: List<String>, round: Int, now: Long,
        scores: Map<PlayerId, Int>, received: Map<PlayerId, Int>, unanimous: List<Highlight>,
    ) = MostLikelyState(
        players, questions, round, Stage.VOTE, Votes(players.map { it.id }.toSet()), now + VOTE_MS,
        scores, received, null, unanimous,
    )

    override fun act(s: MostLikelyState, by: PlayerId, a: Action, now: Long): Step<MostLikelyState> {
        if (s.stage != Stage.VOTE) return Step.Rejected("Essa votação já fechou")
        if (by !in s.votes.voters) return Step.Rejected("Você tá de fora dessa partida")
        if (a.type != Act.PICK) return Step.Rejected("Hmm, não entendi essa")
        val target = a.data["id"]?.jsonPrimitive?.content
        if (target == null || s.players.none { it.id == target }) return Step.Rejected("Escolhe alguém da sala")
        val next = s.copy(votes = s.votes.vote(by, target))
        return Step.Ok(if (next.votes.complete) reveal(next, now) else next)
    }

    private fun reveal(s: MostLikelyState, now: Long): MostLikelyState {
        val leaders = s.votes.leaders()
        val points = if (s.double) 2 else 1
        var scores = s.scores
        s.votes.cast.forEach { (voter, choice) -> if (choice in leaders) scores = Scores.add(scores, voter, points) }
        val received = s.votes.tally().entries.fold(s.received) { acc, (who, n) -> acc + (who to (acc[who] ?: 0) + n) }
        val unanimous = s.votes.unanimous()
        return s.copy(
            stage = Stage.REVEAL, deadline = now + REVEAL_MS, scores = scores, received = received,
            reveal = Reveal(s.votes.tally(), leaders, unanimous),
            unanimous = if (unanimous != null) s.unanimous + Highlight("UNANIMOUS", unanimous, s.round) else s.unanimous,
        )
    }

    private fun next(s: MostLikelyState, now: Long): MostLikelyState =
        if (s.round + 1 >= s.questions.size || s.players.size < 2) s.copy(stage = Stage.DONE, deadline = null)
        else vote(s.players, s.questions, s.round + 1, now, s.scores, s.received, s.unanimous)

    override fun host(s: MostLikelyState, cmd: HostCmd, now: Long): MostLikelyState = when (s.stage) {
        Stage.VOTE -> if (cmd == HostCmd.CONTINUE) reveal(s, now) else next(s, now)
        Stage.REVEAL -> next(s, now)
        Stage.DONE -> s
    }

    override fun timeout(s: MostLikelyState, now: Long): MostLikelyState = when (s.stage) {
        Stage.VOTE -> reveal(s, now)
        Stage.REVEAL -> next(s, now)
        Stage.DONE -> s
    }

    override fun tvAction(s: MostLikelyState) = when (s.stage) {
        Stage.VOTE -> TvAction("Revelar agora", hold = true)
        Stage.REVEAL -> TvAction("Próxima pergunta")
        Stage.DONE -> null
    }

    override fun deadline(s: MostLikelyState) = s.deadline

    override fun phase(s: MostLikelyState) = "${s.stage.name.lowercase()}:${s.round}"

    override fun mood(s: MostLikelyState) = if (s.stage == Stage.VOTE) Mood.FOCUS else Mood.TALK

    override fun phone(s: MostLikelyState, p: PlayerId): PhoneView = when (s.stage) {
        Stage.VOTE -> PhoneView(
            phase = phase(s),
            title = "Pergunta ${s.round + 1} de ${s.questions.size}" + if (s.double) " · vale dobro" else "",
            body = Widget.PickPlayer(s.question, s.players.map { it.id }, chosen = s.votes.cast[p]),
            endsAt = s.deadline,
        )
        Stage.REVEAL -> PhoneView(phase(s), body = Widget.Wait("Olha pra TV", react = true))
        Stage.DONE -> PhoneView(phase(s), body = Widget.Wait("Acabou a partida", "Olha pra TV"))
    }

    override fun left(s: MostLikelyState, p: PlayerId, now: Long): MostLikelyState {
        val rest = s.copy(players = s.players.filterNot { it.id == p }, votes = s.votes.without(p))
        return when {
            rest.players.size < 2 -> rest.copy(stage = Stage.DONE, deadline = null)
            rest.stage == Stage.VOTE && rest.votes.complete -> reveal(rest, now)
            else -> rest
        }
    }

    override fun over(s: MostLikelyState): Outcome? {
        if (s.stage != Stage.DONE) return null
        val topTarget = Scores.leaders(s.received).takeIf { it.size == 1 }?.single()
        val chips = s.scores.toMutableMap().apply { topTarget?.let { merge(it, 3, Int::plus) } }
        val highlights = s.unanimous + listOfNotNull(topTarget?.let { Highlight("MOST_VOTED", it, s.received[it] ?: 0) })
        val name = topTarget?.let { id -> s.players.firstOrNull { it.id == id }?.name }
        return Outcome(chips, name?.let { "$it levou mais votos na noite" } ?: "A turma ficou dividida", highlights)
    }
}
