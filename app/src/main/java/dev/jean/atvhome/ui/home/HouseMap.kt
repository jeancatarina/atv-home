package dev.jean.atvhome.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.jean.atvhome.data.HouseModel
import dev.jean.atvhome.tuya.Feature
import dev.jean.atvhome.tuya.Floor
import dev.jean.atvhome.tuya.Light
import dev.jean.atvhome.tuya.Panel
import dev.jean.atvhome.tuya.Plan
import dev.jean.atvhome.tuya.RoomBox
import dev.jean.atvhome.tuya.Scenes
import dev.jean.atvhome.ui.components.requestWhenReady
import dev.jean.atvhome.ui.theme.Inter
import dev.jean.atvhome.ui.theme.Tv
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * "Maquete": one floor at a time as a furnished dollhouse cut away for the eye, in isometric view.
 * Back walls stand full height with windows, inner walls half, front walls low. Every room has its
 * furniture (the sofa faces the TV, which is on: you are here), each light hangs as a pendant over
 * the pool of warm light it pours on the floor, and the windows take the hour: sun on the floor by
 * day, gold at sunset, moonlight at night.
 *
 * It is alive: a floor builds itself when it comes into view (the slab settles, the walls rise back
 * to front, the furniture arrives, the lights come on one by one), a light that comes on blooms and
 * sends a ripple across the floor, one that goes off fades.
 *
 * The arrows move to the room that way on screen; past the top of a floor is the floor above (the
 * room right over this one), past the bottom the floor below. OK switches the whole room; the room's
 * lights and the scenes sit beside the model.
 *
 * One Canvas of paths and flat, linear or radial fills, no blur: it redraws only while something moves.
 */
@Composable
fun HouseMap(
    home: HouseModel,
    plan: Plan,
    toast: (String) -> Unit,
    modifier: Modifier = Modifier,
    clock: () -> LocalTime = LocalTime::now,
) {
    val statesState = home.states.collectAsState()
    val states = statesState.value
    val scope = rememberCoroutineScope()
    // It opens where you are: the TV's room and floor
    val tvAt = home.lights.firstOrNull()?.room?.let { tv -> plan.floors.indexOfFirst { f -> f.rooms.any { it.room == tv } } }?.takeIf { it >= 0 } ?: 0
    var floor by remember { mutableIntStateOf(tvAt) }
    var chosen by remember { mutableStateOf(plan.floors.getOrNull(tvAt)?.let { f -> f.rooms.firstOrNull { it.room == home.lights.firstOrNull()?.room } ?: f.rooms.firstOrNull() }) }
    var focused by remember { mutableStateOf(false) }
    val mapFocus = remember { FocusRequester() }
    val floorRooms = plan.floors.getOrNull(floor)?.rooms.orEmpty()
    val room = chosen?.takeIf { it in floorRooms } ?: floorRooms.firstOrNull()
    val inRoom = home.lights.filter { it.room == room?.room }
    val roomLit = inRoom.count { Panel.isOn(it, states) }
    val tvRoom = home.lights.firstOrNull()?.room
    val text = rememberTextMeasurer()
    // What does not move on this floor, built once; the tags' text, measured once per label
    val scene = remember(plan, floor, home.lights, tvRoom) { Scene(plan, plan.floors[floor], home.lights, tvRoom) }
    val measured = remember { HashMap<String, androidx.compose.ui.text.TextLayoutResult>() }
    fun group(of: List<Light>, on: Boolean) = scope.launch { if (!home.switchAll(of, on)) toast("Algumas luzes não responderam") }
    LaunchedEffect(Unit) { mapFocus.requestWhenReady() }

    // The floor builds itself each time it comes into view. A new floor starts unbuilt on its very
    // first frame, not built for one frame until the effect resets the animation (a flash)
    val build = remember { Animatable(0f) }
    var builtFor by remember { mutableIntStateOf(-1) }
    LaunchedEffect(floor) { build.snapTo(0f); builtFor = floor; build.animateTo(1f, tween(1700, easing = LinearEasing)) }
    // Each light's glow, 0..1 (a little over while it blooms), and the ripple it sends when it changes
    val glow = remember { mutableStateMapOf<String, Float>() }
    val ripple = remember { mutableStateMapOf<String, Float>() }
    val anims = remember { HashMap<String, Animatable<Float, AnimationVector1D>>() }
    val animScope = rememberCoroutineScope()
    LaunchedEffect(states) {
        home.lights.forEach { l ->
            val target = if (Panel.isOn(l, states)) 1f else 0f
            val a = anims.getOrPut(l.key) { Animatable(target).also { glow[l.key] = target } }
            if (a.targetValue != target) {
                animScope.launch { a.animateTo(target, if (target > 0f) spring(dampingRatio = 0.42f, stiffness = 140f) else tween(450)) { glow[l.key] = value } }
                animScope.launch { Animatable(0f).animateTo(1f, tween(1100)) { ripple[l.key] = value }; ripple.remove(l.key) }
            }
        }
    }
    // Left alone, the model tours the house: a floor every few seconds, each building itself again,
    // back to the ground floor at the top. Any key hands it back. [touched] is read only by this effect,
    // never by the composition: a key restarts the wait without recomposing the model
    var touched by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        snapshotFlow { touched }.collectLatest {
            delay(TOUR_IDLE_MS)
            while (plan.floors.size > 1) {
                val next = (floor + 1) % plan.floors.size
                floor = next
                chosen = plan.floors[next].rooms.firstOrNull()
                delay(TOUR_STEP_MS)
            }
        }
    }
    // The hour outside, checked twice a minute
    var now by remember { mutableStateOf(clock()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = clock() } }
    val sky = remember(now) { ambience(now) }
    // The chosen room's mark slides to the next room; on a new floor it starts where it is
    val mark = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    var markFloor by remember { mutableIntStateOf(-1) }
    LaunchedEffect(room, floor) {
        val r = room ?: return@LaunchedEffect
        val to = Rect(r.x, r.y, r.x + r.w, r.y + r.d)
        if (markFloor != floor) { markFloor = floor; mark.snapTo(to) } else mark.animateTo(to, spring(dampingRatio = 0.82f, stiffness = 420f))
    }

    /** Straight up or down a floor, to the room right over or under the chosen one. */
    fun jump(by: Int): Boolean {
        val target = plan.floors.getOrNull(floor + by) ?: return false
        val here = chosen
        val cx = here?.let { it.x + it.w / 2 } ?: 0f; val cy = here?.let { it.y + it.d / 2 } ?: 0f
        floor += by
        chosen = target.rooms.firstOrNull { cx in it.x..(it.x + it.w) && cy in it.y..(it.y + it.d) } ?: target.rooms.firstOrNull()
        return true
    }

    /** The room that way on screen, or the floor above / below past the top / bottom. */
    fun move(dx: Float, dy: Float): Boolean {
        // Read from the state, not from this composition: several keys can arrive before the next one
        val rooms = plan.floors.getOrNull(floor)?.rooms.orEmpty()
        val here = chosen?.takeIf { it in rooms } ?: rooms.firstOrNull() ?: return false
        val (hx, hy) = screen(here.x + here.w / 2, here.y + here.d / 2)
        val best = rooms.filter { it != here }.mapNotNull { r ->
            val (cx, cy) = screen(r.x + r.w / 2, r.y + r.d / 2)
            val vx = cx - hx; val vy = cy - hy
            val len = sqrt(vx * vx + vy * vy)
            val along = (vx * dx + vy * dy) / len
            // A wide cone: in the isometric view rooms lie on diagonals, so "up" means up-ish
            if (along < 0.25f) null else r to len * (2f - along)
        }.minByOrNull { it.second }?.first
        if (best != null) { chosen = best; return true }
        return when { dy < 0 -> jump(+1); dy > 0 -> jump(-1); else -> false }
    }

    Row(modifier.fillMaxSize().padding(start = Tv.Edge, end = Tv.Edge, top = 26.dp, bottom = 22.dp)) {
        Box(
            Modifier.weight(1f).fillMaxHeight()
                .focusRequester(mapFocus)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    touched++
                    when (e.key) {
                        Key.DirectionLeft -> move(-1f, 0f)
                        // Nothing further right: on to the room's lights beside the model
                        Key.DirectionRight -> move(1f, 0f)
                        Key.DirectionUp -> move(0f, -1f)
                        Key.DirectionDown -> move(0f, 1f)
                        // Channel ± go straight to the floor above or below, like a lift's buttons
                        Key.ChannelUp, Key.PageUp -> jump(+1)
                        Key.ChannelDown, Key.PageDown -> jump(-1)
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (inRoom.isNotEmpty()) group(inRoom, roomLit == 0); true }
                        else -> false
                    }
                }
                .focusable(),
        ) {
            // The house in a layer of its own: moving between rooms redraws only the marks over it
            Canvas(Modifier.fillMaxSize().graphicsLayer()) {
                drawFloor(scene, glow, ripple, if (builtFor == floor) build.value else 0f, sky)
            }
            Canvas(Modifier.fillMaxSize()) {
                drawMarks(scene, glow, room, mark.value, focused, text, measured, if (builtFor == floor) build.value else 0f)
            }
            Column(Modifier.align(Alignment.TopStart)) {
                FloorStack(plan.floors, floor, home.lights, states)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(sky.icon, contentDescription = null, tint = sky.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${now.format(DateTimeFormatter.ofPattern("HH:mm"))}  ·  ${sky.name}", style = Tv.Meta, color = Tv.Secondary)
                }
            }
            Text(
                "◀ ▲ ▶ ▼  cômodos, e andares no alto e embaixo     CH ±  andar     OK  acende ou apaga",
                style = Tv.Caption, color = Tv.Tertiary, modifier = Modifier.align(Alignment.BottomStart),
            )
        }
        Spacer(Modifier.width(26.dp))
        // The chosen room: what is lit, its lights, and the house's two scenes
        Column(Modifier.width(300.dp).fillMaxHeight()) {
            Text(plan.floors.getOrNull(floor)?.name.orEmpty().uppercase(), style = Tv.Caption.copy(letterSpacing = 1.4.sp), color = Casa.Amber)
            Spacer(Modifier.height(2.dp))
            Text(room?.room ?: "", style = Tv.Title, color = Tv.Primary, maxLines = 1)
            Text(
                when { inRoom.isEmpty() -> "Sem luzes"; roomLit == 0 -> "Tudo apagado"; else -> "$roomLit de ${inRoom.size} acesas" },
                style = Tv.Meta, color = Tv.Secondary,
            )
            Spacer(Modifier.height(14.dp))
            // One grid for every room, its tiles kept by place: a new room refills the tiles already
            // there instead of building new ones (on this TV, building them cost most of a room change)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            ) {
                items(inRoom.size, key = { it }) { i ->
                    val light = inRoom[i]
                    LightTile(
                        light = light,
                        states = statesState,
                        showRoom = false,
                        modifier = Modifier.fillMaxWidth().height(92.dp),
                        onClick = { scope.launch { if (!home.toggle(light)) toast("Não consegui falar com o hub") } },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SceneButton(Icons.Rounded.Movie, "Cinema", { group(Scenes.cinema(home.lights, plan), false) }, tint = Casa.Scene)
                SceneButton(Icons.Rounded.Bedtime, "Boa noite", {
                    scope.launch { if (!home.cascade(Scenes.goodNight(home.lights, plan), false)) toast("Algumas luzes não responderam") }
                }, tint = Color(0xFF5E5CE6))
            }
        }
    }
}

/** The direction on screen of a point of the footprint (the model's own iso axes). */
private fun screen(x: Float, y: Float) = (x - y) * COS to (x + y) * SIN

/** The floors as a little stack, top floor first, the one in view in amber, each with what is lit on it. */
@Composable
private fun FloorStack(floors: List<Floor>, current: Int, lights: List<Light>, states: Map<String, Boolean>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        floors.indices.reversed().forEach { k ->
            val on = k == current
            val rooms = floors[k].rooms.map { it.room }.toSet()
            val lit = lights.count { it.room in rooms && Panel.isOn(it, states) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(width = 34.dp, height = 18.dp)) {
                    val w = size.width; val h = size.height
                    val top = Path().apply { moveTo(w / 2, 0f); lineTo(w, h * 0.38f); lineTo(w / 2, h * 0.76f); lineTo(0f, h * 0.38f); close() }
                    val side = Path().apply { moveTo(0f, h * 0.38f); lineTo(w / 2, h * 0.76f); lineTo(w, h * 0.38f); lineTo(w, h * 0.62f); lineTo(w / 2, h); lineTo(0f, h * 0.62f); close() }
                    drawPath(side, if (on) Color(0xFF9A5F17) else Color.White.copy(alpha = 0.06f))
                    drawPath(top, if (on) Casa.Amber else Color.White.copy(alpha = 0.12f))
                    // A lit floor glows a little even when it is not the one in view
                    if (!on && lit > 0) drawCircle(Casa.Amber, radius = 2.5.dp.toPx(), center = Offset(w / 2, h * 0.38f))
                }
                Spacer(Modifier.width(10.dp))
                Text(floors[k].name, style = Tv.Meta.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium), color = if (on) Tv.Primary else Tv.Tertiary)
                if (lit > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text("$lit", style = Tv.Caption.copy(fontWeight = FontWeight.Bold), color = Casa.Amber)
                }
            }
        }
    }
}

// --- Drawing ------------------------------------------------------------------------------------

private const val COS = 0.866f
private const val SIN = 0.5f
private const val SLAB = 0.42f
private const val T = 0.12f
private const val BACK = 1.45f
private const val INNER = 0.62f
private const val FRONT = 0.3f
private const val RAIL = 0.6f
/** No key for this long and the model starts touring the floors, one every [TOUR_STEP_MS]. */
private const val TOUR_IDLE_MS = 30_000L
private const val TOUR_STEP_MS = 8_000L
/** A doorway's width in an inner wall. */
private const val DOOR = 0.85f
/** Where the pendants hang. */
private const val PENDANT = 1.18f

private val Warm = Color(0xFFFFB861)
// Saturated enough to show on light oak by day: which lights are on must read at any hour
private val Pool = Color(0xFFFFAE4D)
private val Bulb = Color(0xFFFFF1CC)

// An interior designer's model: warm off-white walls, light oak, terrazzo where it gets wet. The
// hour washes it (blue at night, so the lamps' warmth shows)
private val FloorDry = Color(0xFFD3BB97)
private val FloorWood = Color(0xFFC6A784)
private val FloorWet = Color(0xFFDAD5CD)
private val FloorDeck = Color(0xFFAE8862)
private val SlabLeft = Color(0xFF3B3732)
private val SlabRight = Color(0xFF2E2B27)
private val Wall = Color(0xFFE7E2D9)
private val WallCap = Color(0xFFF6F3EE)
private val Grain = Color.Black.copy(alpha = 0.06f)

// Furniture: oat, terracotta, mustard, sage and oak, like a page of an interiors magazine
private val Wood = Color(0xFFC9A57A)
private val Walnut = Color(0xFF8B6448)
private val Fabric = Color(0xFFDAD0C0)
private val Terracotta = Color(0xFFCF8B6C)
private val Mustard = Color(0xFFD9B25C)
private val Sage = Color(0xFFA7B59C)
private val Linen = Color(0xFFF1ECE4)
private val Duvet = Color(0xFFB7C6D6)
private val Cabinet = Color(0xFFEDEAE4)
private val Porcelain = Color(0xFFF3F4F6)
private val Black = Color(0xFF1C1D20)
private val Graphite = Color(0xFF3A3D44)
private val Leaf = Color(0xFF5E9159)
private val LeafLight = Color(0xFF86B874)
private val Planter = Color(0xFFC77D5B)
private val Rug = Color(0xFFE6DCCB)
private val Brass = Color(0xFFC9A15A)
private val Art = listOf(Terracotta to Mustard, Sage to Linen, Duvet to Terracotta, Mustard to Sage)

/** [dz]: the whole floor raised, while it settles into place. */
private class Iso(val s: Float, val ox: Float, val oy: Float, val dz: Float = 0f) {
    fun p(x: Float, y: Float, z: Float = 0f) = Offset(ox + (x - y) * COS * s, oy + (x + y) * SIN * s - (z + dz) * s)
}

/** The hour's light on the model. */
private class Sky(
    val name: String, val icon: ImageVector, val accent: Color,
    val glow: Color, val wash: Color, val amount: Float, val pools: Float,
    val glass: Color, val sun: Color,
)

private fun ambience(t: LocalTime): Sky {
    val h = t.hour + t.minute / 60f
    return when {
        h < 5f || h >= 20f -> Sky("noite", Icons.Rounded.DarkMode, Color(0xFF9DB4FF), Color(0xFF3550A0), Color(0xFF1A2444), 0.62f, 1.45f, Color(0xFF16203D), Color(0xFF8FA8FF).copy(alpha = 0.05f))
        h < 7f -> Sky("amanhecer", Icons.Rounded.WbTwilight, Color(0xFFFFB3A1), Color(0xFF8A5A7A), Color(0xFF3A2C44), 0.34f, 1.1f, Color(0xFFE9A7A0), Color(0xFFFFC2B0).copy(alpha = 0.1f))
        h < 17f -> Sky("dia", Icons.Rounded.WbSunny, Color(0xFFFFE08A), Color(0xFFC8D4E6), Color(0xFF8C9AB0), 0.05f, 1.25f, Color(0xFFAFCFEA), Color(0xFFFFF4D6).copy(alpha = 0.13f))
        else -> Sky("pôr do sol", Icons.Rounded.WbTwilight, Color(0xFFFFB066), Color(0xFFC0682E), Color(0xFF4C2E1E), 0.36f, 1.2f, Color(0xFFF2A15E), Color(0xFFFFB066).copy(alpha = 0.14f))
    }
}

/** Linear 0..1 to eased, over the part of the build between [from] and [to]. */
private fun stage(build: Float, from: Float, to: Float): Float {
    val t = ((build - from) / (to - from)).coerceIn(0f, 1f)
    return 1f - (1f - t) * (1f - t) * (1f - t)
}

private fun DrawScope.fit(plan: Plan, dz: Float = 0f): Iso {
    val w = (plan.width + plan.depth) * COS
    val h = (plan.width + plan.depth) * SIN + BACK + SLAB
    val s = minOf(size.width / w, size.height / h) * 0.9f
    val ox = (size.width - w * s) / 2f + plan.depth * COS * s
    val oy = (size.height - h * s) / 2f + BACK * s
    return Iso(s, ox, oy, dz)
}

private fun Iso.poly(vararg pts: Triple<Float, Float, Float>) = Path().apply {
    pts.forEachIndexed { i, (x, y, z) -> val o = p(x, y, z); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
    close()
}

private fun Iso.rect(x0: Float, y0: Float, x1: Float, y1: Float, z: Float = 0f) =
    poly(Triple(x0, y0, z), Triple(x1, y0, z), Triple(x1, y1, z), Triple(x0, y1, z))

private fun path(points: List<Offset>) = Path().apply {
    points.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
    close()
}

/** A rounded rectangle of the footprint as points, corners of radius [r] (a circle or a pill at most). */
private fun rounded(x0: Float, y0: Float, x1: Float, y1: Float, r: Float): List<Pair<Float, Float>> {
    val rr = r.coerceAtMost(minOf(x1 - x0, y1 - y0) / 2f)
    if (rr <= 0.001f) return listOf(x0 to y0, x1 to y0, x1 to y1, x0 to y1)
    val out = ArrayList<Pair<Float, Float>>(28)
    val corners = listOf(Triple(x1 - rr, y0 + rr, -90f), Triple(x1 - rr, y1 - rr, 0f), Triple(x0 + rr, y1 - rr, 90f), Triple(x0 + rr, y0 + rr, 180f))
    corners.forEach { (cx, cy, start) ->
        for (i in 0..6) {
            val a = Math.toRadians((start + i * 15f).toDouble())
            out += (cx + rr * kotlin.math.cos(a).toFloat()) to (cy + rr * kotlin.math.sin(a).toFloat())
        }
    }
    return out
}

/** The outline of a set of points on screen (monotone chain): the silhouette of a rounded prism. */
private fun hull(points: List<Offset>): List<Offset> {
    val p = points.sortedWith(compareBy({ it.x }, { it.y }))
    if (p.size < 3) return p
    fun cross(o: Offset, a: Offset, b: Offset) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    val lower = ArrayList<Offset>(); val upper = ArrayList<Offset>()
    p.forEach { q -> while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), q) <= 0) lower.removeAt(lower.lastIndex); lower += q }
    p.asReversed().forEach { q -> while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), q) <= 0) upper.removeAt(upper.lastIndex); upper += q }
    return lower.dropLast(1) + upper.dropLast(1)
}

private val WET = Regex("(?i)banheiro|lavabo|cozinha|toalete|área de serviço|lavanderia")

/** Where each light hangs in its room: a small grid, so a room with three lights has three pools. */
private fun spots(r: RoomBox, n: Int): List<Pair<Float, Float>> {
    if (n == 0) return emptyList() // a room with no lights (the dining room) has no spots
    val cols = ceil(sqrt(n.toFloat() * r.w / r.d)).toInt().coerceIn(1, n)
    val rows = ceil(n / cols.toFloat()).toInt()
    return (0 until n).map { i -> r.x + r.w * ((i % cols) + 0.5f) / cols to r.y + r.d * ((i / cols) + 0.5f) / rows }
}

/**
 * A box of the model: a wall, or a piece of furniture with rounded corners ([radius]; a cylinder
 * when it is half the box), drawn far to near. [room]: whose light warms it. [screen]: a glowing
 * face (a TV, a monitor), on the +x or +y side, whichever the box is thin in. [glass]: a railing.
 * [bush]: leaves on top (a planter). [lamp]: a shade that glows when its room is lit.
 */
private class Block(
    val x0: Float, val y0: Float, val x1: Float, val y1: Float, val z0: Float, val z1: Float,
    val color: Color, val wall: Boolean, val room: RoomBox?, val glass: Boolean = false, val screen: Boolean = false,
    val top: Color? = null,
    /** The house's outline: its back walls go first, its front walls last, whatever their length. */
    val order: Float? = null,
    val radius: Float = 0f,
    val bush: Boolean = false,
    val lamp: Boolean = false,
) {
    /**
     * Painter's order: by the box's far corner. A long wall's centre would put it after a bed
     * standing against it; its far corner does not.
     */
    val depth: Float get() = order ?: (x0 + y0)
}

private fun walls(plan: Plan, floor: Floor): List<Block> {
    val w = plan.width; val d = plan.depth
    val out = ArrayList<Block>()
    fun along(xa: Float, xb: Float, y: Float, h: Float, order: Float? = null) = out.add(Block(xa, y - T / 2, xb, y + T / 2, 0f, h, Wall, wall = true, room = null, glass = floor.outdoor, order = order))
    fun across(ya: Float, yb: Float, x: Float, h: Float, order: Float? = null) = out.add(Block(x - T / 2, ya, x + T / 2, yb, 0f, h, Wall, wall = true, room = null, glass = floor.outdoor, order = order))
    // The house's own outline: tall at the back (the street front, with its windows), low at the
    // front of the view, a railing on the terrace
    val back = if (floor.outdoor) RAIL else BACK
    val front = if (floor.outdoor) RAIL else FRONT
    along(0f, w, 0f, back, order = -1000f); across(0f, d, 0f, back, order = -1000f)
    along(0f, w, d, front, order = 1000f); across(0f, d, w, front, order = 1000f)
    if (!floor.open && !floor.outdoor) {
        // Inner walls with a doorway: rooms open onto each other, as in a house (not sealed boxes)
        fun door(a: Float, b: Float, wall: (Float, Float) -> Unit) {
            if (b - a < 1.6f) { wall(a, b); return }
            val gap = a + (b - a) * 0.22f
            wall(a, gap); wall(gap + DOOR, b)
        }
        floor.rooms.forEach { r ->
            if (r.x + r.w < w - 0.01f) door(r.y, r.y + r.d) { a, b -> across(a, b, r.x + r.w, INNER) }
            if (r.y + r.d < d - 0.01f) door(r.x, r.x + r.w) { a, b -> along(a, b, r.y + r.d, INNER) }
        }
    }
    return out
}

/**
 * The furniture that says what a room is, sized to it and soft-cornered. Recognised by name, in
 * Portuguese, the way the Tuya app names rooms; a room it does not know stays empty. The TV hangs on
 * the side wall (x = 0), never under a window, and the sofa faces it.
 */
private fun furniture(room: RoomBox, outdoor: Boolean, tv: Boolean, features: List<Feature> = emptyList()): List<Block> {
    val out = ArrayList<Block>()
    // Outside, the loungers go on the tiled terrace when the plan draws one (not on the lawn or the deck)
    val r = features.firstOrNull { outdoor && it.kind == "ceramic" }?.let { RoomBox(room.room, it.x, it.y, it.w, it.d) } ?: room
    val x0 = r.x + 0.25f; val y0 = r.y + 0.25f; val x1 = r.x + r.w - 0.25f; val y1 = r.y + r.d - 0.25f
    val cx = (x0 + x1) / 2; val cy = (y0 + y1) / 2; val w = x1 - x0; val d = y1 - y0
    fun box(ax: Float, ay: Float, bx: Float, by: Float, z0: Float, z1: Float, c: Color, top: Color? = null, radius: Float = 0.08f, bush: Boolean = false, lamp: Boolean = false, screen: Boolean = false) =
        out.add(Block(ax, ay, bx, by, z0, z1, c, wall = false, room = r, top = top, radius = radius, bush = bush, lamp = lamp, screen = screen))
    fun round(x: Float, y: Float, radius: Float, z0: Float, z1: Float, c: Color, top: Color? = null, bush: Boolean = false, lamp: Boolean = false) =
        box(x - radius, y - radius, x + radius, y + radius, z0, z1, c, top, radius = radius, bush = bush, lamp = lamp)
    fun plant(x: Float, y: Float, size: Float = 0.28f) = round(x, y, size, 0f, 0.42f, Planter, bush = true)
    val name = r.room.lowercase()
    when {
        outdoor -> {
            // Loungers with their backs raised, a round table under the sun, planters round the edge
            val lw = minOf(0.72f, w * 0.14f)
            listOf(x0 + 0.4f, x0 + 0.4f + lw + 0.45f).forEach { lx ->
                box(lx, y0 + d * 0.3f + 0.4f, lx + lw, y0 + d * 0.3f + 1.9f, 0f, 0.24f, Linen, radius = 0.14f)
                box(lx, y0 + d * 0.3f, lx + lw, y0 + d * 0.3f + 0.42f, 0f, 0.5f, Linen, radius = 0.14f)
            }
            round(x1 - 1.3f, cy, 0.42f, 0f, 0.5f, Wood, top = Linen)
            // Planters round the edge, unless the plan places its own
            if (features.none { it.kind == "planter" }) {
                listOf(x0 + 0.3f to y0 + 0.3f, x1 - 0.3f to y0 + 0.3f, x1 - 0.3f to y1 - 0.3f, x0 + 0.3f to y1 - 0.3f, x1 - 0.3f to cy).forEach { (px, py) -> plant(px, py, 0.3f) }
            }
        }
        tv || name.contains("estar") || name.contains("living") -> {
            // The TV on the side wall, on: you are here. The sofa faces it across a round table on a rug
            val tl = minOf(2f, d * 0.5f)
            box(r.x + T / 2, cy - tl / 2 - 0.2f, r.x + T / 2 + 0.42f, cy + tl / 2 + 0.2f, 0f, 0.38f, Walnut, radius = 0.05f)
            if (tv) box(r.x + T / 2, cy - tl / 2, r.x + T / 2 + 0.06f, cy + tl / 2, 0.52f, 1.16f, Black, radius = 0f, screen = true)
            val sl = minOf(3f, d * 0.7f)
            val sx = minOf(x1 - 0.95f, x0 + w * 0.62f)
            box(sx, cy - sl / 2, sx + 0.95f, cy + sl / 2, 0f, 0.36f, Fabric, radius = 0.16f) // seat
            box(sx + 0.68f, cy - sl / 2, sx + 0.95f, cy + sl / 2, 0.3f, 0.78f, Fabric, radius = 0.13f) // back
            box(sx, cy - sl / 2, sx + 0.95f, cy - sl / 2 + 0.24f, 0.3f, 0.58f, Fabric, radius = 0.11f) // arms
            box(sx, cy + sl / 2 - 0.24f, sx + 0.95f, cy + sl / 2, 0.3f, 0.58f, Fabric, radius = 0.11f)
            listOf(-0.5f to Terracotta, 0.5f to Mustard).forEach { (o, c) -> box(sx + 0.12f, cy + o * (sl - 0.6f) / 2 - (sl - 0.6f) / 4 + 0.02f, sx + 0.66f, cy + o * (sl - 0.6f) / 2 + (sl - 0.6f) / 4 - 0.02f, 0.36f, 0.48f, c, radius = 0.14f) }
            round((r.x + 0.5f + sx) / 2 + 0.2f, cy, 0.42f, 0f, 0.3f, Wood, top = Linen)
            plant(x0 + 0.3f, y1 - 0.3f, 0.3f)
            round(sx + 0.5f, y0 + 0.3f, 0.2f, 0f, 1.05f, Walnut, lamp = true) // a floor lamp by the sofa
        }
        name.contains("jantar") -> {
            // A long table down the house's length (the kitchen island goes on as the table), chairs either side
            val tl = minOf(2.6f, d * 0.8f); val td = 0.95f
            val tx = cx - td / 2
            (0 until 3).forEach { i ->
                val chy = cy - tl / 2 + tl * (i + 0.5f) / 3
                round(tx - 0.35f, chy, 0.2f, 0f, 0.46f, Linen)
            }
            box(tx, cy - tl / 2, tx + td, cy + tl / 2, 0f, 0.56f, Wood, top = Color(0xFFD8B78C), radius = 0.12f)
            (0 until 3).forEach { i ->
                val chy = cy - tl / 2 + tl * (i + 0.5f) / 3
                round(tx + td + 0.35f, chy, 0.2f, 0f, 0.46f, Linen)
            }
            plant(x0 + 0.35f, y1 - 0.35f, 0.32f)
        }
        name.contains("cozinha") -> {
            // Counter along the wall opposite the stairs (the TV's wall), an island with stools
            box(r.x + r.w - T / 2 - 0.62f, y0, r.x + r.w - T / 2, y1, 0f, 0.9f, Cabinet, top = Wood, radius = 0.04f)
            val il = minOf(2.2f, d * 0.6f)
            box(cx - 0.45f, cy - il / 2, cx + 0.45f, cy + il / 2, 0f, 0.9f, Sage, top = Cabinet, radius = 0.1f)
            listOf(-0.6f, 0f, 0.6f).forEach { o -> round(cx - 0.78f, cy + o, 0.17f, 0f, 0.62f, Graphite) }
        }
        name.contains("quarto") || name.contains("suíte") || name.contains("suite") || name.contains("ninho") || name.contains("principal") -> {
            // A bed against the side wall: headboard, duvet, two pillows; bedside tables with lamps
            val bw = if (d < 2.6f) minOf(1.1f, d * 0.6f) else minOf(1.8f, d * 0.55f)
            val bl = minOf(2.1f, w * 0.6f)
            box(r.x + T / 2, cy - bw / 2 - 0.05f, r.x + T / 2 + 0.12f, cy + bw / 2 + 0.05f, 0f, 0.92f, Walnut, radius = 0.05f)
            box(r.x + T / 2 + 0.12f, cy - bw / 2, r.x + T / 2 + 0.12f + bl, cy + bw / 2, 0f, 0.42f, Linen, radius = 0.12f)
            box(r.x + T / 2 + 0.12f + bl * 0.72f, cy - bw / 2 - 0.02f, r.x + T / 2 + 0.12f + bl * 0.92f, cy + bw / 2 + 0.02f, 0.42f, 0.53f, Terracotta, radius = 0.06f) // a throw
            box(r.x + T / 2 + 0.7f, cy - bw / 2 + 0.02f, r.x + T / 2 + 0.12f + bl, cy + bw / 2 - 0.02f, 0.42f, 0.5f, Duvet, radius = 0.12f)
            val pw = if (bw > 1.3f) 2 else 1
            (0 until pw).forEach { i ->
                val py0 = cy - bw / 2 + 0.1f + i * (bw - 0.2f) / pw
                box(r.x + T / 2 + 0.2f, py0 + 0.04f, r.x + T / 2 + 0.58f, py0 + (bw - 0.2f) / pw - 0.04f, 0.42f, 0.56f, Porcelain, radius = 0.12f)
            }
            if (d - bw > 1.2f) listOf(cy - bw / 2 - 0.4f, cy + bw / 2 + 0.4f).forEach { ny ->
                box(r.x + T / 2 + 0.05f, ny - 0.22f, r.x + T / 2 + 0.5f, ny + 0.22f, 0f, 0.5f, Wood, radius = 0.06f)
                round(r.x + T / 2 + 0.28f, ny, 0.11f, 0.5f, 0.78f, Linen, lamp = true)
            }
        }
        name.contains("escritório") || name.contains("escritorio") -> {
            // A desk under the window with a monitor that is on, a round chair, a shelf, a plant
            val dw = minOf(1.8f, w * 0.6f)
            box(cx - dw / 2, y0, cx + dw / 2, y0 + 0.7f, 0f, 0.74f, Wood, top = Linen, radius = 0.06f)
            box(cx - 0.42f, y0 + 0.12f, cx + 0.42f, y0 + 0.18f, 0.8f, 1.22f, Black, radius = 0f, screen = true)
            round(cx, y0 + 1.25f, 0.24f, 0f, 0.48f, Graphite)
            box(x1 - 0.3f, y0 + 1f, x1, minOf(y1, y0 + 2.6f), 0f, 0.95f, Walnut, radius = 0.04f)
            plant(x0 + 0.3f, y1 - 0.3f)
        }
        name.contains("banheiro") || name.contains("lavabo") || name.contains("toalete") -> {
            box(r.x + T / 2, y0, r.x + T / 2 + 0.55f, y0 + minOf(1.3f, d * 0.55f), 0f, 0.86f, Porcelain, radius = 0.1f)
            box(x1 - 0.5f, y1 - 0.68f, x1, y1, 0f, 0.42f, Porcelain, radius = 0.22f)
            if (w > 3f) box(x1 - 1.3f, y0, x1, y0 + 1.1f, 0f, 0.06f, Color(0xFF8FB4C8), radius = 0.04f) // the shower's tray
        }
        name.contains("corredor") || name.contains("hall") || name.contains("escada") -> {
            // The stairs along the room's long side, rising towards the street, with a plant at their foot
            val steps = 9
            if (d > w) {
                val sw = minOf(1.1f, w * 0.6f)
                val run = minOf(d * 0.8f, 3.4f) / steps
                (0 until steps).forEach { i ->
                    val sy = y1 - (i + 1) * run
                    box(x1 - sw, sy, x1, sy + run, 0f, (i + 1) * 0.15f, Wood, top = Color(0xFFD8B78C), radius = 0f)
                }
                plant(x0 + 0.25f, y1 - 0.25f, 0.22f)
            } else {
                val sw = minOf(1.1f, d * 0.6f)
                val run = minOf(w * 0.75f, 3.2f) / steps
                (0 until steps).forEach { i ->
                    val sx = x1 - (i + 1) * run
                    box(sx, cy - sw / 2, sx + run, cy + sw / 2, 0f, (i + 1) * 0.15f, Wood, top = Color(0xFFD8B78C), radius = 0f)
                }
                plant(x1 - 0.25f, y1 - 0.25f, 0.22f)
            }
        }
    }
    return out
}

/**
 * Everything about a floor that does not move: the lights by room and where they hang, the walls and
 * furniture already in painter's order, each soft piece's silhouette (which of its outline's points
 * make the hull: it does not depend on the height, so it is worked out once), the rugs, the windows.
 * Built once per floor; a frame then only projects and fills.
 */
private class Scene(val plan: Plan, val floor: Floor, lights: List<Light>, val tvRoom: String?) {
    val w = plan.width
    val d = plan.depth
    val inRoom: Map<String, List<Light>> = floor.rooms.associate { r -> r.room to lights.filter { it.room == r.room } }
    val spots: Map<String, List<Pair<Float, Float>>> = floor.rooms.associate { r -> r.room to spots(r, inRoom.getValue(r.room).size) }
    val order: Map<String, Int> = floor.rooms.flatMap { inRoom.getValue(it.room) }.withIndex().associate { (i, l) -> l.key to i }
    val tvBox: RoomBox? = floor.rooms.firstOrNull { it.room == tvRoom }.takeIf { !floor.outdoor }
    fun roomAt(x: Float, y: Float) = floor.rooms.firstOrNull { x >= it.x && x <= it.x + it.w && y >= it.y && y <= it.y + it.d }

    class Piece(val b: Block, val depth: Float, val faceRoomY: RoomBox?, val faceRoomX: RoomBox?, val heatRoom: RoomBox?,
                val outline: List<Pair<Float, Float>>, val lower: IntArray, val upper: IntArray, val shadow: List<Pair<Float, Float>>?)

    val pieces: List<Piece>
    val windows: List<Pair<Float, Float>>

    /**
     * Projected paths, kept once the floor has settled and a piece has risen: from then on a frame
     * only changes colours, so moving between rooms or a light coming on allocates nothing.
     */
    val cache = androidx.collection.MutableIntObjectMap<Path>()
    var cacheFor = Size.Zero
    val rugs: List<Pair<List<Pair<Float, Float>>, List<Pair<Float, Float>>>>
    val art: List<RoomBox>

    init {
        val furnish = floor.rooms.flatMap { furniture(it, floor.outdoor, it.room == tvRoom, floor.features) } + floor.features.mapNotNull { f ->
            val host = roomAt(f.x + f.w / 2, f.y + f.d / 2)
            when (f.kind) {
                // The jacuzzi: white shell, water on top
                "jacuzzi" -> Block(f.x, f.y, f.x + f.w, f.y + f.d, 0f, 0.55f, Porcelain, wall = false, room = host, top = Color(0xFF7CC6DF), radius = 0.35f)
                // The water tank's housing over the stairs, concrete
                "tank" -> Block(f.x, f.y, f.x + f.w, f.y + f.d, 0f, 1.35f, Color(0xFFB9B4AC), wall = false, room = host, top = Color(0xFFCFCAC2), radius = 0.04f)
                "planter" -> { val rr = minOf(f.w, f.d) / 2; Block(f.x, f.y, f.x + 2 * rr, f.y + 2 * rr, 0f, 0.45f, Planter, wall = false, room = host, radius = rr, bush = true) }
                // A built-in concrete bench (burnished cement, like the stairs on the plans)
                "bench" -> Block(f.x, f.y, f.x + f.w, f.y + f.d, 0f, 0.45f, Color(0xFFAFA9A0), wall = false, room = host, top = Color(0xFFC4BEB5), radius = 0.04f)
                else -> null
            }
        } + floor.features.filter { it.kind == "flight" || it.kind == "flight-front" }.flatMap { f ->
            // A flight of stairs along its long side, rising towards the back of the house (y = 0), or
            // towards the front ("flight-front")
            val host = roomAt(f.x + f.w / 2, f.y + f.d / 2)
            val steps = 12
            val forward = f.kind == "flight-front"
            (0 until steps).map { i ->
                if (f.d >= f.w) {
                    val run = f.d / steps; val sy = if (forward) f.y + i * run else f.y + f.d - (i + 1) * run
                    Block(f.x, sy, f.x + f.w, sy + run, 0f, (i + 1) * 0.11f, Color(0xFFAFA9A0), wall = false, room = host, top = Color(0xFFC4BEB5), radius = 0f)
                } else {
                    val run = f.w / steps; val sx = f.x + f.w - (i + 1) * run
                    Block(sx, f.y, sx + run, f.y + f.d, 0f, (i + 1) * 0.11f, Color(0xFFAFA9A0), wall = false, room = host, top = Color(0xFFC4BEB5), radius = 0f)
                }
            }
        }
        pieces = (walls(plan, floor) + furnish).sortedBy { it.depth }.map { b ->
            val outline = if (b.radius > 0.001f) rounded(b.x0, b.y0, b.x1, b.y1, b.radius) else emptyList()
            // The silhouette: from the leftmost to the rightmost point on screen, the outline's lower
            // chain at the foot and its upper chain at the top
            var lower = IntArray(0); var upper = IntArray(0)
            if (outline.isNotEmpty()) {
                val sx = outline.map { (x, y) -> x - y }; val sy = outline.map { (x, y) -> x + y }
                val l = sx.indices.minBy { sx[it] }; val r = sx.indices.maxBy { sx[it] }
                val n = outline.size
                fun arc(from: Int, to: Int): IntArray { val out = ArrayList<Int>(); var i = from; while (true) { out += i; if (i == to) break; i = (i + 1) % n }; return out.toIntArray() }
                val a = arc(l, r); val c = arc(r, l)
                val aLow = a.sumOf { sy[it].toDouble() } / a.size > c.sumOf { sy[it].toDouble() } / c.size
                // The foot runs left to right along the lower arc, the top comes back right to left
                lower = if (aLow) a else c.reversedArray()
                upper = if (aLow) c else a.reversedArray()
            }
            val shadow = if (!b.wall && !b.screen && b.z0 <= 0.05f) rounded(b.x0 - 0.08f, b.y0 - 0.04f, b.x1 + 0.14f, b.y1 + 0.16f, b.radius + 0.1f) else null
            Piece(
                b, (b.x0 + b.x1 + b.y0 + b.y1) / 2f / (w + d),
                if (b.wall) roomAt((b.x0 + b.x1) / 2, b.y1 + 0.3f) else b.room,
                if (b.wall) roomAt(b.x1 + 0.3f, (b.y0 + b.y1) / 2) else b.room,
                b.room ?: roomAt((b.x0 + b.x1) / 2, (b.y0 + b.y1) / 2),
                outline, lower, upper, shadow,
            )
        }
        windows = if (floor.outdoor) emptyList() else floor.rooms.filter { it.y < 0.01f }.flatMap { r ->
            val n = if (r.w >= 4.5f) 2 else 1
            (0 until n).map { i -> r.x + r.w * (i + 0.5f) / n to minOf(1.5f, r.w * 0.42f / n * 1.6f) }
        }
        rugs = if (floor.outdoor) emptyList() else floor.rooms.filter { it.room == tvRoom || it.room.contains("estar", true) }.map { r ->
            val cy = r.y + r.d / 2; val rl = minOf(3f, r.d * 0.62f)
            rounded(r.x + 0.8f, cy - rl / 2, r.x + r.w * 0.78f, cy + rl / 2, 0.35f) to
                rounded(r.x + 0.95f, cy - rl / 2 + 0.15f, r.x + r.w * 0.78f - 0.15f, cy + rl / 2 - 0.15f, 0.25f)
        }
        art = floor.rooms.filter { it.x < 0.01f && it.room != tvRoom && !WET.containsMatchIn(it.room) && it.d >= 2f }
    }
}

private val TagStyle = TextStyle(fontFamily = Inter, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)

private fun DrawScope.drawFloor(
    scene: Scene,
    glow: Map<String, Float>,
    ripple: Map<String, Float>,
    build: Float,
    sky: Sky,
) {
    val floor = scene.floor
    // The build: the slab settles from above, the walls rise back to front, the furniture arrives,
    // then the lights come on one after another and the names appear
    val settle = stage(build, 0f, 0.35f)
    val iso = fit(scene.plan, dz = (1f - settle) * 0.9f)
    val w = scene.w; val d = scene.d
    fun g(l: Light): Float {
        val i = scene.order[l.key] ?: 0
        return (glow[l.key] ?: 0f) * stage(build, 0.62f + i * 0.03f, 0.82f + i * 0.03f)
    }
    // Each room's warmth, once a frame (every wall and piece reads it)
    val heat = HashMap<RoomBox, Float>(floor.rooms.size * 2)
    floor.rooms.forEach { r ->
        val inRoom = scene.inRoom.getValue(r.room)
        val sum = inRoom.sumOf { g(it).coerceIn(0f, 1f).toDouble() }.toFloat()
        heat[r] = if (inRoom.isEmpty() || sum <= 0.001f) 0f else (0.35f * sum.coerceAtMost(1f) + 0.65f * sum / inRoom.size)
    }
    fun warmth(r: RoomBox?) = if (r == null) 0f else heat[r] ?: 0f
    fun washed(c: Color, amount: Float = sky.amount) = lerp(c, sky.wash, amount)
    fun pts(outline: List<Pair<Float, Float>>, z: Float = 0f) = outline.map { (x, y) -> iso.p(x, y, z) }
    val stable = settle >= 0.999f
    if (scene.cacheFor != size) { scene.cache.clear(); scene.cacheFor = size }
    fun cached(id: Int, done: Boolean = true, make: () -> Path): Path =
        if (stable && done) scene.cache.getOrPut(id, make) else make()
    /** Many short lines as one path: one draw call instead of one each. */
    fun lines(segments: List<Pair<Offset, Offset>>) = Path().apply { segments.forEach { (a, b) -> moveTo(a.x, a.y); lineTo(b.x, b.y) } }

    // A soft shadow under the model, on the "table" it stands on (the hour's colour is the screen's own:
    // a gradient across the whole screen cost this TV's GPU a frame's budget)
    scale(1f, SIN / COS, pivot = iso.p(w / 2, d / 2, -SLAB)) {
        val c = iso.p(w / 2, d / 2, -SLAB)
        val r = (w + d) * 0.44f * COS * iso.s
        drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.32f * settle), Color.Transparent), c, r), r, c)
    }
    drawPath(cached(600) { iso.poly(Triple(0f, d, 0f), Triple(w, d, 0f), Triple(w, d, -SLAB), Triple(0f, d, -SLAB)) }, SlabLeft, alpha = settle)
    drawPath(cached(601) { iso.poly(Triple(w, 0f, 0f), Triple(w, d, 0f), Triple(w, d, -SLAB), Triple(w, 0f, -SLAB)) }, SlabRight, alpha = settle)
    drawLine(Color.White.copy(alpha = 0.1f * settle), iso.p(0f, d), iso.p(w, d), 1.2f)
    drawLine(Color.White.copy(alpha = 0.07f * settle), iso.p(w, 0f), iso.p(w, d), 1.2f)

    // Floors and their finish: wood in the bedrooms, tiles where it gets wet, a deck outside
    floor.rooms.forEachIndexed { i, r ->
        val wet = WET.containsMatchIn(r.room)
        val bedroom = BEDROOM.containsMatchIn(r.room)
        val base = washed(when { floor.outdoor -> FloorDeck; wet -> FloorWet; bedroom -> FloorWood; else -> FloorDry })
        val shape = cached(i) { iso.rect(r.x, r.y, r.x + r.w, r.y + r.d) }
        // A lit room's floor warms towards the lamplight; a dark one keeps the hour's colour
        drawPath(shape, lerp(base, Color(0xFFE9C79A), warmth(r) * sky.amount * 0.9f), alpha = settle)
        val grain = cached(100 + i) {
            val seg = ArrayList<Pair<Offset, Offset>>()
            if (floor.outdoor || bedroom) {
                val step = if (floor.outdoor) 0.5f else 0.35f
                var x = r.x + step
                while (x < r.x + r.w) { seg += iso.p(x, r.y) to iso.p(x, r.y + r.d); x += step }
            } else if (wet) {
                var x = r.x + 0.6f
                while (x < r.x + r.w) { seg += iso.p(x, r.y) to iso.p(x, r.y + r.d); x += 0.6f }
                var y = r.y + 0.6f
                while (y < r.y + r.d) { seg += iso.p(r.x, y) to iso.p(r.x + r.w, y); y += 0.6f }
            }
            lines(seg)
        }
        drawPath(grain, Grain.copy(alpha = Grain.alpha * settle), style = Stroke(1f))
        if (floor.open) drawPath(shape, Color.Black.copy(alpha = 0.06f * settle), style = Stroke(1f))
    }
    // A soft rug under the living room's table
    scene.rugs.forEachIndexed { i, (rug, border) ->
        drawPath(cached(200 + 2 * i) { path(pts(rug)) }, washed(Rug).copy(alpha = settle))
        drawPath(cached(201 + 2 * i) { path(pts(border)) }, washed(Terracotta).copy(alpha = 0.45f * settle), style = Stroke(1.5f))
    }

    // The plan's finishes on the floor: a deck, a lawn, tiles; where the stairs come up; a skylight
    floor.features.forEachIndexed { fi, f ->
        val shape = cached(300 + fi) { iso.rect(f.x, f.y, f.x + f.w, f.y + f.d) }
        fun stripes(step: Float, alongX: Boolean, crossToo: Boolean = false) = cached(400 + fi) {
            val seg = ArrayList<Pair<Offset, Offset>>()
            if (alongX || crossToo) { var x = f.x + step; while (x < f.x + f.w) { seg += iso.p(x, f.y) to iso.p(x, f.y + f.d); x += step } }
            if (!alongX || crossToo) { var y = f.y + step; while (y < f.y + f.d) { seg += iso.p(f.x, y) to iso.p(f.x + f.w, y); y += step } }
            lines(seg)
        }
        when (f.kind) {
            "deck" -> {
                drawPath(shape, washed(Color(0xFFB08A60)), alpha = settle)
                drawPath(stripes(0.32f, alongX = true), Grain.copy(alpha = 0.1f * settle), style = Stroke(1f))
            }
            "grass" -> {
                drawPath(shape, washed(Color(0xFF86AE6E)), alpha = settle)
                drawPath(stripes(1.2f, alongX = false), Color.White.copy(alpha = 0.06f * settle), style = Stroke(3f))
            }
            "ceramic" -> {
                drawPath(shape, washed(Color(0xFFDCD3C6)), alpha = settle)
                drawPath(stripes(0.6f, alongX = true, crossToo = true), Grain.copy(alpha = 0.08f * settle), style = Stroke(1f))
            }
            "stairs" -> {
                drawPath(shape, washed(Color(0xFF6E665C)), alpha = settle)
                drawPath(stripes(f.d / 6, alongX = false), Color.White.copy(alpha = 0.18f * settle), style = Stroke(1.2f))
            }
            "skylight" -> {
                drawPath(shape, Brush.linearGradient(listOf(lerp(sky.glass, Color.White, 0.35f), sky.glass), iso.p(f.x, f.y), iso.p(f.x + f.w, f.y + f.d)), alpha = settle)
                drawPath(shape, Color.White.copy(alpha = 0.5f * settle), style = Stroke(1.5f))
                drawLine(Color.White.copy(alpha = 0.3f * settle), iso.p(f.x + f.w / 2, f.y), iso.p(f.x + f.w / 2, f.y + f.d), 1f)
            }
        }
    }

    val wallsUp = stage(build, 0.15f, 0.55f)
    // The sun or the moon through the back windows, on the floor, before anything stands on it
    scene.windows.forEachIndexed { i, (mid, len) ->
        val a = mid - len / 2; val b = mid + len / 2
        drawPath(cached(500 + i) { iso.poly(Triple(a, T / 2, 0f), Triple(b, T / 2, 0f), Triple(b + 0.8f, 1.9f, 0f), Triple(a + 0.8f, 1.9f, 0f)) }, sky.sun.copy(alpha = sky.sun.alpha * wallsUp))
    }
    // Shadow where the floor meets the back walls
    val ao = Color.Black.copy(alpha = 0.32f * wallsUp)
    drawPath(cached(510) { iso.rect(0f, T / 2, w, T / 2 + 0.5f) }, Brush.linearGradient(listOf(ao, Color.Transparent), iso.p(w / 2, T / 2), iso.p(w / 2, T / 2 + 0.5f)))
    drawPath(cached(511) { iso.rect(T / 2, 0f, T / 2 + 0.5f, d) }, Brush.linearGradient(listOf(ao, Color.Transparent), iso.p(T / 2, d / 2), iso.p(T / 2 + 0.5f, d / 2)))

    // Pools of light: a radial fill squashed into the floor's ellipse (no blur), blooming as they come on
    floor.rooms.forEach { r ->
        val inRoom = scene.inRoom.getValue(r.room)
        scene.spots.getValue(r.room).forEachIndexed { i, spot ->
            val k = g(inRoom[i])
            if (k <= 0.01f) return@forEachIndexed
            val c = iso.p(spot.first, spot.second)
            val radius = minOf(r.w, r.d, 3.2f) * 0.66f * 1.414f * COS * iso.s * (0.45f + 0.55f * k)
            val a = (k.coerceIn(0f, 1f) * sky.pools).coerceAtMost(1.2f)
            scale(1f, SIN / COS, pivot = c) {
                drawCircle(Brush.radialGradient(listOf(Pool.copy(alpha = (0.55f * a).coerceAtMost(1f)), Pool.copy(alpha = 0.16f * a), Color.Transparent), c, radius), radius, c)
            }
        }
    }
    // The TV's own light on the floor in front of it, bluish
    scene.tvBox?.let { tv ->
        val c = iso.p(tv.x + 1.1f, tv.y + tv.d / 2)
        val radius = 1.7f * COS * iso.s
        scale(1f, SIN / COS, pivot = c) {
            drawCircle(Brush.radialGradient(listOf(Color(0xFF8FB8FF).copy(alpha = 0.24f * wallsUp), Color.Transparent), c, radius), radius, c)
        }
    }
    // Ripples: a ring across the floor from a light that just changed
    if (ripple.isNotEmpty()) floor.rooms.forEach { r ->
        val inRoom = scene.inRoom.getValue(r.room)
        scene.spots.getValue(r.room).forEachIndexed { i, spot ->
            val p = ripple[inRoom[i].key] ?: return@forEachIndexed
            val c = iso.p(spot.first, spot.second)
            val radius = (0.25f + p * 2.4f) * 1.414f * COS * iso.s
            scale(1f, SIN / COS, pivot = c) {
                drawCircle(Bulb.copy(alpha = (1f - p) * 0.6f), radius, c, style = Stroke(2.dp.toPx() * (1f - p) + 0.6f))
            }
        }
    }

    // Soft contact shadows under every piece, as in a photograph of a model: two flat fills
    scene.pieces.forEachIndexed { pi, pc ->
        val outline = pc.shadow ?: return@forEachIndexed
        val shown = stage(build, 0.42f + pc.depth * 0.2f, 0.66f + pc.depth * 0.2f)
        if (shown <= 0.01f) return@forEachIndexed
        drawPath(cached(1000 + pi * 8) { path(pts(outline)) }, Color.Black.copy(alpha = 0.16f * shown))
    }
    // Walls and furniture, far to near; a face catches the light of the room it faces
    scene.pieces.forEachIndexed { pi, pc ->
        val b = pc.b
        val rise = if (b.wall) stage(build, 0.15f + pc.depth * 0.25f, 0.5f + pc.depth * 0.25f) else stage(build, 0.42f + pc.depth * 0.2f, 0.66f + pc.depth * 0.2f)
        if (rise <= 0.01f) return@forEachIndexed
        val x0 = b.x0; val y0 = b.y0; val x1 = b.x1; val y1 = b.y1
        val z0 = b.z0 * rise; val z1 = b.z1 * rise
        val up = rise >= 0.999f
        val id = 1000 + pi * 8
        if (b.glass) {
            drawPath(cached(id + 6, up) { iso.poly(Triple(x0, y1, z0), Triple(x1, y1, z0), Triple(x1, y1, z1), Triple(x0, y1, z1)) }, Color(0xFF9FC6D8).copy(alpha = 0.09f))
            drawPath(cached(id + 7, up) { iso.poly(Triple(x1, y0, z0), Triple(x1, y1, z0), Triple(x1, y1, z1), Triple(x1, y0, z1)) }, Color(0xFF9FC6D8).copy(alpha = 0.07f))
            val alongX = x1 - x0 > y1 - y0
            val a = if (alongX) iso.p(x0, (y0 + y1) / 2, z1) else iso.p((x0 + x1) / 2, y0, z1)
            val c = if (alongX) iso.p(x1, (y0 + y1) / 2, z1) else iso.p((x0 + x1) / 2, y1, z1)
            drawLine(Color.White.copy(alpha = 0.45f), a, c, 1.5.dp.toPx())
            return@forEachIndexed
        }
        val heatY = warmth(pc.faceRoomY)
        val heatX = warmth(pc.faceRoomX)
        val base = washed(b.color)
        val faceY = lerp(lerp(base, Color.Black, 0.2f), Warm, heatY * (if (b.wall) 0.42f else 0.25f))
        val faceX = lerp(lerp(base, Color.Black, 0.36f), Warm, heatX * (if (b.wall) 0.36f else 0.2f))
        val roomHeat = warmth(pc.heatRoom)
        val top = lerp(washed(b.top ?: lerp(b.color, Color.White, 0.1f)), Warm, roomHeat * 0.18f)
        if (b.screen) {
            // A glowing face: the TV (you are here) or a monitor, on the side the box is thin in
            val face = cached(id + 3, up) {
                if (x1 - x0 < y1 - y0) iso.poly(Triple(x1, y0, z0), Triple(x1, y1, z0), Triple(x1, y1, z1), Triple(x1, y0, z1))
                else iso.poly(Triple(x0, y1, z0), Triple(x1, y1, z0), Triple(x1, y1, z1), Triple(x0, y1, z1))
            }
            drawPath(face, Brush.linearGradient(listOf(Color(0xFFB5CFFF), Color(0xFF6E6BF2)), iso.p(x0, y0, z1), iso.p(x1, y1, z0)))
            drawPath(face, Color.White.copy(alpha = 0.55f), style = Stroke(1f))
            return@forEachIndexed
        }
        if (pc.outline.isNotEmpty()) {
            // A soft piece: the rounded outline extruded, its silhouette shaded left to right like
            // light on a curved side; then its top
            val o = pc.outline
            val sides = cached(id + 1, up) { Path().apply {
                pc.lower.forEachIndexed { i, idx -> val p = iso.p(o[idx].first, o[idx].second, z0); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                pc.upper.forEach { idx -> val p = iso.p(o[idx].first, o[idx].second, z1); lineTo(p.x, p.y) }
                close()
            } }
            val left = iso.p(o[pc.lower.first()].first, o[pc.lower.first()].second).x
            val right = iso.p(o[pc.lower.last()].first, o[pc.lower.last()].second).x
            drawPath(sides, Brush.horizontalGradient(listOf(faceY, lerp(faceY, faceX, 0.5f), faceX), minOf(left, right), maxOf(left, right)))
            drawPath(cached(id + 2, up) { path(pts(o, z1)) }, top)
            if (b.bush) {
                // Leaves: a few overlapping discs above the pot
                val c = iso.p((x0 + x1) / 2, (y0 + y1) / 2, z1)
                val rr = (x1 - x0) * 0.62f * iso.s * rise
                drawCircle(lerp(Leaf, Color.Black, 0.2f), rr * 1.05f, c + Offset(0f, -rr * 0.55f))
                drawCircle(Leaf, rr * 0.85f, c + Offset(-rr * 0.45f, -rr * 0.95f))
                drawCircle(LeafLight, rr * 0.7f, c + Offset(rr * 0.35f, -rr * 1.25f))
            }
            if (b.lamp) {
                // A shade that glows when its room is lit
                val c = iso.p((x0 + x1) / 2, (y0 + y1) / 2, z1)
                val rr = (x1 - x0) * 0.9f * iso.s
                if (roomHeat > 0.01f) drawCircle(Brush.radialGradient(listOf(Bulb.copy(alpha = 0.55f * roomHeat), Color.Transparent), c, rr * 2.6f), rr * 2.6f, c)
                drawCircle(lerp(Linen, Bulb, roomHeat), rr * 0.75f, c)
            }
            return@forEachIndexed
        }
        // A box: a wall (faces a little darker at the foot, a cap) or a step
        val frontFace = cached(id + 3, up) { iso.poly(Triple(x0, y1, z0), Triple(x1, y1, z0), Triple(x1, y1, z1), Triple(x0, y1, z1)) }
        val sideFace = cached(id + 4, up) { iso.poly(Triple(x1, y0, z0), Triple(x1, y1, z0), Triple(x1, y1, z1), Triple(x1, y0, z1)) }
        val cap = cached(id + 5, up) { iso.rect(x0, y0, x1, y1, z1) }
        if (b.wall) {
            drawPath(frontFace, Brush.verticalGradient(listOf(faceY, lerp(faceY, Color.Black, 0.25f)), iso.p(x0, y1, z1).y, iso.p(x0, y1, z0).y))
            drawPath(sideFace, Brush.verticalGradient(listOf(faceX, lerp(faceX, Color.Black, 0.25f)), iso.p(x1, y0, z1).y, iso.p(x1, y0, z0).y))
            drawPath(cap, washed(WallCap, sky.amount * 0.8f))
            drawLine(Color.White.copy(alpha = 0.16f), iso.p(x0, y1, z1), iso.p(x1, y1, z1), 1f)
            drawLine(Color.White.copy(alpha = 0.1f), iso.p(x1, y0, z1), iso.p(x1, y1, z1), 1f)
        } else {
            drawPath(frontFace, faceY)
            drawPath(sideFace, faceX)
            drawPath(cap, top)
        }
        // Framed prints on the side wall, over the bed or the table (never over the TV)
        if (b.order == -1000f && y1 - y0 > x1 - x0 && rise > 0.9f) {
            scene.art.forEachIndexed { i, r ->
                val (outer, inner) = Art[i % Art.size]
                val fw = minOf(1.1f, r.d * 0.32f); val cy = r.y + r.d / 2
                val frame = iso.poly(Triple(x1, cy - fw / 2, 0.98f), Triple(x1, cy + fw / 2, 0.98f), Triple(x1, cy + fw / 2, 1.34f), Triple(x1, cy - fw / 2, 1.34f))
                drawPath(frame, washed(Linen))
                drawPath(frame, washed(Walnut), style = Stroke(1.5f))
                drawPath(iso.poly(Triple(x1, cy - fw / 2 + 0.08f, 1.04f), Triple(x1, cy + fw / 2 - 0.08f, 1.04f), Triple(x1, cy + fw / 2 - 0.08f, 1.28f), Triple(x1, cy - fw / 2 + 0.08f, 1.28f)), washed(outer))
                drawPath(iso.poly(Triple(x1, cy - fw * 0.05f, 1.04f), Triple(x1, cy + fw / 2 - 0.08f, 1.04f), Triple(x1, cy + fw / 2 - 0.08f, 1.18f), Triple(x1, cy - fw * 0.05f, 1.18f)), washed(inner))
            }
        }
        // Windows on the back wall, their glass taking the hour, with a sill and a mullion
        if (b.order == -1000f && x1 - x0 > y1 - y0) {
            scene.windows.forEach { (mid, len) ->
                val a = mid - len / 2; val c = mid + len / 2
                val zz0 = 0.48f * rise; val zz1 = 1.2f * rise
                val pane = iso.poly(Triple(a, y1, zz0), Triple(c, y1, zz0), Triple(c, y1, zz1), Triple(a, y1, zz1))
                drawPath(pane, Brush.verticalGradient(listOf(lerp(sky.glass, Color.White, 0.15f), sky.glass), iso.p(a, y1, zz1).y, iso.p(a, y1, zz0).y))
                drawPath(pane, Color.White.copy(alpha = 0.22f), style = Stroke(1.2f))
                drawLine(Color.White.copy(alpha = 0.18f), iso.p(mid, y1, zz0), iso.p(mid, y1, zz1), 1f)
                drawLine(Color.White.copy(alpha = 0.3f), iso.p(a - 0.05f, y1 + 0.05f, zz0), iso.p(c + 0.05f, y1 + 0.05f, zz0), 2f)
            }
        }
    }

    // Pendants over their pools, on a thin cord, under a brass dome
    val shown = stage(build, 0.55f, 0.75f)
    if (shown > 0.01f) floor.rooms.forEach { r ->
        val inRoom = scene.inRoom.getValue(r.room)
        scene.spots.getValue(r.room).forEachIndexed { i, spot ->
            val k = g(inRoom[i]).coerceIn(0f, 1.3f)
            val c = iso.p(spot.first, spot.second, PENDANT)
            drawLine(Color.Black.copy(alpha = 0.35f * shown), c, iso.p(spot.first, spot.second, PENDANT + 0.32f), 1f)
            if (k > 0.01f) {
                val gr = 16.dp.toPx() * (0.6f + 0.4f * k); val gc = c + Offset(0f, 3.dp.toPx())
                drawCircle(Brush.radialGradient(listOf(Bulb.copy(alpha = 0.5f * k.coerceAtMost(1f)), Color.Transparent), gc, gr), gr, gc)
            }
            val sr = 5.dp.toPx()
            drawArc(washed(Brass).copy(alpha = shown), 180f, 180f, true, topLeft = c - Offset(sr, sr * 0.9f), size = Size(sr * 2, sr * 1.8f))
            drawOval(lerp(Color(0xFF6B6255), Bulb, k.coerceIn(0f, 1f)).copy(alpha = shown), topLeft = c - Offset(sr * 0.8f, sr * 0.22f), size = Size(sr * 1.6f, sr * 0.44f))
        }
    }

}

/**
 * Over the house: the chosen room's mark (a veil and a rim on its floor, [mark] in plan units, sliding
 * between rooms) and the names on glass tags. Moving between rooms redraws only this.
 */
private fun DrawScope.drawMarks(
    scene: Scene,
    glow: Map<String, Float>,
    chosen: RoomBox?,
    mark: Rect,
    focused: Boolean,
    text: TextMeasurer,
    measured: HashMap<String, androidx.compose.ui.text.TextLayoutResult>,
    build: Float,
) {
    val floor = scene.floor
    val iso = fit(scene.plan, dz = (1f - stage(build, 0f, 0.35f)) * 0.9f)
    if (chosen != null && mark.width > 0f) {
        val i = 0.1f
        val shape = path(rounded(mark.left + i, mark.top + i, mark.right - i, mark.bottom - i, 0.35f).map { (x, y) -> iso.p(x, y) })
        drawPath(shape, Color.White.copy(alpha = if (focused) 0.06f else 0.03f))
        drawPath(shape, Color.White.copy(alpha = if (focused) 0.42f else 0.18f), style = Stroke(1.5.dp.toPx()))
    }
    val tags = stage(build, 0.78f, 1f)
    if (tags <= 0.01f) return
    fun tag(key: String, label: () -> androidx.compose.ui.text.AnnotatedString, at: Offset, bg: Color) {
        val m = measured.getOrPut(key) { text.measure(label(), TagStyle) }
        val padX = 7.dp.toPx(); val padY = 3.dp.toPx()
        val tl = Offset(at.x - m.size.width / 2f - padX, at.y - m.size.height - padY * 2)
        val size = Size(m.size.width + padX * 2, m.size.height + padY * 2)
        drawRoundRect(bg, tl, size, CornerRadius(size.height / 2), alpha = tags)
        drawText(m, topLeft = Offset(tl.x + padX, tl.y + padY), alpha = tags)
    }
    floor.rooms.forEach { r ->
        val inRoom = scene.inRoom.getValue(r.room)
        val lit = inRoom.count { (glow[it.key] ?: 0f) > 0.5f }
        val picked = r == chosen
        tag("${r.room}|$lit|$picked", {
            val ink = if (picked) Casa.Ink else Color.White
            buildAnnotatedString {
                withStyle(SpanStyle(color = ink)) { append(r.room) }
                if (inRoom.isNotEmpty()) withStyle(SpanStyle(color = if (lit > 0) (if (picked) Color(0xFFB86E00) else Casa.Amber) else ink.copy(alpha = 0.45f))) { append("  $lit/${inRoom.size}") }
            }
        }, iso.p(r.x + r.w / 2, r.y + r.d / 2, 1.45f), if (picked) Color.White.copy(alpha = if (focused) 1f else 0.7f) else Color(0xE0141518))
    }
    scene.tvBox?.let { tv ->
        tag("tv", { buildAnnotatedString { withStyle(SpanStyle(color = Color(0xFFBFD3FF))) { append("● Você está aqui") } } }, iso.p(tv.x + 0.2f, tv.y + tv.d / 2, 1.7f), Color(0xE0141A2B))
    }
}

private val BEDROOM = Regex("(?i)quarto|suíte|suite|ninho|principal|escritório")
