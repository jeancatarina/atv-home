package dev.jean.atvhome.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QueryParserTest {

    private val providers = listOf(
        Provider(8, "Netflix", null),
        Provider(119, "Amazon Prime Video", null),
        Provider(1899, "HBO Max", null),
        Provider(337, "Disney Plus", null),
    )
    private val today = LocalDate.of(2026, 11, 8)

    private fun parse(s: String) = QueryParser.parse(s, providers, today)

    @Test
    fun shortComedyOnNetflixWithHighRating() {
        val r = parse("Comédia curta na Netflix com nota alta")
        assertEquals(setOf(Genre.COMEDY), r.filters.genres)
        assertEquals(setOf(8), r.filters.providerIds)
        assertEquals(100, r.filters.maxRuntime)
        assertEquals(7.5, r.filters.minImdb!!, 0.0)
        assertEquals("", r.leftover)
    }

    @Test
    fun thrillerFromThe90sOnPrime() {
        val r = parse("suspense dos anos 90 no prime")
        assertEquals(setOf(Genre.THRILLER), r.filters.genres)
        assertEquals(setOf(119), r.filters.providerIds)
        assertEquals(1990, r.filters.fromYear)
        assertEquals(1999, r.filters.toYear)
    }

    @Test
    fun horrorSeriesOnMax() {
        val r = parse("série de terror na max")
        assertEquals(MediaType.TV, r.filters.type)
        assertEquals(setOf(Genre.HORROR), r.filters.genres)
        assertEquals(setOf(1899), r.filters.providerIds)
    }

    @Test
    fun explicitDurationAndRating() {
        val r = parse("filme de ação até 1h30 com nota acima de 7,5")
        assertEquals(90, r.filters.maxRuntime)
        assertEquals(7.5, r.filters.minImdb!!, 0.0)
        assertEquals(setOf(Genre.ACTION), r.filters.genres)
    }

    @Test
    fun person() {
        val r = parse("filmes com o Wagner Moura")
        assertEquals("wagner moura", r.filters.person)
        assertTrue(r.filters.genres.isEmpty())
    }

    @Test
    fun plainTitleIsLeftForSearch() {
        val r = parse("O Agente Secreto")
        assertTrue(r.filters.isEmpty)
        assertEquals("agente secreto", r.leftover)
        assertNull(r.filters.person)
    }

    @Test
    fun recentReleases() {
        val r = parse("lançamentos de ficção científica no disney")
        assertEquals(2024, r.filters.fromYear)
        assertEquals(setOf(Genre.SCIFI), r.filters.genres)
        assertEquals(setOf(337), r.filters.providerIds)
    }

    @Test
    fun realityShowsAreSeries() {
        val r = parse("reality show na netflix")
        assertEquals(setOf(Genre.REALITY), r.filters.genres)
        assertEquals(MediaType.TV, r.filters.type)
        assertEquals(setOf(8), r.filters.providerIds)
        assertEquals("", r.leftover)
    }
}
