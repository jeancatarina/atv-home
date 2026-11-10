package dev.jean.atvhome.social.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.jean.atvhome.social.Sfx
import dev.jean.atvhome.social.SocialViewModel
import dev.jean.atvhome.social.game.Game
import dev.jean.atvhome.social.game.HostCmd
import dev.jean.atvhome.social.games.FinishedDetails
import dev.jean.atvhome.social.games.GameCatalog
import dev.jean.atvhome.social.kit.Suggest
import dev.jean.atvhome.social.games.StageBody
import dev.jean.atvhome.social.games.bomb.BombStage
import dev.jean.atvhome.social.games.bomb.BombState
import dev.jean.atvhome.social.games.frequency.FStage
import dev.jean.atvhome.social.games.frequency.FrequencyState
import dev.jean.atvhome.social.night.Presenter
import dev.jean.atvhome.social.profile.Career
import dev.jean.atvhome.social.room.GameSnap
import dev.jean.atvhome.social.room.Room
import dev.jean.atvhome.social.room.RoomSnapshot
import dev.jean.atvhome.ui.HomeViewModel
import dev.jean.atvhome.ui.theme.Tv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Social: a room of phones around the TV and the games to play. The TV owns the truth; this screen
 * only draws the room's snapshot and sends the host's commands back to it.
 */
@Composable
fun SocialScreen(vm: SocialViewModel, home: HomeViewModel, toast: (String) -> Unit) {
    LaunchedEffect(Unit) { vm.shown() }
    DisposableEffect(Unit) { onDispose { vm.gone() } }

    val room = vm.room
    Box(Modifier.fillMaxSize()) {
        when {
            room != null -> Open(vm, home, room, vm.baseUrl, toast)
            vm.failed -> { Backdrop(null); Message("Não rolou abrir a sala na rede", "Dá uma olhada no Wi-Fi da TV e tenta de novo") }
            else -> { Backdrop(null); Message("Abrindo a sala…", null) }
        }
    }
}

@Composable
private fun Message(title: String, sub: String?) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = Stage.Title, color = Tv.Primary)
        if (sub != null) Text(sub, style = Stage.Body, color = Tv.Tertiary)
    }
}

@Composable
private fun Open(vm: SocialViewModel, home: HomeViewModel, room: Room, baseUrl: String?, toast: (String) -> Unit) {
    val snap by room.snapshot.collectAsState()
    // A room with people in it keeps the TV awake: the remote sits untouched while phones play
    val view = LocalView.current
    val awake = snap.players.isNotEmpty()
    DisposableEffect(awake) {
        view.keepScreenOn = awake
        onDispose { view.keepScreenOn = false }
    }
    val game = snap.game
    // Sounds: someone joins, a game starts, a game ends (a bomb's strikes ring in its own stage)
    val seatedNow = snap.players.count { !it.spectator }
    var seatedBefore by remember { mutableIntStateOf(seatedNow) }
    LaunchedEffect(seatedNow) { if (vm.sound && seatedNow > seatedBefore) Sfx.play(Sfx.Effect.JOIN); seatedBefore = seatedNow }
    LaunchedEffect(game != null) { if (vm.sound && game != null) Sfx.play(Sfx.Effect.START) }
    LaunchedEffect(game?.over != null) { if (vm.sound && game?.over != null) Sfx.play(Sfx.Effect.WIN) }
    // Faces are decoded once per photo (a few KB each) and drawn by every avatar
    val faces by produceState(emptyMap<String, androidx.compose.ui.graphics.ImageBitmap>(), snap.players.map { it.player.id to it.face }) {
        value = withContext(Dispatchers.Default) {
            snap.players.filter { it.face > 0 }.mapNotNull { info ->
                room.face(info.player.id)?.let { b -> runCatching { android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }.getOrNull() }?.let { info.player.id to it }
            }.toMap()
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalFaces provides faces) {
        Box(
            Modifier.fillMaxSize().onPreviewKeyEvent { e ->
                val st = game?.state
                // Games steered with the remote's arrows: the dial and the bomb
                val arrows = !snap.paused && e.type == KeyEventType.KeyDown && game?.over == null &&
                    ((st is FrequencyState && st.stage == FStage.DIAL) || (st is BombState && st.stage == BombStage.PLAY))
                if (arrows) {
                    val vertical = st is BombState
                    val cmd = when (e.key) {
                        Key.DirectionLeft -> HostCmd.LEFT; Key.DirectionRight -> HostCmd.RIGHT
                        Key.DirectionUp -> if (vertical) HostCmd.UP else null; Key.DirectionDown -> if (vertical) HostCmd.DOWN else null
                        else -> null
                    }
                    if (cmd != null) { room.host(cmd); return@onPreviewKeyEvent true }
                }
                // Play/Pause on the remote pauses and resumes a game
                if (e.type == KeyEventType.KeyDown && game != null && (e.key == Key.MediaPlayPause || e.key == Key.MediaPlay || e.key == Key.MediaPause)) {
                    if (snap.paused) room.resume() else room.pause()
                    true
                } else false
            },
        ) {
            when {
                game != null -> {
                    BackHandler(enabled = !snap.paused && game.over == null) {
                        val st = game.state
                        // Inside a bomb module BACK steps out of it instead of pausing
                        when {
                            st is BombState && st.focus != null -> room.host(HostCmd.SKIP)
                            else -> room.pause()
                        }
                    }
                    BackHandler(enabled = !snap.paused && game.over != null) { room.toLobby() }
                    GameStage(vm, room, snap, game)
                    if (snap.paused) PauseMenu(room)
                }
                snap.awards.isNotEmpty() -> CeremonyScreen(room, snap)
                else -> Home(vm, home, room, snap, baseUrl, toast)
            }
            ReactionsLayer(snap.reactions, snap.players)
            HostRibbon(vm.ribbon)
            CelebrationLayer(snap.celebrations, snap.players, vm.sound, onResult = snap.game?.over != null)
        }
    }
}

// --- Home ----------------------------------------------------------------------------------------------

@Composable
private fun Home(vm: SocialViewModel, home: HomeViewModel, room: Room, snap: RoomSnapshot, baseUrl: String?, toast: (String) -> Unit) {
    var tab by remember { mutableStateOf(Tab.PLAY) }
    var page by remember { mutableStateOf<Game<*>?>(null) }
    var profile by remember { mutableStateOf<Career?>(null) }
    var invite by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    val tabFocus = remember { FocusRequester() }
    val seated = snap.players.count { !it.spectator }
    // An empty room has no "Jogar": focus the tabs. The moment someone sits down the hero's "Jogar"
    // appears and takes the focus, or the remote would have nothing to move
    // Closing a game page, a profile or the invite card takes the focused button away with it, and the
    // remote would be dead: whenever all of them are closed, put the focus back on something real
    LaunchedEffect(seated > 0, page == null, profile == null, invite) {
        delay(60)
        if (page == null && profile == null && !invite) runCatching { (if (tab == Tab.PLAY && seated > 0) firstFocus else tabFocus).requestFocus() }
    }

    WithLevels(snap) { Box(Modifier.fillMaxSize()) {
        HomeBackdrop(snap)
        Column(Modifier.fillMaxSize()) {
            TopBar(tab, { tab = it }, snap, onInvite = { invite = true }, tabFocus = tabFocus)
            when (tab) {
                Tab.PLAY -> PlayTab(snap, baseUrl, onOpen = { page = it }, onSurprise = {
                    // A surprise leans toward what this table has not played: drawn from the best five suggestions
                    val fits = GameCatalog.all.filter { it.info.fits(seated) }.ifEmpty { GameCatalog.all }
                    page = Suggest.order(fits, snap.careers.values, snap.events.map { it.gameId }.toSet(), System.currentTimeMillis(), kotlin.random.Random.Default).take(5).random()
                }, firstFocus = firstFocus, onRanking = { tab = Tab.RANKING })
                Tab.RANKING -> RankingTab(snap, onOpen = { profile = it })
                Tab.PROFILES -> ProfilesTab(snap, onOpen = { profile = it })
                Tab.NIGHT -> NightTab(snap, onClose = { room.closeNight() })
                Tab.SETTINGS -> SettingsTab(vm.tone, { vm.tone = it }, vm.sound, { vm.sound = !vm.sound }, onNewRoom = { room.newRoom(); toast("Sala nova: o código mudou") })
            }
        }
        page?.let { g ->
            GamePage(g, snap, busy = g.info.id == "film5" && vm.filmsLoading, onClose = { page = null }, onStart = { picks ->
                scope.launch {
                    if (g.info.id == "film5") vm.loadFilms(home)
                    val why = room.start(g, picks)
                    if (why != null) toast(why) else {
                        vm.tone?.let { t -> vm.say(Presenter.gameStarting(g.info.name, t, vm.said, kotlin.random.Random.Default).also { vm.said += it }) }
                        page = null
                    }
                }
            })
        }
        profile?.let { ProfileDetail(it, onClose = { profile = null }, seated = seatedByDevice(snap)) }
        if (invite) {
            BackHandler { invite = false }
            val close = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { close.requestFocus() } }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Entrar na sala", style = Stage.Hero.copy(fontSize = 34.sp), color = Tv.Primary)
                    QrCard(snap, baseUrl, 230.dp)
                    TvButton("Fechar", { invite = false }, modifier = Modifier.focusRequester(close))
                }
            }
        }
    } }
}

// --- A game on stage ---------------------------------------------------------------------------------

@Composable
private fun GameStage(vm: SocialViewModel, room: Room, snap: RoomSnapshot, game: GameSnap) {
    val over = game.over
    val action = remember(game) { gameOf(game)?.let { g -> tvActionOf(g, game.state) } }
    val line = remember(game.info.id, over?.headline) {
        if (over == null) null else vm.tone?.let { t -> Presenter.afterGame(snap.events.filter { it.gameId == game.info.id }.takeLast(6), snap.players.map { it.player }, t, vm.said, kotlin.random.Random.Default).also { vm.said += it } }
    }
    StageScreen(snap, game, action, line, onFire = { room.host(it) }, onAgain = { room.again() }, onLobby = { room.toLobby() })
}

/** A game on the TV, as a pure function of the room's snapshot: this is what the screenshots render. */
@Composable
fun StageScreen(
    snap: RoomSnapshot,
    game: GameSnap,
    action: dev.jean.atvhome.social.game.TvAction?,
    line: String?,
    onFire: (HostCmd) -> Unit,
    onAgain: () -> Unit,
    onLobby: () -> Unit,
) {
    val st = Styles.of(game.info.id)
    val over = game.over
    val main = remember { FocusRequester() }
    // Focus moves only when the button itself changes, not on every phase: it never steals a navigation
    // (and again when the pause menu closes: its button took the focus away with it)
    LaunchedEffect(action?.label, over != null, snap.paused) { if (!snap.paused) { delay(60); runCatching { main.requestFocus() } } }

    WithLevels(snap) { Box(Modifier.fillMaxSize()) {
        Backdrop(st.accent)
        if (over != null) {
            ResultScreen(snap, game, over, line, onAgain = onAgain, onLobby = onLobby, details = { FinishedDetails(game.state) }, focus = main)
        } else {
            Row(Modifier.padding(start = Tv.Edge, top = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(34.dp).background(st.accent, CircleShape), contentAlignment = Alignment.Center) { Icon(st.icon, null, Modifier.size(20.dp), tint = Color.Black) }
                Text(game.info.name, style = Tv.Headline.copy(fontSize = 16.sp), color = Tv.Secondary)
            }
            Box(Modifier.fillMaxSize().padding(start = Tv.Edge, end = Tv.Edge, top = 56.dp, bottom = 112.dp), contentAlignment = Alignment.Center) { StageBody(game.state, snap, game.endsAt) }
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TvActionBar(action, st.accent, onFire = onFire, focus = main, hint = backHint(game))
            }
        }
    } }
}

private fun gameOf(g: GameSnap): Game<*>? = GameCatalog.byId(g.info.id)

@Suppress("UNCHECKED_CAST")
private fun tvActionOf(game: Game<*>, state: Any) = (game as Game<Any>).tvAction(state)

private fun backHint(g: GameSnap): String {
    val s = g.state
    return when {
        s is BombState && s.focus != null -> "VOLTAR sai do módulo  ·  Play/Pause pausa"
        else -> "VOLTAR pausa o jogo"
    }
}

@Composable
private fun PauseMenu(room: Room) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    BackHandler { room.resume() }
    PauseView(onResume = { room.resume() }, onAgain = { room.again() }, onLobby = { room.toLobby() }, focus = first)
}

/** The pause panel: dimmed game behind, one rounded card with the three ways out. */
@Composable
fun PauseView(onResume: () -> Unit, onAgain: () -> Unit, onLobby: () -> Unit, focus: FocusRequester) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(520.dp).background(Color(0xF21C1C1E), RoundedCornerShape(28.dp)).padding(horizontal = 36.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(56.dp).background(Color.White.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Text("❚❚", style = Tv.Headline.copy(fontSize = 20.sp), color = Tv.Primary)
            }
            Text("Jogo pausado", style = Stage.Title.copy(fontSize = 32.sp), color = Tv.Primary)
            Text("Nos celulares aparece “Olha pra TV”", style = Tv.Meta.copy(fontSize = 15.sp), color = Tv.Tertiary)
            Spacer(Modifier.height(8.dp))
            TvButton("Continuar", onResume, icon = Icons.Rounded.PlayArrow, accent = Color(0xFF30D158), modifier = Modifier.focusRequester(focus))
            TvButton("Recomeçar a partida", onAgain, icon = Icons.Rounded.Replay)
            TvButton("Trocar de jogo", onLobby, icon = Icons.Rounded.SwapHoriz)
        }
    }
}

// --- The night's ceremony ------------------------------------------------------------------------

/** One trophy at a time with its owner's face, building up to the champion; then the whole shelf. */
@Composable
private fun CeremonyScreen(room: Room, snap: RoomSnapshot) {
    val awards = snap.awards
    var index by remember { mutableIntStateOf(0) }
    val done = index >= awards.size
    val main = remember { FocusRequester() }
    LaunchedEffect(index) {
        if (!done) { delay(6_000); index++ } else runCatching { main.requestFocus() }
    }
    BackHandler { room.newNight() }
    WithLevels(snap) { CeremonyView(snap, index, onSkip = { index = awards.size }, onNewNight = { room.newNight() }, focus = main) }
}

/** The ceremony as a pure function of the room and which trophy is up. */
@Composable
fun CeremonyView(snap: RoomSnapshot, index: Int, onSkip: () -> Unit, onNewNight: () -> Unit, focus: FocusRequester) {
    val awards = snap.awards
    val done = index >= awards.size
    Box(Modifier.fillMaxSize()) {
        Backdrop(Stage.Gold)
        Column(Modifier.fillMaxSize().padding(horizontal = Tv.Edge, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (!done) {
                val a = awards[index]
                val p = snap.players.firstOrNull { it.player.id == a.who }?.player
                val champion = a.key == "CHAMPION"
                val pop = remember(index) { androidx.compose.animation.core.Animatable(0.5f) }
                LaunchedEffect(index) { pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = androidx.compose.animation.core.Spring.StiffnessLow)) }
                Text(if (champion) "O GRANDE FINAL" else "TROFÉU ${index + 1} DE ${awards.size}", style = Tv.Headline.copy(fontSize = 14.sp, letterSpacing = 2.4.sp), color = Stage.Gold)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.size(420.dp, 250.dp), contentAlignment = Alignment.Center) {
                    // The spotlight: a soft pool of light under the winner
                    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Stage.Gold.copy(alpha = 0.34f), Color.Transparent))))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
                        if (champion) Crown(44.dp)
                        if (p != null) Avatar(p, if (champion) 150.dp else 124.dp, ring = true)
                    }
                }
                Text(a.title.uppercase(), style = Stage.Hero.copy(fontSize = 46.sp), color = Stage.Gold, textAlign = TextAlign.Center, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value })
                Spacer(Modifier.height(4.dp))
                Text(a.blurb, style = Stage.Title, color = Tv.Primary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 760.dp))
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { awards.indices.forEach { i -> Box(Modifier.size(if (i == index) 12.dp else 8.dp).background(if (i <= index) Stage.Gold else Color.White.copy(alpha = 0.25f), CircleShape)) } }
                Spacer(Modifier.height(14.dp))
                TvButton("Pular", onSkip, modifier = Modifier.focusRequester(focus))
            } else {
                Text("TROFÉUS DA NOITE", style = Tv.Headline.copy(fontSize = 14.sp, letterSpacing = 2.4.sp), color = Stage.Gold)
                Spacer(Modifier.height(10.dp))
                // The shelf: each trophy on a card with its owner's face, two columns
                awards.chunked(2).take(5).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        pair.forEach { a ->
                            val p = snap.players.firstOrNull { it.player.id == a.who }?.player
                            val champion = a.key == "CHAMPION"
                            Row(
                                Modifier.width(520.dp).height(72.dp).background(if (champion) Stage.Gold.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.07f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (p != null) Avatar(p, 40.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(a.title, style = Tv.Headline.copy(fontSize = 17.sp), color = if (champion) Stage.Gold else Tv.Primary, maxLines = 1)
                                    Text(a.blurb, style = Tv.Meta.copy(fontSize = 13.sp), color = Tv.Secondary, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                if (champion) Crown(26.dp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Quem quiser pode baixar o pôster da noite no próprio celular", style = Tv.Meta.copy(fontSize = 15.sp), color = Tv.Tertiary)
                Spacer(Modifier.height(10.dp))
                TvButton("Nova noite", onNewNight, icon = Icons.Rounded.Replay, modifier = Modifier.focusRequester(focus))
            }
        }
    }
}
