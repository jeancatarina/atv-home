package dev.jean.atvhome.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.WbTwilight
import kotlinx.coroutines.delay
import java.time.LocalTime
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
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
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
import androidx.tv.material3.Text
import dev.jean.atvhome.data.HouseModel
import dev.jean.atvhome.tuya.Floor
import dev.jean.atvhome.tuya.Light
import dev.jean.atvhome.tuya.Panel
import dev.jean.atvhome.tuya.Plan
import dev.jean.atvhome.tuya.RoomBox
import dev.jean.atvhome.ui.components.requestWhenReady
import dev.jean.atvhome.ui.theme.Inter
import dev.jean.atvhome.ui.theme.Tv
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * "Maquete": one floor at a time as an architect's cut-away model, in isometric view. Back walls
 * stand full height, inner walls half, front walls low, so every room is in view. A light that is
 * on pours a warm pool on the floor and onto the wall behind it; rooms with everything off stay in
 * the dark. The kitchen and bathrooms are tiled, the terrace is a deck behind a glass railing.
 *
 * ▲ ▼ change floor (the stack in the corner says which), ◀ ▶ change room, OK switches the whole
 * room; ▶ past the last room goes to its lights beside the model.
 *
 * It is alive: a floor builds itself when it comes into view (the slab settles, the walls rise from
 * the back to the front), a light that comes on blooms (its pool overshoots a little, like a bulb
 * warming) and one that goes off fades, and the model takes the hour's light ([clock]): blue at
 * night, gold at sunset.
 *
 * One Canvas of paths and flat or radial fills, no blur: it redraws only while something moves.
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
    var index by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val mapFocus = remember { FocusRequester() }
    val rooms = plan.floors.getOrNull(floor)?.rooms.orEmpty().sortedWith(compareBy({ it.x }, { it.y }))
    val room = rooms.getOrNull(index.coerceIn(0, (rooms.size - 1).coerceAtLeast(0)))
    val inRoom = home.lights.filter { it.room == room?.room }
    val roomLit = inRoom.count { Panel.isOn(it, states) }
    val text = rememberTextMeasurer()
    fun switchRoom(on: Boolean) = scope.launch { if (!home.switchAll(inRoom, on)) toast("Algumas luzes não responderam") }
    LaunchedEffect(Unit) { mapFocus.requestWhenReady() }

    // The floor builds itself each time it comes into view
    val build = remember { Animatable(0f) }
    // Which floor the animation belongs to: a new floor starts unbuilt on its very first frame,
    // not fully built for one frame until the effect resets the animation (a flash)
    var builtFor by remember { mutableIntStateOf(-1) }
    LaunchedEffect(floor) { build.snapTo(0f); builtFor = floor; build.animateTo(1f, tween(1500, easing = LinearEasing)) }
    // Each light's glow, 0..1 (a little over while it blooms); kept across changes so one light animates alone
    val glow = remember { mutableStateMapOf<String, Float>() }
    val anims = remember { HashMap<String, Animatable<Float, AnimationVector1D>>() }
    val animScope = rememberCoroutineScope()
    LaunchedEffect(states) {
        home.lights.forEach { l ->
            val target = if (Panel.isOn(l, states)) 1f else 0f
            val a = anims.getOrPut(l.key) { Animatable(target).also { glow[l.key] = target } }
            if (a.targetValue != target) animScope.launch {
                a.animateTo(target, if (target > 0f) spring(dampingRatio = 0.42f, stiffness = 140f) else tween(450)) { glow[l.key] = value }
            }
        }
    }
    // The hour outside, checked twice a minute
    var now by remember { mutableStateOf(clock()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = clock() } }
    val sky = ambience(now)

    Row(modifier.fillMaxSize().padding(start = Tv.Edge, end = Tv.Edge, top = 26.dp, bottom = 22.dp)) {
        Box(
            Modifier.weight(1f).fillMaxHeight()
                .focusRequester(mapFocus)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft -> if (index > 0) { index--; true } else false
                        Key.DirectionRight -> if (index < rooms.lastIndex) { index++; true } else false
                        Key.DirectionUp -> if (floor < plan.floors.lastIndex) { floor++; index = 0; true } else false
                        Key.DirectionDown -> if (floor > 0) { floor--; index = 0; true } else false
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (inRoom.isNotEmpty()) switchRoom(roomLit == 0); true }
                        else -> false
                    }
                }
                .focusable(),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawFloor(plan, plan.floors[floor], home.lights, glow, room, focused, text, if (builtFor == floor) build.value else 0f, sky)
            }
            Column(Modifier.align(Alignment.TopStart)) {
                FloorStack(plan.floors, floor)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.tv.material3.Icon(sky.icon, contentDescription = null, tint = sky.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}  ·  ${sky.name}", style = Tv.Meta, color = Tv.Secondary)
                }
            }
            Text(
                "▲ ▼  andar     ◀ ▶  cômodo     OK  acende ou apaga",
                style = Tv.Caption, color = Tv.Tertiary, modifier = Modifier.align(Alignment.BottomStart),
            )
        }
        Spacer(Modifier.width(26.dp))
        // The chosen room: what is lit, its shortcuts and its lights
        Column(Modifier.width(300.dp).fillMaxHeight()) {
            Text(plan.floors.getOrNull(floor)?.name.orEmpty().uppercase(), style = Tv.Caption.copy(letterSpacing = 1.4.sp), color = Casa.Amber)
            Spacer(Modifier.height(2.dp))
            Text(room?.room ?: "", style = Tv.Title, color = Tv.Primary, maxLines = 1)
            Text(
                when { inRoom.isEmpty() -> "Sem luzes"; roomLit == 0 -> "Tudo apagado"; else -> "$roomLit de ${inRoom.size} acesas" },
                style = Tv.Meta, color = Tv.Secondary,
            )
            Spacer(Modifier.height(16.dp))
            if (inRoom.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SceneButton(Icons.Rounded.NightsStay, "Apagar", { switchRoom(false) }, tint = Casa.Scene)
                    SceneButton(Icons.Rounded.WbSunny, "Acender", { switchRoom(true) })
                }
                Spacer(Modifier.height(16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(inRoom, key = { it.key }) { light ->
                        LightTile(
                            light = light,
                            states = statesState,
                            showRoom = false,
                            modifier = Modifier.fillMaxWidth().height(96.dp),
                            onClick = { scope.launch { if (!home.toggle(light)) toast("Não consegui falar com o hub") } },
                        )
                    }
                }
            }
        }
    }
}

/** The floors as a little stack, top floor first, the one in view in amber: where you are in the house. */
@Composable
private fun FloorStack(floors: List<Floor>, current: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        floors.indices.reversed().forEach { k ->
            val on = k == current
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(width = 34.dp, height = 18.dp)) {
                    val w = size.width; val h = size.height
                    val top = Path().apply { moveTo(w / 2, 0f); lineTo(w, h * 0.38f); lineTo(w / 2, h * 0.76f); lineTo(0f, h * 0.38f); close() }
                    val side = Path().apply { moveTo(0f, h * 0.38f); lineTo(w / 2, h * 0.76f); lineTo(w, h * 0.38f); lineTo(w, h * 0.62f); lineTo(w / 2, h); lineTo(0f, h * 0.62f); close() }
                    drawPath(side, if (on) Color(0xFF9A5F17) else Color.White.copy(alpha = 0.06f))
                    drawPath(top, if (on) Casa.Amber else Color.White.copy(alpha = 0.12f))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    floors[k].name,
                    style = Tv.Meta.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                    color = if (on) Tv.Primary else Tv.Tertiary,
                )
            }
        }
    }
}

// --- Drawing ------------------------------------------------------------------------------------

private const val COS = 0.866f
private const val SIN = 0.5f
private const val SLAB = 0.42f
private const val T = 0.16f
private const val BACK = 1.35f
private const val INNER = 0.62f
private const val FRONT = 0.3f
private const val RAIL = 0.6f

private val Warm = Color(0xFFFFB861)
private val Pool = Color(0xFFFFC77A)
private val Bulb = Color(0xFFFFF1CC)

private val FloorDry = Color(0xFF292C33)
private val FloorWet = Color(0xFF252A31)
private val FloorDeck = Color(0xFF2C2925)
private val SlabLeft = Color(0xFF17191C)
private val SlabRight = Color(0xFF111214)
private val WallTop = Color(0xFF555A64)
private val WallFaceY = Color(0xFF3A3E46)
private val WallFaceX = Color(0xFF30333A)

/** [dz]: the whole floor raised, while it settles into place. */
private class Iso(val s: Float, val ox: Float, val oy: Float, val dz: Float = 0f) {
    fun p(x: Float, y: Float, z: Float = 0f) = Offset(ox + (x - y) * COS * s, oy + (x + y) * SIN * s - (z + dz) * s)
}

/** The hour's light on the model. */
private class Sky(val name: String, val icon: ImageVector, val accent: Color, val glow: Color, val wash: Color, val amount: Float, val pools: Float)

private fun ambience(t: LocalTime): Sky {
    val h = t.hour + t.minute / 60f
    return when {
        h < 5f || h >= 20f -> Sky("noite", Icons.Rounded.DarkMode, Color(0xFF9DB4FF), Color(0xFF3550A0), Color(0xFF1A2444), 0.3f, 1.2f)
        h < 7f -> Sky("amanhecer", Icons.Rounded.WbTwilight, Color(0xFFFFB3A1), Color(0xFF8A5A7A), Color(0xFF3A2C44), 0.16f, 1f)
        h < 17f -> Sky("dia", Icons.Rounded.WbSunny, Color(0xFFFFE08A), Color(0xFFC8D4E6), Color(0xFF8C9AB0), 0.07f, 0.8f)
        else -> Sky("pôr do sol", Icons.Rounded.WbTwilight, Color(0xFFFFB066), Color(0xFFC0682E), Color(0xFF4C2E1E), 0.2f, 1.05f)
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

/** Where each light sits in its room: a small grid, so a room with three lights has three pools. */
private fun spots(r: RoomBox, n: Int): List<Pair<Float, Float>> {
    if (n == 0) return emptyList() // a room with no lights (the dining room) has no spots
    val cols = ceil(sqrt(n.toFloat() * r.w / r.d)).toInt().coerceIn(1, n)
    val rows = ceil(n / cols.toFloat()).toInt()
    return (0 until n).map { i -> r.x + r.w * ((i % cols) + 0.5f) / cols to r.y + r.d * ((i / cols) + 0.5f) / rows }
}

private class Wall(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val h: Float, val glass: Boolean)

private fun walls(plan: Plan, floor: Floor): List<Wall> {
    val w = plan.width; val d = plan.depth
    val out = ArrayList<Wall>()
    fun along(xa: Float, xb: Float, y: Float, h: Float) = out.add(Wall(xa, y - T / 2, xb, y + T / 2, h, floor.outdoor))
    fun across(ya: Float, yb: Float, x: Float, h: Float) = out.add(Wall(x - T / 2, ya, x + T / 2, yb, h, floor.outdoor))
    // The house's own outline: tall at the back, low at the front, a railing on the terrace
    val back = if (floor.outdoor) RAIL else BACK
    val front = if (floor.outdoor) RAIL else FRONT
    along(0f, w, 0f, back); across(0f, d, 0f, back)
    along(0f, w, d, front); across(0f, d, w, front)
    if (!floor.open && !floor.outdoor) {
        floor.rooms.forEach { r ->
            if (r.x + r.w < w - 0.01f) across(r.y, r.y + r.d, r.x + r.w, INNER)
            if (r.y + r.d < d - 0.01f) along(r.x, r.x + r.w, r.y + r.d, INNER)
        }
    }
    // Far boxes first: painter's order for axis-aligned boxes that do not cross
    return out.sortedBy { it.x0 + it.x1 + it.y0 + it.y1 + it.h * 0.01f }
}

private fun DrawScope.drawFloor(
    plan: Plan,
    floor: Floor,
    lights: List<Light>,
    glow: Map<String, Float>,
    chosen: RoomBox?,
    focused: Boolean,
    text: TextMeasurer,
    build: Float,
    sky: Sky,
) {
    // The build: the slab settles from above and fades in, the walls rise back to front, then the light and the names
    val settle = stage(build, 0f, 0.45f)
    val slabAlpha = settle
    val iso = fit(plan, dz = (1f - settle) * 0.9f)
    val w = plan.width; val d = plan.depth
    val centre = iso.p(w / 2, d / 2)
    // The hour's light behind the model, not the whole screen: it lifts the model off the page
    drawCircle(
        Brush.radialGradient(listOf(sky.glow.copy(alpha = 0.22f), sky.glow.copy(alpha = 0.05f), Color.Transparent), centre, size.minDimension * 0.7f),
        radius = size.minDimension * 0.7f, center = centre,
    )
    // The slab's two front faces
    drawPath(iso.poly(Triple(0f, d, 0f), Triple(w, d, 0f), Triple(w, d, -SLAB), Triple(0f, d, -SLAB)), SlabLeft, alpha = slabAlpha)
    drawPath(iso.poly(Triple(w, 0f, 0f), Triple(w, d, 0f), Triple(w, d, -SLAB), Triple(w, 0f, -SLAB)), SlabRight, alpha = slabAlpha)

    // Once the walls are up, the lights come on one after another, not all at once
    val order = floor.rooms.flatMap { r -> lights.filter { it.room == r.room } }.withIndex().associate { (i, l) -> l.key to i }
    fun g(l: Light): Float {
        val i = order[l.key] ?: 0
        return (glow[l.key] ?: 0f) * stage(build, 0.5f + i * 0.035f, 0.72f + i * 0.035f)
    }
    fun warmth(r: RoomBox?): Float {
        r ?: return 0f
        val inRoom = lights.filter { it.room == r.room }
        if (inRoom.isEmpty()) return 0f
        val sum = inRoom.sumOf { g(it).coerceIn(0f, 1f).toDouble() }.toFloat()
        return if (sum <= 0.001f) 0f else (0.35f * sum.coerceAtMost(1f) + 0.65f * sum / inRoom.size)
    }
    fun roomAt(x: Float, y: Float) = floor.rooms.firstOrNull { x >= it.x && x <= it.x + it.w && y >= it.y && y <= it.y + it.d }

    // Floors, with their finish, the light on them, and the chosen room
    floor.rooms.forEach { r ->
        val wet = WET.containsMatchIn(r.room)
        val base = lerp(when { floor.outdoor -> FloorDeck; wet -> FloorWet; else -> FloorDry }, sky.wash, sky.amount * 0.6f)
        val heat = warmth(r)
        val shape = iso.rect(r.x, r.y, r.x + r.w, r.y + r.d)
        drawPath(shape, lerp(base, Color(0xFF3D3122), heat), alpha = slabAlpha)
        // Finish: deck planks, or tiles where it gets wet
        val line = Color.White.copy(alpha = if (floor.outdoor) 0.05f else 0.035f)
        if (floor.outdoor) {
            var y = r.y + 0.5f
            while (y < r.y + r.d) { drawLine(line, iso.p(r.x, y), iso.p(r.x + r.w, y), 1f, alpha = slabAlpha); y += 0.5f }
        } else if (wet) {
            var x = r.x + 0.6f
            while (x < r.x + r.w) { drawLine(line, iso.p(x, r.y), iso.p(x, r.y + r.d), 1f); x += 0.6f }
            var y = r.y + 0.6f
            while (y < r.y + r.d) { drawLine(line, iso.p(r.x, y), iso.p(r.x + r.w, y), 1f); y += 0.6f }
        }
        // An open floor shows where one space becomes the next with a faint seam only
        if (floor.open) drawPath(shape, Color.White.copy(alpha = 0.05f), style = Stroke(1f))
    }
    // Pools of light: a radial fill squashed into the floor's ellipse (no blur)
    floor.rooms.forEach { r ->
        val inRoom = lights.filter { it.room == r.room }
        spots(r, inRoom.size).zip(inRoom).forEach { (spot, l) ->
            val k = g(l)
            if (k <= 0.01f) return@forEach
            val c = iso.p(spot.first, spot.second)
            // Blooming: the pool grows past its size and settles (the spring's overshoot), and brightens
            val radius = minOf(r.w, r.d, 3.2f) * 0.62f * 1.414f * COS * iso.s * (0.45f + 0.55f * k)
            val a = (k.coerceIn(0f, 1f) * sky.pools).coerceAtMost(1.2f)
            scale(1f, SIN / COS, pivot = c) {
                drawCircle(Brush.radialGradient(listOf(Pool.copy(alpha = (0.55f * a).coerceAtMost(1f)), Pool.copy(alpha = 0.16f * a), Color.Transparent), c, radius), radius, c)
            }
        }
    }
    // The chosen room's floor brightens a little; its tag (below) turns white: an outline hid behind the walls
    chosen?.let { r -> drawPath(iso.rect(r.x, r.y, r.x + r.w, r.y + r.d), Color.White.copy(alpha = if (focused) 0.08f else 0.04f)) }
    // The bulbs, where their pools are
    floor.rooms.forEach { r ->
        val inRoom = lights.filter { it.room == r.room }
        spots(r, inRoom.size).zip(inRoom).forEach { (spot, l) ->
            val c = iso.p(spot.first, spot.second)
            val k = g(l).coerceIn(0f, 1.3f)
            if (k > 0.01f) drawCircle(Bulb.copy(alpha = 0.35f * k.coerceAtMost(1f)), radius = 6.dp.toPx() * (0.6f + 0.4f * k), center = c)
            drawCircle(lerp(Color.White.copy(alpha = 0.22f), Bulb, k.coerceIn(0f, 1f)), radius = 2.6.dp.toPx(), center = c, alpha = slabAlpha)
        }
    }
    // Walls, far to near. A face catches the light of the room it faces
    walls(plan, floor).forEach { wall ->
        // Rising back to front: a wall's delay follows how far forward it stands
        val depth = (wall.x0 + wall.x1 + wall.y0 + wall.y1) / 2f / (w + d)
        val rise = stage(build, 0.18f + depth * 0.3f, 0.55f + depth * 0.3f)
        if (rise <= 0.01f) return@forEach
        val x0 = wall.x0; val y0 = wall.y0; val x1 = wall.x1; val y1 = wall.y1; val h = wall.h * rise
        val facingY = warmth(roomAt((x0 + x1) / 2, y1 + 0.3f))
        val facingX = warmth(roomAt(x1 + 0.3f, (y0 + y1) / 2))
        if (wall.glass) {
            drawPath(iso.poly(Triple(x0, y1, 0f), Triple(x1, y1, 0f), Triple(x1, y1, h), Triple(x0, y1, h)), Color(0xFF9FC6D8).copy(alpha = 0.09f))
            drawPath(iso.poly(Triple(x1, y0, 0f), Triple(x1, y1, 0f), Triple(x1, y1, h), Triple(x1, y0, h)), Color(0xFF9FC6D8).copy(alpha = 0.07f))
            // The handrail along the railing's top
            val alongX = x1 - x0 > y1 - y0
            val a = if (alongX) iso.p(x0, (y0 + y1) / 2, h) else iso.p((x0 + x1) / 2, y0, h)
            val b = if (alongX) iso.p(x1, (y0 + y1) / 2, h) else iso.p((x0 + x1) / 2, y1, h)
            drawLine(Color.White.copy(alpha = 0.45f), a, b, 1.5.dp.toPx())
            return@forEach
        }
        val faceY = lerp(WallFaceY, sky.wash, sky.amount)
        val faceX = lerp(WallFaceX, sky.wash, sky.amount)
        drawPath(iso.poly(Triple(x0, y1, 0f), Triple(x1, y1, 0f), Triple(x1, y1, h), Triple(x0, y1, h)), lerp(faceY, Warm, facingY * 0.42f))
        drawPath(iso.poly(Triple(x1, y0, 0f), Triple(x1, y1, 0f), Triple(x1, y1, h), Triple(x1, y0, h)), lerp(faceX, Warm, facingX * 0.36f))
        drawPath(iso.rect(x0, y0, x1, y1, h), lerp(WallTop, sky.wash, sky.amount * 0.5f))
    }
    // Names on glass tags, with what is lit in amber: the last thing to arrive
    val tags = stage(build, 0.7f, 1f)
    if (tags <= 0.01f) return
    floor.rooms.forEach { r ->
        val inRoom = lights.filter { it.room == r.room }
        val lit = inRoom.count { (glow[it.key] ?: 0f) > 0.5f }
        val picked = r == chosen
        val ink = if (picked) Casa.Ink else Color.White
        val label = buildAnnotatedString {
            withStyle(SpanStyle(color = ink)) { append(r.room) }
            if (inRoom.isNotEmpty()) withStyle(SpanStyle(color = if (lit > 0) (if (picked) Color(0xFFB86E00) else Casa.Amber) else ink.copy(alpha = 0.45f))) { append("  $lit/${inRoom.size}") }
        }
        val m = text.measure(label, TextStyle(fontFamily = Inter, fontSize = 10.sp, fontWeight = FontWeight.SemiBold))
        val c = iso.p(r.x + r.w / 2, r.y + r.d / 2)
        val padX = 7.dp.toPx(); val padY = 3.dp.toPx()
        val tl = Offset(c.x - m.size.width / 2f - padX, c.y - m.size.height - padY * 2 - 10.dp.toPx())
        val tag = Size(m.size.width + padX * 2, m.size.height + padY * 2)
        drawRoundRect(if (picked) Color.White.copy(alpha = if (focused) 1f else 0.7f) else Color(0xE0141518), tl, tag, androidx.compose.ui.geometry.CornerRadius(tag.height / 2), alpha = tags)
        drawText(m, topLeft = Offset(tl.x + padX, tl.y + padY), alpha = tags)
    }
}

