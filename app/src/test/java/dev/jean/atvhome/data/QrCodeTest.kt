package dev.jean.atvhome.data

import dev.jean.atvhome.ui.components.QrCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeTest {

    @Test fun aRoomLinkIsAboutThirtyModulesWide() {
        val m = QrCode.modules("http://192.168.100.100:8766/#k=dzao4Mn8amf2BSncWydBQA")
        assertEquals("a square", m.width, m.height)
        assertTrue("small enough to be 1 pixel per module (was a 512 px bitmap): ${m.width}", m.width in 21..45)
    }

    @Test fun theMatrixIsNotEmptyAndHasTheThreeFinderSquares() {
        val m = QrCode.modules("http://192.168.1.3:8765/")
        fun dark(x: Int, y: Int) = m[x, y]
        assertTrue(dark(0, 0) && dark(m.width - 1, 0) && dark(0, m.height - 1))
        assertEquals("no quiet zone: the caller draws the white frame", true, dark(0, 0))
    }
}
