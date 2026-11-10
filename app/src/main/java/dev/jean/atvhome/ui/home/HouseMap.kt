package dev.jean.atvhome.ui.home

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.jean.atvhome.data.HouseModel
import dev.jean.atvhome.tuya.Light
import dev.jean.atvhome.tuya.Panel
import dev.jean.atvhome.tuya.Plan
import dev.jean.atvhome.tuya.RoomBox
import dev.jean.atvhome.ui.components.requestWhenReady
import dev.jean.atvhome.ui.theme.Tv
import kotlinx.coroutines.launch

/**
 * "Maquete": the house as an exploded isometric model, floors stacked from the ground up. The
 * chosen floor opens: the floors above it lift and fade, so its rooms are in view. A room glows
 * amber as its lights come on (more lit, warmer), with one dot per light.
 *
 * ▲ ▼ change floor, ◀ ▶ change room, OK switches the whole room (off when anything is on, else on).
 * ▶ past the last room goes to the room's lights beside the model.
 *
 * Drawn on one Canvas (paths and flat fills, no blur): it redraws only when a light changes or
 * while a floor opens.
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
    val open by animateFloatAsState(floor.toFloat(), tween(420), label = "open")
    val text = rememberTextMeasurer()
    fun switchRoom(on: Boolean) = scope.launch { if (!home.switchAll(inRoom, on)) toast("Algumas luzes não responderam") }
    androidx.compose.runtime.LaunchedEffect(Unit) { mapFocus.requestWhenReady() }

    Row(modifier.fillMaxSize().padding(start = Tv.Edge, end = Tv.Edge, top = 28.dp, bottom = 24.dp)) {
        Box(
            Modifier.weight(1.45f).fillMaxHeight()
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
                drawModel(plan, home.lights, states, open, floor, room, focused, text)
            }
            Text(
                "▲ ▼ andar  ·  ◀ ▶ cômodo  ·  OK acende ou apaga o cômodo",
                style = Tv.Caption, color = Tv.Tertiary, modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(24.dp))
        // The chosen room: what is lit, its shortcuts and its lights
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(plan.floors.getOrNull(floor)?.name.orEmpty(), style = Tv.Meta, color = Casa.Amber)
            Text(room?.room ?: "", style = Tv.Title, color = Tv.Primary, maxLines = 1)
            Text(
                when { inRoom.isEmpty() -> "Sem luzes"; roomLit == 0 -> "Apagado"; else -> "$roomLit de ${inRoom.size} acesas" },
                style = Tv.Meta, color = Tv.Secondary,
            )
            Spacer(Modifier.height(14.dp))
            if (inRoom.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SceneButton(Icons.Rounded.NightsStay, "Apagar", { switchRoom(false) }, tint = Casa.Scene)
                    SceneButton(Icons.Rounded.WbSunny, "Acender", { switchRoom(true) })
                }
                Spacer(Modifier.height(14.dp))
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

// --- Drawing ------------------------------------------------------------------------------------

private const val COS = 0.866f
private const val SIN = 0.5f
/** Floor to floor, in footprint units. */
private const val GAP = 2.4f
/** How high the floors above the chosen one lift, and how faint they get. */
private const val LIFT = 3.6f
private const val SLAB = 0.32f
private const val WALL = 0.9f

private val SlabLeft = Color(0xFF1E2024)
private val SlabRight = Color(0xFF16171A)
private val RoomOff = Color(0xFF2C2F35)
private val WallOff = Color(0xFF3A3D44)
private val Glow = Color(0xFFFFB24A)
private val Bulb = Color(0xFFFFE6A3)

/** Footprint (x, y) and height z to the screen, after fitting the whole stack in the canvas. */
private class Iso(val s: Float, val ox: Float, val oy: Float) {
    fun p(x: Float, y: Float, z: Float) = Offset(ox + (x - y) * COS * s, oy + (x + y) * SIN * s - z * s)
}

private fun DrawScope.fit(plan: Plan): Iso {
    // The stack at its tallest (every floor lifted), so the scale holds still while floors open
    val n = plan.floors.size
    val top = (n - 1) * GAP + (n - 1).coerceAtLeast(0) * LIFT + WALL
    val w = (plan.width + plan.depth) * COS
    val h = (plan.width + plan.depth) * SIN + top + SLAB
    val s = minOf(size.width / w, (size.height - 24.dp.toPx()) / h) * 0.94f
    val ox = (size.width - w * s) / 2f + plan.depth * COS * s
    val oy = 24.dp.toPx() + (size.height - 24.dp.toPx() - h * s) / 2f + top * s
    return Iso(s, ox, oy)
}

private fun quad(iso: Iso, a: Triple<Float, Float, Float>, b: Triple<Float, Float, Float>, c: Triple<Float, Float, Float>, d: Triple<Float, Float, Float>) = Path().apply {
    listOf(a, b, c, d).forEachIndexed { i, (x, y, z) -> val o = iso.p(x, y, z); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
    close()
}

private fun DrawScope.drawModel(
    plan: Plan,
    lights: List<Light>,
    states: Map<String, Boolean>,
    open: Float,
    chosen: Int,
    room: RoomBox?,
    focused: Boolean,
    text: TextMeasurer,
) {
    val iso = fit(plan)
    val w = plan.width
    val d = plan.depth
    plan.floors.forEachIndexed { k, floor ->
        // Floors above the chosen one lift and fade; the ones below stay, a little dimmer
        val above = (k - open).coerceIn(0f, 1f)
        val below = (open - k).coerceIn(0f, 1f)
        val z = k * GAP + above * LIFT
        val alpha = 1f - 0.9f * above - 0.45f * below
        val isChosen = k == chosen
        // The slab: its two front faces
        drawPath(quad(iso, Triple(0f, d, z), Triple(w, d, z), Triple(w, d, z - SLAB), Triple(0f, d, z - SLAB)), SlabLeft, alpha = alpha)
        drawPath(quad(iso, Triple(w, 0f, z), Triple(w, d, z), Triple(w, d, z - SLAB), Triple(w, 0f, z - SLAB)), SlabRight, alpha = alpha)
        drawPath(quad(iso, Triple(0f, 0f, z), Triple(w, 0f, z), Triple(w, d, z), Triple(0f, d, z)), RoomOff, alpha = alpha)

        floor.rooms.forEach { r ->
            val inRoom = lights.filter { it.room == r.room }
            val lit = inRoom.count { Panel.isOn(it, states) }
            val warmth = if (inRoom.isEmpty() || lit == 0) 0f else 0.45f + 0.55f * lit / inRoom.size
            val x0 = r.x; val y0 = r.y; val x1 = r.x + r.w; val y1 = r.y + r.d
            // Back walls where the room meets the back of the house: they catch its light
            val wall = lerp(WallOff, Glow, warmth * 0.45f)
            if (y0 == 0f) drawPath(quad(iso, Triple(x0, 0f, z), Triple(x1, 0f, z), Triple(x1, 0f, z + WALL), Triple(x0, 0f, z + WALL)), wall, alpha = alpha)
            if (x0 == 0f) drawPath(quad(iso, Triple(0f, y0, z), Triple(0f, y1, z), Triple(0f, y1, z + WALL), Triple(0f, y0, z + WALL)), lerp(wall, Color.Black, 0.18f), alpha = alpha)
            val floorPath = quad(iso, Triple(x0, y0, z), Triple(x1, y0, z), Triple(x1, y1, z), Triple(x0, y1, z))
            drawPath(floorPath, lerp(RoomOff, Glow, warmth), alpha = alpha)
            // Walls between rooms; an open floor only hints where one space becomes the next
            drawPath(
                floorPath, Color.White.copy(alpha = if (floor.open) 0.07f else 0.16f), alpha = alpha,
                style = Stroke(width = 1.dp.toPx(), pathEffect = if (floor.open) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null),
            )
            // One dot per light, along the room's middle
            if (inRoom.isNotEmpty()) {
                val step = r.w / (inRoom.size + 1)
                inRoom.forEachIndexed { i, l ->
                    val c = iso.p(x0 + step * (i + 1), y0 + r.d / 2f, z)
                    val on = Panel.isOn(l, states)
                    if (on) drawCircle(Bulb.copy(alpha = 0.28f), radius = 7.dp.toPx(), center = c, alpha = alpha)
                    drawCircle(if (on) Bulb else Color.White.copy(alpha = 0.28f), radius = 2.8.dp.toPx(), center = c, alpha = alpha)
                }
            }
            if (isChosen) {
                val label = text.measure(r.room, TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (warmth > 0.5f) Casa.Ink else Color.White.copy(alpha = 0.85f)))
                val c = iso.p(x0 + r.w / 2f, y0 + r.d / 2f, z)
                drawText(label, topLeft = Offset(c.x - label.size.width / 2f, c.y - label.size.height - 6.dp.toPx()))
            }
            if (isChosen && r == room) {
                drawPath(floorPath, Color.White.copy(alpha = if (focused) 1f else 0.4f), style = Stroke(width = 2.5.dp.toPx()))
            }
        }
        // The floor's name by its front corner
        val name = text.measure(floor.name, TextStyle(fontSize = 11.sp, fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium, color = if (isChosen) Color.White else Color.White.copy(alpha = 0.45f)))
        val corner = iso.p(0f, d, z - SLAB)
        drawText(name, topLeft = Offset(corner.x - name.size.width - 10.dp.toPx(), corner.y - name.size.height), alpha = alpha)
    }
}
