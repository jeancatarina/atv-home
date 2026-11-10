package dev.jean.atvhome.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun withoutUnanimityThereIsNoConsolationPrizeAndTheDeckKeepsGrowing() {
        val rules = MatchRules(deck)
        val ids = (1..10).map { rules.join("P$it") }
        // Everybody goes through the whole deck; movie:12 is liked by 7 of 10, nothing by all
        ids.forEachIndexed { i, id -> deck.forEach { key -> rules.vote(id, key, key == "movie:12" && i < 7) } }
        assertNull("one title 7 of 10 liked is not a match", rules.result)
        assertEquals(0, rules.remaining())
        // More cards arrive; the room finally agrees on one of them
        rules.add((31..40).map { "movie:$it" })
        assertEquals(10, rules.remaining())
        ids.forEach { rules.vote(it, "movie:35", true) }
        assertEquals(MatchOutcome("movie:35", true, 10, 10), rules.result)
    }

    @Test
    fun addingCardsIgnoresRepeatsAndRemainingFollowsTheFastestPerson() {
        val rules = MatchRules(listOf("a", "b", "c"))
        val fast = rules.join("Fast"); val slow = rules.join("Slow")
        listOf("a", "b", "c").forEach { rules.vote(fast, it, false) }
        rules.vote(slow, "a", false)
        assertEquals("the fast one has nothing left: that is who runs out first", 0, rules.remaining())
        rules.add(listOf("c", "d", "d", "e"))
        assertEquals(5, rules.size)
        assertEquals(2, rules.remaining())
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
