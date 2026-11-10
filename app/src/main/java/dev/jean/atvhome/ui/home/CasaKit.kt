package dev.jean.atvhome.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jean.atvhome.ui.components.FocusCard
import dev.jean.atvhome.ui.theme.Tv

/**
 * The Casa look, after Apple's Home app: a light that is on is a warm white tile with an amber disc
 * (a lamp lit in a dark room), one that is off is a dark tile with a dim disc. Flat fills and
 * hairlines only: blurred shadows and full-screen gradients cost frames on this TV's GPU.
 */
internal object Casa {
    val Off = Color(0xFF25272C)
    val OffFocused = Color(0xFF383B42)
    val On = Color(0xFFF1ECE3)
    val OnFocused = Color(0xFFFFFFFF)
    /** Text on a lit tile. */
    val Ink = Color(0xFF1C1B1A)
    val InkSoft = Color(0xFF77716A)
    val Amber = Color(0xFFFFA62B)
    val Scene = Color(0xFF8E89F5)
    val Disc = Color.White.copy(alpha = 0.09f)
    val Hairline = Color.White.copy(alpha = 0.09f)
    val Glass = Color.White.copy(alpha = 0.07f)
    val Selected = Color.White.copy(alpha = 0.18f)

    val Online = Color(0xFF32D74B)
    val Waiting = Color(0xFFFFB340)
    val Problem = Color(0xFFFF6961)

    /** The film sheet: near-black glass, a touch lighter at the top. Drawn once; the tiles have their own layers. */
    val Sheet = Brush.verticalGradient(listOf(Color(0xF7202227), Color(0xF7121315)))

    val Name = Tv.Headline.copy(fontSize = 14.sp, lineHeight = 17.sp)
    val Status = Tv.Meta.copy(fontSize = 11.sp, lineHeight = 14.sp)
    val Section = Tv.Headline.copy(fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
}

/**
 * A Casa tile: icon disc on top, name and status at the bottom. [on] turns it into the lit tile;
 * the change is a 220 ms colour fade.
 */
@Composable
internal fun CasaTile(
    icon: ImageVector,
    name: String,
    status: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    disc: Color = Casa.Amber,
) {
    val fade = tween<Color>(220)
    val rest by animateColorAsState(if (on) Casa.On else Casa.Off, fade, label = "tile")
    val focus by animateColorAsState(if (on) Casa.OnFocused else Casa.OffFocused, fade, label = "tileFocus")
    val discColor by animateColorAsState(if (on) disc else Casa.Disc, fade, label = "disc")
    FocusCard(
        onClick = onClick,
        onLongClick = onLongClick,
        focusedScale = 1.08f,
        shape = RoundedCornerShape(18.dp),
        container = rest,
        focusedContainer = focus,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Box(Modifier.size(34.dp).background(discColor, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = if (on) Color.White else Tv.Secondary, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(name, style = Casa.Name, color = if (on) Casa.Ink else Tv.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status, style = Casa.Status, color = if (on) Casa.InkSoft else Tv.Tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * A shortcut ("Apagar tudo", a room off or on): an icon disc and a label, glass at rest, white when
 * focused, like a scene button in Home.
 */
@Composable
internal fun SceneButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    tint: Color = Casa.Amber,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Casa.Glass, focusedContainerColor = Color.White, pressedContainerColor = Color.White.copy(alpha = 0.85f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        glow = ClickableSurfaceDefaults.glow(),
    ) {
        Row(Modifier.fillMaxHeight().padding(start = 9.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(if (focused) tint else Casa.Disc, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = if (focused) Color.White else tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = Casa.Name, color = if (focused) Casa.Ink else Tv.Primary, maxLines = 1)
                if (detail != null) Text(detail, style = Casa.Status, color = if (focused) Casa.InkSoft else Tv.Tertiary, maxLines = 1)
            }
        }
    }
}

/** A capsule that is part of a set (a segment, a room tab): white when focused, lighter glass when chosen. */
@Composable
internal fun Capsule(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.height(34.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Casa.Selected else Color.Transparent,
            focusedContainerColor = Color.White,
            pressedContainerColor = Color.White.copy(alpha = 0.85f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        glow = ClickableSurfaceDefaults.glow(),
    ) {
        Row(Modifier.fillMaxHeight().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) { content(focused) }
    }
}

/** A segment: its label and a quieter count ("Acesas 4"). */
@Composable
internal fun Segment(label: String, count: Int?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Capsule(selected, onClick, modifier) { focused ->
        val ink = if (focused) Casa.Ink else if (selected) Tv.Primary else Tv.Secondary
        Text(label, style = Tv.Meta.copy(fontWeight = FontWeight.SemiBold), color = ink, maxLines = 1)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(count.toString(), style = Tv.Meta, color = ink.copy(alpha = 0.55f), maxLines = 1)
        }
    }
}

/** The segments sit together on one glass capsule, like a segmented control. */
@Composable
internal fun SegmentGroup(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.background(Casa.Glass, RoundedCornerShape(50)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Whether the hub answers, as a coloured dot and a few words. */
@Composable
internal fun LinkStatus(color: Color, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(text, style = Tv.Meta, color = Tv.Secondary, maxLines = 1)
    }
}
