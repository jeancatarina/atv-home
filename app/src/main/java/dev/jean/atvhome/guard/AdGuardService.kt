package dev.jean.atvhome.guard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Point
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File

/**
 * The ad-break guard. Knows no streaming app: once a second it asks three readers what the app in
 * front shows, and during an ad it presses "skip" when there is one, or mutes and covers the screen.
 * Fails open: on any doubt or error it undoes everything and the app plays as if we were not here.
 *
 * Runs in the guard process, and is only bound while "Sem anúncios" is on and another app is in
 * front (data/AdShield.kt): being bound is what "on" means here.
 */
class AdGuardService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val state = AdBreak()
    private val size = Point()
    private lateinit var rules: Rules
    private lateinit var signals: AdSignals
    private lateinit var countdown: Countdown
    private lateinit var readers: Readers
    private lateinit var audio: AudioManager
    private lateinit var cover: Cover
    private lateinit var namedIds: List<String>
    private var front: String? = null
    private var mutedByGuard = false
    private var shotSeq = 0L
    private var lastVerdict: Verdict = Verdict.None
    private var keep: List<Label> = emptyList()
    private var ticks = 0
    private var breakTicks = 0

    private val tick = object : Runnable {
        override fun run() {
            runCatching { step() }.onFailure { Log.w(TAG, "step", it); calm() }
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onServiceConnected() {
        rules = Rules.parse(assets.open("guard/rules.json").bufferedReader().use { it.readText() })
        signals = AdSignals(rules)
        countdown = Countdown(signals)
        readers = Readers(this)
        if (File(filesDir, RESET_MARK).delete()) readers.reset()
        audio = getSystemService(AudioManager::class.java)
        cover = Cover(this)
        namedIds = rules.adViewIds + rules.skipViewIds
        @Suppress("DEPRECATION") (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealSize(size)
        front = rootInActiveWindow?.packageName?.toString()?.takeIf(::isApp)
        handler.post(tick)
    }

    /** The app in front, from window changes: the tick never has to ask the app for it. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()?.takeIf(::isApp) ?: return
        if (pkg != front) { front = pkg; calm() }
    }

    /** The system UI passing in front (volume bar, a dialog) and our own windows do not change which app plays. */
    private fun isApp(pkg: String) = pkg != SYSTEM_UI && pkg != packageName

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacksAndMessages(null)
        calm()
        ScreenService.instance?.rest() // the capture token stays for the next time an app is in front
        return super.onUnbind(intent)
    }

    private fun step() {
        val now = SystemClock.elapsedRealtime()
        val pkg = front ?: return
        val screen = ScreenService.instance?.takeIf { it.ready }
        // Cheapest check first, and it does not involve the app: nothing playing, no break open
        // (a menu, a paused film) = read nothing, cost nothing
        if (!audio.isMusicActive && !state.inBreak) { screen?.rest(); return }

        // Cheapest first; on a tie maxByOrNull keeps the first, so the cheapest reader gets the credit
        val verdicts = ArrayList<Pair<Verdict, Source>>(3)
        SessionReader.read(this, pkg)?.let { verdicts += signals.judge(it) to Source.SESSION }
        if (readers.wants(pkg, Source.TREE, state.inBreak)) {
            rootInActiveWindow?.takeIf { it.packageName?.toString() == pkg }?.let { root ->
                TreeReader.read(root, size.x, size.y, namedIds)?.let { verdicts += signals.judge(it) to Source.TREE }
            }
        }
        var waitingForScreen = false
        if (screen != null && rules.screenReader && readers.wants(pkg, Source.SCREEN, state.inBreak)) {
            // Calm: one look every SLOW_EVERY ticks. Alert (something like an ad, a corner number,
            // a break): one a second
            val alert = state.inBreak || lastVerdict != Verdict.None || countdown.watching
            if (alert || ++ticks % SLOW_EVERY == 0) look(screen)
            val shot = screen.latest(now)
            when {
                // A stale or missing read counts as "no ad": the guard fails open
                shot == null -> verdicts += Verdict.None to Source.SCREEN
                // A read is judged once: the same read on two ticks must not count as two
                shot.seq != shotSeq -> {
                    shotSeq = shot.seq
                    val counter = if (rules.countdownRule) countdown.feed(shot.reading) else null
                    verdicts += signals.judge(shot.reading, counter) to Source.SCREEN
                }
                else -> waitingForScreen = true
            }
        } else screen?.rest()

        val (verdict, source) = verdicts.maxByOrNull { rank(it.first) } ?: (Verdict.None to Source.SESSION)
        // With the screen as a reader and no new read yet, the cheaper readers' silence is not news
        if (verdict == Verdict.None && waitingForScreen) return
        lastVerdict = verdict
        val wasInBreak = state.inBreak
        val decision = state.on(verdict, now)
        if (decision.active && verdict != Verdict.None) readers.remember(pkg, source)
        if (state.inBreak && !wasInBreak) Log.i(TAG, "break in $pkg by $source")
        decision.click?.let(::press)
        if (!decision.active) { quiet(); return }
        // Holes only matter to the screen reader; one missed read must not close them
        if (source == Source.SCREEN && decision.keep.isNotEmpty()) keep = decision.keep
        mute()
        cover.show(keep)
    }

    /** Every [PEEK_EVERY] ticks of a break the cover lifts for one frame, to spot a skip button that showed up under it. */
    private fun look(screen: ScreenService) {
        if (state.inBreak && ++breakTicks % PEEK_EVERY == 0) {
            cover.peek(true)
            handler.postDelayed({ screen.look { cover.peek(false) } }, PEEK_MS)
        } else screen.look()
    }

    private fun rank(v: Verdict) = when (v) { Verdict.None -> 0; is Verdict.Ad -> 1; is Verdict.Skippable -> 2 }

    /** Clicks the node when the app has one; otherwise taps the spot where the OCR read the button. */
    private fun press(label: Label) {
        val root = rootInActiveWindow
        var node = when {
            root == null -> null
            label.viewId != null -> root.findAccessibilityNodeInfosByViewId(label.viewId).firstOrNull()
            label.text.isNotEmpty() -> root.findAccessibilityNodeInfosByText(label.text).firstOrNull()
            else -> null
        }
        while (node != null && !node.isClickable) node = node.parent
        if (node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
        if (!rules.tapToSkip) return
        val path = Path().apply { moveTo(label.x * size.x, label.y * size.y) }
        dispatchGesture(
            GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build(), null, null,
        )
    }

    private fun mute() {
        // Already muted by the person: not ours to undo later
        if (mutedByGuard || audio.isStreamMute(AudioManager.STREAM_MUSIC)) return
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
        mutedByGuard = true
    }

    /** Sound and picture back; the break state machine keeps counting. */
    private fun quiet() {
        if (mutedByGuard) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            mutedByGuard = false
        }
        if (::cover.isInitialized) cover.hide()
        keep = emptyList()
    }

    /** Everything back and forgotten: the app in front changed, the guard went off, or something failed. */
    private fun calm() {
        quiet()
        state.reset()
        if (::countdown.isInitialized) countdown.reset()
        lastVerdict = Verdict.None
        breakTicks = 0
    }

    companion object {
        /** Created by AdShield when switched on: the guard forgets what it learned. */
        const val RESET_MARK = "guard_reset"
        private const val TAG = "AdGuard"
        private const val SYSTEM_UI = "com.android.systemui"
        private const val TICK_MS = 1_000L
        /** Lever (docs/anuncios/SINAIS.md): calm screen looks every this many ticks. */
        private const val SLOW_EVERY = 2
        private const val PEEK_EVERY = 5
        private const val PEEK_MS = 150L
    }
}
