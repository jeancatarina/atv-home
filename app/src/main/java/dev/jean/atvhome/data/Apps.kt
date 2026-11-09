package dev.jean.atvhome.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An app the user can open from the dock. */
data class TvApp(
    val packageName: String,
    val label: String,
    /** The Android TV 16:9 banner when the app has one; otherwise null and [icon] is drawn on a tile. */
    val banner: Drawable?,
    val icon: Drawable?,
)

class Apps(private val context: Context) {

    private companion object {
        /** Not apps people open: TV tuner setup and plugins that only serve other apps. */
        val SETUP_TOOLS = setOf("com.sony.dtv.tvinput.tuner", "com.rfmarcos.sofadrop")
    }

    private val prefs = context.getSharedPreferences("apps", Context.MODE_PRIVATE)
    private val pm: PackageManager = context.packageManager

    /** Every launchable app except this launcher, in the user's order (new apps at the end). */
    suspend fun load(): List<TvApp> = withContext(Dispatchers.IO) {
        val leanback = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0,
        )
        val phone = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val byPackage = LinkedHashMap<String, ResolveInfo>()
        (leanback + phone).forEach { info ->
            val pkg = info.activityInfo.packageName
            if (pkg != context.packageName && pkg !in byPackage) byPackage[pkg] = info
        }
        val apps = byPackage.values.map { info ->
            val activity = info.activityInfo
            TvApp(
                packageName = activity.packageName,
                label = info.loadLabel(pm).toString(),
                banner = runCatching { activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm) }.getOrNull(),
                icon = runCatching { info.loadIcon(pm) }.getOrNull(),
            )
        }
        val order = order()
        apps.sortedWith(compareBy<TvApp> { order.indexOf(it.packageName).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            .thenBy { it.label.lowercase() })
    }

    fun launch(packageName: String): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Log.w("Apps", "No launch intent for $packageName")
            return false
        }
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    fun order(): List<String> = prefs.getString("order", null)?.split(',')?.filter { it.isNotBlank() }.orEmpty()

    fun saveOrder(packages: List<String>) = prefs.edit().putString("order", packages.joinToString(",")).apply()

    fun hidden(): Set<String> = prefs.getStringSet("hidden", emptySet()).orEmpty()

    /** True until the first arrangement (streaming first, setup tools hidden) has been saved. */
    fun needsFirstArrangement() = !prefs.getBoolean("arranged", false)

    /**
     * First run only: streaming apps first (most relevant service first), YouTube next, then the
     * rest by name; other launchers, tuner setup and plugin apps start hidden.
     */
    fun firstArrangement(all: List<TvApp>, streamingFirst: List<String>) {
        val homeApps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }.toSet()
        val hidden = all.filter { app ->
            app.packageName in homeApps || app.packageName in SETUP_TOOLS ||
                app.label.startsWith("Setup", ignoreCase = true)
        }.map { it.packageName }.toSet()
        val order = streamingFirst + listOf("com.google.android.youtube.tv") +
            all.sortedBy { it.label.lowercase() }.map { it.packageName }
        prefs.edit()
            .putString("order", order.distinct().joinToString(","))
            .putStringSet("hidden", hidden)
            .putBoolean("arranged", true)
            .apply()
    }

    fun setHidden(packageName: String, hidden: Boolean) {
        val set = hidden().toMutableSet().apply { if (hidden) add(packageName) else remove(packageName) }
        prefs.edit().putStringSet("hidden", set).apply()
    }
}

/**
 * Matches TMDB's Brazilian streaming providers to the apps on this TV by name, so nothing depends
 * on hard-coded ids: "Amazon Prime Video" ↔ "Prime Video", "Disney Plus" ↔ "Disney+",
 * "Netflix Standard with Ads" ↔ "Netflix".
 */
object ProviderMatch {

    fun normalize(name: String): String = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace("+", "plus")
        .replace(Regex("[^a-z0-9]"), "")

    private fun firstWord(name: String) = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().split(Regex("[^a-z0-9]+")).firstOrNull { it.isNotBlank() }.orEmpty()

    /** provider id → package name of the installed app that plays it. */
    fun match(providers: List<Provider>, apps: List<Pair<String, String>>): Map<Int, String> {
        val result = HashMap<Int, String>()
        for (provider in providers) {
            val p = normalize(provider.name)
            if (p.isEmpty()) continue
            val exact = apps.firstOrNull { (_, label) ->
                val a = normalize(label)
                a.length >= 3 && (p.contains(a) || a.contains(p))
            }
            if (exact != null) {
                result[provider.id] = exact.first
                continue
            }
            // "Claro video" ↔ "Claro tv+": same brand, renamed product. Only when the brand is unambiguous.
            val brand = firstWord(provider.name)
            if (brand.length >= 4) {
                val sameBrand = apps.filter { (_, label) -> firstWord(label) == brand }
                if (sameBrand.size == 1) result[provider.id] = sameBrand.single().first
            }
        }
        return result
    }
}
