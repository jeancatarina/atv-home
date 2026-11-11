package dev.jean.atvhome.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jean.atvhome.data.Format
import dev.jean.atvhome.data.ImdbRating
import dev.jean.atvhome.ui.theme.Tv
import dev.jean.atvhome.ui.theme.LocalAccent
import dev.jean.atvhome.ui.theme.pill

/** Remembers the last focused control of a screen, so coming back lands exactly there. */
class FocusMemory {
    var last: FocusRequester? = null
    var lastInDock = false
}

val LocalFocusMemory = staticCompositionLocalOf<FocusMemory?> { null }

/** The last D-pad direction pressed, so a card can lean in from the side focus came from. */
object LastKey {
    var dx by mutableStateOf(0)
}

/**
 * A focusable card with tvOS's focus: it grows, lifts on a long soft shadow, leans in from the
 * direction the remote moved (parallax) and a specular highlight sweeps across it once.
 */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(Tv.Corner),
    focusedScale: Float = 1.1f,
    onFocus: (Boolean) -> Unit = {},
    container: Color = Tv.Card,
    focusedContainer: Color = Tv.CardFocused,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val memory = LocalFocusMemory.current
    val requester = remember { FocusRequester() }
    val sheen = remember { Animatable(-1f) }
    val lean = remember { Animatable(0f) }
    LaunchedEffect(focused) {
        if (focused) {
            lean.snapTo(LastKey.dx * 9f)
            lean.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
        }
    }
    LaunchedEffect(focused) {
        if (focused) {
            sheen.snapTo(-1f)
            sheen.animateTo(2f, tween(900, easing = FastOutSlowInEasing))
        }
    }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) { memory?.last = requester; memory?.lastInDock = false }
                onFocus(it.isFocused)
            }
            .graphicsLayer { rotationY = lean.value; cameraDistance = 14f * density },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container, focusedContainerColor = focusedContainer, pressedContainerColor = focusedContainer,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale, pressedScale = focusedScale * 0.96f),
        // No shadow glow: on this TV's GPU the blurred elevation shadow alone cost ~15 ms a frame
        glow = ClickableSurfaceDefaults.glow(),
    ) {
        Box(
            Modifier.fillMaxSize().drawWithContent {
                drawContent()
                val p = sheen.value
                if (focused && p > -1f && p < 2f) {
                    val x = size.width * p
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = 0.22f),
                            1f to Color.Transparent,
                            start = Offset(x - size.width * 0.35f, 0f),
                            end = Offset(x + size.width * 0.35f, size.height),
                        ),
                    )
                }
            },
        ) { content(focused) }
    }
}

/** tvOS pill button: translucent at rest, white with black text when focused. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    selected: Boolean = false,
    onFocus: (Boolean) -> Unit = {},
    /** Only the [icon] until focused, then the [text] too: for a row with no room to spare. */
    textWhenFocused: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val memory = LocalFocusMemory.current
    val requester = remember { FocusRequester() }
    val showText = !textWhenFocused || focused || icon == null
    Surface(
        onClick = onClick,
        modifier = modifier.focusRequester(requester).onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) { memory?.last = requester; memory?.lastInDock = false }
            onFocus(it.isFocused)
        },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.32f) else LocalAccent.current.pill(),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White.copy(alpha = 0.85f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.07f),
        glow = ClickableSurfaceDefaults.glow(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Text(icon, style = Tv.Headline, color = if (focused) Color.Black else Color.White)
                if (showText) Spacer(Modifier.width(7.dp))
            }
            if (showText) Text(text, style = Tv.Headline, color = if (focused) Color.Black else Color.White, maxLines = 1)
        }
    }
}

/** IMDb's yellow badge with the rating, and the vote count beside it when asked. */
@Composable
fun ImdbBadge(rating: ImdbRating?, showVotes: Boolean = false) {
    if (rating == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.background(Tv.ImdbYellow, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
        ) { Text("IMDb", style = Tv.Caption, color = Color.Black) }
        Spacer(Modifier.width(5.dp))
        Text("★ ${Format.rating(rating.rating)}", style = Tv.Meta, color = Tv.Primary)
        if (showVotes) {
            Spacer(Modifier.width(5.dp))
            Text(Format.votes(rating.votes), style = Tv.Meta, color = Tv.Tertiary)
        }
    }
}

/** Brazilian age rating ("L", "12", "16"…) in a thin box, like tvOS's content badges. */
@Composable
fun CertBadge(text: String?) {
    if (text.isNullOrBlank()) return
    Box(
        Modifier.border(1.dp, Tv.Secondary, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 0.dp),
    ) { Text(text, style = Tv.Caption, color = Tv.Secondary) }
}

@Composable
fun RowTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Tv.Headline, color = Tv.Secondary, modifier = modifier.padding(start = Tv.Edge, bottom = 10.dp))
}

/**
 * Landscape artwork card (Apple TV app style) with the title and "IMDb 8,1 · 2024" underneath,
 * and an optional news badge ("Agora no Prime") on the artwork.
 */
@Composable
fun ContentCard(
    image: String?,
    title: String,
    line: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 200.dp,
    badge: String? = null,
    progress: Float? = null,
    onFocus: (Boolean) -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier.width(width)) {
        FocusCard(onClick = onClick, onLongClick = onLongClick, onFocus = onFocus, modifier = Modifier.width(width).height(width * 9 / 16)) {
            AsyncImage(model = image, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (image == null) {
                Text(title, style = Tv.Headline, color = Tv.Secondary, modifier = Modifier.align(Alignment.Center).padding(12.dp), maxLines = 2)
            }
            if (badge != null) {
                Text(
                    badge, style = Tv.Caption, color = Color.Black,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        .background(Color.White, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            if (progress != null) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp).height(3.dp).background(Color.White.copy(alpha = 0.3f), CircleShape)) {
                    Box(Modifier.fillMaxWidth(progress).height(3.dp).background(Color.White, CircleShape))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = Tv.Meta, color = Tv.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!line.isNullOrBlank()) Text(line, style = Tv.Caption, color = Tv.Tertiary, maxLines = 1)
    }
}

/** 2:3 poster card for grids (search, explore, filmographies). */
@Composable
fun PosterCard(
    image: String?,
    title: String,
    line: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 112.dp,
    badge: String? = null,
) {
    Column(modifier.width(width)) {
        FocusCard(onClick = onClick, modifier = Modifier.width(width).height(width * 3 / 2), focusedScale = 1.08f) {
            AsyncImage(model = image, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (image == null) Text(title, style = Tv.Meta, color = Tv.Secondary, modifier = Modifier.align(Alignment.Center).padding(8.dp))
            if (badge != null) {
                Text(
                    badge, style = Tv.Caption, color = Color.Black,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                        .background(Color.White, RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(title, style = Tv.Caption, color = Tv.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!line.isNullOrBlank()) Text(line, style = Tv.Caption, color = Tv.Tertiary, maxLines = 1)
    }
}

/** A toggle chip for filters ("Comédia", "Netflix", "IMDb 7,5+"). */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PillButton(text = if (selected) "✓ $text" else text, onClick = onClick, selected = selected, modifier = modifier)
}

/** Round cast photo with the name and character. */
@Composable
fun PersonCard(photo: String?, name: String, role: String?, onClick: () -> Unit) {
    Column(Modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FocusCard(onClick = onClick, shape = CircleShape, modifier = Modifier.size(76.dp), focusedScale = 1.12f) {
            AsyncImage(model = photo, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
            if (photo == null) Text(name.take(1), style = Tv.Title, color = Tv.Secondary, modifier = Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = Tv.Caption, color = Tv.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!role.isNullOrBlank()) Text(role, style = Tv.Caption, color = Tv.Tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

val RowPadding = PaddingValues(horizontal = Tv.Edge)
val RowSpacing = Arrangement.spacedBy(Tv.Gap)

/**
 * Focus for a node that may not exist yet: inside a lazy list or grid, items are only there after the
 * first layout, and a hidden window lays nothing out. Tries once a frame, up to [frames] times.
 */
suspend fun FocusRequester.requestWhenReady(frames: Int = 60) {
    repeat(frames) {
        if (runCatching { requestFocus() }.isSuccess) return
        androidx.compose.runtime.withFrameNanos { }
    }
}

/**
 * A layer over Home (a screen, the Central) that never ends up with nothing focused. Home underneath
 * refuses the focus while covered, so once the focus was lost (a system screen opened and closed over
 * the launcher, as the screen-capture request does) no arrow could bring it back: [regain] puts it on
 * the layer's first item. Attach with [Modifier.focusLayer].
 */
class FocusLayer {
    val requester = FocusRequester()
    var hasFocus = false
        internal set

    /** Brings the focus into the layer if it has none; false when it already had it or there is nothing to focus. */
    fun regain(): Boolean = !hasFocus && runCatching { requester.requestFocus() }.isSuccess && hasFocus
}

fun Modifier.focusLayer(layer: FocusLayer): Modifier =
    focusRequester(layer.requester).onFocusChanged { layer.hasFocus = it.hasFocus }
