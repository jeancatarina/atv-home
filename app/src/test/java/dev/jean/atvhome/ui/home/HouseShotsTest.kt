package dev.jean.atvhome.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.jean.atvhome.tuya.Light
import dev.jean.atvhome.ui.theme.InicioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Casa tiles, lit and dark, on the TV's 960×540 dp screen (build/shots/casa-*.png). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi", application = android.app.Application::class)
class HouseShotsTest {
    @get:Rule val rule = createComposeRule()

    private val lights = listOf(
        Light("estar", listOf(1), "Luz cozinha", "Sala de Estar", "Estar"),
        Light("estar", listOf(2), "Luz sala", "Sala de Estar", "Estar"),
        Light("estar", listOf(3), "Luz mesa", "Sala de Estar", "Estar"),
        Light("lavabo", listOf(1, 2, 3), "Lavabo", "Lavabo", "Lavabo"),
    )

    @Test fun `lit and dark tiles`() {
        val states = mutableStateOf(mapOf("estar:1" to false, "estar:2" to true, "estar:3" to false, "lavabo:2" to true))
        rule.setContent {
            InicioTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Row(Modifier.padding(48.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        lights.forEach { light -> LightTile(light, states, onClick = {}) }
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage("build/shots/casa-row.png")
        rule.onNodeWithText("Luz mesa").assertExists()
    }
}
