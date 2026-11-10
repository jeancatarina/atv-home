package dev.jean.atvhome.tuya

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The house as the TV knows it, written once by `:tuya-core:setup` (tools/tuya-setup.sh) from the
 * Tuya cloud and pushed to the TV as tuya.json. The TV never talks to the cloud.
 */
@Serializable
data class HomeConfig(val hub: Hub, val lights: List<Light>) {
    /** Zigbee sub-devices to query on connect, in the order of [lights]. */
    val cids: List<String> get() = lights.map { it.cid }.distinct()

    fun encode(): String = TuyaJson.encodeToString(serializer(), this)

    companion object {
        fun decode(text: String): HomeConfig = TuyaJson.decodeFromString(serializer(), text)
    }
}

/**
 * The Zigbee hub. [ip] and [version] can be missing (the hub moved, or the setup could not see it on
 * the network): the TV then finds it by its UDP broadcast ([Discovery]).
 */
@Serializable
data class Hub(
    val id: String,
    val key: String,
    val ip: String? = null,
    val version: String? = null,
    val port: Int = Codec.PORT,
)

/**
 * One light: a key of a wall switch ("Luz cozinha" is key 1 of "Estar"), or a whole switch whose
 * keys have no names ("Lavabo"), which is on when any key is and switches all of them, like the
 * switch's power button in the Tuya app. [load]: false for keys with nothing wired to them (scene
 * keys), shown only on the house screen.
 */
@Serializable
data class Light(
    val cid: String,
    val dps: List<Int>,
    val name: String,
    val room: String,
    val device: String,
    val load: Boolean = true,
) {
    val key: String get() = "$cid:" + dps.joinToString("+")
    val keys: List<String> get() = dps.map { keyOf(cid, it) }
}

fun keyOf(cid: String, dp: Int) = "$cid:$dp"

internal val TuyaJson = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = false }
