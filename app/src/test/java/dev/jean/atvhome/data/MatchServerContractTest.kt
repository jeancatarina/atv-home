package dev.jean.atvhome.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * What the phones' page depends on: the routes and the JSON of the Match server. Pinned so that
 * moving the server onto the shared LanServer cannot change how a phone sees it.
 */
class MatchServerContractTest {

    private val deck = (1..3).map { Card("movie:$it", "Filme $it", 2000 + it, "7,$it", "Netflix", "https://img/$it.jpg", "Sinopse $it") }
    private lateinit var server: MatchServer
    private val http = OkHttpClient()
    private val base get() = "http://127.0.0.1:${server.listeningPort}"

    @Before fun start() { server = MatchServer({ "<html>Match</html>" }, deck, port = 0).also { it.start(5_000, false) } }
    @After fun stop() { server.stop() }

    private fun get(path: String) = http.newCall(Request.Builder().url(base + path).build()).execute()
    private fun post(path: String, body: String) = http.newCall(
        Request.Builder().url(base + path).post(body.toRequestBody("application/json".toMediaType())).build(),
    ).execute()
    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    private fun join(name: String): String = post("/join", """{"name":"$name"}""").use { json(it.body!!.string())["id"]!!.jsonPrimitive.content }
    private fun vote(id: String, key: String, like: Boolean): JsonObject =
        post("/vote", """{"id":"$id","key":"$key","like":$like}""").use { json(it.body!!.string()) }

    @Test
    fun servesThePageAtTheRoot() {
        get("/").use {
            assertEquals(200, it.code)
            assertTrue(it.header("Content-Type")!!.startsWith("text/html"))
            assertEquals("<html>Match</html>", it.body!!.string())
        }
    }

    @Test
    fun deckListsEveryCardWithTheFieldsThePhoneDraws() {
        get("/deck").use {
            val cards = json(it.body!!.string())["cards"]!!.jsonArray
            assertEquals(3, cards.size)
            val first = cards[0].jsonObject
            assertEquals("movie:1", first["key"]!!.jsonPrimitive.content)
            assertEquals("Filme 1", first["title"]!!.jsonPrimitive.content)
            assertEquals(2001, first["year"]!!.jsonPrimitive.int)
            assertEquals("Netflix", first["where"]!!.jsonPrimitive.content)
            assertEquals("https://img/1.jpg", first["poster"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun joinGivesSequentialIdsAndJsonIsNeverCached() {
        post("/join", """{"name":"Ana"}""").use {
            assertEquals("no-store", it.header("Cache-Control"))
            assertEquals("p1", json(it.body!!.string())["id"]!!.jsonPrimitive.content)
        }
        assertEquals("p2", join("Bia"))
    }

    @Test
    fun twoPeopleLikingTheSameTitleIsAMatchOnState() {
        val a = join("Ana")
        val b = join("Bia")
        vote(a, "movie:2", true)
        val s = vote(b, "movie:2", true)
        assertEquals(2, s["people"]!!.jsonPrimitive.int)
        assertEquals("movie:2", s["match"]!!.jsonPrimitive.content)
        assertTrue(s["unanimous"]!!.jsonPrimitive.boolean)
        assertEquals(2, s["likes"]!!.jsonPrimitive.int)
        get("/state").use { assertEquals("movie:2", json(it.body!!.string())["match"]!!.jsonPrimitive.content) }
    }

    @Test
    fun stateWithoutAMatchReportsNull() {
        join("Ana")
        get("/state").use { assertTrue(json(it.body!!.string())["match"] is kotlinx.serialization.json.JsonNull) }
    }

    @Test
    fun unknownPathIs404() {
        get("/nope").use { assertEquals(404, it.code) }
    }

    @Test
    fun anotherMatchMovesOnWithoutLosingVotes() {
        val a = join("Ana")
        val b = join("Bia")
        listOf("movie:1", "movie:3").forEach { vote(a, it, true); vote(b, it, true) }
        server.next()
        assertEquals("movie:3", server.updates.value.result!!.key)
    }

    @Test
    fun theDeckCanGrowAndPhonesAskForOnlyWhatTheyLack() {
        get("/deck?from=3").use { assertEquals(0, json(it.body!!.string())["cards"]!!.jsonArray.size) }
        server.append(listOf(Card("movie:4", "Filme 4", 2004, null, null, null, ""), Card("movie:2", "repetido", null, null, null, null, "")))
        get("/deck?from=3").use {
            val cards = json(it.body!!.string())["cards"]!!.jsonArray
            assertEquals("the repeat was ignored", listOf("movie:4"), cards.map { c -> c.jsonObject["key"]!!.jsonPrimitive.content })
        }
        get("/deck").use { assertEquals(4, json(it.body!!.string())["cards"]!!.jsonArray.size) }
        assertEquals(setOf("movie:1", "movie:2", "movie:3", "movie:4"), server.dealt())
    }

    @Test
    fun aNoIsRememberedAndAMatchToo() {
        val file = java.io.File.createTempFile("match", ".json").also { it.deleteOnExit() }
        val memory = MatchMemory(file)
        server.stop()
        server = MatchServer({ "x" }, deck, port = 0, memory = memory).also { it.start(5_000, false) }
        val a = join("A"); val b = join("B")
        vote(a, "movie:1", false)
        vote(a, "movie:2", true); vote(b, "movie:2", true)
        assertTrue(memory.daysSince("movie:1") != null)
        assertTrue("the match is remembered too", memory.daysSince("movie:2") != null)
        assertEquals(null, memory.daysSince("movie:3"))
    }
}
