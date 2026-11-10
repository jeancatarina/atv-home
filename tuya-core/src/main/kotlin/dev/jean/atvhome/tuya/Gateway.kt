package dev.jean.atvhome.tuya

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

/** Where the hub is, as its broadcast or the config says. */
data class Located(val ip: String, val version: Version)

sealed interface Link {
    data object Off : Link
    data object Connecting : Link
    data class Online(val version: Version) : Link
    data class Unreachable(val reason: String) : Link
    /** The local key does not match: only re-running the setup fixes it, so retries are slow. */
    data class BadKey(val reason: String) : Link
}

/**
 * One persistent connection to a Tuya Zigbee hub. Commands go out on the socket that is already
 * open (no connect or handshake per click), and the hub pushes every change, from the wall or the
 * phone, on the same socket: [states] is always current without polling.
 *
 * [states] maps [keyOf] (cid:dp) to on/off. [set] writes the new value at once (the tile changes on
 * the same frame) and puts the old one back if the hub does not confirm in [confirmMs].
 */
class Gateway(
    private val config: HomeConfig,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {},
    private val locate: suspend () -> Located? = { null },
    private val connect: (String, Int) -> Socket = ::openSocket,
    private val heartbeatMs: Long = 10_000,
    private val confirmMs: Long = 1_500,
) {
    private val hub = config.hub
    private val _link = MutableStateFlow<Link>(Link.Off)
    val link: StateFlow<Link> = _link
    private val _states = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val states: StateFlow<Map<String, Boolean>> = _states

    private var job: Job? = null
    @Volatile private var session: Session? = null
    /** Learned from the broadcast when the config had none or the hub moved. */
    @Volatile private var where: Located? = hub.ip?.let { ip -> hub.version?.let { Located(ip, Version.of(it)) } }

    val online: Boolean get() = session != null
    /** Where the hub was last reached or heard: the app saves it so the next start skips the search. */
    val located: Located? get() = where

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) { run() }
    }

    fun stop() {
        job?.cancel()
        job = null
        session?.close()
        _link.value = Link.Off
    }

    /** Turns keys of one sub-device on or off in one frame (several keys: "Apagar tudo"). */
    suspend fun set(cid: String, dps: Map<Int, Boolean>): Boolean {
        val s = session ?: return false
        val keys = dps.mapKeys { keyOf(cid, it.key) }
        val before = _states.value
        _states.update { it + keys }
        val pending = Pending(cid, dps)
        s.pending.add(pending)
        val ok = try {
            val started = System.nanoTime()
            withContext(Dispatchers.IO) { s.control(cid, dps) }
            (withTimeoutOrNull(confirmMs) { pending.done.await() } == true)
                .also { log("set $cid $dps: ${if (it) "ok" else "no answer"} in ${(System.nanoTime() - started) / 1_000_000} ms") }
        } catch (e: IOException) {
            log("set $cid failed: ${e.message}")
            false
        } finally {
            s.pending.remove(pending)
        }
        if (!ok) _states.update { now -> now + keys.filter { (k, v) -> now[k] == v }.mapValues { (k, v) -> before[k] ?: !v } }
        return ok
    }

    suspend fun set(cid: String, dp: Int, on: Boolean) = set(cid, mapOf(dp to on))

    private suspend fun run() {
        var backoff = 1_000L
        var rejected = 0
        while (coroutineContext.isActive) {
            val at = where ?: locate()?.also { where = it }
            if (at == null) {
                _link.value = Link.Unreachable("hub not found on the network")
            } else try {
                _link.value = Link.Connecting
                session(at)
                backoff = 1_000L
                rejected = 0
                continue
            } catch (e: CancellationException) {
                throw e
            } catch (e: TuyaException.Rejected) {
                if (!coroutineContext.isActive) break
                // Once can be the network; three times in a row is the key
                if (++rejected < 3) { log("handshake: ${e.message}"); delay(500); continue }
                rejected = 0
                log("bad key: ${e.message}")
                _link.value = Link.BadKey(e.message.orEmpty())
                delay(60_000)
                continue
            } catch (e: TuyaException.BadKey) {
                log("bad key: ${e.message}")
                _link.value = Link.BadKey(e.message.orEmpty())
                delay(60_000)
                continue
            } catch (e: IOException) {
                // stop() closes the socket to wake the blocked read: that is not a failure
                if (!coroutineContext.isActive) break
                log("hub at ${at.ip}: ${e.message}")
                _link.value = Link.Unreachable(e.message ?: e.javaClass.simpleName)
                // Nothing listening there any more: the hub may have a new address, look for it again
                if (e is ConnectException || e is SocketTimeoutException) locate()?.let { where = it }
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(30_000L)
        }
    }

    private suspend fun session(at: Located) {
        val started = System.nanoTime()
        val socket = connect(at.ip, hub.port)
        val s = Session(socket, Codec(at.version, hub.key.toByteArray(Charsets.UTF_8)))
        var heartbeat: Job? = null
        var queries: Job? = null
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 3_000
            if (at.version.negotiates) s.negotiate()
            socket.soTimeout = (heartbeatMs * 3).toInt()
            session = s
            _link.value = Link.Online(at.version)
            log("online ${at.ip} v${at.version.wire} in ${(System.nanoTime() - started) / 1_000_000} ms")
            // The hub drops queries that arrive all at once: one every QUERY_GAP_MS, and once more for
            // the switches that did not answer (an offline one never does)
            queries = scope.launch(Dispatchers.IO) {
                for (round in 0..1) {
                    val ask = config.cids.filter { cid -> round == 0 || config.lights.any { it.cid == cid && it.keys.any { k -> k !in _states.value } } }
                    for (cid in ask) { runCatching { s.query(cid) }; delay(QUERY_GAP_MS) }
                    if (round == 0) delay(1_500)
                }
            }
            heartbeat = scope.launch(Dispatchers.IO) {
                while (isActive) {
                    delay(heartbeatMs)
                    runCatching { s.heartbeat() }.onFailure { s.close() }
                }
            }
            while (coroutineContext.isActive) handle(s, s.read())
        } finally {
            queries?.cancel()
            heartbeat?.cancel()
            if (session === s) session = null
            s.close()
            s.pending.forEach { it.done.complete(false) }
        }
    }

    private fun handle(s: Session, frame: Frame) {
        val update = parseStatus(frame.payload)
        if (update != null) {
            val (cid, dps) = update
            _states.update { it + dps.mapKeys { (dp, _) -> keyOf(cid, dp) } }
            s.pending.filter { it.cid == cid && it.dps.all { (dp, on) -> dps[dp] == on } }.forEach { it.done.complete(true) }
        }
        // The hub acknowledges a command with an empty frame of the same type before the status push
        if ((frame.cmd == Cmd.CONTROL || frame.cmd == Cmd.CONTROL_NEW) && frame.payload.isEmpty()) {
            s.pending.firstOrNull { !it.done.isCompleted }?.done?.complete(frame.retcode == null || frame.retcode == 0)
        }
    }

    private class Pending(val cid: String, val dps: Map<Int, Boolean>) {
        val done = CompletableDeferred<Boolean>()
    }

    private inner class Session(val socket: Socket, val codec: Codec) {
        val pending: MutableList<Pending> = java.util.concurrent.CopyOnWriteArrayList()
        private val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        private val output: OutputStream = socket.getOutputStream()
        private val seq = AtomicInteger(1)

        fun read(): Frame = codec.read(input, fromDevice = true)

        fun send(cmd: Int, payload: ByteArray) {
            synchronized(output) {
                output.write(codec.encode(seq.getAndIncrement(), cmd, payload))
                output.flush()
            }
        }

        fun send(cmd: Int, json: JsonObject) = send(cmd, json.toString().toByteArray(Charsets.UTF_8))

        fun negotiate() {
            val nonce = Handshake.nonce()
            val answer = try {
                send(Cmd.SESS_START, nonce)
                var frame = read()
                var skipped = 0
                while (frame.cmd != Cmd.SESS_RESP && skipped++ < 4) frame = read()
                frame
            } catch (e: TuyaException) {
                throw e
            } catch (e: IOException) {
                throw TuyaException.Rejected("no session key answer: ${e.message ?: e.javaClass.simpleName}")
            }
            val (finish, key) = Handshake.finish(codec, nonce, answer)
            send(Cmd.SESS_FINISH, finish)
            codec.key = key
        }

        fun control(cid: String, dps: Map<Int, Boolean>) {
            val t = System.currentTimeMillis() / 1000
            val values = buildJsonObject { dps.forEach { (dp, on) -> put(dp.toString(), on) } }
            if (codec.version == Version.V33) {
                send(Cmd.CONTROL, buildJsonObject { put("t", t); put("cid", cid); put("dps", values) })
            } else {
                send(Cmd.CONTROL_NEW, buildJsonObject {
                    put("protocol", 5)
                    put("t", t)
                    putJsonObject("data") { put("cid", cid); put("ctype", 0); put("dps", values) }
                    put("cid", cid)
                })
            }
        }

        fun query(cid: String) {
            if (codec.version == Version.V33) {
                send(Cmd.DP_QUERY, buildJsonObject { put("t", System.currentTimeMillis() / 1000); put("cid", cid) })
            } else {
                send(Cmd.DP_QUERY_NEW, buildJsonObject { put("cid", cid) })
            }
        }

        fun heartbeat() = send(Cmd.HEARTBEAT, buildJsonObject { put("gwId", hub.id); put("devId", hub.id) })

        fun close() = runCatching { socket.close() }.let { }
    }

    companion object {
        private const val QUERY_GAP_MS = 40L

        /** A status from the hub, `{"cid":…,"dps":{…}}` (3.3) or `{"data":{"cid":…,"dps":{…}}}` (3.4+): the boolean keys only. */
        fun parseStatus(payload: ByteArray): Pair<String, Map<Int, Boolean>>? {
            if (payload.isEmpty() || payload[0] != '{'.code.toByte()) return null
            val root = runCatching { TuyaJson.parseToJsonElement(String(payload, Charsets.UTF_8)).jsonObject }.getOrNull() ?: return null
            val data = (root["data"] as? JsonObject)
            val cid = (root["cid"] ?: data?.get("cid"))?.let { (it as? JsonPrimitive)?.contentOrNull } ?: return null
            val dps = (root["dps"] ?: data?.get("dps")) as? JsonObject ?: return null
            val on = dps.mapNotNull { (k, v) ->
                val dp = k.toIntOrNull() ?: return@mapNotNull null
                val b = (v as? JsonPrimitive)?.booleanOrNull ?: return@mapNotNull null
                dp to b
            }.toMap()
            return if (on.isEmpty()) null else cid to on
        }
    }
}

private fun openSocket(ip: String, port: Int): Socket = Socket().apply { connect(InetSocketAddress(ip, port), 3_000) }
