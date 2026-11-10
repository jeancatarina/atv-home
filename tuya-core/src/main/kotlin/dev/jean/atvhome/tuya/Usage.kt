package dev.jean.atvhome.tuya

import kotlin.math.pow

/**
 * "Mais usadas": each light keeps a score that grows by one every time it is turned on (from the
 * TV, the wall or the phone: the hub reports all of them) and halves every [HALF_LIFE_MS], so the
 * row follows the house's habits of the last weeks, not of last year.
 */
class Usage(scores: Map<String, Score> = emptyMap()) {
    data class Score(val value: Double, val at: Long)

    private val scores = HashMap(scores)

    fun score(key: String, now: Long): Double = scores[key]?.let { decayed(it, now) } ?: 0.0

    fun used(key: String, now: Long) {
        scores[key] = Score(score(key, now) + 1.0, now)
    }

    /** Most used first; lights never used keep the setup's order (rooms, then names). */
    fun rank(lights: List<Light>, now: Long): List<Light> =
        lights.withIndex().sortedWith(compareByDescending<IndexedValue<Light>> { score(it.value.key, now) }.thenBy { it.index }).map { it.value }

    /** `key=score@millis` lines, for SharedPreferences. */
    fun encode(): String = scores.entries.joinToString("\n") { (k, s) -> "$k=${s.value}@${s.at}" }

    private fun decayed(s: Score, now: Long) = s.value * 0.5.pow((now - s.at).coerceAtLeast(0) / HALF_LIFE_MS.toDouble())

    companion object {
        const val HALF_LIFE_MS = 30L * 24 * 60 * 60 * 1000

        fun decode(text: String?): Usage = Usage(
            text.orEmpty().lines().mapNotNull { line ->
                val k = line.substringBeforeLast('=', "").ifEmpty { return@mapNotNull null }
                val v = line.substringAfterLast('=').split('@')
                val value = v.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
                val at = v.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
                k to Score(value, at)
            }.toMap(),
        )

        /**
         * Lights that went from off to on between two snapshots of the hub's state. A key seen for the
         * first time (the query on connect) is not a use.
         */
        fun turnedOn(lights: List<Light>, before: Map<String, Boolean>, after: Map<String, Boolean>): List<Light> =
            lights.filter { l -> l.keys.any { before[it] == false && after[it] == true } && l.keys.none { before[it] == true } }
    }
}
