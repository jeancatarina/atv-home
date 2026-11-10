package dev.jean.atvhome.social.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.jean.atvhome.social.room.RoomSnapshot
import dev.jean.atvhome.social.ui.HomeBackdrop
import dev.jean.atvhome.social.ui.PlayTab
import dev.jean.atvhome.social.ui.Tab
import dev.jean.atvhome.social.ui.TopBar
import dev.jean.atvhome.social.ui.WithLevels
import dev.jean.atvhome.ui.theme.InicioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi", application = android.app.Application::class)
class HomeShotsTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, snap: RoomSnapshot, tab: Tab = Tab.PLAY) {
        rule.setContent {
            InicioTheme {
                WithLevels(snap) {
                    Box(Modifier.fillMaxSize()) {
                        HomeBackdrop(snap)
                        Column {
                            TopBar(tab, {}, snap, {})
                            PlayTab(snap, "http://192.168.1.3:8766/", {}, {})
                        }
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage("build/shots/$name.png")
    }

    @Test fun emptyRoom() = shot("home-empty", Fixtures.room(0))
    @Test fun fivePeople() = shot("home-5", Fixtures.room(5, ranked = true))
    @Test fun twelvePeople() = shot("home-12", Fixtures.room(12))
}
