package dev.jean.atvhome.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.jean.atvhome.data.HouseModel
import dev.jean.atvhome.tuya.Kind
import dev.jean.atvhome.tuya.Light
import dev.jean.atvhome.tuya.Link
import dev.jean.atvhome.tuya.Show
import dev.jean.atvhome.ui.theme.InicioTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Casa tiles and the house panel on the TV's 960×540 dp screen (build/shots/casa-*.png). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi", application = android.app.Application::class)
class HouseShotsTest {
    @get:Rule val rule = createComposeRule()

    private val house = object : HouseModel {
        override val lights = listOf(
            Light("estar", listOf(1), "Luz cozinha", "Sala de Estar", "Estar"),
            Light("estar", listOf(2), "Luz sala", "Sala de Estar", "Estar"),
            Light("estar", listOf(3), "Luz mesa", "Sala de Estar", "Estar"),
            Light("cenas", listOf(1), "Trilho", "Sala de Estar", "Cenas estar"),
            Light("ninho", listOf(1), "Guarda roupa", "Quarto principal", "Ninho"),
            Light("ninho", listOf(3), "Closet", "Quarto principal", "Ninho"),
            Light("cama", listOf(1, 2, 3), "Cama direito", "Quarto principal", "Cama direito"),
            Light("hall", listOf(3), "Cena Hall", "Corredor", "Hall", load = false, kind = Kind.SCENE),
            Light("hall", listOf(1), "Luz do corredor", "Corredor", "Hall"),
            Light("lavabo", listOf(1, 2, 3), "Lavabo", "Lavabo", "Lavabo"),
        )
        override val states = MutableStateFlow(mapOf("estar:2" to true, "ninho:3" to true, "cama:2" to true, "lavabo:2" to true))
        override val link = MutableStateFlow<Link>(Link.Online(dev.jean.atvhome.tuya.Version.V33))
        override val row = MutableStateFlow(lights.filter { it.load })
        override suspend fun toggle(light: Light) = true
        override suspend fun switchAll(group: List<Light>, on: Boolean) = true
        override fun setHidden(light: Light, hide: Boolean) = Unit
    }

    @Test fun `lit and dark tiles`() {
        val states = mutableStateOf(house.states.value)
        rule.setContent {
            InicioTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Row(Modifier.padding(48.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        house.lights.take(4).forEach { light -> LightTile(light, states, onClick = {}) }
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage("build/shots/casa-row.png")
        rule.onNodeWithText("Luz mesa").assertExists()
    }

    @Test fun `the house on the whole screen`() {
        rule.setContent { InicioTheme { HouseScreen(house, toast = {}) } }
        rule.onNodeWithText("4 acesas").assertExists()
        rule.onRoot().captureRoboImage("build/shots/casa-screen.png")
    }

    @Test fun `the side sheet over a film, lit lights only`() {
        val state = PanelState().apply { show = Show.LIT }
        rule.setContent {
            InicioTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF3A5068))) {
                    Box(
                        Modifier.align(Alignment.CenterEnd).width(452.dp).fillMaxHeight()
                            .background(Color(0xF2141416), RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp)),
                    ) { HousePanel(house, toast = {}, state = state, columns = GridCells.Fixed(3), edge = 22.dp) }
                }
            }
        }
        rule.onNodeWithText("Closet").assertExists()
        rule.onRoot().captureRoboImage("build/shots/casa-sheet.png")
    }

    @Test fun `in Acesas a light turned off stays in place until the filter changes`() {
        val state = PanelState().apply { show = Show.LIT }
        rule.setContent { InicioTheme { HousePanel(house, toast = {}, state = state) } }
        rule.onNodeWithText("Closet").assertExists()
        rule.onNodeWithText("Luz cozinha").assertDoesNotExist()
        house.states.value = house.states.value + ("ninho:3" to false)
        rule.waitForIdle()
        rule.onNodeWithText("Closet").assertExists()
        rule.runOnIdle { state.filter(Show.LIT) }
        rule.waitForIdle()
        rule.onNodeWithText("Closet").assertDoesNotExist()
    }
}
