package dev.jean.atvhome

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import dev.jean.atvhome.data.AdShield
import dev.jean.atvhome.casa.Hold
import dev.jean.atvhome.casa.Shortcut
import dev.jean.atvhome.guard.ScreenService
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import dev.jean.atvhome.ui.components.TvScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.focusGroup
import dev.jean.atvhome.data.Title
import dev.jean.atvhome.ui.HomeViewModel
import dev.jean.atvhome.ui.components.LastKey
import dev.jean.atvhome.ui.detail.DetailScreen
import dev.jean.atvhome.ui.detail.PersonScreen
import dev.jean.atvhome.ui.discover.ExploreScreen
import dev.jean.atvhome.ui.discover.SearchScreen
import dev.jean.atvhome.ui.discover.WhatToWatchScreen
import dev.jean.atvhome.ui.discover.RouletteScreen
import dev.jean.atvhome.ui.discover.MatchScreen
import dev.jean.atvhome.social.SocialViewModel
import dev.jean.atvhome.social.ui.SocialScreen
import dev.jean.atvhome.ui.home.HomeScreen
import dev.jean.atvhome.ui.home.HouseScreen
import dev.jean.atvhome.ui.panel.AdShieldScreen
import dev.jean.atvhome.ui.panel.QuickPanel
import dev.jean.atvhome.ui.theme.InicioTheme

sealed interface Screen {
    data class Detail(val title: Title) : Screen
    data class Person(val id: Int) : Screen
    data object Search : Screen
    data object Explore : Screen
    data object WhatToWatch : Screen
    data object Roulette : Screen
    data object Match : Screen
    data object Social : Screen
    data object AdSettings : Screen
    data object House : Screen
}

class HomeActivity : ComponentActivity() {

    private val vm: HomeViewModel by viewModels()
    private val home get() = (application as App).home
    private val hold = Hold()
    private var heldDown: KeyEvent? = null
    /** Created the first time Social opens: the launcher pays nothing for it before that. */
    private val social: SocialViewModel by viewModels()
    private var socialUsed = false
    private val stack = mutableStateListOf<Screen>()
    private var panelOpen by mutableStateOf(false)
    private var resetSignal by mutableIntStateOf(0)
    /** Bumps on every key: trailers wait for the remote to rest, and stop when nobody is around. */
    private var inputTick by mutableIntStateOf(0)

    @OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InicioTheme {
              CompositionLocalProvider(LocalBringIntoViewSpec provides TvScroll) {
                var restoreSignal by remember { mutableIntStateOf(0) }
                val top = stack.lastOrNull()
                val toast: (String) -> Unit = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                fun open(screen: Screen) { stack.add(screen) }
                val onOpen: (Title) -> Unit = { open(Screen.Detail(it)) }

                BackHandler(enabled = panelOpen || stack.isNotEmpty()) {
                    if (panelOpen) panelOpen = false else stack.removeAt(stack.lastIndex)
                }
                // Back on Home: focus returns to where it was
                LaunchedEffect(top == null, panelOpen) {
                    if (top == null && !panelOpen) restoreSignal++
                }

                Box(Modifier.fillMaxSize()) {
                    // Home stays composed underneath so its scroll, focus and images survive
                    val covered = top != null || panelOpen
                    Box(
                        Modifier.fillMaxSize()
                            // Covered Home isn't drawn at all (alpha 0 skips its render node)
                            .graphicsLayer { alpha = if (top != null) 0f else 1f }
                            .focusProperties { if (covered) enter = { FocusRequester.Cancel } }
                            .focusGroup(),
                    ) {
                        HomeScreen(
                            vm = vm,
                            active = top == null && !panelOpen,
                            restoreSignal = restoreSignal,
                            resetSignal = resetSignal,
                            inputTick = inputTick,
                            onOpen = onOpen,
                            onSearch = { open(Screen.Search) },
                            onWhatToWatch = { open(Screen.WhatToWatch) },
                            onExplore = { open(Screen.Explore) },
                            onRoulette = { open(Screen.Roulette) },
                            onMatch = { open(Screen.Match) },
                            onSocial = { socialUsed = true; open(Screen.Social) },
                            home = home,
                            onHouse = { open(Screen.House) },
                            toast = toast,
                        )
                    }
                    if (top != null) {
                        Box(Modifier.fillMaxSize().focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()) {
                            when (top) {
                                is Screen.Detail -> DetailScreen(top.title, vm, onOpen, onPerson = { open(Screen.Person(it)) }, toast = toast)
                                is Screen.Person -> PersonScreen(top.id, vm, onOpen)
                                Screen.Search -> SearchScreen(vm, onOpen, toast)
                                Screen.Explore -> ExploreScreen(vm, onOpen)
                                Screen.WhatToWatch -> WhatToWatchScreen(vm, onOpen)
                                Screen.Roulette -> RouletteScreen(vm, onOpen)
                                Screen.Match -> MatchScreen(vm, onOpen, toast)
                                Screen.Social -> SocialScreen(social, vm, toast)
                                Screen.AdSettings -> AdShieldScreen(toast, ::askCapture)
                                Screen.House -> home?.let { HouseScreen(it, toast) }
                            }
                        }
                    }
                    AnimatedVisibility(panelOpen, enter = fadeIn(), exit = fadeOut()) {
                        Box(Modifier.fillMaxSize().focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()) {
                            QuickPanel(
                                onClose = { panelOpen = false }, toast = toast, onAdShieldOn = ::askCapture,
                                onAdSettings = { open(Screen.AdSettings) },
                            )
                        }
                    }
                }
              }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // "Casa no filme" takes its key everywhere when it is on; without it, Home still answers it
        val choice = if (home != null && !Shortcut.capturing) Shortcut.get(this) else Shortcut.NONE
        if (choice != Shortcut.NONE && event.keyCode == Shortcut.keyOf(choice)) {
            val openHouse = { panelOpen = false; stack.clear(); stack.add(Screen.House) }
            if (!Shortcut.isHold(choice)) {
                if (event.action == KeyEvent.ACTION_UP) openHouse()
                return true
            }
            when (hold.step(event.action == KeyEvent.ACTION_DOWN, event.repeatCount, event.downTime, event.eventTime)) {
                Hold.Step.KEEP -> if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) heldDown = KeyEvent(event)
                Hold.Step.OPEN -> openHouse()
                // A short press: Home gets it whole, as if nothing had stopped it
                Hold.Step.REPLAY -> { heldDown?.let { super.dispatchKeyEvent(it) }; heldDown = null; return super.dispatchKeyEvent(event) }
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            inputTick++
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> LastKey.dx = -1
                KeyEvent.KEYCODE_DPAD_RIGHT -> LastKey.dx = 1
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> LastKey.dx = 0
                KeyEvent.KEYCODE_MENU -> {
                    panelOpen = !panelOpen
                    return true
                }
                KeyEvent.KEYCODE_SEARCH -> {
                    stack.clear(); stack.add(Screen.Search)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** HOME pressed while the launcher is already in front: back to the top of Home. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            stack.clear()
            panelOpen = false
            resetSignal++
        }
    }

    /**
     * The screen-capture token for "Sem anúncios" (guard/ScreenService). Asked by the launcher,
     * never over another app; with the PROJECT_MEDIA appop (tools/ad-guard-setup.sh) the system
     * answers at once, without a dialog.
     */
    private val capture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val granted = result.resultCode == RESULT_OK && data != null
        AdShield.rememberCapture(this, granted)
        if (granted) ScreenService.start(this, result.resultCode, data!!)
    }
    /** Asked on its own once per launcher process (after a reboot), never again after a "no". */
    private var captureAsked = false
    private var healing: Job? = null

    /** From the Central or the "Sem anúncios" screen: the person asked, so a past "no" does not count. */
    private fun askCapture() {
        captureAsked = true
        if (!AdShield.settings(this).readScreen || ScreenService.running(this)) return
        runCatching { capture.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent()) }
            .onFailure { Log.w("AdShield", "screen capture not available", it) }
    }

    override fun onStart() {
        super.onStart()
        // "Casa": the hub connection opens with Home in front, so a click never waits for it
        home?.connect(true)
        // The guard steps aside while Home is in front: browsing pays nothing for it
        lifecycleScope.launch { AdShield.launcherInFront(this@HomeActivity, true) }
        // A network filter that stopped answering must not leave the TV without internet
        if (AdShield.dnsOn(this) && healing?.isActive != true) healing = lifecycleScope.launch {
            if (AdShield.heal(this@HomeActivity)) {
                Toast.makeText(this@HomeActivity, "O filtro na rede parou de responder e foi desligado", Toast.LENGTH_LONG).show()
            }
        }
        if (AdShield.isOn(this) && !captureAsked && !AdShield.captureRefused(this)) askCapture()
    }

    /** Another app took the screen: a Social game freezes instead of running its clocks behind the app. */
    override fun onStop() {
        super.onStop()
        home?.connect(false)
        if (socialUsed) social.background()
        lifecycleScope.launch { AdShield.launcherInFront(this@HomeActivity, false) }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshAppsAsync()
        vm.refreshWatchNext()
    }
}
