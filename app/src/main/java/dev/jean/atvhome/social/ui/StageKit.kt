package dev.jean.atvhome.social.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.jean.atvhome.social.game.Palette
import dev.jean.atvhome.social.game.Player
import dev.jean.atvhome.ui.theme.Inter
import dev.jean.atvhome.ui.theme.Tv
import kotlinx.coroutines.delay

/**
 * Type for reading from the couch: nothing a player must read during a game is below 20 sp
 * (the launcher's own Body is 13 sp and is for a person holding the remote).
 */
object Stage {
    val Hero = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-0.5).sp)
    val Title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp)
    val Body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp)
    val Gold = Color(0xFFD9B45A)
}

/** The faces people sent, decoded once, by person. Provided by the Social screen. */
val LocalFaces = androidx.compose.runtime.staticCompositionLocalOf<Map<String, androidx.compose.ui.graphics.ImageBitmap>> { emptyMap() }

fun personColor(p: Player): Color = Color(android.graphics.Color.parseColor(Palette.colors[p.color % Palette.colors.size]))
fun personShape(p: Player): String = Palette.shapes[p.shape % Palette.shapes.size]

/**
 * A person: a coloured disc with a shape inside (the shape alone tells people apart for the colour-blind),
 * or their selfie. When the room knows their level, a thin ring in the level's metal and the emblem
 * pinned at the bottom right ([level] overrides it; [showLevel] hides it).
 */
@Composable
fun Avatar(p: Player, size: Dp = 40.dp, dim: Boolean = false, ring: Boolean = false, modifier: Modifier = Modifier, level: Int? = null, showLevel: Boolean = true) {
    val lv = if (showLevel) level ?: LocalLevels.current[p.id] else null
    val tier = lv?.let(::tierOf)
    Box(modifier.size(size).alpha(if (dim) 0.35f else 1f), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size)
                .background(personColor(p), CircleShape)
                .then(
                    when {
                        ring -> Modifier.border(3.dp, Color.White, CircleShape)
                        tier != null && size >= 28.dp -> Modifier.border((size.value * 0.06f).coerceIn(1.5f, 4f).dp, Brush.linearGradient(listOf(tier.light, tier.dark)), CircleShape)
                        else -> Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val face = LocalFaces.current[p.id]
            if (face != null) androidx.compose.foundation.Image(face, p.name, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
            else Text(personShape(p), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp), color = Color.Black)
        }
        if (lv != null && size >= 28.dp) {
            val b = (size.value * 0.44f).coerceIn(15f, 34f).dp
            LevelBadge(lv, b, Modifier.align(Alignment.BottomEnd).offset(x = b * 0.28f, y = b * 0.18f))
        }
    }
}

/** Name beside the avatar; [mark] is a small state ("✓" voted, "…" thinking). */
@Composable
fun PersonChip(p: Player, dim: Boolean = false, mark: String? = null) {
    Row(
        Modifier.background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(start = 5.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(p, 30.dp, dim)
        Text(p.name, style = Tv.Headline, color = if (dim) Tv.Tertiary else Tv.Primary, maxLines = 1)
        if (mark != null) Text(mark, style = Tv.Headline, color = Color(0xFF30D158))
    }
}

/** Whole seconds until [endsAt] (the TV's wall clock), ticking once a second, and the share of the time left. */
@Composable
fun rememberCountdown(endsAt: Long?): Pair<State<Int>, State<Float>> {
    val left = remember { mutableIntStateOf(0) }
    val total = remember { mutableLongStateOf(1L) }
    val share = remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    LaunchedEffect(endsAt) {
        if (endsAt == null) return@LaunchedEffect
        total.longValue = maxOf(1L, endsAt - System.currentTimeMillis())
        while (true) {
            val ms = endsAt - System.currentTimeMillis()
            left.intValue = ((ms + 999) / 1000).toInt().coerceAtLeast(0)
            share.floatValue = (ms.toFloat() / total.longValue).coerceIn(0f, 1f)
            if (ms <= 0) break
            delay(1000)
        }
    }
    return left to share
}

/** A thin bar that empties as time runs out; one animated step per second keeps the GPU idle between ticks. */
@Composable
fun TimeBar(share: Float, modifier: Modifier = Modifier) {
    val w by animateFloatAsState(share, tween(1000, easing = LinearEasing), label = "time")
    Box(modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.18f), CircleShape)) {
        Box(Modifier.fillMaxWidth(w).height(6.dp).background(if (share < 0.25f) Color(0xFFFF453A) else Color.White, CircleShape))
    }
}

/** A stamp that lands: scales down from big with a short spring, like ink hitting paper. */
@Composable
fun Stamp(text: String, color: Color = Color(0xFFFF453A), modifier: Modifier = Modifier) {
    val scale = remember { Animatable(1.6f) }
    LaunchedEffect(text) {
        scale.snapTo(1.6f)
        scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
    }
    Text(
        text,
        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 36.sp, letterSpacing = 2.sp),
        color = color,
        modifier = modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value; rotationZ = -8f; transformOrigin = TransformOrigin.Center }
            .border(4.dp, color, RoundedCornerShape(10.dp)).padding(horizontal = 18.dp, vertical = 6.dp),
    )
}
