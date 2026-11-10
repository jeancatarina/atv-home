package dev.jean.atvhome.tuya

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * The TV's code against the real hub, from this computer: connects with build/tuya.json, prints
 * what it reads and, with `--toggle <name>`, switches that light and back, timing each confirmation.
 *   ./gradlew :tuya-core:probe --args="--toggle Escritório"
 */
fun main(args: Array<String>) = runBlocking {
    val config = HomeConfig.decode(File("build/tuya.json").readText())
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val started = System.nanoTime()
    val gw = Gateway(config, scope, log = { println("  · $it") }, locate = { Discovery.find(config.hub.id) })
    gw.start()
    withTimeout(20_000) { gw.link.first { it is Link.Online || it is Link.BadKey } }.let { println("link: $it") }
    // An offline switch (the doorbell) never answers: wait for the rest, then say who is missing
    kotlinx.coroutines.withTimeoutOrNull(3_000) { gw.states.first { s -> config.lights.all { l -> l.keys.all { it in s } } } }
    val missing = config.lights.filter { l -> l.keys.any { it !in gw.states.value } }
    println("${config.lights.size - missing.size} of ${config.lights.size} lights read in ${(System.nanoTime() - started) / 1_000_000} ms" +
        if (missing.isEmpty()) "" else "; no answer: " + missing.joinToString { it.name })
    config.lights.filter { l -> l.keys.any { gw.states.value[it] == true } }.forEach { println("  on: ${it.name} (${it.room})") }
    val name = args.indexOf("--toggle").let { if (it >= 0) args[it + 1] else null }
    if (name != null) {
        val light = config.lights.first { it.name.equals(name, ignoreCase = true) }
        val was = light.keys.any { gw.states.value[it] == true }
        for (on in listOf(!was, was)) {
            val t = System.nanoTime()
            val ok = gw.set(light.cid, light.dps.associateWith { on })
            println("${light.name} → ${if (on) "on" else "off"}: ${if (ok) "confirmed" else "NOT confirmed"} in ${(System.nanoTime() - t) / 1_000_000} ms")
            delay(1_500)
        }
    }
    gw.stop()
    scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    Unit
}
