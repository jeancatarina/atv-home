package dev.jean.atvhome.ui.detail

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jean.atvhome.data.Catalog
import dev.jean.atvhome.data.Format
import dev.jean.atvhome.data.Item
import dev.jean.atvhome.data.MediaType
import dev.jean.atvhome.data.TmdbClient
import dev.jean.atvhome.data.Title
import dev.jean.atvhome.data.TvSystem
import dev.jean.atvhome.ui.HomeViewModel
import dev.jean.atvhome.ui.components.CertBadge
import dev.jean.atvhome.ui.components.ImdbBadge
import dev.jean.atvhome.ui.components.PersonCard
import dev.jean.atvhome.ui.components.PillButton
import dev.jean.atvhome.ui.components.PosterCard
import dev.jean.atvhome.ui.components.RowPadding
import dev.jean.atvhome.ui.components.RowSpacing
import dev.jean.atvhome.ui.components.RowTitle
import dev.jean.atvhome.ui.home.TitleArt
import dev.jean.atvhome.ui.home.TitleCard
import dev.jean.atvhome.ui.theme.Tv

@Composable
fun DetailScreen(
    title: Title,
    vm: HomeViewModel,
    onOpen: (Title) -> Unit,
    onPerson: (Int) -> Unit,
    toast: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var item by remember(title.key) { mutableStateOf<Item?>(null) }
    var failed by remember(title.key) { mutableStateOf(false) }
    val myList by vm.myList.entries.collectAsState()
    val firstButton = remember { FocusRequester() }
    LaunchedEffect(title.key) {
        item = vm.item(title)
        failed = item == null
        vm.myList.seen(title)
    }
    val loaded = item
    LaunchedEffect(loaded != null) { if (loaded != null) runCatching { firstButton.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = TmdbClient.image(loaded?.title?.backdrop ?: title.backdrop, "w1280"),
            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.92f), 0.55f to Color.Black.copy(alpha = 0.5f), 0.85f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 0.75f to Color.Black.copy(alpha = 0.8f), 1f to Color.Black)))

        if (loaded == null) {
            Text(
                if (failed) "Não consegui carregar ${title.name}. Confira a internet da TV." else "Carregando…",
                style = Tv.Meta, color = Tv.Secondary, modifier = Modifier.padding(Tv.Edge),
            )
            return@Box
        }
        val d = loaded.details
        val mine = vm.myProvidersOf(loaded)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
            item(key = "info") {
                Column(Modifier.fillMaxWidth().padding(top = 56.dp)) {
                    Column(Modifier.padding(start = Tv.Edge).widthIn(max = 470.dp)) {
                        TitleArt(loaded, maxHeight = 90.dp)
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ImdbBadge(loaded.imdb ?: vm.catalog.imdb(d), showVotes = true)
                            CertBadge(d.certification)
                            Text(Format.metaLine(d), style = Tv.Meta, color = Tv.Secondary, maxLines = 1)
                        }
                        Spacer(Modifier.height(10.dp))
                        d.tagline?.let { Text(it, style = Tv.Headline, color = Tv.Primary, maxLines = 1); Spacer(Modifier.height(4.dp)) }
                        Text(d.title.overview.ifBlank { "Sem sinopse em português." }, style = Tv.Body, color = Tv.Secondary, maxLines = 5, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(10.dp))
                        Text(availability(d, mine.map { it.name }), style = Tv.Meta, color = Tv.Tertiary)
                    }
                    Spacer(Modifier.height(16.dp))
                    // Edge padding lives in the row so a focused (scaled) button isn't clipped
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = Tv.Edge, vertical = 6.dp)) {
                        itemsIndexed(mine, key = { _, p -> "watch" + p.id }) { index, provider ->
                            val app = vm.packageFor(provider)?.let(vm::appFor)
                            val mod = if (index == 0) Modifier.focusRequester(firstButton) else Modifier
                            PillButton("Assistir no ${app?.label ?: provider.name}", {
                                val pkg = app?.packageName
                                if (pkg == null) toast("Não achei o app de ${provider.name}")
                                else scope.launch { vm.watch(loaded, pkg)?.let(toast) }
                            }, icon = "▶", modifier = mod)
                        }
                        d.trailerKey?.let { key ->
                            item(key = "trailer") {
                                PillButton("Trailer", { if (!TvSystem.trailer(context, key)) toast("Não consegui abrir o YouTube") },
                                    modifier = if (mine.isEmpty()) Modifier.focusRequester(firstButton) else Modifier)
                            }
                        }
                        item(key = "list") {
                            val inList = myList.any { it.title.key == d.title.key }
                            PillButton(
                                if (inList) "Na Minha Lista" else "Minha Lista",
                                {
                                    vm.myList.toggle(d.title, d.providers.map { it.id })
                                    toast(if (inList) "Tirei ${d.title.name} da Minha Lista" else "Vou avisar quando chegar num streaming seu ou sair episódio novo")
                                },
                                icon = if (inList) "✓" else "+",
                                modifier = if (mine.isEmpty() && d.trailerKey == null) Modifier.focusRequester(firstButton) else Modifier,
                            )
                        }
                    }
                }
            }
            if (d.cast.isNotEmpty()) {
                item(key = "cast") {
                    Column(Modifier.padding(top = 26.dp)) {
                        RowTitle("Elenco  ·  OK mostra a filmografia nos seus streamings")
                        LazyRow(contentPadding = RowPadding, horizontalArrangement = RowSpacing) {
                            items(d.cast, key = { it.id }) { c -> PersonCard(TmdbClient.image(c.photo, "w185"), c.name, c.character) { onPerson(c.id) } }
                        }
                    }
                }
            }
            val similar = d.recommendations.filter { it.backdrop != null }.take(16)
            if (similar.isNotEmpty()) {
                item(key = "similar") {
                    Column(Modifier.padding(top = 26.dp)) {
                        RowTitle("Parecidos")
                        LazyRow(contentPadding = RowPadding, horizontalArrangement = RowSpacing) {
                            items(similar, key = { it.key }) { t -> TitleCard(t, vm, badge = null, onOpen = onOpen) }
                        }
                    }
                }
            }
        }
    }
}

private fun availability(d: dev.jean.atvhome.data.Details, mine: List<String>): String {
    val all = d.providers.map { it.name }.distinct()
    return when {
        mine.isNotEmpty() -> "Nos seus apps: ${mine.joinToString(", ")}" +
            (all - mine.toSet()).takeIf { it.isNotEmpty() }?.let { "  ·  também em ${it.joinToString(", ")}" }.orEmpty()
        all.isNotEmpty() -> "Não está nos seus apps. Disponível em ${all.joinToString(", ")}."
        else -> "Não está em nenhum streaming por assinatura no Brasil agora."
    } + if (d.title.type == MediaType.TV) Format.episodeBadge(d.nextEpisodeDate, d.lastEpisodeDate)?.let { "  ·  $it" }.orEmpty() else ""
}

/** An actor's or director's work, best known first, with what is on the user's services marked. */
@Composable
fun PersonScreen(id: Int, vm: HomeViewModel, onOpen: (Title) -> Unit) {
    var person by remember(id) { mutableStateOf<Catalog.Person?>(null) }
    var failed by remember(id) { mutableStateOf(false) }
    val firstPoster = remember { FocusRequester() }
    LaunchedEffect(id) { person = vm.catalog.person(id); failed = person == null }
    LaunchedEffect(person != null) { if (person != null) runCatching { firstPoster.requestFocus() } }
    val p = person
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0C))) {
        if (p == null) {
            Text(if (failed) "Não consegui carregar." else "Carregando…", style = Tv.Meta, color = Tv.Secondary, modifier = Modifier.padding(Tv.Edge))
            return@Box
        }
        Column(Modifier.fillMaxSize().padding(top = 36.dp)) {
            Row(Modifier.padding(horizontal = Tv.Edge), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(TmdbClient.image(p.photo, "w185"), p.name, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(Tv.Card))
                Spacer(Modifier.padding(8.dp))
                Column {
                    Text(p.name, style = Tv.Title, color = Tv.Primary)
                    Text("${p.credits.size} títulos  ·  o selo mostra onde você já pode assistir", style = Tv.Meta, color = Tv.Tertiary)
                }
            }
            Spacer(Modifier.height(18.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(118.dp),
                contentPadding = PaddingValues(horizontal = Tv.Edge, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(Tv.Gap),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                itemsIndexed(p.credits.take(60), key = { _, t -> t.key }) { index, t ->
                    CreditPoster(t, vm, onOpen, if (index == 0) Modifier.focusRequester(firstPoster) else Modifier)
                }
            }
        }
    }
}

@Composable
fun CreditPoster(t: Title, vm: HomeViewModel, onOpen: (Title) -> Unit, modifier: Modifier = Modifier) {
    var item by remember(t.key) { mutableStateOf<Item?>(null) }
    LaunchedEffect(t.key, vm.imdbVersion) { item = vm.item(t) }
    val loaded = item
    val mine = loaded?.let { vm.myProvidersOf(it) }.orEmpty()
    PosterCard(
        image = TmdbClient.image(t.poster, "w342"),
        title = t.name,
        line = Format.cardLine(loaded?.let { it.imdb ?: vm.catalog.imdb(it.details) }, t.year),
        badge = mine.firstOrNull()?.let { vm.packageFor(it)?.let(vm::appFor)?.label ?: it.name },
        onClick = { onOpen(t) },
        modifier = modifier,
    )
}

/** Poster grid shared by Search and Explore. */
@Composable
fun ItemGrid(items: List<Item>, vm: HomeViewModel, onOpen: (Title) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(118.dp),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Tv.Edge, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(Tv.Gap),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        items(items, key = { it.title.key }) { item ->
            val mine = vm.myProvidersOf(item)
            PosterCard(
                image = TmdbClient.image(item.title.poster, "w342"),
                title = item.title.name,
                line = Format.cardLine(item.imdb ?: vm.catalog.imdb(item.details), item.title.year),
                badge = mine.firstOrNull()?.let { vm.packageFor(it)?.let(vm::appFor)?.label ?: it.name },
                onClick = { onOpen(item.title) },
            )
        }
    }
}
