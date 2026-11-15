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
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import dev.jean.atvhome.devotional.Verse
import dev.jean.atvhome.devotional.Verses
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
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
import dev.jean.atvhome.devotional.Standing
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
    val rows = remember(version, today) { board.standings(today).map { it to board.daysOf(it.person.id) } }
    val together = remember(version, today) { board.together(today) }
    // Someone checks in on a phone while the board is open: say it, so the other sees it from the sofa
    var seen by remember { mutableStateOf<Set<String>?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(version, today) {
        val now = rows.filter { it.first.doneToday }.associate { it.first.person.id to it.first.person.name }
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
            else Text(subtitle(rows.map { it.first }), style = Tv.Meta, color = Tv.Tertiary)
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
            rows.forEachIndexed { i, (s, days) -> PersonCard(i, s, days, today, if (month) 30 else 7); Spacer(Modifier.height(12.dp)) }
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
            VerseCard(Verses.of(today))
        }
    }
}

private fun subtitle(people: List<Standing>): String = when {
    people.isEmpty() -> "Um check-in por dia, cada um no seu celular"
    people.all { it.doneToday } -> "Hoje todo mundo já fez. Que bom!"
    else -> "Falta hoje: " + people.filter { !it.doneToday }.joinToString(", ") { it.person.name }
}

@Composable
private fun PersonCard(index: Int, s: Standing, days: Set<LocalDate>, today: LocalDate, span: Int) {
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
            Text(s.person.name, style = Tv.Title, color = Tv.Primary, modifier = Modifier.weight(1f))
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
            Text("Melhor sequência ${s.best} · ${s.last30} dos últimos 30 dias · ${s.total} no total", style = Tv.Caption, color = Tv.Tertiary, modifier = Modifier.weight(1f))
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

/** The day's verse: the same one the phones show. */
@Composable
private fun VerseCard(v: Verse) {
    Column(
        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f))), RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Icon(Icons.Rounded.FormatQuote, null, Modifier.size(26.dp), tint = Gold)
        Spacer(Modifier.height(4.dp))
        Text(v.text, style = Tv.Headline.copy(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp), color = Tv.Primary)
        Spacer(Modifier.height(8.dp))
        Text(v.ref.uppercase(), style = Tv.Caption.copy(letterSpacing = 1.5.sp), color = Gold)
    }
}

private val AVATARS = listOf(
    Color(0xFFF3CF72) to Color(0xFFE08F3C), Color(0xFF9FD9C3) to Color(0xFF4AA88A),
    Color(0xFFB9B3FF) to Color(0xFF7A6FE0), Color(0xFFFFB3B3) to Color(0xFFE06C7C),
)
