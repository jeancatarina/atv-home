package dev.jean.atvhome.social.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.jean.atvhome.social.game.HostCmd
import dev.jean.atvhome.social.game.TvAction
import dev.jean.atvhome.social.room.PlayerInfo
import dev.jean.atvhome.social.room.Reaction
import dev.jean.atvhome.ui.theme.Tv
import kotlinx.coroutines.delay

private val REACTION_LABELS = mapOf("suspect" to "Suspeito!", "lie" to "Mentira!", "laugh" to "Kkkk", "wow" to "Uau!")

/**
 * Reaction stamps from the couch rise along the right edge and fade, each for about 1.6 s. They
 * were sent by the phones but never drawn before. At most six are alive at once (the room caps it).
 */
@Composable
fun ReactionsLayer(reactions: List<Reaction>, players: List<PlayerInfo>) {
    val live = remember { mutableStateListOf<Reaction>() }
    val seen = remember { HashSet<Pair<String, Long>>() }
    LaunchedEffect(reactions) {
        reactions.filter { seen.add(it.by to it.at) }.forEach { live.add(it) }
    }
    Box(Modifier.fillMaxSize().padding(end = 36.dp, bottom = 110.dp), contentAlignment = Alignment.BottomEnd) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
            live.forEach { r ->
                androidx.compose.runtime.key(r.by, r.at) {
                    val p = players.firstOrNull { it.player.id == r.by }?.player
                    val t = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { t.animateTo(1f, tween(1600, easing = LinearEasing)); live.remove(r) }
                    if (p != null) Row(
                        Modifier.graphicsLayer { alpha = (1f - t.value).coerceIn(0f, 1f).let { a -> if (t.value < 0.15f) t.value / 0.15f else a }; translationY = -60f * t.value }
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50)).padding(start = 5.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Avatar(p, 30.dp)
                        Text(REACTION_LABELS[r.kind] ?: r.kind, style = Stage.Body.copy(fontSize = 18.sp), color = Color.White)
                    }
                }
            }
        }
    }
}

/** The host's line, in a pill at the top of the screen, for as long as the view model keeps it. */
@Composable
fun HostRibbon(line: String?) {
    // Low on the screen, above the TV's button: the top holds the game's name and the phase
    Box(Modifier.fillMaxSize().padding(bottom = 100.dp), contentAlignment = Alignment.BottomCenter) {
        if (line != null) Row(
            Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 9.dp).widthIn(max = 760.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Mic, null, Modifier.padding(0.dp), tint = Stage.Gold)
            Text(line, style = Stage.Body.copy(fontSize = 18.sp), color = Tv.Primary, maxLines = 2)
        }
    }
}

/**
 * The TV's button for this moment of the game. A "hold" action needs OK held for a moment, so a
 * stray press cannot skip people who are still deciding; the hint says so. Nothing shows when the
 * phones are the ones acting.
 */
@Composable
fun TvActionBar(action: TvAction?, accent: Color, onFire: (HostCmd) -> Unit, modifier: Modifier = Modifier, focus: androidx.compose.ui.focus.FocusRequester? = null, hint: String? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (action != null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton(
                action.label, onClick = { if (!action.hold) onFire(action.cmd) }, onLongClick = if (action.hold) ({ onFire(action.cmd) }) else null,
                accent = accent, modifier = if (focus != null) Modifier.then(Modifier.focusRequesterOrNone(focus)) else Modifier,
            )
            action.secondary?.let { s -> TvButton(s.label, onClick = { if (!s.hold) onFire(s.cmd) }, onLongClick = if (s.hold) ({ onFire(s.cmd) }) else null) }
        }
        val holdHint = if (action != null && (action.hold || action.secondary?.hold == true)) "Segure OK pra confirmar" else null
        val line = listOfNotNull(holdHint, hint).joinToString("  ·  ")
        if (line.isNotEmpty()) Text(line, style = Tv.Caption.copy(fontSize = 12.sp), color = Tv.Tertiary)
    }
}

private fun Modifier.focusRequesterOrNone(f: androidx.compose.ui.focus.FocusRequester) = this.then(Modifier.focusRequester(f))
