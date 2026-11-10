package dev.jean.atvhome.guard

import dev.jean.atvhome.guard.AdSignalsTest.Companion.l
import dev.jean.atvhome.guard.AdSignalsTest.Companion.rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CountdownTest {

    private val countdown = Countdown(AdSignals(rules()))

    private fun feed(vararg labels: Label) = countdown.feed(Reading(Source.SCREEN, labels = labels.toList()))
    private fun corner(text: String) = l(text, 0.92f, 0.08f)

    /** Feeds one read per text; returns what each call gave back. */
    private fun run(vararg texts: String) = texts.map { feed(corner(it)) }

    @Test fun fourFallingReadsInACorner() {
        val out = run("15", "14", "13", "12")
        assertEquals(listOf(null, null, null), out.take(3))
        assertEquals("12", out[3]?.text)
    }

    @Test fun clockFormat() {
        assertEquals("0:12", run("0:15", "0:14", "0:13", "0:12").last()?.text)
    }

    @Test fun calmReadsTwoSecondsApart() {
        assertEquals("14", run("20", "18", "16", "14").last()?.text)
    }

    @Test fun aProvenCountdownSurvivesARepeatButNotABlank() {
        run("15", "14", "13", "12")
        assertNotNull(feed(corner("12")))
        assertNull(feed())
        assertNull(feed(corner("11")))
    }

    @Test fun watchingOnceACornerNumberShowsUp() {
        assertEquals(false, countdown.watching)
        feed(corner("15"))
        assertEquals(true, countdown.watching)
        feed()
        assertEquals(false, countdown.watching)
    }

    @Test fun elapsedTimeGoesUp() {
        assertEquals(listOf(null, null, null, null), run("12", "13", "14", "15"))
    }

    @Test fun numbersOutsideTheCornersDoNotCount() {
        assertEquals(listOf(null, null, null, null), listOf("15", "14", "13", "12").map { feed(l(it, 0.5f, 0.5f)) })
    }

    @Test fun longClocksAreNotShortCounters() {
        assertEquals(listOf(null, null, null, null), run("2:31:07", "2:31:06", "2:31:05", "2:31:04"))
        assertNull(Countdown.seconds("12:34"))
    }

    @Test fun controlsOnScreen() {
        val busy = arrayOf(l("Pausar", 0.5f, 0.9f), l("Legendas", 0.6f, 0.9f), l("Áudio", 0.7f, 0.9f))
        assertEquals(listOf(null, null, null, null), listOf("15", "14", "13", "12").map { feed(corner(it), *busy) })
    }

    @Test fun nextEpisodeVetoes() {
        assertEquals(listOf(null, null, null, null), listOf("5", "4", "3", "2").map { feed(corner(it), l("Próximo episódio", 0.85f, 0.85f)) })
    }

    @Test fun aCounterThatJumpsCornersIsNotOne() {
        val out = listOf(corner("15"), l("14", 0.08f, 0.92f), corner("13"), l("12", 0.08f, 0.92f)).map { feed(it) }
        assertEquals(listOf(null, null, null, null), out)
    }
}
