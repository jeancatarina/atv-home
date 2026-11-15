package dev.jean.atvhome.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import dev.jean.atvhome.ui.components.PillButton
import dev.jean.atvhome.ui.components.requestWhenReady
import dev.jean.atvhome.ui.theme.LocalAccent
import dev.jean.atvhome.ui.theme.Tv
import dev.jean.atvhome.ui.theme.screen

/** "Assistir": the four ways to choose what to watch, kept off the top row of Home so it has room for the rest. */
@Composable
fun WatchMenu(onWhatToWatch: () -> Unit, onRoulette: () -> Unit, onMatch: () -> Unit, onExplore: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestWhenReady() }
    Column(Modifier.fillMaxSize().background(LocalAccent.current.screen()).padding(horizontal = Tv.Edge, vertical = 34.dp)) {
        Text("Assistir", style = Tv.Title, color = Tv.Primary)
        Spacer(Modifier.height(3.dp))
        Text("Escolha como decidir o que ver", style = Tv.Meta, color = Tv.Tertiary)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("O que assistir?", onWhatToWatch, Modifier.focusRequester(first), iconVector = Icons.Rounded.AutoAwesome)
            PillButton("Roleta", onRoulette, iconVector = Icons.Rounded.Casino)
            PillButton("Match", onMatch, iconVector = Icons.Rounded.Favorite)
            PillButton("Explorar", onExplore, iconVector = Icons.Rounded.Search)
        }
        Spacer(Modifier.height(18.dp))
        Text("Match: cada um escolhe no celular. Roleta: três títulos sorteados. Explorar: filtros por app, gênero e nota.", style = Tv.Meta, color = Tv.Tertiary)
    }
}
