package dev.jean.atvhome.guard

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.util.Log
import android.view.View
import android.view.WindowManager

/**
 * The dark screen shown over an ad break: a TYPE_ACCESSIBILITY_OVERLAY window, above any app and
 * without extra permissions. Never takes focus or touches: the remote stays with the app. The
 * screen capture sees the cover too, so it leaves holes over the ad badge and the countdown (the
 * screen reader must see the break end) and can turn invisible for one frame ([peek]).
 */
class Cover(private val service: AccessibilityService) {

    private val windows = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var shade: Shade? = null

    /** [keep]: labels left uncovered so the screen reader still sees them. */
    fun show(keep: List<Label>) {
        val view = shade ?: Shade(service).also { created ->
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            )
            created.alpha = 0f
            runCatching { windows.addView(created, params) }.onFailure { Log.w(TAG, "show", it); return }
            shade = created
            // A short fade in; on the way out the film comes back at once (hide)
            created.animate().alpha(1f).setDuration(FADE_MS).start()
        }
        if (view.keep != keep) { view.keep = keep; view.invalidate() }
    }

    /** Invisible for a moment (still attached), so one captured frame shows the whole screen. */
    fun peek(on: Boolean) {
        shade?.let { it.animate().cancel(); it.alpha = if (on) 0f else 1f }
    }

    fun hide() {
        shade?.let { it.animate().cancel(); runCatching { windows.removeView(it) }.onFailure { e -> Log.w(TAG, "hide", e) } }
        shade = null
    }

    private class Shade(context: Context) : View(context) {
        var keep: List<Label> = emptyList()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt(); textAlign = Paint.Align.CENTER }

        override fun onDraw(canvas: Canvas) {
            canvas.save()
            for (l in keep) {
                canvas.clipOutRect(
                    (l.x - l.w / 2 - PAD) * width, (l.y - l.h / 2 - PAD) * height,
                    (l.x + l.w / 2 + PAD) * width, (l.y + l.h / 2 + PAD) * height,
                )
            }
            canvas.drawColor(Color.BLACK)
            canvas.restore()
            // In the middle of the screen on purpose: outside the bands the OCR reads
            paint.textSize = height * 0.05f
            canvas.drawText("Intervalo", width / 2f, height / 2f, paint)
        }
    }

    private companion object {
        const val TAG = "AdGuard"
        const val PAD = 0.02f
        const val FADE_MS = 150L
    }
}
