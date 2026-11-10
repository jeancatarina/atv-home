package dev.jean.atvhome.social.ui

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.jean.atvhome.social.game.Category
import dev.jean.atvhome.social.game.Game
import dev.jean.atvhome.social.game.GameInfo
import dev.jean.atvhome.social.games.GameCatalog
import dev.jean.atvhome.social.kit.Suggest
import dev.jean.atvhome.social.progress.Challenges
import dev.jean.atvhome.social.progress.Progression
import dev.jean.atvhome.social.room.RoomSnapshot
import dev.jean.atvhome.ui.components.FocusCard
import dev.jean.atvhome.ui.components.QrCode
import dev.jean.atvhome.ui.components.RowSpacing
import dev.jean.atvhome.ui.theme.Tv

enum class Tab(val label: String) { PLAY("Jogar"), RANKING("Ranking"), PROFILES("Perfis"), NIGHT("Noite"), SETTINGS("Ajustes") }

/**
 * The game to suggest now: one that suits this many people, and that the people in the room have
 * played least (never, before anything else), never the one just played tonight.
 */
fun recommend(snap: RoomSnapshot, games: List<Game<*>> = GameCatalog.all, now: Long = System.currentTimeMillis()): Game<*> {
    val seated = snap.players.count { !it.spectator }
    val pool = games.filter { it.info.ideal(seated) }.ifEmpty { games.filter { it.info.fits(seated) } }.ifEmpty { games }
    return Suggest.order(pool, snap.careers.values, snap.events.map { it.gameId }.toSet(), now, kotlin.random.Random(seated * 31L + snap.events.size)).first()
}

/** The top bar: the tabs on the left, who is in the room on the right. */
@Composable
fun TopBar(tab: Tab, onTab: (Tab) -> Unit, snap: RoomSnapshot, onInvite: () -> Unit, tabFocus: FocusRequester? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Tv.Edge, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Social", style = Stage.Title.copy(fontSize = 26.sp), color = Tv.Primary)
        Spacer(Modifier.width(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Tab.entries.forEach { t -> TabPill(t.label, tab == t, { onTab(t) }, if (tab == t && tabFocus != null) Modifier.focusRequester(tabFocus) else Modifier) }
        }
        Spacer(Modifier.weight(1f))
        val seated = snap.players.filter { !it.spectator }
        TvButton(if (seated.isEmpty()) "Entrar pelo celular" else "${seated.size} na sala", onInvite, icon = if (seated.isEmpty()) Icons.Rounded.QrCode2 else Icons.Rounded.Group)
    }
}

@Composable
fun PlayTab(
    snap: RoomSnapshot,
    baseUrl: String?,
    onOpen: (Game<*>) -> Unit,
    onSurprise: () -> Unit,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester? = null,
    onRanking: () -> Unit = {},
) {
    val seated = snap.players.count { !it.spectator }
    val featured = remember(seated, snap.events) { recommend(snap) }
    LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(key = "hero") {
            if (seated == 0) JoinHero(snap, baseUrl)
            else Hero(featured, seated, snap.careers, onPlay = { onOpen(featured) }, onSurprise = onSurprise, firstFocus = firstFocus)
        }
        if (seated > 0) item(key = "week") { WeekRow(snap, onRanking) }
        val forNow = if (seated >= 2) GameCatalog.all.filter { it.info.ideal(seated) } else emptyList()
        val rows: List<Pair<String, List<Game<*>>>> = (if (forNow.isNotEmpty()) listOf("Pra $seated pessoas agora" to forNow) else emptyList()) +
            GameCatalog.all.groupBy { it.info.category }.map { (c, g) -> c.label to g }
        items(rows, key = { it.first }) { (title, games) ->
            Column {
                Text(title, style = Tv.Headline.copy(fontSize = 17.sp), color = Tv.Secondary, modifier = Modifier.padding(start = Tv.Edge, bottom = 8.dp))
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Tv.Edge), horizontalArrangement = RowSpacing) {
                    items(games, key = { it.info.id }) { g -> GameCard(g.info, seated, onClick = { onOpen(g) }) }
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

/** The featured game: its name, what it is about, how long and for how many, and the way in. */
@Composable
private fun Hero(game: Game<*>, seated: Int, careers: Map<String, dev.jean.atvhome.social.profile.Career>, onPlay: () -> Unit, onSurprise: () -> Unit, firstFocus: FocusRequester?) {
    val info = game.info
    val st = Styles.of(info.id)
    Row(Modifier.fillMaxWidth().padding(horizontal = Tv.Edge), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val fresh = Suggest.newcomers(info.id, careers.values)
            Text(
                when {
                    careers.isNotEmpty() && fresh == careers.size -> "Novidade pra todo mundo aqui"
                    fresh > 0 -> "$fresh de ${careers.size} nunca jogaram"
                    info.ideal(seated) -> "Ideal pra $seated pessoas"
                    else -> "Sugestão da noite"
                },
                style = Tv.Headline.copy(fontSize = 15.sp), color = st.accent,
            )
            Text(info.name, style = Stage.Hero.copy(fontSize = 38.sp, lineHeight = 42.sp), color = Tv.Primary, maxLines = 2)
            Text(info.tagline, style = Stage.Body.copy(fontSize = 18.sp), color = Tv.Secondary, maxLines = 2, modifier = Modifier.widthIn(max = 460.dp))
            Spacer(Modifier.height(2.dp))
            Meta(info)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton("Jogar", onPlay, icon = Icons.Rounded.PlayArrow, accent = st.accent, modifier = if (firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier)
                TvButton("Surpreenda-me", onSurprise, icon = Icons.Rounded.Casino)
            }
        }
        GameArt(info.id, Modifier.size(320.dp, 180.dp), RoundedCornerShape(22.dp), iconSize = 150.dp)
    }
}

@Composable
private fun Meta(info: GameInfo) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        MetaItem(Icons.Rounded.Group, if (info.minPlayers == info.maxPlayers) "${info.minPlayers} pessoas" else "${info.minPlayers}–${info.maxPlayers} pessoas")
        MetaItem(Icons.Rounded.AccessTime, "${info.minutes} min")
        DifficultyDots(info.difficulty, Tv.Secondary)
    }
}

@Composable
private fun MetaItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = Tv.Secondary)
        Text(text, style = Tv.Meta.copy(fontSize = 15.sp), color = Tv.Secondary)
    }
}

/** An empty room: the code is the whole screen. */
@Composable
private fun JoinHero(snap: RoomSnapshot, baseUrl: String?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Tv.Edge), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sala aberta", style = Tv.Headline.copy(fontSize = 15.sp), color = Stage.Gold)
            Text("Entra pelo celular", style = Stage.Hero.copy(fontSize = 38.sp, lineHeight = 42.sp), color = Tv.Primary)
            Text("Sem instalar nada: aponta a câmera pro código e escolhe um nome.", style = Stage.Body.copy(fontSize = 18.sp), color = Tv.Secondary, modifier = Modifier.widthIn(max = 440.dp))
            Spacer(Modifier.height(6.dp))
            listOf("1  Aponta a câmera do celular", "2  Escolhe um nome e tira uma selfie", "3  Joga: a TV é o palco, o celular é o seu segredo").forEach {
                Text(it, style = Stage.Body.copy(fontSize = 16.sp), color = Tv.Tertiary)
            }
        }
        QrCard(snap, baseUrl, 190.dp)
    }
}

@Composable
fun QrCard(snap: RoomSnapshot, baseUrl: String?, size: androidx.compose.ui.unit.Dp) {
    val link = baseUrl?.let { "$it#k=${snap.key}" }
    val qr = remember(link) { link?.let(QrCode::bitmap) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(size).background(Color.White, RoundedCornerShape(22.dp)).padding(14.dp), contentAlignment = Alignment.Center) {
            if (qr != null) Image(qr.asImageBitmap(), "Código para entrar na sala", filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
        }
        Text(baseUrl.orEmpty().removePrefix("http://").removeSuffix("/"), style = Tv.Meta, color = Tv.Tertiary)
        Text("Mesmo Wi-Fi da TV", style = Tv.Caption, color = Tv.Tertiary)
    }
}

/** A game's card: its art with the name over it, and who it is for underneath. */
@Composable
fun GameCard(info: GameInfo, seated: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ok = info.fits(seated) || seated == 0
    Column(modifier.width(208.dp)) {
        FocusCard(onClick = onClick, modifier = Modifier.width(208.dp).height(117.dp), shape = RoundedCornerShape(14.dp)) { _ ->
            GameArt(info.id, Modifier.fillMaxSize(), RoundedCornerShape(0.dp), iconSize = 84.dp)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))))
            if (!ok) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
            Text(info.name, style = Tv.Headline.copy(fontSize = 15.sp, lineHeight = 18.sp), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(
            if (ok) "${if (info.minPlayers == info.maxPlayers) "${info.minPlayers}" else "${info.minPlayers}–${info.maxPlayers}"} pessoas  ·  ${info.minutes} min"
            else "Precisa de ${info.minPlayers} a ${info.maxPlayers} pessoas",
            style = Tv.Caption.copy(fontSize = 12.sp), color = if (ok) Tv.Tertiary else Color(0xFFFF9F0A),
        )
    }
}

/** What the home shows behind everything: the featured game's colour glowing in the corner. */
@Composable
fun HomeBackdrop(snap: RoomSnapshot) {
    val seated = snap.players.count { !it.spectator }
    Backdrop(if (seated == 0) null else Styles.of(recommend(snap).info.id).accent)
}

/**
 * "Esta semana": the month's season with its leader and the three weekly challenges, each with its
 * reward, how far the best person in the room got and the faces of who already did it.
 */
@Composable
fun WeekRow(snap: RoomSnapshot, onOpen: () -> Unit) {
    val now = remember { System.currentTimeMillis() }
    val week = remember(now) { Progression.weekKey(now) }
    val careers = snap.careers
    Column {
        Row(Modifier.padding(start = Tv.Edge, end = Tv.Edge, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Esta semana", style = Tv.Headline.copy(fontSize = 17.sp), color = Tv.Secondary)
            Text("Desafios novos toda segunda", style = Tv.Caption.copy(fontSize = 12.sp), color = Tv.Tertiary)
        }
        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Tv.Edge), horizontalArrangement = RowSpacing) {
            item(key = "season") { SeasonCard(snap, onOpen) }
            items(Challenges.forWeek(week), key = { it.id }) { ch ->
                val done = careers.filter { (_, c) -> (c.challenges["$week:${ch.id}"] ?: 0) >= ch.target }.keys
                val best = careers.values.maxOfOrNull { (it.challenges["$week:${ch.id}"] ?: 0).coerceAtMost(ch.target) } ?: 0
                ChallengeCard(ch.text, best, ch.target, snap.players.map { it.player }.filter { it.id in done }, onOpen)
            }
        }
    }
}

private val challengeTint = Color(0xFF64D2FF)

@Composable
private fun ChallengeCard(text: String, best: Int, target: Int, done: List<dev.jean.atvhome.social.game.Player>, onClick: () -> Unit) {
    val complete = done.isNotEmpty()
    val tint = if (complete) Color(0xFF30D158) else challengeTint
    FocusCard(onClick = onClick, modifier = Modifier.width(208.dp).height(112.dp), shape = RoundedCornerShape(16.dp)) { _ ->
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(tint.copy(alpha = 0.22f), Color(0xFF1C1C1E)))))
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).background(tint, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (complete) Icons.Rounded.TaskAlt else Icons.Rounded.Bolt, null, Modifier.size(17.dp), tint = Color.Black)
                }
                Spacer(Modifier.weight(1f))
                Text("+${Progression.CHALLENGE_XP} XP", style = Tv.Headline.copy(fontSize = 12.sp), color = Stage.Gold, modifier = Modifier.background(Stage.Gold.copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp))
            }
            Text(text, style = Tv.Headline.copy(fontSize = 14.sp, lineHeight = 17.sp), color = Tv.Primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (complete) { FaceStack(done, 20.dp, max = 4); Text("feito", style = Tv.Caption.copy(fontSize = 12.sp), color = tint) }
                else {
                    XpBar(0f, best.toFloat() / target, Modifier.weight(1f), accent = tint)
                    Text("$best/$target", style = Tv.Caption.copy(fontSize = 12.sp), color = Tv.Tertiary)
                }
            }
        }
    }
}

@Composable
private fun SeasonCard(snap: RoomSnapshot, onClick: () -> Unit) {
    val (name, left) = remember { seasonLabel() }
    val leader = snap.rankingSeason.firstOrNull()
    FocusCard(onClick = onClick, modifier = Modifier.width(208.dp).height(112.dp), shape = RoundedCornerShape(16.dp)) { _ ->
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFFFE58A).copy(alpha = 0.30f), Color(0xFF1C1C1E)))))
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Crown(22.dp)
                Text("TEMPORADA", style = Tv.Headline.copy(fontSize = 12.sp, letterSpacing = 1.4.sp), color = Stage.Gold, maxLines = 1)
            }
            if (leader != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(personOf(leader, seatedByDevice(snap)), 32.dp, level = leader.level)
                Column {
                    Text(leader.name, style = Tv.Headline.copy(fontSize = 15.sp), color = Tv.Primary, maxLines = 1)
                    Text("${leader.seasonXp} XP · lidera", style = Tv.Caption.copy(fontSize = 12.sp), color = Tv.Secondary, maxLines = 1)
                }
            } else Text("Ninguém pontuou ainda", style = Tv.Headline.copy(fontSize = 14.sp), color = Tv.Primary)
            Text(name.removePrefix("Temporada de ").replaceFirstChar { it.uppercase() } + if (left == 1) " · termina hoje" else " · faltam $left dias", style = Tv.Caption.copy(fontSize = 12.sp), color = Tv.Tertiary, maxLines = 1)
        }
    }
}

@Suppress("unused") private val keepCategory = Category.DEDUCTION
