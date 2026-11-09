package dev.jean.atvhome.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import coil3.size.Size
import coil3.transform.Transformation

/**
 * Bakes tvOS's text scrim into the backdrop bitmap once, off the main thread: dark on the left
 * where the title sits and at the bottom under the rows. Drawn live, the two full-screen
 * gradients made every pixel paint four times and the TV's GPU missed most frames.
 */
object Scrim : Transformation() {

    override val cacheKey = "scrim-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val out = input.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, w * 0.75f, 0f,
            intArrayOf(0xE0000000.toInt(), 0x73000000, 0x00000000),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w * 0.75f, h, paint)
        paint.shader = LinearGradient(
            0f, h * 0.35f, 0f, h,
            intArrayOf(0x00000000, 0x8C000000.toInt(), 0xFF000000.toInt()),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, h * 0.35f, w, h, paint)
        return out
    }
}
