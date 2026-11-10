package dev.jean.atvhome.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBreakTest {

    private val state = AdBreak()
    private var now = 10_000L
    private val ad = Verdict.Ad
    private val skip = Verdict.Skippable(Label("Pular", 0.9f, 0.9f))

    private fun next(v: Verdict): Decision = state.on(v, now).also { now += 1_000 }

    @Test fun oneAdReadAloneNeverStartsABreak() {
        assertEquals(listOf(false, false, false), listOf(Verdict.None, ad, Verdict.None).map { next(it).active })
    }

    @Test fun twoReadsInARowStartIt() {
        assertFalse(next(ad).active)
        assertTrue(next(ad).active)
    }

    @Test fun oneMissedReadDoesNotEndIt() {
        next(ad); next(ad)
        assertTrue(next(Verdict.None).active)
        assertTrue(next(ad).active)
        next(Verdict.None)
        assertFalse(next(Verdict.None).active)
    }

    @Test fun skipIsPressedAtOnceButNotTwiceInARow() {
        assertNotNull(next(skip).click)
        assertNull(next(skip).click)
        assertNotNull(next(skip).click)
    }

    @Test fun aBreakThatNeverEndsFailsOpen() {
        val active = (0..243).map { next(ad).active } // one read a second for a little over 240 s
        assertTrue(active[200])
        assertFalse(active.last())
        repeat(58) { assertFalse(next(ad).active) }
        now += 2_000
        next(ad)
        assertTrue(next(ad).active)
    }

    @Test fun resetForgetsTheBreak() {
        next(ad); next(ad)
        state.reset()
        assertFalse(next(Verdict.None).active)
        assertFalse(state.inBreak)
    }
}
