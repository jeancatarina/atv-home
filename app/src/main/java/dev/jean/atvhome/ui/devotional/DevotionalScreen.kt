package dev.jean.atvhome.ui.devotional

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Notifications
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.jean.atvhome.devotional.Block
import dev.jean.atvhome.devotional.Devotional
import dev.jean.atvhome.devotional.DevotionalHost
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
import java.time.format.TextStyle as DateStyle
import java.util.Locale

// One accent, a warm off-white and a quiet grey: the whole board is drawn with these and hairlines
private val Brass = Color(0xFFC9A96B)
private val Ink = Color(0xFFF3EEE4)
private val Mute = Color(0xFFA49D90)
private val Faint = Color(0xFF6F695F)
private val Hairline = Color.White.copy(alpha = 0.09f)
private val Serif = FontFamily.Serif

private val Eyebrow = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 2.4.sp)
private val Label = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.6.sp)

/** The devotional board on the TV: the day, each person's streak and week, the phone's address and the tree. */
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
    val together = remember(version, today) { board.together(today) }
    val tree = remember(version) { Game.tree(board.couplePoints()) }
    val unlocked = remember(version) { board.unlockedRewards() }
    val nextReward = remember(version, today) { board.nextReward(today) }
    // Who was reminded and has not answered: one quiet line under the date
    val calls = remember(version, today, clock) {
        rows.mapNotNull { l -> board.nudgedBy(l.s.person.id, today, clock)?.let { "${it.name} lembrou ${l.s.person.name}" } }
    }
    // Someone checks in on a phone while the board is open: say it, so the other sees it from the sofa
    var seen by remember { mutableStateOf<Set<String>?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(version, today) {
        val now = rows.filter { it.s.doneToday }.associate { it.s.person.id to it.s.person.name }
        val before = seen
        seen = now.keys
        val fresh = if (before == null) emptyList() else now.filterKeys { it !in before }.values.toList()
        if (fresh.isNotEmpty()) { banner = fresh.joinToString(" e ") + (if (fresh.size > 1) " registraram o devocional" else " registrou o devocional"); delay(8_000); banner = null }
    }
    var month by remember { mutableStateOf(true) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestWhenReady() }
    val url = DevotionalHost.url ?: DevotionalHost.lanIp?.let { "http://$it:${dev.jean.atvhome.devotional.DevotionalServer.PORT}/" }
    val qr = remember(url) { url?.let(QrCode::bitmap) }

    Row(Modifier.fillMaxSize().background(LocalAccent.current.screen()).padding(horizontal = Tv.Edge, vertical = 34.dp)) {
        Column(Modifier.weight(1f)) {
            Text("DEVOCIONAL", style = Eyebrow, color = Brass)
            Spacer(Modifier.height(6.dp))
            Text(dateLine(today), style = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 34.sp), color = Ink)
            Spacer(Modifier.height(6.dp))
            val prize = unlocked.firstOrNull()?.let { "Prêmio liberado: ${it.second}" }
            val note = banner ?: calls.firstOrNull() ?: prize
            if (note != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (banner != null) Icons.Rounded.Check else if (calls.isEmpty()) Icons.Rounded.CardGiftcard else Icons.Rounded.Notifications, null, Modifier.size(15.dp), tint = Brass)
                Spacer(Modifier.width(6.dp))
                Text(note, style = Tv.Meta, color = Brass)
            } else Text(subtitle(rows.map { it.s }), style = Tv.Meta, color = Mute)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("30 dias", month, { month = true }, Modifier.focusRequester(first))
                Chip("Semana", !month, { month = false })
            }
            Spacer(Modifier.height(10.dp))
            if (rows.isEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Ninguém entrou ainda. Abra o endereço ao lado no celular e adicione o seu nome.", style = Tv.Headline, color = Mute)
            }
            rows.forEach { l -> Hairline(); PersonRow(l, today, if (month) 30 else 7) }
            if (rows.size > 1) {
                Hairline()
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Bottom) {
                    Stat(together.toString(), if (together == 1) "DIA JUNTOS" else "DIAS JUNTOS", Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(44.dp).background(Hairline))
                    Stat(rows.firstOrNull { it.leader }?.s?.person?.name ?: "Empate", "NA FRENTE ESTA SEMANA", Modifier.weight(1f).padding(start = 24.dp))
                }
            }
        }
        Column(Modifier.padding(start = 40.dp).width(264.dp), horizontalAlignment = Alignment.Start) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.size(152.dp).background(Color.White, RoundedCornerShape(10.dp)).padding(12.dp), contentAlignment = Alignment.Center) {
                val q: Bitmap? = qr
                if (q != null) Image(q.asImageBitmap(), "QR code", filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
                else Text("…", style = Tv.Title, color = Color.Black)
            }
            Spacer(Modifier.height(14.dp))
            Text("REGISTRAR NO CELULAR", style = Label, color = Brass)
            Spacer(Modifier.height(4.dp))
            Text(url ?: "Site fora do ar", style = Tv.Meta, color = Mute)
            Text("No mesmo Wi-Fi da TV", style = Tv.Caption, color = Faint)
            Spacer(Modifier.height(20.dp))
            TreeBlock(tree)
            if (nextReward != null) {
                Spacer(Modifier.height(16.dp))
                Text("PRÓXIMO PRÊMIO", style = Label, color = Brass)
                Spacer(Modifier.height(6.dp))
                Text(nextReward.second, style = TextStyle(fontFamily = Serif, fontSize = 18.sp, lineHeight = 22.sp), color = Ink)
                Spacer(Modifier.height(2.dp))
                Text(if (nextReward.third == 1) "falta 1 dia juntos" else "faltam ${nextReward.third} dias juntos", style = Tv.Caption, color = Faint)
            }
        }
    }
}

private fun dateLine(d: LocalDate): String {
    val l = Locale("pt", "BR")
    return d.dayOfWeek.getDisplayName(DateStyle.FULL, l).replaceFirstChar { it.uppercase() } + ", " + d.dayOfMonth + " de " + d.month.getDisplayName(DateStyle.FULL, l)
}

private fun subtitle(people: List<Standing>): String = when {
    people.isEmpty() -> "Um registro por dia, cada um no seu celular"
    people.all { it.doneToday } -> "Hoje todos registraram"
    else -> "Falta hoje: " + people.filter { !it.doneToday }.joinToString(", ") { it.person.name }
}

@Composable
private fun Hairline() = Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = TextStyle(fontFamily = Serif, fontSize = 26.sp, lineHeight = 28.sp), color = Ink)
        Spacer(Modifier.height(3.dp))
        Text(label, style = Label, color = Faint)
    }
}

private data class Line(val s: Standing, val days: Set<LocalDate>, val points: Int, val week: Int, val leader: Boolean)

@Composable
private fun PersonRow(line: Line, today: LocalDate, span: Int) {
    val s = line.s
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).border(1.dp, Faint, CircleShape), contentAlignment = Alignment.Center) {
                Text(s.person.name.take(1).uppercase(), style = TextStyle(fontFamily = Serif, fontSize = 17.sp), color = Ink)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.person.name, style = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 26.sp), color = Ink)
                Row {
                    Text(if (s.doneToday) "Registrado hoje" else "Ainda não hoje", style = Tv.Meta, color = if (s.doneToday) Brass else Mute)
                    Text("  ·  ${Game.personLevel(line.points).name}  ·  ${if (line.points == 1) "1 ponto" else "${line.points} pontos"}" + (if (s.grace != null) "  ·  dia de graça usado" else ""), style = Tv.Meta, color = Faint)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${s.streak}", style = TextStyle(fontFamily = Serif, fontSize = 38.sp, lineHeight = 38.sp), color = Ink)
                Text(if (s.streak == 1) "DIA" else "DIAS", style = Label, color = Faint)
            }
        }
        Spacer(Modifier.height(14.dp))
        // The days: one small circle each, filled brass when done, the weekday under it on the short view
        Row(Modifier.fillMaxWidth().padding(start = 54.dp), horizontalArrangement = if (span > 7) Arrangement.spacedBy(5.dp) else Arrangement.SpaceBetween) {
            for (back in span - 1 downTo 0) {
                val d = today.minusDays(back.toLong())
                val on = d in line.days
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(if (span > 7) 11.dp else 14.dp).then(if (on) Modifier.background(Brass, CircleShape) else Modifier.border(1.dp, Faint, CircleShape)),
                    )
                    if (span <= 7) {
                        Spacer(Modifier.height(5.dp))
                        Text(d.dayOfWeek.getDisplayName(DateStyle.NARROW, Locale("pt", "BR")).uppercase(), style = Label, color = if (back == 0) Mute else Faint)
                    }
                }
            }
        }
    }
}

/** The couple's tree and how far it is from growing: drawn from the very shapes the phones' image uses. */
@Composable
private fun TreeBlock(tree: Game.Step) {
    Text("ÁRVORE DE VOCÊS", style = Label, color = Brass)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(76.dp)) {
            val k = size.width / 120f
            TreeArt.stage(tree.index).forEach { sh ->
                val c = Color(0xFF000000L or sh.color.toLong())
                when (sh) {
                    is Disc -> drawCircle(c, sh.r * k, Offset(sh.cx * k, sh.cy * k))
                    is Block -> drawRoundRect(c, Offset(sh.x * k, sh.y * k), Size(sh.w * k, sh.h * k), CornerRadius(sh.radius * k))
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(tree.name, style = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 19.sp, lineHeight = 22.sp), color = Ink)
            Spacer(Modifier.height(10.dp))
            val next = tree.nextAt
            val fraction = if (next == null) 1f else ((tree.points - tree.startAt).toFloat() / (next - tree.startAt)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(2.dp).background(Hairline)) { Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Brass)) }
            Spacer(Modifier.height(6.dp))
            Text(if (next == null) "${tree.points} pontos" else "${tree.points} pontos  ·  faltam ${next - tree.points}", style = Tv.Caption, color = Faint)
        }
    }
}
