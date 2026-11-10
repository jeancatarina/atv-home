package dev.jean.atvhome.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.github.takahirom.roborazzi.captureRoboImage
import dev.jean.atvhome.data.HouseModel
import dev.jean.atvhome.tuya.Light
import dev.jean.atvhome.tuya.Link
import dev.jean.atvhome.tuya.Plan
import dev.jean.atvhome.tuya.Version
import dev.jean.atvhome.ui.theme.InicioTheme
import dev.jean.atvhome.ui.theme.LocalAccent
import dev.jean.atvhome.ui.theme.screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalTime

/** The model with the example plan (tools/casa-plan.example.json): build/shots/casa-model-*.png. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi", application = android.app.Application::class)
@OptIn(ExperimentalTestApi::class)
class MapShotsTest {
    @get:Rule val rule = createComposeRule()

    private val plan = Json.decodeFromString(Plan.serializer(), File("../tools/casa-plan.example.json").readText())

    private val house = object : HouseModel {
        override val lights = listOf(
            Light("estar", listOf(1), "Luz cozinha", "Sala de Estar", "Estar"),
            Light("estar", listOf(2), "Luz sala", "Sala de Estar", "Estar"),
            Light("estar", listOf(3), "Luz mesa", "Sala de Estar", "Estar"),
            Light("cenas", listOf(1), "Trilho", "Sala de Estar", "Cenas estar"),
            Light("coz", listOf(1), "Escada térreo cenas", "Cozinha", "Escada"),
            Light("quarto", listOf(1, 2), "Quartinho", "Quarto Secundário", "Quartinho"),
            Light("esc", listOf(1, 2), "Escritório", "Escritório", "Escritório"),
            Light("lav", listOf(1, 2, 3), "Lavabo", "Lavabo", "Lavabo"),
            Light("hall", listOf(1), "Luz do corredor", "Corredor", "Hall"),
            Light("ninho", listOf(1), "Guarda roupa", "Quarto principal", "Ninho"),
            Light("ninho", listOf(2), "Luz da suite", "Quarto principal", "Ninho"),
            Light("ninho", listOf(3), "Closet", "Quarto principal", "Ninho"),
            Light("wc", listOf(1), "Luz do banheiro", "Banheiro", "Toalete"),
            Light("ter", listOf(2), "Terraço", "Terraço", "Terraço cenas"),
            Light("ter", listOf(3), "Estufa", "Terraço", "Terraço cenas"),
        )
        override val plan = this@MapShotsTest.plan
        override val states = MutableStateFlow(mapOf("estar:1" to true, "estar:2" to true, "estar:3" to false, "ninho:2" to true, "ninho:3" to true, "lav:2" to true, "ter:3" to true))
        override val link = MutableStateFlow<Link>(Link.Online(Version.V33))
        override val row = MutableStateFlow(lights)
        override suspend fun toggle(light: Light) = true
        override suspend fun switchAll(group: List<Light>, on: Boolean) = true
        override fun setHidden(light: Light, hide: Boolean) = Unit
    }

    private fun show(at: LocalTime) = rule.setContent {
        InicioTheme { Box(Modifier.fillMaxSize().background(LocalAccent.current.screen())) { HouseMap(house, plan, toast = {}, clock = { at }) } }
    }

    @Test fun `the model opens floor by floor, at night`() {
        show(LocalTime.of(21, 30))
        rule.mainClock.advanceTimeBy(1_500)
        rule.waitForIdle()
        rule.onNodeWithText("2 de 4 acesas").assertExists() // the living room, first on the ground floor
        rule.onNodeWithText("21:30", substring = true).assertExists()
        rule.onRoot().captureRoboImage("build/shots/casa-model-1.png")
        rule.onRoot().performKeyInput { pressKey(Key.ChannelUp) }
        rule.mainClock.advanceTimeBy(1_900)
        rule.waitForIdle()
        rule.onNodeWithText("2º andar").assertExists()
        // Up from the living room at the back is the suite right above it
        rule.onNodeWithText("Quarto principal").assertExists()
        rule.onRoot().captureRoboImage("build/shots/casa-model-suite.png")
        // ▲ climbs to the room above on screen first; Channel + goes straight up a floor
        rule.onRoot().performKeyInput { pressKey(Key.ChannelUp) }
        rule.mainClock.advanceTimeBy(1_900)
        rule.waitForIdle()
        rule.onNodeWithText("TERRAÇO").assertExists()
        rule.onRoot().captureRoboImage("build/shots/casa-model-terraco.png")
    }

    @Test fun `a floor builds itself, the slab settles and the walls rise back to front`() {
        rule.mainClock.autoAdvance = false
        show(LocalTime.of(21, 30))
        rule.mainClock.advanceTimeBy(560)
        rule.onRoot().captureRoboImage("build/shots/casa-model-building.png")
    }

    @Test fun `by day the model is light and airy`() {
        show(LocalTime.of(14, 0))
        rule.mainClock.advanceTimeBy(1_900)
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/casa-model-day.png")
    }

    @Test fun `at sunset the model turns gold`() {
        show(LocalTime.of(18, 10))
        rule.mainClock.advanceTimeBy(1_500)
        rule.waitForIdle()
        rule.onNodeWithText("pôr do sol", substring = true).assertExists()
        rule.onRoot().captureRoboImage("build/shots/casa-model-sunset.png")
    }

    @Test fun `left alone, the model tours the floors`() {
        show(LocalTime.of(21, 30))
        rule.mainClock.advanceTimeBy(1_500)
        rule.onNodeWithText("1º ANDAR").assertExists()
        rule.mainClock.advanceTimeBy(31_000)
        rule.waitForIdle()
        rule.onNodeWithText("2º ANDAR").assertExists()
        rule.mainClock.advanceTimeBy(8_000)
        rule.waitForIdle()
        rule.onNodeWithText("TERRAÇO").assertExists()
    }
}
