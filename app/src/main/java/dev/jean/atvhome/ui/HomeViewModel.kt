package dev.jean.atvhome.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.jean.atvhome.App
import dev.jean.atvhome.data.Filters
import dev.jean.atvhome.data.Genre
import dev.jean.atvhome.data.ImdbRatings
import dev.jean.atvhome.data.Item
import dev.jean.atvhome.data.MediaType
import dev.jean.atvhome.data.Provider
import dev.jean.atvhome.data.ProviderMatch
import dev.jean.atvhome.data.QueryParser
import dev.jean.atvhome.data.Title
import dev.jean.atvhome.data.TvApp
import dev.jean.atvhome.data.TvSystem
import dev.jean.atvhome.data.WatchNextItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App
    val catalog = app.catalog
    val myList = app.myList

    var apps by mutableStateOf<List<TvApp>>(emptyList()); private set
    var hiddenApps by mutableStateOf<List<TvApp>>(emptyList()); private set
    /** Installed streaming apps → the TMDB provider they play (most relevant one). */
    var appProvider by mutableStateOf<Map<String, Provider>>(emptyMap()); private set
    /** Providers the user can watch right now (an app for them is installed). */
    var myProviders by mutableStateOf<List<Provider>>(emptyList()); private set
    var trending by mutableStateOf<List<Item>>(emptyList()); private set
    var highImdb by mutableStateOf<List<Item>>(emptyList()); private set
    /** "Esta semana": new on the user's services, with the reason as a badge. */
    var thisWeek by mutableStateOf<List<Pair<Item, String?>>>(emptyList()); private set

    var watchNext by mutableStateOf<List<WatchNextItem>>(emptyList()); private set
    var fromApps by mutableStateOf<List<WatchNextItem>>(emptyList()); private set
    var problem by mutableStateOf<String?>(null); private set
    /** Bumps when IMDb ratings finish loading, so cards redraw with them. */
    var imdbVersion by mutableStateOf(0); private set
    private val shelves = mutableStateMapOf<Int, List<Item>>()
    private val shelfJobs = HashMap<Int, Job>()

    init {
        viewModelScope.launch {
            refreshApps()
            val providers = runCatching { catalog.providersBR() }
                .onFailure { problem = "Sem conexão com o TMDB: ${it.message}" }.getOrDefault(emptyList())
            matchProviders(providers)
            if (app.apps.needsFirstArrangement() && providers.isNotEmpty()) {
                app.apps.firstArrangement(apps + hiddenApps, appProvider.keys.toList())
                refreshApps()
            }
            launch { loadImdb() }
            loadTrending()
            launch { loadThisWeek() }
            loadHighImdb()
        }
        refreshWatchNext()
    }

    // --- Apps ----------------------------------------------------------------------------------

    suspend fun refreshApps() {
        val all = app.apps.load()
        val hidden = app.apps.hidden()
        apps = all.filter { it.packageName !in hidden }
        hiddenApps = all.filter { it.packageName in hidden }
        Log.i(TAG, "apps: " + all.joinToString { "${it.label} (${it.packageName})" })
    }

    fun refreshAppsAsync() = viewModelScope.launch {
        refreshApps()
        catalog.runCatching { providersBR() }.getOrNull()?.let(::matchProviders)
    }

    fun launch(app: TvApp) = this.app.apps.launch(app.packageName)

    /**
     * "Assistir no {app}": opens [item] on its own page in the app when Wikidata knows that
     * service's id; otherwise opens the app. Returns a message for the user, or null on a direct hit.
     */
    suspend fun watch(item: Item, packageName: String): String? {
        val tvApp = appFor(packageName) ?: (apps + hiddenApps).firstOrNull { it.packageName == packageName }
        val provider = appProvider[packageName]
            ?: item.details.providers.firstOrNull { packageFor(it) == packageName }
        val imdbId = item.details.imdbId
        if (provider != null && imdbId != null) {
            val ids = app.deepLinks.ids(imdbId)
            val intent = app.deepLinks.intent(packageName, provider.name, item.title.type, ids, item.title.name)
            if (intent != null && app.deepLinks.open(app, intent)) return null
        }
        val label = tvApp?.label ?: provider?.name ?: "o app"
        return if (this.app.apps.launch(packageName)) "Abri o $label. Procure por: ${item.title.name}"
        else "Não consegui abrir $label"
    }

    fun move(packageName: String, delta: Int) {
        val list = apps.toMutableList()
        val from = list.indexOfFirst { it.packageName == packageName }
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from < 0 || from == to) return
        list.add(to, list.removeAt(from))
        apps = list
    }

    fun saveOrder() = app.apps.saveOrder(apps.map { it.packageName } + hiddenApps.map { it.packageName })

    fun setHidden(tvApp: TvApp, hidden: Boolean) {
        app.apps.setHidden(tvApp.packageName, hidden)
        saveOrder()
        viewModelScope.launch { refreshApps() }
    }

    fun appFor(packageName: String) = apps.firstOrNull { it.packageName == packageName }

    private fun matchProviders(providers: List<Provider>) {
        val match = ProviderMatch.match(providers, (apps + hiddenApps).map { it.packageName to it.label })
        // providers come most relevant first: keep the first one per app (Netflix over "Netflix with Ads")
        val byApp = LinkedHashMap<String, Provider>()
        providers.forEach { p -> match[p.id]?.let { pkg -> byApp.putIfAbsent(pkg, p) } }
        appProvider = byApp
        myProviders = providers.filter { it.id in match }
        Log.i(TAG, "streaming apps: " + byApp.entries.joinToString { "${it.key}=${it.value.name}" })
    }

    /** Package of the installed app that plays [provider], if any. */
    fun packageFor(provider: Provider): String? {
        appProvider.entries.firstOrNull { it.value.id == provider.id }?.let { return it.key }
        val match = ProviderMatch.match(listOf(provider), (apps + hiddenApps).map { it.packageName to it.label })
        return match[provider.id]
    }

    fun myProvidersOf(item: Item): List<Provider> {
        val mine = myProviders.map { it.id }.toSet()
        return item.details.providers.filter { it.id in mine }.distinctBy { packageFor(it) }
    }

    // --- Content -------------------------------------------------------------------------------

    private suspend fun loadImdb() {
        withContext(Dispatchers.IO) { ImdbRatings.ensureFresh(app) }
        if (ImdbRatings.isLoaded) {
            trending = trending.map(::rerate)
            highImdb = highImdb.map(::rerate)
            shelves.keys.toList().forEach { id -> shelves[id] = shelves[id].orEmpty().map(::rerate) }
            imdbVersion++
            if (highImdb.isEmpty()) loadHighImdb()
        }
    }

    private fun rerate(item: Item) = Item(item.details, catalog.imdb(item.details))

    /** Warms the image cache with the Top Shelf art so it's there before the shelf turns. */
    private fun prefetch(items: List<Item>) {
        val loader = coil3.SingletonImageLoader.get(app)
        items.take(6).forEach { item ->
            listOfNotNull(
                dev.jean.atvhome.data.TmdbClient.image(item.title.backdrop, "w1280"),
                dev.jean.atvhome.data.TmdbClient.image(item.details.logo, "w500"),
            ).forEach { url -> loader.enqueue(coil3.request.ImageRequest.Builder(app).data(url).build()) }
        }
    }

    private suspend fun loadTrending() {
        val titles = catalog.trending(pages = 2).filter { it.backdrop != null }
        // The first eight first, so the Top Shelf fills in quickly; the rest right after
        trending = catalog.items(titles.take(8))
        prefetch(trending)
        trending = trending + catalog.items(titles.drop(8).take(32))
        if (trending.isEmpty() && problem == null) problem = "Não consegui carregar as tendências do TMDB."
    }

    private suspend fun loadHighImdb() {
        if (myProviders.isEmpty()) return
        val ids = myProviders.map { it.id }.toSet()
        val titles = coroutineScope {
            MediaType.entries.map { type ->
                async { catalog.discover(Filters(type = type), ids, page = 1, sortByRating = true) + catalog.discover(Filters(type = type), ids, page = 2, sortByRating = true) }
            }.awaitAll().flatten()
        }
        val day = LocalDate.now().toEpochDay()
        val items = catalog.items(titles.shuffled(Random(day)).take(30))
        val good = items.filter { (it.imdb?.rating ?: 0.0) >= 7.5 && (it.imdb?.votes ?: 0) >= 20_000 }
        // Before IMDb's dataset arrives, TMDB's rating stands in so the row isn't empty on first boot
        highImdb = (if (ImdbRatings.isLoaded) good else items.filter { it.title.tmdbRating >= 7.8 }).take(16)
    }

    private suspend fun loadThisWeek() {
        val titles = catalog.thisWeek(myProviders.map { it.id }.toSet())
        thisWeek = catalog.items(titles).mapNotNull { item ->
            val app = myProvidersOf(item).firstOrNull()?.let { p -> packageFor(p)?.let(::appFor)?.label ?: p.name }
                ?: return@mapNotNull null
            val badge = if (item.title.type == MediaType.TV) {
                dev.jean.atvhome.data.Format.episodeBadge(item.details.nextEpisodeDate, item.details.lastEpisodeDate)
                    ?.let { "$it · $app" } ?: "Novo ep. · $app"
            } else "Novo no $app"
            item to badge
        }
    }

    // --- Match ----------------------------------------------------------------------------------

    /** A shared deck for the phone game: 30 titles on the user's services with IMDb 6.5 or more. */
    /**
     * A batch of titles for Match, from random pages of the user's services. [avoid] are the ones already
     * dealt in this session; [memory] puts what the house recently refused at the end of the batch (or
     * leaves it out while there is plenty else), so the line stays fresh and never ends.
     */
    suspend fun matchDeck(type: MediaType, avoid: Set<String> = emptySet(), memory: dev.jean.atvhome.data.MatchMemory? = null): List<Item> {
        val ids = myProviders.map { it.id }.toSet()
        val filters = Filters(type = type, minImdb = 6.5)
        val titles = coroutineScope {
            (0 until 6).map { i ->
                async { catalog.discover(filters, ids, page = 1 + Random.nextInt(20), sortByRating = i % 2 == 1) }
            }.awaitAll().flatten().distinctBy { it.key }.filter { it.poster != null && it.key !in avoid }
        }
        // What was refused lately goes last; with a good pool left it is not even fetched
        val order = memory?.order(titles.map { it.key }, kotlin.random.Random.Default) ?: titles.map { it.key }.shuffled()
        val byKey = titles.associateBy { it.key }
        val chosen = order.mapNotNull(byKey::get).take(48)
        val minVotes = if (type == MediaType.MOVIE) 3_000 else 1_500
        val items = catalog.items(chosen).filter { catalog.passes(it, filters, minVotes) && myProvidersOf(it).isNotEmpty() }
        // catalog.items may not keep the order: restore it
        val rank = order.withIndex().associate { it.value to it.index }
        return items.sortedBy { rank[it.title.key] ?: Int.MAX_VALUE }.take(30)
    }

    fun refreshWatchNext() = viewModelScope.launch {
        watchNext = TvSystem.watchNext(app)
        fromApps = TvSystem.previewPrograms(app)
    }

    /** What the Top Shelf shows: a label, its titles, and the app they all play in (if one). */
    data class Shelf(val label: String, val items: List<Item>, val appPackage: String?)

    /** Top Shelf content while [packageName] has focus: that service's hits, or Brazil's trending. */
    fun heroFor(packageName: String?): Shelf {
        val provider = packageName?.let { appProvider[it] }
        if (provider != null) {
            val shelf = shelves[provider.id]
            if (shelf == null) requestShelf(provider.id)
            val label = appFor(packageName)?.label ?: provider.name
            val items = shelf ?: trending.filter { i -> i.details.providers.any { it.id == provider.id } }
            if (items.isNotEmpty()) return Shelf("Em alta no $label", items, packageName)
        }
        return Shelf("Em alta no Brasil", trending, null)
    }

    private fun requestShelf(providerId: Int) {
        if (shelfJobs[providerId]?.isActive == true || trending.isEmpty()) return
        shelfJobs[providerId] = viewModelScope.launch {
            shelves[providerId] = catalog.shelf(providerId, trending).also(::prefetch)
        }
    }

    suspend fun item(title: Title): Item? = catalog.details(title)?.let { Item(it, catalog.imdb(it)) }

    // --- "O que assistir agora?" ---------------------------------------------------------------

    enum class Time(val label: String) { SHORT("Até 1 h 30"), MEDIUM("Até 2 h"), ANY("Sem pressa"), SERIES("Uma série") }

    enum class Mood(val label: String, val genres: List<Genre>) {
        LAUGH("Rir", listOf(Genre.COMEDY)),
        TENSION("Tensão", listOf(Genre.THRILLER, Genre.CRIME, Genre.MYSTERY)),
        FEEL("Me emocionar", listOf(Genre.DRAMA, Genre.ROMANCE)),
        ACTION("Ação", listOf(Genre.ACTION, Genre.ADVENTURE, Genre.SCIFI)),
        FAMILY("Família", listOf(Genre.FAMILY, Genre.ANIMATION)),
        LEARN("Aprender", listOf(Genre.DOCUMENTARY, Genre.HISTORY)),
        SURPRISE("Surpresa", emptyList()),
    }

    private var suggestionPool: MutableList<Item> = mutableListOf()

    /** Three titles on the user's services, IMDb ≥ 7 with enough votes, that fit the time and mood. */
    suspend fun suggest(time: Time, mood: Mood, fresh: Boolean): List<Item> {
        if (!fresh && suggestionPool.size >= 3) return List(3) { suggestionPool.removeAt(0) }
        val type = if (time == Time.SERIES) MediaType.TV else MediaType.MOVIE
        val genre = mood.genres.randomOrNull()
        val filters = Filters(
            type = type,
            genres = setOfNotNull(genre),
            maxRuntime = when (time) { Time.SHORT -> 95; Time.MEDIUM -> 125; else -> null },
            minImdb = 7.0,
        )
        val ids = myProviders.map { it.id }.toSet()
        val titles = coroutineScope {
            listOf(
                async { catalog.discover(filters, ids, page = 1 + Random.nextInt(3)) },
                async { catalog.discover(filters, ids, page = 1, sortByRating = true) },
                async { catalog.discover(filters, ids, page = 2 + Random.nextInt(3), sortByRating = true) },
            ).awaitAll().flatten().distinctBy { it.key }
        }
        val minVotes = if (type == MediaType.MOVIE) 5_000 else 2_000
        val items = catalog.items(titles.shuffled().take(24))
            .filter { catalog.passes(it, filters, minVotes) && myProvidersOf(it).isNotEmpty() }
        // Spread over services: the first picks come from different apps when possible
        val spread = items.groupBy { myProvidersOf(it).first().id }.values.flatMap { it.withIndex() }
            .sortedBy { it.index }.map { it.value }
        suggestionPool = spread.toMutableList()
        return List(minOf(3, suggestionPool.size)) { suggestionPool.removeAt(0) }
    }

    // --- Roleta ---------------------------------------------------------------------------------

    /** What a spin shows: the three winners and the posters that flash past on the reels. */
    data class Spin(val picks: List<Item>, val reel: List<String>)

    /**
     * A random spin over the user's services: any genre, any year, IMDb ≥ 6.5 with enough votes
     * that the rating means something, three different titles.
     */
    suspend fun spin(type: MediaType): Spin {
        val ids = myProviders.map { it.id }.toSet()
        val filters = Filters(type = type, minImdb = 6.5)
        val titles = coroutineScope {
            listOf(
                async { catalog.discover(filters, ids, page = 1 + Random.nextInt(15)) },
                async { catalog.discover(filters, ids, page = 1 + Random.nextInt(15)) },
                async { catalog.discover(filters, ids, page = 1 + Random.nextInt(8), sortByRating = true) },
            ).awaitAll().flatten().distinctBy { it.key }
        }
        val reel = titles.mapNotNull { dev.jean.atvhome.data.TmdbClient.image(it.poster, "w342") }.shuffled()
        val minVotes = if (type == MediaType.MOVIE) 3_000 else 1_500
        val picks = catalog.items(titles.shuffled().take(15))
            .filter { catalog.passes(it, filters, minVotes) && myProvidersOf(it).isNotEmpty() }
            .shuffled()
        // One per service first, so a spin doesn't land on three Prime titles
        val spread = picks.groupBy { myProvidersOf(it).first().id }.values.flatMap { it.withIndex() }
            .sortedBy { it.index }.map { it.value }.take(3)
        return Spin(spread, reel)
    }

    // --- Search and Explore ---------------------------------------------------------------------

    data class SearchResult(val understood: String?, val items: List<Item>)

    suspend fun search(query: String): SearchResult {
        val parsed = QueryParser.parse(query, myProviders)
        val f = parsed.filters
        if (f.isEmpty || (parsed.leftover.split(' ').size >= 2 && f.genres.isEmpty() && f.person == null)) {
            val titles = catalog.search(query)
            return SearchResult(null, catalog.items(titles.take(18)))
        }
        val providerIds = f.providerIds.ifEmpty { myProviders.map { it.id }.toSet() }
        val items = explore(f, providerIds)
        val understood = describe(f)
        return SearchResult(understood, items)
    }

    suspend fun explore(filters: Filters, providerIds: Set<Int>): List<Item> {
        val sortByRating = filters.minImdb != null
        val titles = coroutineScope {
            (1..2).map { page -> async { catalog.discover(filters, providerIds, page, sortByRating) } }.awaitAll().flatten()
        }.distinctBy { it.key }
        return catalog.items(titles.take(36)).filter { catalog.passes(it, filters, minVotes = 1_000) }
            .let { list -> if (sortByRating) list.sortedByDescending { it.imdb?.rating ?: 0.0 } else list }
    }

    fun describe(f: Filters): String = listOfNotNull(
        if (f.type == MediaType.TV) "Séries" else "Filmes",
        f.genres.joinToString(", ") { it.label }.ifBlank { null },
        f.providerIds.mapNotNull { id -> myProviders.firstOrNull { it.id == id }?.name }.joinToString(", ").ifBlank { null },
        f.maxRuntime?.let { "até ${it / 60} h ${(it % 60).takeIf { m -> m > 0 }?.let { m -> "$m min" } ?: ""}".trim() },
        f.minRuntime?.let { "longos" },
        f.minImdb?.let { "IMDb ≥ ${dev.jean.atvhome.data.Format.rating(it)}" },
        when {
            f.fromYear != null && f.toYear != null -> "${f.fromYear}–${f.toYear}"
            f.fromYear != null -> "desde ${f.fromYear}"
            f.toYear != null -> "até ${f.toYear}"
            else -> null
        },
        f.person?.let { "com ${it.split(' ').joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) }}" },
    ).joinToString(" · ")

    companion object { private const val TAG = "Home" }
}
