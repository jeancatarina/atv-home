package dev.jean.atvhome.ui.components

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

/**
 * QR codes at their natural size: one pixel per module (about 33x33 for a room address). The old
 * 512x512 ARGB bitmap cost 1 MB; this one is under 5 KB and is scaled up with FilterQuality.None,
 * which keeps the squares sharp.
 */
object QrCode {

    /** The smallest matrix that holds [text], with no quiet zone (the caller draws a white frame). */
    fun modules(text: String): BitMatrix =
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))

    fun bitmap(text: String): Bitmap {
        val m = modules(text)
        val pixels = IntArray(m.width * m.height) { i -> if (m[i % m.width, i / m.width]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        return Bitmap.createBitmap(pixels, m.width, m.height, Bitmap.Config.ARGB_8888)
    }
}
