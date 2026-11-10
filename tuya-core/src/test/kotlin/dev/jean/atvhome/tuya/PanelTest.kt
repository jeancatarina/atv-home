package dev.jean.atvhome.tuya

import org.junit.Assert.assertEquals
import org.junit.Test

class PanelTest {
    private val cozinha = Light("e", listOf(1), "Luz cozinha", "Sala de Estar", "Estar")
    private val sala = Light("e", listOf(2), "Luz sala", "Sala de Estar", "Estar")
    private val cena = Light("h", listOf(3), "Cena Hall", "Corredor", "Hall", load = false, kind = Kind.SCENE)
    private val lavabo = Light("l", listOf(1, 2, 3), "Lavabo", "Lavabo", "Lavabo")
    private val spare = Light("t", listOf(3), "Toalete 3", "Banheiro", "Toalete", load = false, kind = Kind.SPARE)
    private val all = listOf(cozinha, sala, cena, lavabo, spare)
    private val states = mapOf("e:1" to true, "e:2" to false, "h:3" to false, "l:1" to false, "l:2" to true, "l:3" to false, "t:3" to true)

    @Test fun `filters`() {
        assertEquals(listOf(cozinha, lavabo, spare), Panel.filter(all, states, Show.LIT))
        assertEquals(listOf(cozinha, sala, lavabo), Panel.filter(all, states, Show.LIGHTS))
        assertEquals(listOf(cena), Panel.filter(all, states, Show.SCENES))
        assertEquals(listOf(cozinha, sala), Panel.filter(all, states, Show.ALL, "Sala de Estar"))
        assertEquals(listOf("Sala de Estar", "Corredor", "Lavabo", "Banheiro"), Panel.rooms(all))
    }

    @Test fun `off turns off every key that is on, one frame per switch`() {
        assertEquals(mapOf("e" to mapOf(1 to false), "l" to mapOf(2 to false), "t" to mapOf(3 to false)), Panel.switching(all, states, on = false))
    }

    @Test fun `on turns on the lights that are off and never a scene`() {
        assertEquals(mapOf("e" to mapOf(2 to true), "l" to mapOf(1 to true, 3 to true)), Panel.switching(all, states, on = true))
    }

    @Test fun `nothing to do is an empty map`() {
        assertEquals(emptyMap<String, Map<Int, Boolean>>(), Panel.switching(listOf(sala), states, on = false))
    }
}
