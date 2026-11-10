package dev.jean.atvhome.tuya

import org.junit.Assert.assertEquals
import org.junit.Test

/** How the keys of the house's switches become lights, and which go to the Casa row by default. */
class SetupTest {
    @Test fun `named keys keep the name the app gave them`() {
        assertEquals(
            listOf(Key(listOf(1), "Luz cozinha", Kind.LIGHT), Key(listOf(2), "Luz sala", Kind.LIGHT), Key(listOf(3), "Luz mesa", Kind.LIGHT)),
            nameKeys("Estar", listOf(1 to "Luz cozinha", 2 to "Luz sala", 3 to "Luz mesa")),
        )
    }

    @Test fun `a switch with no names is one light with all its keys`() {
        assertEquals(listOf(Key(listOf(1, 2, 3), "Lavabo", Kind.LIGHT)), nameKeys("Lavabo", listOf(1 to "Switch", 2 to "Switch 2", 3 to "Switch 3")))
        assertEquals(listOf(Key(listOf(1, 2), "Quartinho", Kind.LIGHT)), nameKeys("Quartinho", listOf(1 to "switch", 2 to "Switch 2")))
    }

    @Test fun `scene keys and spare keys stay off the row`() {
        assertEquals(
            listOf(Key(listOf(1), "Luz da escada", Kind.LIGHT), Key(listOf(2), "Cena da escada", Kind.SCENE)),
            nameKeys("Escada", listOf(1 to "Luz da escada", 2 to "Cena da escada")),
        )
        assertEquals(
            listOf(Key(listOf(1), "Luz do banheiro", Kind.LIGHT), Key(listOf(2), "Luz do banheiro 2", Kind.LIGHT), Key(listOf(3), "Toalete 3", Kind.SPARE)),
            nameKeys("Toalete", listOf(1 to "Luz do banheiro", 2 to "Luz do banheiro 2", 3 to "Switch 3")),
        )
        assertEquals(listOf(Key(listOf(1, 2, 3), "Escada térreo cenas", Kind.SCENE)), nameKeys("Escada térreo cenas", listOf(1 to "Switch", 2 to "Switch 2", 3 to "Switch 3")))
    }

    @Test fun `rooms in order, scene switches last, no twin names on the row`() {
        val lights = listOf(
            Light("c", listOf(1), "Trilho", "Sala de Estar", "Cenas estar"),
            Light("e", listOf(2), "Luz escada", "Sala de Estar", "escada térreo"),
            Light("a", listOf(1), "Luz cozinha", "Sala de Estar", "Estar"),
            Light("t", listOf(1, 2, 3), "Terraço", "Terraço", "Terraço"),
            Light("tc", listOf(2), "Terraço", "Terraço", "Terraço cenas"),
            Light("q", listOf(1), "Ninho", "Quarto principal", "Ninho"),
        )
        val tidy = tidy(lights, listOf("Sala de Estar", "Quarto principal", "Terraço"))
        assertEquals(listOf("Luz escada", "Luz cozinha", "Trilho", "Ninho", "Terraço", "Terraço"), tidy.map { it.name })
        assertEquals(listOf(false, true), tidy.filter { it.room == "Terraço" }.map { it.load })
    }
}
