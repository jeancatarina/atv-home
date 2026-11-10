package dev.jean.atvhome.social.games.mafia

import dev.jean.atvhome.social.game.*
import dev.jean.atvhome.social.kit.Votes
import kotlinx.serialization.json.jsonPrimitive

enum class MRole(val label: String, val how: String) {
    MAFIOSO("Mafioso", "Você sabe quem são os outros mafiosos. Tenta não ser pego na votação."),
    INFORMANTE("Informante", "Você é da cidade e, de noite, descobre quem são os mafiosos."),
    DETETIVE("Detetive", "De noite, espia a carta de alguém ou do centro."),
    BATEDOR("Batedor", "De noite, troca sua carta com a de alguém e vê qual ficou com você."),
    FOFOQUEIRO("Fofoqueiro", "De noite, troca as cartas de duas pessoas sem olhar."),
    SONAMBULO("Sonâmbulo", "De noite, troca sua carta por uma do centro, sem olhar."),
    CIDADAO("Cidadão", "Você não tem poder nenhum. Observa e vota bem."),
}

enum class MStage { ROLES, NIGHT, DAY, VOTE, DONE }

data class MafiaState(
    val players: List<Player>,
    val original: Map<PlayerId, MRole>,
    val current: Map<PlayerId, MRole>,
    val center: List<MRole>,
    val stage: MStage = MStage.ROLES,
    val step: Int = 0,
    val ready: Set<PlayerId> = emptySet(),
    val picks: Map<PlayerId, List<String>> = emptyMap(),
    val notes: Map<PlayerId, String> = emptyMap(),
    val votes: Votes<PlayerId> = Votes(emptySet()),
    val deadline: Long? = null,
    val out: List<PlayerId> = emptyList(),
    val townWon: Boolean? = null,
    val dayMs: Long = 240_000,
) {
    fun name(id: PlayerId) = players.firstOrNull { it.id == id }?.name ?: "?"
    val stepRole: MRole? get() = NIGHT_ORDER.getOrNull(step)
}

val NIGHT_ORDER = listOf(MRole.MAFIOSO, MRole.INFORMANTE, MRole.DETETIVE, MRole.BATEDOR, MRole.FOFOQUEIRO, MRole.SONAMBULO)

/**
 * Máfia de Uma Noite: everyone gets a role card and three more lie in the middle. One short night,
 * with the powers acting in a fixed order and every phone showing a screen at every step (so nobody
 * can tell who has a power by who is busy). Then the room talks and votes: a mafioso out means the town wins.
 */
object Mafia : Game<MafiaState> {
    private const val ROLES_MS = 60_000L
    private const val STEP_MS = 10_000L
    private const val VOTE_MS = 40_000L
    private val TIMES = listOf(180_000L, 240_000L, 360_000L)
    private val CLASSIC = listOf(MRole.MAFIOSO, MRole.INFORMANTE, MRole.DETETIVE, MRole.BATEDOR, MRole.FOFOQUEIRO, MRole.SONAMBULO, MRole.MAFIOSO,
        MRole.CIDADAO, MRole.CIDADAO, MRole.CIDADAO, MRole.CIDADAO, MRole.CIDADAO, MRole.CIDADAO)
    private val BEGINNER = listOf(MRole.MAFIOSO, MRole.DETETIVE, MRole.MAFIOSO, MRole.CIDADAO, MRole.CIDADAO, MRole.CIDADAO, MRole.CIDADAO,
        MRole.CIDADAO, MRole.CIDADAO, MRole.CIDADAO, MRole.CIDADAO, MRole.CIDADAO, MRole.CIDADAO)

    override val info = GameInfo(
        id = "mafia", name = "Máfia de Uma Noite", tagline = "Uma noite, trocas de carta e uma votação tensa",
        category = Category.DEDUCTION, minPlayers = 5, maxPlayers = 10, idealMin = 6, idealMax = 9, minutes = 10, difficulty = 3,
        style = "Toca no celular em toda etapa da noite, mesmo sem poder",
        howTo = listOf(
            "Cada um recebe um papel secreto. De noite os poderes agem em ordem, e TODO MUNDO toca na tela em cada etapa, tendo poder ou não, pra ninguém se entregar",
            "De dia, conversem: quem fez o quê? Os papéis podem ter sido trocados",
            "Votem. Se um mafioso for eliminado, a cidade vence. Senão, a máfia vence",
        ),
        settings = listOf(
            Setting("mode", "Modo", listOf("Clássico", "Iniciante"), default = 0),
            Setting("day", "Debate", listOf("3 min", "4 min", "6 min"), default = 1),
        ),
    )

    override fun setup(ctx: Setup): MafiaState {
        val base = if ((ctx.settings["mode"] ?: 0) == 1) BEGINNER else CLASSIC
        val deck = base.take(ctx.players.size + 3).shuffled(ctx.random)
        val orig = ctx.players.mapIndexed { i, p -> p.id to deck[i] }.toMap()
        return MafiaState(ctx.players, orig, orig, deck.drop(ctx.players.size), deadline = ctx.now + ROLES_MS,
            dayMs = TIMES[(ctx.settings["day"] ?: 1).coerceIn(0, TIMES.lastIndex)])
    }

    // --- Night ----------------------------------------------------------------------------------------

    private fun enterStep(s: MafiaState, step: Int, now: Long): MafiaState {
        if (step >= NIGHT_ORDER.size) return s.copy(stage = MStage.DAY, step = step, deadline = now + s.dayMs, picks = emptyMap())
        val role = NIGHT_ORDER[step]
        val holders = s.original.filterValues { it == role }.keys
        var notes = s.notes
        if (role == MRole.MAFIOSO || role == MRole.INFORMANTE) {
            val mafia = s.players.filter { s.current[it.id] == MRole.MAFIOSO }
            holders.forEach { h ->
                val others = mafia.filter { it.id != h }.map { it.name }
                notes = notes + (h to if (role == MRole.MAFIOSO) (if (others.isEmpty()) "Você é o único mafioso da mesa" else "Seus comparsas: ${others.joinToString()}")
                else (if (mafia.isEmpty()) "Não tem mafioso nenhum na mesa" else "Mafiosos: ${mafia.joinToString { it.name }}"))
            }
        }
        return s.copy(stage = MStage.NIGHT, step = step, deadline = now + STEP_MS, notes = notes, picks = emptyMap())
    }

    private fun card(s: MafiaState, key: String): MRole? = if (key.startsWith("c")) key.drop(1).toIntOrNull()?.let { s.center.getOrNull(it) } else s.current[key]

    private fun nightAct(s: MafiaState, by: PlayerId, key: String): Step<MafiaState> {
        val role = s.stepRole ?: return Step.Rejected("A noite acabou")
        if (s.original[by] != role) return Step.Ok(s) // a decoy tap: accepted and ignored, so it cannot be told from a real one
        val picked = s.picks[by].orEmpty()
        val isPlayer = s.players.any { it.id == key }
        val isCenter = key in listOf("c0", "c1", "c2")
        if (!isPlayer && !isCenter) return Step.Rejected("Escolhe uma carta")
        return when (role) {
            MRole.DETETIVE -> if (picked.isNotEmpty()) Step.Ok(s) else Step.Ok(s.copy(
                picks = s.picks + (by to listOf(key)),
                notes = s.notes + (by to "${if (isCenter) "Carta ${key.drop(1).toInt() + 1} do centro" else s.name(key)} é ${card(s, key)?.label}"),
            ))
            MRole.BATEDOR -> if (picked.isNotEmpty() || !isPlayer || key == by) Step.Ok(s) else {
                val mine = s.current.getValue(by)
                val cur = s.current + (by to s.current.getValue(key)) + (key to mine)
                Step.Ok(s.copy(current = cur, picks = s.picks + (by to listOf(key)), notes = s.notes + (by to "Você trocou com ${s.name(key)} e agora é ${cur.getValue(by).label}")))
            }
            MRole.FOFOQUEIRO -> when {
                picked.size >= 2 || !isPlayer || key == by || key in picked -> Step.Ok(s)
                picked.isEmpty() -> Step.Ok(s.copy(picks = s.picks + (by to listOf(key)), notes = s.notes + (by to "Escolhe mais uma pessoa")))
                else -> {
                    val a = picked[0]; val cur = s.current + (a to s.current.getValue(key)) + (key to s.current.getValue(a))
                    Step.Ok(s.copy(current = cur, picks = s.picks + (by to listOf(a, key)), notes = s.notes + (by to "Você trocou ${s.name(a)} e ${s.name(key)}")))
                }
            }
            MRole.SONAMBULO -> if (picked.isNotEmpty() || !isCenter) Step.Ok(s) else {
                val i = key.drop(1).toInt(); val mine = s.current.getValue(by)
                Step.Ok(s.copy(current = s.current + (by to s.center[i]), center = s.center.toMutableList().also { it[i] = mine }, picks = s.picks + (by to listOf(key)), notes = s.notes + (by to "Você trocou com uma carta do centro")))
            }
            else -> Step.Ok(s) // Mafioso, Informante and Cidadão have nothing to choose
        }
    }

    private fun startVote(s: MafiaState, now: Long) = s.copy(stage = MStage.VOTE, votes = Votes(s.players.map { it.id }.toSet()), deadline = now + VOTE_MS)

    private fun tally(s: MafiaState): MafiaState {
        val out = s.votes.leaders()
        val mafia = s.players.filter { s.current[it.id] == MRole.MAFIOSO }.map { it.id }
        val townWon = mafia.isEmpty() || out.any { it in mafia }
        return s.copy(stage = MStage.DONE, out = out, townWon = townWon, deadline = null)
    }

    override fun act(s: MafiaState, by: PlayerId, a: Action, now: Long): Step<MafiaState> {
        val key = a.data["key"]?.jsonPrimitive?.content
        return when (s.stage) {
            MStage.ROLES -> {
                if (a.type != Act.BUTTON || key != "ready") return Step.Rejected("Toca em “Já vi”")
                val n = s.copy(ready = s.ready + by)
                Step.Ok(if (n.ready.containsAll(s.players.map { it.id })) enterStep(n, 0, now) else n)
            }
            MStage.NIGHT -> if (a.type == Act.CHOOSE && key != null) nightAct(s, by, key) else Step.Rejected("Toca numa carta")
            MStage.VOTE -> {
                val t = a.data["id"]?.jsonPrimitive?.content
                if (a.type != Act.PICK || by !in s.votes.voters || t == null || t == by || s.players.none { it.id == t }) return Step.Rejected("Escolhe outra pessoa")
                val n = s.copy(votes = s.votes.vote(by, t))
                Step.Ok(if (n.votes.complete) tally(n) else n)
            }
            else -> Step.Rejected("Conversem")
        }
    }

    override fun host(s: MafiaState, cmd: HostCmd, now: Long) = when (s.stage) {
        MStage.ROLES -> enterStep(s, 0, now)
        MStage.NIGHT -> enterStep(s, s.step + 1, now)
        MStage.DAY -> startVote(s, now)
        MStage.VOTE -> tally(s)
        MStage.DONE -> s
    }

    override fun timeout(s: MafiaState, now: Long) = host(s, HostCmd.CONTINUE, now)
    override fun tvAction(s: MafiaState) = when (s.stage) {
        MStage.ROLES -> TvAction("Começar a noite", hold = true)
        MStage.NIGHT -> TvAction("Pular esta etapa", hold = true)
        MStage.DAY -> TvAction("Ir para a votação", hold = true)
        MStage.VOTE -> TvAction("Encerrar a votação", hold = true)
        MStage.DONE -> null
    }

    override fun deadline(s: MafiaState) = s.deadline
    override fun phase(s: MafiaState) = "${s.stage.name.lowercase()}:${s.step}"
    override fun mood(s: MafiaState) = when (s.stage) { MStage.DAY, MStage.DONE -> Mood.TALK; MStage.VOTE -> Mood.FOCUS; else -> Mood.SECRET }

    override fun phone(s: MafiaState, p: PlayerId): PhoneView {
        val ph = phase(s)
        val mine = s.original.getValue(p)
        return when (s.stage) {
            MStage.ROLES -> PhoneView(ph, "Seu papel", Badge("Seu papel", mine.label), Widget.Secret(mine.label, mine.how), s.deadline, if (p in s.ready) emptyList() else listOf(Button("ready", "Já vi")))
            MStage.NIGHT -> {
                val real = s.stepRole == mine
                val options = s.players.filter { it.id != p }.map { Option(it.id, it.name) } + List(3) { Option("c$it", "Carta ${it + 1} do centro") }
                PhoneView(
                    ph, s.notes[p] ?: "Noite · etapa ${s.step + 1}",
                    body = Widget.Choose(if (real) promptFor(mine) else "Toca numa carta (todo mundo toca, pra ninguém se entregar)", options, null),
                    endsAt = s.deadline,
                )
            }
            MStage.DAY -> PhoneView(ph, "Dia", Badge("Seu papel original", mine.label), Widget.Wait("Conversem e descubram a máfia", s.notes[p], react = true), s.deadline)
            MStage.VOTE -> PhoneView(ph, "Votação", Badge("Seu papel original", mine.label), Widget.PickPlayer("Quem é o mafioso?", s.players.map { it.id }.filter { it != p }, s.votes.cast[p]), s.deadline)
            MStage.DONE -> PhoneView(ph, body = Widget.Wait("Acabou a partida", "Olha pra TV"))
        }
    }

    private fun promptFor(r: MRole) = when (r) {
        MRole.DETETIVE -> "Escolhe alguém ou uma carta do centro pra espiar"
        MRole.BATEDOR -> "Escolhe com quem trocar sua carta"
        MRole.FOFOQUEIRO -> "Escolhe duas pessoas pra trocar as cartas"
        MRole.SONAMBULO -> "Escolhe uma carta do centro pra trocar"
        else -> "Toca numa carta (todo mundo toca, pra ninguém se entregar)"
    }

    override fun left(s: MafiaState, p: PlayerId, now: Long): MafiaState {
        val rest = s.copy(players = s.players.filterNot { it.id == p }, ready = s.ready - p, votes = s.votes.without(p))
        return when {
            s.stage == MStage.DONE -> rest
            rest.players.size < 4 -> rest.copy(stage = MStage.DONE, townWon = null, deadline = null)
            rest.stage == MStage.ROLES && rest.ready.containsAll(rest.players.map { it.id }) -> enterStep(rest, 0, now)
            rest.stage == MStage.VOTE && rest.votes.complete -> tally(rest)
            else -> rest
        }
    }

    override fun over(s: MafiaState): Outcome? {
        if (s.stage != MStage.DONE) return null
        val won = s.townWon ?: return Outcome(emptyMap(), "A partida acabou antes da votação")
        val mafia = s.players.filter { s.current[it.id] == MRole.MAFIOSO }
        val chips = if (mafia.isEmpty()) s.players.associate { it.id to 2 }
        else if (won) s.players.filter { s.current[it.id] != MRole.MAFIOSO }.associate { it.id to 3 }
        else mafia.associate { it.id to 4 }
        val headline = when {
            mafia.isEmpty() -> "Não tinha mafioso nenhum: todo mundo ganhou"
            won -> "A cidade pegou ${s.out.filter { s.current[it] == MRole.MAFIOSO }.joinToString { s.name(it) }}"
            else -> "A máfia enganou a cidade: ${mafia.joinToString { it.name }}"
        }
        val highlights = if (!won && mafia.isNotEmpty()) mafia.map { Highlight("MAFIA_WIN", it.id) }
        else s.players.filter { s.original[it.id] == MRole.DETETIVE }.map { Highlight("TOWN_WIN", it.id) }
        return Outcome(chips, headline, highlights)
    }

    override fun secrets(s: MafiaState, p: PlayerId): Set<String> = emptySet()
}
