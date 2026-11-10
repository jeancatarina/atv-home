package dev.jean.atvhome.tuya

import java.io.DataInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.CRC32
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Tuya's local protocol (TCP 6668), the part a Zigbee hub needs: frames, encryption and the 3.4/3.5
 * session key. Follows tinytuya (github.com/jasonacox/tinytuya, core/message_helper.py and
 * core/XenonDevice.py), the reference everyone tests devices against.
 *
 * 3.3: 0x55AA frame, AES-128-ECB with the local key, CRC32.
 * 3.4: 0x55AA frame, AES-128-ECB with a session key, HMAC-SHA256.
 * 3.5: 0x6699 frame, AES-128-GCM with a session key (the header is the associated data).
 */
enum class Version(val wire: String) {
    V33("3.3"), V34("3.4"), V35("3.5");

    /** "3.x" and twelve zero bytes, in front of every payload but the ones in [Cmd.NO_HEADER]. */
    internal val header: ByteArray get() = wire.toByteArray() + ByteArray(12)
    val negotiates: Boolean get() = this != V33

    companion object {
        fun of(wire: String): Version = entries.firstOrNull { it.wire == wire.trim() } ?: throw TuyaException.Unsupported(wire)
    }
}

object Cmd {
    const val SESS_START = 3
    const val SESS_RESP = 4
    const val SESS_FINISH = 5
    const val CONTROL = 7
    const val STATUS = 8
    const val HEARTBEAT = 9
    const val DP_QUERY = 0x0a
    const val CONTROL_NEW = 0x0d
    const val DP_QUERY_NEW = 0x10
    const val UPDATEDPS = 0x12

    internal val NO_HEADER = setOf(DP_QUERY, DP_QUERY_NEW, UPDATEDPS, HEARTBEAT, SESS_START, SESS_RESP, SESS_FINISH, 0x40)
}

/** A frame with its payload already decrypted and stripped of the "3.x" header. */
class Frame(val seq: Int, val cmd: Int, val retcode: Int?, val payload: ByteArray) {
    val text: String get() = String(payload, Charsets.UTF_8)
    override fun toString() = "Frame(seq=$seq, cmd=$cmd, retcode=$retcode, ${payload.size} B)"
}

sealed class TuyaException(message: String) : IOException(message) {
    /** The local key does not match the hub: re-run tools/tuya-setup.sh (it changes when the hub is paired again). */
    class BadKey(message: String) : TuyaException(message)
    /** The hub hung up or went silent during the handshake: what a hub does with a wrong local key. */
    class Rejected(message: String) : TuyaException(message)
    class Unsupported(version: String) : TuyaException("Tuya protocol $version is not supported (3.3, 3.4 or 3.5)")
    class Corrupt(message: String) : TuyaException(message)
}

/**
 * Encodes and decodes frames for one connection. [key] starts as the local key and becomes the
 * session key once [Handshake] finishes. [fromDevice]: frames the hub sends carry a 4-byte return code.
 */
class Codec(val version: Version, localKey: ByteArray) {
    val localKey: ByteArray = localKey.copyOf()
    @Volatile var key: ByteArray = localKey.copyOf()

    fun encode(seq: Int, cmd: Int, payload: ByteArray, retcode: Int? = null): ByteArray {
        val withHeader = if (cmd in Cmd.NO_HEADER) payload else version.header + payload
        return when (version) {
            Version.V33 -> {
                val body = ecb(key, payload, Cipher.ENCRYPT_MODE).let { if (cmd in Cmd.NO_HEADER) it else version.header + it }
                frame55(seq, cmd, retcode, body, hmac = false)
            }
            Version.V34 -> frame55(seq, cmd, retcode, ecb(key, withHeader, Cipher.ENCRYPT_MODE), hmac = true)
            Version.V35 -> frame66(seq, cmd, retcode, withHeader)
        }
    }

    fun encode(seq: Int, cmd: Int, json: String, retcode: Int? = null) = encode(seq, cmd, json.toByteArray(Charsets.UTF_8), retcode)

    /** Reads the next frame, skipping any bytes before a prefix (a desynced stream resyncs on its own). */
    fun read(input: DataInputStream, fromDevice: Boolean): Frame {
        var prefix = input.readInt()
        var skipped = 0
        while (prefix != PREFIX_55AA && prefix != PREFIX_6699) {
            if (++skipped > MAX_FRAME) throw TuyaException.Corrupt("no frame prefix in $skipped bytes")
            prefix = (prefix shl 8) or input.readUnsignedByte()
        }
        return if (prefix == PREFIX_55AA) read55(input, fromDevice) else read66(input, fromDevice)
    }

    private fun frame55(seq: Int, cmd: Int, retcode: Int?, body: ByteArray, hmac: Boolean): ByteArray {
        val rc = if (retcode != null) 4 else 0
        val end = if (hmac) 36 else 8
        val buf = ByteBuffer.allocate(16 + rc + body.size + end)
        buf.putInt(PREFIX_55AA).putInt(seq).putInt(cmd).putInt(rc + body.size + end)
        if (retcode != null) buf.putInt(retcode)
        buf.put(body)
        val signed = buf.array().copyOf(buf.position())
        if (hmac) buf.put(hmacSha256(key, signed)) else buf.putInt(crc32(signed))
        buf.putInt(SUFFIX_55AA)
        return buf.array()
    }

    private fun frame66(seq: Int, cmd: Int, retcode: Int?, plain: ByteArray): ByteArray {
        val raw = if (retcode != null) ByteBuffer.allocate(4).putInt(retcode).array() + plain else plain
        val header = ByteBuffer.allocate(18).putInt(PREFIX_6699).putShort(0).putInt(seq).putInt(cmd).putInt(12 + raw.size + 16).array()
        val iv = ByteArray(12).also(random::nextBytes)
        val sealed = gcm(key, iv, raw, header.copyOfRange(4, 18), Cipher.ENCRYPT_MODE)
        return header + iv + sealed + ByteBuffer.allocate(4).putInt(SUFFIX_6699).array()
    }

    private fun read55(input: DataInputStream, fromDevice: Boolean): Frame {
        val seq = input.readInt()
        val cmd = input.readInt()
        val length = input.readInt()
        if (length < 8 || length > MAX_FRAME) throw TuyaException.Corrupt("frame length $length")
        val rest = ByteArray(length).also(input::readFully)
        val end = if (version == Version.V33) 8 else 36
        if (length < end) throw TuyaException.Corrupt("frame length $length")
        val header = ByteBuffer.allocate(16).putInt(PREFIX_55AA).putInt(seq).putInt(cmd).putInt(length).array()
        val signed = header + rest.copyOfRange(0, length - end)
        val check = rest.copyOfRange(length - end, length - 4)
        val good = if (version == Version.V33) ByteBuffer.wrap(check).int == crc32(signed) else MessageDigest.isEqual(check, hmacSha256(key, signed))
        if (!good) {
            if (version == Version.V33) throw TuyaException.Corrupt("CRC mismatch")
            throw TuyaException.BadKey("HMAC mismatch on frame cmd $cmd")
        }
        var body = rest.copyOfRange(0, length - end)
        var retcode: Int? = null
        if (fromDevice && body.size >= 4) {
            retcode = ByteBuffer.wrap(body, 0, 4).int
            body = body.copyOfRange(4, body.size)
        }
        val plain = when (version) {
            Version.V33 -> {
                val cipher = if (body.startsWith(version.header.copyOf(3))) body.copyOfRange(15, body.size) else body
                when {
                    cipher.isEmpty() -> cipher
                    cipher[0] == '{'.code.toByte() -> cipher // 3.3 hubs answer some commands in clear text
                    else -> decryptEcb(cipher)
                }
            }
            else -> if (body.isEmpty()) body else decryptEcb(body)
        }
        return Frame(seq, cmd, retcode, stripHeader(plain))
    }

    private fun read66(input: DataInputStream, fromDevice: Boolean): Frame {
        val unknown = input.readUnsignedShort()
        val seq = input.readInt()
        val cmd = input.readInt()
        val length = input.readInt()
        if (length < 28 || length > MAX_FRAME) throw TuyaException.Corrupt("frame length $length")
        val rest = ByteArray(length + 4).also(input::readFully)
        val aad = ByteBuffer.allocate(14).putShort(unknown.toShort()).putInt(seq).putInt(cmd).putInt(length).array()
        val plain = try {
            gcm(key, rest.copyOfRange(0, 12), rest.copyOfRange(12, length), aad, Cipher.DECRYPT_MODE)
        } catch (e: AEADBadTagException) {
            throw TuyaException.BadKey("GCM tag mismatch on frame cmd $cmd")
        }
        var body = plain
        var retcode: Int? = null
        if (fromDevice && body.size >= 4 && body[0] == 0.toByte()) {
            retcode = ByteBuffer.wrap(body, 0, 4).int
            body = body.copyOfRange(4, body.size)
        }
        return Frame(seq, cmd, retcode, stripHeader(body))
    }

    private fun stripHeader(plain: ByteArray): ByteArray =
        if (plain.size >= 15 && plain.startsWith(version.header.copyOf(3))) plain.copyOfRange(15, plain.size) else plain

    private fun decryptEcb(cipher: ByteArray): ByteArray {
        if (cipher.size % 16 != 0) throw TuyaException.Corrupt("ciphertext of ${cipher.size} bytes")
        return try {
            ecb(key, cipher, Cipher.DECRYPT_MODE)
        } catch (e: BadPaddingException) {
            throw TuyaException.BadKey("payload does not decrypt with this key")
        }
    }

    companion object {
        const val PORT = 6668
        internal const val PREFIX_55AA = 0x000055AA
        internal const val SUFFIX_55AA = 0x0000AA55
        internal const val PREFIX_6699 = 0x00006699
        internal const val SUFFIX_6699 = 0x00009966
        private const val MAX_FRAME = 64 * 1024
        private val random = SecureRandom()
    }
}

/**
 * The 3.4/3.5 session key: the client sends a nonce, the hub answers with its own nonce and an HMAC
 * proving it has the same local key, and the session key is derived from both nonces.
 */
internal object Handshake {
    fun nonce(): ByteArray = ByteArray(16).also(SecureRandom()::nextBytes)

    /** Checks the hub's answer and returns the client's last message and the session key. */
    fun finish(codec: Codec, localNonce: ByteArray, answer: Frame): Pair<ByteArray, ByteArray> {
        val p = answer.payload
        if (answer.cmd != Cmd.SESS_RESP || p.size < 48) throw TuyaException.Corrupt("session answer cmd ${answer.cmd}, ${p.size} B")
        val remote = p.copyOfRange(0, 16)
        if (!MessageDigest.isEqual(p.copyOfRange(16, 48), hmacSha256(codec.localKey, localNonce))) {
            throw TuyaException.BadKey("the hub does not know this local key")
        }
        return hmacSha256(codec.localKey, remote) to sessionKey(codec.version, codec.localKey, localNonce, remote)
    }

    fun sessionKey(version: Version, localKey: ByteArray, localNonce: ByteArray, remoteNonce: ByteArray): ByteArray {
        val xor = ByteArray(16) { (localNonce[it].toInt() xor remoteNonce[it].toInt()).toByte() }
        return when (version) {
            Version.V35 -> gcm(localKey, localNonce.copyOf(12), xor, null, Cipher.ENCRYPT_MODE).copyOf(16)
            else -> Cipher.getInstance("AES/ECB/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(localKey, "AES")); doFinal(xor)
            }
        }
    }

    /** The hub's side, for the fake hub in tests. */
    fun answer(localKey: ByteArray, clientNonce: ByteArray, hubNonce: ByteArray): ByteArray = hubNonce + hmacSha256(localKey, clientNonce)
}

internal fun ecb(key: ByteArray, data: ByteArray, mode: Int): ByteArray =
    Cipher.getInstance("AES/ECB/PKCS5Padding").run { init(mode, SecretKeySpec(key, "AES")); doFinal(data) }

internal fun gcm(key: ByteArray, iv: ByteArray, data: ByteArray, aad: ByteArray?, mode: Int): ByteArray =
    Cipher.getInstance("AES/GCM/NoPadding").run {
        init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        if (aad != null) updateAAD(aad)
        doFinal(data)
    }

internal fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
    Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(data) }

private fun crc32(data: ByteArray): Int = CRC32().apply { update(data) }.value.toInt()

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
