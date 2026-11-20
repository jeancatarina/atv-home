package dev.jean.atvhome.saver

import android.content.Context
import dev.jean.atvhome.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

data class Day(val label: String, val icon: String, val max: Int, val min: Int)
data class Forecast(val city: String?, val now: Int?, val nowIcon: String, val days: List<Day>)

@Serializable
data class WeatherLocation(val city: String, val lat: Double, val lon: Double, val detail: String = "")

/**
 * The week's weather with no account or key: prefers a saved city (`weather_location.json`, shared
 * between `:main` and `:casa`) or a build-time location from `local.properties`, falling back to
 * the TV's public IP (ipwho.is / geojs.io). The forecast comes from Open-Meteo and is cached for
 * an hour keyed by coordinates so switching cities takes effect immediately.
 */
class Weather(
    private val context: Context,
    private val defaultLocation: WeatherLocation? = configuredLocation(),
    private val fetch: ((String) -> String)? = null,
) {

    private val pt = Locale("pt", "BR")

    suspend fun forecast(): Forecast? = withContext(Dispatchers.IO) {
        runCatching {
            val (lat, lon, city) = location() ?: return@runCatching null
            val cache = File(context.cacheDir, CACHE_FILE)
            val locFile = File(context.cacheDir, CACHE_LOC_FILE)
            val locKey = locKey(lat, lon)
            val matchesSavedLoc = locFile.exists() && locFile.readText() == locKey
            val fresh = cache.exists() && matchesSavedLoc && System.currentTimeMillis() - cache.lastModified() < 3_600_000
            val text = if (fresh) {
                cache.readText()
            } else {
                runCatching {
                    get(
                        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                            "&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                            "&timezone=auto&forecast_days=7",
                        fetch,
                    ).also {
                        cache.writeText(it)
                        locFile.writeText(locKey)
                    }
                }.getOrElse {
                    // Only serve stale cache if it belongs to these coordinates (or legacy cache with no loc tag)
                    if (cache.exists() && (!locFile.exists() || matchesSavedLoc)) cache.readText() else throw it
                }
            }
            parse(json.parseToJsonElement(text) as JsonObject, city)
        }.getOrNull()
    }

    private fun location(): Triple<Double, Double, String?>? {
        savedLocation(context)?.let { return Triple(it.lat, it.lon, it.city) }
        defaultLocation?.let { return Triple(it.lat, it.lon, it.city) }
        val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
        val saved = if (prefs.contains("lat") && prefs.contains("lon")) {
            Triple(prefs.getFloat("lat", 0f).toDouble(), prefs.getFloat("lon", 0f).toDouble(), prefs.getString("city", null))
        } else null
        val fresh = System.currentTimeMillis() - prefs.getLong("at", 0) < 86_400_000
        if (fresh && saved != null) return saved
        val resolved = fetchLocation("https://ipwho.is/") ?: fetchLocation("https://get.geojs.io/v1/ip/geo.json")
        if (resolved != null) {
            val (lat, lon, city) = resolved
            prefs.edit().putFloat("lat", lat.toFloat()).putFloat("lon", lon.toFloat()).putString("city", city)
                .putLong("at", System.currentTimeMillis()).apply()
            return resolved
        }
        return saved
    }

    private fun fetchLocation(url: String): Triple<Double, Double, String?>? = runCatching {
        val o = json.parseToJsonElement(get(url, fetch)) as JsonObject
        fun num(k: String) = (o[k] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }
        val lat = num("latitude") ?: return@runCatching null
        val lon = num("longitude") ?: return@runCatching null
        val city = (o["city"] as? JsonPrimitive)?.content
        Triple(lat, lon, city)
    }.getOrNull()

    private fun parse(o: JsonObject, city: String?): Forecast {
        val current = o["current"] as? JsonObject
        val daily = o["daily"] as JsonObject
        fun arr(k: String) = (daily[k] as JsonArray).map { it as JsonPrimitive }
        val dates = arr("time").map { LocalDate.parse(it.content) }
        val codes = arr("weather_code").map { it.intOrNull ?: 0 }
        val max = arr("temperature_2m_max").map { it.doubleOrNull ?: 0.0 }
        val min = arr("temperature_2m_min").map { it.doubleOrNull ?: 0.0 }
        val today = LocalDate.now()
        val days = dates.indices.map { i ->
            val label = if (dates[i] == today) "Hoje"
            else dates[i].dayOfWeek.getDisplayName(TextStyle.SHORT, pt).replace(".", "").replaceFirstChar { it.uppercase() }
            Day(label, icon(codes[i]), Math.round(max[i]).toInt(), Math.round(min[i]).toInt())
        }
        return Forecast(
            city = city,
            now = (current?.get("temperature_2m") as? JsonPrimitive)?.doubleOrNull?.let { Math.round(it).toInt() },
            nowIcon = icon((current?.get("weather_code") as? JsonPrimitive)?.intOrNull ?: codes.firstOrNull() ?: 0),
            days = days,
        )
    }

    companion object {
        private const val LOCATION_FILE = "weather_location.json"
        private const val CACHE_FILE = "weather.json"
        private const val CACHE_LOC_FILE = "weather.loc"
        private val json = Json { ignoreUnknownKeys = true }
        private val http by lazy { OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build() }

        fun configuredLocation(): WeatherLocation? {
            val city = BuildConfig.WEATHER_CITY.trim()
            val lat = BuildConfig.WEATHER_LAT.toDoubleOrNull()
            val lon = BuildConfig.WEATHER_LON.toDoubleOrNull()
            return if (city.isNotEmpty() && lat != null && lon != null) WeatherLocation(city, lat, lon) else null
        }

        fun savedLocation(context: Context): WeatherLocation? = runCatching {
            val f = File(context.filesDir, LOCATION_FILE)
            if (!f.isFile) null else json.decodeFromString<WeatherLocation>(f.readText())
        }.getOrNull()

        fun currentCity(context: Context, defaultLocation: WeatherLocation? = configuredLocation()): String? =
            savedLocation(context)?.city
                ?: defaultLocation?.city
                ?: context.getSharedPreferences("weather", Context.MODE_PRIVATE).getString("city", null)

        fun saveLocation(context: Context, location: WeatherLocation?) {
            val f = File(context.filesDir, LOCATION_FILE)
            runCatching {
                if (location == null) f.delete() else f.writeText(json.encodeToString(location.copy(detail = "")))
                File(context.cacheDir, CACHE_FILE).delete()
                File(context.cacheDir, CACHE_LOC_FILE).delete()
            }
        }

        suspend fun searchCity(query: String, fetch: ((String) -> String)? = null): List<WeatherLocation> = withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.length < 2) return@withContext emptyList()
            runCatching {
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                val body = get("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=5&language=pt&format=json", fetch)
                val results = (json.parseToJsonElement(body) as? JsonObject)?.get("results") as? JsonArray ?: return@runCatching emptyList()
                results.mapNotNull { item ->
                    val o = item as? JsonObject ?: return@mapNotNull null
                    val name = (o["name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val lat = (o["latitude"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                    val lon = (o["longitude"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                    val region = listOfNotNull(
                        (o["admin1"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() },
                        (o["country_code"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() },
                    ).joinToString(", ")
                    WeatherLocation(city = name, lat = lat, lon = lon, detail = region)
                }
            }.getOrDefault(emptyList())
        }

        private fun get(url: String, fetch: ((String) -> String)?): String =
            fetch?.invoke(url) ?: http.newCall(Request.Builder().url(url).build()).execute().use {
                check(it.isSuccessful) { "HTTP ${it.code} em $url" }
                it.body!!.string()
            }

        private fun locKey(lat: Double, lon: Double) = String.format(Locale.US, "%.4f,%.4f", lat, lon)

        /** WMO weather codes (Open-Meteo) as one glyph each. */
        fun icon(code: Int): String = when (code) {
            0 -> "☀️"
            1, 2 -> "⛅"
            3 -> "☁️"
            45, 48 -> "🌫️"
            in 51..57 -> "🌦️"
            in 61..67, in 80..82 -> "🌧️"
            in 71..77, 85, 86 -> "❄️"
            in 95..99 -> "⛈️"
            else -> "☁️"
        }
    }
}
