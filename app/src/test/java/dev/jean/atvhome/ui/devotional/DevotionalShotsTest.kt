package dev.jean.atvhome.ui.devotional

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.jean.atvhome.devotional.DevotionalHost
import dev.jean.atvhome.ui.home.WatchMenu
import dev.jean.atvhome.ui.theme.InicioTheme
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

    @Test fun watchMenu() {
        rule.setContent { InicioTheme { WatchMenu({}, {}, {}, {}) } }
        rule.onRoot().captureRoboImage("build/shots/assistir.png")
    }
}
