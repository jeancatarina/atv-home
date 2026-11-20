package dev.jean.atvhome.ui.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import dev.jean.atvhome.ui.theme.InicioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HeroSoundButtonTest {

    @get:Rule val rule = createComposeRule()

    @OptIn(ExperimentalTestApi::class)
    @Test fun `hero sound button toggles between Sem som and Com som`() {
        var sound by mutableStateOf(false)
        rule.setContent {
            InicioTheme {
                HeroSoundButton(sound = sound, onToggle = { sound = !sound })
            }
        }
        rule.onNodeWithText("Sem som").assertIsDisplayed()
        rule.onNode(hasClickAction() and hasText("Sem som", substring = true))
            .requestFocus()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        rule.onNodeWithText("Com som").assertIsDisplayed()
        rule.onNode(hasClickAction() and hasText("Com som", substring = true))
            .performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        rule.onNodeWithText("Sem som").assertIsDisplayed()
    }
}
