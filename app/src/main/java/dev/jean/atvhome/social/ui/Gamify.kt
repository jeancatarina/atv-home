package dev.jean.atvhome.social.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.jean.atvhome.social.room.RoomSnapshot
import dev.jean.atvhome.ui.theme.Inter
import java.time.LocalDate
import java.time.format.TextStyle as DateStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * The metal of a level: every ten levels the emblem changes material, so a glance at a face tells
 * a newcomer from a veteran. Bronze up to 9, then silver, gold, platinum, diamond, and a legend at 50.
 */
enum class Tier(val label: String, val light: Color, val dark: Color) {
    BRONZE("Bronze", Color(0xFFF0B27A), Color(0xFF8A4B22)),
    SILVER("Prata", Color(0xFFF5F5FA), Color(0xFF7C7C86)),
    GOLD("Ouro", Color(0xFFFFE58A), Color(0xFFB07A0A)),
    PLATINUM("Platina", Color(0xFFC8FBF6), Color(0xFF2F8F98)),
    DIAMOND("Diamante", Color(0xFFE2CCFF), Color(0xFF5B34D6)),
    LEGEND("Lenda", Color(0xFFFFB199), Color(0xFFD1004F));

    val mid: Color get() = androidx.compose.ui.graphics.lerp(light, dark, 0.45f)
}

fun tierOf(level: Int): Tier = when {
    level >= 50 -> Tier.LEGEND
    level >= 40 -> Tier.DIAMOND
    level >= 30 -> Tier.PLATINUM
    level >= 20 -> Tier.GOLD
    level >= 10 -> Tier.SILVER
    else -> Tier.BRONZE
}

/** Each seated person's level, by player id, so every avatar can wear its emblem without being told. */
val LocalLevels = staticCompositionLocalOf<Map<String, Int>> { emptyMap() }

@Composable
fun WithLevels(snap: RoomSnapshot, content: @Composable () -> Unit) {
    val levels = remember(snap.careers) { snap.careers.mapValues { it.value.level } }
    CompositionLocalProvider(LocalLevels provides levels, content = content)
}

/** A hexagon in the tier's metal with the level number: the emblem pinned to faces and names. */
@Composable
fun LevelBadge(level: Int, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    val t = tierOf(level)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val hex = Path().apply {
                for (i in 0 until 6) {
                    val a = Math.toRadians(60.0 * i - 90.0)
                    val p = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(hex, Color.Black.copy(alpha = 0.55f))
            val inner = Path().apply {
                for (i in 0 until 6) {
                    val a = Math.toRadians(60.0 * i - 90.0)
                    val p = Offset(c.x + r * 0.84f * cos(a).toFloat(), c.y + r * 0.84f * sin(a).toFloat())
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(inner, Brush.linearGradient(listOf(t.light, t.dark), start = Offset(0f, 0f), end = Offset(this.size.width, this.size.height)))
            drawPath(inner, Color.White.copy(alpha = 0.45f), style = Stroke(width = r * 0.08f))
        }
        Text(
            "$level",
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = (size.value * if (level >= 10) 0.40f else 0.46f).sp, lineHeight = (size.value * 0.5f).sp),
            color = if (t == Tier.DIAMOND || t == Tier.LEGEND) Color.White else Color(0xFF1C1C1E),
        )
    }
}

/** Gold, silver or bronze for a place on a podium. */
fun medalColors(place: Int): Pair<Color, Color> = when (place) {
    1 -> Color(0xFFFFE58A) to Color(0xFFC48A00)
    2 -> Color(0xFFF2F2F7) to Color(0xFF8E8E98)
    else -> Color(0xFFF0B27A) to Color(0xFF8A4B22)
}

/** A round medal with the place on it. */
@Composable
fun MedalDisc(place: Int, size: Dp = 30.dp, modifier: Modifier = Modifier) {
    val (light, dark) = medalColors(place)
    Box(
        modifier.size(size).background(Brush.radialGradient(listOf(light, dark), center = Offset.Unspecified), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("$place", style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = (size.value * 0.5f).sp), color = Color(0xFF2C2C2E))
    }
}

/** A small crown for the first place, drawn so no image is needed. */
@Composable
fun Crown(size: Dp = 28.dp, color: Color = Color(0xFFFFD60A), modifier: Modifier = Modifier) {
    Canvas(modifier.size(size, size * 0.72f)) {
        val w = this.size.width; val h = this.size.height
        val p = Path().apply {
            moveTo(0f, h * 0.30f); lineTo(w * 0.27f, h * 0.62f); lineTo(w * 0.5f, 0f); lineTo(w * 0.73f, h * 0.62f); lineTo(w, h * 0.30f)
            lineTo(w * 0.88f, h); lineTo(w * 0.12f, h); close()
        }
        drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFF1B0), color, Color(0xFFC48A00))))
        listOf(0f, 0.5f, 1f).forEach { x -> drawCircle(Color(0xFFFFF1B0), radius = w * 0.07f, center = Offset(w * x.coerceIn(0.06f, 0.94f), if (x == 0.5f) h * 0.04f else h * 0.30f)) }
    }
}

/** "Temporada de novembro" and the days left in it, by the TV's calendar. */
fun seasonLabel(today: LocalDate = LocalDate.now()): Pair<String, Int> {
    val month = today.month.getDisplayName(DateStyle.FULL, Locale("pt", "BR"))
    return "Temporada de $month" to (today.lengthOfMonth() - today.dayOfMonth + 1)
}

/** The people sitting in the room, by their phone's device id: their seat's colour, shape and selfie. */
fun seatedByDevice(snap: RoomSnapshot): Map<String, dev.jean.atvhome.social.game.Player> =
    snap.careers.mapNotNull { (id, c) -> snap.players.firstOrNull { it.player.id == id }?.player?.let { c.device to it } }.toMap()

/**
 * How to draw someone from the board: as they sit in the room when they are here, so the ranking
 * shows the same colour and face as the stage; otherwise with a colour fixed by their device.
 */
fun personOf(c: dev.jean.atvhome.social.profile.Career, seated: Map<String, dev.jean.atvhome.social.game.Player>): dev.jean.atvhome.social.game.Player =
    seated[c.device] ?: (c.device.hashCode() and 0x7fffffff).let { h -> dev.jean.atvhome.social.game.Player(c.device, c.name, h % 12, (h / 12) % 6) }
