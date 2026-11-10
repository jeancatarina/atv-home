package dev.jean.atvhome.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class MatchMemoryTest {

    private val day = 86_400_000L
    private var now = 100 * day
    private val keys = (1..20).map { "movie:$it" }

    @Test fun titlesNeverRefusedComeFirstAndRecentRefusalsComeLast() {
        val m = MatchMemory(null) { now }
        m.pass("movie:1"); now += 3 * day
        m.pass("movie:2")
        val order = m.order(keys, Random(1))
        assertEquals(keys.size, order.size)
        assertEquals("recent refusals at the end, oldest of them first", listOf("movie:1", "movie:2"), order.takeLast(2))
        assertTrue(order.take(18).none { it == "movie:1" || it == "movie:2" })
    }

    @Test fun aRefusedTitleComesBackAfterTheCooldownAheadOfTheRecentOnes() {
        val m = MatchMemory(null) { now }
        m.pass("movie:1")
        now += (MatchMemory.COOLDOWN_DAYS + 5) * day
        m.pass("movie:2")
        val order = m.order(listOf("movie:1", "movie:2", "movie:3"), Random(2))
        assertEquals("never refused, then refused long ago, then refused lately", listOf("movie:3", "movie:1", "movie:2"), order)
    }

    @Test fun whatWasMatchedIsNotThrownBackRightAway() {
        val m = MatchMemory(null) { now }
        m.matched("movie:5")
        assertEquals(0.0, m.daysSince("movie:5")!!, 0.001)
        assertEquals("movie:5", m.order(listOf("movie:5", "movie:6"), Random(3)).last())
    }

    @Test fun memorySurvivesARestartAndIsBounded() {
        val file = File.createTempFile("mem", ".json").also { it.deleteOnExit() }
        MatchMemory(file) { now }.apply { pass("movie:1"); matched("movie:2") }
        val again = MatchMemory(file) { now }
        assertEquals(0.0, again.daysSince("movie:1")!!, 0.001)
        assertEquals(0.0, again.daysSince("movie:2")!!, 0.001)
        assertNull(again.daysSince("movie:3"))
        val big = MatchMemory(file) { now }
        repeat(1_000) { big.pass("t$it") }
        assertNull("the oldest were dropped", MatchMemory(file) { now }.daysSince("t0"))
        assertTrue(MatchMemory(file) { now }.daysSince("t999") != null)
    }

    @Test fun orderKeepsEveryTitleOnce() {
        val m = MatchMemory(null) { now }
        m.pass("movie:4")
        assertEquals(keys.toSet(), m.order(keys + keys, Random(4)).toSet())
        assertEquals(keys.size, m.order(keys + keys, Random(4)).size)
    }
}
