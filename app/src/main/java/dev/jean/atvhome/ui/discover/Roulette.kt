package dev.jean.atvhome.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.jean.atvhome.data.Format
import dev.jean.atvhome.data.MediaType
import dev.jean.atvhome.data.TmdbClient
import dev.jean.atvhome.data.Title
import dev.jean.atvhome.ui.HomeViewModel
import dev.jean.atvhome.ui.components.Chip
import dev.jean.atvhome.ui.components.PillButton
import dev.jean.atvhome.ui.components.PosterCard
import dev.jean.atvhome.ui.components.Reel
import dev.jean.atvhome.ui.theme.Tv
import dev.jean.atvhome.ui.theme.LocalAccent
import dev.jean.atvhome.ui.theme.screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


private val ReelWidth = 150.dp
private val ReelHeight = 225.dp

/**
 * Roleta: pick Filme or Série, spin, and three slot-machine reels flash posters from the user's
 * services before stopping one by one on three titles with IMDb 6.5 or more.
 */
@Composable
fun RouletteScreen(vm: HomeViewModel, onOpen: (Title) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(MediaType.MOVIE) }
    var spin by remember { mutableStateOf<HomeViewModel.Spin?>(null) }
    var loading by remember { mutableStateOf(false) }
    var stopped by remember { mutableIntStateOf(0) }
    var frame by remember { mutableIntStateOf(0) }
    val spinButton = remember { FocusRequester() }
    val firstPick = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { spinButton.requestFocus() } }

    fun go() {
        if (loading) return
        loading = true
        stopped = 0
        scope.launch {
            val result = vm.spin(type)
            // Warm the cache so the reels flash real posters, not blanks
            val loader = SingletonImageLoader.get(context)
            (result.reel.take(14) + result.picks.mapNotNull { TmdbClient.image(it.title.poster, "w342") })
                .forEach { loader.enqueue(ImageRequest.Builder(context).data(it).build()) }
            spin = result
            loading = false
            var interval = 55L
            var elapsed = 0L
            while (stopped < result.picks.size) {
                frame++
                delay(interval)
                elapsed += interval
                interval = (interval * 1.07).toLong().coerceAtMost(230L)
                if (elapsed > 1300L + stopped * 750L) stopped++
            }
            runCatching { firstPick.requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize().background(LocalAccent.current.screen()).padding(start = Tv.Edge, top = 30.dp, end = Tv.Edge)) {
        Text("Roleta", style = Tv.Title, color = Tv.Primary)
        Spacer(Modifier.height(3.dp))
        Text("Deixe a sorte escolher: três títulos dos seus streamings, sempre com IMDb 6,5 ou mais", style = Tv.Meta, color = Tv.Tertiary)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip("Filme", type == MediaType.MOVIE, { type = MediaType.MOVIE })
            Chip("Série", type == MediaType.TV, { type = MediaType.TV })
            Spacer(Modifier.width(8.dp))
            PillButton(
                if (spin == null) "Girar" else "Girar de novo",
                { go() },
                icon = "🎲",
                modifier = Modifier.focusRequester(spinButton),
            )
        }
        Spacer(Modifier.height(22.dp))
        val s = spin
        when {
            loading && s == null -> Text("Embaralhando os seus streamings…", style = Tv.Meta, color = Tv.Tertiary)
            s == null -> Text("Escolha Filme ou Série e aperte Girar.", style = Tv.Body, color = Tv.Secondary)
            s.picks.isEmpty() -> Text("Desta vez não saiu nada com IMDb 6,5+. Gire de novo.", style = Tv.Body, color = Tv.Secondary)
            else -> Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                s.picks.forEachIndexed { i, pick ->
                    if (i < stopped) {
                        val where = vm.myProvidersOf(pick).firstOrNull()?.let { vm.packageFor(it)?.let(vm::appFor)?.label ?: it.name }
                        val length = if (pick.title.type == MediaType.TV) Format.seasons(pick.details.seasons) else Format.runtime(pick.details.runtime)
                        PosterCard(
                            image = TmdbClient.image(pick.title.poster, "w342"),
                            title = pick.title.name,
                            line = listOfNotNull(pick.imdb?.let { "IMDb ${Format.rating(it.rating)}" }, length).joinToString(" · "),
                            badge = where,
                            width = ReelWidth,
                            onClick = { onOpen(pick.title) },
                            modifier = if (i == 0) Modifier.focusRequester(firstPick) else Modifier,
                        )
                    } else {
                        Reel(url = s.reel.getOrNull((frame + i * 5) % maxOf(1, s.reel.size)), width = ReelWidth, height = ReelHeight)
                    }
                }
            }
        }
    }
}
