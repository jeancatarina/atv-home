package dev.jean.atvhome.casa

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
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
import dev.jean.atvhome.saver.WeatherStrip
import dev.jean.atvhome.ui.components.TvScroll
import dev.jean.atvhome.ui.components.requestWhenReady
import dev.jean.atvhome.ui.home.Casa
import dev.jean.atvhome.ui.home.HousePanel
import dev.jean.atvhome.ui.home.PanelState
import dev.jean.atvhome.ui.theme.InicioTheme
import kotlinx.coroutines.delay

/**
 * "Casa no filme": the shortcut key ([Shortcut], yellow unless changed in "Ver a casa") opens the
 * house panel over any app (Netflix, Prime Video, a live channel), as a side sheet with the film
 * still playing beside it. The same key or Back closes it, and it closes on its own after [IDLE_MS]
 * without a key.
 *
 * An accessibility service only to see that one key: every other key goes straight to the app
 * (onKeyEvent returns false at once), and it asks for no events. It runs in its own process
 * (":casa"), so a film never holds the launcher's memory, and keeps its own connection to the hub
 * while the screen is on: the panel opens with every light already current.
 *
 * It also writes the week's weather over the TV's screensaver ([WeatherStrip]) while the system
 * dreams, so it stays listed even with no shortcut and no house.
 */
class HouseKeyService : AccessibilityService() {

    private var home: SmartHome? = null
    /** When the tuya.json in use was written: tools/tuya-setup.sh can bring a new one at any time. */
    private var loaded = Long.MIN_VALUE
    private var overlay: Overlay? = null
    private val main = Handler(Looper.getMainLooper())
    private val weather by lazy { WeatherStrip(this) }

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> ensureHome()?.connect(true)
                Intent.ACTION_SCREEN_OFF -> { overlay?.hide(); weather.hide(); home?.connect(false, graceMs = 0) }
                Intent.ACTION_DREAMING_STARTED -> { overlay?.hide(); weather.show() }
                Intent.ACTION_DREAMING_STOPPED -> weather.hide()
            }
        }
    }

    override fun onServiceConnected() {
        registerReceiver(screen, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_DREAMING_STARTED); addAction(Intent.ACTION_DREAMING_STOPPED)
        })
        if (ensureHome() == null) Log.i(TAG, "no tuya.json yet: the shortcut key stays with the apps")
        // Built once, a while after start, and kept hidden: the first press opens it at once
        main.postDelayed({ ensureOverlay() }, WARM_UP_MS)
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

    /** A short press of a held shortcut goes back to the app, as if nothing had stopped it. */
    private fun replay(code: Int) {
        replayingUntil = android.os.SystemClock.uptimeMillis() + 400
        when (code) {
            // Back goes to whatever window is in front, the overlay included (it closes)
            KeyEvent.KEYCODE_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            else -> getSystemService(android.media.AudioManager::class.java)?.let { audio ->
                val now = android.os.SystemClock.uptimeMillis()
                audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
                audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
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
        main.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screen) }
        weather.hide()
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
        private const val WARM_UP_MS = 8_000L
    }
}
