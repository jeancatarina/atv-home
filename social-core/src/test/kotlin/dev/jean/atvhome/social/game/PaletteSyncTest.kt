package dev.jean.atvhome.social.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The TV and the phone page must give a person the same colour and shape. Reads app.js as data. */
class PaletteSyncTest {

    private val js = File("../app/src/main/assets/social/web/app.js").also { assertTrue("app.js not found from ${File(".").absolutePath}", it.isFile) }.readText()

    private fun listIn(name: String): List<String> =
        Regex("const $name = \\[(.*?)\\];", RegexOption.DOT_MATCHES_ALL).find(js)!!.groupValues[1]
            .split(',').map { it.trim().trim('\'') }.filter { it.isNotEmpty() }

    @Test fun colorsAreTheSame() = assertEquals(Palette.colors, listIn("COLORS"))

    @Test fun shapesAreTheSame() = assertEquals(Palette.shapes, listIn("SHAPES"))

    @Test fun roomSlotsUseEveryColourBeforeRepeating() = assertEquals(
        "Room hands out colours by slot; there must be as many as seats' colours",
        dev.jean.atvhome.social.room.Room.COLORS, Palette.colors.size,
    )
}
