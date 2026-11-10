package dev.jean.atvhome.guard

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadersTest {

    private fun wants(known: Source?, inBreak: Boolean = false) =
        Source.entries.filter { ReaderChoice.wants(known, it, inBreak) }

    @Test fun aNewAppTriesEveryReader() {
        assertEquals(Source.entries, wants(null))
    }

    @Test fun aSessionAppSkipsTheCostlyReaders() {
        assertEquals(listOf(Source.SESSION), wants(Source.SESSION))
    }

    @Test fun aTreeAppNeverPaysForTheScreen() {
        assertEquals(listOf(Source.SESSION, Source.TREE), wants(Source.TREE))
    }

    @Test fun aScreenAppReadsTheTreeOnlyDuringABreak() {
        assertEquals(listOf(Source.SESSION, Source.SCREEN), wants(Source.SCREEN))
        assertEquals(Source.entries, wants(Source.SCREEN, inBreak = true))
    }
}
