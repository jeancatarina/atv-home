package dev.jean.atvhome.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.jean.atvhome.tuya.HomeConfig
import dev.jean.atvhome.tuya.Hub
import dev.jean.atvhome.tuya.Kind
import dev.jean.atvhome.tuya.Light
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The house on the TV's storage: the pushed tuya.json, and the row's choices across setups. Never connects. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SmartHomeTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val cozinha = Light("e", listOf(1), "Luz cozinha", "Sala de Estar", "Estar")
    private val sala = Light("e", listOf(2), "Luz sala", "Sala de Estar", "Estar")
    private val cena = Light("h", listOf(3), "Cena Hall", "Corredor", "Hall", load = false, kind = Kind.SCENE)

    private fun push(vararg lights: Light) {
        val dir = context.getExternalFilesDir(null)!!.apply { mkdirs() }
        File(dir, "tuya.json").writeText(HomeConfig(Hub("hub", "0123456789abcdef", "127.0.0.1", "3.3"), lights.toList()).encode())
    }

    @Before fun clean() {
        File(context.filesDir, "tuya.json").delete()
        context.getSharedPreferences("casa", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `no house, no Casa`() {
        assertNull(SmartHome.forLauncher(context))
    }

    @Test fun `the pushed file moves to private storage`() {
        push(cozinha, sala)
        val home = SmartHome.forLauncher(context)!!
        assertEquals(listOf(cozinha, sala), home.lights)
        assertFalse(File(context.getExternalFilesDir(null), "tuya.json").exists())
        home.close()
    }

    @Test fun `the row's choices survive the same setup and start over with new lights`() {
        push(cozinha, sala, cena)
        val first = SmartHome.forLauncher(context)!!
        assertEquals(listOf(cozinha, sala), first.row.value) // the scene stays off by default
        first.setHidden(sala, true)
        first.close()

        push(cozinha, sala, cena)
        val again = SmartHome.forLauncher(context)!!
        assertEquals(listOf(cozinha), again.row.value)
        again.close()

        val renamed = sala.copy(name = "Luz da sala")
        push(cozinha, renamed, cena)
        val changed = SmartHome.forLauncher(context)!!
        assertEquals(listOf(cozinha, renamed), changed.row.value)
        changed.close()
    }
}
