package dev.jean.atvhome.tuya

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/** Frames checked against ones built independently (openssl AES, as tinytuya's pack_message does). */
class ProtocolTest {
    private val key = "0123456789abcdef".toByteArray()

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    private fun stream(b: ByteArray) = DataInputStream(ByteArrayInputStream(b))

    @Test fun `3_3 command frame matches the reference byte for byte`() {
        val frame = Codec(Version.V33, key).encode(1, Cmd.CONTROL, """{"t":1,"cid":"abc","dps":{"1":true}}""")
        assertEquals(V33, frame.hex())
    }

    @Test fun `3_4 command frame matches the reference byte for byte`() {
        val codec = Codec(Version.V34, key).apply { this.key = ByteArray(16) { it.toByte() } }
        val json = """{"protocol":5,"t":1,"data":{"cid":"abc","ctype":0,"dps":{"1":false}},"cid":"abc"}"""
        assertEquals(V34, codec.encode(2, Cmd.CONTROL_NEW, json).hex())
    }

    @Test fun `3_4 session key matches the reference`() {
        val sk = Handshake.sessionKey(Version.V34, key, ByteArray(16) { 'A'.code.toByte() }, ByteArray(16) { (it + 16).toByte() })
        assertEquals("7ab32ad9151284a6517afe9a3cba6aab", sk.hex())
    }

    @Test fun `the broadcast key is md5 of Tuya's shared secret`() {
        assertEquals("6c1ec8e2bb9bb59ab50b0daf649b410a", Discovery.UDP_KEY.hex())
    }

    @Test fun `a frame from the hub decodes in every version, return code and 3_x header stripped`() {
        for (v in Version.entries) {
            val hub = Codec(v, key)
            val json = """{"cid":"abc","dps":{"1":true}}"""
            val frame = Codec(v, key).read(stream(hub.encode(9, Cmd.STATUS, json, retcode = 0)), fromDevice = true)
            assertEquals(v.wire, Cmd.STATUS, frame.cmd)
            assertEquals(v.wire, 0, frame.retcode)
            assertEquals(v.wire, json, frame.text)
        }
    }

    @Test fun `an empty acknowledgement decodes to an empty payload`() {
        for (v in Version.entries) {
            val ack = Codec(v, key).encode(3, Cmd.CONTROL_NEW, ByteArray(0), retcode = 0)
            assertEquals(v.wire, 0, Codec(v, key).read(stream(ack), fromDevice = true).payload.size)
        }
    }

    @Test fun `bytes before a frame are skipped`() {
        val frame = Codec(Version.V33, key).encode(1, Cmd.HEARTBEAT, "{}")
        val read = Codec(Version.V33, key).read(stream(byteArrayOf(1, 2, 3) + frame), fromDevice = false)
        assertEquals("{}", read.text)
    }

    @Test(expected = TuyaException.BadKey::class)
    fun `a 3_4 frame signed with another key is a bad key`() {
        val frame = Codec(Version.V34, "fedcba9876543210".toByteArray()).encode(1, Cmd.STATUS, "{}", retcode = 0)
        Codec(Version.V34, key).read(stream(frame), fromDevice = true)
    }

    @Test(expected = TuyaException.BadKey::class)
    fun `a 3_5 frame sealed with another key is a bad key`() {
        val frame = Codec(Version.V35, "fedcba9876543210".toByteArray()).encode(1, Cmd.STATUS, "{}", retcode = 0)
        Codec(Version.V35, key).read(stream(frame), fromDevice = true)
    }

    @Test fun `the handshake agrees on one session key on both sides`() {
        for (v in listOf(Version.V34, Version.V35)) {
            val client = Codec(v, key)
            val nonce = Handshake.nonce()
            val hubNonce = ByteArray(16) { (it * 3).toByte() }
            val answer = Frame(1, Cmd.SESS_RESP, 0, Handshake.answer(key, nonce, hubNonce))
            val (_, sk) = Handshake.finish(client, nonce, answer)
            assertArrayEquals(v.wire, Handshake.sessionKey(v, key, nonce, hubNonce), sk)
        }
    }

    @Test(expected = TuyaException.BadKey::class)
    fun `a hub with another local key fails the handshake`() {
        val nonce = Handshake.nonce()
        val answer = Frame(1, Cmd.SESS_RESP, 0, Handshake.answer("fedcba9876543210".toByteArray(), nonce, ByteArray(16)))
        Handshake.finish(Codec(Version.V34, key), nonce, answer)
    }

    @Test fun `status in the 3_3 and 3_4 shapes keeps only switch keys`() {
        assertEquals("abc" to mapOf(1 to true), Gateway.parseStatus("""{"cid":"abc","dps":{"1":true,"7":0},"t":1}""".toByteArray()))
        assertEquals("abc" to mapOf(2 to false), Gateway.parseStatus("""{"protocol":4,"data":{"cid":"abc","dps":{"2":false}}}""".toByteArray()))
        assertNull(Gateway.parseStatus("""{"dps":{"1":true}}""".toByteArray()))
        assertNull(Gateway.parseStatus("json obj data unvalid".toByteArray()))
    }

    @Test fun `a 3_3 broadcast decodes to the hub's address and version`() {
        val json = """{"ip":"192.168.1.40","gwId":"hub-id","active":2,"encrypt":true,"productKey":"pk","version":"3.4"}"""
        val packet = Codec(Version.V33, Discovery.UDP_KEY).encode(0, 0x13, json, retcode = 0)
        val a = Discovery.decode(packet)!!
        assertEquals(Announce("hub-id", "192.168.1.40", Version.V34, "pk"), a)
        assertTrue(Discovery.decode(ByteArray(10)) == null)
    }

    private companion object {
        const val V33 = "000055aa000000010000000700000047332e330000000000000000000000006ab84e08d50f78e330a5690a473d355809eaa6d8a0b675ae0a645dae7488c7be966b9c62350422878a31183c31bbd4a3cd1b811d0000aa55"
        const val V34 = "000055aa000000020000000d000000943ca85ceaa9c73efc4536de356ff35c7aeafc4c153772ce79e3f9929b41013040338120c273d9899f0d649b5c6308f43ed158f5d057b0dc2c497481b4713fbcadb5e20cec8cc865fa833e974f82fbca1fdd5d331cbb36cbd05358c1f747bb522e954f64f2e4e86e9eee82d202166848996e358db06edcb7be343dae1de978cd0160798e8fa6c5285dcdbf5528ff3435c50000aa55"
    }
}
