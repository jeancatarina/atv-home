package dev.jean.atvhome.social

import dev.jean.atvhome.social.game.FILM_FIXTURE
import dev.jean.atvhome.social.game.Act
import dev.jean.atvhome.social.game.Action
import dev.jean.atvhome.social.game.HostCmd
import dev.jean.atvhome.social.game.SocialJson
import dev.jean.atvhome.social.games.GameCatalog
import dev.jean.atvhome.social.net.LanServer
import dev.jean.atvhome.social.room.ExecutorTimer
import dev.jean.atvhome.social.game.Widget
import dev.jean.atvhome.social.room.JoinResult
import dev.jean.atvhome.social.room.PhoneEnvelope
import dev.jean.atvhome.social.room.Room
import dev.jean.atvhome.social.room.RoomServer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import kotlin.random.Random

/**
 * A development stand-in for the TV: a real room and server on this computer, serving the phone
 * page straight from the app's assets folder (edit, reload). Commands on stdin play the TV's part:
 * start [game] · continue · skip · pause · resume · lobby · again · newroom · status · quit.
 */
fun main(args: Array<String>) {
    val bots = args.indexOf("--bots").let { if (it >= 0) args[it + 1].toInt() else 0 }
    val port = args.indexOf("--port").let { if (it >= 0) args[it + 1].toInt() else RoomServer.PORT }
    val botMs = args.indexOf("--bot-ms").let { if (it >= 0) args[it + 1].toLong() else 1_500L }
    val assets = File("app/src/main/assets/social")
    val room = Room(
        content = { id -> if (id == "film5") FILM_FIXTURE else runCatching { SocialJson.decodeFromString<List<String>>(File(assets, "content/$id.json").readText()) }.getOrDefault(emptyList()) },
        timer = ExecutorTimer(),
    )
    val server = RoomServer(room, { path -> File(assets, path).takeIf { it.isFile }?.readBytes()?.let { b -> if (path == "web/index.html") String(b).replace("__V__", "dev").toByteArray() else b } }, port)
    server.start(5_000, false)
    val ip = LanServer.localIp() ?: "127.0.0.1"
    println("Phones: http://$ip:${server.listeningPort}/#k=${room.key}")
    println("Local : http://127.0.0.1:${server.listeningPort}/#k=${room.key}")

    val tokens = java.util.concurrent.CopyOnWriteArrayList<String>()
    fun addBots(n: Int) { repeat(n) { i -> (room.join(room.key, "Bot ${tokens.size + i + 1}") as? JoinResult.Joined)?.let { tokens += it.token } } }
    fun dropBots() { tokens.forEach { room.leave(it) }; tokens.clear() }
    addBots(bots)
    val random = Random.Default
    // Bots play any game: they read their own view and tap something legal, like a person would
    Thread {
        while (true) {
            Thread.sleep(botMs)
            tokens.forEach { t ->
                val view = room.view(t, 0, 0)?.json?.let { runCatching { SocialJson.decodeFromString<PhoneEnvelope>(it) }.getOrNull() } ?: return@forEach
                val v = view.view
                val body = v.body
                val action: Action? = when {
                    v.buttons.isNotEmpty() && random.nextInt(3) == 0 -> v.buttons.random(random).let { b -> Action(Act.BUTTON, buildJsonObject { put("key", JsonPrimitive(b.key)) }) }
                    body is Widget.PickPlayer -> body.options.randomOrNull(random)?.let { id -> Action(Act.PICK, buildJsonObject { put("id", JsonPrimitive(id)) }) }
                    body is Widget.Choose -> body.options.randomOrNull(random)?.let { o -> Action(Act.CHOOSE, buildJsonObject { put("key", JsonPrimitive(o.key)) }) }
                    body is Widget.Scale -> Action(Act.SCALE, buildJsonObject { put("value", JsonPrimitive(random.nextInt(0, 11))) })
                    body is Widget.TextInput -> Action(Act.TEXT, buildJsonObject { put("value", JsonPrimitive("palavra")) })
                    v.buttons.isNotEmpty() -> v.buttons.random(random).let { b -> Action(Act.BUTTON, buildJsonObject { put("key", JsonPrimitive(b.key)) }) }
                    else -> null
                }
                action?.let { room.act(t, null, v.phase, it) }
            }
        }
    }.apply { isDaemon = true }.start()

    println("Commands: bots N | start [id] | continue | skip | pause | resume | lobby | again | night | newnight | newroom | status | quit")
    generateSequence(::readLine).forEach { line ->
        val words = line.trim().split(' ')
        when (words[0]) {
            "start" -> println(room.start(GameCatalog.byId(words.getOrNull(1) ?: GameCatalog.all.first().info.id) ?: GameCatalog.all.first()) ?: "started")
            "continue" -> room.host(HostCmd.CONTINUE)
            "skip" -> room.host(HostCmd.SKIP)
            "pause" -> room.pause()
            "resume" -> room.resume()
            "lobby" -> room.toLobby()
            "again" -> println(room.again() ?: "again")
            "night" -> room.closeNight()
            "newnight" -> room.newNight()
            "bots" -> { dropBots(); addBots(words.getOrNull(1)?.toIntOrNull() ?: 0) }
            "newroom" -> { room.newRoom(); println("Phones: http://$ip:${server.listeningPort}/#k=${room.key}") }
            "status" -> println(room.snapshot.value.let { s -> "${s.players.size} people, ${s.players.count { it.face > 0 }} faces, game=${s.game?.info?.id} phase=${s.game?.phase} paused=${s.paused} night=${s.night}" })
            "quit" -> { server.stop(); room.close(); return }
        }
    }
}
