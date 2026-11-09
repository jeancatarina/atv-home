package dev.jean.atvhome.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class TmdbException(message: String) : IOException(message)

/**
 * Thin TMDB v3 client (read token, Bearer auth). Responses are cached on disk by OkHttp: TMDB
 * itself sends short max-ages, so a network interceptor stamps ours: lists for 6 hours, title
 * details, genres and providers for 7 days.
 */
class TmdbClient(private val token: String, cacheDir: File) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .cache(Cache(File(cacheDir, "tmdb"), 40L * 1024 * 1024))
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            val path = request.url.encodedPath
            val maxAge = when {
                path.contains("/trending/") || path.contains("/discover/") || path.contains("/search/") -> 6 * 3600
                else -> 7 * 86400
            }
            response.newBuilder()
                .removeHeader("Pragma")
                .header("Cache-Control", "public, max-age=$maxAge")
                .build()
        }
        .build()

    val hasToken get() = token.isNotBlank()

    suspend fun get(path: String, params: Map<String, String> = emptyMap()): JsonObject =
        withContext(Dispatchers.IO) {
            if (!hasToken) throw TmdbException("Sem token do TMDB em local.properties (tmdb.token)")
            val url = StringBuilder(API_URL).append(path).append('?')
            (mapOf("language" to "pt-BR") + params).entries.joinTo(url, "&") { (k, v) ->
                "$k=${java.net.URLEncoder.encode(v, "UTF-8")}"
            }
            val request = Request.Builder().url(url.toString())
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "TMDB $path -> HTTP ${response.code}")
                    throw TmdbException("TMDB respondeu ${response.code} em $path")
                }
                json.parseToJsonElement(body) as? JsonObject ?: throw TmdbException("Resposta inesperada do TMDB em $path")
            }
        }

    companion object {
        private const val TAG = "Tmdb"
        private const val API_URL = "https://api.themoviedb.org/3/"
        private const val IMAGE_URL = "https://image.tmdb.org/t/p/"
        private val json = Json { ignoreUnknownKeys = true }

        fun image(path: String?, size: String): String? = path?.let { "$IMAGE_URL$size$it" }
    }
}

// --- JSON helpers -----------------------------------------------------------------------------

fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? JsonArray) ?: emptyList()
fun JsonObject.objects(key: String): List<JsonObject> = array(key).filterIsInstance<JsonObject>()
