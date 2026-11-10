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
import dev.jean.atvhome.ui.components.TvScroll
import dev.jean.atvhome.ui.home.HousePanel
import dev.jean.atvhome.ui.home.PanelState
import dev.jean.atvhome.ui.theme.InicioTheme
import kotlinx.coroutines.delay

/**
 * "Casa no filme": the yellow key opens the house panel over any app (Netflix, Prime Video, a live
 * channel), as a side sheet with the film still playing beside it. Yellow or Back closes it, and it
 * closes on its own after [IDLE_MS] without a key.
 *
 * An accessibility service only to see that one key: every other key goes straight to the app
 * (onKeyEvent returns false at once), and it asks for no events. It runs in its own process
 * (":casa"), so a film never holds the launcher's memory, and keeps its own connection to the hub
 * while the screen is on: the panel opens with every light already current.
 */
class HouseKeyService : AccessibilityService() {

    private var home: SmartHome? = null
    private var overlay: Overlay? = null
    private val main = Handler(Looper.getMainLooper())

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> home?.connect(true)
                Intent.ACTION_SCREEN_OFF -> { overlay?.hide(); home?.connect(false, graceMs = 0) }
            }
        }
    }

    override fun onServiceConnected() {
        val h = SmartHome.forOverlay(this) ?: return Unit.also { Log.i(TAG, "no tuya.json: the yellow key stays with the apps") }
        home = h
        h.connect(true)
        registerReceiver(screen, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) })
        // Built once, a while after start, and kept hidden: the first yellow press opens it at once
        main.postDelayed({ ensureOverlay() }, WARM_UP_MS)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != TRIGGER || home == null) return false
        if (event.action == KeyEvent.ACTION_UP) ensureOverlay()?.toggle()
        return true
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
        overlay?.destroy()
        overlay = null
        home?.connect(false, graceMs = 0)
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
                                .background(Color(0xF2141416), RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp))
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
                                state = panel, columns = GridCells.Fixed(3), edge = 22.dp, firstFocus = first,
                            )
                        }
                    }
                }
                // Every opening starts on "Apagar tudo"; nobody touching it for a while: back to the film
                LaunchedEffect(opened) { if (opened > 0) runCatching { first.requestFocus() } }
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
            registry.currentState = Lifecycle.State.RESUMED
            shown = true
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
            const val HIDDEN_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            const val SHOWN_FLAGS = 0
        }
    }

    companion object {
        private const val TAG = "CasaOverlay"
        /** The yellow key of the remote. */
        const val TRIGGER = KeyEvent.KEYCODE_PROG_YELLOW
        private const val IDLE_MS = 20_000L
        private const val WARM_UP_MS = 8_000L
    }
}
