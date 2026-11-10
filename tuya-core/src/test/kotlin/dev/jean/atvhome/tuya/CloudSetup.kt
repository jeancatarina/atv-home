package dev.jean.atvhome.tuya

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * One-time setup, on the computer (tools/tuya-setup.sh): reads from the Tuya cloud what the TV
 * cannot learn on its own (the hub's local key, each sub-device's Zigbee id, rooms and the names
 * given to each key in the app), finds the hub on the network by its broadcast and writes tuya.json.
 * The access id and secret stay in local.properties; tuya.json holds only the hub's local key.
 *
 *   --out <file>   where to write (default build/tuya.json)
 */
fun main(args: Array<String>) = runBlocking {
    val props = Properties().apply { File("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load) }
    fun prop(name: String) = props.getProperty(name)?.trim()?.ifBlank { null }
        ?: error("Falta $name no local.properties (passo a passo no README, seção Casa)")
    val cloud = TuyaCloud(prop("tuya.accessId"), prop("tuya.accessSecret"), props.getProperty("tuya.region", "us").trim())
    val out = File(args.indexOf("--out").let { if (it >= 0) args[it + 1] else "build/tuya.json" })

    val devices = cloud.devices()
    val hub = devices.firstOrNull { it.str("category") == "wg2" || (it.bool("sub") == false && it.str("category")?.startsWith("wg") == true) }
        ?: error("Nenhum hub Zigbee na conta. A conta do app está ligada ao projeto (Devices → Link App Account)?")
    val hubId = hub.str("id")!!
    val detail = cloud.get("/v1.0/devices/$hubId").jsonObject
    val localKey = detail.str("local_key") ?: error("A Tuya não devolveu a chave local do hub")
    val nodes = (cloud.get("/v1.0/devices/$hubId/sub-devices") as JsonArray).associate { it.jsonObject.str("id")!! to it.jsonObject.str("node_id") }

    val home = hub.str("bindSpaceId")
    val roomOf = HashMap<String, String>()
    val roomOrder = ArrayList<String>()
    if (home != null) {
        val rooms = (cloud.get("/v1.0/homes/$home/rooms").jsonObject["rooms"] as? JsonArray).orEmpty()
        for (room in rooms) {
            val name = room.jsonObject.str("name")!!.trim()
            roomOrder += name
            val inRoom = cloud.get("/v1.0/homes/$home/rooms/${room.jsonObject.str("room_id")}/devices") as? JsonArray
            inRoom?.forEach { d -> d.jsonObject.str("id")?.let { roomOf[it] = name } }
        }
    }

    val lights = ArrayList<Light>()
    for (d in devices.filter { it.bool("sub") == true && it.str("category") == "kg" }) {
        val id = d.str("id")!!
        val cid = nodes[id] ?: d.str("uuid") ?: continue
        val device = (d.str("customName") ?: d.str("name")!!).trim()
        val names = (cloud.get("/v1.0/devices/$id/multiple-names") as? JsonArray).orEmpty()
            .associate { it.jsonObject.str("identifier")!! to it.jsonObject.str("name")!!.trim() }
        val props2 = (cloud.get("/v2.0/cloud/thing/$id/shadow/properties").jsonObject["properties"] as? JsonArray).orEmpty().map { it.jsonObject }
        val keys = props2.filter { it.str("code")?.matches(Regex("switch_\\d+")) == true && it.str("type") == "bool" }
            .map { it.str("code")!! to it["dp_id"]!!.let { v -> (v as JsonPrimitive).intOrNull!! } }
            .ifEmpty { names.keys.filter { it.matches(Regex("switch_\\d+")) }.map { it to it.removePrefix("switch_").toInt() } }
        // A switch offline at setup (the doorbell's) stays off the row until the person puts it there
        val online = d.bool("isOnline") != false
        lights += nameKeys(device, keys.map { (code, dp) -> dp to names[code] }).map { (dps, name, load) ->
            Light(cid = cid, dps = dps, name = name, room = roomOf[id] ?: "Casa", device = device, load = load && online)
        }
    }
    val tidy = tidy(lights, roomOrder)
    lights.clear(); lights += tidy

    println("Procurando o hub na rede (até 15 s)…")
    val seen = Discovery.find(hubId, 15_000)
    if (seen == null) println("Não vi o hub na rede: a TV vai procurá-lo sozinha.")
    else println("Hub em ${seen.ip}, protocolo ${seen.version.wire}")

    val config = HomeConfig(Hub(id = hubId, key = localKey, ip = seen?.ip, version = seen?.version?.wire), lights)
    out.parentFile?.mkdirs()
    out.writeText(config.encode())
    println("${lights.size} teclas (${lights.count { it.load }} na fileira Casa) em ${devices.count { it.bool("sub") == true }} interruptores → ${out.path}")
    lights.groupBy { it.room }.forEach { (room, ls) ->
        println("  $room: " + ls.joinToString { (if (it.load) "" else "(") + it.name + (if (it.load) "" else ")") })
    }
}

/**
 * The lights of one switch. A key named in the app is a light with that name. A switch with no named
 * key is one light called after the switch, all keys together: nobody knows which key is wired (the
 * Lavabo's is key 2), and switching the spare ones does nothing. Hidden by default (house screen
 * only): scene keys ("Cena…", "Atalho…"), unnamed keys of a switch whose other keys have names, and
 * scene switches with no names ("Escada térreo cenas").
 */
internal fun nameKeys(device: String, keys: List<Pair<Int, String?>>): List<Triple<List<Int>, String, Boolean>> {
    fun custom(name: String?) = name != null && !name.matches(Regex("(?i)switch( \\d+)?|momentary.*|delay.*"))
    val sorted = keys.sortedBy { it.first }
    if (sorted.none { custom(it.second) }) {
        return listOf(Triple(sorted.map { it.first }, device, !device.contains("cena", ignoreCase = true)))
    }
    return sorted.mapIndexed { i, (dp, name) ->
        if (custom(name)) Triple(listOf(dp), name!!, !name.matches(Regex("(?i)(cena|atalho)\\b.*")))
        else Triple(listOf(dp), "$device ${i + 1}", false)
    }
}

/**
 * Rooms in the app's order; in a room, plain switches before scene switches ("Cenas estar"), by name.
 * A switch with no named key is called after itself: when a named key in the same room already has
 * that name ("Terraço" on "Terraço cenas"), the unnamed one leaves the row so the row has no twins.
 */
internal fun tidy(lights: List<Light>, roomOrder: List<String>): List<Light> {
    val named = lights.filter { it.name != it.device }.map { it.room to it.name.lowercase() }.toSet()
    return lights
        .map { if (it.name == it.device && (it.room to it.name.lowercase()) in named) it.copy(load = false) else it }
        .sortedWith(compareBy<Light>(
            { roomOrder.indexOf(it.room).let { i -> if (i < 0) Int.MAX_VALUE else i } },
            { it.device.contains("cena", ignoreCase = true) },
            { it.device.lowercase() },
            { it.dps.first() },
        ))
}

/** The Tuya OpenAPI with HMAC-SHA256 request signing (developer.tuya.com, "Sign requests"). */
class TuyaCloud(private val id: String, private val secret: String, region: String) {
    private val host = when (region) {
        "eu" -> "openapi.tuyaeu.com"; "in" -> "openapi.tuyain.com"; "cn" -> "openapi.tuyacn.com"; else -> "openapi.tuyaus.com"
    }
    private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private var token: String = ""

    fun get(path: String): kotlinx.serialization.json.JsonElement {
        if (token.isEmpty()) token = call("/v1.0/token?grant_type=1", "").jsonObject.str("access_token")!!
        return call(path, token)
    }

    /** Every device of the linked app account, paged. */
    fun devices(): List<JsonObject> {
        val all = ArrayList<JsonObject>()
        var last = ""
        do {
            val page = (get("/v2.0/cloud/thing/device?page_size=20" + (if (last.isNotEmpty()) "&last_id=$last" else "")) as JsonArray).map { it.jsonObject }
            all += page
            last = page.lastOrNull()?.str("id").orEmpty()
        } while (page.size == 20)
        return all
    }

    private fun call(path: String, token: String): kotlinx.serialization.json.JsonElement {
        val t = System.currentTimeMillis().toString()
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val emptyBody = MessageDigest.getInstance("SHA-256").digest(ByteArray(0)).joinToString("") { "%02x".format(it) }
        val toSign = "GET\n$emptyBody\n\n$path"
        val sign = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
            doFinal((id + token + t + nonce + toSign).toByteArray())
        }.joinToString("") { "%02X".format(it) }
        val request = Request.Builder().url("https://$host$path")
            .header("client_id", id).header("t", t).header("nonce", nonce)
            .header("sign_method", "HMAC-SHA256").header("sign", sign)
            .apply { if (token.isNotEmpty()) header("access_token", token) }
            .build()
        val body = http.newCall(request).execute().use { it.body!!.string() }
        val json = TuyaJson.parseToJsonElement(body).jsonObject
        if (json["success"].let { (it as? JsonPrimitive)?.booleanOrNull } != true) {
            error("Tuya $path: ${json.str("msg")} (${json["code"]})")
        }
        return json["result"]!!
    }
}

private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull
