package dev.jean.atvhome.tuya

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.security.MessageDigest

/** What a Tuya device says about itself in its UDP broadcast. */
data class Announce(val id: String, val ip: String, val version: Version?, val productKey: String?)

/**
 * Finds Tuya devices by the broadcast they send every few seconds: 6666 (3.1, clear text), 6667
 * (3.3/3.4, AES-ECB with a key every Tuya device shares) and 7000 (3.5, AES-GCM with that key).
 * Used when the hub is not at the address in tuya.json (the router gave it a new one).
 */
object Discovery {
    private val PORTS = listOf(6666, 6667, 7000)
    internal val UDP_KEY: ByteArray = MessageDigest.getInstance("MD5").digest("yGAdlopoPVldABfn".toByteArray())

    /** Listens up to [timeoutMs] for the device [id]. */
    suspend fun find(id: String, timeoutMs: Long = 8_000): Located? =
        listen(timeoutMs) { it.id == id }.firstOrNull { it.id == id }?.let { Located(it.ip, it.version ?: Version.V33) }

    /** Everything heard in [timeoutMs], or until [enough] says so. */
    suspend fun listen(timeoutMs: Long, enough: (Announce) -> Boolean = { false }): List<Announce> = withContext(Dispatchers.IO) {
        val heard = java.util.concurrent.ConcurrentHashMap<String, Announce>()
        val done = CompletableDeferred<Unit>()
        val sockets = PORTS.mapNotNull { port ->
            runCatching {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 500
                    bind(InetSocketAddress(port))
                }
            }.getOrNull()
        }
        try {
            coroutineScope {
                val readers = sockets.map { socket ->
                    launch {
                        val buf = ByteArray(2048)
                        while (isActive && !done.isCompleted) {
                            val packet = DatagramPacket(buf, buf.size)
                            try { socket.receive(packet) } catch (e: SocketTimeoutException) { continue } catch (e: Exception) { break }
                            val announce = decode(buf.copyOf(packet.length)) ?: continue
                            heard[announce.id] = announce
                            if (enough(announce)) done.complete(Unit)
                        }
                    }
                }
                withTimeoutOrNull(timeoutMs) { done.await() }
                done.complete(Unit)
                readers.forEach { it.cancel() }
            }
        } finally {
            sockets.forEach { it.close() }
        }
        heard.values.toList()
    }

    /** One broadcast packet, or null if it is not a Tuya announce. */
    fun decode(packet: ByteArray): Announce? = runCatching {
        val prefix = if (packet.size >= 4) DataInputStream(ByteArrayInputStream(packet)).readInt() else return null
        val codec = Codec(if (prefix == Codec.PREFIX_6699) Version.V35 else Version.V33, UDP_KEY)
        val frame = codec.read(DataInputStream(ByteArrayInputStream(packet)), fromDevice = true)
        val json = TuyaJson.parseToJsonElement(frame.text).jsonObject
        fun str(k: String) = (json[k] as? JsonPrimitive)?.contentOrNull
        Announce(
            id = str("gwId") ?: return null,
            ip = str("ip") ?: return null,
            version = str("version")?.let { v -> Version.entries.firstOrNull { it.wire == v } },
            productKey = str("productKey"),
        )
    }.getOrNull()
}
