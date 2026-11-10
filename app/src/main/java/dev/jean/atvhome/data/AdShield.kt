package dev.jean.atvhome.data

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.util.Log
import dev.jean.atvhome.guard.AdGuardService
import dev.jean.atvhome.guard.GuardSettings
import dev.jean.atvhome.guard.ScreenService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * "Sem anúncios", two independent switches in the Central. Both need WRITE_SECURE_SETTINGS,
 * granted once over adb (tools/ad-guard-setup.sh). Launcher process only: the guard runs in its own
 * process and never reads these preferences.
 *
 * - The guard (dev.jean.atvhome.guard): mutes, covers or skips ad breaks in any app. It is listed
 *   as an accessibility service only while another app plays sound, so Home, app menus and a
 *   silent TV run exactly as if it were not installed.
 * - The network filter: the TV's Private DNS pointed at an ad-filtering resolver. Opt-in, because
 *   a filtering resolver can break an app that waits for its ad server, or a name only the home
 *   router knows. It never leaves the TV offline: checked when switched on and on every return to
 *   Home, and switched off on its own when it stops answering.
 */
object AdShield {

    private const val TAG = "AdShield"
    const val DNS_HOST = "dns.adguard-dns.com"
    private const val DNS_MODE = "private_dns_mode"           // Settings.Global, hidden constant
    private const val DNS_SPECIFIER = "private_dns_specifier" // Settings.Global, hidden constant
    private const val A11Y_SERVICES = "enabled_accessibility_services" // Settings.Secure
    private const val A11Y_ENABLED = "accessibility_enabled"           // Settings.Secure
    private const val DNS_CHECKS = 10 // one a second: Android validates a new resolver in a few seconds

    /** What switching the network filter on did. */
    enum class Outcome { DONE, NO_ANSWER, REFUSED }

    /** Settings writes block on the settings provider: one at a time, in order, off the main thread. */
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "adshield") }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + writer)

    /** Whether the network in use reaches the internet through our resolver. Replaced in tests. */
    internal var dnsCheck: suspend (Context) -> Boolean = ::dnsAnswers

    /** A pause, or the gap between an ad and the film, must not unbind the guard mid-break. */
    internal var offDelayMs = 10_000L

    // Touched on the writer thread only
    private var homeInFront = true
    private var playing = false
    private var pendingOff: Job? = null
    private var playback: AudioManager.AudioPlaybackCallback? = null

    private fun prefs(context: Context) = context.getSharedPreferences("adshield", Context.MODE_PRIVATE)

    fun allowed(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    // ---- The guard ----

    /** The guard's switch as the user left it. */
    fun isOn(context: Context): Boolean = prefs(context).getBoolean("on", false)

    /** Turns the guard on or off. False when the TV refused. */
    suspend fun set(context: Context, on: Boolean): Boolean = withContext(writer) {
        runCatching {
            check(allowed(context)) { "WRITE_SECURE_SETTINGS not granted" }
            prefs(context).edit().putBoolean("on", on).commit()
            if (on) {
                // Switching on again is how the user says "start over": the guard forgets what it learned
                resetLearning(context)
            } else {
                pendingOff?.cancel()
                guardService(context, false)
                context.stopService(Intent(context, ScreenService::class.java))
            }
        }.onFailure { Log.w(TAG, "set $on", it) }.isSuccess
    }.also { if (it) { if (on) watch(context) else unwatch(context) } }

    /** What the guard does, as the person chose in the "Sem anúncios" screen. */
    fun settings(context: Context): GuardSettings = GuardSettings.load(context.filesDir)

    /** Saves the person's choices; the guard reads them the next time it is listed. False when it failed. */
    suspend fun saveSettings(context: Context, settings: GuardSettings): Boolean = withContext(writer) {
        runCatching {
            GuardSettings.save(context.filesDir, settings)
            if (!settings.readScreen) context.stopService(Intent(context, ScreenService::class.java))
            update(context)
        }.onFailure { Log.w(TAG, "settings", it) }.isSuccess
    }

    /** The guard forgets which reader works in each app, and learns again. */
    fun resetLearning(context: Context) {
        runCatching { File(context.filesDir, AdGuardService.RESET_MARK).createNewFile() }.onFailure { Log.w(TAG, "reset", it) }
    }

    /**
     * Follows sound playing on the TV (a callback, no polling). Called when the launcher starts and
     * when the guard is switched on.
     */
    fun watch(context: Context) {
        if (playback != null || !isOn(context)) return
        val app = context.applicationContext
        val audio = app.getSystemService(AudioManager::class.java)
        val callback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                scope.launch {
                    playbackChanged(app, audio.isMusicActive)
                    // A player reports starting a moment before its sound really reaches the output
                    delay(1_000)
                    playbackChanged(app, audio.isMusicActive)
                }
            }
        }
        runCatching { audio.registerAudioPlaybackCallback(callback, null) }
            .onSuccess { playback = callback }.onFailure { Log.w(TAG, "watch", it) }
    }

    private fun unwatch(context: Context) {
        val callback = playback ?: return
        playback = null
        runCatching { context.applicationContext.getSystemService(AudioManager::class.java).unregisterAudioPlaybackCallback(callback) }
    }

    /** From HomeActivity.onStart/onStop. */
    suspend fun launcherInFront(context: Context, front: Boolean) = withContext(writer) {
        homeInFront = front
        update(context)
    }

    internal suspend fun playbackChanged(context: Context, nowPlaying: Boolean) = withContext(writer) {
        if (playing == nowPlaying) return@withContext // nothing new: what is listed (or about to be) holds
        playing = nowPlaying
        update(context)
    }

    /** Listed only while another app plays sound, and only with something to do: it costs nothing anywhere else. */
    private fun update(context: Context) {
        pendingOff?.cancel()
        pendingOff = null
        if (!isOn(context) || !allowed(context)) return
        if (!settings(context).acts) { write(context, false); return }
        when {
            !homeInFront && playing -> write(context, true)
            homeInFront -> write(context, false) // Home must never pay for it, at once
            else -> pendingOff = scope.launch { delay(offDelayMs); write(context, false) }
        }
    }

    private fun write(context: Context, on: Boolean) {
        runCatching { guardService(context, on) }.onFailure { Log.w(TAG, "guard $on", it) }
    }

    private fun guardService(context: Context, on: Boolean) = listService(context, AdGuardService::class.java, on)

    /**
     * Lists or unlists another of our accessibility services ("Casa no filme"), on the same writer as
     * the guard's so neither overwrites the other's change to the list. Without WRITE_SECURE_SETTINGS
     * it does nothing.
     */
    fun keepListed(context: Context, service: Class<*>, on: Boolean = true) {
        if (!allowed(context)) return
        val app = context.applicationContext
        scope.launch { runCatching { listService(app, service, on) }.onFailure { Log.w(TAG, "list ${service.simpleName}", it) } }
    }

    /** Adds or removes one of our accessibility services in the system's colon-separated list. */
    private fun listService(context: Context, service: Class<*>, on: Boolean) {
        val cr = context.contentResolver
        val p = prefs(context)
        val me = ComponentName(context, service).flattenToString()
        val current = Settings.Secure.getString(cr, A11Y_SERVICES).orEmpty().split(':').filter { it.isNotBlank() }
        val next = if (on) (current + me).distinct() else current - me
        if (next == current) return
        // Accessibility was off with no service at all: it goes back to off, exactly as it was
        if (on && current.isEmpty()) p.edit().putString("a11y_before", Settings.Secure.getString(cr, A11Y_ENABLED) ?: "0").commit()
        Settings.Secure.putString(cr, A11Y_SERVICES, next.joinToString(":"))
        when {
            next.isNotEmpty() -> Settings.Secure.putString(cr, A11Y_ENABLED, "1")
            // Never switch accessibility off for the others (TalkBack, Sony's own services)
            p.contains("a11y_before") -> {
                Settings.Secure.putString(cr, A11Y_ENABLED, p.getString("a11y_before", "0"))
                p.edit().remove("a11y_before").commit()
            }
        }
    }

    // ---- The network filter ----

    /** The network filter's switch. */
    fun dnsOn(context: Context): Boolean = prefs(context).getBoolean("dns", false)

    /** Turns the network filter on or off. On only stays on when the TV still reaches the internet through it. */
    suspend fun setDns(context: Context, on: Boolean): Outcome {
        val written = withContext(writer) {
            runCatching {
                check(allowed(context)) { "WRITE_SECURE_SETTINGS not granted" }
                if (on) pointDns(context) else restoreDns(context)
            }.onFailure { Log.w(TAG, "dns $on", it) }.isSuccess
        }
        if (!written) return Outcome.REFUSED
        if (!on || dnsCheck(context)) return Outcome.DONE
        withContext(writer) { runCatching { restoreDns(context) }.onFailure { Log.w(TAG, "dns back", it) } }
        return Outcome.NO_ANSWER
    }

    /**
     * Back on Home: a resolver that stopped answering is switched off (no internet is far worse than
     * ads). True when it did; the Central then shows the filter off.
     */
    suspend fun heal(context: Context): Boolean {
        if (!dnsOn(context) || dnsCheck(context)) return false
        withContext(writer) { runCatching { restoreDns(context) }.onFailure { Log.w(TAG, "heal", it) } }
        Log.w(TAG, "$DNS_HOST stopped answering: back to the TV's own DNS")
        return true
    }

    private fun pointDns(context: Context) {
        val cr = context.contentResolver
        val p = prefs(context)
        // Remember what was there, to put it back when switched off
        if (!p.getBoolean("dns", false)) {
            p.edit().putString("mode_before", Settings.Global.getString(cr, DNS_MODE))
                .putString("specifier_before", Settings.Global.getString(cr, DNS_SPECIFIER)).commit()
        }
        Settings.Global.putString(cr, DNS_SPECIFIER, DNS_HOST)
        Settings.Global.putString(cr, DNS_MODE, "hostname")
        p.edit().putBoolean("dns", true).commit()
    }

    private fun restoreDns(context: Context) {
        val cr = context.contentResolver
        val p = prefs(context)
        if (!p.getBoolean("dns", false)) return
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

    // ---- Screen capture ----

    /** The person said no to the screen-capture dialog: the launcher stops asking on its own. */
    fun captureRefused(context: Context): Boolean = prefs(context).getBoolean("capture_refused", false)

    fun rememberCapture(context: Context, granted: Boolean) {
        prefs(context).edit().putBoolean("capture_refused", !granted).apply()
    }
}
