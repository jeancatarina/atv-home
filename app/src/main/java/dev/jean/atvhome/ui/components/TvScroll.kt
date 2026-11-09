package dev.jean.atvhome.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * tvOS scrolling: nothing moves while the focused item is fully on screen; when it isn't, it lands
 * 10% in from the edge. Compose's TV default instead pivots every focus change to 30% of the
 * screen, which pushed the Top Shelf away as soon as the dock got focus.
 */
@OptIn(ExperimentalFoundationApi::class)
object TvScroll : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val trailing = offset + size
        if (offset >= 0f && trailing <= containerSize) return 0f
        val target = containerSize * 0.1f
        return if (size <= containerSize - target) offset - target else offset
    }
}
