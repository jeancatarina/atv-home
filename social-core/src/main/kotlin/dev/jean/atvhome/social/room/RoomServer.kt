package dev.jean.atvhome.social.room

import dev.jean.atvhome.social.game.Action
import dev.jean.atvhome.social.net.LanServer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The phones' door to a [Room]. Thin on purpose: it checks who is asking, turns requests into room
 * calls and the room's answers into JSON. There is no route that returns another player's view.
 *
 * `assets` serves the phone page (`web/index.html`, `web/app.js`, …) from the app's assets.
 */
class RoomServer(
    private val room: Room,
    private val assets: (path: String) -> ByteArray?,
    port: Int = PORT,
) : LanServer(port, ipHostOnly = true) {

    private val joinTimes = HashMap<String, ArrayDeque<Long>>()

    /** A face photo travels as base64 in JSON: up to 24 KB of JPEG. Every other route stays at the 4 KB default. */
    override fun bodyLimit(uri: String) = if (uri == "/api/face") 36 * 1024 else super.bodyLimit(uri)

    override fun route(session: IHTTPSession): Response {
        val uri = session.uri
        return when {
            uri == "/" -> asset("web/index.html", "text/html; charset=utf-8", cache = false)
            uri.startsWith("/a/") -> {
                val name = uri.removePrefix("/a/")
                if (!SAFE_NAME.matches(name)) text(Response.Status.NOT_FOUND, "")
                else asset("web/$name", mime(name), cache = true)
            }
            uri == "/api/join" && session.method == Method.POST -> join(session)
            uri == "/api/view" -> view(session)
            uri == "/api/act" && session.method == Method.POST -> act(session)
            uri == "/api/react" && session.method == Method.POST -> authed(session) { token ->
                jsonResponse(buildJsonObject { put("ok", room.react(token, jsonBody(session).str("kind").orEmpty())) })
            }
            uri.startsWith("/api/face/") && session.method == Method.GET -> authed(session) { token ->
                // A seated phone may fetch the faces of the people at the table (for the night's poster)
                val bytes = room.faceFor(token, uri.removePrefix("/api/face/"))
                // No photo is not an error: an empty body, and the poster draws the person's shape instead
                val photo = bytes ?: ByteArray(0)
                newFixedLengthResponse(Response.Status.OK, "image/jpeg", photo.inputStream(), photo.size.toLong()).apply { addHeader("Cache-Control", "no-store") }
            }
            uri == "/api/face" && session.method == Method.POST -> authed(session) { token ->
                val bytes = runCatching { java.util.Base64.getDecoder().decode(jsonBody(session).str("jpeg").orEmpty()) }.getOrNull()
                val remember = jsonBody(session)["remember"]?.jsonPrimitive?.contentOrNull == "true"
                jsonResponse(buildJsonObject { put("ok", bytes != null && room.setFace(token, bytes, remember)) })
            }
            uri == "/api/forget" && session.method == Method.POST -> authed(session) { token ->
                jsonResponse(buildJsonObject { put("ok", room.forget(token)) })
            }
            uri == "/api/me" && session.method == Method.POST -> authed(session) { token ->
                jsonResponse(buildJsonObject { put("ok", room.rename(token, jsonBody(session).str("name").orEmpty())) })
            }
            uri == "/api/leave" && session.method == Method.POST -> authed(session) { token ->
                room.leave(token)
                jsonResponse(buildJsonObject { put("ok", true) })
            }
            else -> text(Response.Status.NOT_FOUND, "")
        }
    }

    private fun join(session: IHTTPSession): Response {
        if (!allowJoin(session.remoteIpAddress.orEmpty())) return text(TOO_MANY, "")
        val body = jsonBody(session)
        return when (val r = room.join(body.str("key").orEmpty(), body.str("name").orEmpty(), body.str("seat"), body.str("device"))) {
            is JoinResult.Joined -> jsonResponse(buildJsonObject {
                put("token", r.token); put("me", r.id); put("spectator", r.spectator)
            })
            JoinResult.BadKey -> text(FORBIDDEN, "")
            JoinResult.Full -> text(CONFLICT, "")
        }
    }

    private fun view(session: IHTTPSession): Response = authed(session) { token ->
        val since = session.parameters["since"]?.firstOrNull()?.toLongOrNull() ?: 0L
        // wait=0 answers at once: the phone asks that after a failure, to learn the TV is reachable
        val wait = session.parameters["wait"]?.firstOrNull()?.toLongOrNull()?.coerceIn(0L, WAIT_MS) ?: WAIT_MS
        val r = room.view(token, since, wait) ?: return@authed text(UNAUTHORIZED, "")
        // The envelope is a JSON object: the poll's own fields go in front of it
        val body = "{\"now\":${System.currentTimeMillis()},\"v\":${r.version},${r.json.substring(1)}"
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body).apply { addHeader("Cache-Control", "no-store") }
    }

    private fun act(session: IHTTPSession): Response = authed(session) { token ->
        val b = jsonBody(session)
        val a = Action(b.str("type").orEmpty(), b["data"] as? JsonObject ?: JsonObject(emptyMap()))
        when (val r = room.act(token, b.str("aid"), b.str("phase"), a)) {
            ActResult.Ok -> jsonResponse(buildJsonObject { put("ok", true) })
            is ActResult.Rejected -> jsonResponse(buildJsonObject { put("ok", false); put("why", r.why) })
            ActResult.Unauthorized -> text(UNAUTHORIZED, "")
        }
    }

    private fun authed(session: IHTTPSession, block: (token: String) -> Response): Response {
        val token = session.headers["authorization"]?.removePrefix("Bearer ")?.trim()
        return if (token.isNullOrEmpty()) text(UNAUTHORIZED, "") else block(token)
    }

    private fun asset(path: String, type: String, cache: Boolean): Response {
        val bytes = assets(path) ?: return text(Response.Status.NOT_FOUND, "")
        return newFixedLengthResponse(Response.Status.OK, type, bytes.inputStream(), bytes.size.toLong()).apply {
            // Files are named by app version in the page's own links, so they can be kept for good
            addHeader("Cache-Control", if (cache) "max-age=31536000, immutable" else "no-cache")
            addHeader("Content-Security-Policy", CSP)
        }
    }

    private fun allowJoin(ip: String): Boolean = synchronized(joinTimes) {
        val now = System.currentTimeMillis()
        val q = joinTimes.getOrPut(ip) { ArrayDeque() }
        while (q.isNotEmpty() && now - q.first() > 60_000) q.removeFirst()
        if (q.size >= JOINS_PER_MINUTE) return false
        q.addLast(now)
        if (joinTimes.size > 256) joinTimes.keys.removeAll { joinTimes[it].isNullOrEmpty() }
        true
    }

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull

    private fun mime(name: String) = when (name.substringAfterLast('.', "")) {
        "js" -> "application/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

    private class Code(private val code: Int, private val reason: String) : Response.IStatus {
        override fun getRequestStatus() = code
        override fun getDescription() = "$code $reason"
    }

    companion object {
        const val PORT = 8766
        private const val WAIT_MS = 25_000L
        private const val JOINS_PER_MINUTE = 5
        private val SAFE_NAME = Regex("[A-Za-z0-9._-]{1,64}")
        private val UNAUTHORIZED = Code(401, "Unauthorized")
        private val FORBIDDEN = Code(403, "Forbidden")
        private val CONFLICT = Code(409, "Conflict")
        private val TOO_MANY = Code(429, "Too Many Requests")
        private const val CSP = "default-src 'self'; img-src 'self' https://image.tmdb.org data:; style-src 'self' 'unsafe-inline'"
    }
}
