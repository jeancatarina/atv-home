package dev.jean.atvhome.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.jean.atvhome.ui.theme.Tv

/** A spinning reel: posters swap with no fade, like a slot machine. */
@Composable
fun Reel(url: String?, width: Dp, height: Dp) {
    val context = LocalContext.current
    Box(Modifier.size(width, height).clip(RoundedCornerShape(Tv.Corner)).background(Tv.Card)) {
        AsyncImage(
            model = remember(url) { ImageRequest.Builder(context).data(url).crossfade(false).build() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Text("?", style = Tv.Display, color = Color.White.copy(alpha = 0.25f), modifier = Modifier.align(Alignment.Center), overflow = TextOverflow.Clip)
    }
}
