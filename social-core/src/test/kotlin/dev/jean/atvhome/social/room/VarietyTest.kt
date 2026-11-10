package dev.jean.atvhome.social.room

import dev.jean.atvhome.social.FakeClock
import dev.jean.atvhome.social.games.mostlikely.MostLikely
import dev.jean.atvhome.social.games.mostlikely.MostLikelyState
import dev.jean.atvhome.social.kit.Suggest
import dev.jean.atvhome.social.kit.Variety
import dev.jean.atvhome.social.profile.Career
import dev.jean.atvhome.social.profile.Ledger
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.random.Random

class VarietyTest {
    private val clock = FakeClock()
    private val pack = (1..60).map { "Quem é mais provável de fazer a coisa número $it?" }

    private fun room(ledger: Ledger) = Room(clock = clock, content = { pack }, ledger = ledger, random = Random(9)).also { r ->
        repeat(3) { i -> (r.join(r.key, "Pessoa $i", null, "device-aaaaaaaaaaaa$i") as JoinResult.Joined) }
    }

    private fun Room.questions() = (snapshot.value.game!!.state as MostLikelyState).questions

    @Test fun twoGamesInARowNeverShareAQuestion() {
        val r = room(Ledger())
        assertNull(r.start(MostLikely, mapOf("rounds" to 0)))
        val first = r.questions()
        assertNull(r.again())
        val second = r.questions()
        assertTrue(first.isNotEmpty() && second.isNotEmpty())
        assertTrue("shared: ${first.intersect(second.toSet())}", first.intersect(second.toSet()).isEmpty())
    }

    @Test fun thePackStartsOverOnlyWhenTooLittleIsLeft() {
        val r = room(Ledger())
        val all = HashSet<String>()
        repeat(12) { r.start(MostLikely, mapOf("rounds" to 0)); all += r.questions() }
        // Twelve games of at least 5 questions used the pack more than once over, so it must have restarted rather than run dry
        assertTrue(all.size <= pack.size)
        assertNull("still able to start", r.start(MostLikely, mapOf("rounds" to 0)))
        assertTrue(r.questions().isNotEmpty())
    }

    @Test fun whatWasPlayedIsRememberedAcrossRestarts() {
        val file = File.createTempFile("ledger", ".json").also { it.deleteOnExit() }
        val a = room(Ledger(file)); a.start(MostLikely, mapOf("rounds" to 0)); val first = a.questions()
        val b = room(Ledger(file)); b.start(MostLikely, mapOf("rounds" to 0))
        assertTrue(first.intersect(b.questions().toSet()).isEmpty())
    }

    @Test fun usedFindsTheEntriesAGameHolds() {
        val used = Variety.used(listOf("C|Qual é o filme de {nome}?|a|b", "Pergunta que não saiu"), "texto Qual é o filme de {nome}? mais texto")
        assertEquals(1, used.size)
        assertEquals(Variety.key("C|Qual é o filme de {nome}?|a|b"), Variety.key(used.single()))
    }

    @Test fun theSuggestionLeansTowardWhatTheTableHasNotPlayed() {
        val people = listOf(
            Career("d1", "Ana", counters = mapOf("played:mostlikely" to 8), lastByGame = mapOf("mostlikely" to 1_000_000L)),
            Career("d2", "Bia", counters = mapOf("played:mostlikely" to 5)),
        )
        val games = dev.jean.atvhome.social.games.GameCatalog.all.filter { it.info.id in setOf("mostlikely", "frequency", "couple") }
        val ordered = Suggest.order(games, people, emptySet(), 2_000_000L, Random(1)).map { it.info.id }
        assertEquals("the one they play all the time is last", "mostlikely", ordered.last())
        assertEquals(2, Suggest.newcomers("frequency", people))
        assertEquals("never what was just played tonight", "frequency", Suggest.order(games.filter { it.info.id != "couple" }, people, setOf("frequency"), 2_000_000L, Random(1)).last().info.id)
    }
}
