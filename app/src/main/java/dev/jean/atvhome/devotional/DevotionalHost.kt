package dev.jean.atvhome.devotional

import android.content.Context
import android.os.PowerManager
import android.util.Log
import dev.jean.atvhome.social.net.LanServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.LocalDate

/**
 * The devotional board and its site, shared by the screen on the TV and the phones. It lives with the
 * app process (not with a screen) so the site answers with the launcher in the background; the
 * [DevotionalService] keeps that process, and a partial wake lock keeps the CPU, for as long as the TV
 * lets the network stay up.
 */
object DevotionalHost {
    private const val TAG = "Devotional"
    private var board: Devotional? = null
    private var server: DevotionalServer? = null
    private var lock: PowerManager.WakeLock? = null
    private val tick = MutableStateFlow(0)

    /** Bumps whenever the board changes, so the TV screen redraws when a phone checks in. */
    val changes: StateFlow<Int> = tick

    @Synchronized
    fun board(context: Context): Devotional = board ?: run {
        val store = DevotionalStore(File(context.applicationContext.filesDir, "devotional.json"))
        Devotional(store.load()) { data -> store.save(data); tick.value++ }.also { board = it }
    }

    /** The site's address on the home network, or null when it is not running. */
    val url: String? get() = server?.url

    @Synchronized
    fun start(context: Context) {
        if (server != null) return
        val app = context.applicationContext
        val s = DevotionalServer(board(app), { app.assets.open("devotional.html").bufferedReader().use { it.readText() } }, { LocalDate.now() })
        runCatching { s.start(5_000, false) }
            .onSuccess { server = s; tick.value++ }
            .onFailure { Log.w(TAG, "could not open the site", it) }
        if (lock == null) {
            lock = app.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "atvhome:devotional").apply { setReferenceCounted(false); acquire() }
        }
    }

    @Synchronized
    fun stop() {
        server?.stop(); server = null
        lock?.takeIf { it.isHeld }?.release(); lock = null
    }

    val lanIp: String? get() = LanServer.localIp()
}
