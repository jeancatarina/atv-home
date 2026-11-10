package dev.jean.atvhome.data

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import dev.jean.atvhome.tuya.Discovery
import dev.jean.atvhome.tuya.Gateway
import dev.jean.atvhome.tuya.HomeConfig
import dev.jean.atvhome.tuya.Light
import dev.jean.atvhome.tuya.Link
import dev.jean.atvhome.tuya.Located
import dev.jean.atvhome.tuya.Usage
import dev.jean.atvhome.tuya.keyOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * "Casa": the house's lights, straight from the Tuya Zigbee hub on the home network
 * (tuya-core's [Gateway]; no cloud, no server). Exists only when tools/tuya-setup.sh pushed a
 * tuya.json: without it nothing runs and the row is not shown.
 *
 * The connection is open while the launcher is in front, and closes [IDLE_MS] after it leaves, so
 * coming back from an app finds it still open and a click goes out at once.
 *
 * The row puts the most used lights first ([Usage]), rearranged only when Home comes to the front so
 * a tile never moves under the focus.
 */
class SmartHome private constructor(private val context: Context, private val file: File, private var config: HomeConfig) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("casa", Context.MODE_PRIVATE)
    private val gateway = Gateway(config, scope, log = { Log.i(TAG, it) }, locate = ::locate)
    private var idle: Job? = null

    val lights: List<Light> get() = config.lights
    /** cid:dp → on. */
    val states: StateFlow<Map<String, Boolean>> = gateway.states
    val link: StateFlow<Link> = gateway.link

    private val usage = Usage.decode(prefs.getString("usage", null))
    private val _row = MutableStateFlow(arrange())
    /** The Casa row: most used first, without the hidden lights. */
    val row: StateFlow<List<Light>> = _row

    init {
        // Every off → on the hub reports is a use, whoever pressed it
        scope.launch {
            var before = states.value
            states.collect { now ->
                val on = Usage.turnedOn(lights, before, now)
                before = now
                if (on.isNotEmpty()) synchronized(usage) {
                    on.forEach { usage.used(it.key, System.currentTimeMillis()) }
                    prefs.edit().putString("usage", usage.encode()).apply()
                }
            }
        }
        // A hub found at a new address is saved, so the next start does not wait for its broadcast
        scope.launch {
            link.collect { l ->
                val at = gateway.located
                if (l is Link.Online && at != null && (at.ip != config.hub.ip || at.version.wire != config.hub.version)) {
                    config = config.copy(hub = config.hub.copy(ip = at.ip, version = at.version.wire))
                    runCatching { save(file, config) }.onFailure { Log.w(TAG, "save", it) }
                }
            }
        }
    }

    fun foreground(front: Boolean) {
        idle?.cancel()
        if (front) {
            _row.value = arrange()
            gateway.start()
        } else {
            idle = scope.launch { delay(IDLE_MS); gateway.stop() }
        }
    }

    fun isOn(light: Light) = isOn(light, states.value)

    /** A whole switch ("Lavabo") turns all its keys off when any is on, and all on when none is. */
    suspend fun toggle(light: Light): Boolean {
        val on = !isOn(light)
        return gateway.set(light.cid, light.dps.associateWith { on })
    }

    /** Every key that is on, one frame per switch, all switches at once. */
    suspend fun allOff(): Boolean = coroutineScope {
        val now = states.value
        lights.flatMap { l -> l.dps.filter { now[keyOf(l.cid, it)] == true }.map { l.cid to it } }.distinct()
            .groupBy({ it.first }, { it.second })
            .map { (cid, dps) -> async { gateway.set(cid, dps.associateWith { false }) } }
            .awaitAll().all { it }
    }

    // --- The row ---------------------------------------------------------------------------------

    /** Hidden by the person; before any choice, the keys the setup marked as not wired (scenes, spares). */
    fun hidden(): Set<String> = prefs.getStringSet("hidden", null) ?: lights.filterNot { it.load }.map { it.key }.toSet()

    private fun arrange(): List<Light> {
        val hidden = hidden()
        return synchronized(usage) { usage.rank(lights.filter { it.key !in hidden }, System.currentTimeMillis()) }
    }

    fun setHidden(light: Light, hide: Boolean) {
        val set = hidden().toMutableSet().apply { if (hide) add(light.key) else remove(light.key) }
        prefs.edit().putStringSet("hidden", set).apply()
        _row.value = arrange()
    }

    /** UDP broadcasts reach an app only while it holds a multicast lock on some Wi-Fi drivers. */
    private suspend fun locate(): Located? {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = runCatching { wifi?.createMulticastLock("casa")?.apply { setReferenceCounted(false); acquire() } }.getOrNull()
        return try {
            Discovery.find(config.hub.id).also { Log.i(TAG, "broadcast: ${it ?: "hub not heard"}") }
        } finally {
            runCatching { lock?.release() }
        }
    }

    companion object {
        private const val TAG = "SmartHome"

        private const val IDLE_MS = 30_000L

        fun isOn(light: Light, states: Map<String, Boolean>) = light.keys.any { states[it] == true }

        /**
         * Reads tuya.json. tools/tuya-setup.sh pushes it to the app's external folder (the only one adb
         * can write); it is moved to internal storage, where no other app can read the hub's key.
         */
        fun load(context: Context): SmartHome? {
            val file = File(context.filesDir, "tuya.json")
            val pushed = context.getExternalFilesDir(null)?.let { File(it, "tuya.json") }
            if (pushed != null && pushed.isFile) {
                runCatching { pushed.copyTo(file, overwrite = true); pushed.delete() }.onFailure { Log.w(TAG, "import", it) }
                context.getSharedPreferences("casa", Context.MODE_PRIVATE).edit().clear().apply()
            }
            if (!file.isFile) return null
            return runCatching { SmartHome(context, file, HomeConfig.decode(file.readText())) }
                .onFailure { Log.w(TAG, "tuya.json", it) }.getOrNull()
        }

        private fun save(file: File, config: HomeConfig) {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(config.encode())
            tmp.renameTo(file)
        }
    }
}
