package dev.jean.atvhome.saver

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import dev.jean.atvhome.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The week's weather over the TV's own screensaver (Sony's "Decoração do Ambiente", the monstera
 * leaves in the water): it writes the time over its film but has no weather for Brazil. Written straight
 * on the film, no panel, in the bottom-right corner under its clock; a soft shadow keeps it readable.
 *
 * Shown by [dev.jean.atvhome.casa.HouseKeyService] while the system dreams, in an accessibility
 * overlay window (above the dream, never focusable or touchable: any key still wakes the TV).
 */
class WeatherStrip(private val context: Context) {

    private val wm = context.getSystemService(WindowManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var view: LinearLayout? = null
    private var job: Job? = null
    private val inter by lazy { ResourcesCompat.getFont(context, R.font.inter_medium) ?: Typeface.DEFAULT }
    private val interBold by lazy { ResourcesCompat.getFont(context, R.font.inter_semibold) ?: Typeface.DEFAULT_BOLD }

    fun show() {
        if (view != null) return
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            // Nothing until the forecast is here: no empty frame over the film
            visibility = View.INVISIBLE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(46f)
            y = dp(40f)
        }
        runCatching { wm.addView(row, params) }.onFailure { Log.w(TAG, "add", it); return }
        view = row
        job = scope.launch {
            while (isActive) {
                Weather(context).forecast()?.let { fill(row, it) }
                delay(30 * 60_000L)
            }
        }
    }

    fun hide() {
        job?.cancel()
        job = null
        view?.let { v -> runCatching { wm.removeView(v) } }
        view = null
    }

    private fun fill(row: LinearLayout, f: Forecast) {
        row.removeAllViews()
        row.addView(text(36f, interBold, Color.WHITE).apply {
            text = listOfNotNull(f.nowIcon, f.now?.let { "$it°" }).joinToString(" ")
            setPadding(0, 0, dp(26f), dp(6f))
        })
        f.days.drop(1).take(6).forEach { d ->
            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(12f), 0, dp(12f), 0)
            }
            col.addView(text(14f, inter, SOFT).apply { text = d.label })
            col.addView(text(22f, inter, Color.WHITE).apply { text = d.icon })
            col.addView(text(16f, interBold, Color.WHITE).apply { text = "${d.max}°" })
            col.addView(text(13f, inter, SOFT).apply { text = "${d.min}°" })
            row.addView(col)
        }
        row.visibility = View.VISIBLE
        row.alpha = 0f
        row.animate().alpha(1f).setDuration(900).start()
    }

    private fun text(sp: Float, face: Typeface, color: Int) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = face
        setTextColor(color)
        setShadowLayer(dp(6f).toFloat(), 0f, dp(1f).toFloat(), SHADOW)
        includeFontPadding = false
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()

    private companion object {
        const val TAG = "WeatherStrip"
        const val SOFT = 0xD9FFFFFF.toInt()
        const val SHADOW = 0xB3000000.toInt()
    }
}
