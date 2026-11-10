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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.jean.atvhome.data.SmartHome
import dev.jean.atvhome.tuya.Light
import dev.jean.atvhome.tuya.Link
import dev.jean.atvhome.ui.components.FocusCard
import dev.jean.atvhome.ui.components.RowPadding
import dev.jean.atvhome.ui.components.RowSpacing
import dev.jean.atvhome.ui.theme.LocalAccent
import dev.jean.atvhome.ui.theme.Tv
import dev.jean.atvhome.ui.theme.screen
import kotlinx.coroutines.launch

private val Lit = Color(0xFFFFD60A)

/** "Sem conexão com o hub" and the like, for the row title; null while all is well. */
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
fun HouseRow(home: SmartHome, onHouse: () -> Unit, toast: (String) -> Unit) {
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
                        else scope.launch { if (!home.toggle(light)) toast("Não consegui falar com o hub") }
                    },
                    onLongClick = { editing = light.key },
                )
            }
            item(key = "allOff") {
                ActionTile("Apagar tudo", Icons.Rounded.PowerSettingsNew) {
                    scope.launch { if (!home.allOff()) toast("Algumas luzes não responderam") }
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
 * One light. Reads only its own key of [states]: a push from the hub redraws the tiles it changed,
 * not the row.
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
) {
    val on by remember(light.key) { derivedStateOf { SmartHome.isOn(light, states.value) } }
    val accent = LocalAccent.current
    FocusCard(
        onClick = onClick,
        onLongClick = onLongClick,
        focusedScale = 1.1f,
        modifier = modifier.width(132.dp).height(88.dp).graphicsLayer { alpha = if (dimmed) 0.5f else 1f; rotationZ = if (editing) 1.5f else 0f },
    ) { focused ->
        Box(Modifier.fillMaxSize().background(if (on) lerp(accent, Color.White, if (focused) 0.28f else 0.12f) else Color.Transparent)) {
            Icon(
                if (on) Icons.Rounded.Lightbulb else Icons.Outlined.Lightbulb,
                contentDescription = null,
                tint = if (on) Lit else Tv.Tertiary,
                modifier = Modifier.padding(10.dp).size(24.dp),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(light.name, style = Tv.Meta, color = Tv.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(light.room, style = Tv.Caption, color = Tv.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/**
 * Every key of every switch, by room, including the ones off the row (dimmed). OK switches the
 * light; holding OK puts it on the row or takes it off.
 */
@Composable
fun HouseScreen(home: SmartHome, toast: (String) -> Unit) {
    val states = home.states.collectAsState()
    val link by home.link.collectAsState()
    val row by home.row.collectAsState()
    val scope = rememberCoroutineScope()
    val onRow = row.map { it.key }.toSet()
    val rooms = remember(home.lights) { home.lights.groupBy { it.room }.toList() }
    Box(Modifier.fillMaxSize().background(LocalAccent.current.screen())) {
        LazyColumn(contentPadding = PaddingValues(top = 36.dp, bottom = 48.dp)) {
            item {
                Row(Modifier.padding(start = Tv.Edge, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Casa", style = Tv.Display, color = Tv.Primary)
                    Spacer(Modifier.width(16.dp))
                    Text(link.problem() ?: "Segure OK para pôr ou tirar da fileira da tela inicial", style = Tv.Meta, color = Tv.Secondary)
                }
            }
            items(rooms, key = { it.first }) { (room, lights) ->
                Column(Modifier.padding(top = 20.dp)) {
                    Text(room, style = Tv.Headline, color = Tv.Secondary, modifier = Modifier.padding(start = Tv.Edge, bottom = 10.dp))
                    LazyRow(contentPadding = RowPadding, horizontalArrangement = RowSpacing) {
                        items(lights, key = { it.key }) { light ->
                            val shown = light.key in onRow
                            LightTile(
                                light = light,
                                states = states,
                                dimmed = !shown,
                                onClick = { scope.launch { if (!home.toggle(light)) toast("Não consegui falar com o hub") } },
                                onLongClick = {
                                    home.setHidden(light, shown)
                                    toast(if (shown) "${light.name} saiu da fileira" else "${light.name} está na fileira Casa")
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
