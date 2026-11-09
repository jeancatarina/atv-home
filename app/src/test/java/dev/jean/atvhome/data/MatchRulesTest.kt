package dev.jean.atvhome.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MatchRulesTest {

    private val deck = (1..30).map { "movie:$it" }

    @Test
    fun twoPeopleMatchOnTheFirstTitleBothLike() {
        val rules = MatchRules(deck)
        val emily = rules.join("Emily")
        val jean = rules.join("Jean")
        rules.vote(emily, "movie:3", true)
        assertNull(rules.result)
        rules.vote(jean, "movie:3", true)
        assertEquals(MatchOutcome("movie:3", unanimous = true, likes = 2, people = 2), rules.result)
    }

    @Test
    fun onePersonAloneNeverMatches() {
        val rules = MatchRules(deck)
        val me = rules.join("Jean")
        deck.forEach { rules.vote(me, it, true) }
        assertNull(rules.result)
    }

    @Test
    fun tenPeopleAllLikingOneTitleIsAMatch() {
        val rules = MatchRules(deck)
        val ids = (1..10).map { rules.join("P$it") }
        ids.forEachIndexed { i, id ->
            rules.vote(id, "movie:7", true)
            if (i < 9) assertNull(rules.result)
        }
        assertEquals(MatchOutcome("movie:7", true, 10, 10), rules.result)
    }

    @Test
    fun tenPeopleWithoutUnanimityGetTheMostLikedOnceEveryoneFinishes() {
        val rules = MatchRules(deck)
        val ids = (1..10).map { rules.join("P$it") }
        val random = Random(42)
        ids.forEachIndexed { i, id ->
            deck.forEach { key ->
                // movie:12 is liked by 7 of the 10; everything else at random but never unanimous
                val like = when {
                    key == "movie:12" -> i < 7
                    i == 9 -> false
                    else -> random.nextInt(10) < 3
                }
                rules.vote(id, key, like)
            }
            if (i < 9) assertNull("no result until everyone has finished", rules.result)
        }
        val result = rules.result!!
        assertEquals("movie:12", result.key)
        assertFalse(result.unanimous)
        assertEquals(7, result.likes)
        assertEquals(10, result.people)
    }

    @Test
    fun anotherMatchKeepsTheVotes() {
        val rules = MatchRules(deck)
        val a = rules.join("A")
        val b = rules.join("B")
        listOf("movie:2", "movie:5").forEach { rules.vote(a, it, true); rules.vote(b, it, true) }
        assertEquals("movie:2", rules.result!!.key)
        rules.next()
        assertEquals("movie:5", rules.result!!.key)
        assertTrue(rules.result!!.unanimous)
    }
}
