package dev.jean.atvhome.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The title.ratings.tsv layout of IMDb's dataset, out of order, with a broken line and CRLF. */
class ImdbRatingsTest {

    private val index = ImdbRatings.parse(
        (
            "tconst\taverageRating\tnumVotes\n" +
                "tt0903747\t9.5\t2694802\n" +
                "tt0000001\t5.7\t2100\r\n" +
                "tt10872600\t7.9\t1050000\n" +
                "not a line\n" +
                "tt0137523\t10\t42"
            ).byteInputStream(),
    )

    @Test
    fun findsRatingsAndVotesById() {
        assertEquals(4, index.size)
        assertEquals(ImdbRating(10.0, 42), ImdbRatings.find(index, 137523))
        assertEquals(ImdbRating(9.5, 2694802), ImdbRatings.find(index, 903747))
        assertEquals(ImdbRating(7.9, 1050000), ImdbRatings.find(index, 10872600))
        assertEquals(ImdbRating(5.7, 2100), ImdbRatings.find(index, 1))
        assertNull(ImdbRatings.find(index, 1234567))
    }

    @Test
    fun capsHugeVoteCounts() {
        val big = ImdbRatings.parse("h\th\th\ntt1\t8.0\t99999999\n".byteInputStream())
        assertEquals(0xFFFFFF, ImdbRatings.find(big, 1)!!.votes)
    }
}
