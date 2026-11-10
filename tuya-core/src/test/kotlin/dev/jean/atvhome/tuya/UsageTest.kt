package dev.jean.atvhome.tuya

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageTest {
    private val cozinha = Light("e", listOf(1), "Luz cozinha", "Sala de Estar", "Estar")
    private val sala = Light("e", listOf(2), "Luz sala", "Sala de Estar", "Estar")
    private val lavabo = Light("l", listOf(1, 2, 3), "Lavabo", "Lavabo", "Lavabo")
    private val day = 24 * 60 * 60 * 1000L

    @Test fun `the most used light comes first and the rest keep their order`() {
        val u = Usage()
        u.used(lavabo.key, 0); u.used(lavabo.key, 0); u.used(sala.key, 0)
        assertEquals(listOf(lavabo, sala, cozinha), u.rank(listOf(cozinha, sala, lavabo), 0))
    }

    @Test fun `old habits fade`() {
        val u = Usage()
        repeat(4) { u.used(cozinha.key, 0) }
        assertEquals(2.0, u.score(cozinha.key, 30 * day), 1e-9)
        repeat(3) { u.used(sala.key, 60 * day) }
        assertEquals(listOf(sala, cozinha), u.rank(listOf(cozinha, sala), 60 * day))
    }

    @Test fun `scores survive a round trip through preferences`() {
        val u = Usage().apply { used(lavabo.key, 5); used(cozinha.key, 7) }
        val back = Usage.decode(u.encode())
        assertEquals(u.score(lavabo.key, 7), back.score(lavabo.key, 7), 1e-9)
        assertEquals(0.0, Usage.decode("junk\n=1@2\nk=x@y").score("k", 0), 0.0)
    }

    @Test fun `only an off to on change is a use, not the first status after connecting`() {
        val all = listOf(cozinha, sala, lavabo)
        assertEquals(emptyList<Light>(), Usage.turnedOn(all, emptyMap(), mapOf("e:1" to true)))
        assertEquals(listOf(cozinha), Usage.turnedOn(all, mapOf("e:1" to false, "e:2" to false), mapOf("e:1" to true, "e:2" to false)))
        // A whole switch already on does not count again when a second key comes on
        assertEquals(emptyList<Light>(), Usage.turnedOn(all, mapOf("l:1" to false, "l:2" to true), mapOf("l:1" to true, "l:2" to true)))
        assertEquals(listOf(lavabo), Usage.turnedOn(all, mapOf("l:1" to false, "l:2" to false, "l:3" to false), mapOf("l:1" to true, "l:2" to true, "l:3" to true)))
    }
}
