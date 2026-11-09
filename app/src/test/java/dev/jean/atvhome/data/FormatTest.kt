package dev.jean.atvhome.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FormatTest {

    @Test
    fun ratingsUseCommaAndOneDecimal() {
        assertEquals("8,2", Format.rating(8.168))
        assertEquals("7,0", Format.rating(7.0))
    }

    @Test
    fun votesAreShortened() {
        assertEquals("312 mil votos", Format.votes(312_480))
        assertEquals("1,2 mi votos", Format.votes(1_234_567))
        assertEquals("2 mi votos", Format.votes(2_000_000))
        assertEquals("840 votos", Format.votes(840))
    }

    @Test
    fun runtimeAndSeasons() {
        assertEquals("2 h 18 min", Format.runtime(138))
        assertEquals("47 min", Format.runtime(47))
        assertEquals("2 h", Format.runtime(120))
        assertNull(Format.runtime(0))
        assertEquals("1 temporada", Format.seasons(1))
        assertEquals("4 temporadas", Format.seasons(4))
    }

    @Test
    fun episodeBadges() {
        val sunday = LocalDate.of(2026, 11, 8)
        assertEquals("Novo ep. sexta", Format.episodeBadge("2026-11-13", null, sunday))
        assertEquals("Novo ep. amanhã", Format.episodeBadge("2026-11-09", null, sunday))
        assertEquals("Ep. novo", Format.episodeBadge("2026-12-20", "2026-11-05", sunday))
        assertNull(Format.episodeBadge("2026-12-20", "2026-10-01", sunday))
    }
}
