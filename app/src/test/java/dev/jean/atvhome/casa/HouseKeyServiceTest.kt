package dev.jean.atvhome.casa

import android.app.Application
import android.media.AudioManager
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HouseKeyServiceTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun `volume keys start repeat on initial down, step on repeat, stop on up or cancel`() {
        assertEquals(
            VolumeHold.Step.Start(AudioManager.ADJUST_RAISE),
            VolumeHold.step(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, repeatCount = 0),
        )
        assertEquals(
            VolumeHold.Step.Repeat(AudioManager.ADJUST_RAISE),
            VolumeHold.step(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, repeatCount = 1),
        )
        assertEquals(
            VolumeHold.Step.Stop,
            VolumeHold.step(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_UP, repeatCount = 0),
        )
        assertEquals(
            VolumeHold.Step.Start(AudioManager.ADJUST_LOWER),
            VolumeHold.step(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, repeatCount = 0),
        )
        assertEquals(
            VolumeHold.Step.Stop,
            VolumeHold.step(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, repeatCount = 0, flags = KeyEvent.FLAG_CANCELED),
        )
        assertEquals(
            VolumeHold.Step.None,
            VolumeHold.step(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN, repeatCount = 0),
        )
    }

    @Test fun `dream state marker tracks screensaver across processes`() {
        DreamState.set(context, false)
        assertFalse(DreamState.isDreaming(context))
        DreamState.set(context, true)
        assertTrue(DreamState.isDreaming(context))
        DreamState.set(context, false)
        assertFalse(DreamState.isDreaming(context))
    }
}
