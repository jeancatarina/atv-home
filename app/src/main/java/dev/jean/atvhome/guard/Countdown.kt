package dev.jean.atvhome.guard

import kotlin.math.abs

/**
 * Language-free ad signal: a small number in a corner of the screen that goes down with time
 * while almost nothing else is written on screen (no player controls). Feed it each new screen read.
 */
class Countdown(private val signals: AdSignals) {

    private var last: Pair<Label, Int>? = null
    private var run = 0

    /** A corner number was seen on the last read: the guard looks more often until it is settled. */
    val watching: Boolean get() = last != null

    /** The counter label once it has gone down [NEEDED] reads in a row; null otherwise. */
    fun feed(reading: Reading): Label? {
        val quiet = reading.labels.size <= MAX_LABELS && !signals.hasVeto(reading.labels)
        val now = if (!quiet) null else reading.labels.firstNotNullOfOrNull { l ->
            seconds(l.text)?.takeIf { inCorner(l) }?.let { l to it }
        }
        val prev = last
        when {
            now == null || prev == null || !near(now.first, prev.first) -> run = 0
            // Calm reads come 2 s apart and an OCR read may run late: up to 4 down counts
            prev.second - now.second in 1..4 -> run++
            // One missed or repeated OCR read does not end a countdown that was already proven
            run >= NEEDED && now.second <= prev.second -> Unit
            else -> run = 0
        }
        last = now
        return if (run >= NEEDED) now?.first else null
    }

    fun reset() { last = null; run = 0 }

    companion object {
        const val NEEDED = 3      // decrements in a row = 4 reads
        const val MAX_LABELS = 3  // more text than this = controls or a menu are on screen
        const val MAX_SECONDS = 180
        private val CLOCK = Regex("^(\\d{1,2}):(\\d{2})$")
        private val PLAIN = Regex("^\\d{1,3}$")

        /** "0:15" → 15, "23" → 23; null for anything else or outside 1..[MAX_SECONDS]. */
        fun seconds(text: String): Int? {
            val t = text.trim()
            val value = CLOCK.matchEntire(t)?.let { it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
                ?: if (PLAIN.matches(t)) t.toInt() else return null
            return value.takeIf { it in 1..MAX_SECONDS }
        }

        fun inCorner(l: Label) = (l.x < 0.30f || l.x > 0.70f) && (l.y < 0.25f || l.y > 0.72f)
        fun near(a: Label, b: Label) = abs(a.x - b.x) < 0.06f && abs(a.y - b.y) < 0.06f
    }
}
