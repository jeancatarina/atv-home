package dev.jean.atvhome.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Brazilian Portuguese labels for the metadata line, ratings and badges. */
object Format {

    fun rating(value: Double): String = String.format(java.util.Locale.ROOT, "%.1f", value).replace('.', ',')

    fun votes(count: Int): String = when {
        count >= 1_000_000 -> "${rating(count / 1_000_000.0)} mi".replace(",0 ", " ")
        count >= 1_000 -> "${count / 1_000} mil"
        else -> count.toString()
    } + " votos"

    fun runtime(minutes: Int?): String? = when {
        minutes == null || minutes <= 0 -> null
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }

    fun seasons(count: Int?): String? = when {
        count == null || count <= 0 -> null
        count == 1 -> "1 temporada"
        else -> "$count temporadas"
    }

    /** "2022 · 4 temporadas · Drama, Comédia" (the IMDb badge and age rating are drawn apart). */
    fun metaLine(details: Details): String = listOfNotNull(
        details.title.year?.toString(),
        if (details.title.type == MediaType.TV) seasons(details.seasons) else runtime(details.runtime),
        details.genres.take(2).joinToString(", ").ifBlank { null },
    ).joinToString(" · ")

    /** Short lines under a card: "IMDb 8,1 · 2024". */
    fun cardLine(imdb: ImdbRating?, year: Int?): String = listOfNotNull(
        imdb?.let { "IMDb ${rating(it.rating)}" },
        year?.toString(),
    ).joinToString(" · ")

    /**
     * Badge for a series in My List: "Novo ep. sexta" when one airs within a week, "Ep. novo"
     * when one aired in the last 7 days.
     */
    fun episodeBadge(nextAirDate: String?, lastAirDate: String?, today: LocalDate = LocalDate.now()): String? {
        nextAirDate?.let(::parseDate)?.let { next ->
            val days = ChronoUnit.DAYS.between(today, next)
            when {
                days == 0L -> return "Novo ep. hoje"
                days == 1L -> return "Novo ep. amanhã"
                days in 2..6 -> return "Novo ep. ${weekday(next.dayOfWeek)}"
            }
        }
        lastAirDate?.let(::parseDate)?.let { last ->
            if (ChronoUnit.DAYS.between(last, today) in 0..6) return "Ep. novo"
        }
        return null
    }

    private fun parseDate(text: String) = runCatching { LocalDate.parse(text) }.getOrNull()

    private fun weekday(day: DayOfWeek) = when (day) {
        DayOfWeek.MONDAY -> "segunda"
        DayOfWeek.TUESDAY -> "terça"
        DayOfWeek.WEDNESDAY -> "quarta"
        DayOfWeek.THURSDAY -> "quinta"
        DayOfWeek.FRIDAY -> "sexta"
        DayOfWeek.SATURDAY -> "sábado"
        DayOfWeek.SUNDAY -> "domingo"
    }
}
