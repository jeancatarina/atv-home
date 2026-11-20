package dev.jean.atvhome.saver

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import dev.jean.atvhome.tuya.Floor
import dev.jean.atvhome.tuya.Kind
import dev.jean.atvhome.tuya.Light
import dev.jean.atvhome.tuya.Plan
import dev.jean.atvhome.tuya.RoomBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HouseStripTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private val plan = Plan(
        width = 7f,
        depth = 12f,
        floors = listOf(
            Floor("1º andar", listOf(RoomBox("Sala", 0f, 0f, 7f, 6f), RoomBox("Cozinha", 0f, 6f, 7f, 6f))),
            Floor("2º andar", listOf(RoomBox("Quarto", 0f, 0f, 7f, 6f), RoomBox("Escritório", 0f, 6f, 7f, 6f))),
            Floor("Terraço", listOf(RoomBox("Terraço", 0f, 0f, 7f, 12f)), outdoor = true),
        ),
    )

    private val lights = listOf(
        Light("a", listOf(1), "Sofá", "Sala", "Estar"),
        Light("a", listOf(2), "Bancada", "Cozinha", "Estar"),
        Light("a", listOf(3), "Cena Cinema", "Sala", "Estar", kind = Kind.SCENE),
        Light("b", listOf(1), "Abajur", "Quarto", "Quarto"),
        Light("b", listOf(2), "Mesa", "Escritório", "Escritório"),
        Light("b", listOf(3), "Sobra", "Escritório", "Escritório", kind = Kind.SPARE),
        Light("c", listOf(1), "Deck", "Terraço", "Terraço"),
    )

    @Test fun `summary orders floors top-down and counts only real lit lights per floor`() {
        val states = mapOf(
            "a:1" to true,
            "a:2" to false,
            "a:3" to true, // scene must not inflate lit light count
            "b:1" to true,
            "b:2" to true,
            "b:3" to true, // spare must not inflate lit light count
            "c:1" to false,
        )
        val summary = HouseSummary.from(plan, lights, states)
        assertEquals(3, summary.totalLit)
        assertEquals(
            listOf(
                FloorStatus("Terraço", lit = 0, total = 1, litRooms = emptyList()),
                FloorStatus("2º andar", lit = 2, total = 2, litRooms = listOf("Quarto", "Escritório")),
                FloorStatus("1º andar", lit = 1, total = 2, litRooms = listOf("Sala")),
            ),
            summary.floors,
        )
        assertEquals(null, summary.floors[0].roomHint())
        assertEquals("Quarto · Escritório", summary.floors[1].roomHint())
        assertEquals("Sala", summary.floors[2].roomHint())
        assertEquals("A · B +1", FloorStatus("X", 3, 3, listOf("A", "B", "C")).roomHint())
    }

    @Test fun `floor lights view measures and draws cleanly with lit and dark transitions`() {
        val view = FloorLightsView(context)
        val initial = HouseSummary.from(plan, lights, mapOf("a:1" to true, "b:1" to true, "b:2" to true))
        view.update(initial)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.AT_MOST),
        )
        assertTrue(view.measuredWidth > 100)
        assertTrue(view.measuredHeight > 60)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val bmp = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))

        // Update with all lights off and draw again
        view.update(HouseSummary.from(plan, lights, emptyMap()))
        view.draw(Canvas(bmp))
        view.stopAnimations()
    }
}
