package dev.jean.atvhome.social.games

import dev.jean.atvhome.social.FakeClock
import dev.jean.atvhome.social.people
import dev.jean.atvhome.social.game.*
import dev.jean.atvhome.social.games.film5.*
import dev.jean.atvhome.social.room.Room
import dev.jean.atvhome.social.room.JoinResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class Film5Test {

    private val pack = (1..12).map { i -> "k$i|Titulo Numero $i|${2000 + i}|Drama, Ação|${100 + i}|Uma frase sobre Titulo Numero $i|Ana Atriz, Beto Ator, Caio Extra|Heroi $i|Numero $i e o titulo que muda a vida de todos nós numa grande cidade.|http://x/$i.jpg|Netflix" }

    private fun choose(i: Int) = Action(Act.CHOOSE, buildJsonObject { put("key", JsonPrimitive("$i")) })
    private fun start(n: Int = 3) = Film5.setup(Setup(people(n), mapOf("films" to 0), Random(2), 0, pack))

    @Test fun theMaskHidesEveryWordOfTheTitleWhateverTheAccentOrCase() {
        assertEquals("▒▒▒ de ▒▒▒ e o ▒▒▒", Film5.mask("Pão de Queijo e o PAO", "Pão de Queijo"))
        assertFalse(Film5.mask("O Senhor dos Anéis: a sociedade", "O Senhor dos Anéis").contains("Senhor"))
        val f = Film.parse(pack[0])!!
        Film5.clueTexts(f).take(4).forEach { assertFalse(it, it.contains("Titulo")) }
    }

    @Test fun cluesGrowFromTheYearToTheLettersOfTheTitle() {
        val f = Film.parse(pack[0])!!
        val c = Film5.clueTexts(f)
        assertEquals(5, c.size)
        assertTrue(c[0].contains("2001") && c[0].contains("Drama"))
        assertTrue(c[2].contains("Ana Atriz e Beto Ator"))
        assertTrue(c[4].startsWith("O título: T"))
    }

    @Test fun eachFilmOffersSixDistinctTitlesIncludingTheRightOne() {
        val s = start()
        assertEquals(3, s.films.size)
        s.options.forEachIndexed { i, o -> assertEquals(6, o.size); assertEquals(6, o.toSet().size); assertTrue(s.films[i].title in o) }
    }

    @Test fun answeringEarlierPaysMoreAndAWrongAnswerPaysNothing() {
        var s = start(3)
        val right = s.rightIndex; val wrong = (right + 1) % 6
        val (a, b, c) = s.players.map { it.id }
        s = (Film5.act(s, a, choose(right), 0) as Step.Ok).state            // clue 1: 5 points
        s = Film5.host(s, HostCmd.CONTINUE, 0).let { Film5.host(it, HostCmd.CONTINUE, 0) }  // clue 3
        s = (Film5.act(s, b, choose(right), 0) as Step.Ok).state            // clue 3: 3 points
        assertTrue(Film5.act(s, a, choose(right), 0) is Step.Rejected)       // only once
        s = (Film5.act(s, c, choose(wrong), 0) as Step.Ok).state
        assertEquals(FiStage.REVEAL, s.stage)
        assertEquals(mapOf(a to 5, b to 3), s.scores)
    }

    @Test fun theLastClueEndsTheFilmAndTheGameEndsAfterTheLastFilm() {
        var s = start()
        repeat(3) { s = Film5.host(s, HostCmd.SKIP, 0); s = Film5.host(s, HostCmd.CONTINUE, 0) }
        assertEquals(FiStage.DONE, s.stage)
        assertEquals("nobody answered", "Ninguém acertou nenhum filme", Film5.over(s)!!.headline)
        var t = start()
        repeat(4) { t = Film5.host(t, HostCmd.CONTINUE, 0) }
        assertEquals("the fifth clue is the last", 4, t.clue)
        assertEquals(FiStage.REVEAL, Film5.host(t, HostCmd.CONTINUE, 0).stage)
    }

    @Test fun earlyCorrectAnswersEarnTheCinephileMoment() {
        var s = start(2)
        s = (Film5.act(s, s.players[0].id, choose(s.rightIndex), 0) as Step.Ok).state
        s = (Film5.act(s, s.players[1].id, choose((s.rightIndex + 1) % 6), 0) as Step.Ok).state
        repeat(2) { s = Film5.host(s, HostCmd.SKIP, 0).let { r -> Film5.host(r, HostCmd.CONTINUE, 0) } }
        s = s.copy(stage = FiStage.DONE)
        assertEquals(listOf("CINEPHILE"), Film5.over(s)!!.highlights.map { it.kind })
    }

    @Test fun withoutEnoughFilmsTheGameSaysWhyAndNeverStarts() {
        assertNotNull(Film5.unavailable(emptyList()))
        assertNotNull(Film5.unavailable(pack.take(5)))
        assertNull(Film5.unavailable(pack))
        val room = Room(clock = FakeClock(), content = { emptyList() })
        (1..2).forEach { room.join(room.key, "P$it") }
        val why = room.start(Film5)
        assertTrue(why!!, why.contains("internet"))
        assertNull(room.snapshot.value.game)
    }
}
