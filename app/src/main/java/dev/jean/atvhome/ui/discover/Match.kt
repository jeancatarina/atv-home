package dev.jean.atvhome.ui.discover

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dev.jean.atvhome.data.Card
import dev.jean.atvhome.data.Format
import dev.jean.atvhome.data.Item
import dev.jean.atvhome.data.MatchServer
import dev.jean.atvhome.data.MatchState
import dev.jean.atvhome.data.MediaType
import dev.jean.atvhome.data.TmdbClient
import dev.jean.atvhome.data.Title
import dev.jean.atvhome.data.TvSystem
import dev.jean.atvhome.ui.HomeViewModel
import dev.jean.atvhome.ui.components.Chip
import dev.jean.atvhome.ui.components.ImdbBadge
import dev.jean.atvhome.ui.components.PillButton
import dev.jean.atvhome.ui.home.TitleArt
import dev.jean.atvhome.ui.theme.LocalAccent
import dev.jean.atvhome.ui.theme.Tv
import dev.jean.atvhome.ui.theme.screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Match: a QR code on the TV, everyone swipes the same deck on their phone, and the first title the
 * whole room likes takes over the screen.
 */
@Composable
fun MatchScreen(vm: HomeViewModel, onOpen: (Title) -> Unit, toast: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(MediaType.MOVIE) }
    var deck by remember { mutableStateOf<List<Item>?>(null) }
    var server by remember { mutableStateOf<MatchServer?>(null) }
    var qr by remember { mutableStateOf<Bitmap?>(null) }
    val firstFocus = remember { FocusRequester() }
    val empty = remember { MutableStateFlow(MatchState()) }
    val state by (server?.updates ?: empty).collectAsState()

    // A new deck (and a fresh server) for each type
    LaunchedEffect(type) {
        server?.stop(); server = null; deck = null; qr = null
        val items = vm.matchDeck(type)
        deck = items
        val cards = items.map { item ->
            Card(
                key = item.title.key,
                title = item.title.name,
                year = item.title.year,
                imdb = item.imdb?.let { Format.rating(it.rating) },
                where = vm.myProvidersOf(item).firstOrNull()?.let { p -> vm.packageFor(p)?.let(vm::appFor)?.label ?: p.name },
                poster = TmdbClient.image(item.title.poster, "w500"),
                overview = item.title.overview,
            )
        }
        val s = withContext(Dispatchers.IO) {
            runCatching { MatchServer(context, cards).apply { start(5_000, false) } }.getOrNull()
        }
        if (s == null) { toast("Não consegui abrir o Match na rede"); return@LaunchedEffect }
        server = s
        qr = withContext(Dispatchers.Default) { s.url?.let(::qrBitmap) }
        runCatching { firstFocus.requestFocus() }
    }
    DisposableEffect(Unit) { onDispose { server?.stop() } }

    val matched = state.match?.let { key -> deck?.firstOrNull { it.title.key == key } }
    if (matched != null) {
        MatchReveal(matched, vm, onOpen, toast, onAgain = { server?.next() })
        return
    }

    Row(Modifier.fillMaxSize().background(LocalAccent.current.screen()).padding(horizontal = Tv.Edge, vertical = 34.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Match", style = Tv.Title, color = Tv.Primary)
            Spacer(Modifier.height(3.dp))
            Text("Todo mundo vota pelo celular. O primeiro que a sala inteira quiser aparece aqui.", style = Tv.Meta, color = Tv.Tertiary)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("Filme", type == MediaType.MOVIE, { type = MediaType.MOVIE }, Modifier.focusRequester(firstFocus))
                Chip("Série", type == MediaType.TV, { type = MediaType.TV })
            }
            Spacer(Modifier.height(26.dp))
            when {
                deck == null -> Text("Montando o baralho com os seus streamings…", style = Tv.Body, color = Tv.Secondary)
                else -> {
                    Text(
                        when (state.people.size) {
                            0 -> "Esperando a primeira pessoa…"
                            1 -> "${state.people.first()} entrou. Falta pelo menos mais uma pessoa."
                            else -> "${state.people.size} pessoas votando"
                        },
                        style = Tv.Headline, color = Tv.Primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.people.forEach { name ->
                            Box(Modifier.background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                                Text(name, style = Tv.Meta, color = Tv.Primary)
                            }
                        }
                    }
                    if (state.votes > 0) {
                        Spacer(Modifier.height(12.dp))
                        Text("${state.votes} votos até agora", style = Tv.Meta, color = Tv.Tertiary)
                    }
                    Spacer(Modifier.height(22.dp))
                    // The deck's first posters fanned out, so the TV hints at what's in play
                    Row(horizontalArrangement = Arrangement.spacedBy((-28).dp)) {
                        deck.orEmpty().take(6).forEachIndexed { i, item ->
                            AsyncImage(
                                TmdbClient.image(item.title.poster, "w185"), null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(86.dp, 129.dp).graphicsLayer { rotationZ = (i - 2.5f) * 4f }
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                        }
                    }
                }
            }
        }
        Column(Modifier.width(300.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(30.dp))
            Box(Modifier.size(250.dp).background(Color.White, RoundedCornerShape(22.dp)).padding(14.dp), contentAlignment = Alignment.Center) {
                val q = qr
                if (q != null) Image(q.asImageBitmap(), "QR code", modifier = Modifier.fillMaxSize())
                else Text("…", style = Tv.Title, color = Color.Black)
            }
            Spacer(Modifier.height(14.dp))
            Text("Aponte a câmera do celular", style = Tv.Headline, color = Tv.Primary)
            Text(server?.url ?: "", style = Tv.Meta, color = Tv.Tertiary)
            Text("Os celulares precisam estar no mesmo Wi-Fi da TV", style = Tv.Caption, color = Tv.Tertiary)
        }
    }
}

@Composable
private fun MatchReveal(item: Item, vm: HomeViewModel, onOpen: (Title) -> Unit, toast: (String) -> Unit, onAgain: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pop = remember { Animatable(0.6f) }
    val first = remember { FocusRequester() }
    LaunchedEffect(item.title.key) {
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
        runCatching { first.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(TmdbClient.image(item.title.backdrop, "w1280"), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.92f), 0.6f to Color.Black.copy(alpha = 0.35f), 1f to Color.Transparent)))
        Column(Modifier.padding(start = Tv.Edge, top = 70.dp)) {
            Text(
                "♥ Deu match!", style = Tv.Display, color = Color(0xFFFF375F),
                modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) },
            )
            Spacer(Modifier.height(18.dp))
            TitleArt(item, maxHeight = 86.dp)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImdbBadge(item.imdb)
                Text(Format.metaLine(item.details), style = Tv.Meta, color = Tv.Secondary)
            }
            Spacer(Modifier.height(8.dp))
            Text(item.title.overview, style = Tv.Body, color = Tv.Secondary, maxLines = 3, modifier = Modifier.widthIn(max = 480.dp))
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val app = vm.myProvidersOf(item).firstOrNull()?.let(vm::packageFor)?.let(vm::appFor)
                if (app != null) {
                    PillButton("Assistir no ${app.label}", { scope.launch { vm.watch(item, app.packageName)?.let(toast) } }, icon = "▶", modifier = Modifier.focusRequester(first))
                }
                item.details.trailerKey?.let { key ->
                    PillButton("Trailer", { TvSystem.trailer(context, key) }, modifier = if (app == null) Modifier.focusRequester(first) else Modifier)
                }
                PillButton("Detalhes", { onOpen(item.title) })
                PillButton("Outro match", onAgain)
            }
        }
    }
}

private fun qrBitmap(text: String): Bitmap {
    val size = 512
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 0))
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
