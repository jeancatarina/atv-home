package dev.jean.atvhome.social.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HowToVote
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.lerp
import androidx.tv.material3.Icon
import dev.jean.atvhome.ui.theme.LocalAccent

/** Each game's colour and icon: the identity of its card, its page, its stage and its result. */
data class GameStyle(val accent: Color, val icon: ImageVector)

object Styles {
    private val table = mapOf(
        "mostlikely" to GameStyle(Color(0xFFFF9F0A), Icons.Rounded.HowToVote),
        "undercover" to GameStyle(Color(0xFFBF5AF2), Icons.Rounded.TheaterComedy),
        "frequency" to GameStyle(Color(0xFF64D2FF), Icons.Rounded.Tune),
        "couple" to GameStyle(Color(0xFFFF375F), Icons.Rounded.Favorite),
        "twotruths" to GameStyle(Color(0xFFFFD60A), Icons.Rounded.RecordVoiceOver),
        "spyfall" to GameStyle(Color(0xFF30D158), Icons.Rounded.Public),
        "insider" to GameStyle(Color(0xFF5E5CE6), Icons.Rounded.Visibility),
        "psychologist" to GameStyle(Color(0xFFAC8E68), Icons.Rounded.Psychology),
        "blackstory" to GameStyle(Color(0xFF7A8BA8), Icons.Rounded.Search),
        "missions" to GameStyle(Color(0xFF63E6E2), Icons.Rounded.Flag),
        "mafia" to GameStyle(Color(0xFFC0392B), Icons.Rounded.NightsStay),
        "tribunal" to GameStyle(Color(0xFFD9B45A), Icons.Rounded.Gavel),
        "film5" to GameStyle(Color(0xFFFF7A45), Icons.Rounded.Movie),
        "bomb" to GameStyle(Color(0xFFFF3B30), Icons.Rounded.Timer),
    )
    fun of(gameId: String): GameStyle = table[gameId] ?: GameStyle(Color(0xFF8E8E93), Icons.Rounded.GridView)
}

/**
 * A full-screen backdrop painted once into a small bitmap (a coloured glow in one corner over
 * near-black) and stretched. A live full-screen gradient would redraw every frame on this GPU.
 */
@Composable
fun Backdrop(accent: Color?, modifier: Modifier = Modifier.fillMaxSize()) {
    val base = androidx.compose.ui.graphics.lerp(Color(0xFF09090B), LocalAccent.current, 0.06f)
    val bitmap = remember(accent, base) {
        val w = 384; val h = 216
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
            val c = AndroidCanvas(b)
            c.drawColor(base.toArgb())
            val glow = accent ?: LocalAccentFallback
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = RadialGradient(w * 0.86f, h * 0.02f, w * 0.80f, intArrayOf(glow.copy(alpha = 0.36f).toArgb(), glow.copy(alpha = 0.10f).toArgb(), 0x00000000), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            // A cooler, fainter light low on the left gives the black some depth
            val q = Paint(Paint.ANTI_ALIAS_FLAG)
            val cool = androidx.compose.ui.graphics.lerp(glow, Color(0xFF5E5CE6), 0.6f)
            q.shader = RadialGradient(w * 0.08f, h * 1.05f, w * 0.62f, intArrayOf(cool.copy(alpha = 0.16f).toArgb(), 0x00000000), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), q)
            // Darken the bottom so rows and buttons read over it
            val shade = Paint()
            shade.shader = android.graphics.LinearGradient(0f, h * 0.55f, 0f, h.toFloat(), 0x00000000, 0x99000000.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), shade)
        }.asImageBitmap()
    }
    Image(bitmap, null, modifier = modifier, contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Low)
}

private val LocalAccentFallback = Color(0xFF5E5CE6)

private fun Color.toArgb(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

/**
 * The art of a game: its colour as a diagonal gradient, its icon large and half hidden in the
 * corner, and a soft highlight. Made of two gradients and a vector: no image files.
 */
@Composable
fun GameArt(gameId: String, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(14.dp), iconSize: Dp = 96.dp, content: @Composable BoxScope.() -> Unit = {}) {
    val st = Styles.of(gameId)
    Box(
        modifier.clip(shape)
            .background(Brush.linearGradient(listOf(st.accent, lerp(st.accent, Color.Black, 0.55f))))
            .drawWithCache {
                val glow = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.30f), Color.Transparent), center = Offset(size.width * 0.18f, size.height * 0.05f), radius = size.width * 0.75f)
                // Rings spreading from the icon's corner give the flat colour some depth
                val center = Offset(size.width * 0.86f, size.height * 0.86f)
                onDrawBehind {
                    drawRect(glow)
                    listOf(0.34f, 0.58f, 0.86f).forEachIndexed { i, r ->
                        drawCircle(Color.White.copy(alpha = 0.10f - i * 0.025f), radius = size.minDimension * r, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.012f))
                    }
                }
            },
    ) {
        Icon(
            st.icon, null, tint = Color.White.copy(alpha = 0.34f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = iconSize * 0.14f, bottom = iconSize * 0.14f).size(iconSize),
        )
        content()
    }
}
