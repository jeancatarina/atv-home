package dev.jean.atvhome.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.jean.atvhome.ui.theme.InicioTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A layer that lost the focus (a system screen came and went) gets it back; one that has it keeps it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi", application = android.app.Application::class)
@OptIn(ExperimentalComposeUiApi::class)
class FocusLayerTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `a layer with nothing focused takes the focus back on its first item`() {
        val layer = FocusLayer()
        lateinit var focus: FocusManager
        rule.setContent {
            // The TV's remote mode: buttons take the focus only outside touch mode
            val input = LocalInputModeManager.current
            var remote by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { remote = input.requestInputMode(InputMode.Keyboard) }
            focus = LocalFocusManager.current
            if (remote) InicioTheme {
                Column(Modifier.fillMaxSize().focusLayer(layer).focusGroup()) {
                    PillButton("Primeiro", {})
                    PillButton("Segundo", {})
                }
            }
        }
        rule.waitForIdle()
        rule.runOnIdle { focus.clearFocus(force = true) }
        rule.waitForIdle()
        assertFalse(layer.hasFocus)
        rule.runOnIdle { assertTrue(layer.regain()) }
        rule.onNodeWithText("Primeiro").assertIsFocused()
        // Already focused: nothing to do, and the key goes on to whatever has it
        rule.runOnIdle { assertFalse(layer.regain()) }
    }
}
