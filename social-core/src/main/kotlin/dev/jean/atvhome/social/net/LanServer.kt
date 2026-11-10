package dev.jean.atvhome.social.net

import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The small HTTP server every phone-facing feature of the launcher shares (Match, Social).
 *
 * NanoHTTPD on its own opens one thread per connection with no limit and reads any body size, which
 * is too loose for a 2.3 GB TV. This adds a bounded pool, a body cap checked on Content-Length
 * before the body is read, and an opt-in check that the Host header is an IP address (a DNS-rebinding
 * page on the internet cannot make a browser send one). Routing stays in the subclass.
 */
abstract class LanServer(
    port: Int,
    maxThreads: Int = 32,
    private val maxBody: Int = 4 * 1024,
    private val ipHostOnly: Boolean = false,
) : NanoHTTPD(port) {

    private val body = ThreadLocal<JsonObject>()

    init {
        setAsyncRunner(BoundedRunner(maxThreads))
    }

    /** The TV's address on the home network, so phones can reach it. */
    val url: String? get() = localIp()?.let { "http://$it:$listeningPort/" }

    /** Largest body accepted for [uri]; subclasses raise it for a route like an uploaded photo. */
    protected open fun bodyLimit(uri: String): Int = maxBody

    protected abstract fun route(session: IHTTPSession): Response

    protected open fun onError(uri: String, error: Throwable) = Unit

    final override fun serve(session: IHTTPSession): Response {
        if (ipHostOnly && !isIpHost(session.headers["host"])) {
            return refuse(MISDIRECTED)
        }
        val declared = session.headers["content-length"]?.toLongOrNull() ?: 0L
        if (declared > bodyLimit(session.uri)) return refuse(TOO_LARGE)
        return try {
            // The body is always read, even by a route that ignores it: a leftover body on a keep-alive
            // connection would be parsed as the start of the next request
            body.set(if (session.method == Method.POST || session.method == Method.PUT) readBody(session) else JsonObject(emptyMap()))
            route(session)
        } catch (e: BadBody) {
            text(Response.Status.BAD_REQUEST, "")
        } catch (e: Throwable) {
            onError(session.uri, e)
            text(Response.Status.INTERNAL_ERROR, e.message.orEmpty())
        } finally {
            body.remove()
        }
    }

    /** A refusal sent without reading the request's body: the connection must close, or those bytes would be parsed as the next request. */
    private fun refuse(status: Response.IStatus): Response = text(status, "").also { it.closeConnection(true) }

    private class BadBody : Exception()

    private fun readBody(session: IHTTPSession): JsonObject {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val raw = files["postData"].orEmpty()
        return try {
            parser.parseToJsonElement(raw.ifBlank { "{}" }) as? JsonObject ?: JsonObject(emptyMap())
        } catch (e: Exception) {
            throw BadBody()
        }
    }

    /** The request's JSON body (empty object when there is none). It was read before routing. */
    @Suppress("UNUSED_PARAMETER")
    protected fun jsonBody(session: IHTTPSession): JsonObject = body.get() ?: JsonObject(emptyMap())

    protected fun jsonResponse(o: JsonObject): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", o.toString())
            .apply { addHeader("Cache-Control", "no-store") }

    protected fun html(page: String): Response = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", page)

    protected fun text(status: Response.IStatus, body: String): Response = newFixedLengthResponse(status, "text/plain", body)

    private class BoundedRunner(maxThreads: Int) : AsyncRunner {
        private val running = java.util.Collections.synchronizedSet(HashSet<ClientHandler>())
        private val count = AtomicInteger()
        private val pool = ThreadPoolExecutor(
            maxThreads, maxThreads, 30, TimeUnit.SECONDS, ArrayBlockingQueue(maxThreads * 2),
            { r -> Thread(r, "lan-${count.incrementAndGet()}").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy(),
        ).apply { allowCoreThreadTimeOut(true) }

        override fun exec(code: ClientHandler) {
            running += code
            // A full pool drops the connection instead of growing without limit
            runCatching { pool.execute(code) }.onFailure { running -= code; code.close() }
        }

        override fun closed(clientHandler: ClientHandler) { running -= clientHandler }

        override fun closeAll() {
            pool.shutdownNow()
            ArrayList(running).forEach { it.close() }
        }
    }

    private class Code(private val code: Int, private val reason: String) : Response.IStatus {
        override fun getRequestStatus() = code
        override fun getDescription() = "$code $reason"
    }

    companion object {
        private val parser = Json { ignoreUnknownKeys = true }
        private val TOO_LARGE = Code(413, "Payload Too Large")
        private val MISDIRECTED = Code(421, "Misdirected Request")
        private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")

        /** True for "192.168.1.3:8766", "localhost" and nothing that is a name a browser would resolve. */
        fun isIpHost(header: String?): Boolean {
            val host = header?.substringBeforeLast(':', header)?.trim().orEmpty()
            return host == "localhost" || IPV4.matches(host)
        }

        /** The TV's address on the home network (Wi-Fi or cable), so phones can reach it. */
        fun localIp(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
        }.getOrNull()
    }
}
