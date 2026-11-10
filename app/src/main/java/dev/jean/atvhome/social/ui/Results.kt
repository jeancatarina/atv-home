package dev.jean.atvhome.social.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.jean.atvhome.social.game.Outcome
import dev.jean.atvhome.social.game.Player
import dev.jean.atvhome.social.progress.Gain
import dev.jean.atvhome.social.progress.Progression
import dev.jean.atvhome.social.room.GameSnap
import dev.jean.atvhome.social.room.RoomSnapshot
import dev.jean.atvhome.ui.theme.Tv

/**
 * The end of a game: the podium on the left, the story on the right (who was who, what the answer
 * was, what the host says) and, for everyone with a known phone, the XP just earned with a bar that
 * fills and a "Nível N" when it overflows. Then the two ways on.
 */
@Composable
fun ResultScreen(
    snap: RoomSnapshot,
    game: GameSnap,
    over: Outcome,
    line: String?,
    onAgain: () -> Unit,
    onLobby: () -> Unit,
    details: @Composable () -> Unit,
    focus: FocusRequester,
) {
    val st = Styles.of(game.info.id)
    val ranked = over.chips.entries.sortedByDescending { it.value }.mapNotNull { (id, n) -> snap.players.firstOrNull { it.player.id == id }?.player?.let { it to n } }
    Row(Modifier.fillMaxSize().padding(horizontal = Tv.Edge, vertical = 34.dp), horizontalArrangement = Arrangement.spacedBy(34.dp)) {
        Column(Modifier.width(330.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(game.info.name.uppercase(), style = Tv.Headline.copy(fontSize = 13.sp, letterSpacing = 1.2.sp), color = st.accent)
            Text(if (over.coopWon == true) "Vitória do grupo" else if (over.coopWon == false) "Não foi dessa vez" else "Resultado", style = Stage.Title, color = Tv.Primary)
            Podium(ranked.take(3), st.accent, Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton("Mais uma", onAgain, icon = Icons.Rounded.Replay, accent = st.accent, modifier = Modifier.focusRequester(focus))
                TvButton("Trocar", onLobby, icon = Icons.Rounded.SwapHoriz)
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
            Text(over.headline, style = Stage.Title.copy(fontSize = 26.sp, lineHeight = 30.sp), color = Tv.Primary, maxLines = 3)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { details() }
            if (line != null) Text("“$line”", style = Stage.Body.copy(fontSize = 18.sp), color = Stage.Gold, maxLines = 2)
            Spacer(Modifier.height(10.dp))
            val gains = snap.players.filter { snap.gains[it.player.id] != null }
            if (gains.isNotEmpty()) {
                Text("EXPERIÊNCIA", style = Tv.Headline.copy(fontSize = 12.sp, letterSpacing = 1.4.sp), color = Tv.Tertiary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    gains.sortedByDescending { snap.gains[it.player.id]?.xp ?: 0 }.take(6).forEach { XpRow(it.player, snap.gains.getValue(it.player.id)) }
                }
            }
        }
    }
}

/** Three steps in gold, silver and bronze: second, first, third, tallest in the middle, a crown on the winner. */
@Composable
fun Podium(top: List<Pair<Player, Int>>, accent: Color, modifier: Modifier = Modifier) {
    val order = listOfNotNull(top.getOrNull(1)?.let { 2 to it }, top.getOrNull(0)?.let { 1 to it }, top.getOrNull(2)?.let { 3 to it })
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
        if (top.isEmpty()) Text("Ninguém pontuou", style = Stage.Body, color = Tv.Tertiary)
        order.forEach { (place, entry) ->
            val (p, n) = entry
            val (light, dark) = medalColors(place)
            val h = when (place) { 1 -> 118.dp; 2 -> 86.dp; else -> 64.dp }
            Column(Modifier.width(100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (place == 1) Crown(30.dp)
                Avatar(p, if (place == 1) 66.dp else 52.dp, ring = place == 1)
                Spacer(Modifier.height(6.dp))
                Text(p.name, style = Tv.Headline.copy(fontSize = 14.sp), color = Tv.Primary, maxLines = 1)
                Text("★ $n", style = Tv.Meta.copy(fontSize = 14.sp), color = light)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().height(h)
                        .background(Brush.verticalGradient(listOf(light, dark, dark.copy(alpha = 0.25f))), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)))
                    Text("$place", style = Stage.Hero.copy(fontSize = 36.sp), color = Color.Black.copy(alpha = 0.45f), modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

/**
 * One person's XP: the face with its emblem, a bar in the level's metal from where it was to where it
 * is, "+85 XP" counting up, and a bright "Nível 15" pill when it rose.
 */
@Composable
fun XpRow(p: Player, g: Gain) {
    val before = Progression.progress(g.before); val after = Progression.progress(g.after)
    val rose = after.level > before.level
    val tier = tierOf(after.level)
    val shown = remember(g) { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(g) { shown.animateTo(g.xp.toFloat(), androidx.compose.animation.core.tween(1100)) }
    Row(
        Modifier.fillMaxWidth().background(if (rose) tier.mid.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(p, 32.dp, level = after.level)
        Text(p.name, style = Tv.Headline.copy(fontSize = 15.sp), color = Tv.Primary, modifier = Modifier.width(92.dp), maxLines = 1)
        XpBar(if (rose) 0f else before.fraction, after.fraction, Modifier.weight(1f), accent = tier.mid)
        Text("+${shown.value.toInt()} XP", style = Tv.Headline.copy(fontSize = 15.sp), color = Stage.Gold, modifier = Modifier.width(76.dp), textAlign = TextAlign.End)
        Box(Modifier.width(92.dp), contentAlignment = Alignment.CenterStart) {
            if (rose) Text(
                "NÍVEL ${after.level}", style = Tv.Headline.copy(fontSize = 12.sp, letterSpacing = 1.sp), color = Color.Black,
                modifier = Modifier.background(Brush.linearGradient(listOf(tier.light, tier.mid)), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
            ) else Text("Nível ${after.level}", style = Tv.Meta.copy(fontSize = 13.sp), color = Tv.Tertiary)
        }
    }
}
