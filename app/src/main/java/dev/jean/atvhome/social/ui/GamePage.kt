package dev.jean.atvhome.social.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.jean.atvhome.social.game.Game
import dev.jean.atvhome.social.progress.Records
import dev.jean.atvhome.social.room.RoomSnapshot
import dev.jean.atvhome.ui.components.Chip
import dev.jean.atvhome.ui.theme.Tv

/**
 * A game's own page before it starts: its colour as the whole mood, what it is about, three steps
 * to learn it, the settings, the house record to beat, and the way in. Replaces the old plain sheet.
 */
@Composable
fun GamePage(
    game: Game<*>,
    snap: RoomSnapshot,
    busy: Boolean,
    onStart: (Map<String, Int>) -> Unit,
    onClose: () -> Unit,
    toast: (String) -> Unit = {},
) {
    BackHandler(onBack = onClose)
    val info = game.info
    val st = Styles.of(info.id)
    val picks = remember { mutableStateMapOf<String, Int>().apply { info.settings.forEach { put(it.key, it.default) } } }
    val start = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { start.requestFocus() } }
    val seated = snap.players.count { !it.spectator }
    val record = snap.records.firstOrNull { it.gameId == info.id }

    Box(Modifier.fillMaxSize()) {
        Backdrop(st.accent)
        Row(Modifier.fillMaxSize().padding(horizontal = Tv.Edge, vertical = 28.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(info.category.label.uppercase(), style = Tv.Headline.copy(fontSize = 13.sp, letterSpacing = 1.2.sp), color = st.accent)
                Text(info.name, style = Stage.Hero.copy(fontSize = 36.sp, lineHeight = 40.sp), color = Tv.Primary, maxLines = 2)
                Text(info.tagline, style = Stage.Body.copy(fontSize = 18.sp), color = Tv.Secondary, maxLines = 2, modifier = Modifier.widthIn(max = 520.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text(if (info.minPlayers == info.maxPlayers) "${info.minPlayers} pessoas" else "${info.minPlayers}–${info.maxPlayers} pessoas", style = Tv.Meta.copy(fontSize = 15.sp), color = Tv.Secondary)
                    Text("${info.minutes} min", style = Tv.Meta.copy(fontSize = 15.sp), color = Tv.Secondary)
                    DifficultyDots(info.difficulty, Tv.Secondary)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    info.howTo.forEachIndexed { i, step -> HowCard(i + 1, step, st.accent) }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    info.settings.forEach { setting -> Segmented(setting.label, setting.options, picks[setting.key] ?: 0, { picks[setting.key] = it }) }
                }
                if (record != null) {
                    val def = Records.def(info.id, record.key)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.EmojiEvents, null, Modifier.size(20.dp), tint = Stage.Gold)
                        Text("Recorde da casa: ${record.value}${def?.unit?.let { if (it.isBlank()) "" else " $it" } ?: ""} · ${record.holder}", style = Tv.Meta.copy(fontSize = 15.sp), color = Stage.Gold)
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvButton(if (busy) "Buscando filmes…" else "Jogar", { onStart(picks.toMap()) }, icon = Icons.Rounded.PlayArrow, accent = st.accent, modifier = Modifier.focusRequester(start))
                    TvButton("Voltar", onClose)
                    if (!info.fits(seated)) Text("Agora há $seated na sala. Chama mais gente pelo código.", style = Tv.Meta.copy(fontSize = 14.sp), color = Color(0xFFFF9F0A))
                }
            }
            Spacer(Modifier.width(30.dp))
            GameArt(info.id, Modifier.size(220.dp, 220.dp), RoundedCornerShape(26.dp), iconSize = 170.dp)
        }
    }
}

@Composable
private fun HowCard(n: Int, text: String, accent: Color) {
    Column(
        Modifier.width(188.dp).height(132.dp).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(24.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) { Text("$n", style = Tv.Headline.copy(fontSize = 14.sp), color = Color.Black) }
        Text(text, style = Tv.Body.copy(fontSize = 13.sp, lineHeight = 16.sp), color = Tv.Secondary, maxLines = 6)
    }
}
