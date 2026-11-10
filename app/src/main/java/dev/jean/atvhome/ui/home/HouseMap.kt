package dev.jean.atvhome.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
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
    var floor by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf(plan.floors.firstOrNull()?.rooms?.firstOrNull()) }
    var focused by remember { mutableStateOf(false) }
    val mapFocus = remember { FocusRequester() }
    val floorRooms = plan.floors.getOrNull(floor)?.rooms.orEmpty()
    val room = chosen?.takeIf { it in floorRooms } ?: floorRooms.firstOrNull()
    val inRoom = home.lights.filter { it.room == room?.room }
    val roomLit = inRoom.count { Panel.isOn(it, states) }
    val tvRoom = home.lights.firstOrNull()?.room
    val text = rememberTextMeasurer()
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
    // The hour outside, checked twice a minute
    var now by remember { mutableStateOf(clock()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = clock() } }
    val sky = ambience(now)

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
            if (along < 0.45f) null else r to len * (2f - along)
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
            Canvas(Modifier.fillMaxSize()) {
                drawFloor(plan, plan.floors[floor], home.lights, glow, ripple, room, focused, text, if (builtFor == floor) build.value else 0f, sky, tvRoom)
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
            if (inRoom.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                ) {
                    items(inRoom, key = { it.key }) { light ->
                        LightTile(
                            light = light,
                            states = statesState,
                            showRoom = false,
                            modifier = Modifier.fillMaxWidth().height(92.dp),
                            onClick = { scope.launch { if (!home.toggle(light)) toast("Não consegui falar com o hub") } },
                        )
                    }
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
private const val T = 0.16f
private const val BACK = 1.45f
private const val INNER = 0.62f
private const val FRONT = 0.3f
private const val RAIL = 0.6f
/** Where the pendants hang. */
private const val PENDANT = 1.18f

private val Warm = Color(0xFFFFB861)
private val Pool = Color(0xFFFFC77A)
private val Bulb = Color(0xFFFFF1CC)

private val FloorDry = Color(0xFF2A2C32)
private val FloorWood = Color(0xFF30292A)
private val FloorWet = Color(0xFF262B31)
private val FloorDeck = Color(0xFF2E2923)
private val SlabLeft = Color(0xFF17191C)
private val SlabRight = Color(0xFF111214)
private val Wall = Color(0xFF4C515B)

// Furniture
private val Wood = Color(0xFF7A6047)
private val Fabric = Color(0xFF5A6474)
private val Linen = Color(0xFFDCD5C8)
private val Stone = Color(0xFF9A958E)
private val Porcelain = Color(0xFFD5D8DC)
private val Black = Color(0xFF1C1D20)
private val Leaf = Color(0xFF5B8A55)
private val Planter = Color(0xFF6E5B48)

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
        h < 5f || h >= 20f -> Sky("noite", Icons.Rounded.DarkMode, Color(0xFF9DB4FF), Color(0xFF3550A0), Color(0xFF1A2444), 0.3f, 1.25f, Color(0xFF16203D), Color(0xFF8FA8FF).copy(alpha = 0.05f))
        h < 7f -> Sky("amanhecer", Icons.Rounded.WbTwilight, Color(0xFFFFB3A1), Color(0xFF8A5A7A), Color(0xFF3A2C44), 0.16f, 1f, Color(0xFFE9A7A0), Color(0xFFFFC2B0).copy(alpha = 0.1f))
        h < 17f -> Sky("dia", Icons.Rounded.WbSunny, Color(0xFFFFE08A), Color(0xFFC8D4E6), Color(0xFF8C9AB0), 0.07f, 0.8f, Color(0xFFAFCFEA), Color(0xFFFFF4D6).copy(alpha = 0.13f))
        else -> Sky("pôr do sol", Icons.Rounded.WbTwilight, Color(0xFFFFB066), Color(0xFFC0682E), Color(0xFF4C2E1E), 0.2f, 1.05f, Color(0xFFF2A15E), Color(0xFFFFB066).copy(alpha = 0.14f))
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

private val WET = Regex("(?i)banheiro|lavabo|cozinha|toalete|área de serviço|lavanderia")

/** Where each light hangs in its room: a small grid, so a room with three lights has three pools. */
private fun spots(r: RoomBox, n: Int): List<Pair<Float, Float>> {
    if (n == 0) return emptyList() // a room with no lights (the dining room) has no spots
    val cols = ceil(sqrt(n.toFloat() * r.w / r.d)).toInt().coerceIn(1, n)
    val rows = ceil(n / cols.toFloat()).toInt()
    return (0 until n).map { i -> r.x + r.w * ((i % cols) + 0.5f) / cols to r.y + r.d * ((i / cols) + 0.5f) / rows }
}

/**
 * A box of the model: a wall or a piece of furniture, drawn far to near. [room]: whose light warms it.
 * [screen]: a TV's glowing face. [glass]: a railing.
 */
private class Block(
    val x0: Float, val y0: Float, val x1: Float, val y1: Float, val z0: Float, val z1: Float,
    val color: Color, val wall: Boolean, val room: RoomBox?, val glass: Boolean = false, val screen: Boolean = false,
    val top: Color? = null,
    /** The house's outline: its back walls go first, its front walls last, whatever their length. */
    val order: Float? = null,
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
    // The house's own outline: tall at the back, low at the front, a railing on the terrace
    val back = if (floor.outdoor) RAIL else BACK
    val front = if (floor.outdoor) RAIL else FRONT
    along(0f, w, 0f, back, order = -1000f); across(0f, d, 0f, back, order = -1000f)
    along(0f, w, d, front, order = 1000f); across(0f, d, w, front, order = 1000f)
    if (!floor.open && !floor.outdoor) {
        floor.rooms.forEach { r ->
            if (r.x + r.w < w - 0.01f) across(r.y, r.y + r.d, r.x + r.w, INNER)
            if (r.y + r.d < d - 0.01f) along(r.x, r.x + r.w, r.y + r.d, INNER)
        }
    }
    return out
}

/**
 * The furniture that says what a room is, sized to it, against its back wall. Recognised by name,
 * in Portuguese, the way the Tuya app names rooms; a room it does not know stays empty.
 */
private fun furniture(r: RoomBox, outdoor: Boolean, tv: Boolean): List<Block> {
    val out = ArrayList<Block>()
    val x0 = r.x + 0.25f; val y0 = r.y + 0.25f; val x1 = r.x + r.w - 0.25f; val y1 = r.y + r.d - 0.25f
    val cx = (x0 + x1) / 2; val w = x1 - x0; val d = y1 - y0
    fun box(ax: Float, ay: Float, bx: Float, by: Float, z0: Float, z1: Float, c: Color, top: Color? = null) =
        out.add(Block(ax, ay, bx, by, z0, z1, c, wall = false, room = r, top = top))
    val name = r.room.lowercase()
    when {
        outdoor -> {
            // Two loungers, a little table, planters at the corners
            // Each lounger a seat with its back raised at the head, apart from the next
            val lw = minOf(0.75f, w * 0.14f)
            listOf(cx - 2.6f, cx - 1.45f).forEach { lx ->
                box(lx, y0 + 1.15f, lx + lw, y0 + 2.7f, 0f, 0.26f, Linen)
                box(lx, y0 + 0.75f, lx + lw, y0 + 1.15f, 0f, 0.55f, Linen)
            }
            box(cx - 0.05f, y0 + 1.4f, cx + 0.55f, y0 + 2f, 0f, 0.5f, Wood)
            listOf(x0 to y0, x1 - 0.6f to y0, x1 - 0.6f to y1 - 0.6f).forEach { (px, py) -> box(px, py, px + 0.6f, py + 0.6f, 0f, 0.5f, Planter, top = Leaf) }
        }
        tv || name.contains("estar") || name.contains("living") -> {
            // TV against the back wall, on (you are here), a sofa facing it, a low table between
            val tw = minOf(2f, w * 0.55f)
            box(cx - tw / 2 - 0.2f, y0, cx + tw / 2 + 0.2f, y0 + 0.4f, 0f, 0.4f, Black)
            if (tv) out.add(Block(cx - tw / 2, r.y + T / 2, cx + tw / 2, r.y + T / 2 + 0.06f, 0.5f, 1.15f, Black, wall = false, room = r, screen = true))
            val sw = minOf(3f, w * 0.7f)
            val sy = minOf(y1 - 0.9f, y0 + d * 0.62f)
            box(cx - sw / 2, sy, cx + sw / 2, sy + 0.9f, 0f, 0.38f, Fabric)
            box(cx - sw / 2, sy + 0.65f, cx + sw / 2, sy + 0.9f, 0.38f, 0.75f, Fabric)
            box(cx - 0.6f, y0 + d * 0.36f, cx + 0.6f, y0 + d * 0.36f + 0.6f, 0f, 0.3f, Wood)
        }
        name.contains("jantar") -> {
            val tw = minOf(2.2f, w * 0.6f); val td = minOf(1f, d * 0.35f)
            val ty = y0 + d / 2 - td / 2
            (0 until 3).forEach { i ->
                val chx = cx - tw / 2 + tw * (i + 0.5f) / 3 - 0.2f
                box(chx, ty - 0.55f, chx + 0.4f, ty - 0.15f, 0f, 0.45f, Wood)
            }
            box(cx - tw / 2, ty, cx + tw / 2, ty + td, 0f, 0.55f, Wood)
            (0 until 3).forEach { i ->
                val chx = cx - tw / 2 + tw * (i + 0.5f) / 3 - 0.2f
                box(chx, ty + td + 0.15f, chx + 0.4f, ty + td + 0.55f, 0f, 0.45f, Wood)
            }
        }
        name.contains("cozinha") -> {
            box(x0, y0, x1, y0 + 0.6f, 0f, 0.9f, Stone, top = Porcelain)
            val iw = minOf(2f, w * 0.55f)
            box(cx - iw / 2, y0 + d * 0.5f, cx + iw / 2, y0 + d * 0.5f + 0.8f, 0f, 0.9f, Wood, top = Porcelain)
        }
        name.contains("quarto") || name.contains("suíte") || name.contains("suite") || name.contains("ninho") || name.contains("principal") -> {
            val bw = if (w < 2.4f) minOf(1f, w * 0.7f) else minOf(1.8f, w * 0.55f)
            val bl = minOf(2.1f, d * 0.6f)
            box(cx - bw / 2 - 0.05f, y0, cx + bw / 2 + 0.05f, y0 + 0.12f, 0f, 0.85f, Wood) // headboard
            box(cx - bw / 2, y0 + 0.12f, cx + bw / 2, y0 + 0.12f + bl, 0f, 0.45f, Fabric, top = Linen)
            box(cx - bw / 2 + 0.12f, y0 + 0.2f, cx + bw / 2 - 0.12f, y0 + 0.55f, 0.45f, 0.58f, Porcelain)
            if (w - bw > 1.1f) {
                box(cx - bw / 2 - 0.55f, y0, cx - bw / 2 - 0.1f, y0 + 0.45f, 0f, 0.5f, Wood)
                box(cx + bw / 2 + 0.1f, y0, cx + bw / 2 + 0.55f, y0 + 0.45f, 0f, 0.5f, Wood)
            }
        }
        name.contains("escritório") || name.contains("escritorio") -> {
            box(x0, y0, x0 + 0.32f, y0 + minOf(1.8f, d * 0.45f), 0f, 0.95f, Wood) // bookshelf, under the walls' height
            val dw = minOf(1.6f, w * 0.6f)
            box(cx - dw / 2 + 0.2f, y0, cx + dw / 2 + 0.2f, y0 + 0.7f, 0f, 0.75f, Wood, top = Linen)
            box(cx + 0.05f, y0 + 0.95f, cx + 0.42f, y0 + 1.32f, 0f, 0.42f, Color(0xFF3A3D44))
        }
        name.contains("banheiro") || name.contains("lavabo") || name.contains("toalete") -> {
            box(x0, y0, x0 + minOf(1.3f, w * 0.6f), y0 + 0.5f, 0f, 0.85f, Porcelain)
            box(x1 - 0.45f, y0, x1, y0 + 0.65f, 0f, 0.42f, Porcelain)
        }
        name.contains("corredor") || name.contains("hall") || name.contains("escada") -> {
            // The stairs, rising towards the back
            val steps = 8
            val sw = minOf(1.1f, w * 0.7f)
            val run = minOf(d * 0.75f, 3f) / steps
            (0 until steps).forEach { i ->
                val sy = y1 - (i + 1) * run
                box(cx - sw / 2, sy, cx + sw / 2, sy + run, 0f, (i + 1) * 0.15f, Stone)
            }
        }
    }
    return out
}

private fun DrawScope.drawFloor(
    plan: Plan,
    floor: Floor,
    lights: List<Light>,
    glow: Map<String, Float>,
    ripple: Map<String, Float>,
    chosen: RoomBox?,
    focused: Boolean,
    text: TextMeasurer,
    build: Float,
    sky: Sky,
    tvRoom: String?,
) {
    // The build: the slab settles from above, the walls rise back to front, the furniture arrives,
    // then the lights come on one after another and the names appear
    val settle = stage(build, 0f, 0.35f)
    val iso = fit(plan, dz = (1f - settle) * 0.9f)
    val w = plan.width; val d = plan.depth
    val centre = iso.p(w / 2, d / 2)
    val order = floor.rooms.flatMap { r -> lights.filter { it.room == r.room } }.withIndex().associate { (i, l) -> l.key to i }
    fun g(l: Light): Float = (glow[l.key] ?: 0f) * stage(build, 0.62f + (order[l.key] ?: 0) * 0.03f, 0.82f + (order[l.key] ?: 0) * 0.03f)
    fun warmth(r: RoomBox?): Float {
        r ?: return 0f
        val inRoom = lights.filter { it.room == r.room }
        if (inRoom.isEmpty()) return 0f
        val sum = inRoom.sumOf { g(it).coerceIn(0f, 1f).toDouble() }.toFloat()
        return if (sum <= 0.001f) 0f else (0.35f * sum.coerceAtMost(1f) + 0.65f * sum / inRoom.size)
    }
    fun roomAt(x: Float, y: Float) = floor.rooms.firstOrNull { x >= it.x && x <= it.x + it.w && y >= it.y && y <= it.y + it.d }
    fun washed(c: Color, amount: Float = sky.amount) = lerp(c, sky.wash, amount)

    // The hour's light behind the model, not the whole screen: it lifts the model off the page
    drawCircle(
        Brush.radialGradient(listOf(sky.glow.copy(alpha = 0.22f), sky.glow.copy(alpha = 0.05f), Color.Transparent), centre, size.minDimension * 0.72f),
        radius = size.minDimension * 0.72f, center = centre,
    )
    // A soft shadow under the model, on the "table" it stands on
    scale(1f, SIN / COS, pivot = iso.p(w / 2, d / 2, -SLAB)) {
        val c = iso.p(w / 2, d / 2, -SLAB)
        val r = (w + d) * 0.55f * COS * iso.s
        drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.35f * settle), Color.Transparent), c, r), r, c)
    }
    drawPath(iso.poly(Triple(0f, d, 0f), Triple(w, d, 0f), Triple(w, d, -SLAB), Triple(0f, d, -SLAB)), SlabLeft, alpha = settle)
    drawPath(iso.poly(Triple(w, 0f, 0f), Triple(w, d, 0f), Triple(w, d, -SLAB), Triple(w, 0f, -SLAB)), SlabRight, alpha = settle)
    drawLine(Color.White.copy(alpha = 0.08f * settle), iso.p(0f, d), iso.p(w, d), 1f)
    drawLine(Color.White.copy(alpha = 0.06f * settle), iso.p(w, 0f), iso.p(w, d), 1f)

    // Floors and their finish: wood in the bedrooms, tiles where it gets wet, a deck outside
    floor.rooms.forEach { r ->
        val wet = WET.containsMatchIn(r.room)
        val bedroom = Regex("(?i)quarto|suíte|suite|ninho|principal|escritório").containsMatchIn(r.room)
        val base = washed(when { floor.outdoor -> FloorDeck; wet -> FloorWet; bedroom -> FloorWood; else -> FloorDry }, sky.amount * 0.6f)
        val shape = iso.rect(r.x, r.y, r.x + r.w, r.y + r.d)
        drawPath(shape, lerp(base, Color(0xFF3D3122), warmth(r)), alpha = settle)
        val line = Color.White.copy(alpha = (if (floor.outdoor) 0.05f else 0.035f) * settle)
        if (floor.outdoor || bedroom) {
            var y = r.y + if (floor.outdoor) 0.5f else 0.35f
            while (y < r.y + r.d) { drawLine(line, iso.p(r.x, y), iso.p(r.x + r.w, y), 1f); y += if (floor.outdoor) 0.5f else 0.35f }
        } else if (wet) {
            var x = r.x + 0.6f
            while (x < r.x + r.w) { drawLine(line, iso.p(x, r.y), iso.p(x, r.y + r.d), 1f); x += 0.6f }
            var y = r.y + 0.6f
            while (y < r.y + r.d) { drawLine(line, iso.p(r.x, y), iso.p(r.x + r.w, y), 1f); y += 0.6f }
        }
        if (floor.open) drawPath(shape, Color.White.copy(alpha = 0.05f * settle), style = Stroke(1f))
        // A rug under the living room's table
        if (!floor.outdoor && (r.room == tvRoom || r.room.contains("estar", true))) {
            val cx = r.x + r.w / 2; val rw = minOf(2.6f, r.w * 0.6f)
            drawPath(iso.rect(cx - rw / 2, r.y + r.d * 0.3f, cx + rw / 2, r.y + r.d * 0.3f + r.d * 0.42f), Color(0xFF3B3A44).copy(alpha = 0.8f * settle))
        }
    }

    val wallsUp = stage(build, 0.15f, 0.55f)
    // Sun or moon through the back windows, on the floor (before anything stands on it)
    val windows = if (floor.outdoor) emptyList() else floor.rooms.flatMap { r ->
        buildList {
            if (r.y < 0.01f && r.w >= 1.8f) add(Triple(true, r.x + r.w / 2, minOf(1.6f, r.w * 0.45f)))
            if (r.x < 0.01f && r.d >= 1.8f) add(Triple(false, r.y + r.d / 2, minOf(1.6f, r.d * 0.45f)))
        }
    }
    windows.forEach { (alongX, mid, len) ->
        val a = mid - len / 2; val b = mid + len / 2
        val patch = if (alongX) iso.poly(Triple(a, T / 2, 0f), Triple(b, T / 2, 0f), Triple(b + 0.8f, 1.9f, 0f), Triple(a + 0.8f, 1.9f, 0f))
        else iso.poly(Triple(T / 2, a, 0f), Triple(T / 2, b, 0f), Triple(1.9f, b + 0.8f, 0f), Triple(1.9f, a + 0.8f, 0f))
        drawPath(patch, sky.sun.copy(alpha = sky.sun.alpha * wallsUp))
    }
    // Shadow where the floor meets the back walls
    val ao = Color.Black.copy(alpha = 0.32f * wallsUp)
    drawPath(iso.rect(0f, T / 2, w, T / 2 + 0.5f), Brush.linearGradient(listOf(ao, Color.Transparent), iso.p(w / 2, T / 2), iso.p(w / 2, T / 2 + 0.5f)))
    drawPath(iso.rect(T / 2, 0f, T / 2 + 0.5f, d), Brush.linearGradient(listOf(ao, Color.Transparent), iso.p(T / 2, d / 2), iso.p(T / 2 + 0.5f, d / 2)))

    // Pools of light: a radial fill squashed into the floor's ellipse (no blur), blooming as they come on
    floor.rooms.forEach { r ->
        val inRoom = lights.filter { it.room == r.room }
        spots(r, inRoom.size).zip(inRoom).forEach { (spot, l) ->
            val k = g(l)
            if (k <= 0.01f) return@forEach
            val c = iso.p(spot.first, spot.second)
            val radius = minOf(r.w, r.d, 3.2f) * 0.66f * 1.414f * COS * iso.s * (0.45f + 0.55f * k)
            val a = (k.coerceIn(0f, 1f) * sky.pools).coerceAtMost(1.2f)
            scale(1f, SIN / COS, pivot = c) {
                drawCircle(Brush.radialGradient(listOf(Pool.copy(alpha = (0.55f * a).coerceAtMost(1f)), Pool.copy(alpha = 0.16f * a), Color.Transparent), c, radius), radius, c)
            }
        }
    }
    // The TV's own light on the floor in front of it, bluish
    val tvBox = floor.rooms.firstOrNull { it.room == tvRoom }
    if (tvBox != null && !floor.outdoor) {
        val c = iso.p(tvBox.x + tvBox.w / 2, tvBox.y + 1.1f)
        val radius = 1.6f * COS * iso.s
        scale(1f, SIN / COS, pivot = c) {
            drawCircle(Brush.radialGradient(listOf(Color(0xFF8FB8FF).copy(alpha = 0.22f * wallsUp), Color.Transparent), c, radius), radius, c)
        }
    }
    // The chosen room's floor brightens a little; its tag (below) turns white
    chosen?.let { r -> drawPath(iso.rect(r.x, r.y, r.x + r.w, r.y + r.d), Color.White.copy(alpha = if (focused) 0.07f else 0.035f)) }
    // Ripples: a ring across the floor from a light that just changed
    floor.rooms.forEach { r ->
        val inRoom = lights.filter { it.room == r.room }
        spots(r, inRoom.size).zip(inRoom).forEach { (spot, l) ->
            val p = ripple[l.key] ?: return@forEach
            val c = iso.p(spot.first, spot.second)
            val radius = (0.25f + p * 2.4f) * 1.414f * COS * iso.s
            scale(1f, SIN / COS, pivot = c) {
                drawCircle(Bulb.copy(alpha = (1f - p) * 0.6f), radius, c, style = Stroke(2.dp.toPx() * (1f - p) + 0.6f))
            }
        }
    }

    // Walls and furniture, far to near; a face catches the light of the room it faces
    val furnish = floor.rooms.flatMap { furniture(it, floor.outdoor, it.room == tvRoom) }
    (walls(plan, floor) + furnish).sortedBy { it.depth }.forEach { b ->
        val depth = (b.x0 + b.x1 + b.y0 + b.y1) / 2f / (w + d)
        val rise = if (b.wall) stage(build, 0.15f + depth * 0.25f, 0.5f + depth * 0.25f) else stage(build, 0.42f + depth * 0.2f, 0.66f + depth * 0.2f)
        if (rise <= 0.01f) return@forEach
        val x0 = b.x0; val y0 = b.y0; val x1 = b.x1; val y1 = b.y1
        val z0 = b.z0 * rise; val z1 = b.z1 * rise
        if (b.glass) {
            drawPath(iso.poly(Triple(x0, y1, z0), Triple(x1, y1, z0), Triple(x1, y1, z1), Triple(x0, y1, z1)), Color(0xFF9FC6D8).copy(alpha = 0.09f))
            drawPath(iso.poly(Triple(x1, y0, z0), Triple(x1, y1, z0), Triple(x1, y1, z1), Triple(x1, y0, z1)), Color(0xFF9FC6D8).copy(alpha = 0.07f))
            val alongX = x1 - x0 > y1 - y0
            val a = if (alongX) iso.p(x0, (y0 + y1) / 2, z1) else iso.p((x0 + x1) / 2, y0, z1)
            val c = if (alongX) iso.p(x1, (y0 + y1) / 2, z1) else iso.p((x0 + x1) / 2, y1, z1)
            drawLine(Color.White.copy(alpha = 0.45f), a, c, 1.5.dp.toPx())
            return@forEach
        }
        val heatY = if (b.wall) warmth(roomAt((x0 + x1) / 2, y1 + 0.3f)) else warmth(b.room)
        val heatX = if (b.wall) warmth(roomAt(x1 + 0.3f, (y0 + y1) / 2)) else warmth(b.room)
        val base = washed(b.color)
        val faceY = lerp(lerp(base, Color.Black, 0.22f), Warm, heatY * (if (b.wall) 0.42f else 0.25f))
        val faceX = lerp(lerp(base, Color.Black, 0.36f), Warm, heatX * (if (b.wall) 0.36f else 0.2f))
        val top = lerp(washed(b.top ?: lerp(b.color, Color.White, 0.08f)), Warm, warmth(b.room ?: roomAt((x0 + x1) / 2, (y0 + y1) / 2)) * 0.18f)
        val frontFace = iso.poly(Triple(x0, y1, z0), Triple(x1, y1, z0), Triple(x1, y1, z1), Triple(x0, y1, z1))
        if (b.screen) {
            // The TV is on: you are here
            drawPath(frontFace, Brush.linearGradient(listOf(Color(0xFFA9C8FF), Color(0xFF6E6BF2)), iso.p(x0, y1, z1), iso.p(x1, y1, z0)))
            drawPath(frontFace, Color.White.copy(alpha = 0.6f), style = Stroke(1f))
            return@forEach
        }
        drawPath(frontFace, faceY)
        drawPath(iso.poly(Triple(x1, y0, z0), Triple(x1, y1, z0), Triple(x1, y1, z1), Triple(x1, y0, z1)), faceX)
        drawPath(iso.rect(x0, y0, x1, y1, z1), top)
        // Windows on the back walls, their glass taking the hour
        if (b.wall && z1 > 0.9f && (y0 < 0f || x0 < 0f)) {
            windows.forEach { (alongX, mid, len) ->
                val a = mid - len / 2; val c = mid + len / 2
                val zz0 = 0.5f * rise; val zz1 = 1.18f * rise
                val pane = when {
                    alongX && y0 < 0f && x1 - x0 > y1 - y0 -> iso.poly(Triple(a, y1, zz0), Triple(c, y1, zz0), Triple(c, y1, zz1), Triple(a, y1, zz1))
                    !alongX && x0 < 0f && y1 - y0 > x1 - x0 -> iso.poly(Triple(x1, a, zz0), Triple(x1, c, zz0), Triple(x1, c, zz1), Triple(x1, a, zz1))
                    else -> null
                } ?: return@forEach
                drawPath(pane, sky.glass)
                drawPath(pane, Color.White.copy(alpha = 0.18f), style = Stroke(1.2f))
            }
        }
    }

    // Pendants over their pools, on a thin cord
    floor.rooms.forEach { r ->
        val inRoom = lights.filter { it.room == r.room }
        spots(r, inRoom.size).zip(inRoom).forEach { (spot, l) ->
            val k = g(l).coerceIn(0f, 1.3f)
            val shown = stage(build, 0.55f, 0.75f)
            if (shown <= 0.01f) return@forEach
            val c = iso.p(spot.first, spot.second, PENDANT)
            drawLine(Color.White.copy(alpha = 0.14f * shown), c, iso.p(spot.first, spot.second, PENDANT + 0.3f), 1f)
            if (k > 0.01f) {
                drawCircle(Bulb.copy(alpha = 0.18f * k.coerceAtMost(1f)), radius = 11.dp.toPx() * (0.6f + 0.4f * k), center = c)
                drawCircle(Bulb.copy(alpha = 0.4f * k.coerceAtMost(1f)), radius = 5.5.dp.toPx(), center = c)
            }
            drawCircle(lerp(Color(0xFF5A5E66), Bulb, k.coerceIn(0f, 1f)), radius = 3.dp.toPx(), center = c, alpha = shown)
        }
    }

    // Names on glass tags, with what is lit in amber: the last thing to arrive
    val tags = stage(build, 0.78f, 1f)
    if (tags <= 0.01f) return
    fun tag(label: androidx.compose.ui.text.AnnotatedString, at: Offset, bg: Color) {
        val m = text.measure(label, TextStyle(fontFamily = Inter, fontSize = 10.sp, fontWeight = FontWeight.SemiBold))
        val padX = 7.dp.toPx(); val padY = 3.dp.toPx()
        val tl = Offset(at.x - m.size.width / 2f - padX, at.y - m.size.height - padY * 2)
        val size = Size(m.size.width + padX * 2, m.size.height + padY * 2)
        drawRoundRect(bg, tl, size, CornerRadius(size.height / 2), alpha = tags)
        drawText(m, topLeft = Offset(tl.x + padX, tl.y + padY), alpha = tags)
    }
    floor.rooms.forEach { r ->
        val inRoom = lights.filter { it.room == r.room }
        val lit = inRoom.count { (glow[it.key] ?: 0f) > 0.5f }
        val picked = r == chosen
        val ink = if (picked) Casa.Ink else Color.White
        val label = buildAnnotatedString {
            withStyle(SpanStyle(color = ink)) { append(r.room) }
            if (inRoom.isNotEmpty()) withStyle(SpanStyle(color = if (lit > 0) (if (picked) Color(0xFFB86E00) else Casa.Amber) else ink.copy(alpha = 0.45f))) { append("  $lit/${inRoom.size}") }
        }
        tag(label, iso.p(r.x + r.w / 2, r.y + r.d / 2, 1.45f), if (picked) Color.White.copy(alpha = if (focused) 1f else 0.7f) else Color(0xE0141518))
    }
    if (tvBox != null && !floor.outdoor) {
        tag(buildAnnotatedString { withStyle(SpanStyle(color = Color(0xFFBFD3FF))) { append("● Você está aqui") } }, iso.p(tvBox.x + tvBox.w / 2, tvBox.y + T, 1.75f), Color(0xE0141A2B))
    }
}

