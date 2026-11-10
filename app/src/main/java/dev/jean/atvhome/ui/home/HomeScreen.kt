package dev.jean.atvhome.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import dev.jean.atvhome.ui.theme.LocalAccent
import dev.jean.atvhome.ui.theme.screen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.transformations
import coil3.request.crossfade
import dev.jean.atvhome.data.Format
import dev.jean.atvhome.data.Item
import dev.jean.atvhome.data.TmdbClient
import dev.jean.atvhome.data.TvApp
import dev.jean.atvhome.data.TvSystem
import dev.jean.atvhome.data.Title
import dev.jean.atvhome.ui.HomeViewModel
import dev.jean.atvhome.ui.components.CertBadge
import dev.jean.atvhome.ui.components.ContentCard
import dev.jean.atvhome.ui.components.FocusCard
import dev.jean.atvhome.ui.components.FocusMemory
import dev.jean.atvhome.ui.components.LocalFocusMemory
import androidx.compose.runtime.CompositionLocalProvider
import dev.jean.atvhome.ui.components.ImdbBadge
import dev.jean.atvhome.ui.components.PillButton
import dev.jean.atvhome.ui.components.RowPadding
import dev.jean.atvhome.ui.components.RowSpacing
import dev.jean.atvhome.ui.components.RowTitle
import dev.jean.atvhome.ui.theme.Tv
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    active: Boolean,
    restoreSignal: Int,
    resetSignal: Int,
    inputTick: Int,
    onOpen: (Title) -> Unit,
    onSearch: () -> Unit,
    onWhatToWatch: () -> Unit,
    onExplore: () -> Unit,
    onRoulette: () -> Unit,
    onMatch: () -> Unit,
    onSocial: () -> Unit,
    toast: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val myList by vm.myList.entries.collectAsState()
    val firstAppFocus = remember { FocusRequester() }
    val titleFocus = remember { FocusRequester() }
    val memory = remember { FocusMemory() }

    // Focus writes the pending choice; the Top Shelf follows once focus rests for 300 ms, like
    // tvOS, so sweeping across the dock doesn't fire a full-screen transition per app
    var pendingPackage by remember { mutableStateOf<String?>(null) }
    var pendingOverride by remember { mutableStateOf<Item?>(null) }
    var focusedPackage by remember { mutableStateOf<String?>(null) }
    var override by remember { mutableStateOf<Item?>(null) }
    LaunchedEffect(pendingPackage, pendingOverride) {
        delay(300)
        focusedPackage = pendingPackage
        override = pendingOverride
    }
    var heroIndex by remember { mutableIntStateOf(0) }
    var heroButtonsFocused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }

    val shelf = vm.heroFor(focusedPackage)
    val heroLabel = shelf.label
    val heroItems = shelf.items
    val hero: Item? = override ?: heroItems.takeIf { it.isNotEmpty() }?.let { it[heroIndex.mod(it.size)] }
    val heroProviderPackage = if (override == null) shelf.appPackage else null

    // The Top Shelf moves on every 8 s, unless the user is reading it (buttons focused) or away
    // Trailers only play while the remote rests (like tvOS), and stop for good after two minutes
    // with nobody touching it: a playing video keeps the screen awake and the screensaver away
    var resting by remember { mutableStateOf(false) }
    var away by remember { mutableStateOf(false) }
    LaunchedEffect(inputTick) {
        resting = false
        away = false
        delay(3_000)
        resting = true
        delay(120_000)
        away = true
    }
    var trailerAlpha by remember { mutableFloatStateOf(0f) }
    var sound by remember { mutableStateOf(false) }
    val trailerOn = trailerAlpha > 0.05f
    LaunchedEffect(hero?.title?.key) { sound = false }
    var manualTick by remember { mutableIntStateOf(0) }
    val step: (Int) -> Unit = { d ->
        if (heroItems.size > 1 && override == null) {
            heroIndex = (heroIndex + d).mod(heroItems.size)
            manualTick++
        }
    }
    LaunchedEffect(heroLabel, heroItems.size, heroButtonsFocused, override, active, manualTick, trailerOn, away) {
        if (heroButtonsFocused || override != null || !active || trailerOn || away) return@LaunchedEffect
        while (true) {
            delay(8_000)
            heroIndex++
        }
    }
    LaunchedEffect(heroLabel) { heroIndex = 0 }

    // First focus lands on the first app, like tvOS
    var focusedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(vm.apps.isNotEmpty()) {
        if (vm.apps.isNotEmpty() && !focusedOnce) {
            focusedOnce = true
            runCatching { firstAppFocus.requestFocus() }
        }
    }
    // Snapshot where focus was when another screen covers Home: on the way back Android first drops
    // focus on the first card of that row by itself, which would overwrite the memory
    var saved by remember { mutableStateOf<Pair<FocusRequester?, Boolean>?>(null) }
    LaunchedEffect(active) { if (!active) saved = memory.last to memory.lastInDock }
    // Back from another screen: focus returns to the exact card or button it left
    LaunchedEffect(restoreSignal) {
        if (restoreSignal == 0) return@LaunchedEffect
        withFrameNanos { }
        val (last, inDock) = saved ?: (memory.last to memory.lastInDock)
        if (last == null || !runCatching { last.requestFocus() }.isSuccess) runCatching { firstAppFocus.requestFocus() }
        if (inDock) listState.scrollToItem(0)
    }
    // HOME pressed while already home: back to the top and the first app
    LaunchedEffect(resetSignal) {
        if (resetSignal > 0) {
            pendingOverride = null
            override = null
            editing = null
            listState.scrollToItem(0)
            runCatching { firstAppFocus.requestFocus() }
        }
    }

    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120 } }
    val dim = animateFloatAsState(if (scrolled) 0.6f else 0f, tween(400), label = "dim")

    CompositionLocalProvider(LocalFocusMemory provides memory) {
    // No background here: the window is already black, and every full-screen fill costs a frame budget
    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            // The remote's ◀◀ ▶▶ switch the Top Shelf title from anywhere on Home
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.MediaFastForward, Key.MediaNext -> { step(1); true }
                Key.MediaRewind, Key.MediaPrevious -> { step(-1); true }
                // Play/Pause turns the trailer's sound on and off, like tvOS
                Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { if (trailerOn) { sound = !sound; true } else false }
                else -> false
            }
        },
    ) {
        // Scrolled down, the page takes the artwork's colour; at the top the artwork covers it
        if (dim.value > 0f) Box(Modifier.fillMaxSize().background(LocalAccent.current.screen()))
        val trailerKey = hero?.details?.trailerKey
        if (trailerKey != null && active && resting && !away && !scrolled && editing == null) {
            TrailerBackdrop(
                videoKey = trailerKey,
                sound = sound,
                dim = { dim.value },
                onAlpha = { trailerAlpha = it },
                onEnded = { sound = false; if (override == null) step(1) },
            )
        }
        HeroBackdrop(hero) { (1f - trailerAlpha) * (1f - dim.value) }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 60.dp),
        ) {
            item(key = "hero") {
                HeroInfo(
                    label = if (override != null) null else heroLabel,
                    item = hero,
                    count = if (override == null) heroItems.size else 0,
                    index = heroIndex,
                    providerPackage = heroProviderPackage,
                    vm = vm,
                    onSearch = onSearch,
                    onWhatToWatch = onWhatToWatch,
                    onExplore = onExplore,
                    onRoulette = onRoulette,
                    onMatch = onMatch,
                    onSocial = onSocial,
                    soundState = if (!trailerOn) 0 else if (sound) 2 else 1,
                    onOpen = onOpen,
                    onStep = step,
                    titleFocus = titleFocus,
                    onButtonsFocus = { heroButtonsFocused = it; if (it) scope.launch { listState.animateScrollToItem(0) } },
                    toast = toast,
                )
            }
            item(key = "dock") {
                Dock(
                    apps = vm.apps,
                    firstFocus = firstAppFocus,
                    upTo = if (hero != null) titleFocus else null,
                    editing = editing,
                    onFocus = { app ->
                        memory.lastInDock = true
                        pendingPackage = app.packageName
                        pendingOverride = null
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                    onClick = { app ->
                        if (editing != null) { editing = null; vm.saveOrder() }
                        else if (!vm.launch(app)) toast("Não consegui abrir ${app.label}")
                    },
                    onLongClick = { app -> editing = app.packageName },
                    onMove = { app, delta -> vm.move(app.packageName, delta) },
                    onHide = { app -> editing = null; vm.setHidden(app, true); toast("${app.label} foi para Apps ocultos, no fim da tela") },
                    onDone = { editing = null; vm.saveOrder() },
                )
            }
            if (vm.watchNext.isNotEmpty()) {
                item(key = "watchNext") {
                    Section("Continuar assistindo") {
                        LazyRow(contentPadding = RowPadding, horizontalArrangement = RowSpacing) {
                            items(vm.watchNext, key = { it.packageName + it.title }) { w ->
                                ContentCard(
                                    image = w.image, title = w.title, line = w.subtitle, progress = w.progress,
                                    onClick = { if (!TvSystem.open(context, w)) toast("Não consegui abrir ${w.title}") },
                                )
                            }
                        }
                    }
                }
            }
            if (vm.fromApps.isNotEmpty()) {
                item(key = "fromApps") {
                    Section("Destaques dos seus apps") {
                        LazyRow(contentPadding = RowPadding, horizontalArrangement = RowSpacing) {
                            items(vm.fromApps, key = { it.packageName + it.title }) { w ->
                                ContentCard(
                                    image = w.image, title = w.title,
                                    line = vm.appFor(w.packageName)?.label ?: w.subtitle,
                                    onClick = { if (!TvSystem.open(context, w)) toast("Não consegui abrir ${w.title}") },
                                )
                            }
                        }
                    }
                }
            }
            if (myList.isNotEmpty()) {
                item(key = "myList") {
                    Section("Minha Lista") {
                        LazyRow(contentPadding = RowPadding, horizontalArrangement = RowSpacing) {
                            items(myList, key = { it.title.key }) { entry ->
                                TitleCard(entry.title, vm, badge = entry.badge, onOpen = onOpen, onFocusItem = { pendingOverride = it })
                            }
                        }
                    }
                }
            }
            if (vm.thisWeek.isNotEmpty()) {
                item(key = "thisWeek") {
                    Section("Esta semana nos seus streamings") {
                        LazyRow(contentPadding = RowPadding, horizontalArrangement = RowSpacing) {
                            items(vm.thisWeek, key = { it.first.title.key }) { (item, badge) ->
                                ItemCard(item, vm, onOpen = onOpen, onFocusItem = { pendingOverride = it }, badge = badge)
                            }
                        }
                    }
                }
            }
            if (vm.trending.isNotEmpty()) {
                item(key = "trending") {
                    Section("Em alta no Brasil") {
                        LazyRow(contentPadding = RowPadding, horizontalArrangement = RowSpacing) {
                            items(vm.trending, key = { it.title.key }) { item ->
                                ItemCard(item, vm, onOpen = onOpen, onFocusItem = { pendingOverride = it })
                            }
                        }
                    }
                }
            }
            if (vm.highImdb.isNotEmpty()) {
                item(key = "imdb") {
                    Section("Notas altas no IMDb nos seus streamings") {
                        LazyRow(contentPadding = RowPadding, horizontalArrangement = RowSpacing) {
                            items(vm.highImdb, key = { it.title.key }) { item ->
                                ItemCard(item, vm, onOpen = onOpen, onFocusItem = { pendingOverride = it })
                            }
                        }
                    }
                }
            }
            if (vm.hiddenApps.isNotEmpty()) {
                item(key = "hidden") {
                    Section("Apps ocultos") {
                        LazyRow(contentPadding = RowPadding, horizontalArrangement = RowSpacing) {
                            items(vm.hiddenApps, key = { it.packageName }) { app ->
                                AppCard(app, editing = false, onFocus = {}, onClick = { vm.launch(app) }, onLongClick = {
                                    vm.setHidden(app, false); toast("${app.label} voltou para a fileira de apps")
                                })
                            }
                        }
                    }
                }
            }
            vm.problem?.let { problem ->
                item(key = "problem") {
                    Text(problem, style = Tv.Meta, color = Tv.Tertiary, modifier = Modifier.padding(horizontal = Tv.Edge, vertical = 12.dp))
                }
            }
        }

        Clock(Modifier.align(Alignment.TopEnd).padding(top = 22.dp, end = Tv.Edge))
    }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 26.dp)) {
        RowTitle(title)
        content()
    }
}

/**
 * Full-bleed artwork crossfading as the Top Shelf changes. Still, like tvOS: a continuous zoom
 * redrew the whole screen every frame and the TV's GPU missed most frame deadlines.
 */
@Composable
private fun HeroBackdrop(item: Item?, alpha: () -> Float) {
    val context = LocalContext.current
    val url = TmdbClient.image(item?.title?.backdrop, "w1280")
    // The scrim is baked into the bitmap (see Scrim) and dimming is the image's own alpha over the
    // black window: one full-screen pass per frame instead of five
    AsyncImage(
        model = remember(url) {
            // A short crossfade: two full-screen bitmaps blending is the heaviest frame on this GPU
            coil3.request.ImageRequest.Builder(context).data(url).transformations(Scrim).crossfade(220).build()
        },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha() },
    )
}

@Composable
private fun HeroInfo(
    label: String?,
    item: Item?,
    count: Int,
    index: Int,
    providerPackage: String?,
    vm: HomeViewModel,
    onSearch: () -> Unit,
    onWhatToWatch: () -> Unit,
    onExplore: () -> Unit,
    onRoulette: () -> Unit,
    onMatch: () -> Unit,
    onSocial: () -> Unit,
    soundState: Int,
    onOpen: (Title) -> Unit,
    onButtonsFocus: (Boolean) -> Unit,
    onStep: (Int) -> Unit,
    titleFocus: FocusRequester,
    toast: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var titleFocused by remember { mutableStateOf(false) }
    val myList by vm.myList.entries.collectAsState()
    Column(Modifier.fillMaxWidth().height(330.dp).padding(start = Tv.Edge, top = 20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Buscar", onSearch, icon = "⌕", onFocus = onButtonsFocus)
            PillButton("O que assistir agora?", onWhatToWatch, icon = "✦", onFocus = onButtonsFocus)
            PillButton("Roleta", onRoulette, icon = "🎲", onFocus = onButtonsFocus)
            PillButton("Match", onMatch, icon = "♥", onFocus = onButtonsFocus)
            PillButton("Social", onSocial, icon = "☻", onFocus = onButtonsFocus)
            PillButton("Explorar", onExplore, onFocus = onButtonsFocus)
        }
        Spacer(Modifier.height(30.dp))
        if (item == null) {
            Text(if (vm.problem != null) "" else "Carregando o que está em alta…", style = Tv.Meta, color = Tv.Tertiary)
            return@Column
        }
        val shown = item
        val fade = remember { androidx.compose.animation.core.Animatable(1f) }
        LaunchedEffect(shown.title.key) { fade.snapTo(0f); fade.animateTo(1f, tween(450)) }
        // Text grows upward from a fixed baseline, so the buttons below never move between titles
        val watchPackage = providerPackage ?: vm.myProvidersOf(item).firstOrNull()?.let(vm::packageFor)
        val watchApp = watchPackage?.let(vm::appFor)
        val ring by animateFloatAsState(if (titleFocused) 1f else 0f, tween(200), label = "ring")
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
            // The title itself is the carousel: focus it and ◀ ▶ switch titles, OK watches
            Box(
                Modifier
                    .focusRequester(titleFocus)
                    .onFocusChanged { titleFocused = it.isFocused; onButtonsFocus(it.isFocused) }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionLeft -> { onStep(-1); true }
                            Key.DirectionRight -> { onStep(1); true }
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                if (watchApp != null) scope.launch { vm.watch(item, watchApp.packageName)?.let(toast) }
                                else onOpen(item.title)
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable()
                    .offset(x = (-14).dp)
                    .background(Color.White.copy(alpha = 0.08f * ring), RoundedCornerShape(16.dp))
                    .border(2.dp, Color.White.copy(alpha = 0.7f * ring), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
            Column(Modifier.widthIn(max = 440.dp).graphicsLayer { alpha = fade.value }) {
                if (label != null) {
                    Text(label.uppercase(), style = Tv.Caption, color = Tv.Secondary)
                    Spacer(Modifier.height(8.dp))
                }
                TitleArt(shown, maxHeight = 78.dp)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImdbBadge(shown.imdb)
                    CertBadge(shown.details.certification)
                    Text(Format.metaLine(shown.details), style = Tv.Meta, color = Tv.Secondary, maxLines = 1)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    shown.title.overview, style = Tv.Body, color = Tv.Secondary, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (watchApp != null) {
                PillButton("Assistir no ${watchApp.label}", {
                    scope.launch { vm.watch(item, watchApp.packageName)?.let(toast) }
                }, icon = "▶", onFocus = onButtonsFocus)
            }
            PillButton("Detalhes", { onOpen(item.title) }, onFocus = onButtonsFocus)
            item.details.trailerKey?.let { key ->
                PillButton("Trailer", { if (!TvSystem.trailer(context, key)) toast("Não consegui abrir o YouTube") }, onFocus = onButtonsFocus)
            }
            val inList = myList.any { it.title.key == item.title.key }
            PillButton(
                if (inList) "Na Minha Lista" else "Minha Lista",
                {
                    vm.myList.toggle(item.title, item.details.providers.map { it.id })
                    toast(if (inList) "Tirei ${item.title.name} da Minha Lista" else "${item.title.name} está na Minha Lista")
                },
                icon = if (inList) "✓" else "+",
                onFocus = onButtonsFocus,
            )
            if (count > 1) {
                Spacer(Modifier.width(8.dp))
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        repeat(minOf(count, 12)) { i ->
                            val on = i == index.mod(count) % 12
                            Box(Modifier.size(if (on) 7.dp else 5.dp).background(if (on) Color.White else Tv.Tertiary, CircleShape))
                        }
                        if (soundState > 0) {
                            Spacer(Modifier.width(10.dp))
                            Text(if (soundState == 2) "🔊" else "🔇", style = Tv.Meta, color = Tv.Secondary)
                        }
                    }
                }
            }
        }
    }
}

/** The title's logo from TMDB, or its name set large when there is no logo. */
@Composable
fun TitleArt(item: Item, maxHeight: androidx.compose.ui.unit.Dp) {
    val logo = item.details.logo
    if (logo != null) {
        AsyncImage(
            model = TmdbClient.image(logo, "w500"),
            contentDescription = item.title.name,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = Modifier.heightIn(max = maxHeight).widthIn(max = 340.dp),
        )
    } else {
        Text(item.title.name, style = Tv.Display, color = Tv.Primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Dock(
    apps: List<TvApp>,
    firstFocus: FocusRequester,
    upTo: FocusRequester?,
    editing: String?,
    onFocus: (TvApp) -> Unit,
    onClick: (TvApp) -> Unit,
    onLongClick: (TvApp) -> Unit,
    onMove: (TvApp, Int) -> Unit,
    onHide: (TvApp) -> Unit,
    onDone: () -> Unit,
) {
    var focusedLabel by remember { mutableStateOf<String?>(null) }
    Column {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Tv.Edge, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(Tv.Gap),
        ) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                val isEditing = editing == app.packageName
                AppCard(
                    app = app,
                    editing = isEditing,
                    modifier = (if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        // ▲ from any app lands on the Top Shelf title, where ◀ ▶ browse it
                        .focusProperties { if (upTo != null) up = upTo }
                        .onPreviewKeyEvent { event ->
                            if (!isEditing || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionLeft -> { onMove(app, -1); true }
                                Key.DirectionRight -> { onMove(app, 1); true }
                                Key.DirectionDown -> { onHide(app); true }
                                Key.DirectionUp -> true
                                Key.Back, Key.Escape -> { onDone(); true }
                                else -> false
                            }
                        },
                    onFocus = { focused -> if (focused) { focusedLabel = app.label; onFocus(app) } },
                    onClick = { onClick(app) },
                    onLongClick = { onLongClick(app) },
                )
            }
        }
        val hint = if (editing != null) "Modo de edição  ·  ◀ ▶ mover  ·  ▼ ocultar  ·  OK concluir"
        else focusedLabel
        AnimatedVisibility(hint != null, enter = fadeIn(), exit = fadeOut()) {
            Text(hint.orEmpty(), style = Tv.Meta, color = if (editing != null) Tv.Primary else Tv.Tertiary, modifier = Modifier.padding(start = Tv.Edge))
        }
    }
}

@Composable
fun AppCard(
    app: TvApp,
    editing: Boolean,
    onFocus: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The jiggle only exists while editing: an infinite animation per card kept the screen redrawing
    val angle = if (editing) {
        val wiggle = rememberInfiniteTransition(label = "wiggle")
        wiggle.animateFloat(-1.6f, 1.6f, infiniteRepeatable(tween(140), RepeatMode.Reverse), label = "angle").value
    } else 0f
    val banner = remember(app.packageName) { app.banner?.let { runCatching { BitmapPainter(it.toBitmap(320, 180).asImageBitmap()) }.getOrNull() } }
    val icon = remember(app.packageName) {
        if (banner != null) null else app.icon?.let { runCatching { BitmapPainter(it.toBitmap(128, 128).asImageBitmap()) }.getOrNull() }
    }
    FocusCard(
        onClick = onClick,
        onLongClick = onLongClick,
        onFocus = onFocus,
        focusedScale = 1.12f,
        modifier = modifier.width(156.dp).height(88.dp).graphicsLayer { rotationZ = angle },
    ) {
        if (banner != null) {
            androidx.compose.foundation.Image(banner, app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (icon != null) androidx.compose.foundation.Image(icon, null, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(4.dp))
                Text(app.label, style = Tv.Caption, color = Tv.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A card for a full [Item] (details already loaded). */
@Composable
fun ItemCard(item: Item, vm: HomeViewModel, onOpen: (Title) -> Unit, onFocusItem: (Item) -> Unit = {}, badge: String? = null) {
    @Suppress("UNUSED_VARIABLE") val version = vm.imdbVersion
    ContentCard(
        image = TmdbClient.image(item.title.backdrop, "w780"),
        title = item.title.name,
        line = Format.cardLine(item.imdb ?: vm.catalog.imdb(item.details), item.title.year),
        badge = badge,
        onClick = { onOpen(item.title) },
        onFocus = { if (it) onFocusItem(item) },
    )
}

/** A card for a bare [Title]; its details (and IMDb rating) load when it is first shown. */
@Composable
fun TitleCard(title: Title, vm: HomeViewModel, badge: String?, onOpen: (Title) -> Unit, onFocusItem: (Item) -> Unit = {}) {
    var item by remember(title.key) { mutableStateOf<Item?>(null) }
    LaunchedEffect(title.key, vm.imdbVersion) { item = vm.item(title) }
    val loaded = item
    if (loaded != null) {
        ItemCard(loaded, vm, onOpen, onFocusItem, badge)
    } else {
        ContentCard(TmdbClient.image(title.backdrop, "w780"), title.name, title.year?.toString(), { onOpen(title) }, badge = badge)
    }
}

@Composable
private fun Clock(modifier: Modifier) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1000L * (60 - now.second))
        }
    }
    Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), style = Tv.Clock, color = Tv.Primary, modifier = modifier)
}
