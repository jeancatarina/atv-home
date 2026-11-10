package dev.jean.atvhome.guard

import java.text.Normalizer
import java.util.Locale

/** One short text on screen. [x],[y] = centre, [w],[h] = size, all as fractions of the screen. */
data class Label(
    val text: String, val x: Float, val y: Float, val w: Float = 0f, val h: Float = 0f,
    val viewId: String? = null,
)

/** Cheapest first: the order matters (Readers.kt keeps the cheapest that ever caught an ad). */
enum class Source { SESSION, TREE, SCREEN }

/** What one reader saw in one look. [adFlag] only comes from the media session. */
data class Reading(val source: Source, val adFlag: Boolean = false, val labels: List<Label> = emptyList())

sealed interface Verdict {
    data object None : Verdict
    data object Ad : Verdict
    data class Skippable(val button: Label) : Verdict
}

/** Decides whether a reading is an ad. Knows no app: only words, shapes and limits from [Rules]. */
class AdSignals(private val rules: Rules) {

    private val adWords = rules.adWords.values.flatten().flatMap(::tokens).toSet()
    private val skipWords = rules.skipWords.values.flatten().flatMap(::tokens).toSet()
    private val filler = rules.fillerWords.flatMap(::tokens).toSet()
    private val veto = rules.vetoWords.flatMap(::tokens).toSet()

    /** [countdown]: the label [Countdown] reported for this same reading, if any. */
    fun judge(reading: Reading, countdown: Label? = null): Verdict {
        if (reading.adFlag) return Verdict.Ad
        val labels = reading.labels.filter { it.text.length <= rules.maxLabelChars }
        val badge = labels.firstOrNull(::isBadge)
        val skip = labels.firstOrNull { isSkip(it, badge != null) }
        // A bare word ("Anúncio") only counts on a screen with little text (a player, not a menu) and
        // off the middle, where subtitles are: a line of dialogue saying "Comercial!" is not a break
        val badgeCounts = badge != null &&
            (badge.text.any(Char::isDigit) || (labels.size <= rules.maxPlayerLabels && offCentre(badge)))
        return when {
            skip != null -> Verdict.Skippable(skip)
            badgeCounts || countdown != null -> Verdict.Ad
            else -> Verdict.None
        }
    }

    /** "Anúncio", "Ad 0:15", "Anúncio 1 de 2 · 0:23": an ad word and nothing else but numbers and fillers. */
    private fun isBadge(label: Label): Boolean {
        if (label.viewId != null && rules.adViewIds.any { it in label.viewId }) return true
        val t = tokens(label.text)
        return t.any { it in adWords } && t.all { it in adWords || it in filler || it.all(Char::isDigit) }
    }

    /** "Pular anúncio" always; a bare "Pular" only next to a badge; never "Pular abertura". */
    private fun isSkip(label: Label, hasBadge: Boolean): Boolean {
        if (label.viewId != null && rules.skipViewIds.any { it in label.viewId }) return true
        val t = tokens(label.text)
        if (t.none { it in skipWords } || t.any { it in veto }) return false
        return hasBadge || t.any { it in adWords }
    }

    private fun offCentre(label: Label) = label.x < 0.35f || label.x > 0.65f

    fun hasVeto(labels: List<Label>): Boolean = labels.any { l -> tokens(l.text).any { it in veto } }

    companion object {
        private val MARKS = Regex("\\p{Mn}+")
        private val SPLIT = Regex("[^a-z0-9]+")

        /** "Anúncio 1 de 2 · 0:23" → [anuncio, 1, de, 2, 0, 23] */
        fun tokens(text: String): List<String> =
            MARKS.replace(Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD), "")
                .split(SPLIT).filter { it.isNotEmpty() }
    }
}
