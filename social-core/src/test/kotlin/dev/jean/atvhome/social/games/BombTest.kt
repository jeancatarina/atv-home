package dev.jean.atvhome.social.games

import dev.jean.atvhome.social.people
import dev.jean.atvhome.social.game.*
import dev.jean.atvhome.social.games.bomb.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.ceil
import kotlin.random.Random

class BombTest {

    private fun bomb(n: Int = 3, level: Int = 0, seed: Int = 1) = Bomb.setup(Setup(people(n), mapOf("level" to level), Random(seed), 0, emptyList()))
    private fun host(s: BombState, cmd: HostCmd, now: Long = 0) = Bomb.host(s, cmd, now)
    private fun BombState.focusOn(i: Int): BombState = copy(sel = i, focus = i, cursor = 0)

    // --- The manual and the rules are one thing ------------------------------------------------------------

    @Test fun everyWireConfigurationHasExactlyOneCorrectWireInsideTheBomb() {
        val r = Random(7)
        repeat(5_000) {
            val n = r.nextInt(3, 7)
            val wires = List(n) { WIRE_COLORS.random(r) }
            val e = Edgework("ABC12${r.nextInt(10)}", r.nextInt(5), r.nextBoolean(), r.nextBoolean())
            val i = WireRules.correct(wires, e)
            assertTrue("$wires -> $i", i in wires.indices)
        }
    }

    @Test fun theWireManualListsEveryRuleOfEveryCount() {
        val lines = WireRules.manual()
        assertEquals(4, lines.count { it.startsWith("Com ") })
        assertEquals(WireRules.byCount.values.sumOf { it.size }, lines.count { it.contains("corte") })
        WireRules.byCount.values.forEach { rules -> assertNull("the last rule is the catch-all", rules.last().whenText); assertTrue(rules.dropLast(1).all { it.whenText != null }) }
    }

    @Test fun aWireRuleThatNamesAColourOnlyFiresWhenThatColourIsThere() {
        // "o último fio azul" needs a blue wire, "o primeiro" etc. need nothing: no rule may point outside the bomb
        val rules = WireRules.byCount.getValue(4)
        val wires = listOf("azul", "vermelho", "azul", "branco")
        assertEquals(2, rules[0].index(wires))
        assertTrue(rules[0].test(wires, Edgework("AAAAA1", 0, false, false)))
        assertFalse(rules[0].test(wires, Edgework("AAAAA2", 0, false, false)))
    }

    @Test fun mazesArePerfectAndTellableApartByTheirMarkers() {
        val markers = (0 until Mazes.COUNT).map { Mazes.markers(it).toList().sorted() }
        assertEquals(Mazes.COUNT, markers.toSet().size)
        for (id in 0 until Mazes.COUNT) {
            val open = Mazes.open(id)
            assertEquals("every cell is reachable", Mazes.SIZE * Mazes.SIZE, reach(open, 0).size)
            assertEquals("a perfect maze has cells-1 passages", Mazes.SIZE * Mazes.SIZE - 1, open.values.sumOf { it.size } / 2)
            val art = Mazes.draw(id)
            assertEquals(Mazes.SIZE * 2 + 1, art.size)
            assertTrue(art.all { it.length == 14 }) // the ¦ marker plus 13 cells
            assertEquals(2, art.sumOf { row -> row.count { it == '●' } })
        }
    }

    private fun reach(open: Map<Int, Set<Int>>, from: Int): Set<Int> {
        val seen = mutableSetOf(from); val q = ArrayDeque(listOf(from))
        while (q.isNotEmpty()) open[q.removeFirst()].orEmpty().forEach { if (seen.add(it)) q.addLast(it) }
        return seen
    }

    @Test fun theSymbolsOnAModuleFitExactlyOneColumn() {
        repeat(300) { seed ->
            val (shown, order) = SymbolTable.draw(Random(seed))
            assertEquals(4, shown.toSet().size)
            assertEquals(1, SymbolTable.columns.count { c -> shown.all { it in c } })
            val col = SymbolTable.columns.first { c -> shown.all { it in c } }
            assertEquals(order, order.sortedBy { col.indexOf(it) })
            assertEquals(shown.toSet(), order.toSet())
        }
    }

    // --- Playing -------------------------------------------------------------------------------------------

    @Test fun cuttingTheRightWireSolvesAndTheWrongOneIsAStrike() {
        val e = Edgework("ABCDE1", 0, false, false)
        val m = Wires(listOf("vermelho", "azul", "branco"))
        val right = WireRules.correct(m.wires, e)
        val s0 = BombState(people(3), "p1", e, listOf(m, Maze(0, 0, 35)), deadline = 100_000, totalMs = 100_000)
        val ok = host(host(s0.focusOn(0).copy(cursor = right), HostCmd.CONTINUE), HostCmd.CONTINUE)
        assertNull(ok.focus); assertTrue(ok.modules[0].solved); assertEquals(0, ok.strikes)
        val wrong = host(s0.focusOn(0).copy(cursor = (right + 1) % 3), HostCmd.CONTINUE)
        assertEquals(1, wrong.strikes); assertFalse(wrong.modules[0].solved)
        assertEquals("a cut wire stays cut", setOf((right + 1) % 3), (wrong.modules[0] as Wires).cut)
    }

    @Test fun theButtonNeedsTheRightWayAndTheRightMoment() {
        val e = Edgework("ABCDE1", 1, false, false)
        val tapButton = PushButton("vermelho", "SEGURAR")           // rule 6: tap
        assertFalse(ButtonRules.shouldHold(tapButton, e)); 
        val holdButton = PushButton("amarelo", "APERTAR")          // rule 5: hold
        assertTrue(ButtonRules.shouldHold(holdButton, e))
        val total = 400_000L
        fun start(b: PushButton) = BombState(people(3), "p1", e, listOf(b, Maze(0, 0, 35)), deadline = total, totalMs = total).focusOn(0)
        // tap: press and release at once
        var s = host(start(tapButton), HostCmd.CONTINUE, 1_000); s = host(s, HostCmd.CONTINUE, 1_200)
        assertTrue(s.modules[0].solved)
        // tap held too long: strike
        s = host(start(tapButton), HostCmd.CONTINUE, 1_000); s = host(s, HostCmd.CONTINUE, 3_000)
        assertEquals(1, s.strikes)
        // hold: release when the timer shows the strip's digit
        val digit = ButtonRules.digitFor(ButtonRules.stripOf(holdButton)).toString()
        val releaseAt = (0L..300L).map { r -> total - r * 1000 }.first { now -> val left = ceil((total - now) / 1000.0).toInt(); "%d:%02d".format(left / 60, left % 60).contains(digit) && now > 5_000 }
        s = host(start(holdButton), HostCmd.CONTINUE, 1_000); s = host(s, HostCmd.CONTINUE, releaseAt)
        assertTrue("released at $releaseAt", s.modules[0].solved)
        val wrongAt = (0L..300L).map { r -> total - r * 1000 }.first { now -> val left = ceil((total - now) / 1000.0).toInt(); !"%d:%02d".format(left / 60, left % 60).contains(digit) && now > 5_000 }
        s = host(start(holdButton), HostCmd.CONTINUE, 1_000); s = host(s, HostCmd.CONTINUE, wrongAt)
        assertEquals(1, s.strikes); assertFalse(s.modules[0].solved)
    }

    @Test fun symbolsMustBePressedInOrderAndAMistakeStartsOver() {
        val shown = listOf(3, 0, 5, 1); val order = listOf(0, 1, 3, 5)
        var s = BombState(people(3), "p1", Edgework("A1", 0, false, false), listOf(Symbols(shown, order), Maze(0, 0, 35)), deadline = 9_999, totalMs = 9_999).focusOn(0)
        fun press(i: Int) { s = s.copy(cursor = i); s = host(s, HostCmd.CONTINUE) }
        press(1); press(3)
        assertEquals(listOf(0, 1), (s.modules[0] as Symbols).pressed)
        press(0)                                  // shown[0] is glyph 3, the next in order: correct
        assertEquals(listOf(0, 1, 3), (s.modules[0] as Symbols).pressed)
        press(1)                                  // glyph 0 again: already pressed, ignored
        assertEquals(0, s.strikes)
        s = s.copy(modules = listOf(Symbols(shown, order, emptyList()), Maze(0, 0, 35)))
        press(0)                                  // 3 first: wrong
        assertEquals(1, s.strikes); assertTrue((s.modules[0] as Symbols).pressed.isEmpty())
    }

    @Test fun hittingAMazeWallIsAStrikeAndThreeStrikesExplode() {
        val id = 0; val open = Mazes.open(id)
        var s = BombState(people(3), "p1", Edgework("A1", 0, false, false), listOf(Maze(id, 0, 35), Wires(listOf("azul", "azul", "azul"))), deadline = 9_999, totalMs = 9_999).focusOn(0)
        val cmd = if (1 !in open[0].orEmpty()) HostCmd.RIGHT else HostCmd.DOWN
        repeat(3) { s = host(s, cmd) }
        assertEquals(BombStage.EXPLODED, s.stage)
        assertEquals(3, s.strikes)
        assertTrue(Bomb.over(s)!!.headline.contains("parede"))
        assertEquals(s, host(s, HostCmd.LEFT))
    }

    @Test fun theClockRunningOutExplodesTheBomb() {
        val s = Bomb.timeout(bomb(), 999_999_999)
        assertEquals(BombStage.EXPLODED, s.stage)
        assertEquals("O tempo acabou", s.why)
        assertTrue(Bomb.over(s)!!.chips.values.all { it == 1 })
    }

    @Test fun focusMovesBetweenModulesAndLeavingAHeldButtonLetsGo() {
        var s = bomb(3, level = 2)
        val n = s.modules.size
        repeat(n) { s = host(s, HostCmd.RIGHT) }
        assertEquals(0, s.sel)
        s = host(s, HostCmd.LEFT); assertEquals(n - 1, s.sel)
        s = host(s, HostCmd.CONTINUE); assertEquals(n - 1, s.focus)
        s = host(s, HostCmd.SKIP); assertNull(s.focus)
    }

    // --- Every bomb can be defused -------------------------------------------------------------------------

    /** Solves a bomb using only the rules, driving it with remote commands: what a perfect team would do. */
    private fun solve(start: BombState): BombState {
        var s = start
        var now = 1_000L
        while (s.stage == BombStage.PLAY) {
            val i = s.modules.indexOfFirst { !it.solved }
            while (s.sel != i) s = host(s, HostCmd.RIGHT, now)
            s = host(s, HostCmd.CONTINUE, now)
            when (val m = s.modules[i]) {
                is Wires -> { val target = WireRules.correct(m.wires, s.edge); repeat(target) { s = host(s, HostCmd.DOWN, now) }; s = host(s, HostCmd.CONTINUE, now) }
                is PushButton -> {
                    s = host(s, HostCmd.CONTINUE, now)
                    if (ButtonRules.shouldHold(m, s.edge)) {
                        val d = ButtonRules.digitFor(ButtonRules.stripOf(m)).toString()
                        now = (now + 2_000..s.deadline!! - 1_000).first { t -> val left = ceil((s.deadline!! - t) / 1000.0).toInt(); "%d:%02d".format(left / 60, left % 60).contains(d) }
                    } else now += 200
                    s = host(s, HostCmd.CONTINUE, now)
                }
                is Symbols -> m.order.forEach { g -> val want = m.shown.indexOf(g); while (s.cursor != want) s = host(s, if (s.cursor % 2 != want % 2) (if (want % 2 == 1) HostCmd.RIGHT else HostCmd.LEFT) else (if (want > s.cursor) HostCmd.DOWN else HostCmd.UP), now); s = host(s, HostCmd.CONTINUE, now) }
                is Maze -> {
                    val open = Mazes.open(m.id)
                    val prev = HashMap<Int, Int>(); val q = ArrayDeque(listOf(m.pos)); prev[m.pos] = -1
                    while (q.isNotEmpty()) { val c = q.removeFirst(); open[c].orEmpty().forEach { if (it !in prev) { prev[it] = c; q.addLast(it) } } }
                    val path = generateSequence(m.end) { prev[it]?.takeIf { p -> p >= 0 } }.toList().reversed()
                    path.zipWithNext().forEach { (a, b) -> s = host(s, when (b - a) { 1 -> HostCmd.RIGHT; -1 -> HostCmd.LEFT; Mazes.SIZE -> HostCmd.DOWN; else -> HostCmd.UP }, now) }
                }
            }
        }
        return s
    }

    @Test fun everyGeneratedBombCanBeDefusedWithoutAStrikeFromTheRulesAlone() {
        for (level in 0..4) for (seed in 1..120) {
            val s = solve(bomb(3, level, seed))
            assertEquals("level $level seed $seed: ${s.why}", BombStage.WON, s.stage)
            assertEquals(0, s.strikes)
        }
    }

    @Test fun aDefusedBombPaysEveryoneAndNamesTheDefuser() {
        val s = solve(bomb(3, 1, 4))
        val o = Bomb.over(s)!!
        assertTrue(o.chips.values.all { it == 4 })
        assertEquals("DEFUSED", o.highlights.single().kind); assertEquals(s.defuser, o.highlights.single().who)
    }

    // --- Who sees what -------------------------------------------------------------------------------------

    @Test fun theDefuserNeverSeesTheManualAndTheExpertsSplitIt() {
        val s = bomb(4, 1, 2)
        val json = SocialJson.encodeToString(PhoneView.serializer(), Bomb.phone(s, s.defuser))
        Bomb.secrets(s, s.defuser).forEach { assertFalse(it, json.contains(it)) }
        val experts = s.players.map { it.id }.filter { it != s.defuser }
        val pages = experts.map { e -> (Bomb.phone(s, e).body as Widget.Manual).pages.map { it.title } }
        pages.forEach { assertEquals("Como funciona", it.first()) }
        assertEquals(setOf("Fios", "Botão", "Símbolos", "Labirintos"), pages.flatMap { it.drop(1) }.toSet())
        assertTrue("three experts share four pages", pages.all { it.size <= 3 })
        val duo = bomb(2, 1, 2)
        assertEquals(5, (Bomb.phone(duo, duo.players.first { it.id != duo.defuser }.id).body as Widget.Manual).pages.size)
    }

    @Test fun theDailyBombIsTheSameForEveryoneOnTheSameDay() {
        fun daily(day: Long) = Bomb.setup(Setup(people(3), mapOf("daily" to 1), Random(day.toInt()), day * 86_400_000L + 5_000, emptyList()))
        val a = daily(20_000); val b = daily(20_000).let { Bomb.setup(Setup(people(3), mapOf("daily" to 1), Random(99), 20_000L * 86_400_000L + 9_000, emptyList())) }
        assertEquals(a.modules, b.modules); assertEquals(a.edge, b.edge)
        assertNotEquals(a.modules, daily(20_001).modules)
    }

    @Test fun phonesAreToldWhenTheBombChanges() {
        val s = bomb(3, 0, 3)
        val ex = s.players.first { it.id != s.defuser }.id
        val before = Bomb.phone(s, ex)
        val after = Bomb.phone(s.copy(strikes = 1), ex)
        assertNotEquals(before.phase, after.phase)
        assertTrue(after.title!!.contains("1/3"))
    }
}
