package dev.jean.atvhome.data

import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate

/** A title with everything a card or the Top Shelf shows. */
data class Item(val details: Details, val imdb: ImdbRating?) {
    val title get() = details.title
}

/**
 * Everything the screens ask TMDB and IMDb for. Calls are cached on disk (see [TmdbClient]) and
 * details in memory, and run at most six at a time so the TV stays responsive.
 */
class Catalog(val tmdb: TmdbClient) {

    private val details = LruCache<String, Details>(300)
    private val gate = Semaphore(6)
    private val providersLock = Mutex()
    private var providers: List<Provider>? = null

    fun imdb(details: Details): ImdbRating? = ImdbRatings.rating(details.imdbId)

    /** Subscription services in Brazil, most relevant first. */
    suspend fun providersBR(): List<Provider> = providersLock.withLock {
        providers ?: run {
            val all = LinkedHashMap<Int, Pair<Provider, Int>>()
            for (type in MediaType.entries) {
                tmdb.get("watch/providers/${type.path}", mapOf("watch_region" to "BR")).objects("results").forEach { p ->
                    val id = p.int("provider_id") ?: return@forEach
                    val priority = p.obj("display_priorities")?.int("BR") ?: 999
                    val provider = Provider(id, p.str("provider_name") ?: return@forEach, p.str("logo_path"))
                    val current = all[id]
                    if (current == null || priority < current.second) all[id] = provider to priority
                }
            }
            all.values.sortedBy { it.second }.map { it.first }.also { providers = it }
        }
    }

    suspend fun details(title: Title): Details? = details(title.type, title.id)

    suspend fun details(type: MediaType, id: Int): Details? = withContext(Dispatchers.Default) { loadDetails(type, id) }

    private suspend fun loadDetails(type: MediaType, id: Int): Details? {
        val key = "${type.path}:$id"
        details.get(key)?.let { return it }
        val appended = listOf(
            "external_ids", "images", "credits", "videos", "watch/providers", "recommendations",
            if (type == MediaType.MOVIE) "release_dates" else "content_ratings",
        ).joinToString(",")
        val json = runCatching {
            gate.withPermit {
                tmdb.get(
                    "${type.path}/$id",
                    mapOf(
                        "append_to_response" to appended,
                        "include_image_language" to "pt,en,null",
                        "include_video_language" to "pt,en",
                    ),
                )
            }
        }.onFailure { Log.w(TAG, "details $key", it) }.getOrNull() ?: return null
        return Parse.details(json, type)?.also { details.put(key, it) }
    }

    suspend fun items(titles: List<Title>): List<Item> = coroutineScope {
        titles.map { t -> async { details(t)?.let { Item(it, imdb(it)) } } }.awaitAll().filterNotNull()
    }

    suspend fun trending(pages: Int = 2): List<Title> = coroutineScope {
        (1..pages).map { page ->
            async { runCatching { Parse.titles(tmdb.get("trending/all/week", mapOf("page" to "$page"))) }.getOrDefault(emptyList()) }
        }.awaitAll().flatten().distinctBy { it.key }
    }

    /**
     * What's hot on one service in Brazil: this week's trending titles that stream there, then the
     * service's most popular titles released in the last 120 days.
     */
    suspend fun shelf(providerId: Int, trendingItems: List<Item>): List<Item> {
        val fromTrending = trendingItems.filter { item -> item.details.providers.any { it.id == providerId } }
        if (fromTrending.size >= 8) return fromTrending.take(12)
        val since = LocalDate.now().minusDays(120).toString()
        val recent = coroutineScope {
            MediaType.entries.map { type ->
                async {
                    runCatching {
                        Parse.titles(
                            tmdb.get(
                                "discover/${type.path}",
                                mapOf(
                                    "watch_region" to "BR",
                                    "with_watch_providers" to "$providerId",
                                    "with_watch_monetization_types" to "flatrate",
                                    "sort_by" to "popularity.desc",
                                    (if (type == MediaType.MOVIE) "primary_release_date.gte" else "first_air_date.gte") to since,
                                    "vote_count.gte" to "20",
                                ),
                            ),
                            type,
                        )
                    }.getOrDefault(emptyList())
                }
            }.awaitAll()
        }
        // Interleave movies and series so one kind doesn't fill the shelf
        val mixed = recent[0].zip(recent[1]).flatMap { listOf(it.first, it.second) } +
            recent[0].drop(recent[1].size) + recent[1].drop(recent[0].size)
        val known = fromTrending.map { it.title.key }.toSet()
        val extra = items(mixed.filter { it.key !in known && it.backdrop != null }.take(12 - fromTrending.size))
        return fromTrending + extra
    }

    /** Discover with [filters]; the IMDb floor and the runtime are applied after details arrive. */
    suspend fun discover(filters: Filters, providerIds: Set<Int>, page: Int = 1, sortByRating: Boolean = false): List<Title> {
        val type = filters.type
        val params = HashMap<String, String>()
        params["watch_region"] = "BR"
        params["with_watch_monetization_types"] = "flatrate"
        if (providerIds.isNotEmpty()) params["with_watch_providers"] = providerIds.joinToString("|")
        val genreIds = filters.genres.mapNotNull { it.id(type) }.distinct()
        if (genreIds.isNotEmpty()) params["with_genres"] = genreIds.joinToString(",")
        val keywords = filters.genres.filter { it.fits(type) }.mapNotNull { it.keyword }
        if (keywords.isNotEmpty()) params["with_keywords"] = keywords.joinToString(",")
        // Countries are one or the other ("Coreanos" and "Nacionais" together: either)
        val countries = filters.genres.mapNotNull { it.country }
        if (countries.isNotEmpty()) params["with_origin_country"] = countries.joinToString("|")
        if (sortByRating) {
            params["sort_by"] = "vote_average.desc"
            params["vote_count.gte"] = if (type == MediaType.MOVIE) "800" else "300"
        } else {
            params["sort_by"] = "popularity.desc"
            params["vote_count.gte"] = "150"
        }
        filters.minImdb?.let { params["vote_average.gte"] = "${(it - 1.0).coerceAtLeast(0.0)}" }
        if (type == MediaType.MOVIE) {
            filters.maxRuntime?.let { params["with_runtime.lte"] = "$it" }
            // Shorts (Pixar's 6-minute ones) aren't what anyone means by "a movie tonight"
            params["with_runtime.gte"] = "${filters.minRuntime ?: 40}"
        }
        val dateKey = if (type == MediaType.MOVIE) "primary_release_date" else "first_air_date"
        filters.fromYear?.let { params["$dateKey.gte"] = "$it-01-01" }
        filters.toYear?.let { params["$dateKey.lte"] = "$it-12-31" }
        filters.person?.let { name -> personId(name)?.let { params["with_people"] = "$it" } }
        params["page"] = "$page"
        return runCatching { Parse.titles(tmdb.get("discover/${type.path}", params), type) }
            .onFailure { Log.w(TAG, "discover", it) }.getOrDefault(emptyList())
    }

    /**
     * What's new this week on the user's services: series with an episode airing in the next
     * seven days, and movies that reached streaming in Brazil in the last two weeks.
     */
    suspend fun thisWeek(providerIds: Set<Int>): List<Title> = coroutineScope {
        if (providerIds.isEmpty()) return@coroutineScope emptyList()
        val today = LocalDate.now()
        val base = mapOf(
            "watch_region" to "BR",
            "with_watch_providers" to providerIds.joinToString("|"),
            "with_watch_monetization_types" to "flatrate",
            "sort_by" to "popularity.desc",
        )
        val series = async {
            runCatching {
                Parse.titles(
                    tmdb.get("discover/tv", base + mapOf(
                        "air_date.gte" to today.toString(),
                        "air_date.lte" to today.plusDays(7).toString(),
                        "vote_count.gte" to "30",
                    )),
                    MediaType.TV,
                )
            }.getOrDefault(emptyList())
        }
        val movies = async {
            runCatching {
                Parse.titles(
                    tmdb.get("discover/movie", base + mapOf(
                        "region" to "BR",
                        "with_release_type" to "4|6",
                        "release_date.gte" to today.minusDays(14).toString(),
                        "release_date.lte" to today.plusDays(7).toString(),
                    )),
                    MediaType.MOVIE,
                )
            }.getOrDefault(emptyList())
        }
        val s = series.await().filter { it.backdrop != null }
        val m = movies.await().filter { it.backdrop != null }
        (s.zip(m).flatMap { listOf(it.first, it.second) } + s.drop(m.size) + m.drop(s.size)).take(18)
    }

    /** Keeps items whose IMDb rating and vote count clear the bar (and, for movies, the runtime). */
    fun passes(item: Item, filters: Filters, minVotes: Int = 3000): Boolean {
        val min = filters.minImdb
        if (min != null && (item.imdb == null || item.imdb.rating < min || item.imdb.votes < minVotes)) return false
        val runtime = item.details.runtime
        if (item.title.type == MediaType.MOVIE && runtime != null) {
            filters.maxRuntime?.let { if (runtime > it) return false }
            filters.minRuntime?.let { if (runtime < it) return false }
        }
        return true
    }

    suspend fun search(query: String): List<Title> = runCatching {
        Parse.titles(tmdb.get("search/multi", mapOf("query" to query, "include_adult" to "false")))
            .filter { it.poster != null || it.backdrop != null }
    }.getOrDefault(emptyList())

    suspend fun personId(name: String): Int? = runCatching {
        tmdb.get("search/person", mapOf("query" to name)).objects("results").firstOrNull()?.int("id")
    }.getOrNull()

    data class Person(val id: Int, val name: String, val photo: String?, val credits: List<Title>)

    suspend fun person(id: Int): Person? = runCatching {
        val json = tmdb.get("person/$id", mapOf("append_to_response" to "combined_credits"))
        val credits = json.obj("combined_credits")?.let { c ->
            (c.objects("cast") + c.objects("crew").filter { it.str("job") == "Director" })
                .mapNotNull { Parse.title(it) }
        }.orEmpty().distinctBy { it.key }.sortedByDescending { it.tmdbVotes }
        Person(id, json.str("name") ?: "", json.str("profile_path"), credits)
    }.onFailure { Log.w(TAG, "person $id", it) }.getOrNull()

    companion object { private const val TAG = "Catalog" }
}
