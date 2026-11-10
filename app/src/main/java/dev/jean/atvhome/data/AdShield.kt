package dev.jean.atvhome.data

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.util.Log
import dev.jean.atvhome.guard.AdGuardService
import dev.jean.atvhome.guard.ScreenService
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * "Sem anúncios": the TV's Private DNS pointed at an ad-filtering resolver, plus the ad-break
 * guard (dev.jean.atvhome.guard). Needs WRITE_SECURE_SETTINGS, granted once over adb
 * (tools/ad-guard-setup.sh). Strict Private DNS means "resolver down = no internet", so the DNS
 * part is checked when switched on and on every return to Home, and steps aside on its own when it
 * stops answering. Launcher process only: the guard runs in its own process and never reads these
 * preferences.
 */
object AdShield {

    private const val TAG = "AdShield"
    const val DNS_HOST = "dns.adguard-dns.com"
    private const val DNS_MODE = "private_dns_mode"           // Settings.Global, hidden constant
    private const val DNS_SPECIFIER = "private_dns_specifier" // Settings.Global, hidden constant
    private const val A11Y_SERVICES = "enabled_accessibility_services" // Settings.Secure
    private const val A11Y_ENABLED = "accessibility_enabled"           // Settings.Secure
    private const val DNS_CHECKS = 10 // one a second: Android validates a new resolver in a few seconds

    /** What a switch did. [NO_DNS]: the guard is on, but the resolver did not answer and the DNS went back. */
    enum class Outcome { DONE, NO_DNS, REFUSED }

    /** Settings writes block on the settings provider: one at a time, in order, off the main thread. */
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "adshield") }.asCoroutineDispatcher()

    /** Whether the network in use reaches the internet through our resolver. Replaced in tests. */
    internal var dnsCheck: suspend (Context) -> Boolean = ::dnsAnswers

    private fun prefs(context: Context) = context.getSharedPreferences("adshield", Context.MODE_PRIVATE)

    fun allowed(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** The switch as the user left it. */
    fun isOn(context: Context): Boolean = prefs(context).getBoolean("on", false)

    /** Turns DNS filtering and the guard on or off together. */
    suspend fun set(context: Context, on: Boolean): Outcome {
        val written = withContext(writer) {
            runCatching {
                check(allowed(context)) { "WRITE_SECURE_SETTINGS not granted" }
                val p = prefs(context)
                if (on) {
                    // Remember what was there, to put it back when switched off
                    if (!p.getBoolean("on", false)) {
                        val cr = context.contentResolver
                        p.edit().putString("mode_before", Settings.Global.getString(cr, DNS_MODE))
                            .putString("specifier_before", Settings.Global.getString(cr, DNS_SPECIFIER)).apply()
                    }
                    pointDns(context)
                    // Switching on again is how the user says "start over": the guard forgets what it learned
                    File(context.filesDir, AdGuardService.RESET_MARK).createNewFile()
                    // The guard itself waits until another app is in front (launcherInFront)
                } else {
                    restoreDns(context)
                    guardService(context, false)
                    context.stopService(Intent(context, ScreenService::class.java))
                }
                p.edit().putBoolean("on", on).commit()
            }.onFailure { Log.w(TAG, "set $on", it) }.isSuccess
        }
        if (!written) return Outcome.REFUSED
        if (!on || dnsCheck(context)) return Outcome.DONE
        withContext(writer) { runCatching { restoreDns(context) }.onFailure { Log.w(TAG, "dns back", it) } }
        return Outcome.NO_DNS
    }

    /**
     * Back on Home: if the network stopped reaching the internet through our resolver, the TV goes
     * back to its own DNS (no internet is far worse than ads). True when it did.
     */
    suspend fun heal(context: Context): Boolean {
        if (!isOn(context) || !prefs(context).getBoolean("dns", false) || dnsCheck(context)) return false
        withContext(writer) { runCatching { restoreDns(context) }.onFailure { Log.w(TAG, "heal", it) } }
        Log.w(TAG, "$DNS_HOST stopped answering: back to the TV's own DNS")
        return true
    }

    private fun pointDns(context: Context) {
        val cr = context.contentResolver
        Settings.Global.putString(cr, DNS_SPECIFIER, DNS_HOST)
        Settings.Global.putString(cr, DNS_MODE, "hostname")
        prefs(context).edit().putBoolean("dns", true).commit()
    }

    private fun restoreDns(context: Context) {
        val cr = context.contentResolver
        val p = prefs(context)
        Settings.Global.putString(cr, DNS_MODE, p.getString("mode_before", null) ?: "opportunistic")
        Settings.Global.putString(cr, DNS_SPECIFIER, p.getString("specifier_before", null))
        p.edit().putBoolean("dns", false).commit()
    }

    /** Polls for up to [DNS_CHECKS] s; with no network at all there is nothing to judge. */
    private suspend fun dnsAnswers(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28) return true // no Private DNS before Android 9: the setting does nothing
        val cm = context.getSystemService(ConnectivityManager::class.java)
        repeat(DNS_CHECKS) {
            val net = cm.activeNetwork ?: return true
            val validated = cm.getNetworkCapabilities(net)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            if (validated && cm.getLinkProperties(net)?.privateDnsServerName == DNS_HOST) return true
            delay(1_000)
        }
        return false
    }

    /** The person said no to the screen-capture dialog: the launcher stops asking on its own. */
    fun captureRefused(context: Context): Boolean = prefs(context).getBoolean("capture_refused", false)

    fun rememberCapture(context: Context, granted: Boolean) {
        prefs(context).edit().putBoolean("capture_refused", !granted).apply()
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
