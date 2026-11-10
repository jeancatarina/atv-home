package dev.jean.atvhome.data

import android.util.Log
import dev.jean.atvhome.social.net.LanServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One title of the shared deck, as the phones see it. */
data class Card(
    val key: String,
    val title: String,
    val year: Int?,
    val imdb: String?,
    val where: String?,
    val poster: String?,
    val overview: String,
)

/** What the TV shows while people vote. */
data class MatchState(
    val people: List<String> = emptyList(),
    val votes: Int = 0,
    val result: MatchOutcome? = null,
    /** Cards the fastest person still has ahead, and the deck so far: the TV adds more when this runs low. */
    val remaining: Int = 0,
    val deckSize: Int = 0,
)

/**
 * "Match": the TV serves a small page on the home Wi-Fi; everyone opens it from a QR code, swipes
 * the same deck, and the first title everyone likes is the match. No accounts, no cloud: phones
 * talk straight to the TV. Who wins is decided by [MatchRules].
 */
class MatchServer(
    private val page: () -> String, deck: List<Card>, port: Int = PORT,
    private val memory: MatchMemory = MatchMemory(),
) : LanServer(port) {

    private val deck = java.util.concurrent.CopyOnWriteArrayList(deck)
    private val rules = MatchRules(deck.map { it.key })
    private val state = MutableStateFlow(MatchState())
    val updates: StateFlow<MatchState> = state

    override fun route(session: IHTTPSession): Response =
        when (session.uri) {
            "/" -> html(page())
            "/deck" -> jsonResponse(deckJson(session.parms["from"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0))
            "/join" -> {
                val body = jsonBody(session)
                jsonResponse(buildJsonObject { put("id", join(body.str("name").orEmpty())) })
            }
            "/vote" -> {
                val body = jsonBody(session)
                vote(body.str("id").orEmpty(), body.str("key").orEmpty(), body.bool("like") == true)
                jsonResponse(statusJson())
            }
            "/state" -> jsonResponse(statusJson())
            else -> text(Response.Status.NOT_FOUND, "")
        }

    override fun onError(uri: String, error: Throwable) {
        Log.w(TAG, "serve $uri", error)
    }

    @Synchronized
    private fun join(name: String): String = rules.join(name).also { publish() }

    @Synchronized
    private fun vote(id: String, key: String, like: Boolean) {
        val before = rules.result?.key
        rules.vote(id, key, like)
        // A "no" sends the title to the end of the line for a while; a match is not shown again soon either
        if (!like) memory.pass(key)
        rules.result?.takeIf { it.key != before }?.let { memory.matched(it.key) }
        publish()
    }

    private fun publish() {
        state.value = MatchState(rules.people, rules.votes, rules.result, rules.remaining(), rules.size)
    }

    /** More cards for the phones to swipe; titles already in the deck are ignored. */
    @Synchronized
    fun append(more: List<Card>) {
        val have = deck.map { it.key }.toSet()
        val fresh = more.filter { it.key !in have }.distinctBy { it.key }
        if (fresh.isEmpty()) return
        deck += fresh
        rules.add(fresh.map { it.key })
        publish()
    }

    /** Keys of every card dealt so far, so the next batch never repeats one. */
    fun dealt(): Set<String> = deck.map { it.key }.toSet()

    /** "Outro match": keep every vote and move to the next result, if any. */
    @Synchronized
    fun next() {
        val before = rules.result?.key
        rules.next()
        rules.result?.takeIf { it.key != before }?.let { memory.matched(it.key) }
        publish()
    }

    private fun statusJson() = synchronized(this) {
        val r = rules.result
        buildJsonObject {
            put("people", rules.people.size)
            put("match", r?.key)
            put("unanimous", r?.unanimous ?: false)
            put("likes", r?.likes ?: 0)
        }
    }

    private fun deckJson(from: Int) = buildJsonObject {
        put("cards", buildJsonArray {
            deck.drop(from).forEach { c ->
                add(buildJsonObject {
                    put("key", c.key); put("title", c.title); put("year", c.year)
                    put("imdb", c.imdb); put("where", c.where); put("poster", c.poster); put("overview", c.overview)
                })
            }
        })
    }

    companion object {
        private const val TAG = "Match"
        const val PORT = 8765
    }
}
