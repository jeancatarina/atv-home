package dev.jean.atvhome.ui.home

import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.delay

private const val PREVIEW_MS = 30_000L

/**
 * tvOS's Top Shelf preview: after the title has been on screen a few seconds, its trailer fades in
 * behind the text, muted. [alpha] reports how visible the video is (0–1) so the still backdrop can
 * fade out under it; [sound] unmutes it.
 */
@Composable
fun TrailerBackdrop(
    videoKey: String?,
    play: Boolean,
    sound: Boolean,
    dim: () -> Float,
    onAlpha: (Float) -> Unit,
    onEnded: () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    val fade = remember { Animatable(0f) }
    val scrim = remember { Scrim.overlay().asImageBitmap() }

    // Start the trailer once the title has rested, stop it as soon as anything changes
    LaunchedEffect(videoKey, play, player) {
        val p = player ?: return@LaunchedEffect
        if (!play || videoKey == null) {
            fade.animateTo(0f, tween(300))
            onAlpha(0f)
            p.pause()
            playing = false
            return@LaunchedEffect
        }
        delay(3_500)
        p.mute()
        p.loadVideo(videoKey, 4f)
    }
    // The video shows only after it has really started, past YouTube's own loading frames
    LaunchedEffect(playing) {
        if (playing) {
            delay(700)
            fade.animateTo(1f, tween(900)) { onAlpha(value) }
            // A short preview, like tvOS: about half a minute, then back to the still and onward
            delay(PREVIEW_MS)
            fade.animateTo(0f, tween(900)) { onAlpha(value) }
            player?.pause()
            onEnded()
        } else {
            fade.animateTo(0f, tween(300)) { onAlpha(value) }
        }
    }
    LaunchedEffect(sound, player, playing) {
        val p = player ?: return@LaunchedEffect
        if (sound && playing) { p.unMute(); p.setVolume(100) } else p.mute()
    }

    // The player stays alive (invisible) between titles: creating a WebView per title costs seconds
    AndroidView(
        factory = { context ->
            YouTubePlayerView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                enableAutomaticInitialization = false
                // The remote must never reach the player: a stray OK or arrow paused it and
                // showed YouTube's own pause icon over the Top Shelf
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                lifecycle.addObserver(this)
                val options = IFramePlayerOptions.Builder(context)
                    .controls(0).rel(0).ivLoadPolicy(3).ccLoadPolicy(0).mute(1).autoplay(1).fullscreen(0)
                    .build()
                initialize(
                    object : AbstractYouTubePlayerListener() {
                        override fun onReady(youTubePlayer: YouTubePlayer) { player = youTubePlayer }
                        override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                            when (state) {
                                PlayerConstants.PlayerState.PLAYING -> playing = true
                                PlayerConstants.PlayerState.ENDED -> { playing = false; onEnded() }
                                PlayerConstants.PlayerState.PAUSED, PlayerConstants.PlayerState.UNSTARTED -> playing = false
                                else -> Unit
                            }
                        }
                        override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                            // Embedding disabled or unavailable in Brazil: stay on the still image
                            playing = false
                        }
                    },
                    false,
                    options,
                )
            }
        },
        // Scaled past the screen edges: hides YouTube's title bar, its corner logo and the black bars of 2.39:1 trailers
        modifier = Modifier.fillMaxSize().graphicsLayer {
            val a = fade.value * (1f - dim())
            alpha = a
            scaleX = 1.34f
            scaleY = 1.34f
        },
    )
    if (fade.value > 0f) {
        Image(
            scrim, null, contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fade.value * (1f - dim()) },
        )
    }
    DisposableEffect(Unit) { onDispose { player = null } }
}
