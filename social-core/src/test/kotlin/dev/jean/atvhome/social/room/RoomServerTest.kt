package dev.jean.atvhome.social.room

import dev.jean.atvhome.social.QUESTIONS
import dev.jean.atvhome.social.games.mostlikely.MostLikely
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** The phone's side of the protocol, over real sockets. */
class RoomServerTest {

    private val room = Room(content = { QUESTIONS })
    private val pages = mapOf("web/index.html" to "<html>social</html>".toByteArray(), "web/app.js" to "var x=1".toByteArray())
    private lateinit var server: RoomServer
    private val http = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
    private val base get() = "http://127.0.0.1:${server.listeningPort}"
    private val json = "application/json".toMediaType()

    @Before fun start() { server = RoomServer(room, { pages[it] }, port = 0).also { it.start(5_000, false) } }
    @After fun stop() { server.stop(); room.close() }

    private fun get(path: String, token: String? = null, host: String? = null): Response = http.newCall(
        Request.Builder().url(base + path).apply {
            token?.let { header("Authorization", "Bearer $it") }
            host?.let { header("Host", it) }
        }.build(),
    ).execute()

    private fun post(path: String, body: String, token: String? = null): Response = http.newCall(
        Request.Builder().url(base + path).post(body.toRequestBody(json)).apply { token?.let { header("Authorization", "Bearer $it") } }.build(),
    ).execute()

    private fun obj(r: Response): JsonObject = r.use { Json.parseToJsonElement(it.body!!.string()).jsonObject }

    private class Phone(val token: String, val id: String)

    private fun join(name: String, key: String = room.key): Phone {
        val o = obj(post("/api/join", """{"key":"$key","name":"$name"}"""))
        return Phone(o["token"]!!.jsonPrimitive.content, o["me"]!!.jsonPrimitive.content)
    }

    private fun view(p: Phone, since: Long = 0): JsonObject = obj(get("/api/view?since=$since", p.token))

    @Test
    fun servesThePageAndItsFilesWithTheRightCaching() {
        get("/").use {
            assertEquals("<html>social</html>", it.body!!.string())
            assertEquals("no-cache", it.header("Cache-Control"))
            assertTrue(it.header("Content-Security-Policy")!!.contains("default-src 'self'"))
        }
        get("/a/app.js").use {
            assertEquals("var x=1", it.body!!.string())
            assertEquals("max-age=31536000, immutable", it.header("Cache-Control"))
            assertTrue(it.header("Content-Type")!!.startsWith("application/javascript"))
        }
        get("/a/..%2F..%2Fsecret").use { assertEquals(404, it.code) }
        get("/a/missing.js").use { assertEquals(404, it.code) }
    }

    @Test
    fun joiningNeedsTheRoomKey() {
        post("/api/join", """{"key":"nope","name":"Ana"}""").use { assertEquals(403, it.code) }
        post("/api/join", """{"name":"Ana"}""").use { assertEquals(403, it.code) }
        assertTrue(room.snapshot.value.players.isEmpty())
        val p = join("Ana")
        assertEquals("p1", p.id)
    }

    @Test
    fun theViewComesWithTheTvClockAVersionAndTheWholeEnvelope() {
        val p = join("Ana")
        val v = view(p)
        assertTrue(v["now"]!!.jsonPrimitive.content.toLong() > 0)
        assertTrue(v["v"]!!.jsonPrimitive.content.toLong() >= 1)
        assertEquals(p.id, v["me"]!!.jsonPrimitive.content)
        assertEquals("lobby", v["view"]!!.jsonObject["phase"]!!.jsonPrimitive.content)
    }

    @Test
    fun withoutAValidTokenThereIsNoView() {
        get("/api/view").use { assertEquals(401, it.code) }
        get("/api/view", token = "forged").use { assertEquals(401, it.code) }
        post("/api/act", """{"type":"pick"}""").use { assertEquals(401, it.code) }
    }

    @Test
    fun aPhoneNeverReceivesAnotherPlayersChoice() {
        val a = join("Ana"); val b = join("Bia"); val c = join("Caio")
        room.start(MostLikely)
        val phase = room.snapshot.value.game!!.phase
        val ok = obj(post("/api/act", """{"aid":"1","phase":"$phase","type":"pick","data":{"id":"${c.id}"}}""", b.token))
        assertTrue(ok["ok"]!!.jsonPrimitive.boolean)
        val forA = get("/api/view?since=0", a.token).use { it.body!!.string() }
        val forB = get("/api/view?since=0", b.token).use { it.body!!.string() }
        assertFalse(forA.contains("\"chosen\""))
        assertTrue(forB.contains("\"chosen\":\"${c.id}\""))
        assertNotEquals(forA, forB)
    }

    @Test
    fun aLateActionComesBackAsNotOkWithAReasonNotAnError() {
        val a = join("Ana"); join("Bia"); join("Caio")
        room.start(MostLikely)
        val o = obj(post("/api/act", """{"phase":"vote:99","type":"pick","data":{"id":"${a.id}"}}""", a.token))
        assertFalse(o["ok"]!!.jsonPrimitive.boolean)
        assertEquals("Essa etapa já passou", o["why"]!!.jsonPrimitive.content)
    }

    @Test
    fun aLongPollAnswersAsSoonAsTheTvMovesOn() {
        val a = join("Ana"); join("Bia"); join("Caio")
        val v0 = view(a)["v"]!!.jsonPrimitive.content.toLong()
        val t = System.nanoTime()
        Thread { Thread.sleep(200); room.start(MostLikely) }.start()
        val v1 = view(a, since = v0)
        val ms = (System.nanoTime() - t) / 1_000_000
        assertTrue("took ${ms}ms", ms in 150..2_000)
        assertEquals("vote:0", v1["view"]!!.jsonObject["phase"]!!.jsonPrimitive.content)
    }

    @Test
    fun waitZeroAnswersAtOnceEvenWhenNothingChanged() {
        val a = join("Ana")
        val v = view(a)["v"]!!.jsonPrimitive.content.toLong()
        val t = System.nanoTime()
        val again = obj(get("/api/view?since=$v&wait=0", a.token))
        assertTrue("took ${(System.nanoTime() - t) / 1_000_000}ms", (System.nanoTime() - t) / 1_000_000 < 2_000)
        assertEquals(v, again["v"]!!.jsonPrimitive.content.toLong())
    }

    @Test
    fun aFaceComesInAsBase64AndIsRefusedWhenItIsNotAPhoto() {
        val a = join("Ana")
        val photo = java.util.Base64.getEncoder().encodeToString(ByteArray(4_000).also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte() })
        assertTrue(obj(post("/api/face", "{\"jpeg\":\"$photo\"}", a.token))["ok"]!!.jsonPrimitive.boolean)
        assertEquals(1, room.snapshot.value.players.single().face)
        assertFalse(obj(post("/api/face", "{\"jpeg\":\"bm90IGEgcGhvdG8=\"}", a.token))["ok"]!!.jsonPrimitive.boolean)
        assertFalse(obj(post("/api/face", "{\"jpeg\":\"%%%\"}", a.token))["ok"]!!.jsonPrimitive.boolean)
        post("/api/face", "{\"jpeg\":\"" + "A".repeat(40_000) + "\"}", a.token).use { assertEquals(413, it.code) }
        post("/api/face", "{\"jpeg\":\"$photo\"}").use { assertEquals(401, it.code) }
    }

    @Test
    fun aSeatedPhoneCanFetchATableFaceButAStrangerCannot() {
        val a = join("Ana"); val b = join("Bia")
        val photo = ByteArray(4_000).also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte() }
        post("/api/face", "{\"jpeg\":\"${java.util.Base64.getEncoder().encodeToString(photo)}\"}", a.token).use { assertEquals(200, it.code) }
        get("/api/face/${a.id}", b.token).use { assertEquals(200, it.code); assertTrue(photo.contentEquals(it.body!!.bytes())) }
        get("/api/face/${b.id}", a.token).use { assertEquals(200, it.code); assertEquals("no photo, no bytes", 0, it.body!!.bytes().size) }
        get("/api/face/${a.id}").use { assertEquals(401, it.code) }
        get("/api/face/${a.id}", "not-a-token").use { assertEquals("a stranger gets nothing", 0, it.body!!.bytes().size) }
    }

    @Test
    fun aPhoneIdIsAcceptedOnJoinAndForgettingErasesIt() {
        val dev = "device-0123456789abcdef"
        val o = obj(post("/api/join", "{\"key\":\"${room.key}\",\"name\":\"Ana\",\"device\":\"$dev\"}"))
        val token = o["token"]!!.jsonPrimitive.content
        val again = obj(post("/api/join", "{\"key\":\"${room.key}\",\"name\":\"Ana\",\"device\":\"$dev\"}"))
        assertEquals("the same phone gets the same seat", token, again["token"]!!.jsonPrimitive.content)
        assertTrue(obj(post("/api/forget", "{}", token))["ok"]!!.jsonPrimitive.boolean)
        post("/api/forget", "{}").use { assertEquals(401, it.code) }
    }

    @Test
    fun leavingFreesTheSeatAndTheTokenStopsWorking() {
        val a = join("Ana")
        post("/api/leave", "{}", a.token).use { assertEquals(200, it.code) }
        get("/api/view", a.token).use { assertEquals(401, it.code) }
        assertTrue(room.snapshot.value.players.isEmpty())
    }

    @Test
    fun aHostThatIsANameIsRefusedSoARebindingPageCannotTalkToTheTv() {
        get("/", host = "attacker.example.com").use { assertEquals(421, it.code) }
        get("/api/view", host = "attacker.example.com").use { assertEquals(421, it.code) }
    }

    @Test
    fun bodiesAboveFourKilobytesAreRefused() {
        val big = """{"key":"${room.key}","name":"${"a".repeat(5_000)}"}"""
        post("/api/join", big).use { assertEquals(413, it.code) }
    }

    @Test
    fun noMoreThanFiveJoinsAMinuteFromOneAddress() {
        repeat(5) { post("/api/join", """{"key":"${room.key}","name":"P$it"}""").use { r -> assertEquals(200, r.code) } }
        post("/api/join", """{"key":"${room.key}","name":"extra"}""").use { assertEquals(429, it.code) }
    }

    @Test
    fun reactionsGoThroughTheSameDoor() {
        val a = join("Ana")
        assertTrue(obj(post("/api/react", """{"kind":"laugh"}""", a.token))["ok"]!!.jsonPrimitive.boolean)
        assertEquals(1, room.snapshot.value.reactions.size)
    }
}
