package dev.jean.atvhome.ui.theme

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The colour of whatever the Top Shelf is showing, like Apple TV tints its pages after the
 * artwork. Every screen reads it to tint its background and buttons.
 */
val LocalAccent = compositionLocalOf { Accent.Neutral }

object Accent {
    val Neutral = Color(0xFF3A3A3C)
    private val cache = LruCache<String, Color>(64)

    /** A vivid but not neon colour from [bitmap]; cached by [key] (the image URL). */
    suspend fun of(key: String, bitmap: Bitmap): Color = cache.get(key) ?: withContext(Dispatchers.Default) {
        val palette = Palette.from(bitmap).maximumColorCount(16).resizeBitmapArea(160 * 90).generate()
        val swatch = palette.vibrantSwatch ?: palette.mutedSwatch ?: palette.darkVibrantSwatch ?: palette.dominantSwatch
        val color = swatch?.let { Color(it.rgb) } ?: Neutral
        // Keep it calm: halfway to a neutral grey so posters and logos stay the stars
        lerp(color, Neutral, 0.35f).also { cache.put(key, it) }
    }
}

/** Page background: almost black, with a breath of the accent. */
fun Color.screen(): Color = lerp(Color(0xFF0A0A0B), this, 0.22f)

/** Resting pill: translucent white warmed by the accent. */
fun Color.pill(): Color = lerp(Color.White, this, 0.45f).copy(alpha = 0.17f)
