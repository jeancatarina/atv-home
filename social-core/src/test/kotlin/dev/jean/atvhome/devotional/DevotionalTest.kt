package dev.jean.atvhome.devotional

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

class DevotionalTest {
    private val today = LocalDate.of(2026, 11, 15)

    private fun board(): Pair<Devotional, Pair<Person, Person>> {
        val d = Devotional()
        return d to (d.addPerson("Jean")!! to d.addPerson("Ana")!!)
    }

    @Test fun streakCountsConsecutiveDaysAndSurvivesUntilTonight() {
        val (d, p) = board()
        d.mark(p.first.id, today.minusDays(2), today, true)
        d.mark(p.first.id, today.minusDays(1), today, true)
        assertEquals(2, d.standings(today).first { it.person.name == "Jean" }.streak)
        d.mark(p.first.id, today, today, true)
        assertEquals(3, d.standings(today).first { it.person.name == "Jean" }.streak)
    }

    @Test fun aMissedDayBreaksTheStreak() {
        val (d, p) = board()
        d.mark(p.first.id, today.minusDays(2), today, true)
        assertEquals(0, d.standings(today).first { it.person.name == "Jean" }.streak)
        assertEquals(1, d.standings(today).first { it.person.name == "Jean" }.best)
    }

    @Test fun togetherCountsOnlyDaysEveryoneDidIt() {
        val (d, p) = board()
        listOf(0L, 1L, 2L).forEach { d.mark(p.first.id, today.minusDays(it), today, true) }
        d.mark(p.second.id, today.minusDays(1), today, true)
        d.mark(p.second.id, today, today, true)
        // Both did today and yesterday; only Jean did the day before
        assertEquals(2, d.together(today))
        // Today still open for Ana: the run up to yesterday stands
        d.mark(p.second.id, today, today, false)
        assertEquals(1, d.together(today))
    }

    @Test fun pendingListsWhoStillOwesToday() {
        val (d, p) = board()
        assertEquals(listOf("Jean", "Ana"), d.pending(today).map { it.name })
        d.mark(p.first.id, today, today, true)
        assertEquals(listOf("Ana"), d.pending(today).map { it.name })
        d.mark(p.second.id, today, today, true)
        assertTrue(d.pending(today).isEmpty())
        // A new day starts with everyone pending again
        assertEquals(2, d.pending(today.plusDays(1)).size)
    }

    @Test fun pointsGrowInsideARunAndWithTheTogetherBonus() {
        val (d, p) = board()
        d.mark(p.first.id, today.minusDays(1), today, true)
        d.mark(p.first.id, today, today, true)
        // 10 for the first day, 12 for the second (a run of two)
        assertEquals(22, d.points(p.first.id))
        d.mark(p.second.id, today, today, true)
        // Today everyone did it: +5 to each
        assertEquals(27, d.points(p.first.id))
        assertEquals(15, d.points(p.second.id))
        assertEquals(42, d.couplePoints())
    }

    @Test fun aRunsBonusStopsAfterSevenDays() {
        val days = (0L..9L).map { today.minusDays(it) }.toSet()
        // Days 1..7 add 0,2,..,12; days 8..10 stay at the cap of 12
        assertEquals(10 * 10 + (0 + 2 + 4 + 6 + 8 + 10 + 12) + 3 * 12, Game.points(days, null))
    }

    @Test fun levelsAndTreeStagesFollowTheThresholds() {
        assertEquals("Início", Game.personLevel(0).name)
        assertEquals(30, Game.personLevel(0).nextAt)
        assertEquals("Constância", Game.personLevel(30).name)
        assertEquals("Plenitude", Game.personLevel(5000).name)
        assertNull(Game.personLevel(5000).nextAt)
        assertEquals(0, Game.tree(39).index)
        assertEquals(1, Game.tree(40).index)
        assertEquals(5, Game.tree(99999).index)
    }

    @Test fun theWeekDuelHasAtMostOneLeader() {
        val (d, p) = board()
        val monday = Game.weekStart(today)
        assertNull(d.weekLeader(today))
        d.mark(p.first.id, today, today, true)
        assertEquals("Jean", d.weekLeader(today)?.name)
        d.mark(p.second.id, today, today, true)
        assertNull(d.weekLeader(today))
        assertEquals(1, d.weekCount(p.first.id, today))
        // The week restarts on Monday
        assertEquals(java.time.DayOfWeek.MONDAY, monday.dayOfWeek)
        assertEquals(0, d.weekCount(p.first.id, monday.plusDays(7)))
    }

    @Test fun aNudgeReachesOnlyWhoStillOwesTodayAndIsRateLimited() {
        val (d, p) = board()
        val t0 = 1_000_000L
        assertFalse(d.nudge(p.first.id, p.first.id, today, t0))
        assertTrue(d.nudge(p.first.id, p.second.id, today, t0))
        assertEquals("Jean", d.nudgedBy(p.second.id, today, t0 + 1000)?.name)
        // Same caller again within the cooldown is ignored; after it, allowed
        assertFalse(d.nudge(p.first.id, p.second.id, today, t0 + 60_000))
        assertTrue(d.nudge(p.first.id, p.second.id, today, t0 + Devotional.NUDGE_COOLDOWN_MS + 1))
        // It fades with time and ends once the person checks in
        assertNull(d.nudgedBy(p.second.id, today, t0 + Devotional.NUDGE_LIFETIME_MS + Devotional.NUDGE_COOLDOWN_MS + 2))
        d.mark(p.second.id, today, today, true)
        assertNull(d.nudgedBy(p.second.id, today, t0 + Devotional.NUDGE_COOLDOWN_MS + 2))
        assertFalse(d.nudge(p.first.id, p.second.id, today, t0 + 99_999_999))
    }

    @Test fun everyTreeStageDrawsAndGrows() {
        // Each stage reaches higher than the one before: the tree really grows
        val tops = (0 until TreeArt.STAGES).map { n ->
            TreeArt.stage(n).minOf { sh -> when (sh) { is Disc -> sh.cy - sh.r; is Block -> sh.y } }
        }
        assertEquals(tops.sortedDescending(), tops)
        assertTrue(tops.first() > tops.last())
        (0 until TreeArt.STAGES).forEach { assertTrue(TreeArt.svg(it).startsWith("<svg") && TreeArt.svg(it).endsWith("</svg>")) }
        assertTrue(TreeArt.stage(5).any { it.color == TreeArt.FRUIT })
        assertEquals(TreeArt.stage(5), TreeArt.stage(99))
    }

    @Test fun togetherNeedsTwoPeople() {
        val d = Devotional()
        val p = d.addPerson("Jean")!!
        d.mark(p.id, today, today, true)
        assertEquals(0, d.together(today))
    }

    @Test fun rankingPutsTheLongerStreakFirst() {
        val (d, p) = board()
        d.mark(p.second.id, today, today, true)
        d.mark(p.second.id, today.minusDays(1), today, true)
        d.mark(p.first.id, today, today, true)
        assertEquals("Ana", d.standings(today).first().person.name)
    }

    @Test fun futureAndOldDatesAreRefusedAndUndoWorks() {
        val (d, p) = board()
        assertFalse(d.mark(p.first.id, today.plusDays(1), today, true))
        assertFalse(d.mark(p.first.id, today.minusDays(3), today, true))
        assertTrue(d.mark(p.first.id, today, today, true))
        assertTrue(d.mark(p.first.id, today, today, false))
        assertEquals(0, d.standings(today).first { it.person.name == "Jean" }.total)
    }

    @Test fun peopleAreCappedAndNotDuplicated() {
        val d = Devotional()
        assertNotNull(d.addPerson("A")); assertNull(d.addPerson("a")); assertNull(d.addPerson("  "))
        d.addPerson("B"); d.addPerson("C"); d.addPerson("D")
        assertNull(d.addPerson("E"))
    }

    @Test fun storeSurvivesARestart() {
        val f = File.createTempFile("dev", ".json").apply { deleteOnExit() }
        val store = DevotionalStore(f)
        val d = Devotional(store.load(), onChange = store::save)
        val p = d.addPerson("Jean")!!
        d.mark(p.id, today, today, true)
        val again = Devotional(DevotionalStore(f).load())
        assertEquals(1, again.standings(today).single().streak)
    }

    @Test fun serverChecksInOverHttp() {
        val d = Devotional()
        val s = DevotionalServer(d, { "<html></html>" }, { today }, port = 0).apply { start(5000, false) }
        try {
            fun post(path: String, body: String): Pair<Int, String> {
                val c = URL("http://127.0.0.1:${s.listeningPort}$path").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
                val code = c.responseCode
                return code to (if (code == 200) c.inputStream else c.errorStream)?.readBytes()?.decodeToString().orEmpty()
            }
            assertEquals(200, post("/people", """{"name":"Ana"}""").first)
            val id = d.snapshot().people.single().id
            val (code, body) = post("/checkin", """{"id":"$id","done":true}""")
            assertEquals(200, code)
            assertTrue(body.contains("\"doneToday\":true"))
            assertEquals(400, post("/checkin", """{"id":"nobody"}""").first)
            // The couple's tree is served as an image, and a nudge shows up for the person called
            val img = URL("http://127.0.0.1:${s.listeningPort}/tree.svg?stage=3").openConnection() as HttpURLConnection
            assertEquals("image/svg+xml", img.contentType)
            assertTrue(img.inputStream.readBytes().decodeToString().contains("<circle"))
            post("/people", """{"name":"Jean"}""")
            val ids = d.snapshot().people.map { it.id }
            val (code2, body2) = post("/nudge", """{"from":"${ids[0]}","to":"${ids[1]}"}""")
            assertEquals(200, code2)
            assertTrue(body2.contains("\"nudgedBy\":\"Ana\""))
            // Same person, or someone who already did today, cannot be called
            assertEquals(400, post("/nudge", """{"from":"${ids[1]}","to":"${ids[1]}"}""").first)
            assertEquals(400, post("/nudge", """{"from":"${ids[1]}","to":"${ids[0]}"}""").first)
        } finally { s.stop() }
    }
}
