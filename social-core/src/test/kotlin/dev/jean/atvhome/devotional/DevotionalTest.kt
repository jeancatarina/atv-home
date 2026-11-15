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

    @Test fun theVerseChangesEveryDayAndRepeatsOnlyAfterTheWholeList() {
        val seen = (0L until Verses.all.size).map { Verses.of(today.plusDays(it)) }
        assertEquals(Verses.all.size, seen.toSet().size)
        assertEquals(Verses.of(today), Verses.of(today.plusDays(Verses.all.size.toLong())))
        assertTrue(Verses.all.all { it.text.isNotBlank() && it.ref.isNotBlank() })
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
        val d = Devotional(store.load(), store::save)
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
        } finally { s.stop() }
    }
}
