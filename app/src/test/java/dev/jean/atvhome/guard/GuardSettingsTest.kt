package dev.jean.atvhome.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GuardSettingsTest {

    @get:Rule val dir = TemporaryFolder()

    @Test fun theDefaultsSkipAndKeepTheSound() {
        val d = GuardSettings.load(dir.root) // nothing saved yet
        assertEquals(GuardSettings(), d)
        assertTrue(d.skip)
        assertFalse(d.mute)
        assertFalse(d.tapSkip)
    }

    @Test fun whatThePersonChoseComesBack() {
        val chosen = GuardSettings(skip = false, tapSkip = true, mute = true, readScreen = false, countdown = false)
        GuardSettings.save(dir.root, chosen)
        assertEquals(chosen, GuardSettings.load(dir.root))
    }

    @Test fun aBrokenFileFallsBackToTheDefaults() {
        File(dir.root, "guard_settings.json").writeText("{ not json")
        assertEquals(GuardSettings(), GuardSettings.load(dir.root))
    }

    @Test fun withNothingToDoTheGuardDoesNotAct() {
        assertFalse(GuardSettings(skip = false, mute = false).acts)
        assertTrue(GuardSettings(skip = false, mute = true).acts)
    }
}
