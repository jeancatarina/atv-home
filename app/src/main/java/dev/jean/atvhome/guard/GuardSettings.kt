package dev.jean.atvhome.guard

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * What the person chose in the "Sem anúncios" screen. Written by the launcher, read by the guard
 * each time it is bound: a file, because the two processes do not share preferences live.
 */
@Serializable
data class GuardSettings(
    /** Press a "skip" button the app exposes. */
    val skip: Boolean = true,
    /** Also tap where the screen reader saw "Pular", when the app exposes no button. */
    val tapSkip: Boolean = false,
    /** Mute an ad that cannot be skipped. Off: it plays as the app shows it. */
    val mute: Boolean = false,
    /** Read the edges of the screen (OCR), for apps that show the break to nothing else. */
    val readScreen: Boolean = true,
    /** A falling number in a corner counts as an ad in any language (only in apps with a confirmed ad before). */
    val countdown: Boolean = true,
) {
    /** With nothing to do in a break, the guard is never listed at all. */
    val acts: Boolean get() = skip || mute

    companion object {
        private const val FILE = "guard_settings.json"
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun load(dir: File): GuardSettings =
            runCatching { json.decodeFromString(serializer(), File(dir, FILE).readText()) }.getOrDefault(GuardSettings())

        /** Written whole and then renamed, so the guard never reads half a file. */
        fun save(dir: File, settings: GuardSettings) {
            val tmp = File(dir, "$FILE.tmp")
            tmp.writeText(json.encodeToString(serializer(), settings))
            check(tmp.renameTo(File(dir, FILE))) { "could not save $FILE" }
        }
    }
}
