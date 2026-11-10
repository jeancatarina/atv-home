package dev.jean.atvhome.social.night

import dev.jean.atvhome.social.FakeClock
import dev.jean.atvhome.social.QUESTIONS
import dev.jean.atvhome.social.people
import dev.jean.atvhome.social.game.Act
import dev.jean.atvhome.social.game.Action
import dev.jean.atvhome.social.game.HostCmd
import dev.jean.atvhome.social.games.mostlikely.MostLikely
import dev.jean.atvhome.social.room.JoinResult
import dev.jean.atvhome.social.room.Room
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class NightTest {

    private val ps = people(5)

    @Test fun everyoneGetsAtLeastOneTrophy() {
        for (n in 2..12) {
            val players = people(n)
            val events = listOf(NightEvent("g", "FOOLED", "p1", 3))
            val awards = Ceremony.build(players, mapOf("p1" to 7, "p2" to 2), events)
            players.forEach { p -> assertTrue("n=$n ${p.id}", awards.any { it.who == p.id }) }
        }
    }

    @Test fun trophiesComeFromMemorableMomentsAndTheChampionIsLast() {
        val events = listOf(NightEvent("a", "FOOLED", "p2", 4), NightEvent("b", "UNANIMOUS", "p3"), NightEvent("c", "SPY_ESCAPED", "p1"))
        val awards = Ceremony.build(ps, mapOf("p1" to 9, "p2" to 4, "p3" to 1), events)
        assertEquals("Cara de Pau", awards.first { it.who == "p2" && it.key == "FOOLED" }.title)
        assertTrue(awards.first { it.key == "FOOLED" }.blurb.contains("4 pessoas"))
        assertEquals("CHAMPION", awards.last().key)
        assertEquals("p1", awards.last().who)
    }

    @Test fun theCeremonyIsDeterministicAndTiedChampionsShareIt() {
        val chips = mapOf("p1" to 5, "p2" to 5, "p3" to 1)
        val a = Ceremony.build(ps, chips, emptyList()); val b = Ceremony.build(ps, chips, emptyList())
        assertEquals(a, b)
        assertEquals(setOf("p1", "p2"), a.filter { it.key == "CHAMPION" }.map { it.who }.toSet())
    }

    @Test fun eventsForUnknownPeopleOrKindsAreIgnored() {
        val awards = Ceremony.build(ps, emptyMap(), listOf(NightEvent("x", "NOPE", "p1"), NightEvent("x", "FOOLED", "ghost", 2), NightEvent("x", "FOOLED", null, 2)))
        assertTrue(awards.none { it.key == "FOOLED" })
        assertEquals(ps.size, awards.map { it.who }.toSet().size)
    }

    @Test fun aGameNameEndingInAQuestionMarkGetsNoExtraPunctuation() {
        val used = mutableSetOf<String>()
        repeat(12) {
            listOf(Tone.FAMILY, Tone.TEASING).forEach { t ->
                val line = Presenter.gameStarting("Quem é Mais Provável?", t, used, Random(it))
                assertFalse(line, Regex("[?!][.:]").containsMatchIn(line))
                assertFalse("a sentence after ? starts in capitals: $line", Regex("[?!] \\p{Ll}").containsMatchIn(line))
                used += line
            }
        }
    }

    @Test fun theHostFillsInNamesAndNumbersAndNeverLeavesAPlaceholder() {
        for (tone in Tone.entries) repeat(30) { seed ->
            val line = Presenter.afterGame(listOf(NightEvent("g", "FOOLED", "p2", 4)), ps, tone, emptySet(), Random(seed))
            assertFalse(line, line.contains("{")); assertTrue(line, line.contains("Pessoa 2"))
            assertFalse(Presenter.gameStarting("Infiltrados", tone, emptySet(), Random(seed)).contains("{"))
        }
    }

    @Test fun theHostDoesNotRepeatALineWithinTheNight() {
        val said = mutableSetOf<String>()
        val random = Random(1)
        repeat(3) { said += Presenter.afterGame(listOf(NightEvent("g", "FOOLED", "p1", 2)), ps, Tone.FAMILY, said, random) }
        assertEquals(3, said.size)
    }

    @Test fun theToneChangesTheWords() {
        val e = listOf(NightEvent("g", "UNANIMOUS", "p1"))
        val family = (0..20).map { Presenter.afterGame(e, ps, Tone.FAMILY, emptySet(), Random(it)) }.toSet()
        val teasing = (0..20).map { Presenter.afterGame(e, ps, Tone.TEASING, emptySet(), Random(it)) }.toSet()
        assertTrue(family.intersect(teasing).isEmpty())
    }

    // --- Through the room ---------------------------------------------------------------------------

    private fun pick(id: String) = Action(Act.PICK, buildJsonObject { put("id", JsonPrimitive(id)) })

    @Test fun finishedGamesFeedTheNightAndClosingItShowsTrophiesOnEveryPhone() {
        val clock = FakeClock()
        val room = Room(clock = clock, content = { QUESTIONS }, random = Random(2))
        val seats = (1..3).map { room.join(room.key, "P$it") as JoinResult.Joined }
        room.start(MostLikely, mapOf("rounds" to 0))
        repeat(5) {
            val phase = room.snapshot.value.game!!.phase
            seats.forEach { room.act(it.token, null, phase, pick(seats[0].id)) }
            room.host(HostCmd.CONTINUE)
        }
        assertTrue(room.snapshot.value.events.any { it.kind == "UNANIMOUS" })
        room.closeNight()
        val snap = room.snapshot.value
        assertTrue(snap.awards.isNotEmpty())
        assertNull(snap.game)
        seats.forEach { s ->
            val json = room.view(s.token, 0, 0)!!.json
            assertTrue(json.contains("\"phase\":\"ceremony\""))
            assertTrue(json.contains("Seu troféu") || json.contains("Valeu por jogar"))
        }
        room.newNight()
        assertTrue(room.snapshot.value.awards.isEmpty() && room.snapshot.value.night.isEmpty() && room.snapshot.value.events.isEmpty())
        assertEquals(3, room.snapshot.value.players.size)
    }
}
