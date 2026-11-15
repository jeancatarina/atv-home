package dev.jean.atvhome.ui.devotional

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.jean.atvhome.devotional.DevotionalHost
import dev.jean.atvhome.ui.home.WatchMenu
import dev.jean.atvhome.ui.theme.InicioTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.unit.dp
import dev.jean.atvhome.ui.components.PillButton
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi", application = android.app.Application::class)
class DevotionalShotsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun board() {
        val board = DevotionalHost.board(ApplicationProvider.getApplicationContext())
        val today = LocalDate.now()
        val jean = board.addPerson("Jean")!!
        val ana = board.addPerson("Ana")!!
        (0L..20L).filter { it % 7 != 3L }.forEach { board.mark(jean.id, today.minusDays(it), today.minusDays(it), true) }
        (0L..4L).forEach { board.mark(ana.id, today.minusDays(it), today.minusDays(it), true) }
        rule.setContent { InicioTheme { DevotionalScreen() } }
        rule.onRoot().captureRoboImage("build/shots/devocional.png")
    }

    @Test fun topRow() {
        rule.setContent {
            InicioTheme {
                androidx.compose.foundation.layout.Row(
                    androidx.compose.ui.Modifier.padding(start = 48.dp, top = 20.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    PillButton("Buscar", {}, iconVector = Icons.Rounded.Search)
                    PillButton("Assistir", {}, iconVector = Icons.Rounded.PlayArrow)
                    PillButton("Social", {}, iconVector = Icons.Rounded.Groups)
                    PillButton("Devocional", {}, iconVector = Icons.Rounded.MenuBook)
                    PillButton("Central", {}, iconVector = Icons.Rounded.Settings, textWhenFocused = true)
                }
            }
        }
        rule.onRoot().captureRoboImage("build/shots/topo.png")
    }

    @Test fun watchMenu() {
        rule.setContent { InicioTheme { WatchMenu({}, {}, {}, {}) } }
        rule.onRoot().captureRoboImage("build/shots/assistir.png")
    }
}
