package dev.jean.atvhome.social.games.insider

import dev.jean.atvhome.social.game.*
import dev.jean.atvhome.social.kit.Votes
import kotlinx.serialization.json.jsonPrimitive

enum class IStage { ROLES, PLAY, VOTE, DONE }

data class InsiderState(
    val players: List<Player>,
    val word: String,
    val master: PlayerId,
    val insider: PlayerId,
    val stage: IStage = IStage.ROLES,
    val ready: Set<PlayerId> = emptySet(),
    /** What the Master answered, oldest first: yes / no / unsure. Public. */
    val history: List<String> = emptyList(),
    val found: Boolean = false,
    val votes: Votes<PlayerId> = Votes(emptySet()),
    val deadline: Long? = null,
    val playMs: Long = 300_000,
    val caught: Boolean? = null,
) {
    fun name(id: PlayerId) = players.firstOrNull { it.id == id }?.name ?: "?"
}

/**
 * Quem Sabia?: the Master knows a word and answers yes/no/don't know. One of the others, the
 * Insider, also knows it and steers the group without being noticed. Find the word, then vote.
 */
object Insider : Game<InsiderState> {
    private const val ROLES_MS = 45_000L
    private const val VOTE_MS = 40_000L
    private val TIMES = listOf(180_000L, 300_000L, 420_000L)

    override val info = GameInfo(
        id = "insider", name = "Quem Sabia?", tagline = "Alguém aqui já sabia a resposta",
        category = Category.DEDUCTION, minPlayers = 4, maxPlayers = 8, idealMin = 5, idealMax = 7, minutes = 9, difficulty = 1,
        style = "O Mestre responde, o resto pergunta e vota",
        howTo = listOf(
            "O Mestre vê uma palavra. Um dos outros também vê, escondido: esse é o Infiltrado",
            "A turma faz perguntas de sim ou não ao Mestre até acertar a palavra",
            "Acertaram? Todo mundo vota em quem tava soprando as respostas. O Infiltrado ganha se não for pego",
        ),
        settings = listOf(Setting("time", "Tempo", listOf("3 min", "5 min", "7 min"), default = 1)),
    )

    override fun setup(ctx: Setup): InsiderState {
        val order = ctx.players.shuffled(ctx.random)
        return InsiderState(
            ctx.players, ctx.content.ifEmpty { listOf("Girafa") }.random(ctx.random), master = order[0].id, insider = order[1].id,
            deadline = ctx.now + ROLES_MS, playMs = TIMES[(ctx.settings["time"] ?: 1).coerceIn(0, TIMES.lastIndex)],
        )
    }

    private fun play(s: InsiderState, now: Long) = s.copy(stage = IStage.PLAY, deadline = now + s.playMs)
    private fun vote(s: InsiderState, now: Long) =
        s.copy(stage = IStage.VOTE, found = true, votes = Votes(s.players.map { it.id }.toSet() - s.master), deadline = now + VOTE_MS)
    private fun done(s: InsiderState, caught: Boolean?) = s.copy(stage = IStage.DONE, caught = caught, deadline = null)
    private fun tally(s: InsiderState): InsiderState = s.votes.leaders().let { done(s, it.size == 1 && it.single() == s.insider) }

    override fun act(s: InsiderState, by: PlayerId, a: Action, now: Long): Step<InsiderState> {
        val key = a.data["key"]?.jsonPrimitive?.content
        return when (s.stage) {
            IStage.ROLES -> {
                if (a.type != Act.BUTTON || key != "ready") return Step.Rejected("Toca em “Já vi”")
                val n = s.copy(ready = s.ready + by)
                Step.Ok(if (n.ready.containsAll(s.players.map { it.id })) play(n, now) else n)
            }
            IStage.PLAY -> {
                if (by != s.master || a.type != Act.BUTTON) return Step.Rejected("Só o Mestre responde")
                when (key) {
                    "yes", "no", "unsure" -> Step.Ok(s.copy(history = (s.history + key).takeLast(40)))
                    "found" -> Step.Ok(vote(s, now))
                    else -> Step.Rejected("Hmm, não entendi essa")
                }
            }
            IStage.VOTE -> {
                val t = a.data["id"]?.jsonPrimitive?.content
                if (a.type != Act.PICK || by !in s.votes.voters || t == null || t == by || t == s.master || s.players.none { it.id == t }) return Step.Rejected("Escolhe outra pessoa")
                val n = s.copy(votes = s.votes.vote(by, t))
                Step.Ok(if (n.votes.complete) tally(n) else n)
            }
            IStage.DONE -> Step.Rejected("Olha pra TV")
        }
    }

    override fun host(s: InsiderState, cmd: HostCmd, now: Long) = when (s.stage) {
        IStage.ROLES -> play(s, now)
        IStage.PLAY -> if (cmd == HostCmd.SKIP) done(s, null) else vote(s, now)
        IStage.VOTE -> tally(s)
        IStage.DONE -> s
    }

    override fun timeout(s: InsiderState, now: Long) = when (s.stage) {
        IStage.PLAY -> done(s, null)
        else -> host(s, HostCmd.CONTINUE, now)
    }

    override fun tvAction(s: InsiderState) = when (s.stage) {
        IStage.ROLES -> TvAction("Começar", hold = true)
        IStage.PLAY -> TvAction("Ir para a votação", hold = true)
        IStage.VOTE -> TvAction("Encerrar a votação", hold = true)
        IStage.DONE -> null
    }

    override fun deadline(s: InsiderState) = s.deadline
    override fun phase(s: InsiderState) = s.stage.name.lowercase()
    override fun mood(s: InsiderState) = if (s.stage == IStage.VOTE) Mood.FOCUS else Mood.TALK

    private fun badge(s: InsiderState, p: PlayerId) = when (p) {
        s.master -> Badge("Você é o Mestre", s.word)
        s.insider -> Badge("Você é o Infiltrado", s.word)
        else -> Badge("Você é comum", "sem palavra")
    }

    override fun phone(s: InsiderState, p: PlayerId): PhoneView {
        val ph = phase(s)
        val b = if (s.stage == IStage.DONE) null else badge(s, p)
        return when (s.stage) {
            IStage.ROLES -> PhoneView(ph, "Seu papel", b, Widget.Secret(b!!.label, b.value), s.deadline, if (p in s.ready) emptyList() else listOf(Button("ready", "Já vi")))
            IStage.PLAY -> if (p == s.master) PhoneView(
                ph, "Você é o Mestre", b, Widget.Wait("Responde às perguntas em voz alta", "Palavra: segura o selo aqui embaixo"), s.deadline,
                listOf(Button("yes", "Sim"), Button("no", "Não"), Button("unsure", "Não sei"), Button("found", "Acertaram!")),
            ) else PhoneView(ph, "Façam perguntas ao Mestre", b, Widget.Wait("Descubram a palavra", "Perguntas de sim ou não", react = true), s.deadline)
            IStage.VOTE -> if (p in s.votes.voters) PhoneView(ph, "Quem tava guiando vocês?", b, Widget.PickPlayer("Quem é o Infiltrado?", s.players.map { it.id }.filter { it != p && it != s.master }, s.votes.cast[p]), s.deadline)
            else PhoneView(ph, body = Widget.Wait("Olha pra TV", "Os outros estão votando"), endsAt = s.deadline)
            IStage.DONE -> PhoneView(ph, body = Widget.Wait("Acabou a partida", "Olha pra TV"))
        }
    }

    override fun left(s: InsiderState, p: PlayerId, now: Long): InsiderState {
        val rest = s.copy(players = s.players.filterNot { it.id == p }, ready = s.ready - p, votes = s.votes.without(p))
        return when {
            s.stage == IStage.DONE -> rest
            p == s.master || p == s.insider || rest.players.size < 4 -> done(rest, null)
            rest.stage == IStage.ROLES && rest.ready.containsAll(rest.players.map { it.id }) -> play(rest, now)
            rest.stage == IStage.VOTE && rest.votes.complete -> tally(rest)
            else -> rest
        }
    }

    override fun over(s: InsiderState): Outcome? {
        if (s.stage != IStage.DONE) return null
        return when {
            !s.found -> Outcome(emptyMap(), "Ninguém achou a palavra: era “${s.word}”")
            s.caught == true -> Outcome(s.players.filter { it.id != s.insider }.associate { it.id to 3 }, "Descobriram ${s.name(s.insider)}: era o Infiltrado", listOf(Highlight("INSIDER_CAUGHT", s.insider)))
            else -> Outcome(mapOf(s.insider to 4), "${s.name(s.insider)} guiou a turma toda e ninguém percebeu", listOf(Highlight("INSIDER_GOLD", s.insider)))
        }
    }

    override fun secrets(s: InsiderState, p: PlayerId): Set<String> =
        if (s.stage == IStage.DONE || p == s.master || p == s.insider) emptySet() else setOf(s.word)
}
