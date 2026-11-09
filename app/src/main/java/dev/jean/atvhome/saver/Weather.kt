package dev.jean.atvhome.saver

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

data class Day(val label: String, val icon: String, val max: Int, val min: Int)
data class Forecast(val city: String?, val now: Int?, val nowIcon: String, val days: List<Day>)

/**
 * The week's weather with no account or key: the city comes from the TV's public IP (ipwho.is), the
 * forecast from Open-Meteo. Location is cached for a day, the forecast for an hour.
 */
class Weather(private val context: Context) {

    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val pt = Locale("pt", "BR")

    suspend fun forecast(): Forecast? = withContext(Dispatchers.IO) {
        runCatching {
            val (lat, lon, city) = location() ?: return@runCatching null
            val cache = File(context.cacheDir, "weather.json")
            val text = if (cache.exists() && System.currentTimeMillis() - cache.lastModified() < 3_600_000) cache.readText()
            else get(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                    "&timezone=auto&forecast_days=7",
            ).also { cache.writeText(it) }
            parse(json.parseToJsonElement(text) as JsonObject, city)
        }.getOrNull()
    }

    private fun location(): Triple<Double, Double, String?>? {
        val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
        val fresh = System.currentTimeMillis() - prefs.getLong("at", 0) < 86_400_000
        if (fresh && prefs.contains("lat")) {
            return Triple(prefs.getFloat("lat", 0f).toDouble(), prefs.getFloat("lon", 0f).toDouble(), prefs.getString("city", null))
        }
        val o = json.parseToJsonElement(get("https://ipwho.is/")) as JsonObject
        val lat = (o["latitude"] as? JsonPrimitive)?.doubleOrNull ?: return null
        val lon = (o["longitude"] as? JsonPrimitive)?.doubleOrNull ?: return null
        val city = (o["city"] as? JsonPrimitive)?.content
        prefs.edit().putFloat("lat", lat.toFloat()).putFloat("lon", lon.toFloat()).putString("city", city)
            .putLong("at", System.currentTimeMillis()).apply()
        return Triple(lat, lon, city)
    }

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

    private fun get(url: String): String = http.newCall(Request.Builder().url(url).build()).execute().use {
        check(it.isSuccessful) { "HTTP ${it.code} em $url" }
        it.body!!.string()
    }

    companion object {
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
