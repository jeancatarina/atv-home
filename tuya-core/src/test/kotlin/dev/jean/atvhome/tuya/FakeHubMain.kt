package dev.jean.atvhome.tuya

import java.io.File

/**
 * The app without the house: a fake hub on this computer with the lights of tuya.json (or two of its
 * own). Writes build/tuya-fake.json pointing at this computer, to push to the TV in place of the real one.
 * Commands on stdin: `press <cid> <dp> on|off` (the wall switch), `drop` (hub reboot), `quit`.
 */
fun main(args: Array<String>) {
    val version = args.indexOf("--version").let { if (it >= 0) Version.of(args[it + 1]) else Version.V33 }
    val real = File("build/tuya.json").takeIf { it.isFile }?.let { HomeConfig.decode(it.readText()) }
    val lights = real?.lights ?: listOf(
        Light("fake-estar", listOf(1), "Luz cozinha", "Sala de Estar", "Estar"),
        Light("fake-estar", listOf(2), "Luz sala", "Sala de Estar", "Estar"),
        Light("fake-estar", listOf(3), "Luz mesa", "Sala de Estar", "Estar"),
    )
    val hub = FakeHub(version, port = Codec.PORT)
    lights.forEach { l -> l.dps.forEach { hub.states.getOrPut(l.cid) { java.util.concurrent.ConcurrentHashMap() }[it] = false } }
    val ip = java.net.NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }?.hostAddress ?: "127.0.0.1"
    val config = HomeConfig(Hub(id = hub.id, key = hub.key, ip = ip, version = version.wire), lights)
    File("build").mkdirs()
    File("build/tuya-fake.json").writeText(config.encode())
    println("Fake hub ${version.wire} on $ip:${hub.port}, ${lights.size} lights → build/tuya-fake.json")
    while (true) {
        val line = readlnOrNull()?.trim() ?: break
        val parts = line.split(" ")
        when (parts[0]) {
            "press" -> hub.press(parts[1], parts[2].toInt(), parts[3] == "on")
            "drop" -> hub.dropClients()
            "quit" -> break
            "status" -> println(hub.states)
        }
    }
    hub.close()
}
