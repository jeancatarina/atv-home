package dev.jean.atvhome.guard

import android.content.Context

/**
 * Remembers, per package, the cheapest reader that ever caught an ad there, so the costly ones
 * stop running where they are not needed. A newly installed app has no entry: all readers try,
 * and the first to catch an ad wins. Lives in the guard process only.
 */
class Readers(context: Context) {
    private val prefs = context.getSharedPreferences("guard_readers", Context.MODE_PRIVATE)

    fun wants(pkg: String, source: Source, inBreak: Boolean): Boolean = ReaderChoice.wants(known(pkg), source, inBreak)

    fun remember(pkg: String, source: Source) {
        val known = known(pkg)
        if (known == null || source.ordinal < known.ordinal) prefs.edit().putString(pkg, source.name).apply()
    }

    /** "Sem anúncios" switched on again: learn every app from scratch. */
    fun reset() { prefs.edit().clear().apply() }

    private fun known(pkg: String): Source? = prefs.getString(pkg, null)?.let { runCatching { Source.valueOf(it) }.getOrNull() }
}

object ReaderChoice {
    /**
     * The media session costs one binder call: always. The node tree makes the app's own UI thread
     * work, so it runs in a new app, in an app where it once caught an ad, or during a break (the
     * picture is covered then, and the skip button must be found). The screen runs in a new app or
     * in one only it ever caught.
     */
    fun wants(known: Source?, source: Source, inBreak: Boolean): Boolean = when (source) {
        Source.SESSION -> true
        Source.TREE -> known == null || known == Source.TREE || inBreak
        Source.SCREEN -> known == null || known == Source.SCREEN
    }
}
