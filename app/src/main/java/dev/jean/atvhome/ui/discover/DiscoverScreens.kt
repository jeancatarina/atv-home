package dev.jean.atvhome.ui.discover

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.jean.atvhome.data.Filters
import dev.jean.atvhome.data.Format
import dev.jean.atvhome.data.Genre
import dev.jean.atvhome.data.Item
import dev.jean.atvhome.data.MediaType
import dev.jean.atvhome.data.TmdbClient
import dev.jean.atvhome.data.Title
import dev.jean.atvhome.ui.HomeViewModel
import dev.jean.atvhome.ui.components.Chip
import dev.jean.atvhome.ui.components.ContentCard
import dev.jean.atvhome.ui.components.PillButton
import dev.jean.atvhome.ui.detail.ItemGrid
import dev.jean.atvhome.ui.theme.Tv
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Backdrop = Color(0xFF0B0B0C)

@Composable
private fun Header(title: String, subtitle: String) {
    Column(Modifier.padding(start = Tv.Edge, top = 30.dp, end = Tv.Edge)) {
        Text(title, style = Tv.Title, color = Tv.Primary)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, style = Tv.Meta, color = Tv.Tertiary)
    }
}

// --- Busca ------------------------------------------------------------------------------------

@Composable
fun SearchScreen(vm: HomeViewModel, onOpen: (Title) -> Unit, toast: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<HomeViewModel.SearchResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it }
        }
    }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
    LaunchedEffect(query) {
        if (query.isBlank()) { result = null; return@LaunchedEffect }
        delay(450)
        loading = true
        result = vm.search(query)
        loading = false
    }
    Column(Modifier.fillMaxSize().background(Backdrop)) {
        Header("Buscar", "Títulos, pessoas ou do seu jeito: \"comédia curta na Netflix com nota alta\"")
        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(horizontal = Tv.Edge), verticalAlignment = Alignment.CenterVertically) {
            PillButton("Falar", {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga o que quer assistir")
                runCatching { voice.launch(intent) }.onFailure { toast("Esta TV não tem reconhecimento de voz disponível") }
            }, icon = "🎙")
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = Tv.Headline.copy(color = Color.White),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                modifier = Modifier.width(520.dp).focusRequester(fieldFocus).onFocusChanged { fieldFocused = it.isFocused },
                decorationBox = { inner ->
                    Box(
                        Modifier.background(if (fieldFocused) Color.White.copy(alpha = 0.22f) else Tv.Pill, RoundedCornerShape(50))
                            .border(if (fieldFocused) 2.dp else 0.dp, Color.White.copy(alpha = if (fieldFocused) 0.9f else 0f), RoundedCornerShape(50))
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        if (query.isEmpty()) Text("Digite aqui", style = Tv.Headline, color = Tv.Tertiary)
                        inner()
                    }
                },
            )
        }
        val r = result
        when {
            query.isBlank() -> {
                Spacer(Modifier.height(22.dp))
                Text("Experimente", style = Tv.Meta, color = Tv.Secondary, modifier = Modifier.padding(start = Tv.Edge, bottom = 10.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = Tv.Edge), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(examples(vm)) { example -> PillButton(example, { query = example }) }
                }
            }
            loading && r == null -> Text("Procurando…", style = Tv.Meta, color = Tv.Tertiary, modifier = Modifier.padding(Tv.Edge))
            r != null -> {
                Text(
                    r.understood?.let { "Entendi: $it" } ?: "${r.items.size} resultados",
                    style = Tv.Meta, color = if (r.understood != null) Tv.Primary else Tv.Tertiary,
                    modifier = Modifier.padding(start = Tv.Edge, top = 14.dp),
                )
                if (r.items.isEmpty()) {
                    Text("Nada encontrado nos seus streamings. Tente menos filtros.", style = Tv.Body, color = Tv.Secondary, modifier = Modifier.padding(Tv.Edge))
                } else {
                    ItemGrid(r.items, vm, onOpen, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun examples(vm: HomeViewModel): List<String> {
    val first = vm.myProviders.firstOrNull()?.name?.substringBefore(" ")?.let { if (it == "Amazon") "Prime" else it } ?: "Netflix"
    return listOf(
        "comédia curta na $first com nota alta",
        "suspense dos anos 90",
        "série de terror",
        "filmes com o Wagner Moura",
        "ficção científica com nota acima de 8",
        "animação para família",
        "documentário recente",
    )
}

// --- Explorar ---------------------------------------------------------------------------------

@Composable
fun ExploreScreen(vm: HomeViewModel, onOpen: (Title) -> Unit) {
    var filters by remember { mutableStateOf(Filters(minImdb = 7.0)) }
    var providerIds by remember { mutableStateOf(emptySet<Int>()) }
    var items by remember { mutableStateOf<List<Item>?>(null) }
    val firstChip = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstChip.requestFocus() } }
    LaunchedEffect(filters, providerIds, vm.myProviders) {
        items = null
        val ids = providerIds.ifEmpty { vm.myProviders.map { it.id }.toSet() }
        items = vm.explore(filters, ids)
    }
    Column(Modifier.fillMaxSize().background(Backdrop)) {
        Header("Explorar", "Combine os filtros; só aparece o que está nos seus streamings, pela nota do IMDb")
        Spacer(Modifier.height(10.dp))
        ChipRow {
            items(MediaType.entries) { t ->
                Chip(
                    if (t == MediaType.MOVIE) "Filmes" else "Séries", filters.type == t,
                    { filters = filters.copy(type = t, maxRuntime = null) },
                    if (t == MediaType.MOVIE) Modifier.focusRequester(firstChip) else Modifier,
                )
            }
            items(vm.myProviders.distinctBy { vm.packageFor(it) }, key = { it.id }) { p ->
                val label = vm.packageFor(p)?.let(vm::appFor)?.label ?: p.name
                Chip(label, p.id in providerIds, { providerIds = if (p.id in providerIds) providerIds - p.id else providerIds + p.id })
            }
        }
        ChipRow {
            items(Genre.entries.filter { it.id(filters.type) != null }) { g ->
                Chip(g.label, g in filters.genres, {
                    filters = filters.copy(genres = if (g in filters.genres) filters.genres - g else filters.genres + g)
                })
            }
        }
        ChipRow {
            items(listOf(null, 6.5, 7.0, 7.5, 8.0)) { r ->
                Chip(r?.let { "IMDb ${Format.rating(it)}+" } ?: "Qualquer nota", filters.minImdb == r, { filters = filters.copy(minImdb = r) })
            }
            val decades = listOf<Pair<String, Pair<Int?, Int?>>>(
                "Qualquer época" to (null to null), "Recentes" to (java.time.LocalDate.now().year - 2 to null),
                "2010s" to (2010 to 2019), "2000s" to (2000 to 2009), "Anos 90" to (1990 to 1999), "Anos 80" to (1980 to 1989), "Clássicos" to (null to 1979),
            )
            items(decades) { (label, range) ->
                Chip(label, filters.fromYear == range.first && filters.toYear == range.second, {
                    filters = filters.copy(fromYear = range.first, toYear = range.second)
                })
            }
            if (filters.type == MediaType.MOVIE) {
                items(listOf<Pair<String, Int?>>("Qualquer duração" to null, "Até 1 h 30" to 90, "Até 2 h" to 120)) { (label, max) ->
                    Chip(label, filters.maxRuntime == max, { filters = filters.copy(maxRuntime = max) })
                }
            }
        }
        val list = items
        when {
            list == null -> Text("Buscando…", style = Tv.Meta, color = Tv.Tertiary, modifier = Modifier.padding(Tv.Edge))
            list.isEmpty() -> Text("Nada com esses filtros. Tire algum.", style = Tv.Body, color = Tv.Secondary, modifier = Modifier.padding(Tv.Edge))
            else -> ItemGrid(list, vm, onOpen, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ChipRow(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Tv.Edge, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

// --- O que assistir agora? ---------------------------------------------------------------------

@Composable
fun WhatToWatchScreen(vm: HomeViewModel, onOpen: (Title) -> Unit) {
    val scope = rememberCoroutineScope()
    var time by remember { mutableStateOf(HomeViewModel.Time.MEDIUM) }
    var mood by remember { mutableStateOf(HomeViewModel.Mood.SURPRISE) }
    var picks by remember { mutableStateOf<List<Item>?>(null) }
    var loading by remember { mutableStateOf(false) }
    val firstChip = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstChip.requestFocus() } }
    fun run(fresh: Boolean) {
        loading = true
        scope.launch {
            picks = vm.suggest(time, mood, fresh)
            loading = false
        }
    }
    Column(Modifier.fillMaxSize().background(Backdrop)) {
        Header("O que assistir agora?", "Duas perguntas e três sugestões dos seus streamings, todas com IMDb 7 ou mais")
        Spacer(Modifier.height(14.dp))
        Text("Quanto tempo você tem?", style = Tv.Meta, color = Tv.Secondary, modifier = Modifier.padding(start = Tv.Edge))
        ChipRow {
            items(HomeViewModel.Time.entries) { t ->
                Chip(t.label, time == t, { time = t; picks = null }, if (t == HomeViewModel.Time.entries.first()) Modifier.focusRequester(firstChip) else Modifier)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Está a fim de quê?", style = Tv.Meta, color = Tv.Secondary, modifier = Modifier.padding(start = Tv.Edge))
        ChipRow {
            items(HomeViewModel.Mood.entries) { m -> Chip(m.label, mood == m, { mood = m; picks = null }) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(horizontal = Tv.Edge), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(if (picks == null) "Sugerir" else "Outras três", { run(fresh = picks == null) }, icon = "✦")
        }
        Spacer(Modifier.height(18.dp))
        val list = picks
        when {
            loading -> Text("Escolhendo pelos seus streamings e pela nota do IMDb…", style = Tv.Meta, color = Tv.Tertiary, modifier = Modifier.padding(horizontal = Tv.Edge))
            list != null && list.isEmpty() -> Text("Não achei nada com IMDb 7+ nesse tempo e clima. Tente \"Surpresa\" ou mais tempo.", style = Tv.Body, color = Tv.Secondary, modifier = Modifier.padding(horizontal = Tv.Edge))
            list != null -> LazyRow(contentPadding = PaddingValues(horizontal = Tv.Edge), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                items(list, key = { it.title.key }) { item ->
                    val where = vm.myProvidersOf(item).firstOrNull()?.let { vm.packageFor(it)?.let(vm::appFor)?.label ?: it.name }
                    val length = if (item.title.type == MediaType.TV) Format.seasons(item.details.seasons) else Format.runtime(item.details.runtime)
                    ContentCard(
                        image = TmdbClient.image(item.title.backdrop, "w780"),
                        title = item.title.name,
                        line = listOfNotNull(item.imdb?.let { "IMDb ${Format.rating(it.rating)}" }, length, where?.let { "no $it" }).joinToString(" · "),
                        width = 262.dp,
                        onClick = { onOpen(item.title) },
                    )
                }
            }
        }
    }
}
