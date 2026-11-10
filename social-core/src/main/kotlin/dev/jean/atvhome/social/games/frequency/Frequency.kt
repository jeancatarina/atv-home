package dev.jean.atvhome.social.games.frequency

import dev.jean.atvhome.social.game.*
import dev.jean.atvhome.social.kit.Scores
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

enum class FStage { CLUE, DIAL, BET, REVEAL, DONE }

data class FrequencyState(
    val players: List<Player>,
    /** Teams: playerId → 0 or 1. Everyone is on team 0 in the cooperative mode (fewer than 4 people). */
    val team: Map<PlayerId, Int>,
    val coop: Boolean,
    val spectrums: List<String>,
    val round: Int,
    val target: Int,
    val psychic: PlayerId,
    val dial: Int = 50,
    val bet: Int? = null,           // 0 = "mais à esquerda", 1 = "mais à direita"
    val stage: FStage = FStage.CLUE,
    val deadline: Long? = null,
    val points: List<Int> = listOf(0, 0),
    val lastGain: Int = 0,
    val betRight: Boolean? = null,
    val turn: Int = 0,
    val random: Long = 0,
) {
    val pair: List<String> get() = spectrums.getOrElse(round) { "Frio|Quente" }.split("|")
    val dialTeam: Int get() = if (coop) 0 else turn % 2
}

/**
 * Frequência: a spectrum ("Frio ↔ Quente"), a hidden target and one clue. The psychic knows the
 * target and gives a clue aloud; the team turns the dial on the TV with the remote. The other team
 * bets left or right of the dial. Closer to the target scores more.
 */
object Frequency : Game<FrequencyState> {
    private const val CLUE_MS = 90_000L
    private const val DIAL_MS = 60_000L
    private const val BET_MS = 20_000L
    private const val REVEAL_MS = 9_000L
    private val CARDS = listOf(5, 7, 10)

    override val info = GameInfo(
        id = "frequency", name = "Frequência", tagline = "Uma pista, um dial e muita discussão",
        category = Category.COOP, minPlayers = 2, maxPlayers = 10, idealMin = 4, idealMax = 8, minutes = 18, difficulty = 1,
        style = "Controle na mão, celular só pro segredo",
        howTo = listOf(
            "A TV mostra dois extremos. Só quem dá a pista vê, no celular, onde fica o alvo",
            "Quem vê o alvo dá uma pista falando. O time gira o dial no controle até onde acha que tá o alvo",
            "Quanto mais perto do alvo, mais pontos. Com 4 ou mais pessoas, o outro time aposta se foi pra esquerda ou pra direita",
        ),
        settings = listOf(Setting("cards", "Cartas", CARDS.map { "$it" }, default = 1)),
    )

    private fun pickPsychic(players: List<Player>, team: Map<PlayerId, Int>, coop: Boolean, turn: Int): PlayerId {
        val pool = if (coop) players else players.filter { team[it.id] == turn % 2 }.ifEmpty { players }
        return pool[(turn / (if (coop) 1 else 2)) % pool.size].id
    }

    override fun setup(ctx: Setup): FrequencyState {
        val n = CARDS[(ctx.settings["cards"] ?: 1).coerceIn(0, CARDS.lastIndex)]
        val coop = ctx.players.size < 4
        val order = ctx.players.shuffled(ctx.random)
        val team = order.mapIndexed { i, p -> p.id to if (coop) 0 else i % 2 }.toMap()
        val spectrums = ctx.content.ifEmpty { listOf("Frio|Quente") }.shuffled(ctx.random).take(n)
        return FrequencyState(
            ctx.players, team, coop, spectrums, 0, ctx.random.nextInt(8, 93), pickPsychic(ctx.players, team, coop, 0),
            deadline = ctx.now + CLUE_MS, random = ctx.random.nextLong(),
        )
    }

    private fun next(s: FrequencyState, now: Long): FrequencyState {
        val round = s.round + 1
        if (round >= s.spectrums.size || (!s.coop && s.points.any { it >= 10 })) return s.copy(stage = FStage.DONE, deadline = null)
        val turn = s.turn + 1
        val r = java.util.Random(s.random + round)
        return s.copy(
            stage = FStage.CLUE, round = round, turn = turn, target = 8 + r.nextInt(85), dial = 50, bet = null, betRight = null,
            psychic = pickPsychic(s.players, s.team, s.coop, turn), deadline = now + CLUE_MS, lastGain = 0,
        )
    }

    private fun reveal(s: FrequencyState, now: Long): FrequencyState {
        val d = abs(s.dial - s.target)
        val gain = when { d <= 4 -> 4; d <= 9 -> 3; d <= 15 -> 2; else -> 0 }
        val points = s.points.toMutableList().also { it[s.dialTeam] += gain * if (s.round == s.spectrums.lastIndex) 2 else 1 }
        var betRight: Boolean? = null
        if (!s.coop && s.bet != null && d > 4) {
            betRight = (s.bet == 1) == (s.target > s.dial)
            if (betRight) points[1 - s.dialTeam] += 1
        }
        return s.copy(stage = FStage.REVEAL, points = points, lastGain = gain, betRight = betRight, deadline = now + REVEAL_MS)
    }

    private fun afterDial(s: FrequencyState, now: Long) =
        if (s.coop) reveal(s, now) else s.copy(stage = FStage.BET, deadline = now + BET_MS)

    override fun act(s: FrequencyState, by: PlayerId, a: Action, now: Long): Step<FrequencyState> {
        if (s.stage == FStage.BET && a.type == Act.CHOOSE) {
            if (s.team[by] == s.dialTeam) return Step.Rejected("Quem aposta é o outro time")
            val v = a.data["key"]?.jsonPrimitive?.content?.toIntOrNull()?.takeIf { it in 0..1 } ?: return Step.Rejected("Esquerda ou direita?")
            val next = s.copy(bet = v)
            return Step.Ok(reveal(next, now))
        }
        if (s.stage == FStage.CLUE && a.type == Act.BUTTON && by == s.psychic && a.data["key"]?.jsonPrimitive?.content == "told")
            return Step.Ok(s.copy(stage = FStage.DIAL, deadline = now + DIAL_MS))
        return Step.Rejected("Agora é no controle da TV")
    }

    override fun host(s: FrequencyState, cmd: HostCmd, now: Long): FrequencyState = when (s.stage) {
        FStage.CLUE -> if (cmd == HostCmd.CONTINUE) s.copy(stage = FStage.DIAL, deadline = now + DIAL_MS) else if (cmd == HostCmd.SKIP) next(s, now) else s
        FStage.DIAL -> when (cmd) {
            HostCmd.LEFT -> s.copy(dial = (s.dial - 2).coerceAtLeast(0))
            HostCmd.RIGHT -> s.copy(dial = (s.dial + 2).coerceAtMost(100))
            HostCmd.CONTINUE -> afterDial(s, now)
            HostCmd.SKIP -> next(s, now)
            else -> s
        }
        FStage.BET -> reveal(s, now)
        FStage.REVEAL -> next(s, now)
        FStage.DONE -> s
    }

    override fun timeout(s: FrequencyState, now: Long) = when (s.stage) {
        FStage.CLUE -> s.copy(stage = FStage.DIAL, deadline = now + DIAL_MS)
        FStage.DIAL -> afterDial(s, now)
        else -> host(s, HostCmd.CONTINUE, now)
    }

    override fun tvAction(s: FrequencyState) = when (s.stage) {
        FStage.CLUE -> TvAction("Ir para o dial")
        FStage.DIAL -> TvAction("Travar o dial", secondary = TvAction("Trocar de carta", HostCmd.SKIP, hold = true))
        FStage.BET -> TvAction("Revelar", hold = true)
        FStage.REVEAL -> TvAction("Próxima carta")
        FStage.DONE -> null
    }

    override fun deadline(s: FrequencyState) = s.deadline
    override fun phase(s: FrequencyState) = "${s.stage.name.lowercase()}:${s.round}"
    override fun mood(s: FrequencyState) = if (s.stage == FStage.DIAL) Mood.TALK else Mood.FOCUS

    override fun phone(s: FrequencyState, p: PlayerId): PhoneView {
        val ph = phase(s)
        val low = s.pair[0]; val high = s.pair.getOrElse(1) { "" }
        return when (s.stage) {
            FStage.CLUE -> if (p == s.psychic) PhoneView(
                ph, "Você dá a pista", Badge("$low ↔ $high", "alvo ${s.target}%"),
                Widget.Secret("$low  ↔  $high", "${s.target}%"), s.deadline, listOf(Button("told", "Já dei a pista")),
            ) else PhoneView(ph, body = Widget.Wait("${s.players.first { it.id == s.psychic }.name} vai dar uma pista", "Olha pra TV"), endsAt = s.deadline)
            FStage.DIAL -> PhoneView(ph, body = Widget.Wait("O time gira o dial no controle", "Discutam!", react = true), endsAt = s.deadline)
            FStage.BET -> if (!s.coop && s.team[p] != s.dialTeam) PhoneView(
                ph, "Aposte", body = Widget.Choose("O alvo está mais à…", listOf(Option("0", "◀ esquerda do dial"), Option("1", "direita do dial ▶")), s.bet?.toString()), endsAt = s.deadline,
            ) else PhoneView(ph, body = Widget.Wait("O outro time está apostando"), endsAt = s.deadline)
            FStage.REVEAL -> PhoneView(ph, body = Widget.Wait("Olha pra TV", react = true))
            FStage.DONE -> PhoneView(ph, body = Widget.Wait("Acabou a partida", "Olha pra TV"))
        }
    }

    override fun left(s: FrequencyState, p: PlayerId, now: Long): FrequencyState {
        val players = s.players.filterNot { it.id == p }
        if (players.size < 2) return s.copy(players = players, stage = FStage.DONE, deadline = null)
        val rest = s.copy(players = players, team = s.team - p)
        return if (p == s.psychic && s.stage != FStage.REVEAL && s.stage != FStage.DONE) next(rest, now) else rest
    }

    override fun over(s: FrequencyState): Outcome? {
        if (s.stage != FStage.DONE) return null
        return if (s.coop) {
            val total = s.points[0]; val max = s.spectrums.size * 4
            val pct = total * 100 / maxOf(max, 1)
            Outcome(s.players.associate { it.id to pct / 10 }, "Sintonia de $pct%", listOf(Highlight("SYNC", null, pct)), coopWon = pct >= 50)
        } else {
            val win = if (s.points[0] == s.points[1]) null else if (s.points[0] > s.points[1]) 0 else 1
            Outcome(
                s.players.filter { win == null || s.team[it.id] == win }.associate { it.id to 3 },
                when (win) { null -> "Empate"; 0 -> "O time Verde venceu"; else -> "O time Roxo venceu" } + " (${s.points[0]} × ${s.points[1]})",
            )
        }
    }

    override fun secrets(s: FrequencyState, p: PlayerId) = if (p == s.psychic || s.stage != FStage.CLUE) emptySet() else setOf("${s.target}%")
}
