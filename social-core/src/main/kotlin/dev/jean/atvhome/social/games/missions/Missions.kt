package dev.jean.atvhome.social.games.missions

import dev.jean.atvhome.social.game.*
import kotlinx.serialization.json.jsonPrimitive

data class Mission(val text: String, val level: Int)

data class FeedItem(val kind: String, val who: PlayerId, val other: PlayerId? = null, val n: Int = 0)

data class MissionsState(
    val players: List<Player>,
    val hands: Map<PlayerId, List<Mission>>,
    val deck: List<Mission>,
    val points: Map<PlayerId, Int> = emptyMap(),
    val selected: Map<PlayerId, Int> = emptyMap(),
    /** Points of each person's last completed mission: what an unmasking takes away. */
    val lastDone: Map<PlayerId, Int> = emptyMap(),
    val picking: Set<PlayerId> = emptySet(),
    /** target → accuser, waiting for the target's honest answer. */
    val accusations: Map<PlayerId, PlayerId> = emptyMap(),
    val cooldown: Map<PlayerId, Long> = emptyMap(),
    val feed: List<FeedItem> = emptyList(),
    val deadline: Long? = null,
    val over: Boolean = false,
)

/**
 * Missões Secretas: a game in the background. Everybody holds three secret missions to complete
 * during the evening without being noticed; the TV only shows who scored. Someone who suspects can
 * unmask a person, and that person answers truthfully on their own phone.
 */
object Missions : Game<MissionsState> {
    private const val COOLDOWN_MS = 180_000L
    private val TIMES = listOf(900_000L, 1_800_000L, 3_600_000L)

    override val info = GameInfo(
        id = "missions", name = "Missões Secretas", tagline = "Cumpra suas missões sem ninguém perceber",
        category = Category.TALK, minPlayers = 2, maxPlayers = 10, idealMin = 3, idealMax = 8, minutes = 30, difficulty = 1,
        style = "Roda em segundo plano enquanto vocês conversam",
        howTo = listOf(
            "Cada um tem 3 missões secretas no celular, de 1 a 3 pontos",
            "Cumpra durante a conversa sem ninguém notar e marque no celular",
            "Desconfiou de alguém? Desmascara: se acertar, a missão da pessoa queima e você ganha 1 ponto",
        ),
        settings = listOf(Setting("time", "Duração", listOf("15 min", "30 min", "60 min"), default = 0)),
    )

    override fun setup(ctx: Setup): MissionsState {
        val pool = ctx.content.map { l -> l.split("|").let { Mission(it[0], it.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 3) ?: 1) } }
            .ifEmpty { listOf(Mission("Faça alguém dizer “pipoca”", 1)) }.shuffled(ctx.random)
        var i = 0
        fun draw() = pool[i++ % pool.size]
        val hands = ctx.players.associate { it.id to List(3) { draw() } }
        val deck = List(pool.size) { draw() }
        return MissionsState(ctx.players, hands, deck, deadline = ctx.now + TIMES[(ctx.settings["time"] ?: 0).coerceIn(0, TIMES.lastIndex)])
    }

    override fun act(s: MissionsState, by: PlayerId, a: Action, now: Long): Step<MissionsState> {
        if (s.over || s.players.none { it.id == by }) return Step.Rejected("A partida acabou")
        val key = a.data["key"]?.jsonPrimitive?.content
        // An accusation waiting for this person's answer comes first
        s.accusations[by]?.let { accuser ->
            val v = key?.toIntOrNull()
            if (a.type != Act.CHOOSE || v !in 0..1) return Step.Rejected("Responde sim ou não")
            val acc = s.accusations - by
            return Step.Ok(
                if (v == 1) {
                    val taken = s.lastDone[by] ?: 0
                    s.copy(
                        accusations = acc, lastDone = s.lastDone + (by to 0),
                        points = s.points + (by to maxOf(0, (s.points[by] ?: 0) - taken)) + (accuser to (s.points[accuser] ?: 0) + 1),
                        feed = (s.feed + FeedItem("unmasked", accuser, by)).takeLast(8),
                    )
                } else s.copy(accusations = acc, feed = (s.feed + FeedItem("wrong", accuser, by)).takeLast(8)),
            )
        }
        if (by in s.picking) {
            val t = a.data["id"]?.jsonPrimitive?.content
            if (a.type != Act.PICK || t == null || t == by || s.players.none { it.id == t }) return Step.Rejected("Escolhe outra pessoa")
            if (t in s.accusations) return Step.Rejected("${s.players.first { it.id == t }.name} já tá respondendo uma acusação")
            return Step.Ok(s.copy(picking = s.picking - by, accusations = s.accusations + (t to by), cooldown = s.cooldown + (by to now + COOLDOWN_MS)))
        }
        return when {
            a.type == Act.CHOOSE -> {
                val i = key?.removePrefix("m")?.toIntOrNull()
                if (i == null || i !in 0..2) Step.Rejected("Escolhe uma missão") else Step.Ok(s.copy(selected = s.selected + (by to i)))
            }
            a.type == Act.BUTTON && key == "done" -> {
                val i = s.selected[by] ?: return Step.Rejected("Marca a missão cumprida primeiro")
                val m = s.hands.getValue(by)[i]
                val replacement = s.deck[(s.points.size + s.feed.size + i + by.hashCode().and(0xff)) % s.deck.size]
                Step.Ok(s.copy(
                    hands = s.hands + (by to s.hands.getValue(by).toMutableList().also { it[i] = replacement }),
                    points = s.points + (by to (s.points[by] ?: 0) + m.level), lastDone = s.lastDone + (by to m.level),
                    selected = s.selected - by, feed = (s.feed + FeedItem("done", by, null, m.level)).takeLast(8),
                ))
            }
            a.type == Act.BUTTON && key == "accuse" ->
                if ((s.cooldown[by] ?: 0L) > now) Step.Rejected("Calma, espera um pouco pra desmascarar de novo")
                else Step.Ok(s.copy(picking = s.picking + by))
            a.type == Act.BUTTON && key == "cancel" -> Step.Ok(s.copy(picking = s.picking - by))
            else -> Step.Rejected("Hmm, não entendi essa")
        }
    }

    override fun host(s: MissionsState, cmd: HostCmd, now: Long) = if (cmd == HostCmd.SKIP) s.copy(over = true, deadline = null) else s
    override fun timeout(s: MissionsState, now: Long) = s.copy(over = true, deadline = null)
    override fun tvAction(s: MissionsState) = if (s.over) null else TvAction("Encerrar a partida", HostCmd.SKIP, hold = true)

    override fun deadline(s: MissionsState) = s.deadline
    override fun phase(s: MissionsState) = if (s.over) "over" else "run"
    override fun mood(s: MissionsState) = Mood.TALK

    override fun phone(s: MissionsState, p: PlayerId): PhoneView {
        if (s.over) return PhoneView("over", body = Widget.Wait("Acabou a partida", "Olha pra TV"))
        s.accusations[p]?.let { accuser ->
            val name = s.players.firstOrNull { it.id == accuser }?.name ?: "Alguém"
            return PhoneView("run", "Alguém tá desconfiando de você", body = Widget.Choose("$name acha que você cumpriu uma missão. Foi isso mesmo?", listOf(Option("1", "Sim, acertou"), Option("0", "Não")), null))
        }
        if (p in s.picking) return PhoneView("run", "Quem você desmascara?", body = Widget.PickPlayer("Quem cumpriu uma missão?", s.players.map { it.id }.filter { it != p }), buttons = listOf(Button("cancel", "Cancelar")))
        val hand = s.hands.getValue(p)
        return PhoneView(
            "run", "Suas missões · ${s.points[p] ?: 0} pts",
            body = Widget.Choose("Marca a que você cumpriu", hand.mapIndexed { i, m -> Option("m$i", "${"★".repeat(m.level)}  ${m.text}") }, s.selected[p]?.let { "m$it" }),
            endsAt = s.deadline, buttons = listOf(Button("done", "Cumpri a marcada"), Button("accuse", "Desmascarar")),
        )
    }

    override fun left(s: MissionsState, p: PlayerId, now: Long): MissionsState {
        val rest = s.copy(
            players = s.players.filterNot { it.id == p }, picking = s.picking - p, accusations = s.accusations.filterKeys { it != p }.filterValues { it != p },
        )
        return if (rest.players.size < 2) rest.copy(over = true, deadline = null) else rest
    }

    override fun over(s: MissionsState): Outcome? {
        if (!s.over) return null
        val top = s.points.maxByOrNull { it.value }
        val name = s.players.firstOrNull { it.id == top?.key }?.name
        return Outcome(
            s.points.filterValues { it > 0 }, if (top != null && top.value > 0 && name != null) "$name cumpriu mais missões: ${top.value} pontos" else "Ninguém pontuou",
            listOfNotNull(top?.takeIf { it.value > 0 }?.let { Highlight("MISSION_MASTER", it.key, it.value) }),
        )
    }

    override fun secrets(s: MissionsState, p: PlayerId): Set<String> =
        s.hands.filterKeys { it != p }.values.flatten().map { it.text }.toSet() - s.hands[p].orEmpty().map { it.text }.toSet()
}
