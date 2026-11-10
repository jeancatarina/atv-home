package dev.jean.atvhome.data

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import dev.jean.atvhome.guard.AdGuardService
import dev.jean.atvhome.guard.ScreenService
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * "Sem anúncios": the TV's Private DNS pointed at an ad-filtering resolver, plus the ad-break
 * guard (dev.jean.atvhome.guard). Needs WRITE_SECURE_SETTINGS, granted once over adb
 * (tools/ad-guard-setup.sh). Strict Private DNS means "resolver down = no internet":
 * turning this off in the Central is the way out. Launcher process only: the guard runs in its
 * own process and never reads these preferences.
 */
object AdShield {

    private const val TAG = "AdShield"
    const val DNS_HOST = "dns.adguard-dns.com"
    private const val DNS_MODE = "private_dns_mode"           // Settings.Global, hidden constant
    private const val DNS_SPECIFIER = "private_dns_specifier" // Settings.Global, hidden constant
    private const val A11Y_SERVICES = "enabled_accessibility_services" // Settings.Secure
    private const val A11Y_ENABLED = "accessibility_enabled"           // Settings.Secure

    /** Settings writes block on the settings provider: one at a time, in order, off the main thread. */
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "adshield") }.asCoroutineDispatcher()

    private fun prefs(context: Context) = context.getSharedPreferences("adshield", Context.MODE_PRIVATE)

    fun allowed(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** The switch as the user left it. */
    fun isOn(context: Context): Boolean = prefs(context).getBoolean("on", false)

    /** Turns DNS filtering and the guard on or off together. False when the TV refused. */
    suspend fun set(context: Context, on: Boolean): Boolean = withContext(writer) {
        runCatching {
            check(allowed(context)) { "WRITE_SECURE_SETTINGS not granted" }
            val cr = context.contentResolver
            val p = prefs(context)
            if (on) {
                // Remember what was there, to put it back when switched off
                if (!p.getBoolean("on", false)) {
                    p.edit().putString("mode_before", Settings.Global.getString(cr, DNS_MODE))
                        .putString("specifier_before", Settings.Global.getString(cr, DNS_SPECIFIER)).apply()
                }
                Settings.Global.putString(cr, DNS_SPECIFIER, DNS_HOST)
                Settings.Global.putString(cr, DNS_MODE, "hostname")
                // Switching on again is how the user says "start over": the guard forgets what it learned
                File(context.filesDir, AdGuardService.RESET_MARK).createNewFile()
                // The guard itself waits until another app is in front (launcherInFront)
            } else {
                Settings.Global.putString(cr, DNS_MODE, p.getString("mode_before", null) ?: "opportunistic")
                Settings.Global.putString(cr, DNS_SPECIFIER, p.getString("specifier_before", null))
                guardService(context, false)
                context.stopService(Intent(context, ScreenService::class.java))
            }
            p.edit().putBoolean("on", on).commit()
        }.onFailure { Log.w(TAG, "set $on", it) }.isSuccess
    }

    /**
     * The guard is listed as an accessibility service only while another app is in front. While
     * any such service is enabled, Compose keeps the launcher's semantics tree up to date on every
     * scroll; this way browsing Home costs nothing.
     */
    suspend fun launcherInFront(context: Context, front: Boolean) = withContext(writer) {
        if (!isOn(context) || !allowed(context)) return@withContext
        runCatching { guardService(context, !front) }.onFailure { Log.w(TAG, "guard ${!front}", it) }
    }

    /** Adds or removes our accessibility service in the system's colon-separated list. */
    private fun guardService(context: Context, on: Boolean) {
        val cr = context.contentResolver
        val me = ComponentName(context, AdGuardService::class.java).flattenToString()
        val current = Settings.Secure.getString(cr, A11Y_SERVICES).orEmpty().split(':').filter { it.isNotBlank() }
        val next = if (on) (current + me).distinct() else current - me
        if (next == current) return
        Settings.Secure.putString(cr, A11Y_SERVICES, next.joinToString(":"))
        // Never switch accessibility off for the others (TalkBack, Sony's own services)
        if (next.isNotEmpty()) Settings.Secure.putString(cr, A11Y_ENABLED, "1")
    }
}
