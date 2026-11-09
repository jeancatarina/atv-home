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
    val match: String? = null,
)

/**
 * "Match": the TV serves a small page on the home Wi-Fi; everyone opens it from a QR code, swipes
 * the same deck, and the first title everyone likes is the match. No accounts, no cloud: phones
 * talk straight to the TV.
 */
class MatchServer(private val context: Context, private val deck: List<Card>) : NanoHTTPD(PORT) {

    private val json = Json { ignoreUnknownKeys = true }
    private val people = LinkedHashMap<String, String>() // id → name
    private val likes = HashMap<String, MutableSet<String>>() // card key → ids that liked it
    private var voteCount = 0
    private var matched: String? = null
    private val seen = HashSet<String>() // matches already shown; "Outro match" moves past them
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
    private fun join(name: String): String {
        val id = "p${people.size + 1}"
        people[id] = name.trim().take(20).ifBlank { "Pessoa ${people.size + 1}" }
        publish()
        return id
    }

    @Synchronized
    private fun vote(id: String, key: String, like: Boolean) {
        if (id !in people || deck.none { it.key == key }) return
        voteCount++
        if (like) likes.getOrPut(key) { mutableSetOf() }.add(id) else likes[key]?.remove(id)
        if (matched == null) matched = nextMatch()
        publish()
    }

    private fun publish() {
        state.value = MatchState(people.values.toList(), voteCount, matched)
    }

    /** A match needs everyone in the room: two or more people who all liked the same title. */
    private fun nextMatch(): String? = deck.map { it.key }.firstOrNull { key ->
        key !in seen && people.size >= 2 && likes[key]?.containsAll(people.keys) == true
    }

    /** "Outro match": keep every vote and move to the next title the whole room liked, if any. */
    @Synchronized
    fun next() {
        matched?.let(seen::add)
        matched = nextMatch()
        publish()
    }

    private fun statusJson() = synchronized(this) {
        buildJsonObject {
            put("people", people.size)
            put("match", matched)
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
