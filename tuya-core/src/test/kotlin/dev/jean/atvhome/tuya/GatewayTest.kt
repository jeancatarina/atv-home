package dev.jean.atvhome.tuya

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The gateway against [FakeHub], once per protocol version. */
class GatewayTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val hubs = mutableListOf<FakeHub>()
    @After fun tearDown() { scope.cancel(); hubs.forEach { it.close() } }

    private fun config(hub: FakeHub, key: String = hub.key) = HomeConfig(
        Hub(id = hub.id, key = key, ip = "127.0.0.1", version = hub.version.wire, port = hub.port),
        listOf(Light("estar", listOf(1), "Luz cozinha", "Sala de Estar", "Estar"), Light("estar", listOf(2), "Luz sala", "Sala de Estar", "Estar")),
    )

    private fun each(test: suspend (FakeHub, Gateway) -> Unit) = runBlocking {
        for (v in Version.entries) {
            val hub = FakeHub(v).also { hubs += it }
            hub.states["estar"] = mutableMapOf(1 to true, 2 to false)
            val gw = Gateway(config(hub), scope, heartbeatMs = 200, confirmMs = 800)
            gw.start()
            withTimeout(5_000) { gw.link.first { it is Link.Online } }
            withTimeout(5_000) { gw.states.first { it.size == 2 } }
            try { test(hub, gw) } catch (e: Throwable) { throw AssertionError("protocol ${v.wire}: ${e.message}", e) }
            gw.stop()
        }
    }

    @Test fun `connects and reads the current state`() = each { _, gw ->
        assertEquals(mapOf("estar:1" to true, "estar:2" to false), gw.states.value)
    }

    @Test fun `a command reaches the hub and is confirmed`() = each { hub, gw ->
        assertTrue(gw.set("estar", 2, true))
        assertEquals(true, hub.states["estar"]!![2])
        assertEquals(true, gw.states.value["estar:2"])
    }

    @Test fun `the wall switch shows up without polling`() = each { hub, gw ->
        hub.press("estar", 1, false)
        withTimeout(2_000) { gw.states.first { it["estar:1"] == false } }
    }

    @Test fun `several keys in one frame`() = each { hub, gw ->
        assertTrue(gw.set("estar", mapOf(1 to false, 2 to false)))
        assertEquals(mapOf(1 to false, 2 to false), hub.states["estar"]!!.toMap())
    }

    @Test fun `no answer puts the old value back`() = each { hub, gw ->
        hub.answering = false
        assertFalse(gw.set("estar", 2, true))
        assertEquals(false, gw.states.value["estar:2"])
        hub.answering = true
    }

    @Test fun `heartbeats keep the connection and a dropped one comes back`() = each { hub, gw ->
        Thread.sleep(700)
        assertTrue(hub.received.any { it.cmd == Cmd.HEARTBEAT })
        hub.dropClients()
        withTimeout(2_000) { gw.link.first { it !is Link.Online } }
        withTimeout(5_000) { gw.link.first { it is Link.Online } }
        assertTrue(gw.set("estar", 1, false))
    }

    @Test fun `a wrong local key is reported as such`() = runBlocking {
        for (v in listOf(Version.V34, Version.V35)) {
            val hub = FakeHub(v).also { hubs += it }
            val gw = Gateway(config(hub, key = "fedcba9876543210"), scope)
            gw.start()
            val link = withTimeout(5_000) { gw.link.first { it is Link.BadKey } }
            assertTrue(v.wire, link is Link.BadKey)
            gw.stop()
        }
    }
}
