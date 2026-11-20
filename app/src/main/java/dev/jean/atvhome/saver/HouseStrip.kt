package dev.jean.atvhome.saver

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.core.content.res.ResourcesCompat
import dev.jean.atvhome.R
import dev.jean.atvhome.data.SmartHome
import dev.jean.atvhome.tuya.Kind
import dev.jean.atvhome.tuya.Light
import dev.jean.atvhome.tuya.Link
import dev.jean.atvhome.tuya.Panel
import dev.jean.atvhome.tuya.Plan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class FloorStatus(
    val name: String,
    val lit: Int,
    val total: Int,
    val litRooms: List<String> = emptyList(),
) {
    /** Compact room hint shown beneath a lit floor ("Escritório · Lavabo +1"). */
    fun roomHint(): String? = when {
        litRooms.isEmpty() -> null
        litRooms.size <= 2 -> litRooms.joinToString(" · ")
        else -> "${litRooms[0]} · ${litRooms[1]} +${litRooms.size - 2}"
    }
}

data class HouseSummary(val floors: List<FloorStatus>, val totalLit: Int) {
    companion object {
        /** Top floor down to ground floor, counting real lights ([Kind.LIGHT]). */
        fun from(plan: Plan, lights: List<Light>, states: Map<String, Boolean>): HouseSummary {
            val real = lights.filter { it.kind == Kind.LIGHT }
            val floors = plan.floors.indices.reversed().map { i ->
                val floor = plan.floors[i]
                val rooms = floor.rooms.mapTo(HashSet()) { it.room }
                val onFloor = real.filter { it.room in rooms }
                val litLights = onFloor.filter { Panel.isOn(it, states) }
                FloorStatus(
                    name = floor.name,
                    lit = litLights.size,
                    total = onFloor.size,
                    litRooms = litLights.map { it.room }.distinct(),
                )
            }
            return HouseSummary(floors, totalLit = real.count { Panel.isOn(it, states) })
        }
    }
}

/**
 * Architectural floor-by-floor light status shown in the top-left of Sony's screensaver while the
 * system dreams: a connected isometric elevation of the floors that glows warm amber where lights
 * are on, whispers the lit rooms beneath each active floor, and ripples when any switch toggles.
 * Uses a single hardware-accelerated custom [View] with zero Compose overhead in `:casa`.
 */
class HouseStrip(private val context: Context) {

    private val wm = context.getSystemService(WindowManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var view: FloorLightsView? = null
    private var params: WindowManager.LayoutParams? = null
    private var job: Job? = null
    private var driftJob: Job? = null

    fun show(home: SmartHome) {
        if (view != null) return
        val strip = FloorLightsView(context).apply {
            visibility = View.INVISIBLE
        }
        val baseX = dp(46f)
        val baseY = dp(38f)
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = baseX
            y = baseY
        }
        runCatching { wm.addView(strip, layoutParams) }.onFailure { Log.w(TAG, "add", it); return }
        view = strip
        params = layoutParams
        job = scope.launch {
            combine(home.link, home.states) { link, states -> link to states }.collect { (link, states) ->
                // Stay hidden until the hub has answered at least once so we never flash "Tudo apagado" falsely
                if (link !is Link.Online && states.isEmpty()) return@collect
                val summary = HouseSummary.from(home.plan, home.lights, states)
                val wasHidden = strip.visibility != View.VISIBLE
                strip.update(summary)
                if (wasHidden) {
                    strip.visibility = View.VISIBLE
                    strip.alpha = 0f
                    strip.animate().alpha(1f).setDuration(900).start()
                }
                layoutParams.let { p -> if (strip.isAttachedToWindow) runCatching { wm.updateViewLayout(strip, p) } }
            }
        }
        // Subtle pixel shift every 2 minutes to prevent OLED/LCD retention on long screensavers
        driftJob = scope.launch {
            val offsets = listOf(0 to 0, 3 to 2, 2 to 4, -2 to 3)
            var step = 0
            while (isActive) {
                delay(120_000L)
                val (dx, dy) = offsets[++step % offsets.size]
                layoutParams.x = baseX + dp(dx.toFloat())
                layoutParams.y = baseY + dp(dy.toFloat())
                if (strip.isAttachedToWindow) runCatching { wm.updateViewLayout(strip, layoutParams) }
            }
        }
    }

    fun hide() {
        job?.cancel()
        job = null
        driftJob?.cancel()
        driftJob = null
        view?.let { v ->
            v.stopAnimations()
            runCatching { wm.removeView(v) }
        }
        view = null
        params = null
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()

    private companion object {
        const val TAG = "HouseStrip"
    }
}

internal class FloorLightsView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, context.resources.displayMetrics)

    private val interMedium = ResourcesCompat.getFont(context, R.font.inter_medium) ?: Typeface.DEFAULT
    private val interSemiBold = ResourcesCompat.getFont(context, R.font.inter_semibold) ?: Typeface.DEFAULT_BOLD

    private val eyebrowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(10.5f)
        typeface = interMedium
        letterSpacing = 0.14f
        setShadowLayer(dp(5f), 0f, dp(1f), SHADOW)
    }
    private val floorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(14.5f)
        typeface = interMedium
        setShadowLayer(dp(6f), 0f, dp(1f), SHADOW)
    }
    private val roomPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(11f)
        typeface = interMedium
        setShadowLayer(dp(5f), 0f, dp(1f), SHADOW)
    }
    private val countPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(15f)
        typeface = interSemiBold
        fontFeatureSettings = "tnum"
        textAlign = Paint.Align.RIGHT
        setShadowLayer(dp(6f), 0f, dp(1f), SHADOW)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val spinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
    }

    private val slabW = dp(28f)
    private val slabH = dp(16f)
    private val glowR = dp(26f)
    private val glowShader = RadialGradient(
        0f, 0f, glowR,
        intArrayOf(0x6BFFB040, 0x24FFA62B, Color.TRANSPARENT),
        floatArrayOf(0f, 0.45f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val padX = dp(18f) // extra margin around slabs so radial glow never clips
    private val padY = dp(14f)
    private val rowBaseH = dp(26f)
    private val roomSubGap = dp(3f)
    private val rowGap = dp(9f)
    private val eyebrowGap = dp(13f)
    private val slabNameGap = dp(13f)
    private val nameDotsGap = dp(18f)
    private val dotR = dp(2.1f)
    private val dotGap = dp(6f)
    private val dotsCountGap = dp(8f)
    private val eyebrowDotR = dp(2.6f)
    private val eyebrowDotGap = dp(7f)

    private val topPath = Path()
    private val sidePath = Path()
    private val rimPath = Path()

    private var summary = HouseSummary(emptyList(), 0)
    private var roomHints: List<String?> = emptyList()
    private var eyebrowText = ""
    private var maxLabelW = 0f
    private var countW = 0f
    private var dotsColumnW = 0f
    private var eyebrowW = 0f
    private val eyebrowH: Float
    private val floorTextOffset: Float
    private val roomH: Float
    private val roomAscent: Float

    private var heats = FloatArray(0)
    private var startHeats = FloatArray(0)
    private var targetHeats = FloatArray(0)
    private var ripples = FloatArray(0)
    private var heatAnim: ValueAnimator? = null
    private val rippleAnims = ArrayList<ValueAnimator>()
    private var scrimW = 0
    private var scrimH = 0

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        val efm = eyebrowPaint.fontMetrics
        eyebrowH = efm.descent - efm.ascent
        val fm = floorPaint.fontMetrics
        floorTextOffset = -(fm.ascent + fm.descent) / 2f
        val rfm = roomPaint.fontMetrics
        roomH = rfm.descent - rfm.ascent
        roomAscent = -rfm.ascent
        buildSlabPaths()
        glowPaint.shader = glowShader
    }

    private fun buildSlabPaths() {
        val w = slabW
        val h = slabH
        topPath.reset()
        topPath.moveTo(w * 0.5f, 0f)
        topPath.lineTo(w, h * 0.36f)
        topPath.lineTo(w * 0.5f, h * 0.72f)
        topPath.lineTo(0f, h * 0.36f)
        topPath.close()

        sidePath.reset()
        sidePath.moveTo(0f, h * 0.36f)
        sidePath.lineTo(w * 0.5f, h * 0.72f)
        sidePath.lineTo(w, h * 0.36f)
        sidePath.lineTo(w, h * 0.62f)
        sidePath.lineTo(w * 0.5f, h)
        sidePath.lineTo(0f, h * 0.62f)
        sidePath.close()

        rimPath.reset()
        rimPath.moveTo(0f, h * 0.36f)
        rimPath.lineTo(w * 0.5f, 0f)
        rimPath.lineTo(w, h * 0.36f)
    }

    fun update(next: HouseSummary) {
        val prev = summary
        val sameFloors = prev.floors.size == next.floors.size &&
            prev.floors.indices.all { prev.floors[it].name == next.floors[it].name }
        val prevHints = roomHints
        summary = next
        roomHints = next.floors.map { it.roomHint() }
        eyebrowText = when (next.totalLit) {
            0 -> "CASA  ·  TUDO APAGADO"
            1 -> "CASA  ·  1 LUZ ACESA"
            else -> "CASA  ·  ${next.totalLit} LUZES ACESAS"
        }
        val dotOffset = if (next.totalLit > 0) eyebrowDotR * 2f + eyebrowDotGap else 0f
        eyebrowW = dotOffset + eyebrowPaint.measureText(eyebrowText)

        maxLabelW = next.floors.indices.maxOfOrNull { i ->
            val floorW = floorPaint.measureText(next.floors[i].name)
            val hintW = roomHints[i]?.let { roomPaint.measureText(it) } ?: 0f
            maxOf(floorW, hintW)
        } ?: 0f
        countW = maxOf(countPaint.measureText("88"), countPaint.measureText("–"))
        val maxDots = (next.floors.maxOfOrNull { minOf(it.lit, MAX_DOTS) } ?: 0)
        dotsColumnW = if (maxDots > 0) {
            maxDots * dotR * 2f + (maxDots - 1) * dotGap + dotsCountGap
        } else 0f

        val layoutChanged = !sameFloors || prevHints != roomHints ||
            (prev.floors.maxOfOrNull { minOf(it.lit, MAX_DOTS) } ?: 0) != maxDots

        val n = next.floors.size
        if (!sameFloors) {
            heats = FloatArray(n) { if (next.floors[it].lit > 0) 1f else 0f }
            startHeats = heats.copyOf()
            targetHeats = heats.copyOf()
            ripples = FloatArray(n)
            requestLayout()
            invalidate()
            return
        }

        if (layoutChanged) requestLayout()

        var changed = false
        val newTargets = FloatArray(n) { if (next.floors[it].lit > 0) 1f else 0f }
        for (i in 0 until n) {
            if (newTargets[i] != targetHeats[i] || prev.floors[i].lit != next.floors[i].lit) {
                changed = true
                if (next.floors[i].lit != prev.floors[i].lit) triggerRipple(i)
            }
        }
        if (changed) animateHeats(newTargets)
        invalidate()
    }

    private fun animateHeats(targets: FloatArray) {
        heatAnim?.cancel()
        startHeats = heats.copyOf()
        targetHeats = targets
        heatAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 550L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                for (i in heats.indices) {
                    heats[i] = startHeats[i] + (targetHeats[i] - startHeats[i]) * t
                }
                invalidate()
            }
            start()
        }
    }

    private fun triggerRipple(index: Int) {
        if (index !in ripples.indices) return
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 950L
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                if (index in ripples.indices) {
                    ripples[index] = a.animatedValue as Float
                    invalidate()
                }
            }
            start()
        }
        rippleAnims.add(anim)
    }

    fun stopAnimations() {
        heatAnim?.cancel()
        heatAnim = null
        rippleAnims.forEach { it.cancel() }
        rippleAnims.clear()
    }

    private fun rowHeight(index: Int): Float =
        if (roomHints.getOrNull(index) != null) rowBaseH + roomSubGap + roomH else rowBaseH

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val n = summary.floors.size
        val rowsW = slabW + slabNameGap + maxLabelW + nameDotsGap + dotsColumnW + countW
        val contentW = maxOf(eyebrowW, rowsW)
        var rowsH = 0f
        for (i in 0 until n) {
            rowsH += rowHeight(i)
            if (i < n - 1) rowsH += rowGap
        }
        val contentH = eyebrowH + eyebrowGap + rowsH
        setMeasuredDimension(
            (padX * 2f + contentW).roundToInt(),
            (padY * 2f + contentH).roundToInt(),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val n = summary.floors.size
        if (n == 0) return

        // Invisible-edge radial backdrop softens bright leaves behind typography without adding a box
        if (width != scrimW || height != scrimH) {
            scrimW = width; scrimH = height
            val r = maxOf(width, height) * 0.72f
            scrimPaint.shader = RadialGradient(
                width * 0.42f, height * 0.46f, r.coerceAtLeast(1f),
                intArrayOf(0x40000000, 0x1A000000, Color.TRANSPARENT),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        val quietScale = if (summary.totalLit > 0) 1f else 0.72f
        val left = padX
        val top = padY

        // Eyebrow with subtle warm indicator dot when any light is on
        var eyebrowTextX = left
        if (summary.totalLit > 0) {
            val dotY = top + eyebrowH * 0.5f
            fillPaint.color = AMBER
            canvas.drawCircle(left + eyebrowDotR, dotY, eyebrowDotR, fillPaint)
            eyebrowTextX += eyebrowDotR * 2f + eyebrowDotGap
        }
        eyebrowPaint.color = withAlpha(
            if (summary.totalLit > 0) 0xC4FFFFFF.toInt() else 0x80FFFFFF.toInt(),
            quietScale,
        )
        canvas.drawText(eyebrowText, eyebrowTextX, top - eyebrowPaint.fontMetrics.ascent, eyebrowPaint)

        val rowsTop = top + eyebrowH + eyebrowGap
        val rightEdge = width - padX
        val nameX = left + slabW + slabNameGap
        val spineX = left + slabW * 0.5f

        // Precompute vertical centers so the architectural elevation spine connects the floors cleanly
        val rowTops = FloatArray(n)
        val slabCentersY = FloatArray(n)
        var y = rowsTop
        for (i in 0 until n) {
            rowTops[i] = y
            slabCentersY[i] = y + rowBaseH * 0.5f
            y += rowHeight(i) + rowGap
        }

        // Architectural spine connecting floor slabs top-to-bottom
        for (i in 0 until n - 1) {
            val y1 = slabCentersY[i] + slabH * 0.55f
            val y2 = slabCentersY[i + 1] - slabH * 0.55f
            if (y2 > y1) {
                val linkHeat = maxOf(heats.getOrElse(i) { 0f }, heats.getOrElse(i + 1) { 0f }) * 0.55f
                spinePaint.color = lerpColor(0x28FFFFFF, 0x73FFA62B, linkHeat, quietScale)
                canvas.drawLine(spineX, y1, spineX, y2, spinePaint)
            }
        }

        for (i in 0 until n) {
            val floor = summary.floors[i]
            val heat = heats.getOrElse(i) { if (floor.lit > 0) 1f else 0f }.coerceIn(0f, 1f)
            val cy = slabCentersY[i]
            val slabTop = cy - slabH / 2f

            // Soft warm radial bloom behind lit slab
            if (heat > 0.01f) {
                val cx = left + slabW / 2f
                val gcy = slabTop + slabH * 0.42f
                canvas.save()
                canvas.translate(cx, gcy)
                glowPaint.alpha = (255 * heat).roundToInt()
                canvas.drawCircle(0f, 0f, glowR, glowPaint)
                canvas.restore()
            }

            // Live ripple ring when a light on this floor toggles
            val rp = ripples.getOrElse(i) { 0f }
            if (rp > 0.01f && rp < 0.99f) {
                val cx = left + slabW / 2f
                val rcy = slabTop + slabH * 0.42f
                ripplePaint.color = withAlpha(AMBER, (1f - rp) * 0.65f)
                canvas.drawCircle(cx, rcy, dp(8f) + dp(18f) * rp, ripplePaint)
            }

            // Isometric floor slab
            canvas.save()
            canvas.translate(left, slabTop)
            fillPaint.color = lerpColor(SLAB_SIDE_OFF, SLAB_SIDE_ON, heat, quietScale)
            canvas.drawPath(sidePath, fillPaint)
            fillPaint.color = lerpColor(SLAB_TOP_OFF, SLAB_TOP_ON, heat, quietScale)
            canvas.drawPath(topPath, fillPaint)
            strokePaint.color = lerpColor(SLAB_RIM_OFF, SLAB_RIM_ON, heat, quietScale)
            canvas.drawPath(rimPath, strokePaint)
            canvas.restore()

            // Floor name + optional whispered lit room names underneath
            floorPaint.color = lerpColor(TEXT_OFF, TEXT_ON, heat, quietScale)
            canvas.drawText(floor.name, nameX, cy + floorTextOffset, floorPaint)
            roomHints.getOrNull(i)?.let { hint ->
                roomPaint.color = withAlpha(ROOM_HINT, maxOf(0.55f, heat))
                val hintBaseline = rowTops[i] + rowBaseH + roomSubGap + roomAscent
                canvas.drawText(hint, nameX, hintBaseline, roomPaint)
            }

            // Right column: glowing light micro-dots + tabular numeral (or quiet dash)
            if (floor.lit > 0) {
                val dots = minOf(floor.lit, MAX_DOTS)
                val dotsRight = rightEdge - countW - dotsCountGap
                fillPaint.color = withAlpha(AMBER_SOFT, 0.92f)
                for (d in 0 until dots) {
                    val dx = dotsRight - dotR - (dots - 1 - d) * (dotR * 2f + dotGap)
                    canvas.drawCircle(dx, cy, dotR, fillPaint)
                }
                countPaint.color = AMBER_TEXT
                canvas.drawText(floor.lit.toString(), rightEdge, cy + floorTextOffset, countPaint)
            } else {
                countPaint.color = withAlpha(0x4DFFFFFF, quietScale)
                canvas.drawText("–", rightEdge, cy + floorTextOffset, countPaint)
            }
        }
    }

    private fun lerpColor(a: Int, b: Int, t: Float, alphaScale: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val aa = Color.alpha(a) + ((Color.alpha(b) - Color.alpha(a)) * k)
        val r = Color.red(a) + ((Color.red(b) - Color.red(a)) * k)
        val g = Color.green(a) + ((Color.green(b) - Color.green(a)) * k)
        val bl = Color.blue(a) + ((Color.blue(b) - Color.blue(a)) * k)
        return Color.argb((aa * alphaScale).roundToInt().coerceIn(0, 255), r.roundToInt(), g.roundToInt(), bl.roundToInt())
    }

    private fun withAlpha(color: Int, scale: Float): Int =
        Color.argb((Color.alpha(color) * scale).roundToInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        const val MAX_DOTS = 4
        const val SHADOW = 0x80000000.toInt()
        const val AMBER = 0xFFFFA62B.toInt()
        const val AMBER_SOFT = 0xFFFFBE5C.toInt()
        const val AMBER_TEXT = 0xEBFFD180.toInt()
        const val ROOM_HINT = 0xB8FFE2B8.toInt()
        const val SLAB_TOP_OFF = 0x26FFFFFF
        const val SLAB_TOP_ON = 0xE6FFA62B.toInt()
        const val SLAB_SIDE_OFF = 0x14FFFFFF
        const val SLAB_SIDE_ON = 0xCC9A5F17.toInt()
        const val SLAB_RIM_OFF = 0x4DFFFFFF
        const val SLAB_RIM_ON = 0xE6FFE0B2.toInt()
        const val TEXT_OFF = 0x85FFFFFF.toInt()
        const val TEXT_ON = 0xF0FFFFFF.toInt()
    }
}
