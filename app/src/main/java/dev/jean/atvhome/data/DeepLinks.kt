package dev.jean.atvhome.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Opens a title straight on its page inside the streaming app ("Assistir no Netflix" lands on the
 * series, not on Netflix's home). TMDB doesn't know each service's own id; Wikidata, open and
 * keyless, does, keyed by the IMDb id: Netflix ID (P1874), Prime Video (P8055), Disney+ movie
 * (P7595) and series (P7596), Apple TV movie (P9586) and show (P9751).
 */
class DeepLinks(cacheDir: File) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .cache(Cache(File(cacheDir, "wikidata"), 5L * 1024 * 1024))
        .addNetworkInterceptor { chain ->
            chain.proceed(chain.request()).newBuilder().header("Cache-Control", "public, max-age=${7 * 86400}").build()
        }
        .build()
    private val memory = ConcurrentHashMap<String, Map<String, String>>()
    private val json = Json { ignoreUnknownKeys = true }

    /** Wikidata property → that service's id for [imdbId]; empty when Wikidata has none. */
    suspend fun ids(imdbId: String): Map<String, String> = memory[imdbId] ?: withContext(Dispatchers.IO) {
        val query = """
            SELECT ?p ?v WHERE {
              ?item wdt:P345 "$imdbId" .
              VALUES ?p { wdt:P1874 wdt:P8055 wdt:P7595 wdt:P7596 wdt:P9586 wdt:P9751 }
              ?item ?p ?v .
            }
        """.trimIndent()
        val url = "https://query.wikidata.org/sparql".toHttpUrl().newBuilder()
            .addQueryParameter("query", query).addQueryParameter("format", "json").build()
        runCatching {
            http.newCall(
                Request.Builder().url(url).header("User-Agent", "Inicio-TV/1.0 (personal Android TV launcher)").build(),
            ).execute().use { response ->
                val root = json.parseToJsonElement(response.body?.string().orEmpty()) as JsonObject
                root.obj("results")?.objects("bindings").orEmpty().mapNotNull { b ->
                    val p = b.obj("p")?.str("value")?.substringAfterLast('/') ?: return@mapNotNull null
                    val v = b.obj("v")?.str("value") ?: return@mapNotNull null
                    p to v
                }.toMap()
            }
        }.onFailure { Log.w(TAG, "Wikidata $imdbId", it) }.getOrDefault(emptyMap())
            .also { memory[imdbId] = it }
    }

    /**
     * The intent that opens [type] title on its own page in [packageName], or null when we don't
     * know that service's id for it (the caller then just opens the app).
     */
    fun intent(packageName: String, providerName: String, type: MediaType, ids: Map<String, String>, name: String): Intent? {
        val p = ProviderMatch.normalize(providerName)
        val q = Uri.encode(name)
        val uri = when {
            "netflix" in p -> ids["P1874"]?.let { "https://www.netflix.com/title/$it" }
            "prime" in p || "amazon" in p -> ids["P8055"]?.let { "https://app.primevideo.com/detail?gti=$it" }
            "disney" in p -> when (type) {
                MediaType.MOVIE -> ids["P7595"]?.let { "https://www.disneyplus.com/movies/_/$it" }
                MediaType.TV -> ids["P7596"]?.let { "https://www.disneyplus.com/series/_/$it" }
            }
            "apple" in p -> when (type) {
                MediaType.MOVIE -> ids["P9586"]?.let { "https://tv.apple.com/movie/_/$it" }
                MediaType.TV -> ids["P9751"]?.let { "https://tv.apple.com/show/_/$it" }
            }
            // No public ids for these two, but both open their search from a link
            "globo" in p -> "https://globoplay.globo.com/busca/$q/"
            "hbo" in p || p == "max" -> "https://play.max.com/search?q=$q"
            // Claro tv+ ships its deep links switched off (only Claro's own remote uses them)
            else -> null
        } ?: return null
        return Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .setPackage(packageName)
            .putExtra("source", "30") // Netflix's code for "launched from the TV launcher"
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    fun open(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent) }.onFailure { Log.w(TAG, "deep link", it) }.isSuccess

    companion object { private const val TAG = "DeepLinks" }
}
