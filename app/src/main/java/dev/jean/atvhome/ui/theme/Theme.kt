package dev.jean.atvhome.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import dev.jean.atvhome.R

/**
 * tvOS look on a 1080p TV that reports 960×540 dp: Inter (the closest open font to SF Pro, which
 * Apple doesn't license outside its devices), pure black, white text at three opacities.
 */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

object Tv {
    val Primary = Color.White
    val Secondary = Color.White.copy(alpha = 0.72f)
    val Tertiary = Color.White.copy(alpha = 0.48f)
    val Card = Color(0xFF1C1C1E)
    val CardFocused = Color(0xFF2C2C2E)
    val Pill = Color.White.copy(alpha = 0.14f)
    val ImdbYellow = Color(0xFFF5C518)

    /** Margin from the screen edge, like tvOS's title-safe inset. */
    val Edge = 48.dp
    val Gap = 18.dp
    val Corner = 12.dp

    val Display = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp)
    val Title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp)
    val Headline = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 19.sp)
    val Body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val Meta = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 15.sp)
    val Caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 13.sp)
    val Clock = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.sp)
}

@Composable
fun InicioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(background = Color.Black, surface = Color.Black, onSurface = Color.White),
        typography = Typography(
            bodyMedium = Tv.Body, bodyLarge = Tv.Body, titleMedium = Tv.Headline, labelLarge = Tv.Meta,
        ),
        content = content,
    )
}
