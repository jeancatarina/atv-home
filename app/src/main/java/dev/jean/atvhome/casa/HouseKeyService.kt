package dev.jean.atvhome.casa

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.jean.atvhome.data.SmartHome
import dev.jean.atvhome.saver.HouseStrip
import dev.jean.atvhome.saver.WeatherStrip
import dev.jean.atvhome.ui.components.TvScroll
import dev.jean.atvhome.ui.components.requestWhenReady
import dev.jean.atvhome.ui.home.Casa
import dev.jean.atvhome.ui.home.HousePanel
import dev.jean.atvhome.ui.home.PanelState
import dev.jean.atvhome.ui.theme.InicioTheme
import kotlinx.coroutines.delay
import java.io.File

/**
 * "Casa no filme": the shortcut key ([Shortcut], yellow unless changed in "Ver a casa") opens the
 * house panel over any app (Netflix, Prime Video, a live channel), as a side sheet with the film
 * still playing beside it. The same key or Back closes it, and it closes on its own after [IDLE_MS]
 * without a key.
 *
 * Key filtering (`flagRequestFilterKeyEvents`) is turned on only when a house and shortcut are
 * configured and the TV is not dreaming; while active, held volume keys are repeated through
 * [AudioManager] because Android's key-filter pipeline suppresses hardware auto-repeat.
 *
 * It also writes the week's weather over the TV's screensaver ([WeatherStrip]) while the system
 * dreams, and returns the TV to Home when woken from standby.
 */
class HouseKeyService : AccessibilityService() {

    private var home: SmartHome? = null
    /** When the tuya.json in use was written: tools/tuya-setup.sh can bring a new one at any time. */
    private var loaded = Long.MIN_VALUE
    private var overlay: Overlay? = null
    private val main = Handler(Looper.getMainLooper())
    private val weather by lazy { WeatherStrip(this) }
    private val houseStrip by lazy { HouseStrip(this) }
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val volumeRepeatDelay by lazy { ViewConfiguration.getKeyRepeatDelay().toLong().coerceIn(50L, 100L) }
    private val volumeRepeatTimeout by lazy { ViewConfiguration.getKeyRepeatTimeout().toLong().coerceIn(300L, 500L) }
    private var activeVolumeKey = 0
    private var dreaming = false
    private var screenWasOff = false

    private val volumeRepeater = object : Runnable {
        override fun run() {
            val dir = VolumeHold.direction(activeVolumeKey) ?: return
            stepVolume(dir)
            main.postDelayed(this, volumeRepeatDelay)
        }
    }

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    DreamState.set(context, false)
                    dreaming = false
                    if (screenWasOff) {
                        screenWasOff = false
                        goHomeOnWake()
                    } else {
                        ensureHome()?.connect(true)
                    }
                    syncKeyFilter()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    screenWasOff = true
                    dreaming = false
                    DreamState.set(context, false)
                    cancelVolumeRepeat()
                    overlay?.hide()
                    hideDreamOverlays()
                    home?.connect(false, graceMs = 0)
                    syncKeyFilter()
                }
                Intent.ACTION_DREAMING_STARTED -> {
                    dreaming = true
                    DreamState.set(context, true)
                    cancelVolumeRepeat()
                    overlay?.hide()
                    showDreamOverlays()
                    syncKeyFilter()
                }
                Intent.ACTION_DREAMING_STOPPED -> {
                    dreaming = false
                    DreamState.set(context, false)
                    hideDreamOverlays()
                    syncKeyFilter()
                }
            }
        }
    }

    override fun onServiceConnected() {
        registerReceiver(screen, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_DREAMING_STARTED); addAction(Intent.ACTION_DREAMING_STOPPED)
        })
        if (ensureHome() == null) Log.i(TAG, "no tuya.json yet: the shortcut key stays with the apps")
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive != false
        dreaming = interactive && DreamState.isDreaming(this)
        if (dreaming) showDreamOverlays()
        syncKeyFilter()
    }

    private fun showDreamOverlays() {
        weather.show()
        ensureHome()?.let {
            it.connect(true)
            houseStrip.show(it)
        }
    }

    private fun hideDreamOverlays() {
        weather.hide()
        houseStrip.hide()
    }

    private fun syncKeyFilter() {
        val info = serviceInfo ?: return
        val want = !dreaming && ensureHome() != null && Shortcut.get(this) != Shortcut.NONE
        val has = (info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0
        if (has == want) return
        info.flags = if (want) info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        else info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        runCatching { serviceInfo = info }.onFailure { Log.w(TAG, "serviceInfo", it) }
    }

    private fun goHomeOnWake() {
        if (performGlobalAction(GLOBAL_ACTION_HOME)) return
        val homeIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { startActivity(homeIntent) }.onFailure { Log.w(TAG, "wake home", it) }
    }

    /**
     * The house, loaded the first time it is needed and again when tuya.json changes (the setup ran
     * while this service was up, or the launcher imported it after this started). One stat per call.
     */
    private fun ensureHome(): SmartHome? {
        SmartHome.importPushed(this)
        val stamp = SmartHome.configStamp(this)
        if (home != null && (stamp == loaded || SmartHome.isOwnWrite(stamp))) return home.also { loaded = stamp }
        if (stamp == 0L) return null
        houseStrip.hide()
        overlay?.destroy()
        overlay = null
        home?.close()
        home = SmartHome.forOverlay(this)?.also { it.connect(true) }
        loaded = stamp
        return home
    }

    private val hold = Hold()
    /** The press just given back to the app comes through here again: it goes on to the app. */
    private var replayingUntil = 0L

    override fun onKeyEvent(event: KeyEvent): Boolean {
        when (val step = VolumeHold.step(event.keyCode, event.action, event.repeatCount, event.flags)) {
            is VolumeHold.Step.Start -> {
                cancelVolumeRepeat()
                activeVolumeKey = event.keyCode
                main.postDelayed(volumeRepeater, volumeRepeatTimeout)
                return false
            }
            is VolumeHold.Step.Repeat -> {
                main.removeCallbacks(volumeRepeater)
                stepVolume(step.direction)
                return true
            }
            VolumeHold.Step.Stop -> {
                if (event.keyCode == activeVolumeKey) cancelVolumeRepeat()
                return false
            }
            VolumeHold.Step.None -> Unit
        }
        // HOME leaves whatever is open: the sheet must not stay over the launcher
        if (event.keyCode == KeyEvent.KEYCODE_HOME) { overlay?.hide(); return false }
        val choice = Shortcut.get(this)
        if (choice == Shortcut.NONE || event.keyCode != Shortcut.keyOf(choice)) return false
        if (ensureHome() == null) return false
        if (!Shortcut.isHold(choice)) {
            if (event.action == KeyEvent.ACTION_UP) ensureOverlay()?.toggle()
            return true
        }
        if (event.eventTime < replayingUntil) return false
        when (hold.step(event.action == KeyEvent.ACTION_DOWN, event.repeatCount, event.downTime, event.eventTime)) {
            Hold.Step.KEEP -> Unit
            Hold.Step.OPEN -> ensureOverlay()?.toggle()
            Hold.Step.REPLAY -> replay(event.keyCode)
        }
        return true
    }

    private fun stepVolume(direction: Int) {
        runCatching {
            audio?.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
        }.onFailure { Log.w(TAG, "volume", it) }
    }

    private fun cancelVolumeRepeat() {
        activeVolumeKey = 0
        main.removeCallbacks(volumeRepeater)
    }

    /** A short press of a held shortcut goes back to the app, as if nothing had stopped it. */
    private fun replay(code: Int) {
        replayingUntil = android.os.SystemClock.uptimeMillis() + 400
        when (code) {
            // Back goes to whatever window is in front, the overlay included (it closes)
            KeyEvent.KEYCODE_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            else -> audio?.let { a ->
                val now = android.os.SystemClock.uptimeMillis()
                a.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
                a.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
            }
        }
    }

    private fun ensureOverlay(): Overlay? {
        val h = home ?: return null
        return overlay ?: runCatching { Overlay(this, h) }.onFailure { Log.w(TAG, "overlay", it) }.getOrNull()?.also { overlay = it }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        cancelVolumeRepeat()
        main.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screen) }
        hideDreamOverlays()
        overlay?.destroy()
        overlay = null
        home?.close()
        home = null
        super.onDestroy()
    }

    /**
     * The side sheet: an accessibility overlay window (no "draw over apps" permission needed),
     * focusable only while shown so the remote drives it and nothing else.
     */
    private class Overlay(private val service: AccessibilityService, home: SmartHome) : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

        private val wm = service.getSystemService(WindowManager::class.java)
        private val panel = PanelState()
        private val first = FocusRequester()
        private var shown by mutableStateOf(false)
        private var keys by mutableIntStateOf(0)
        private var opened by mutableIntStateOf(0)
        private val params = WindowManager.LayoutParams(
            (SHEET_DP * service.resources.displayMetrics.density).toInt(),
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            HIDDEN_FLAGS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.END or Gravity.TOP }

        @OptIn(ExperimentalFoundationApi::class)
        private val view = ComposeView(service).apply {
            setViewTreeLifecycleOwner(this@Overlay)
            setViewTreeSavedStateRegistryOwner(this@Overlay)
            visibility = View.GONE
            setContent {
                InicioTheme {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides TvScroll) {
                        Box(
                            Modifier.fillMaxSize()
                                .background(Casa.Sheet, SHEET_SHAPE)
                                .border(1.dp, Casa.Hairline, SHEET_SHAPE)
                                .onPreviewKeyEvent { e ->
                                    keys++
                                    if (e.key == Key.Back || e.key == Key.Escape) {
                                        if (e.type == KeyEventType.KeyUp) hide()
                                        true
                                    } else false
                                },
                        ) {
                            HousePanel(
                                home, toast = { Toast.makeText(service, it, Toast.LENGTH_SHORT).show() },
                                state = panel, columns = GridCells.Fixed(3), edge = 22.dp, firstFocus = first, compact = true,
                            )
                        }
                    }
                }
                // Every opening starts on "Apagar tudo"; nobody touching it for a while: back to the film
                LaunchedEffect(opened) { if (opened > 0) first.requestWhenReady() }
                LaunchedEffect(shown, keys) { if (shown) { delay(IDLE_MS); hide() } }
            }
        }

        init {
            saved.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
            wm.addView(view, params)
            registry.currentState = Lifecycle.State.STARTED
        }

        fun toggle() = if (shown) hide() else show()

        fun show() {
            params.flags = SHOWN_FLAGS
            view.visibility = View.VISIBLE
            wm.updateViewLayout(view, params)
            view.requestFocus()
            registry.currentState = Lifecycle.State.RESUMED
            shown = true
            panel.keep = emptySet() // every opening shows what is lit now
            opened++
        }

        fun hide() {
            if (!shown) return
            shown = false
            view.visibility = View.GONE
            params.flags = HIDDEN_FLAGS
            runCatching { wm.updateViewLayout(view, params) }
            registry.currentState = Lifecycle.State.STARTED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
            runCatching { wm.removeView(view) }
        }

        private companion object {
            const val SHEET_DP = 452
            val SHEET_SHAPE = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
            const val HIDDEN_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            const val SHOWN_FLAGS = 0
        }
    }

    companion object {
        private const val TAG = "CasaOverlay"
        private const val IDLE_MS = 20_000L
    }
}

/**
 * Shared dreaming flag between `:main` and `:casa`: when the screensaver starts over Home,
 * `:casa` is bound right after `ACTION_DREAMING_STARTED`, so it reads this marker on connect.
 */
object DreamState {
    private const val FILE = "dreaming"
    private const val MAX_AGE_MS = 12 * 3_600_000L

    fun set(context: Context, active: Boolean) {
        val f = File(context.filesDir, FILE)
        runCatching {
            if (active) {
                f.writeText("1")
                f.setLastModified(System.currentTimeMillis())
            } else {
                f.delete()
            }
        }
    }

    fun isDreaming(context: Context): Boolean {
        val f = File(context.filesDir, FILE)
        return f.isFile && (System.currentTimeMillis() - f.lastModified()) in 0..MAX_AGE_MS
    }
}

/**
 * Volume hold handling while `flagRequestFilterKeyEvents` is active: the first press passes through
 * natively, and holding repeats volume changes through [AudioManager] instead of relying on
 * Android's filtered-key auto-repeat (which stops on re-injected events).
 */
object VolumeHold {
    sealed interface Step {
        data object None : Step
        data class Start(val direction: Int) : Step
        data class Repeat(val direction: Int) : Step
        data object Stop : Step
    }

    fun direction(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
        KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
        else -> null
    }

    fun step(keyCode: Int, action: Int, repeatCount: Int, flags: Int = 0): Step {
        val dir = direction(keyCode) ?: return Step.None
        return when {
            (flags and KeyEvent.FLAG_CANCELED) != 0 || action == KeyEvent.ACTION_UP -> Step.Stop
            action == KeyEvent.ACTION_DOWN && repeatCount == 0 -> Step.Start(dir)
            action == KeyEvent.ACTION_DOWN -> Step.Repeat(dir)
            else -> Step.None
        }
    }
}
