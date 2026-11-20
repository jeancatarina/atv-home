package dev.jean.atvhome.saver

import android.app.Application
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WeatherTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `cached location and forecast survive offline or geo-ip failure`() = runBlocking {
        Weather.saveLocation(context, null)
        // Seed stale location (>24h) and stale weather.json (>1h) and simulate all network calls failing
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putFloat("lat", -23.55f)
            .putFloat("lon", -46.63f)
            .putString("city", "São Paulo")
            .putLong("at", 1L)
            .commit()
        val cache = File(context.cacheDir, "weather.json")
        cache.writeText(
            """
            {
              "current": {"temperature_2m": 24.4, "weather_code": 1},
              "daily": {
                "time": ["2026-11-20","2026-11-21","2026-11-22","2026-11-23","2026-11-24","2026-11-25","2026-11-26"],
                "weather_code": [1, 0, 3, 61, 2, 0, 95],
                "temperature_2m_max": [26.2, 28.0, 22.1, 19.8, 25.4, 29.1, 27.0],
                "temperature_2m_min": [16.4, 17.0, 15.2, 14.1, 16.0, 18.2, 17.5]
              }
            }
            """.trimIndent(),
        )
        cache.setLastModified(1L)
        val forecast = Weather(context, defaultLocation = null) { throw java.io.IOException("offline") }.forecast()
        assertNotNull(forecast)
        assertEquals("São Paulo", forecast!!.city)
        assertEquals(24, forecast.now)
        assertEquals(7, forecast.days.size)
    }

    @Test fun `geo-ip falls back to secondary provider when ipwho fails`() = runBlocking {
        Weather.saveLocation(context, null)
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.cacheDir, "weather.json").delete()
        val forecast = Weather(context, defaultLocation = null) { url ->
            when {
                "ipwho.is" in url -> """{"success":false}"""
                "geojs.io" in url -> """{"latitude":"-23.55","longitude":"-46.63","city":"Campinas"}"""
                else -> """{"current":{"temperature_2m":21.0,"weather_code":0},"daily":{"time":["2026-11-20","2026-11-21"],"weather_code":[0,1],"temperature_2m_max":[25.0,26.0],"temperature_2m_min":[15.0,16.0]}}"""
            }
        }.forecast()
        assertNotNull(forecast)
        assertEquals("Campinas", forecast!!.city)
        assertEquals(21, forecast.now)
    }

    @Test fun `saved and configured locations override IP geo and invalidate old city cache`() = runBlocking {
        Weather.saveLocation(context, null)
        File(context.cacheDir, "weather.json").writeText(
            """{"current":{"temperature_2m":31.0,"weather_code":0},"daily":{"time":["2026-11-20","2026-11-21"],"weather_code":[0,0],"temperature_2m_max":[33.0,33.0],"temperature_2m_min":[22.0,22.0]}}""",
        )
        val configured = WeatherLocation("Joinville", -26.30444, -48.84556)
        var requestedUrl = ""
        val forecast = Weather(context, defaultLocation = configured) { url ->
            requestedUrl = url
            """{"current":{"temperature_2m":22.0,"weather_code":2},"daily":{"time":["2026-11-20","2026-11-21"],"weather_code":[2,1],"temperature_2m_max":[25.0,24.0],"temperature_2m_min":[17.0,16.0]}}"""
        }.forecast()
        assertNotNull(forecast)
        assertEquals("Joinville", forecast!!.city)
        assertEquals(22, forecast.now)
        assertTrue(requestedUrl.contains("-26.30444") && requestedUrl.contains("-48.84556"))

        // Explicit user choice overrides configured build location too
        Weather.saveLocation(context, WeatherLocation("Florianópolis", -27.59667, -48.54917))
        assertEquals("Florianópolis", Weather.currentCity(context, defaultLocation = configured))
        Weather.saveLocation(context, null)
    }

    @Test fun `city search parses open-meteo geocoding results`() = runBlocking {
        val matches = Weather.searchCity("Joinville") {
            """{"results":[{"name":"Joinville","latitude":-26.30444,"longitude":-48.84556,"admin1":"Santa Catarina","country_code":"BR"}]}"""
        }
        assertEquals(1, matches.size)
        assertEquals("Joinville", matches[0].city)
        assertEquals("Santa Catarina, BR", matches[0].detail)
        assertEquals(-26.30444, matches[0].lat, 0.0001)
        assertEquals(-48.84556, matches[0].lon, 0.0001)
    }

    @Test fun `weather strip sits bottom-start and keeps every label on a single line`() {
        val strip = WeatherStrip(context)
        val forecast = Forecast(
            city = "São Paulo",
            now = 24,
            nowIcon = "⛅",
            days = listOf(
                Day("Hoje", "⛅", 26, 16),
                Day("Sáb", "☀️", 28, 17),
                Day("Dom", "☁️", 22, 15),
                Day("Seg", "🌧️", 20, 14),
                Day("Ter", "⛅", 25, 16),
                Day("Qua", "☀️", 29, 18),
                Day("Qui", "⛈️", 27, 17),
            ),
        )
        val row = LinearLayout(context)
        val fillMethod = WeatherStrip::class.java.getDeclaredMethod("fill", LinearLayout::class.java, Forecast::class.java)
        fillMethod.isAccessible = true
        fillMethod.invoke(strip, row, forecast)

        val texts = collectTextViews(row)
        assertTrue(texts.isNotEmpty())
        assertTrue(texts.all { it.maxLines == 1 })

        // Also verify show() places the overlay at BOTTOM | START (away from Sony's right-side clock)
        strip.show()
        val paramsField = WeatherStrip::class.java.getDeclaredField("params")
        paramsField.isAccessible = true
        val params = paramsField.get(strip) as WindowManager.LayoutParams
        assertEquals(Gravity.BOTTOM or Gravity.START, params.gravity)
        strip.hide()
    }

    private fun collectTextViews(v: View): List<TextView> = when (v) {
        is TextView -> listOf(v)
        is ViewGroup -> (0 until v.childCount).flatMap { collectTextViews(v.getChildAt(it)) }
        else -> emptyList()
    }
}
