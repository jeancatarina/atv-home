package dev.jean.atvhome.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import dev.jean.atvhome.casa.Shortcut
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.jean.atvhome.data.HouseModel
import dev.jean.atvhome.tuya.Kind
import dev.jean.atvhome.tuya.Light
import dev.jean.atvhome.tuya.Link
import dev.jean.atvhome.tuya.Panel
import dev.jean.atvhome.tuya.Show
import dev.jean.atvhome.ui.components.Chip
import dev.jean.atvhome.ui.components.FocusCard
import dev.jean.atvhome.ui.components.PillButton
import dev.jean.atvhome.ui.components.RowPadding
import dev.jean.atvhome.ui.components.RowSpacing
import dev.jean.atvhome.ui.theme.LocalAccent
import dev.jean.atvhome.ui.theme.Tv
import dev.jean.atvhome.ui.theme.screen
import kotlinx.coroutines.launch

private val Lit = Color(0xFFFFD60A)
private const val NO_HUB = "Não consegui falar com o hub"

/** "Sem conexão com o hub" and the like; null while all is well. */
private fun Link.problem(): String? = when (this) {
    is Link.Online, Link.Off -> null
    Link.Connecting -> "conectando ao hub…"
    is Link.Unreachable -> "sem conexão com o hub"
    is Link.BadKey -> "a chave do hub mudou: rode tools/tuya-setup.sh"
}

/**
 * The Casa row under the dock: one tile per light, the most used first; OK switches it. The tile
 * changes on the same frame (the hub confirms in the background and the tile goes back if it does
 * not). Hold OK, then ▼, to take a light off the row. Ends with "Apagar tudo" and "Ver a casa".
 */
@Composable
fun HouseRow(home: HouseModel, onHouse: () -> Unit, toast: (String) -> Unit) {
    val row by home.row.collectAsState()
    val states = home.states.collectAsState()
    val link by home.link.collectAsState()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<String?>(null) }
    val title = listOfNotNull("Casa", link.problem()).joinToString("  ·  ")
    Column(Modifier.padding(top = 26.dp)) {
        Text(title, style = Tv.Headline, color = Tv.Secondary, modifier = Modifier.padding(start = Tv.Edge, bottom = 10.dp))
        LazyRow(contentPadding = RowPadding, horizontalArrangement = RowSpacing) {
            items(row, key = { it.key }) { light ->
                val isEditing = editing == light.key
                LightTile(
                    light = light,
                    states = states,
                    editing = isEditing,
                    modifier = Modifier.onPreviewKeyEvent { e ->
                        if (!isEditing || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionDown -> {
                                editing = null; home.setHidden(light, true)
                                toast("${light.name} saiu da fileira; volta pela tela Ver a casa"); true
                            }
                            Key.DirectionUp -> true
                            Key.Back, Key.Escape -> { editing = null; true }
                            else -> false
                        }
                    },
                    onClick = {
                        if (editing != null) editing = null
                        else scope.launch { if (!home.toggle(light)) toast(NO_HUB) }
                    },
                    onLongClick = { editing = light.key },
                )
            }
            item(key = "allOff") {
                ActionTile("Apagar tudo", Icons.Rounded.PowerSettingsNew) {
                    scope.launch { if (!home.switchAll(home.lights, false)) toast("Algumas luzes não responderam") }
                }
            }
            item(key = "house") { ActionTile("Ver a casa", Icons.Rounded.GridView, onHouse) }
        }
        AnimatedVisibility(editing != null, enter = fadeIn(), exit = fadeOut()) {
            Text("Mais usadas primeiro  ·  ▼ tira da fileira  ·  OK concluir", style = Tv.Meta, color = Tv.Primary, modifier = Modifier.padding(start = Tv.Edge, top = 8.dp))
        }
    }
}

/**
 * One light. Reads only its own keys of [states]: a push from the hub redraws the tiles it changed,
 * not the grid.
 */
@Composable
fun LightTile(
    light: Light,
    states: State<Map<String, Boolean>>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    editing: Boolean = false,
    dimmed: Boolean = false,
    showRoom: Boolean = true,
) {
    val on by remember(light.key) { derivedStateOf { Panel.isOn(light, states.value) } }
    val accent = LocalAccent.current
    FocusCard(
        onClick = onClick,
        onLongClick = onLongClick,
        focusedScale = 1.1f,
        modifier = modifier.width(132.dp).height(88.dp).graphicsLayer { alpha = if (dimmed) 0.5f else 1f; rotationZ = if (editing) 1.5f else 0f },
    ) { focused ->
        Box(Modifier.fillMaxSize().background(if (on) lerp(accent, Color.White, if (focused) 0.28f else 0.12f) else Color.Transparent)) {
            Icon(
                when {
                    light.kind == Kind.SCENE -> Icons.Rounded.AutoAwesome
                    on -> Icons.Rounded.Lightbulb
                    else -> Icons.Outlined.Lightbulb
                },
                contentDescription = null,
                tint = if (on) Lit else Tv.Tertiary,
                modifier = Modifier.padding(10.dp).size(24.dp),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(light.name, style = Tv.Meta, color = Tv.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showRoom) Text(light.room, style = Tv.Caption, color = Tv.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, onClick: () -> Unit) {
    FocusCard(onClick = onClick, focusedScale = 1.1f, modifier = Modifier.width(132.dp).height(88.dp)) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, contentDescription = null, tint = Tv.Primary, modifier = Modifier.size(24.dp))
            Text(label, style = Tv.Meta, color = Tv.Primary, maxLines = 1)
        }
    }
}

/** What the panel shows; kept by the caller so the overlay reopens where it was. */
class PanelState {
    var show by mutableStateOf(Show.LIGHTS)
    var room by mutableStateOf<String?>(null)
}

/**
 * The house panel, the same on Home ("Ver a casa", [columns] across the screen) and over a film (the
 * film overlay's side sheet). Top to bottom: shortcuts (everything off; a room off or on: the chosen
 * room, or the TV's room, the first in the app), filters with counts (Acesas, Luzes, Cenas, Todas,
 * then rooms), and the lights by room, each room with its own "Apagar".
 *
 * Holding OK on a light puts it on the Casa row or takes it off ([onRowToggle], Home only).
 */
@Composable
fun HousePanel(
    home: HouseModel,
    toast: (String) -> Unit,
    state: PanelState = remember { PanelState() },
    columns: GridCells = GridCells.Adaptive(150.dp),
    edge: Dp = Tv.Edge,
    firstFocus: FocusRequester = remember { FocusRequester() },
    onRowToggle: ((Light, Boolean) -> Unit)? = null,
    shortcut: Pair<String, () -> Unit>? = null,
) {
    val states = home.states.collectAsState()
    val now = states.value
    val link by home.link.collectAsState()
    val row by home.row.collectAsState()
    val scope = rememberCoroutineScope()
    val lights = home.lights
    val rooms = home.rooms
    val lit = lights.count { Panel.isOn(it, now) }
    val shown = Panel.filter(lights, now, state.show, state.room)
    val onRow = row.map { it.key }.toSet()
    val focusRoom = state.room ?: rooms.firstOrNull()
    // The tiles are the feedback: only a failure says something
    fun group(of: List<Light>, on: Boolean) = scope.launch {
        if (!home.switchAll(of, on)) toast("Algumas luzes não responderam")
    }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    LazyVerticalGrid(
        columns = columns,
        contentPadding = PaddingValues(start = edge, end = edge, top = 28.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Casa", style = Tv.Display, color = Tv.Primary)
                Spacer(Modifier.width(14.dp))
                Text(
                    link.problem() ?: if (lit == 0) "tudo apagado" else if (lit == 1) "1 acesa" else "$lit acesas",
                    style = Tv.Headline, color = if (link.problem() != null) Lit else Tv.Secondary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        // Shortcuts: what people reach for during a film
        item(span = { GridItemSpan(maxLineSpan) }, key = "shortcuts") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                item(key = "off") {
                    PillButton("Apagar tudo", { group(lights, false) },
                        modifier = Modifier.focusRequester(firstFocus))
                }
                if (focusRoom != null) {
                    val inRoom = lights.filter { it.room == focusRoom }
                    item(key = "roomOff") { PillButton("Apagar $focusRoom", { group(inRoom, false) }) }
                    item(key = "roomOn") { PillButton("Acender $focusRoom", { group(inRoom, true) }) }
                }
                if (shortcut != null) item(key = "shortcut") { PillButton(shortcut.first, shortcut.second) }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(Show.entries, key = { it.name }) { s ->
                    val n = Panel.filter(lights, now, s, state.room).size
                    Chip("${s.label} $n", state.show == s, { state.show = s })
                }
                item(key = "allRooms") { Chip("Todos os cômodos", state.room == null, { state.room = null }) }
                items(rooms, key = { "room:$it" }) { r -> Chip(r, state.room == r, { state.room = if (state.room == r) null else r }) }
            }
        }
        if (shown.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                Text(
                    if (state.show == Show.LIT) "Nenhuma luz acesa${state.room?.let { " em $it" }.orEmpty()}" else "Nada aqui",
                    style = Tv.Headline, color = Tv.Tertiary, modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
        shown.groupBy { it.room }.forEach { (room, inRoom) ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "room-$room") {
                val roomLit = inRoom.count { Panel.isOn(it, now) }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(room, style = Tv.Headline, color = Tv.Secondary)
                    if (roomLit > 0) {
                        Spacer(Modifier.width(12.dp))
                        PillButton("Apagar", { group(lights.filter { it.room == room }, false) })
                    }
                }
            }
            items(inRoom, key = { it.key }) { light ->
                LightTile(
                    light = light,
                    states = states,
                    showRoom = false,
                    dimmed = onRowToggle != null && light.key !in onRow,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { scope.launch { if (!home.toggle(light)) toast(NO_HUB) } },
                    onLongClick = onRowToggle?.let { toggleRow ->
                        { toggleRow(light, light.key !in onRow) }
                    },
                )
            }
        }
    }
}

/** "Ver a casa": the house panel on the whole screen, from the Casa row, with the film shortcut's setting. */
@Composable
fun HouseScreen(home: HouseModel, toast: (String) -> Unit) {
    val context = LocalContext.current
    var key by remember { mutableIntStateOf(Shortcut.get(context)) }
    var picking by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(LocalAccent.current.screen())) {
        HousePanel(
            home, toast,
            onRowToggle = { light, add ->
                home.setHidden(light, !add)
                toast(if (add) "${light.name} está na fileira Casa" else "${light.name} saiu da fileira")
            },
            shortcut = "Atalho no filme: ${Shortcut.name(key)}" to { picking = true },
        )
        if (picking) {
            ShortcutPicker(
                current = key,
                onPick = { code ->
                    Shortcut.set(context, code)
                    key = code
                    picking = false
                    toast(if (code == Shortcut.NONE) "Atalho no filme desligado" else "${Shortcut.name(code)} abre a Casa em qualquer app")
                },
                onCancel = { picking = false },
            )
        }
    }
}

/**
 * Choosing the film shortcut: press the key on the remote, or pick a colour (or none) with ◀ ▶ and OK.
 * Keys the TV needs everywhere (moving, OK, Back, Home, sound, power, playback) are refused. Back cancels.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ShortcutPicker(current: Int, onPick: (Int) -> Unit, onCancel: () -> Unit) {
    val first = remember { FocusRequester() }
    var refused by remember { mutableStateOf<String?>(null) }
    var holding by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        Shortcut.capturing = true
        onDispose { Shortcut.capturing = false }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f))
            .focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()
            .onPreviewKeyEvent { e ->
                val native = e.nativeKeyEvent
                val code = native.keyCode
                val held = native.repeatCount > 0 && native.eventTime - native.downTime >= Shortcut.HOLD_MS
                // Back and Play/Pause held: the shortcut for remotes with no spare key
                (Shortcut.HOLD + code).takeIf { it in Shortcut.HOLDS }?.let { hold ->
                    if (e.type == KeyEventType.KeyDown && held && !holding) { holding = true; onPick(hold) }
                    if (e.type == KeyEventType.KeyUp) {
                        if (!holding) { if (e.key == Key.Back) onCancel() else refused = "Segure ${Shortcut.name(code)} para usá-la como atalho" }
                        holding = false
                    }
                    return@onPreviewKeyEvent true
                }
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    e.key == Key.Escape -> { onCancel(); true }
                    e.key in NAVIGATION -> false
                    Shortcut.allowed(code) -> { onPick(code); true }
                    else -> { refused = "${Shortcut.name(code)} não pode: a TV precisa dela em todo app"; true }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(560.dp).background(Color(0xF21C1C1E), RoundedCornerShape(22.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Atalho no filme", style = Tv.Title, color = Tv.Primary)
            Text(
                "Aperte no controle a tecla que vai abrir a Casa por cima de qualquer app, ou escolha abaixo. " +
                    "Controle sem tecla sobrando: segure Voltar ou Play/Pause (o toque curto continua igual). " +
                    "Agora: ${Shortcut.name(current)}.",
                style = Tv.Body, color = Tv.Secondary,
            )
            // Colours, then the held keys and none; focus starts on the key in use (the first colour
            // when it is a key pressed on the remote)
            val start = (Shortcut.COLORS + Shortcut.HOLDS + Shortcut.NONE).let { all -> all.indexOf(current).takeIf { it >= 0 }?.let(all::get) } ?: Shortcut.COLORS.first()
            for (line in listOf(Shortcut.COLORS, Shortcut.HOLDS + Shortcut.NONE)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    line.forEach { code ->
                        Chip(Shortcut.name(code), code == current, { onPick(code) }, modifier = if (code == start) Modifier.focusRequester(first) else Modifier)
                    }
                }
            }
            Text(refused ?: "Um toque em Voltar cancela", style = Tv.Meta, color = if (refused != null) Lit else Tv.Tertiary)
        }
    }
}

private val NAVIGATION = setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
