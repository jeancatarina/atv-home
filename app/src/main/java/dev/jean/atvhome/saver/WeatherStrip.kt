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
 * on the film, no panel, a little see-through ([OPACITY]), in the bottom-left corner clear of the TV's
 * right-side clock; a soft shadow keeps it readable.
 *
 * Shown by [dev.jean.atvhome.casa.HouseKeyService] while the system dreams, in an accessibility
 * overlay window (above the dream, never focusable or touchable: any key still wakes the TV).
 */
class WeatherStrip(private val context: Context) {

    private val wm = context.getSystemService(WindowManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var view: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var job: Job? = null
    private var driftJob: Job? = null
    private val inter by lazy { ResourcesCompat.getFont(context, R.font.inter_medium) ?: Typeface.DEFAULT }
    private val interBold by lazy { ResourcesCompat.getFont(context, R.font.inter_semibold) ?: Typeface.DEFAULT_BOLD }

    fun show() {
        if (view != null) return
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            clipChildren = false
            clipToPadding = false
            setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
            // Nothing until the forecast is here: no empty frame over the film
            visibility = View.INVISIBLE
        }
        val baseX = dp(46f)
        val baseY = dp(40f)
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = baseX
            y = baseY
        }
        runCatching { wm.addView(row, layoutParams) }.onFailure { Log.w(TAG, "add", it); return }
        view = row
        params = layoutParams
        job = scope.launch {
            var retries = 0
            while (isActive) {
                val forecast = Weather(context).forecast()
                if (forecast != null) {
                    fill(row, forecast)
                    retries = 0
                    delay(30 * 60_000L)
                } else {
                    retries++
                    delay(if (retries <= 4) 15_000L else 60_000L)
                }
            }
        }
        // Subtle pixel shift every 2 minutes to prevent OLED/LCD retention on long screensavers
        driftJob = scope.launch {
            val offsets = listOf(0 to 0, 3 to 2, 1 to 3, -2 to 2)
            var step = 0
            while (isActive) {
                delay(120_000L)
                val (dx, dy) = offsets[++step % offsets.size]
                layoutParams.x = baseX + dp(dx.toFloat())
                layoutParams.y = baseY + dp(dy.toFloat())
                if (row.isAttachedToWindow) runCatching { wm.updateViewLayout(row, layoutParams) }
            }
        }
    }

    fun hide() {
        job?.cancel()
        job = null
        driftJob?.cancel()
        driftJob = null
        view?.let { v -> runCatching { wm.removeView(v) } }
        view = null
        params = null
    }

    private fun fill(row: LinearLayout, f: Forecast) {
        val first = row.visibility != View.VISIBLE
        row.removeAllViews()

        // Current temperature + whispered city name
        val nowCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            setPadding(0, 0, dp(20f), dp(2f))
        }
        nowCol.addView(text(34f, interBold, Color.WHITE).apply {
            text = listOfNotNull(f.nowIcon, f.now?.let { "$it°" }).joinToString(" ")
            fontFeatureSettings = "tnum"
        })
        f.city?.takeIf { it.isNotBlank() }?.let { city ->
            nowCol.addView(text(10f, inter, MUTED).apply {
                text = city.uppercase()
                letterSpacing = 0.12f
                setPadding(dp(2f), dp(2f), 0, 0)
            })
        }
        row.addView(nowCol)

        // Delicate hairline separating now from the week ahead
        row.addView(View(context).apply {
            setBackgroundColor(DIVIDER)
            layoutParams = LinearLayout.LayoutParams(dp(1f).coerceAtLeast(1), dp(36f)).apply {
                gravity = Gravity.CENTER_VERTICAL
                setMargins(0, 0, dp(10f), dp(4f))
            }
        })

        f.days.drop(1).take(6).forEach { d ->
            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                clipChildren = false
                clipToPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                setPadding(dp(11f), 0, dp(11f), 0)
            }
            col.addView(text(12.5f, inter, SOFT).apply { text = d.label })
            col.addView(text(21f, inter, Color.WHITE).apply {
                text = d.icon
                setPadding(0, dp(2f), 0, dp(2f))
            })
            col.addView(text(15.5f, interBold, Color.WHITE).apply {
                text = "${d.max}°"
                fontFeatureSettings = "tnum"
            })
            col.addView(text(12.5f, inter, MUTED).apply {
                text = "${d.min}°"
                fontFeatureSettings = "tnum"
            })
            row.addView(col)
        }
        row.visibility = View.VISIBLE
        row.requestLayout()
        params?.let { p -> if (row.isAttachedToWindow) runCatching { wm.updateViewLayout(row, p) } }
        if (first) {
            row.alpha = 0f
            row.animate().alpha(OPACITY).setDuration(900).start()
        } else {
            row.alpha = OPACITY
        }
    }

    private fun text(sp: Float, face: Typeface, color: Int) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = face
        setTextColor(color)
        setShadowLayer(dp(6f).toFloat(), 0f, dp(1f).toFloat(), SHADOW)
        includeFontPadding = false
        isSingleLine = true
        maxLines = 1
        ellipsize = null
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()

    private companion object {
        const val TAG = "WeatherStrip"
        const val SOFT = 0xCCFFFFFF.toInt()
        const val MUTED = 0x99FFFFFF.toInt()
        const val DIVIDER = 0x33FFFFFF
        const val SHADOW = 0x80000000.toInt()
        /** Balanced translucency matching [HouseStrip] so leaves still show through. */
        const val OPACITY = 0.82f
    }
}
