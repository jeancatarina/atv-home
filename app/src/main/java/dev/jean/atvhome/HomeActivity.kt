package dev.jean.atvhome

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import dev.jean.atvhome.ui.home.HomeScreen
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
}

class HomeActivity : ComponentActivity() {

    private val vm: HomeViewModel by viewModels()
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
                            }
                        }
                    }
                    AnimatedVisibility(panelOpen, enter = fadeIn(), exit = fadeOut()) {
                        Box(Modifier.fillMaxSize().focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()) {
                            QuickPanel(onClose = { panelOpen = false }, toast = toast)
                        }
                    }
                }
              }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
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

    override fun onResume() {
        super.onResume()
        vm.refreshAppsAsync()
        vm.refreshWatchNext()
    }
}
