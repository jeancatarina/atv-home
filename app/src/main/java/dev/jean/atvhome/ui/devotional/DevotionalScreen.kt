package dev.jean.atvhome.ui.devotional

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.tv.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.jean.atvhome.devotional.Devotional
import dev.jean.atvhome.devotional.DevotionalHost
import dev.jean.atvhome.devotional.Block
import dev.jean.atvhome.devotional.Disc
import dev.jean.atvhome.devotional.Game
import dev.jean.atvhome.devotional.Standing
import dev.jean.atvhome.devotional.TreeArt
import dev.jean.atvhome.ui.components.Chip
import dev.jean.atvhome.ui.components.QrCode
import dev.jean.atvhome.ui.components.requestWhenReady
import dev.jean.atvhome.ui.theme.LocalAccent
import dev.jean.atvhome.ui.theme.Tv
import dev.jean.atvhome.ui.theme.screen
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val Done = Color(0xFF4CC27F)
private val Flame = Color(0xFFFF9F43)
private val Gold = Color(0xFFE2B53C)
private val Silver = Color(0xFFC4C8CF)
private val Bronze = Color(0xFFC58355)

/** The devotional board on the TV: the ranking, the days each of us did it and the phone's address. */
@Composable
fun DevotionalScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val board = remember { DevotionalHost.board(context) }
    val version by DevotionalHost.changes.collectAsState()
    var today by remember { mutableStateOf(LocalDate.now()) }
    // A new day starts at midnight even if the screen stays open
    LaunchedEffect(Unit) { while (true) { delay(60_000); today = LocalDate.now() } }
    var clock by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); clock = System.currentTimeMillis() } }
    val leader = remember(version, today) { board.weekLeader(today)?.id }
    val rows = remember(version, today) {
        board.standings(today).map { Line(it, board.daysOf(it.person.id), board.points(it.person.id), board.weekCount(it.person.id, today), it.person.id == leader) }
    }
    val tree = remember(version) { Game.tree(board.couplePoints()) }
    // Who was called and has not answered: a short line under the title, for whoever is on the sofa
    val calls = remember(version, today, clock) {
        rows.mapNotNull { l -> board.nudgedBy(l.s.person.id, today, clock)?.let { "${it.name} chamou ${l.s.person.name} para o devocional" } }
    }
    val together = remember(version, today) { board.together(today) }
    // Someone checks in on a phone while the board is open: say it, so the other sees it from the sofa
    var seen by remember { mutableStateOf<Set<String>?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(version, today) {
        val now = rows.filter { it.s.doneToday }.associate { it.s.person.id to it.s.person.name }
        val before = seen
        seen = now.keys
        val fresh = if (before == null) emptyList() else now.filterKeys { it !in before }.values.toList()
        if (fresh.isNotEmpty()) { banner = fresh.joinToString(" e ") + " fez o devocional"; delay(8_000); banner = null }
    }
    var month by remember { mutableStateOf(true) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestWhenReady() }
    val url = DevotionalHost.url ?: DevotionalHost.lanIp?.let { "http://$it:${dev.jean.atvhome.devotional.DevotionalServer.PORT}/" }
    val qr = remember(url) { url?.let(QrCode::bitmap) }

    Row(Modifier.fillMaxSize().background(LocalAccent.current.screen()).padding(horizontal = Tv.Edge, vertical = 34.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Devocional", style = Tv.Title, color = Tv.Primary)
            Spacer(Modifier.height(3.dp))
            val shown = banner
            if (shown != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Celebration, null, Modifier.size(18.dp), tint = Done)
                Spacer(Modifier.width(6.dp))
                Text(shown, style = Tv.Headline, color = Done)
            }
            else if (calls.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Notifications, null, Modifier.size(18.dp), tint = Gold)
                Spacer(Modifier.width(6.dp))
                Text(calls.first(), style = Tv.Headline, color = Gold)
            }
            else Text(subtitle(rows.map { it.s }), style = Tv.Meta, color = Tv.Tertiary)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("Últimos 30 dias", month, { month = true }, Modifier.focusRequester(first))
                Chip("Esta semana", !month, { month = false })
            }
            Spacer(Modifier.height(20.dp))
            if (rows.isEmpty()) {
                Text("Ninguém entrou ainda. Abra o endereço ao lado no celular e adicione o seu nome.", style = Tv.Headline, color = Tv.Secondary)
            }
            if (together > 0) {
                Row(
                    Modifier.fillMaxWidth().background(Done.copy(alpha = 0.16f), RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Favorite, null, Modifier.size(26.dp), tint = Done)
                    Spacer(Modifier.width(12.dp))
                    Text("Vocês juntos: ${if (together == 1) "1 dia" else "$together dias"} seguidos", style = Tv.Headline, color = Tv.Primary)
                }
                Spacer(Modifier.height(12.dp))
            }
            rows.forEachIndexed { i, l -> PersonCard(i, l, today, if (month) 30 else 7); Spacer(Modifier.height(12.dp)) }
        }
        Column(Modifier.padding(start = 28.dp).width(308.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(30.dp))
            Box(Modifier.size(220.dp).background(Color.White, RoundedCornerShape(22.dp)).padding(14.dp), contentAlignment = Alignment.Center) {
                val q: Bitmap? = qr
                if (q != null) Image(q.asImageBitmap(), "QR code", filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
                else Text("…", style = Tv.Title, color = Color.Black)
            }
            Spacer(Modifier.height(14.dp))
            Text("Dar check-in no celular", style = Tv.Headline, color = Tv.Primary)
            Text(url ?: "Site fora do ar", style = Tv.Meta, color = Tv.Tertiary)
            Text("O celular precisa estar no mesmo Wi-Fi da TV", style = Tv.Caption, color = Tv.Tertiary)
            Spacer(Modifier.height(22.dp))
            TreeCard(tree)
        }
    }
}

private fun subtitle(people: List<Standing>): String = when {
    people.isEmpty() -> "Um check-in por dia, cada um no seu celular"
    people.all { it.doneToday } -> "Hoje todo mundo já fez. Que bom!"
    else -> "Falta hoje: " + people.filter { !it.doneToday }.joinToString(", ") { it.person.name }
}

@Composable
private fun PersonCard(index: Int, line: Line, today: LocalDate, span: Int) {
    val s = line.s
    val days = line.days
    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val ring = listOf(Gold, Silver).getOrNull(index)
            val (c1, c2) = AVATARS[index % AVATARS.size]
            Box(
                Modifier.size(44.dp)
                    .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier)
                    .padding(if (ring != null) 4.dp else 0.dp)
                    .background(Brush.linearGradient(listOf(c1, c2)), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(s.person.name.take(1).uppercase(), style = Tv.Title, color = Color(0xFF1A1208)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.person.name, style = Tv.Title, color = Tv.Primary)
                    if (line.leader) { Spacer(Modifier.width(8.dp)); Icon(Icons.Rounded.EmojiEvents, null, Modifier.size(22.dp), tint = Gold) }
                }
                Text("${Game.personLevel(line.points).name} · ${if (line.points == 1) "1 ponto" else "${line.points} pontos"}", style = Tv.Caption, color = Tv.Tertiary)
            }
            if (s.doneToday) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = Done)
            if (s.doneToday) Spacer(Modifier.width(4.dp))
            Text(if (s.doneToday) "hoje" else "ainda não hoje", style = Tv.Meta, color = if (s.doneToday) Done else Tv.Tertiary)
            Spacer(Modifier.width(16.dp))
            Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(26.dp), tint = Flame)
            Spacer(Modifier.width(2.dp))
            Text("${s.streak}", style = Tv.Title, color = Tv.Primary)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(if (span > 7) 4.dp else 8.dp)) {
            for (back in span - 1 downTo 0) {
                val d = today.minusDays(back.toLong())
                val on = d in days
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(if (span > 7) 14.dp else 34.dp).background(if (on) Done else Color.White.copy(alpha = 0.14f), RoundedCornerShape(if (span > 7) 4.dp else 8.dp)))
                    if (span <= 7) Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("pt", "BR")), style = Tv.Caption, color = Tv.Tertiary)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Esta semana ${if (line.week == 1) "1 dia" else "${line.week} dias"} · melhor sequência ${s.best} · ${s.total} no total", style = Tv.Caption, color = Tv.Tertiary, modifier = Modifier.weight(1f))
            // Milestones: lit gold once the best streak reaches them
            listOf(7, 30, 100, 365).forEach { n ->
                val on = s.best >= n
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.background(if (on) Gold else Color.White.copy(alpha = 0.08f), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 3.dp),
                ) { Text("$n", style = Tv.Caption, color = if (on) Color(0xFF2A1D00) else Tv.Tertiary) }
            }
        }
    }
}

private val AVATARS = listOf(
    Color(0xFFF3CF72) to Color(0xFFE08F3C), Color(0xFF9FD9C3) to Color(0xFF4AA88A),
    Color(0xFFB9B3FF) to Color(0xFF7A6FE0), Color(0xFFFFB3B3) to Color(0xFFE06C7C),
)

private data class Line(val s: Standing, val days: Set<LocalDate>, val points: Int, val week: Int, val leader: Boolean)

/** The couple's tree and how far it is from growing: drawn from the very shapes the phones' image uses. */
@Composable
private fun TreeCard(tree: Game.Step) {
    Row(
        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f))), RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(88.dp)) {
            val k = size.width / 120f
            TreeArt.stage(tree.index).forEach { sh ->
                val c = Color(0xFF000000L or sh.color.toLong())
                when (sh) {
                    is Disc -> drawCircle(c, sh.r * k, Offset(sh.cx * k, sh.cy * k))
                    is Block -> drawRoundRect(c, Offset(sh.x * k, sh.y * k), Size(sh.w * k, sh.h * k), CornerRadius(sh.radius * k))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Árvore de vocês", style = Tv.Caption, color = Tv.Tertiary)
            Text(tree.name, style = Tv.Headline, color = Tv.Primary)
            Spacer(Modifier.height(6.dp))
            val next = tree.nextAt
            val fraction = if (next == null) 1f else ((tree.points - tree.startAt).toFloat() / (next - tree.startAt)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(3.dp))) {
                Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(Done, RoundedCornerShape(3.dp)))
            }
            Spacer(Modifier.height(4.dp))
            Text(if (next == null) "No auge" else "Faltam ${next - tree.points} pontos", style = Tv.Caption, color = Tv.Tertiary)
        }
    }
}
