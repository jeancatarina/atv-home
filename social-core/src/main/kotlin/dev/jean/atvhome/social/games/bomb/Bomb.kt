package dev.jean.atvhome.social.games.bomb

import dev.jean.atvhome.social.game.*
import kotlin.math.ceil
import kotlin.random.Random

enum class BombStage { PLAY, WON, EXPLODED }

data class BombState(
    val players: List<Player>,
    val defuser: PlayerId,
    val edge: Edgework,
    val modules: List<Module>,
    val strikes: Int = 0,
    /** The module being worked on, or null while looking at the whole bomb. */
    val focus: Int? = null,
    val sel: Int = 0,
    val cursor: Int = 0,
    val stage: BombStage = BombStage.PLAY,
    val deadline: Long? = null,
    val totalMs: Long = 0,
    val level: Int = 0,
    val secondsLeftAtEnd: Int = 0,
    val why: String = "",
) {
    val solvedCount: Int get() = modules.count { it.solved }
}

/**
 * Desarme a Bomba: one person defuses on the TV with the remote and cannot read the manual; the
 * others hold the manual on their phones and cannot see the bomb. Everyone talks. Three strikes or
 * the clock reaching zero and it explodes.
 */
object Bomb : Game<BombState> {
    const val MAX_STRIKES = 3
    private val LEVEL_MODULES = listOf(2, 3, 4, 5, 6)
    private val TYPES = listOf("wires", "button", "symbols", "maze")

    override val info = GameInfo(
        id = "bomb", name = "Desarme a Bomba", tagline = "Um vê a bomba. Os outros têm o manual",
        category = Category.COOP, minPlayers = 2, maxPlayers = 4, idealMin = 2, idealMax = 3, minutes = 6, difficulty = 3,
        style = "Um no controle, o resto no manual",
        howTo = listOf(
            "Um de vocês é o desarmador: fica com o controle e vai descrevendo a bomba em voz alta",
            "O resto lê o manual no celular e diz o que fazer. O desarmador não pode espiar o manual",
            "Desarmem todos os módulos antes do tempo acabar. Três erros e ela explode",
        ),
        coop = true,
        settings = listOf(
            Setting("level", "Nível", listOf("1", "2", "3", "4", "5"), default = 0),
            Setting("daily", "Bomba do dia", listOf("Não", "Sim"), default = 0),
        ),
    )

    override fun setup(ctx: Setup): BombState {
        val level = (ctx.settings["level"] ?: 0).coerceIn(0, 4)
        val random = if ((ctx.settings["daily"] ?: 0) == 1) Random(ctx.now / 86_400_000L) else ctx.random
        val serial = (1..6).map { "ABCDEFGHJKLMNPRSTUVXZ0123456789".random(random) }.joinToString("").let { if (it.none(Char::isDigit)) it.dropLast(1) + random.nextInt(10) else it }
        val edge = Edgework(serial, random.nextInt(0, 5), random.nextBoolean(), random.nextBoolean())
        val n = LEVEL_MODULES[level]
        val kinds = (TYPES + TYPES).shuffled(random).take(n).sortedBy { TYPES.indexOf(it) }.let { list -> if (list.toSet().size < 2) TYPES.take(2) else list }
        val modules = kinds.map { kind ->
            when (kind) {
                "wires" -> Wires(List(random.nextInt(3, 7)) { WIRE_COLORS.random(random) })
                "button" -> PushButton(ButtonRules.colors.random(random), ButtonRules.words.random(random))
                "symbols" -> SymbolTable.draw(random).let { (shown, order) -> Symbols(shown, order) }
                else -> {
                    val id = random.nextInt(Mazes.COUNT); val (m1, m2) = Mazes.markers(id)
                    var start: Int; var end: Int
                    do { start = random.nextInt(Mazes.SIZE * Mazes.SIZE); end = random.nextInt(Mazes.SIZE * Mazes.SIZE) } while (start == end || start == m1 || start == m2 || end == m1 || end == m2)
                    Maze(id, start, end)
                }
            }
        }
        val total = (420_000L - 40_000L * level)
        return BombState(ctx.players, ctx.players.random(random).id, edge, modules, deadline = ctx.now + total, totalMs = total, level = level)
    }

    private fun strike(s: BombState, why: String): BombState =
        if (s.strikes + 1 >= MAX_STRIKES) s.copy(strikes = MAX_STRIKES, stage = BombStage.EXPLODED, why = why, deadline = null)
        else s.copy(strikes = s.strikes + 1)

    private fun replace(s: BombState, i: Int, m: Module) = s.copy(modules = s.modules.toMutableList().also { it[i] = m })

    private fun solved(s: BombState, i: Int, m: Module, now: Long): BombState {
        val n = replace(s, i, m).copy(focus = null)
        return if (n.modules.all { it.solved }) n.copy(stage = BombStage.WON, secondsLeftAtEnd = ceil(((s.deadline ?: now) - now) / 1000.0).toInt().coerceAtLeast(0), deadline = null) else n
    }

    override fun act(s: BombState, by: PlayerId, a: Action, now: Long): Step<BombState> = Step.Rejected(
        if (by == s.defuser) "Usa o controle da TV" else "Você lê o manual e orienta, o desarmador age na TV",
    )

    override fun host(s: BombState, cmd: HostCmd, now: Long): BombState {
        if (s.stage != BombStage.PLAY) return s
        val f = s.focus ?: return when (cmd) {
            HostCmd.LEFT, HostCmd.UP -> s.copy(sel = (s.sel - 1 + s.modules.size) % s.modules.size)
            HostCmd.RIGHT, HostCmd.DOWN -> s.copy(sel = (s.sel + 1) % s.modules.size)
            HostCmd.CONTINUE -> if (s.modules[s.sel].solved) s else s.copy(focus = s.sel, cursor = (s.modules[s.sel] as? Maze)?.pos ?: 0)
            HostCmd.SKIP -> s
        }
        if (cmd == HostCmd.SKIP) return replace(s, f, (s.modules[f] as? PushButton)?.copy(held = false) ?: s.modules[f]).copy(focus = null)
        return when (val m = s.modules[f]) {
            is Wires -> when (cmd) {
                HostCmd.UP -> s.copy(cursor = (s.cursor - 1).coerceAtLeast(0))
                HostCmd.DOWN -> s.copy(cursor = (s.cursor + 1).coerceAtMost(m.wires.lastIndex))
                HostCmd.CONTINUE -> if (s.cursor in m.cut) s else {
                    val cut = m.copy(cut = m.cut + s.cursor)
                    if (s.cursor == WireRules.correct(m.wires, s.edge)) solved(s, f, cut.copy(solved = true), now) else strike(replace(s, f, cut), "Cortou o fio errado")
                }
                else -> s
            }
            is PushButton -> if (cmd != HostCmd.CONTINUE) s else if (!m.held) replace(s, f, m.copy(held = true, heldAt = now)) else {
                val held = now - m.heldAt
                val hold = ButtonRules.shouldHold(m, s.edge)
                val left = ceil(((s.deadline ?: now) - now) / 1000.0).toInt().coerceAtLeast(0)
                val shown = "%d:%02d".format(left / 60, left % 60)
                val ok = if (hold) held >= ButtonRules.TAP_MS && shown.contains(ButtonRules.digitFor(ButtonRules.stripOf(m)).toString()) else held < ButtonRules.TAP_MS
                if (ok) solved(s, f, m.copy(held = false, solved = true), now) else strike(replace(s, f, m.copy(held = false)), "Soltou o botão na hora errada")
            }
            is Symbols -> when (cmd) {
                HostCmd.LEFT -> s.copy(cursor = if (s.cursor % 2 == 0) s.cursor else s.cursor - 1)
                HostCmd.RIGHT -> s.copy(cursor = if (s.cursor % 2 == 1) s.cursor else s.cursor + 1)
                HostCmd.UP -> s.copy(cursor = if (s.cursor < 2) s.cursor else s.cursor - 2)
                HostCmd.DOWN -> s.copy(cursor = if (s.cursor >= 2) s.cursor else s.cursor + 2)
                HostCmd.CONTINUE -> {
                    val g = m.shown[s.cursor]
                    when {
                        g in m.pressed -> s
                        g == m.order[m.pressed.size] -> m.copy(pressed = m.pressed + g).let { n -> if (n.pressed.size == 4) solved(s, f, n.copy(solved = true), now) else replace(s, f, n) }
                        else -> strike(replace(s, f, m.copy(pressed = emptyList())), "Símbolo fora da ordem")
                    }
                }
                else -> s
            }
            is Maze -> {
                val x = m.pos % Mazes.SIZE; val y = m.pos / Mazes.SIZE
                val to = when (cmd) {
                    HostCmd.LEFT -> if (x > 0) m.pos - 1 else -1
                    HostCmd.RIGHT -> if (x < Mazes.SIZE - 1) m.pos + 1 else -1
                    HostCmd.UP -> if (y > 0) m.pos - Mazes.SIZE else -1
                    HostCmd.DOWN -> if (y < Mazes.SIZE - 1) m.pos + Mazes.SIZE else -1
                    else -> return s
                }
                when {
                    to < 0 -> s
                    !Mazes.canMove(m.id, m.pos, to) -> strike(s, "Bateu na parede")
                    to == m.end -> solved(s, f, m.copy(pos = to, solved = true), now)
                    else -> replace(s, f, m.copy(pos = to))
                }
            }
            else -> s
        }
    }

    override fun timeout(s: BombState, now: Long) = if (s.stage == BombStage.PLAY) s.copy(stage = BombStage.EXPLODED, why = "O tempo acabou", deadline = null) else s
    override fun tvAction(s: BombState): TvAction? {
        if (s.stage != BombStage.PLAY) return null
        val f = s.focus ?: return TvAction("Abrir o módulo")
        return when (s.modules[f]) {
            is Wires -> TvAction("Cortar o fio", secondary = TvAction("Sair do módulo", HostCmd.SKIP))
            is PushButton -> TvAction(if ((s.modules[f] as PushButton).held) "Soltar" else "Apertar", secondary = TvAction("Sair do módulo", HostCmd.SKIP))
            is Symbols -> TvAction("Apertar o símbolo", secondary = TvAction("Sair do módulo", HostCmd.SKIP))
            else -> TvAction("Sair do módulo", HostCmd.SKIP)
        }
    }

    override fun deadline(s: BombState) = s.deadline
    override fun phase(s: BombState) = "${s.stage.name.lowercase()}:${s.strikes}:${s.solvedCount}"
    override fun mood(s: BombState) = Mood.TALK

    /** The manual, in pages; experts split them between them. The first page is for everybody. */
    private fun pages(): List<ManualPage> = listOf(
        ManualPage("Fios", WireRules.manual()),
        ManualPage("Botão", ButtonRules.manual()),
        ManualPage("Símbolos", SymbolTable.manual()),
        ManualPage("Labirintos", Mazes.manual()),
    )

    fun manualFor(s: BombState, p: PlayerId): List<ManualPage> {
        val experts = s.players.map { it.id }.filter { it != s.defuser }
        val i = experts.indexOf(p)
        val general = ManualPage("Como funciona", listOf(
            "Peça ao desarmador: o número de série, quantas baterias, e se as luzes CAR e FRK estão acesas.",
            "Depois, peça que ele descreva um módulo de cada vez. Procure a regra no manual e diga o que fazer.",
            "Você tem só parte do manual. Se faltar uma página, outro especialista tem.",
        ))
        return listOf(general) + pages().filterIndexed { idx, _ -> experts.size <= 1 || idx % experts.size == i.coerceAtLeast(0) }
    }

    override fun phone(s: BombState, p: PlayerId): PhoneView {
        val ph = phase(s)
        if (s.stage != BombStage.PLAY) return PhoneView(ph, body = Widget.Wait(if (s.stage == BombStage.WON) "Desarmada!" else "Explodiu…", "Olha pra TV"))
        return if (p == s.defuser) PhoneView(ph, "Erros: ${s.strikes}/$MAX_STRIKES", body = Widget.Wait("Você é o desarmador", "Use o controle da TV e descreva em voz alta o que vê"), endsAt = s.deadline)
        else PhoneView(ph, "Erros: ${s.strikes}/$MAX_STRIKES · ${s.solvedCount}/${s.modules.size} módulos", body = Widget.Manual(manualFor(s, p)), endsAt = s.deadline)
    }

    override fun left(s: BombState, p: PlayerId, now: Long): BombState {
        val rest = s.copy(players = s.players.filterNot { it.id == p })
        return if (s.stage != BombStage.PLAY) rest
        else if (p == s.defuser || rest.players.size < 2) rest.copy(stage = BombStage.EXPLODED, why = "Saiu gente demais e a bomba ficou sem equipe", deadline = null) else rest
    }

    override fun over(s: BombState): Outcome? = when (s.stage) {
        BombStage.PLAY -> null
        BombStage.WON -> Outcome(s.players.associate { it.id to 4 }, "Bomba desarmada com ${s.secondsLeftAtEnd} segundos de sobra", listOf(Highlight("DEFUSED", s.defuser, s.secondsLeftAtEnd)), coopWon = true)
        BombStage.EXPLODED -> Outcome(s.players.associate { it.id to 1 }, "A bomba explodiu: ${s.why}", listOf(Highlight("BOOM", s.defuser)), coopWon = false)
    }

    override fun secrets(s: BombState, p: PlayerId): Set<String> =
        if (p == s.defuser && s.stage == BombStage.PLAY) pages().flatMap { it.lines }.filter { it.length > 12 }.toSet() else emptySet()
}
