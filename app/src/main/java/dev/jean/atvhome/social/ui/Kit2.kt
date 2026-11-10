package dev.jean.atvhome.social.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jean.atvhome.social.game.Player
import dev.jean.atvhome.social.progress.LevelProgress
import dev.jean.atvhome.ui.theme.Inter
import dev.jean.atvhome.ui.theme.Tv

/**
 * The Social's button: a large pill with a vector icon. At rest it is a soft translucent fill; in
 * focus it turns white with black text and grows a little, like tvOS. [accent] tints the primary
 * action of a game page with that game's colour.
 */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color? = null,
    onFocus: (Boolean) -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = accent?.copy(alpha = 0.9f) ?: Color.White.copy(alpha = 0.16f), contentColor = Color.White,
            focusedContainerColor = Color.White, focusedContentColor = Color.Black, pressedContainerColor = Color.White.copy(alpha = 0.85f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        glow = ClickableSurfaceDefaults.glow(),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(20.dp), tint = if (focused) Color.Black else Color.White)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = Tv.Headline.copy(fontSize = 16.sp), color = if (focused) Color.Black else Color.White, maxLines = 1)
        }
    }
}

/** A tab that selects itself the moment it gets focus (the tvOS way): no OK needed. */
@Composable
fun TabPill(text: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.onFocusChanged { focused = it.isFocused; if (it.isFocused) onSelect() }.focusable()
            .background(if (focused) Color.White else if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(text, style = Tv.Headline.copy(fontSize = 16.sp), color = if (focused) Color.Black else if (selected) Tv.Primary else Tv.Tertiary, maxLines = 1)
    }
}

/** A few faces overlapping in a row, with a "+n" when there are more. */
@Composable
fun FaceStack(players: List<Player>, size: Dp = 30.dp, max: Int = 5, dimOffline: (Player) -> Boolean = { false }) {
    Row(horizontalArrangement = Arrangement.spacedBy(-(size.value * 0.3f).dp), verticalAlignment = Alignment.CenterVertically) {
        players.take(max).forEach { Avatar(it, size, dim = dimOffline(it), ring = true) }
        if (players.size > max) Box(Modifier.size(size).background(Color(0xFF3A3A3C), CircleShape), contentAlignment = Alignment.Center) {
            Text("+${players.size - max}", style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp), color = Color.White)
        }
    }
}

/** A ring around [content] that fills with the progress inside the level, with the level number in the middle. */
@Composable
fun LevelRing(p: LevelProgress, size: Dp = 56.dp, accent: Color = Stage.Gold, content: @Composable () -> Unit = {}) {
    val sweep by animateFloatAsState(p.fraction * 360f, tween(700), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = size.toPx() * 0.09f
            val inset = w / 2f
            val box = Size(this.size.width - w, this.size.height - w)
            drawArc(Color.White.copy(alpha = 0.16f), 0f, 360f, false, Offset(inset, inset), box, style = Stroke(w))
            drawArc(accent, -90f, sweep, false, Offset(inset, inset), box, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** A thin bar for XP: [from] to [to] are fractions of the level, animated from the first to the second. */
@Composable
fun XpBar(from: Float, to: Float, modifier: Modifier = Modifier, accent: Color = Stage.Gold) {
    val w by animateFloatAsState(to, tween(900), label = "xp")
    Box(modifier.height(8.dp).background(Color.White.copy(alpha = 0.14f), CircleShape)) {
        Box(Modifier.fillMaxWidth(if (w.isNaN()) from else w.coerceIn(0f, 1f)).height(8.dp).background(accent, CircleShape))
    }
}

/** Bronze, silver or gold for a badge's rarity. */
fun tierColor(tier: Int): Color = when (tier) { 3 -> Color(0xFFFFD60A); 2 -> Color(0xFFD1D1D6); else -> Color(0xFFCD7F32) }

fun tierName(tier: Int): String = when (tier) { 3 -> "Ouro"; 2 -> "Prata"; else -> "Bronze" }

/** Difficulty as one to three dots. */
@Composable
fun DifficultyDots(level: Int, tint: Color = Color.White) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        (1..3).forEach { Box(Modifier.size(7.dp).background(if (it <= level) tint else tint.copy(alpha = 0.25f), CircleShape)) }
    }
}


/** A segmented control: the options side by side in one rounded track, the chosen one in white. */
@Composable
fun Segmented(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = Tv.Headline.copy(fontSize = 15.sp), color = Tv.Secondary)
        Row(Modifier.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEachIndexed { i, o ->
                var focused by remember { mutableStateOf(false) }
                val on = i == selected
                Surface(
                    onClick = { onSelect(i) },
                    modifier = Modifier.onFocusChanged { focused = it.isFocused },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (on) Color.White.copy(alpha = 0.9f) else Color.Transparent, contentColor = Color.White,
                        focusedContainerColor = Color.White, focusedContentColor = Color.Black,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                ) {
                    Text(o, style = Tv.Headline.copy(fontSize = 14.sp), color = if (on || focused) Color.Black else Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), maxLines = 1)
                }
            }
        }
    }
}
