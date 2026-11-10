package dev.jean.atvhome.social.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.jean.atvhome.social.Sfx
import dev.jean.atvhome.social.game.Player
import dev.jean.atvhome.social.progress.Celebration
import dev.jean.atvhome.social.room.PlayerInfo
import dev.jean.atvhome.ui.theme.Tv
import kotlinx.coroutines.delay

private data class Look(val kicker: String, val icon: ImageVector, val color: Color, val sfx: Sfx.Effect)

private fun look(c: Celebration): Look = when (c.kind) {
    "LEVEL" -> Look("Subiu de nível", Icons.Rounded.TrendingUp, Color(0xFF30D158), Sfx.Effect.LEVELUP)
    "BADGE" -> Look("Conquista desbloqueada", Icons.Rounded.MilitaryTech, tierColor(c.tier), Sfx.Effect.BADGE)
    "RECORD" -> Look("Recorde da casa", Icons.Rounded.EmojiEvents, Stage.Gold, Sfx.Effect.RECORD)
    "CHALLENGE" -> Look("Desafio da semana", Icons.Rounded.TaskAlt, Color(0xFF64D2FF), Sfx.Effect.BADGE)
    "STREAK" -> Look("Em chamas", Icons.Rounded.LocalFireDepartment, Color(0xFFFF9F0A), Sfx.Effect.BADGE)
    else -> Look("Momento", Icons.Rounded.Star, Stage.Gold, Sfx.Effect.BADGE)
}

/** One celebration as a card: a coloured bar, the person's face, what happened and what it is worth. */
@Composable
fun CelebrationCard(c: Celebration, who: Player?, modifier: Modifier = Modifier, compact: Boolean = false) {
    val l = look(c)
    Row(
        modifier.width(if (compact) 340.dp else 560.dp).height(IntrinsicSize.Min).background(Color(0xE61C1C1E), RoundedCornerShape(20.dp)).padding(end = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(8.dp).fillMaxHeight().background(l.color, RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)))
        Box(Modifier.padding(vertical = 12.dp)) { if (who != null) Avatar(who, if (compact) 44.dp else 54.dp, ring = true, showLevel = false) else Icon(l.icon, null, Modifier.size(if (compact) 44.dp else 54.dp), tint = l.color) }
        Column(Modifier.padding(vertical = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(l.kicker.uppercase(), style = Tv.Headline.copy(fontSize = 12.sp, letterSpacing = 1.4.sp), color = l.color)
            Text(c.title, style = Stage.Title.copy(fontSize = if (compact) 20.sp else 24.sp), color = Tv.Primary, maxLines = 1)
            Text((who?.name?.let { "$it · " } ?: "") + c.detail, style = Tv.Meta.copy(fontSize = 14.sp), color = Tv.Secondary, maxLines = 2)
        }
        if (!compact) Icon(l.icon, null, Modifier.size(34.dp), tint = l.color)
    }
}

/**
 * Shows the room's celebrations one at a time, top of the screen, each for about 3.4 s with its own
 * sound. Only the ones that arrive while this screen is open: coming back later does not replay them.
 * On a result screen ([onResult]) the card is compact and sits under the title, where the podium
 * leaves room, so it never covers the headline.
 */
@Composable
fun CelebrationLayer(celebrations: List<Celebration>, players: List<PlayerInfo>, sound: Boolean, onResult: Boolean = false) {
    var lastSeen by remember { mutableStateOf(celebrations.maxOfOrNull { it.id } ?: 0) }
    var current by remember { mutableStateOf<Celebration?>(null) }
    val pending = remember { ArrayDeque<Celebration>() }
    LaunchedEffect(celebrations.lastOrNull()?.id) {
        celebrations.filter { it.id > lastSeen }.forEach { pending.addLast(it); lastSeen = maxOf(lastSeen, it.id) }
        if (current == null) while (pending.isNotEmpty()) {
            val c = pending.removeFirst()
            current = c
            if (sound) Sfx.play(look(c).sfx)
            delay(3_400)
            current = null
            delay(250)
        }
    }
    val c = current
    Box(
        Modifier.fillMaxSize().then(if (onResult) Modifier.padding(start = Tv.Edge, top = 100.dp) else Modifier.padding(top = 70.dp)),
        contentAlignment = if (onResult) Alignment.TopStart else Alignment.TopCenter,
    ) {
        if (c != null) {
            val enter = remember(c.id) { Animatable(0f) }
            LaunchedEffect(c.id) { enter.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)) }
            CelebrationCard(c, players.firstOrNull { it.player.id == c.who }?.player, compact = onResult, modifier = Modifier.graphicsLayer { alpha = enter.value.coerceIn(0f, 1f); translationY = (1f - enter.value) * -80f })
        }
    }
}

