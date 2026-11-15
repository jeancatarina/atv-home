package dev.jean.atvhome.devotional

import dev.jean.atvhome.social.net.LanServer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

/**
 * The devotional site on the home network: open it on any phone, tap your name, tap "Fiz o devocional".
 * No accounts and no cloud; the board is the file [Devotional] saves.
 */
class DevotionalServer(
    private val board: Devotional,
    private val page: () -> String,
    private val today: () -> LocalDate = { LocalDate.now() },
    port: Int = PORT,
) : LanServer(port) {

    override fun route(session: IHTTPSession): Response = when (session.uri) {
        "/" -> html(page())
        "/state" -> jsonResponse(stateJson())
        "/checkin" -> {
            val b = jsonBody(session)
            val date = b.str("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today()
            val ok = board.mark(b.str("id").orEmpty(), date, today(), (b["done"] as? JsonPrimitive)?.let { runCatching { it.boolean }.getOrNull() } ?: true)
            if (ok) jsonResponse(stateJson()) else text(Response.Status.BAD_REQUEST, "")
        }
        "/people" -> {
            val b = jsonBody(session)
            val remove = b.str("remove")
            val ok = if (remove != null) { board.removePerson(remove); true } else board.addPerson(b.str("name").orEmpty()) != null
            if (ok) jsonResponse(stateJson()) else text(Response.Status.BAD_REQUEST, "")
        }
        else -> text(Response.Status.NOT_FOUND, "")
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content

    fun stateJson(): JsonObject {
        val now = today()
        return buildJsonObject {
            put("today", now.toString())
            put("together", board.together(now))
            Verses.of(now).let { v -> put("verse", buildJsonObject { put("text", v.text); put("ref", v.ref) }) }
            put("people", buildJsonArray {
                board.standings(now).forEach { s ->
                    val days = board.daysOf(s.person.id)
                    add(buildJsonObject {
                        put("id", s.person.id); put("name", s.person.name)
                        put("streak", s.streak); put("best", s.best); put("total", s.total)
                        put("last30", s.last30); put("doneToday", s.doneToday)
                        // Last 7 days, oldest first: true when done
                        put("week", buildJsonArray { (6 downTo 0).forEach { add(JsonPrimitive(now.minusDays(it.toLong()) in days)) } })
                    })
                }
            })
        }
    }

    companion object { const val PORT = 8767 }
}
