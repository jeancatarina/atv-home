package dev.jean.atvhome.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The house colour: a dark olive green picked to sit with the living-room wall. Every screen tints
 * its background and resting buttons with it (the screensaver's panel uses the same green).
 */
val LocalAccent = compositionLocalOf { Accent.Wall }

object Accent {
    val Wall = Color(0xFF3B4A2F)
}

/** Page background: almost black, with a breath of the accent. */
fun Color.screen(): Color = lerp(Color(0xFF0A0A0B), this, 0.35f)

/** Resting pill: translucent white warmed by the accent. */
fun Color.pill(): Color = lerp(Color.White, this, 0.55f).copy(alpha = 0.2f)
