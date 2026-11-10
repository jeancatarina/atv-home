package dev.jean.atvhome.data

import android.content.ComponentName
import android.content.Context
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log
import dev.jean.atvhome.casa.HouseKeyService
import dev.jean.atvhome.tuya.Discovery
import dev.jean.atvhome.tuya.Gateway
import dev.jean.atvhome.tuya.HomeConfig
import dev.jean.atvhome.tuya.Light
import dev.jean.atvhome.tuya.Link
import dev.jean.atvhome.tuya.Located
import dev.jean.atvhome.tuya.Panel
import dev.jean.atvhome.tuya.Plan
import dev.jean.atvhome.tuya.Usage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/** What the Casa screens need from the house: [SmartHome], or a stand-in in screenshot tests. */
interface HouseModel {
    val lights: List<Light>
    val rooms: List<String> get() = Panel.rooms(lights)
    /** cid:dp → on. */
    val states: StateFlow<Map<String, Boolean>>
    val link: StateFlow<Link>
    /** The Casa row: most used first, without the hidden lights. */
    val row: StateFlow<List<Light>>
    /** The floor plan for the model: the one in tuya.json, or every room on one floor. */
    val plan: Plan get() = Plan.auto(rooms)
    suspend fun toggle(light: Light): Boolean
    suspend fun switchAll(group: List<Light>, on: Boolean): Boolean
    fun setHidden(light: Light, hide: Boolean)
}

/**
 * "Casa": the house's lights, straight from the Tuya Zigbee hub on the home network
 * (tuya-core's [Gateway]; no cloud, no server). Exists only when tools/tuya-setup.sh pushed a
 * tuya.json: without it nothing runs and the row is not shown.
 *
 * One per process: the launcher's (the Casa row and "Ver a casa", connected while Home is in front)
 * and the film overlay's ([HouseKeyService], connected while the screen is on). The hub serves both
 * and tells both about every change.
 *
 * "Mais usadas": every off → on the hub reports is a use, whoever pressed it. Only one process
 * counts ([counts]), into a file the other reads: the overlay's when it runs (it is connected all
 * day), the launcher's otherwise.
 */
class SmartHome private constructor(
    private val context: Context,
    private val file: File,
    private var config: HomeConfig,
    private val counts: () -> Boolean,
) : HouseModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("casa", Context.MODE_PRIVATE)
    private val gateway = Gateway(config, scope, log = { Log.i(TAG, it) }, locate = ::locate)
    private val usageFile = File(context.filesDir, USAGE)
    private var idle: Job? = null

    override val lights: List<Light> get() = config.lights
    override val plan: Plan get() = config.plan ?: Plan.auto(rooms)
    override val states: StateFlow<Map<String, Boolean>> = gateway.states
    override val link: StateFlow<Link> = gateway.link

    private val _row = MutableStateFlow(arrange())
    override val row: StateFlow<List<Light>> = _row

    init {
        // From what the hub reported only: a press that failed is not a use
        scope.launch {
            var before = gateway.reported.value
            gateway.reported.collect { now ->
                val on = Usage.turnedOn(lights, before, now)
                before = now
                if (on.isNotEmpty() && counts()) countUses(on)
            }
        }
        // A hub found at a new address is saved, so the next start does not wait for its broadcast
        scope.launch {
            link.collect { l ->
                val at = gateway.located
                if (l is Link.Online && at != null && (at.ip != config.hub.ip || at.version.wire != config.hub.version)) {
                    config = config.copy(hub = config.hub.copy(ip = at.ip, version = at.version.wire))
                    runCatching { write(file, config.encode()); ownWrites += file.lastModified() }.onFailure { Log.w(TAG, "save", it) }
                }
            }
        }
    }

    /** Connects now; with [on] false, disconnects after [graceMs] (coming back soon finds it open). */
    fun connect(on: Boolean, graceMs: Long = IDLE_MS) {
        idle?.cancel()
        if (on) {
            _row.value = arrange()
            gateway.start()
        } else {
            idle = scope.launch { delay(graceMs); gateway.stop() }
        }
    }

    /** For good: disconnects and stops everything this instance runs (the overlay replaces it with a new house). */
    fun close() {
        idle?.cancel()
        gateway.stop()
        scope.cancel()
    }

    fun isOn(light: Light) = Panel.isOn(light, states.value)

    /** A whole switch ("Lavabo") turns all its keys off when any is on, and all on when none is. */
    override suspend fun toggle(light: Light): Boolean {
        val on = !isOn(light)
        return gateway.set(light.cid, light.dps.associateWith { on })
    }

    /**
     * Switches a group ("Apagar tudo", a room): one frame per switch, all switches at once. Off turns
     * off every key that is on; on turns on only lights, never scenes.
     */
    override suspend fun switchAll(group: List<Light>, on: Boolean): Boolean = coroutineScope {
        Panel.switching(group, states.value, on)
            .map { (cid, dps) -> async { gateway.set(cid, dps) } }
            .awaitAll().all { it }
    }

    suspend fun allOff(): Boolean = switchAll(lights, false)

    // --- The row ---------------------------------------------------------------------------------

    /**
     * Hidden by the person; before any choice, what the setup left off the row (scenes, spares,
     * offline). A choice made for other lights (the setup found a new switch, or new names) starts over.
     */
    fun hidden(): Set<String> {
        val chosen = prefs.getStringSet("hidden", null)?.takeIf { prefs.getString("hiddenFor", null) == lightsStamp() }
        return chosen ?: lights.filterNot { it.load }.map { it.key }.toSet()
    }

    /** Same lights, same stamp, in any process and after any restart (no enum or object hash codes). */
    private fun lightsStamp(): String = lights.joinToString("|") { "${it.key}=${it.name}/${it.kind.name}/${it.load}" }.hashCode().toString()

    private fun arrange(): List<Light> {
        val hidden = hidden()
        return readUsage().rank(lights.filter { it.key !in hidden }, System.currentTimeMillis())
    }

    override fun setHidden(light: Light, hide: Boolean) {
        val set = hidden().toMutableSet().apply { if (hide) add(light.key) else remove(light.key) }
        prefs.edit().putStringSet("hidden", set).putString("hiddenFor", lightsStamp()).apply()
        _row.value = arrange()
    }

    private fun readUsage(): Usage = synchronized(usageFile) { Usage.decode(runCatching { usageFile.readText() }.getOrNull()) }

    private fun countUses(on: List<Light>) = synchronized(usageFile) {
        val usage = readUsage()
        on.forEach { usage.used(it.key, System.currentTimeMillis()) }
        runCatching { write(usageFile, usage.encode()) }.onFailure { Log.w(TAG, "usage", it) }
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
        private const val CONFIG = "tuya.json"
        private const val USAGE = "casa-usage.txt"

        /** The launcher's: it counts uses only while the film overlay does not. */
        fun forLauncher(context: Context): SmartHome? = open(context) { !overlayListed(context) }

        /** The film overlay's. */
        fun forOverlay(context: Context): SmartHome? = open(context) { true }

        /** When the house in use was written: the overlay reloads when it changes. */
        fun configStamp(context: Context): Long = File(context.filesDir, CONFIG).lastModified()

        /**
         * Stamps of tuya.json written only to save the hub's new address: the same house, so the
         * overlay keeps its connection and its open sheet. (The other process may still reload once.)
         */
        private val ownWrites: MutableSet<Long> = java.util.concurrent.ConcurrentHashMap.newKeySet()
        fun isOwnWrite(stamp: Long) = stamp in ownWrites

        /**
         * tools/tuya-setup.sh pushes tuya.json to the app's external folder (the only one adb can
         * write); whichever process starts first moves it to internal storage, where no other app can
         * read the hub's key. The row's choices and the use counts stay: they are kept by light.
         */
        @Synchronized
        fun importPushed(context: Context) {
            val pushed = context.getExternalFilesDir(null)?.let { File(it, CONFIG) }?.takeIf { it.isFile } ?: return
            runCatching { write(File(context.filesDir, CONFIG), pushed.readText()); pushed.delete() }
                .onFailure { Log.w(TAG, "import", it) }
        }

        private fun open(context: Context, counts: () -> Boolean): SmartHome? {
            importPushed(context)
            val file = File(context.filesDir, CONFIG)
            if (!file.isFile) return null
            return runCatching { SmartHome(context, file, HomeConfig.decode(file.readText()), counts) }
                .onFailure { Log.w(TAG, CONFIG, it) }.getOrNull()
        }

        /** Whether the film overlay's service is switched on (then it is the one counting uses). */
        fun overlayListed(context: Context): Boolean =
            Settings.Secure.getString(context.contentResolver, "enabled_accessibility_services").orEmpty()
                .contains(ComponentName(context, HouseKeyService::class.java).flattenToString())

        private fun write(file: File, text: String) {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(text)
            tmp.renameTo(file)
        }
    }
}
