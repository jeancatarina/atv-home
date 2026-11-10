package dev.jean.atvhome.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
 * ▲ ▼ change floor (the floor slides, like a lift, and the stack in the corner says which), ◀ ▶
 * change room, OK switches the whole room; ▶ past the last room goes to its lights beside the model.
 *
 * One Canvas of paths and flat or radial fills, no blur: it redraws only when a light changes or
 * while a floor slides in.
 */
@Composable
fun HouseMap(home: HouseModel, plan: Plan, toast: (String) -> Unit, modifier: Modifier = Modifier) {
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
            // Up slides the new floor down from above, like going up a storey; down, the other way
            AnimatedContent(
                targetState = floor,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(tween(380)) { h -> if (up) -h / 5 else h / 5 } + fadeIn(tween(300))) togetherWith
                        (slideOutVertically(tween(380)) { h -> if (up) h / 5 else -h / 5 } + fadeOut(tween(220)))
                },
                label = "floor",
            ) { f ->
                val fl = plan.floors[f]
                Canvas(Modifier.fillMaxSize()) {
                    drawFloor(plan, fl, home.lights, states, if (f == floor) room else null, focused, text)
                }
            }
            FloorStack(plan.floors, floor, Modifier.align(Alignment.TopStart))
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

private class Iso(val s: Float, val ox: Float, val oy: Float) {
    fun p(x: Float, y: Float, z: Float = 0f) = Offset(ox + (x - y) * COS * s, oy + (x + y) * SIN * s - z * s)
}

private fun DrawScope.fit(plan: Plan): Iso {
    val w = (plan.width + plan.depth) * COS
    val h = (plan.width + plan.depth) * SIN + BACK + SLAB
    val s = minOf(size.width / w, size.height / h) * 0.9f
    val ox = (size.width - w * s) / 2f + plan.depth * COS * s
    val oy = (size.height - h * s) / 2f + BACK * s
    return Iso(s, ox, oy)
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
    states: Map<String, Boolean>,
    chosen: RoomBox?,
    focused: Boolean,
    text: TextMeasurer,
) {
    val iso = fit(plan)
    val w = plan.width; val d = plan.depth
    val centre = iso.p(w / 2, d / 2)
    // A soft light behind the model, not the whole screen: it lifts the model off the page
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent), centre, size.minDimension * 0.62f),
        radius = size.minDimension * 0.62f, center = centre,
    )
    // The slab's two front faces, and a hairline on its edge
    drawPath(iso.poly(Triple(0f, d, 0f), Triple(w, d, 0f), Triple(w, d, -SLAB), Triple(0f, d, -SLAB)), SlabLeft)
    drawPath(iso.poly(Triple(w, 0f, 0f), Triple(w, d, 0f), Triple(w, d, -SLAB), Triple(w, 0f, -SLAB)), SlabRight)

    fun warmth(r: RoomBox?): Float {
        r ?: return 0f
        val inRoom = lights.filter { it.room == r.room }
        val lit = inRoom.count { Panel.isOn(it, states) }
        return if (lit == 0) 0f else 0.35f + 0.65f * lit / inRoom.size
    }
    fun roomAt(x: Float, y: Float) = floor.rooms.firstOrNull { x >= it.x && x <= it.x + it.w && y >= it.y && y <= it.y + it.d }

    // Floors, with their finish, the light on them, and the chosen room
    floor.rooms.forEach { r ->
        val wet = WET.containsMatchIn(r.room)
        val base = when { floor.outdoor -> FloorDeck; wet -> FloorWet; else -> FloorDry }
        val heat = warmth(r)
        val shape = iso.rect(r.x, r.y, r.x + r.w, r.y + r.d)
        drawPath(shape, lerp(base, Color(0xFF3D3122), heat))
        // Finish: deck planks, or tiles where it gets wet
        val line = Color.White.copy(alpha = if (floor.outdoor) 0.05f else 0.035f)
        if (floor.outdoor) {
            var y = r.y + 0.5f
            while (y < r.y + r.d) { drawLine(line, iso.p(r.x, y), iso.p(r.x + r.w, y), 1f); y += 0.5f }
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
            if (!Panel.isOn(l, states)) return@forEach
            val c = iso.p(spot.first, spot.second)
            val radius = minOf(r.w, r.d, 3.2f) * 0.62f * 1.414f * COS * iso.s
            scale(1f, SIN / COS, pivot = c) {
                drawCircle(Brush.radialGradient(listOf(Pool.copy(alpha = 0.55f), Pool.copy(alpha = 0.16f), Color.Transparent), c, radius), radius, c)
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
            val on = Panel.isOn(l, states)
            if (on) drawCircle(Bulb.copy(alpha = 0.35f), radius = 6.dp.toPx(), center = c)
            drawCircle(if (on) Bulb else Color.White.copy(alpha = 0.22f), radius = 2.6.dp.toPx(), center = c)
        }
    }
    // Walls, far to near. A face catches the light of the room it faces
    walls(plan, floor).forEach { wall ->
        val x0 = wall.x0; val y0 = wall.y0; val x1 = wall.x1; val y1 = wall.y1; val h = wall.h
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
        drawPath(iso.poly(Triple(x0, y1, 0f), Triple(x1, y1, 0f), Triple(x1, y1, h), Triple(x0, y1, h)), lerp(WallFaceY, Warm, facingY * 0.42f))
        drawPath(iso.poly(Triple(x1, y0, 0f), Triple(x1, y1, 0f), Triple(x1, y1, h), Triple(x1, y0, h)), lerp(WallFaceX, Warm, facingX * 0.36f))
        drawPath(iso.rect(x0, y0, x1, y1, h), WallTop)
    }
    // Names on glass tags, with what is lit in amber
    floor.rooms.forEach { r ->
        val inRoom = lights.filter { it.room == r.room }
        val lit = inRoom.count { Panel.isOn(it, states) }
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
        drawRoundRect(if (picked) Color.White.copy(alpha = if (focused) 1f else 0.7f) else Color(0xE0141518), tl, tag, androidx.compose.ui.geometry.CornerRadius(tag.height / 2))
        drawText(m, topLeft = Offset(tl.x + padX, tl.y + padY))
    }
}

