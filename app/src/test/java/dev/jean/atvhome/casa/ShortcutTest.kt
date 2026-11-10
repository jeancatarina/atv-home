package dev.jean.atvhome.casa

import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.jean.atvhome.ui.home.ShortcutPicker
import dev.jean.atvhome.ui.theme.InicioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The film shortcut: what can be chosen, where it is kept, and the picker on screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi", application = android.app.Application::class)
@OptIn(ExperimentalTestApi::class)
class ShortcutTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun `yellow until changed, and a new choice is read back at once`() {
        File(context.filesDir, "casa-key.txt").delete()
        assertEquals(KeyEvent.KEYCODE_PROG_YELLOW, Shortcut.get(context))
        Shortcut.set(context, KeyEvent.KEYCODE_PROG_BLUE)
        File(context.filesDir, "casa-key.txt").setLastModified(System.currentTimeMillis() + 5_000)
        assertEquals(KeyEvent.KEYCODE_PROG_BLUE, Shortcut.get(context))
        Shortcut.set(context, Shortcut.NONE)
        File(context.filesDir, "casa-key.txt").setLastModified(System.currentTimeMillis() + 10_000)
        assertEquals(Shortcut.NONE, Shortcut.get(context))
    }

    @Test fun `keys every app needs cannot be the shortcut`() {
        for (k in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MENU)) {
            assertFalse(KeyEvent.keyCodeToString(k), Shortcut.allowed(k))
        }
        assertTrue(Shortcut.allowed(KeyEvent.KEYCODE_PROG_RED))
        assertTrue(Shortcut.allowed(KeyEvent.KEYCODE_INFO))
        assertEquals("Azul", Shortcut.name(KeyEvent.KEYCODE_PROG_BLUE))
        assertEquals("Número 7", Shortcut.name(KeyEvent.KEYCODE_7))
    }

    @Test fun `pressing a key on the remote picks it, a needed key is refused`() {
        val picked = mutableListOf<Int>()
        rule.setContent { InicioTheme { ShortcutPicker(KeyEvent.KEYCODE_PROG_YELLOW, onPick = { picked += it }, onCancel = {}) } }
        rule.onRoot().captureRoboImage("build/shots/casa-shortcut.png")
        rule.onRoot().performKeyInput { pressKey(Key(KeyEvent.KEYCODE_VOLUME_UP)) }
        rule.onNodeWithText("não pode", substring = true).assertExists()
        rule.onRoot().performKeyInput { pressKey(Key(KeyEvent.KEYCODE_PROG_RED)) }
        assertEquals(listOf(KeyEvent.KEYCODE_PROG_RED), picked)
    }

    @Test fun `a held shortcut opens once, a short press goes back to the app`() {
        val hold = Hold(600)
        // Short press: kept while down, given back on release
        assertEquals(Hold.Step.KEEP, hold.step(down = true, repeat = 0, downTime = 0, eventTime = 0))
        assertEquals(Hold.Step.REPLAY, hold.step(down = false, repeat = 0, downTime = 0, eventTime = 150))
        // Held: the remote repeats it; past 600 ms the house opens, once, and the release is swallowed
        assertEquals(Hold.Step.KEEP, hold.step(true, 0, 1_000, 1_000))
        assertEquals(Hold.Step.KEEP, hold.step(true, 1, 1_000, 1_400))
        assertEquals(Hold.Step.OPEN, hold.step(true, 5, 1_000, 1_650))
        assertEquals(Hold.Step.KEEP, hold.step(true, 6, 1_000, 1_700))
        assertEquals(Hold.Step.KEEP, hold.step(false, 0, 1_000, 1_900))
        // And the next short press is a press again
        assertEquals(Hold.Step.KEEP, hold.step(true, 0, 3_000, 3_000))
        assertEquals(Hold.Step.REPLAY, hold.step(false, 0, 3_000, 3_100))
    }

    @Test fun `only Back and Play-Pause can be held, and a held choice is kept`() {
        assertTrue(Shortcut.valid(Shortcut.HOLD_BACK))
        assertTrue(Shortcut.valid(Shortcut.HOLD_PLAY))
        assertFalse(Shortcut.valid(Shortcut.HOLD + KeyEvent.KEYCODE_HOME))
        assertEquals(KeyEvent.KEYCODE_BACK, Shortcut.keyOf(Shortcut.HOLD_BACK))
        assertEquals("Segurar Voltar", Shortcut.name(Shortcut.HOLD_BACK))
        Shortcut.set(context, Shortcut.HOLD_PLAY)
        File(context.filesDir, "casa-key.txt").setLastModified(System.currentTimeMillis() + 20_000)
        assertEquals(Shortcut.HOLD_PLAY, Shortcut.get(context))
    }

    @Test fun `holding Back in the picker picks it, a short Back cancels`() {
        val picked = mutableListOf<Int>()
        var cancelled = 0
        rule.setContent { InicioTheme { ShortcutPicker(KeyEvent.KEYCODE_PROG_YELLOW, onPick = { picked += it }, onCancel = { cancelled++ }) } }
        rule.onRoot().performKeyInput { keyDown(Key.Back); advanceEventTime(900); keyUp(Key.Back) }
        assertEquals(listOf(Shortcut.HOLD_BACK), picked)
        assertEquals(0, cancelled)
        rule.onRoot().performKeyInput { pressKey(Key.Back) }
        assertEquals(1, cancelled)
    }
}
