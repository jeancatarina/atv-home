package dev.jean.atvhome.social.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.tv.material3.Text
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import dev.jean.atvhome.ui.theme.InicioTheme
import dev.jean.atvhome.ui.theme.Tv
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi", application = android.app.Application::class)
class ToolchainSmokeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun rendersAScreenAtTvSize() {
        rule.setContent { InicioTheme { Box(Modifier.fillMaxSize().background(Color(0xFF14180F)), Alignment.Center) { Text("Social", style = Tv.Display, color = Tv.Primary) } } }
        rule.onRoot().captureRoboImage("build/shots/smoke.png")
    }
}
