package dev.jean.atvhome.data

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.Inet4Address
import java.net.NetworkInterface

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
)

/**
 * "Match": the TV serves a small page on the home Wi-Fi; everyone opens it from a QR code, swipes
 * the same deck, and the first title everyone likes is the match. No accounts, no cloud: phones
 * talk straight to the TV. Who wins is decided by [MatchRules].
 */
class MatchServer(private val context: Context, private val deck: List<Card>) : NanoHTTPD(PORT) {

    private val json = Json { ignoreUnknownKeys = true }
    private val rules = MatchRules(deck.map { it.key })
    private val state = MutableStateFlow(MatchState())
    val updates: StateFlow<MatchState> = state

    val url: String? get() = localIp()?.let { "http://$it:$listeningPort/" }

    override fun serve(session: IHTTPSession): Response = runCatching {
        when (session.uri) {
            "/" -> newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", page())
            "/deck" -> jsonResponse(deckJson())
            "/join" -> {
                val body = body(session)
                jsonResponse(buildJsonObject { put("id", join(body.str("name").orEmpty())) })
            }
            "/vote" -> {
                val body = body(session)
                vote(body.str("id").orEmpty(), body.str("key").orEmpty(), body.bool("like") == true)
                jsonResponse(statusJson())
            }
            "/state" -> jsonResponse(statusJson())
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "")
        }
    }.getOrElse {
        Log.w(TAG, "serve ${session.uri}", it)
        newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", it.message ?: "")
    }

    @Synchronized
    private fun join(name: String): String = rules.join(name).also { publish() }

    @Synchronized
    private fun vote(id: String, key: String, like: Boolean) {
        rules.vote(id, key, like)
        publish()
    }

    private fun publish() {
        state.value = MatchState(rules.people, rules.votes, rules.result)
    }

    /** "Outro match": keep every vote and move to the next result, if any. */
    @Synchronized
    fun next() {
        rules.next()
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

    private fun deckJson() = buildJsonObject {
        put("cards", buildJsonArray {
            deck.forEach { c ->
                add(buildJsonObject {
                    put("key", c.key); put("title", c.title); put("year", c.year)
                    put("imdb", c.imdb); put("where", c.where); put("poster", c.poster); put("overview", c.overview)
                })
            }
        })
    }

    private fun body(session: IHTTPSession): JsonObject {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val raw = files["postData"].orEmpty()
        return json.parseToJsonElement(raw.ifBlank { "{}" }) as? JsonObject ?: JsonObject(emptyMap())
    }

    private fun jsonResponse(o: JsonObject) =
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", o.toString())
            .apply { addHeader("Cache-Control", "no-store") }

    private fun page(): String = context.assets.open("match.html").bufferedReader().use { it.readText() }

    companion object {
        private const val TAG = "Match"
        const val PORT = 8765

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
