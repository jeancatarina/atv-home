package dev.jean.atvhome.saver

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.service.dreams.DreamService
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.core.content.res.ResourcesCompat
import dev.jean.atvhome.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The screensaver: Apple TV's aerial films, nothing written over them except a small dark-green
 * panel with the time and the week's weather.
 */
class AerialSaver : DreamService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var video: VideoView
    private lateinit var curtain: View
    private lateinit var time: TextView
    private lateinit var date: TextView
    private lateinit var now: TextView
    private lateinit var week: LinearLayout
    private val urls by lazy {
        assets.open("aerials.txt").bufferedReader().readLines().filter { it.startsWith("https://") }.shuffled()
    }
    private var index = 0
    private val inter by lazy { ResourcesCompat.getFont(this, R.font.inter_medium) ?: Typeface.DEFAULT }
    private val interBold by lazy { ResourcesCompat.getFont(this, R.font.inter_semibold) ?: Typeface.DEFAULT_BOLD }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        setContentView(build())
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        playNext()
        scope.launch {
            while (isActive) {
                val t = LocalDateTime.now()
                time.text = t.format(DateTimeFormatter.ofPattern("HH:mm"))
                date.text = t.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("pt", "BR")))
                    .replaceFirstChar { it.uppercase() }
                delay(1000L * (60 - t.second))
            }
        }
        scope.launch {
            while (isActive) {
                Weather(this@AerialSaver).forecast()?.let(::showWeather)
                delay(30 * 60_000L)
            }
        }
    }

    override fun onDreamingStopped() {
        scope.cancel()
        video.stopPlayback()
        super.onDreamingStopped()
    }

    private fun playNext() {
        if (urls.isEmpty()) return
        curtain.animate().alpha(1f).setDuration(600).withEndAction {
            video.setVideoURI(Uri.parse(urls[index++ % urls.size]))
            video.start()
        }.start()
    }

    private fun build(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        video = VideoView(this).apply {
            setOnPreparedListener { mp ->
                mp.setVolume(0f, 0f)
                mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                curtain.animate().alpha(0f).setDuration(1200).start()
            }
            setOnCompletionListener { playNext() }
            setOnErrorListener { _, _, _ -> playNext(); true }
        }
        root.addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        curtain = View(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(curtain, FrameLayout.LayoutParams(-1, -1))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(22f)
                setColor(PANEL)
            }
            setPadding(dp(26f).toInt(), dp(16f).toInt(), dp(26f).toInt(), dp(16f).toInt())
        }
        val clock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        time = text(46f, interBold, Color.WHITE)
        date = text(14f, inter, SOFT)
        clock.addView(time); clock.addView(date)
        panel.addView(clock)
        now = text(30f, interBold, Color.WHITE).apply { setPadding(dp(30f).toInt(), 0, dp(26f).toInt(), 0) }
        panel.addView(now)
        week = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        panel.addView(week)
        root.addView(
            panel,
            FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply {
                setMargins(dp(36f).toInt(), 0, 0, dp(32f).toInt())
            },
        )
        return root
    }

    private fun showWeather(f: Forecast) {
        now.text = listOfNotNull(f.nowIcon, f.now?.let { "$it°" }).joinToString(" ")
        week.removeAllViews()
        f.days.drop(1).take(6).forEach { d ->
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(11f).toInt(), 0, dp(11f).toInt(), 0)
            }
            col.addView(text(13f, inter, SOFT).apply { text = d.label })
            col.addView(text(20f, inter, Color.WHITE).apply { text = d.icon })
            col.addView(text(14f, interBold, Color.WHITE).apply { text = "${d.max}°" })
            col.addView(text(12f, inter, SOFT).apply { text = "${d.min}°" })
            week.addView(col)
        }
    }

    private fun text(sp: Float, face: Typeface, color: Int) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = face
        setTextColor(color)
        includeFontPadding = false
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private companion object {
        /** The dark olive green of the living-room wall, slightly see-through over the films. */
        const val PANEL = 0xD91F2A1C.toInt()
        const val SOFT = 0xB3FFFFFF.toInt()
    }
}
