package dev.jean.atvhome.tuya

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
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * A Tuya Zigbee hub on localhost, the hub's side of [Codec] and [Handshake]: answers queries,
 * applies commands, acknowledges them and pushes the status to every client, like the real one.
 * [press] plays someone pressing the wall switch.
 */
class FakeHub(
    val version: Version,
    val key: String = "0123456789abcdef",
    val id: String = "hub-id",
    port: Int = 0,
) : AutoCloseable {
    private val server = ServerSocket(port)
    val port: Int get() = server.localPort
    val states = ConcurrentHashMap<String, MutableMap<Int, Boolean>>()
    val received = CopyOnWriteArrayList<Frame>()
    private val clients = CopyOnWriteArrayList<Client>()
    /** Off: the hub stops answering commands (a frozen hub), to test the rollback. */
    @Volatile var answering = true

    init {
        thread(isDaemon = true, name = "fake-hub") {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                val client = Client(socket)
                clients += client
                thread(isDaemon = true) { client.serve() }
            }
        }
    }

    fun press(cid: String, dp: Int, on: Boolean) {
        states.getOrPut(cid) { ConcurrentHashMap() }[dp] = on
        clients.forEach { it.push(cid, mapOf(dp to on)) }
    }

    /** Drops every connection, like the hub rebooting. */
    fun dropClients() = clients.forEach { it.close() }

    override fun close() {
        server.close()
        dropClients()
    }

    private inner class Client(val socket: Socket) {
        private val codec = Codec(version, key.toByteArray())
        private val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        private val out = socket.getOutputStream()
        private val seq = AtomicInteger(100)
        private var nonce: ByteArray? = null
        private var hubNonce = ByteArray(16) { (it * 7 + 3).toByte() }

        fun serve() = runCatching {
            while (!socket.isClosed) handle(codec.read(input, fromDevice = false))
        }.let { clients.remove(this); close() }

        private fun send(seqNo: Int, cmd: Int, payload: ByteArray) = synchronized(out) {
            out.write(codec.encode(seqNo, cmd, payload, retcode = 0)); out.flush()
        }

        fun push(cid: String, dps: Map<Int, Boolean>) = runCatching { send(seq.getAndIncrement(), Cmd.STATUS, status(cid, dps)) }

        private fun status(cid: String, dps: Map<Int, Boolean>): ByteArray {
            val values = buildJsonObject { dps.forEach { (dp, on) -> put(dp.toString(), on) } }
            val json = if (version == Version.V33) buildJsonObject { put("cid", cid); put("dps", values); put("t", 1) }
            else buildJsonObject { put("protocol", 4); put("t", 1); putJsonObject("data") { put("cid", cid); put("dps", values) } }
            return json.toString().toByteArray()
        }

        private fun handle(frame: Frame) {
            received += frame
            when (frame.cmd) {
                Cmd.SESS_START -> {
                    nonce = frame.payload.copyOf(16)
                    send(frame.seq, Cmd.SESS_RESP, Handshake.answer(codec.localKey, nonce!!, hubNonce))
                }
                Cmd.SESS_FINISH -> codec.key = Handshake.sessionKey(version, codec.localKey, nonce!!, hubNonce)
                Cmd.HEARTBEAT -> send(frame.seq, Cmd.HEARTBEAT, ByteArray(0))
                Cmd.DP_QUERY, Cmd.DP_QUERY_NEW -> {
                    val cid = json(frame)["cid"].str() ?: return
                    send(frame.seq, frame.cmd, status(cid, states[cid] ?: mapOf(1 to false)))
                }
                Cmd.CONTROL, Cmd.CONTROL_NEW -> {
                    if (!answering) return
                    val root = json(frame)
                    val data = if (version == Version.V33) root else root["data"]!!.jsonObject
                    val cid = data["cid"].str()!!
                    val dps = (data["dps"] as JsonObject).mapNotNull { (k, v) -> (v as JsonPrimitive).booleanOrNull?.let { k.toInt() to it } }.toMap()
                    states.getOrPut(cid) { ConcurrentHashMap() }.putAll(dps)
                    send(frame.seq, frame.cmd, ByteArray(0))
                    clients.forEach { it.push(cid, dps) }
                }
            }
        }

        private fun json(frame: Frame) = TuyaJson.parseToJsonElement(frame.text).jsonObject
        private fun kotlinx.serialization.json.JsonElement?.str() = (this as? JsonPrimitive)?.contentOrNull

        fun close() = runCatching { socket.close() }.let { }
    }
}
