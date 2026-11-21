package dev.jean.atvhome.saver

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
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
 * The week's weather over the TV's own screensaver (Sony's "Decoração do Ambiente", Rain Forest
 * Clock): laid out as a compact two-tier block in the 235dp bottom-center pocket between Sony's
 * lower-left and lower-right clock corners and below its center clock, so it stays completely clear
 * of all 7 rotating Sony clock positions.
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
        val row = createContainer()
        // Centered in the 235dp pocket between Sony's lower_left (<=330dp) and lower_right (>=565dp) clocks
        val baseX = dp(-32f)
        val baseY = dp(22f)
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
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
        // Subtle pixel shift every 2 minutes within the clock-free corridor to prevent screen retention
        driftJob = scope.launch {
            val offsets = listOf(0 to 0, 2 to 1, -2 to 2, 1 to -1)
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

    internal fun createContainer(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false
        clipToPadding = false
        setPadding(dp(4f), dp(6f), dp(4f), dp(6f))
        background = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = dp(130f).toFloat()
            colors = intArrayOf(0x38000000, Color.TRANSPARENT)
        }
        // Nothing until the forecast is here: no empty frame over the film
        visibility = View.INVISIBLE
    }

    private fun fill(row: LinearLayout, f: Forecast) {
        val first = row.visibility != View.VISIBLE
        row.removeAllViews()

        // Tier 1: current temperature + whispered city
        val nowRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        nowRow.addView(text(24f, interBold, Color.WHITE).apply {
            text = listOfNotNull(f.nowIcon, f.now?.let { "$it°" }).joinToString(" ")
            fontFeatureSettings = "tnum"
        })
        f.city?.takeIf { it.isNotBlank() }?.let { city ->
            nowRow.addView(text(11f, inter, MUTED).apply {
                text = "  ·  "
            })
            nowRow.addView(text(9.5f, inter, SOFT).apply {
                text = city.uppercase()
                letterSpacing = 0.12f
                maxWidth = dp(120f)
                ellipsize = TextUtils.TruncateAt.END
            })
        }
        row.addView(nowRow)

        // Delicate hairline separating now from the 6-day outlook
        row.addView(View(context).apply {
            setBackgroundColor(DIVIDER)
            layoutParams = LinearLayout.LayoutParams(dp(156f), dp(1f).coerceAtLeast(1)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, dp(5f), 0, dp(7f))
            }
        })

        // Tier 2: compact 6-day row (6 * 34dp = 204dp, clear of both lower-corner clocks)
        val daysRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        f.days.drop(1).take(6).forEach { d ->
            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                clipChildren = false
                clipToPadding = false
                layoutParams = LinearLayout.LayoutParams(dp(34f), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            col.addView(text(10.5f, inter, SOFT).apply { text = d.label })
            col.addView(text(16f, inter, Color.WHITE).apply {
                text = d.icon
                setPadding(0, dp(1f), 0, dp(1f))
            })
            col.addView(text(13f, interBold, Color.WHITE).apply {
                text = "${d.max}°"
                fontFeatureSettings = "tnum"
            })
            col.addView(text(10.5f, inter, MUTED).apply {
                text = "${d.min}°"
                fontFeatureSettings = "tnum"
            })
            daysRow.addView(col)
        }
        row.addView(daysRow)
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
