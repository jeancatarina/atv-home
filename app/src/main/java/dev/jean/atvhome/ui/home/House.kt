package dev.jean.atvhome.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.jean.atvhome.casa.HouseKeyService
import dev.jean.atvhome.casa.Shortcut
import dev.jean.atvhome.data.AdShield
import dev.jean.atvhome.data.HouseModel
import dev.jean.atvhome.tuya.Kind
import dev.jean.atvhome.tuya.Light
import dev.jean.atvhome.tuya.Link
import dev.jean.atvhome.tuya.Panel
import dev.jean.atvhome.tuya.Show
import dev.jean.atvhome.ui.components.RowPadding
import dev.jean.atvhome.ui.components.requestWhenReady
import dev.jean.atvhome.ui.theme.LocalAccent
import dev.jean.atvhome.ui.theme.Tv
import dev.jean.atvhome.ui.theme.screen
import kotlinx.coroutines.launch

private const val NO_HUB = "Não consegui falar com o hub"

/** "Sem conexão com o hub" and the like; null while all is well. */
private fun Link.problem(): String? = when (this) {
    is Link.Online, Link.Off -> null
    Link.Connecting -> "conectando ao hub…"
    is Link.Unreachable -> "sem conexão com o hub"
    is Link.BadKey -> "a chave do hub mudou: rode tools/tuya-setup.sh"
}

private fun Link.dot(): Color = when (this) {
    is Link.Online -> Casa.Online
    Link.Off, Link.Connecting -> Casa.Waiting
    else -> Casa.Problem
}

private fun lit(n: Int) = when (n) { 0 -> "Tudo apagado"; 1 -> "1 luz acesa"; else -> "$n luzes acesas" }

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
    val litCount by remember { derivedStateOf { home.lights.count { Panel.isOn(it, states.value) } } }
    // A light taken off the row takes the focus with it: it goes to the tile that takes its place
    val focus = remember { HashMap<String, FocusRequester>() }
    var refocus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(refocus) { refocus?.let { focus[it]?.requestWhenReady(); refocus = null } }
    Column(Modifier.padding(top = 26.dp)) {
        Row(Modifier.padding(start = Tv.Edge, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Casa", style = Tv.Headline, color = Tv.Secondary)
            Spacer(Modifier.width(12.dp))
            LinkStatus(link.dot(), link.problem() ?: lit(litCount))
        }
        LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(row, key = { it.key }) { light ->
                val isEditing = editing == light.key
                LightTile(
                    light = light,
                    states = states,
                    editing = isEditing,
                    modifier = Modifier.width(156.dp).height(100.dp)
                        .focusRequester(focus.getOrPut(light.key) { FocusRequester() })
                        .onPreviewKeyEvent { e ->
                            if (!isEditing || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.DirectionDown -> {
                                    val i = row.indexOf(light)
                                    refocus = (row.getOrNull(i + 1) ?: row.getOrNull(i - 1))?.key ?: "allOff"
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
                CasaTile(
                    Icons.Rounded.PowerSettingsNew, "Apagar tudo", lit(litCount), on = false,
                    onClick = { scope.launch { if (!home.switchAll(home.lights, false)) toast("Algumas luzes não responderam") } },
                    modifier = Modifier.width(156.dp).height(100.dp).focusRequester(focus.getOrPut("allOff") { FocusRequester() }),
                )
            }
            item(key = "house") {
                CasaTile(
                    Icons.Rounded.SpaceDashboard, "Ver a casa", "${home.rooms.size} cômodos", on = false,
                    onClick = onHouse, modifier = Modifier.width(156.dp).height(100.dp),
                )
            }
        }
        AnimatedVisibility(editing != null, enter = fadeIn(), exit = fadeOut()) {
            Text("Mais usadas primeiro  ·  ▼ tira da fileira  ·  OK concluir", style = Tv.Meta, color = Tv.Primary, modifier = Modifier.padding(start = Tv.Edge, top = 10.dp))
        }
    }
}

/**
 * One light. Reads only its own keys of [states]: a push from the hub redraws the tiles it changed,
 * not the grid. [showRoom]: on the row the room is in the status line; in the panel the section says it.
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
    val scene = light.kind == Kind.SCENE
    val state = if (scene) (if (on) "Cena ativa" else "Cena") else if (on) "Acesa" else "Apagada"
    CasaTile(
        icon = if (scene) Icons.Rounded.AutoAwesome else if (on) Icons.Rounded.Lightbulb else Icons.Outlined.Lightbulb,
        name = light.name,
        status = if (showRoom) "$state · ${light.room}" else state,
        on = on,
        disc = if (scene) Casa.Scene else Casa.Amber,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.graphicsLayer { alpha = if (dimmed) 0.45f else 1f; rotationZ = if (editing) 1.5f else 0f },
    )
}

/** What the panel shows; kept by the caller so the overlay reopens where it was. */
class PanelState {
    var show by mutableStateOf(Show.LIGHTS)
    var room by mutableStateOf<String?>(null)
    /**
     * In "Acesas", a light turned off stays where it was until the filter changes: a tile vanishing
     * under the remote would take the focus with it, and turning it back on would mean finding it.
     */
    var keep by mutableStateOf<Set<String>>(emptySet())

    fun filter(s: Show) { show = s; keep = emptySet() }
    fun room(r: String?) { room = r; keep = emptySet() }
}

/**
 * The house panel, the same on Home ("Ver a casa", [columns] across the screen) and over a film (the
 * film overlay's side sheet). Top to bottom: the title with what is lit and whether the hub answers;
 * shortcuts (everything off; a room off or on: the chosen room, or the TV's room, the first in the
 * app); a segmented filter (Acesas, Luzes, Cenas, Todas) and room tabs; the lights by room, each
 * room with its own button.
 *
 * Holding OK on a light puts it on the Casa row or takes it off ([onRowToggle], Home only).
 */
@Composable
fun HousePanel(
    home: HouseModel,
    toast: (String) -> Unit,
    state: PanelState = remember { PanelState() },
    columns: GridCells = GridCells.Adaptive(168.dp),
    edge: Dp = Tv.Edge,
    firstFocus: FocusRequester = remember { FocusRequester() },
    onRowToggle: ((Light, Boolean) -> Unit)? = null,
    shortcut: Pair<String, () -> Unit>? = null,
    shortcutFocus: FocusRequester = remember { FocusRequester() },
    compact: Boolean = false,
    onModel: (() -> Unit)? = null,
) {
    val states = home.states.collectAsState()
    val now = states.value
    val link by home.link.collectAsState()
    val row by home.row.collectAsState()
    val scope = rememberCoroutineScope()
    val lights = home.lights
    val rooms = home.rooms
    val litNow = Panel.filter(lights, now, Show.LIT, state.room)
    if (state.show == Show.LIT && !litNow.all { it.key in state.keep }) {
        SideEffect { state.keep = state.keep + litNow.map { it.key } }
    }
    val shown = if (state.show == Show.LIT) Panel.filter(lights, now, Show.ALL, state.room).filter { it.key in state.keep || Panel.isOn(it, now) }
    else Panel.filter(lights, now, state.show, state.room)
    val onRow = row.map { it.key }.toSet()
    val focusRoom = state.room ?: rooms.firstOrNull()
    val litAll = lights.count { Panel.isOn(it, now) }
    val litRooms = lights.filter { Panel.isOn(it, now) }.map { it.room }.distinct().size
    // The tiles are the feedback: only a failure says something
    fun group(of: List<Light>, on: Boolean) = scope.launch {
        if (!home.switchAll(of, on)) toast("Algumas luzes não responderam")
    }
    LaunchedEffect(Unit) { firstFocus.requestWhenReady() }

    LazyVerticalGrid(
        columns = columns,
        contentPadding = PaddingValues(start = edge, end = edge, top = if (compact) 24.dp else 30.dp, bottom = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            val summary = if (litAll == 0) "Tudo apagado" else "${lit(litAll)} em $litRooms ${if (litRooms == 1) "cômodo" else "cômodos"}"
            // Over a film the lights matter more than the title: one line for it, the status beside
            if (compact) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Casa", style = Tv.Title, color = Tv.Primary)
                        Spacer(Modifier.weight(1f))
                        LinkStatus(link.dot(), link.problem() ?: "Hub conectado")
                    }
                    Text(summary, style = Tv.Meta, color = Tv.Secondary)
                }
            } else {
                Column {
                    Text("Casa", style = Tv.Display, color = Tv.Primary)
                    Spacer(Modifier.height(4.dp))
                    Text(summary, style = Tv.Headline, color = Tv.Secondary)
                    Spacer(Modifier.height(6.dp))
                    LinkStatus(link.dot(), link.problem() ?: "Hub conectado")
                }
            }
        }
        // Shortcuts: what people reach for during a film
        item(span = { GridItemSpan(maxLineSpan) }, key = "shortcuts") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                item(key = "off") {
                    SceneButton(Icons.Rounded.PowerSettingsNew, "Apagar tudo", { group(lights, false) },
                        detail = lit(litAll), modifier = Modifier.focusRequester(firstFocus))
                }
                if (focusRoom != null) {
                    val inRoom = lights.filter { it.room == focusRoom }
                    item(key = "roomOff") {
                        SceneButton(Icons.Rounded.NightsStay, "Apagar", { group(inRoom, false) }, detail = focusRoom, tint = Casa.Scene)
                    }
                    item(key = "roomOn") {
                        SceneButton(Icons.Rounded.WbSunny, "Acender", { group(inRoom, true) }, detail = focusRoom)
                    }
                }
                if (onModel != null) item(key = "model") {
                    SceneButton(Icons.Rounded.ViewInAr, "Maquete", onModel, detail = "${home.plan.floors.size} ${if (home.plan.floors.size == 1) "andar" else "andares"}", tint = Casa.Scene)
                }
                if (shortcut != null) item(key = "shortcut") {
                    SceneButton(Icons.Rounded.Keyboard, "Atalho no filme", shortcut.second, detail = shortcut.first,
                        tint = Tv.Secondary, modifier = Modifier.focusRequester(shortcutFocus))
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
            val segments: @Composable () -> Unit = {
                SegmentGroup {
                    Show.entries.forEach { s ->
                        Segment(s.label, Panel.filter(lights, now, s, state.room).size, state.show == s, { state.filter(s) })
                    }
                }
            }
            val roomTabs: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
                item(key = "allRooms") { Segment("Todos os cômodos", null, state.room == null, { state.room(null) }) }
                items(rooms, key = { "room:$it" }) { r -> Segment(r, null, state.room == r, { state.room(if (state.room == r) null else r) }) }
            }
            if (compact) {
                // One line on the sheet: the filter, then the rooms, scrolling together
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                    item(key = "segments") { Row { segments(); Spacer(Modifier.width(8.dp)) } }
                    roomTabs()
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LazyRow(contentPadding = PaddingValues(vertical = 2.dp)) { item { segments() } }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 2.dp)) { roomTabs() }
                }
            }
        }
        if (shown.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                Column(Modifier.padding(top = 30.dp), horizontalAlignment = Alignment.Start) {
                    Icon(Icons.Rounded.NightsStay, contentDescription = null, tint = Tv.Tertiary, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (state.show == Show.LIT) "Nenhuma luz acesa${state.room?.let { " em $it" }.orEmpty()}" else "Nada aqui",
                        style = Tv.Headline, color = Tv.Tertiary,
                    )
                }
            }
        }
        shown.groupBy { it.room }.forEach { (room, inRoom) ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "room-$room") {
                // Always there, even with the room dark: a button vanishing under the remote takes the focus with it
                val roomLit = lights.count { it.room == room && Panel.isOn(it, now) }
                Row(Modifier.fillMaxWidth().padding(top = if (compact) 6.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(room, style = Casa.Section, color = Tv.Primary)
                        Text(if (roomLit == 0) "Apagado" else lit(roomLit), style = Casa.Status, color = Tv.Tertiary)
                    }
                    // Glass, like a button (a plain segment read as text)
                    Segment(if (roomLit > 0) "Apagar" else "Acender", null, true, { group(lights.filter { it.room == room }, roomLit == 0) })
                }
            }
            items(inRoom, key = { it.key }) { light ->
                LightTile(
                    light = light,
                    states = states,
                    showRoom = false,
                    dimmed = onRowToggle != null && light.key !in onRow,
                    modifier = Modifier.fillMaxWidth().height(100.dp),
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
    val shortcutFocus = remember { FocusRequester() }
    // Back from the picker, focus returns to the button that opened it (the picker's chips are gone)
    var pickedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(picking) { if (!picking && pickedOnce) shortcutFocus.requestWhenReady() }
    var model by remember { mutableStateOf(false) }
    // Back from the model goes to the list, not out of the house
    androidx.activity.compose.BackHandler(enabled = model) { model = false }
    Box(Modifier.fillMaxSize().background(LocalAccent.current.screen())) {
        if (model) {
            HouseMap(home, home.plan, toast)
            return@Box
        }
        HousePanel(
            home, toast,
            onRowToggle = { light, add ->
                home.setHidden(light, !add)
                toast(if (add) "${light.name} está na fileira Casa" else "${light.name} saiu da fileira")
            },
            shortcut = Shortcut.name(key) to { picking = true; pickedOnce = true },
            shortcutFocus = shortcutFocus,
            onModel = { model = true },
        )
        if (picking) {
            ShortcutPicker(
                current = key,
                onPick = { code ->
                    Shortcut.set(context, code)
                    // No shortcut, no service: nothing watches the remote and nothing stays connected
                    AdShield.keepListed(context, HouseKeyService::class.java, on = code != Shortcut.NONE)
                    key = code
                    picking = false
                    toast(if (code == Shortcut.NONE) "Atalho no filme desligado" else "${Shortcut.name(code)} abre a Casa em qualquer app")
                },
                onCancel = { picking = false },
            )
        }
    }
}

/** The colour keys get their colour beside the name. */
private fun swatch(code: Int): Color? = when (code) {
    android.view.KeyEvent.KEYCODE_PROG_RED -> Color(0xFFFF453A)
    android.view.KeyEvent.KEYCODE_PROG_GREEN -> Color(0xFF32D74B)
    android.view.KeyEvent.KEYCODE_PROG_YELLOW -> Color(0xFFFFD60A)
    android.view.KeyEvent.KEYCODE_PROG_BLUE -> Color(0xFF0A84FF)
    else -> null
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
    LaunchedEffect(Unit) { first.requestWhenReady() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f))
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
            Modifier.width(580.dp)
                .background(Casa.Sheet, RoundedCornerShape(28.dp))
                .border(1.dp, Casa.Hairline, RoundedCornerShape(28.dp))
                .padding(30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(Casa.Disc, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Keyboard, contentDescription = null, tint = Tv.Primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Atalho no filme", style = Tv.Title, color = Tv.Primary)
                    Text("Agora: ${Shortcut.name(current)}", style = Tv.Meta, color = Tv.Secondary)
                }
            }
            Text(
                "Aperte no controle a tecla que vai abrir a Casa por cima de qualquer app, ou escolha abaixo. " +
                    "Controle sem tecla sobrando: segure Voltar ou Play/Pause; o toque curto continua igual.",
                style = Tv.Body, color = Tv.Secondary,
            )
            // Colours, then the held keys and none; focus starts on the key in use (the first colour
            // when it is a key pressed on the remote)
            val start = (Shortcut.COLORS + Shortcut.HOLDS + Shortcut.NONE).let { all -> all.indexOf(current).takeIf { it >= 0 }?.let(all::get) } ?: Shortcut.COLORS.first()
            for (line in listOf(Shortcut.COLORS, Shortcut.HOLDS + Shortcut.NONE)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    line.forEach { code ->
                        Capsule(code == current, { onPick(code) }, if (code == start) Modifier.focusRequester(first) else Modifier) { focused ->
                            swatch(code)?.let { c ->
                                Box(Modifier.size(10.dp).background(c, CircleShape))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                Shortcut.name(code), style = Tv.Meta.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                                color = if (focused) Casa.Ink else Tv.Primary, maxLines = 1,
                            )
                        }
                    }
                }
            }
            Text(refused ?: "Um toque em Voltar cancela", style = Tv.Meta, color = if (refused != null) Casa.Waiting else Tv.Tertiary)
        }
    }
}

private val NAVIGATION = setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
