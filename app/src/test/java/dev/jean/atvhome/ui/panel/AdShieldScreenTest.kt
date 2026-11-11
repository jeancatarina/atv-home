package dev.jean.atvhome.ui.panel

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.jean.atvhome.ui.theme.InicioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The "Sem anúncios" settings answer the remote: focus on the first row when the screen opens, ▼ to the next. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi", application = android.app.Application::class)
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class AdShieldScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `the remote moves through the settings`() {
        rule.setContent {
            // A TV is never in touch mode (buttons take the focus only outside it): the screen opens in the
            // remote's mode, as it does on the TV
            val input = LocalInputModeManager.current
            var remote by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { remote = input.requestInputMode(InputMode.Keyboard) }
            if (remote) InicioTheme { AdShieldScreen(toast = {}, askCapture = {}) }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Sem anúncios: desligado").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        rule.onNodeWithText("Pular anúncio", substring = true).assertIsFocused()
    }
}
