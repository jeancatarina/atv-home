package dev.jean.atvhome.social.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LanServerTest {

    private class Echo(maxThreads: Int = 32, ipHostOnly: Boolean = false, private val gate: CountDownLatch? = null) :
        LanServer(0, maxThreads = maxThreads, ipHostOnly = ipHostOnly) {
        override fun bodyLimit(uri: String) = if (uri == "/big") 40_000 else super.bodyLimit(uri)
        override fun route(session: IHTTPSession): Response = when (session.uri) {
            "/ok" -> text(Response.Status.OK, "ok")
            "/ignore" -> text(Response.Status.OK, "ignored")
            "/post" -> jsonResponse(jsonBody(session))
            "/big" -> jsonResponse(jsonBody(session))
            "/slow" -> { gate?.await(10, TimeUnit.SECONDS); text(Response.Status.OK, "slow") }
            "/boom" -> error("boom")
            else -> text(Response.Status.NOT_FOUND, "")
        }
    }

    private val servers = mutableListOf<Echo>()
    private val http = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
    private fun start(s: Echo): Echo = s.also { it.start(5_000, false); servers += it }
    private fun url(s: Echo, path: String) = "http://127.0.0.1:${s.listeningPort}$path"

    @After fun stop() { servers.forEach { it.stop() } }

    @Test
    fun servesAndRoutes() {
        val s = start(Echo())
        http.newCall(Request.Builder().url(url(s, "/ok")).build()).execute().use { assertEquals("ok", it.body!!.string()) }
        http.newCall(Request.Builder().url(url(s, "/x")).build()).execute().use { assertEquals(404, it.code) }
    }

    @Test
    fun anErrorInARouteIs500AndTheServerKeepsServing() {
        val s = start(Echo())
        http.newCall(Request.Builder().url(url(s, "/boom")).build()).execute().use { assertEquals(500, it.code) }
        http.newCall(Request.Builder().url(url(s, "/ok")).build()).execute().use { assertEquals(200, it.code) }
    }

    @Test
    fun aRouteThatIgnoresItsBodyDoesNotBreakTheNextRequestOnTheSameConnection() {
        val s = start(Echo())
        val type = "application/json".toMediaType()
        // OkHttp reuses the connection: a body left unread would be parsed as the next request line
        repeat(3) {
            http.newCall(Request.Builder().url(url(s, "/ignore")).post("""{"unused":true}""".toRequestBody(type)).build()).execute()
                .use { assertEquals(200, it.code) }
            http.newCall(Request.Builder().url(url(s, "/ok")).build()).execute().use { assertEquals(200, it.code) }
        }
    }

    @Test
    fun malformedJsonIs400NotAServerError() {
        val s = start(Echo())
        http.newCall(Request.Builder().url(url(s, "/post")).post("{not json".toRequestBody("application/json".toMediaType())).build()).execute()
            .use { assertEquals(400, it.code) }
    }

    @Test
    fun smallJsonBodiesPassAndOversizedOnesAre413BeforeBeingRead() {
        val s = start(Echo())
        val type = "application/json".toMediaType()
        http.newCall(Request.Builder().url(url(s, "/post")).post("""{"a":1}""".toRequestBody(type)).build()).execute()
            .use { assertEquals(200, it.code); assertEquals("""{"a":1}""", it.body!!.string()) }
        val big = """{"x":"${"y".repeat(5_000)}"}"""
        http.newCall(Request.Builder().url(url(s, "/post")).post(big.toRequestBody(type)).build()).execute().use { assertEquals(413, it.code) }
    }

    @Test
    fun aRefusedBigBodyDoesNotPoisonTheNextRequestOnTheSameConnection() {
        val s = start(Echo())
        val type = "application/json".toMediaType()
        val big = """{"x":"${"y".repeat(5_000)}"}"""
        repeat(3) {
            http.newCall(Request.Builder().url(url(s, "/post")).post(big.toRequestBody(type)).build()).execute().use { assertEquals(413, it.code) }
            http.newCall(Request.Builder().url(url(s, "/ok")).build()).execute().use { assertEquals(200, it.code) }
        }
    }

    @Test
    fun aRouteCanRaiseItsOwnLimit() {
        val s = start(Echo())
        val body = """{"x":"${"y".repeat(20_000)}"}"""
        http.newCall(Request.Builder().url(url(s, "/big")).post(body.toRequestBody("application/json".toMediaType())).build()).execute()
            .use { assertEquals(200, it.code) }
    }

    @Test
    fun hostCheckAcceptsIpsAndRejectsNamesWhenEnabled() {
        val s = start(Echo(ipHostOnly = true))
        http.newCall(Request.Builder().url(url(s, "/ok")).build()).execute().use { assertEquals(200, it.code) }
        http.newCall(Request.Builder().url(url(s, "/ok")).header("Host", "evil.example.com:8766").build()).execute().use { assertEquals(421, it.code) }
        assertTrue(LanServer.isIpHost("192.168.1.3:8766"))
        assertTrue(LanServer.isIpHost("localhost"))
        assertFalse(LanServer.isIpHost("rebind.example.com"))
        assertFalse(LanServer.isIpHost(null))
    }

    @Test
    fun hostCheckIsOffByDefaultSoMatchBehavesAsBefore() {
        val s = start(Echo())
        http.newCall(Request.Builder().url(url(s, "/ok")).header("Host", "tv.local").build()).execute().use { assertEquals(200, it.code) }
    }

    @Test
    fun threadsAreBoundedAndTheServerRecoversWhenTheyFree() {
        val gate = CountDownLatch(1)
        val s = start(Echo(maxThreads = 2, gate = gate))
        val before = Thread.getAllStackTraces().keys.count { it.name.startsWith("lan-") }
        // Open many connections that hold a worker: more than pool + queue
        val sockets = (1..20).map { Socket("127.0.0.1", s.listeningPort).apply { getOutputStream().write("GET /slow HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray()) } }
        Thread.sleep(300)
        val lan = Thread.getAllStackTraces().keys.count { it.name.startsWith("lan-") } - before
        assertTrue("worker threads must stay at 2, saw $lan", lan <= 2)
        gate.countDown()
        sockets.forEach { runCatching { it.close() } }
        Thread.sleep(300)
        http.newCall(Request.Builder().url(url(s, "/ok")).build()).execute().use { assertEquals(200, it.code) }
    }
}
