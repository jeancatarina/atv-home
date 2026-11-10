@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.jean.atvhome.social.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.jean.atvhome.social.game.Player
import dev.jean.atvhome.social.night.Tone
import dev.jean.atvhome.social.profile.Career
import dev.jean.atvhome.social.progress.ProfileViews
import dev.jean.atvhome.social.progress.Progression
import dev.jean.atvhome.social.progress.Records
import dev.jean.atvhome.social.room.RoomSnapshot
import dev.jean.atvhome.ui.components.Chip
import dev.jean.atvhome.ui.components.FocusCard
import dev.jean.atvhome.ui.theme.Tv


// --- Ranking ---------------------------------------------------------------------------------------

private enum class Board(val label: String) { SEASON("Temporada"), ALL("Geral"), RECORDS("Recordes") }

@Composable
fun RankingTab(snap: RoomSnapshot, onOpen: (Career) -> Unit, modifier: Modifier = Modifier) {
    var board by remember { mutableStateOf(Board.SEASON) }
    val here = snap.careers.values.map { it.device }.toSet()
    val seated = remember(snap.careers, snap.players) { seatedByDevice(snap) }
    Column(modifier.fillMaxSize().padding(horizontal = Tv.Edge), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Board.entries.forEach { Chip(it.label, board == it, { board = it }) }
            Spacer(Modifier.weight(1f))
            if (board == Board.SEASON) SeasonTag()
        }
        if (board == Board.RECORDS) {
            val records = snap.records
            if (records.isEmpty()) Empty("Nenhum recorde ainda", "Os recordes aparecem conforme vocês jogam.")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                items(records, key = { "${it.gameId}:${it.key}" }) { r -> RecordRow(r) }
            }
        } else {
            val list = if (board == Board.SEASON) snap.rankingSeason else snap.ranking
            val xpOf: (Career) -> Int = { if (board == Board.SEASON) it.seasonXp else it.xp }
            if (list.isEmpty()) Empty("Ninguém no ranking ainda", "Joguem uma partida com o celular conectado e os nomes aparecem aqui.")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                item(key = "podium") { PodiumCards(list.take(3), here, xpOf, onOpen, seated) }
                items(list.size - minOf(3, list.size), key = { list[it + 3].device }) { j ->
                    val i = j + 3
                    RankRow(i + 1, list[i], personOf(list[i], seated), list[i].device in here, xpOf(list[i])) { onOpen(list[i]) }
                }
            }
        }
    }
}

/** "Temporada de novembro · faltam 22 dias" with the three medals the month gives away. */
@Composable
private fun SeasonTag() {
    val (name, left) = remember { seasonLabel() }
    Row(
        Modifier.background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(50)).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column {
            Text(name, style = Tv.Headline.copy(fontSize = 14.sp), color = Tv.Primary)
            Text(if (left == 1) "Termina hoje · os 3 primeiros ganham medalha" else "Faltam $left dias · os 3 primeiros ganham medalha", style = Tv.Caption.copy(fontSize = 12.sp), color = Tv.Tertiary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) { (1..3).forEach { MedalDisc(it, 26.dp) } }
    }
}

/** The top three as cards: first in the middle and taller, with a crown; each in its medal's light. */
@Composable
private fun PodiumCards(top: List<Career>, here: Set<String>, xpOf: (Career) -> Int, onOpen: (Career) -> Unit, seated: Map<String, Player>) {
    val order = listOfNotNull(top.getOrNull(1)?.let { 2 to it }, top.getOrNull(0)?.let { 1 to it }, top.getOrNull(2)?.let { 3 to it })
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
        order.forEach { (place, c) ->
            val (light, dark) = medalColors(place)
            val prog = Progression.progress(c.xp)
            Box(Modifier.weight(1f)) {
                FocusCard(onClick = { onOpen(c) }, modifier = Modifier.fillMaxWidth().height(if (place == 1) 176.dp else 150.dp), shape = RoundedCornerShape(22.dp), focusedScale = 1.04f) { _ ->
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(light.copy(alpha = 0.30f), dark.copy(alpha = 0.10f), Color(0xFF151517)))))
                    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(light.copy(alpha = 0.22f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(0f, 0f), radius = 520f)))
                    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(contentAlignment = Alignment.TopCenter) {
                            Avatar(personOf(c, seated), if (place == 1) 86.dp else 72.dp, level = c.level, modifier = Modifier.padding(top = if (place == 1) 18.dp else 0.dp))
                            if (place == 1) Crown(32.dp, modifier = Modifier.offset(y = (-6).dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MedalDisc(place, 24.dp)
                                if (c.device in here) LiveDot()
                            }
                            Text(c.name, style = Stage.Title.copy(fontSize = if (place == 1) 24.sp else 21.sp), color = Tv.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(prog.title, style = Tv.Meta.copy(fontSize = 13.sp), color = tierOf(prog.level).light, maxLines = 1)
                            Text("${"%,d".format(xpOf(c)).replace(',', '.')} XP", style = Stage.Title.copy(fontSize = if (place == 1) 26.sp else 22.sp), color = light)
                        }
                    }
                }
            }
        }
    }
}

/** "na sala": a green dot and the words, for people sitting in the room right now. */
@Composable
private fun LiveDot() {
    Row(Modifier.background(Color(0xFF30D158).copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(6.dp).background(Color(0xFF30D158), CircleShape))
        Text("na sala", style = Tv.Caption.copy(fontSize = 11.sp), color = Color(0xFF30D158))
    }
}

/** Fourth place and below: position, face with emblem, name and title, the way to the next level, badges and XP. */
@Composable
private fun RankRow(place: Int, c: Career, p: Player, inRoom: Boolean, xp: Int, onClick: () -> Unit) {
    val prog = Progression.progress(c.xp)
    val tier = tierOf(prog.level)
    FocusCard(onClick = onClick, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(16.dp), focusedScale = 1.02f) { _ ->
        Row(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.04f)).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("$place", style = Stage.Title.copy(fontSize = 20.sp), color = Tv.Tertiary, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
            Avatar(p, 38.dp, level = c.level)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(c.name, style = Tv.Headline.copy(fontSize = 17.sp), color = Tv.Primary, maxLines = 1)
                    if (inRoom) LiveDot()
                }
                Text("${tier.label} · ${prog.title}", style = Tv.Meta.copy(fontSize = 13.sp), color = tier.light.copy(alpha = 0.85f), maxLines = 1)
            }
            Column(Modifier.width(170.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                XpBar(0f, prog.fraction, Modifier.fillMaxWidth(), accent = tier.mid)
                Text(if (prog.span == 0) "Nível máximo" else "Nível ${prog.level + 1} em ${prog.span - prog.into} XP", style = Tv.Caption.copy(fontSize = 11.sp), color = Tv.Tertiary)
            }
            if (c.badges.isNotEmpty()) Row(Modifier.width(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.MilitaryTech, null, Modifier.size(18.dp), tint = Stage.Gold)
                Text("${c.badges.size}", style = Tv.Headline.copy(fontSize = 15.sp), color = Tv.Secondary)
            } else Spacer(Modifier.width(56.dp))
            Text("${"%,d".format(xp).replace(',', '.')} XP", style = Stage.Title.copy(fontSize = 19.sp), color = Tv.Primary, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
        }
    }
}

/** A house record: the game's emblem, what was beaten, who holds it and the number, like a trophy plate. */
@Composable
private fun RecordRow(r: dev.jean.atvhome.social.profile.HouseRecord) {
    val def = Records.def(r.gameId, r.key)
    val st = Styles.of(r.gameId)
    Row(
        Modifier.fillMaxWidth().height(62.dp).background(Brush.horizontalGradient(listOf(st.accent.copy(alpha = 0.20f), Color.White.copy(alpha = 0.04f))), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GameArt(r.gameId, Modifier.size(64.dp, 40.dp), RoundedCornerShape(10.dp), iconSize = 30.dp)
        Column(Modifier.weight(1f)) {
            Text(def?.label ?: r.key, style = Tv.Headline.copy(fontSize = 17.sp), color = Tv.Primary, maxLines = 1)
            Text(r.holder, style = Tv.Meta.copy(fontSize = 13.sp), color = Tv.Secondary, maxLines = 1)
        }
        Icon(Icons.Rounded.EmojiEvents, null, Modifier.size(22.dp), tint = Stage.Gold)
        Text("${r.value}${def?.unit?.let { if (it.isBlank()) "" else " $it" } ?: ""}", style = Stage.Title.copy(fontSize = 22.sp), color = Stage.Gold, modifier = Modifier.width(120.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun Empty(title: String, sub: String) {
    Column(Modifier.fillMaxWidth().padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = Stage.Title, color = Tv.Primary)
        Text(sub, style = Stage.Body.copy(fontSize = 17.sp), color = Tv.Tertiary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp))
    }
}

// --- Profiles --------------------------------------------------------------------------------------

@Composable
fun ProfilesTab(snap: RoomSnapshot, onOpen: (Career) -> Unit, modifier: Modifier = Modifier) {
    val people = (snap.careers.values + snap.ranking).distinctBy { it.device }
    val here = snap.careers.values.map { it.device }.toSet()
    val seated = remember(snap.careers, snap.players) { seatedByDevice(snap) }
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Perfis", style = Stage.Title.copy(fontSize = 22.sp), color = Tv.Primary, modifier = Modifier.padding(horizontal = Tv.Edge))
        if (people.isEmpty()) Empty("Nenhum perfil ainda", "Quem joga com o celular conectado ganha um perfil: nível, conquistas e rivalidades.")
        else LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Tv.Edge), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(people.size, key = { people[it].device }) { i ->
                val c = people[i]; val prog = Progression.progress(c.xp)
                Column(Modifier.width(172.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FocusCard(onClick = { onOpen(c) }, modifier = Modifier.size(172.dp, 196.dp), shape = RoundedCornerShape(20.dp)) { _ ->
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tierOf(prog.level).mid.copy(alpha = 0.30f), Color(0xFF1C1C1E)))))
                        Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            LevelRing(prog, 86.dp, accent = tierOf(prog.level).mid) { Avatar(personOf(c, seated), 62.dp, level = c.level) }
                            Spacer(Modifier.height(10.dp))
                            Text(c.name, style = Tv.Headline.copy(fontSize = 16.sp), color = Tv.Primary, maxLines = 1)
                            Text("Nível ${prog.level} · ${tierOf(prog.level).label}", style = Tv.Meta.copy(fontSize = 13.sp), color = tierOf(prog.level).light)
                            Text(prog.title, style = Tv.Caption.copy(fontSize = 12.sp), color = Tv.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (c.device in here) Text("na sala", style = Tv.Caption.copy(fontSize = 12.sp), color = Color(0xFF30D158), modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

/**
 * One person in full, like a player card: the face in a ring of its metal with the emblem, the title
 * and the way to the next level; six numbers as tiles; the week's challenges; the badge cabinet (locked
 * ones dim with a lock); and the rivalries face to face.
 */
@Composable
fun ProfileDetail(c: Career, onClose: () -> Unit, seated: Map<String, Player> = emptyMap()) {
    BackHandler(onBack = onClose)
    val close = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { close.requestFocus() } }
    val view = remember(c) { ProfileViews.of(c, null, System.currentTimeMillis()) }
    val prog = Progression.progress(c.xp)
    val tier = tierOf(prog.level)
    val me = personOf(c, seated)
    Box(Modifier.fillMaxSize()) {
        Backdrop(tier.mid)
        Row(Modifier.fillMaxSize().padding(horizontal = Tv.Edge, vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
            Column(Modifier.width(250.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LevelRing(prog, 122.dp, accent = tier.mid) { Avatar(me, 92.dp, level = prog.level) }
                Text(c.name, style = Stage.Title, color = Tv.Primary, maxLines = 1)
                Text(
                    "${tier.label.uppercase()} · NÍVEL ${prog.level}", style = Tv.Headline.copy(fontSize = 11.sp, letterSpacing = 1.2.sp), color = Color.Black,
                    modifier = Modifier.background(Brush.linearGradient(listOf(tier.light, tier.mid)), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
                )
                Text(prog.title, style = Tv.Headline.copy(fontSize = 15.sp), color = tier.light)
                XpBar(0f, prog.fraction, Modifier.fillMaxWidth(), accent = tier.mid)
                Text(if (prog.span == 0) "${view.xp} XP · nível máximo" else "Faltam ${prog.span - prog.into} XP para o nível ${prog.level + 1}", style = Tv.Meta.copy(fontSize = 12.sp), color = Tv.Tertiary)
                Spacer(Modifier.height(4.dp))
                val stats = listOf(
                    Triple(Icons.Rounded.EmojiEvents, "${c.wins}", "vitórias"), Triple(Icons.Rounded.SportsEsports, "${c.games}", "partidas"),
                    Triple(Icons.Rounded.NightsStay, "${c.nights}", "noites"), Triple(Icons.Rounded.Star, "${c.chips}", "fichas"),
                    Triple(Icons.Rounded.LocalFireDepartment, "${view.streakWeeks}", "semanas"), Triple(Icons.Rounded.MilitaryTech, "${view.medals.size}", "medalhas"),
                )
                stats.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { pair.forEach { (icon, n, label) -> StatTile(icon, n, label, Modifier.weight(1f)) } }
                }
                Spacer(Modifier.weight(1f))
                TvButton("Voltar", onClose, modifier = Modifier.focusRequester(close))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Section("Desafios da semana", "+${Progression.CHALLENGE_XP} XP cada")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    view.challenges.forEach { ch ->
                        val done = ch.progress >= ch.target
                        val tint = if (done) Color(0xFF30D158) else Color(0xFF64D2FF)
                        Column(
                            Modifier.weight(1f).height(78.dp).background(Brush.linearGradient(listOf(tint.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f))), RoundedCornerShape(14.dp)).padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(ch.text, style = Tv.Headline.copy(fontSize = 13.sp, lineHeight = 16.sp), color = Tv.Primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                XpBar(0f, ch.progress.toFloat() / ch.target, Modifier.weight(1f), accent = tint)
                                Text(if (done) "feito" else "${ch.progress}/${ch.target}", style = Tv.Caption.copy(fontSize = 11.sp), color = if (done) tint else Tv.Tertiary)
                            }
                        }
                    }
                }
                Section("Conquistas", "${view.badges.count { it.at != null }} de ${view.badges.size}")
                BadgeCabinet(view.badges.sortedWith(compareByDescending<dev.jean.atvhome.social.progress.BadgeLine> { it.at != null }.thenByDescending { it.tier }))
                if (view.rivals.isNotEmpty()) {
                    Section("Rivalidades", "confrontos diretos")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        view.rivals.take(3).forEachIndexed { i, r -> RivalCard(me, Player("rival$i", r.name, (i + 3) % 12, (i + 1) % 6), r.wins, r.losses, Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, sub: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = Tv.Headline.copy(fontSize = 12.sp, letterSpacing = 1.4.sp), color = Tv.Secondary)
        Text(sub, style = Tv.Caption.copy(fontSize = 12.sp), color = Tv.Tertiary)
    }
}

@Composable
private fun StatTile(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, modifier: Modifier) {
    Row(modifier.height(44.dp).background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = Stage.Gold)
        Column {
            Text(value, style = Tv.Headline.copy(fontSize = 15.sp, lineHeight = 17.sp), color = Tv.Primary)
            Text(label, style = Tv.Caption.copy(fontSize = 10.sp, lineHeight = 12.sp), color = Tv.Tertiary, maxLines = 1)
        }
    }
}

/** Badges as medallions in their metal, six to a shelf, two shelves; locked ones are dark with a lock. */
@Composable
private fun BadgeCabinet(badges: List<dev.jean.atvhome.social.progress.BadgeLine>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        badges.take(12).chunked(6).forEach { shelf ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                shelf.forEach { b ->
                    val on = b.at != null
                    val metal = tierColor(b.tier)
                    Column(Modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(
                            Modifier.size(46.dp).background(
                                if (on) Brush.linearGradient(listOf(metal, androidx.compose.ui.graphics.lerp(metal, Color.Black, 0.55f))) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.04f))),
                                CircleShape,
                            ),
                            contentAlignment = Alignment.Center,
                        ) { Icon(if (on) Icons.Rounded.Star else Icons.Rounded.Lock, null, Modifier.size(22.dp), tint = if (on) Color.White else Tv.Tertiary) }
                        Text(b.name, style = Tv.Caption.copy(fontSize = 11.sp, lineHeight = 13.sp), color = if (on) Tv.Primary else Tv.Tertiary, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Two faces and the head-to-head score, the leader's side lit. */
@Composable
private fun RivalCard(me: Player, them: Player, wins: Int, losses: Int, modifier: Modifier) {
    Row(modifier.height(52.dp).background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Avatar(me, 30.dp, showLevel = false)
        Text("$wins", style = Stage.Title.copy(fontSize = 20.sp), color = if (wins >= losses) Stage.Gold else Tv.Secondary)
        Text("×", style = Tv.Meta, color = Tv.Tertiary)
        Text("$losses", style = Stage.Title.copy(fontSize = 20.sp), color = if (losses > wins) Stage.Gold else Tv.Secondary)
        Avatar(them, 30.dp, showLevel = false)
        Text(them.name, style = Tv.Headline.copy(fontSize = 13.sp), color = Tv.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// --- The night and the settings ----------------------------------------------------------------------

@Composable
fun NightTab(snap: RoomSnapshot, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val rows = snap.night.entries.sortedByDescending { it.value }.mapNotNull { (id, n) -> snap.players.firstOrNull { it.player.id == id }?.player?.let { it to n } }
    Row(modifier.fillMaxSize().padding(horizontal = Tv.Edge), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Placar da noite", style = Stage.Title.copy(fontSize = 22.sp), color = Tv.Primary)
            if (rows.isEmpty()) Text("Ninguém pontuou ainda hoje. Bora jogar uma partida!", style = Stage.Body.copy(fontSize = 17.sp), color = Tv.Tertiary)
            rows.take(8).forEachIndexed { i, (p, n) ->
                Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${i + 1}º", style = Stage.Title.copy(fontSize = 18.sp), color = if (i == 0) Stage.Gold else Tv.Secondary, modifier = Modifier.width(36.dp))
                    Avatar(p, 30.dp)
                    Text(p.name, style = Tv.Headline.copy(fontSize = 16.sp), color = Tv.Primary, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("★ $n", style = Stage.Title.copy(fontSize = 18.sp), color = Stage.Gold)
                }
            }
        }
        Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Encerrar a noite", style = Stage.Title.copy(fontSize = 22.sp), color = Tv.Primary)
            Text("A cerimônia dá um troféu pra cada um, com os momentos mais marcantes da noite.", style = Stage.Body.copy(fontSize = 16.sp), color = Tv.Secondary)
            Text("${snap.events.size} momentos guardados", style = Tv.Meta.copy(fontSize = 14.sp), color = Tv.Tertiary)
            Spacer(Modifier.height(6.dp))
            TvButton("Encerrar a noite", onClose, icon = Icons.Rounded.EmojiEvents, accent = Stage.Gold)
        }
    }
}

@Composable
fun SettingsTab(
    tone: Tone?, onTone: (Tone?) -> Unit, sound: Boolean, onSound: () -> Unit, onNewRoom: () -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(horizontal = Tv.Edge), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Ajustes", style = Stage.Title.copy(fontSize = 22.sp), color = Tv.Primary)
        Setting("Apresentador", "Comenta o fim de cada partida e abre a noite") {
            listOf<Pair<String, Tone?>>("Família" to Tone.FAMILY, "Provocador" to Tone.TEASING, "Mudo" to null).forEach { (label, t) -> Chip(label, tone == t, { onTone(t) }) }
        }
        Setting("Sons da TV", "Efeitos de entrada, início, vitória e erro. O celular nunca faz barulho") {
            Chip("Ligados", sound, { if (!sound) onSound() }); Chip("Desligados", !sound, { if (sound) onSound() })
        }
        Setting("Nova sala", "Troca o código e tira todo mundo da sala (os pontos ficam guardados)") {
            TvButton("Nova sala", {}, onLongClick = onNewRoom)
            Text("Segure OK pra confirmar", style = Tv.Caption.copy(fontSize = 12.sp), color = Tv.Tertiary)
        }
    }
}

@Composable
private fun Setting(title: String, sub: String, controls: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Tv.Headline.copy(fontSize = 17.sp), color = Tv.Primary)
            Text(sub, style = Tv.Meta.copy(fontSize = 13.sp), color = Tv.Tertiary, maxLines = 2)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { controls() }
    }
}
