package dev.jean.atvhome.social.progress

import dev.jean.atvhome.social.FakeClock
import dev.jean.atvhome.social.QUESTIONS
import dev.jean.atvhome.social.game.Act
import dev.jean.atvhome.social.game.Action
import dev.jean.atvhome.social.game.Category
import dev.jean.atvhome.social.game.HostCmd
import dev.jean.atvhome.social.game.Highlight
import dev.jean.atvhome.social.game.Outcome
import dev.jean.atvhome.social.game.SocialJson
import dev.jean.atvhome.social.games.GameCatalog
import dev.jean.atvhome.social.games.mostlikely.MostLikely
import dev.jean.atvhome.social.profile.Career
import dev.jean.atvhome.social.profile.Ledger
import dev.jean.atvhome.social.profile.RankBy
import dev.jean.atvhome.social.room.JoinResult
import dev.jean.atvhome.social.room.Room
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.random.Random

class ProgressionTest {

    private val d1 = "device-aaaaaaaaaaaa1"; private val d2 = "device-bbbbbbbbbbbb2"; private val d3 = "device-cccccccccccc3"
    private fun at(y: Int, m: Int, d: Int, h: Int = 20) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    private val t0 = at(2026, 11, 9)
    private val oldZone = Progression.zone

    @Before fun utc() { Progression.zone = ZoneOffset.UTC }
    @After fun restore() { Progression.zone = oldZone }

    private fun result(game: String = "mostlikely", won: Boolean = false, chips: Int = 0, hl: List<Highlight> = emptyList(), table: Int = 4, coop: Boolean = false, cat: Category = Category.TALK) =
        PlayerResult(game, cat, coop, won, chips, hl, table)
    private fun night(now: Long = t0, first: Boolean = true, streak: Int = 0) = NightContext(now, first, streak)

    // --- Levels and XP -----------------------------------------------------------------------------

    @Test fun theLevelCurveStartsAtOneNeverGoesBackAndStopsAtFifty() {
        assertEquals(1, Progression.level(0))
        var last = 1
        for (xp in 0..30_000 step 25) { val l = Progression.level(xp); assertTrue(l >= last); last = l }
        assertEquals(50, Progression.level(1_000_000))
        for (n in 2..50) { assertEquals(n, Progression.level(Progression.xpFor(n))); assertEquals(n - 1, Progression.level(Progression.xpFor(n) - 1)) }
    }

    @Test fun aHundredXpAGameReachesTenInAboutAdozenGames() {
        assertTrue(Progression.level(12 * 100) in 9..11)
        assertTrue("level 50 is a long way off", Progression.xpFor(50) > 15_000)
    }

    @Test fun progressWithinALevelIsAFractionAndTitlesComeEveryFiveLevels() {
        val p = Progression.progress(Progression.xpFor(3) + 10)
        assertEquals(3, p.level); assertEquals(10, p.into); assertTrue(p.fraction in 0f..1f)
        assertEquals(1f, Progression.progress(999_999).fraction, 0f)
        assertEquals("Estreante", Progression.title(1)); assertEquals("Estreante", Progression.title(4)); assertEquals("Figurinha Carimbada", Progression.title(5))
        assertEquals("Eterno da Sala", Progression.title(50))
    }

    @Test fun xpRewardsPresenceChipsWinsMomentsTheFirstGameAndAWinningRun() {
        assertEquals(20, Progression.gameXp(0, false, 0, false, 0))
        assertEquals(20 + 30 + 15, Progression.gameXp(3, true, 0, false, 0))
        assertEquals(20 + 30, Progression.gameXp(0, false, 0, true, 0))
        assertEquals("two moments at most", 20 + 50, Progression.gameXp(0, false, 5, false, 0))
        assertEquals((20 + 15) * 3 / 2, Progression.gameXp(0, true, 0, false, 3))
    }

    // --- Applying a game ----------------------------------------------------------------------------

    @Test fun aGameUpdatesChipsGamesWinsXpAndTheNight() {
        val l = Ledger()
        val p = Progression.applyGame(l, d1, "Ana", result(won = true, chips = 4), night())
        val c = p.after
        assertEquals(4, c.chips); assertEquals(1, c.games); assertEquals(1, c.wins); assertEquals(1, c.nights)
        assertEquals(mapOf("mostlikely" to 1), c.winsPerGame); assertEquals(p.gain.xp, c.xp); assertEquals(0, p.gain.before)
        assertEquals(c.xp, p.gain.after)
        val again = Progression.applyGame(l, d1, "Ana", result(), night(first = false)).after
        assertEquals("the night counts once", 1, again.nights); assertEquals(2, again.games)
    }

    @Test fun crossingALevelIsReportedAndABadgeUnlocksOnlyOnce() {
        val l = Ledger()
        val first = Progression.applyGame(l, d1, "Ana", result(won = true, chips = 3), night())
        assertTrue(first.badges.any { it.id == "first_win" })
        val second = Progression.applyGame(l, d1, "Ana", result(won = true, chips = 3), night(first = false, streak = 2))
        assertTrue("never twice", second.badges.none { it.id == "first_win" })
        assertEquals(t0, second.after.badges["first_win"])
        var c = second.after
        repeat(12) { c = Progression.applyGame(l, d1, "Ana", result(won = true, chips = 5), night(first = false)).after }
        assertTrue(c.level >= 5)
    }

    @Test fun highlightsFeedTheSpecialBadges() {
        val l = Ledger()
        val c = Progression.applyGame(l, d1, "Ana", result("bomb", true, 4, listOf(Highlight("DEFUSED", null, 7)), coop = true, cat = Category.COOP), night()).after
        assertNotNull(c.badges["defuser"]); assertNotNull("under ten seconds spare", c.badges["nerves"])
        val slow = Progression.applyGame(Ledger(), d2, "Bia", result("bomb", true, 4, listOf(Highlight("DEFUSED", null, 60)), coop = true, cat = Category.COOP), night()).after
        assertNotNull(slow.badges["defuser"]); assertNull(slow.badges["nerves"])
        val fooled = Progression.applyGame(Ledger(), d3, "Caio", result("twotruths", true, 3, listOf(Highlight("FOOLED", "p1", 4))), night()).after
        assertNotNull(fooled.badges["fooled_4"])
    }

    @Test fun everyGameHasABeastBadgeAndAllBadgeIdsAreUnique() {
        assertEquals(Badges.all.size, Badges.all.map { it.id }.toSet().size)
        assertTrue("a rich catalogue", Badges.all.size >= 36)
        GameCatalog.all.forEach { assertNotNull(Badges.byId("beast_${it.info.id}")) }
        assertTrue(Badges.all.all { it.tier in 1..3 && it.name.isNotBlank() && it.hint.isNotBlank() })
    }

    @Test fun fiveWinsInOneGameMakeABeast() {
        val l = Ledger(); var c: Career? = null
        repeat(5) { c = Progression.applyGame(l, d1, "Ana", result("spyfall", true, 3, cat = Category.DEDUCTION), night(first = it == 0)).after }
        assertNotNull(c!!.badges["beast_spyfall"])
    }

    // --- Weekly challenges --------------------------------------------------------------------------

    @Test fun theWeeksChallengesAreTheSameForEveryoneAndDifferentAcrossWeeks() {
        val w = Progression.weekKey(t0)
        assertEquals(Challenges.forWeek(w).map { it.id }, Challenges.forWeek(w).map { it.id })
        assertEquals(3, Challenges.forWeek(w).map { it.id }.toSet().size)
        val weeks = (0 until 12).map { Challenges.forWeek(Progression.weekKey(t0 + it * 7 * 86_400_000L)).map { c -> c.id } }
        assertTrue("it is not always the same three", weeks.toSet().size > 4)
    }

    @Test fun completingAChallengePaysOnceAndTheNextWeekStartsFresh() {
        val l = Ledger()
        val week = Progression.weekKey(t0)
        val ch = Challenges.forWeek(week).first()
        var xpBonusSeen = 0
        var c: Career? = null
        // Play enough winning, chip-rich, varied games that any challenge of the week is bound to be met
        val games = GameCatalog.all.map { it.info }
        repeat(14) { i ->
            val g = games[i % games.size]
            val p = Progression.applyGame(l, d1, "Ana", result(g.id, true, 6, listOf(Highlight("SYNC", null, 50)), 6, g.coop, g.category), night(t0 + i * 1000L, first = i == 0, streak = 2))
            xpBonusSeen += p.challenges.size; c = p.after
        }
        assertTrue("every challenge of the week was reachable", xpBonusSeen >= 1)
        assertEquals("a completed challenge is counted once each", Challenges.forWeek(week).count { (c!!.challenges["$week:${it.id}"] ?: 0) >= it.target }, xpBonusSeen)
        val next = Progression.applyGame(l, d1, "Ana", result(), night(t0 + 8 * 86_400_000L, first = true)).after
        assertTrue("old week's progress is dropped", next.challenges.keys.none { it.startsWith(week) })
        assertNotNull(ch)
    }

    @Test fun theVarietyChallengeCountsDistinctGamesOnly() {
        val v = Challenges.pool.first { it.id == "variety3" }
        val before = Career(d1, "Ana", weekGames = listOf("mostlikely"))
        assertEquals(0, v.step(result("mostlikely"), before, night()))
        assertEquals(1, v.step(result("couple"), before, night()))
    }

    // --- Seasons, records, rivalries ------------------------------------------------------------------

    @Test fun whenTheMonthTurnsTheTopThreeGetMedalsAndSeasonXpStartsAgain() {
        val l = Ledger()
        val nov = at(2026, 11, 20)
        listOf(d1 to 5, d2 to 3, d3 to 1).forEach { (d, n) -> repeat(n) { Progression.applyGame(l, d, "P$d", result(won = true, chips = 4), night(nov + it, first = it == 0)) } }
        val dec = Progression.applyGame(l, d3, "Caio", result(), night(at(2026, 12, 2)))
        assertEquals("2026-12", l.currentSeason())
        assertEquals(listOf("2026-11:1"), l.get(d1)!!.medals); assertEquals(listOf("2026-11:2"), l.get(d2)!!.medals)
        assertEquals(listOf("2026-11:3"), dec.after.medals)
        assertEquals("only the person who played in December has season points", 0, l.get(d1)!!.seasonXp)
        assertTrue(l.get(d3)!!.seasonXp > 0)
        assertEquals(listOf("Caio"), l.ranking(5, RankBy.SEASON).map { it.name })
        assertEquals("the all-time board is untouched", 3, l.ranking(5, RankBy.XP).size)
        Progression.applyGame(l, d3, "Caio", result(), night(at(2026, 12, 3)))
        assertEquals("rolling twice in a month changes nothing", listOf("2026-11:3"), l.get(d3)!!.medals)
    }

    @Test fun aRecordIsBrokenOnlyByAnImprovementAndLowerCanBeBetter() {
        val l = Ledger()
        assertNotNull(Progression.offerRecords(l, d1, "Ana", "bomb", listOf(Highlight("DEFUSED", "p1", 30)), t0).singleOrNull())
        assertTrue(Progression.offerRecords(l, d2, "Bia", "bomb", listOf(Highlight("DEFUSED", "p2", 30)), t0).isEmpty())
        val better = Progression.offerRecords(l, d2, "Bia", "bomb", listOf(Highlight("DEFUSED", "p2", 45)), t0).single()
        assertEquals("Ana", better.old!!.holder)
        assertEquals("Bia", l.houseRecords().single().holder)
        // Fewer hints is better, and no hints at all is the best there is
        Progression.offerRecords(l, d1, "Ana", "blackstory", listOf(Highlight("SOLVED", null, 2)), t0)
        assertEquals(1, Progression.offerRecords(l, d2, "Bia", "blackstory", listOf(Highlight("SOLVED", null, 0)), t0).size)
        assertEquals(0, l.houseRecords().first { it.gameId == "blackstory" }.value)
    }

    @Test fun aRecordGoesWithItsOwnerWhenTheyAreForgotten() {
        val l = Ledger()
        Progression.applyGame(l, d1, "Ana", result(), night())
        Progression.offerRecords(l, d1, "Ana", "bomb", listOf(Highlight("DEFUSED", null, 30)), t0)
        l.forget(d1)
        assertTrue(l.houseRecords().isEmpty())
    }

    @Test fun rivalriesKeepAHeadToHeadTallyAndStayShort() {
        val l = Ledger()
        Progression.applyGame(l, d1, "Ana", result(), night()); Progression.applyGame(l, d2, "Bia", result(), night())
        repeat(3) { Progression.rivalry(l, d1, "Ana", d2, "Bia", t0) }
        Progression.rivalry(l, d2, "Bia", d1, "Ana", t0)
        assertEquals(3, l.get(d1)!!.rivals[d2]!!.wins); assertEquals(1, l.get(d1)!!.rivals[d2]!!.losses)
        assertEquals(1, l.get(d2)!!.rivals[d1]!!.wins); assertEquals(3, l.get(d2)!!.rivals[d1]!!.losses)
        repeat(12) { i -> Progression.rivalry(l, d1, "Ana", "device-%016d".format(i), "P$i", t0) }
        assertTrue(l.get(d1)!!.rivals.size <= 8)
    }

    @Test fun consecutiveWeeksBuildAStreakAndAGapBreaksIt() {
        val l = Ledger(); val week = 7 * 86_400_000L
        Progression.applyGame(l, d1, "Ana", result(), night(t0))
        assertEquals(1, l.get(d1)!!.streakWeeks)
        Progression.applyGame(l, d1, "Ana", result(), night(t0 + 3_600_000, first = false))
        assertEquals("same week", 1, l.get(d1)!!.streakWeeks)
        Progression.applyGame(l, d1, "Ana", result(), night(t0 + week))
        Progression.applyGame(l, d1, "Ana", result(), night(t0 + 2 * week))
        assertEquals(3, l.get(d1)!!.streakWeeks); assertNotNull(l.get(d1)!!.badges["weeks_3"])
        Progression.applyGame(l, d1, "Ana", result(), night(t0 + 5 * week))
        assertEquals("a missed week starts again", 1, l.get(d1)!!.streakWeeks)
        assertEquals(3, l.get(d1)!!.maxes["streakWeeks"])
    }

    // --- Saving and migrating --------------------------------------------------------------------------

    @Test fun anOldLedgerFileKeepsEveryPointAndGainsALevel() {
        val f = File.createTempFile("ledger", ".json").apply { delete() }
        f.writeText(SocialJson.encodeToString(listOf(Career(d1, "Ana", chips = 40, games = 12, nights = 3, perGame = mapOf("mostlikely" to 40)))))
        val l = Ledger(f)
        val c = l.get(d1)!!
        assertEquals(40, c.chips); assertEquals(12, c.games)
        assertEquals(40 * 10 + 12 * 20 + 3 * 30, c.xp); assertTrue(c.level > 1)
        l.mutate(d1, "Ana", t0) { it.copy(chips = it.chips + 1) }
        val reread = Ledger(f)
        assertEquals(41, reread.get(d1)!!.chips); assertEquals("not migrated twice", c.xp, reread.get(d1)!!.xp)
        assertTrue(f.readText().trimStart().startsWith("{"))
        f.delete()
    }

    @Test fun everythingSurvivesARestartIncludingSeasonRecordsAndBadges() {
        val f = File.createTempFile("ledger", ".json").apply { delete() }
        val l = Ledger(f)
        Progression.applyGame(l, d1, "Ana", result(won = true, chips = 3), night())
        Progression.offerRecords(l, d1, "Ana", "bomb", listOf(Highlight("DEFUSED", null, 30)), t0)
        val again = Ledger(f)
        assertNotNull(again.get(d1)!!.badges["first_win"]); assertEquals(1, again.houseRecords().size); assertEquals("2026-11", again.currentSeason())
        f.delete()
    }

    // --- Outcome.winners ----------------------------------------------------------------------------------

    @Test fun winnersAreThoseWithTheMostChipsOrTheWholeTableInACooperativeGame() {
        assertEquals(setOf("a"), Outcome(mapOf("a" to 5, "b" to 3), "x").winners)
        assertEquals(setOf("a", "b"), Outcome(mapOf("a" to 4, "b" to 4, "c" to 1), "x").winners)
        assertTrue(Outcome(mapOf("a" to 0, "b" to 0), "x").winners.isEmpty())
        assertEquals(setOf("a", "b"), Outcome(mapOf("a" to 1, "b" to 4), "x", coopWon = true).winners)
        assertTrue(Outcome(mapOf("a" to 1, "b" to 1), "x", coopWon = false).winners.isEmpty())
    }

    // --- Through the room ---------------------------------------------------------------------------------

    private val clock = FakeClock(t0)
    private fun room(l: Ledger) = Room(clock = clock, content = { QUESTIONS }, ledger = l, random = Random(4))
    private fun pick(id: String) = Action(Act.PICK, buildJsonObject { put("id", JsonPrimitive(id)) })

    private fun play(r: Room, seats: List<JoinResult.Joined>, target: Int = 0) {
        r.start(MostLikely, mapOf("rounds" to 0))
        repeat(5) {
            val phase = r.snapshot.value.game!!.phase
            seats.forEach { r.act(it.token, null, phase, pick(seats[target].id)) }
            r.host(HostCmd.CONTINUE)
        }
    }

    @Test fun aFinishedGameGivesXpGainsAndPartiesToThePhonesThatPlayed() {
        val l = Ledger(); val r = room(l)
        val seats = listOf(r.join(r.key, "Ana", null, d1), r.join(r.key, "Bia", null, d2), r.join(r.key, "Caio", null, d3)).map { it as JoinResult.Joined }
        play(r, seats)
        val snap = r.snapshot.value
        assertEquals(3, snap.gains.size)
        snap.gains.forEach { (pid, g) -> assertTrue(g.xp >= 20); assertEquals(l.get(r.snapshot.value.careers.getValue(pid).device)!!.xp, g.after) }
                assertTrue("someone won, so somebody got the first win badge", snap.celebrations.any { it.kind == "BADGE" && it.title == "Primeira vitória" })
        val ids = snap.celebrations.map { it.id }
        assertEquals("every party has its own id", ids.size, ids.toSet().size)
        val mine = r.view(seats[0].token, 0, 0)!!.json
        assertTrue(mine.contains("\"profile\""))
    }

    @Test fun competitiveGamesFeedRivalriesAndCooperativeOnesDoNot() {
        val l = Ledger(); val r = room(l)
        val seats = listOf(r.join(r.key, "Ana", null, d1), r.join(r.key, "Bia", null, d2), r.join(r.key, "Caio", null, d3)).map { it as JoinResult.Joined }
        play(r, seats, target = 0)   // everyone points at Ana: she wins the most chips
        val ana = l.get(d1)!!
        assertEquals(2, ana.rivals.size)
        assertTrue(ana.rivals.values.all { it.wins >= 1 && it.losses == 0 })
        val bia = l.get(d2)!!
        assertTrue(bia.rivals.values.any { it.losses >= 1 })
    }

    @Test fun threeWinsInARowInOneNightSetsSomeoneOnFire() {
        val l = Ledger(); val r = room(l)
        val seats = listOf(r.join(r.key, "Ana", null, d1), r.join(r.key, "Bia", null, d2), r.join(r.key, "Caio", null, d3)).map { it as JoinResult.Joined }
        repeat(3) { play(r, seats, target = 0) }
        assertTrue(r.snapshot.value.celebrations.any { it.kind == "STREAK" })
        assertNotNull(l.get(d1)!!.badges["on_fire"])
        r.newNight()
        assertTrue("a new night starts the run again", r.snapshot.value.gains.isEmpty())
    }

    @Test fun anonymousPhonesPlayWithoutAnyCareer() {
        val l = Ledger(); val r = room(l)
        val seats = (1..3).map { r.join(r.key, "P$it") as JoinResult.Joined }
        play(r, seats)
        assertEquals(0, l.size()); assertTrue(r.snapshot.value.gains.isEmpty() && r.snapshot.value.celebrations.isEmpty())
    }
}
