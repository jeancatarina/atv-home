package dev.jean.atvhome.devotional

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Points, levels and the couple's tree: the playful side of the board. Pure rules, no clock and no disk. */
object Game {
    const val BASE = 10
    const val TOGETHER_BONUS = 5
    private const val RUN_CAP = 7
    private const val RUN_STEP = 2

    private val PERSON = listOf(0, 30, 100, 250, 500, 1000)
    val personLevels = listOf("Início", "Constância", "Dedicação", "Fidelidade", "Perseverança", "Plenitude")

    /** The tree grows with the couple's points together. */
    private val TREE = listOf(0, 40, 120, 300, 600, 1200)
    val treeNames = listOf("Semente", "Broto", "Muda", "Árvore jovem", "Árvore frondosa", "Árvore com frutos")

    /** A day is worth [BASE] plus 2 for each day already in the run (up to 6 more), plus a bonus when everyone did it. */
    fun points(days: Set<LocalDate>, everyoneDays: Set<LocalDate>?): Int {
        var total = 0
        var run = 0
        var previous: LocalDate? = null
        for (d in days.sorted()) {
            run = if (previous != null && previous == d.minusDays(1)) run + 1 else 1
            previous = d
            total += BASE + RUN_STEP * (minOf(run, RUN_CAP) - 1)
            if (everyoneDays != null && d in everyoneDays) total += TOGETHER_BONUS
        }
        return total
    }

    data class Step(val index: Int, val name: String, val points: Int, val startAt: Int, val nextAt: Int?)

    fun personLevel(points: Int): Step = step(points, PERSON, personLevels)
    fun tree(points: Int): Step = step(points, TREE, treeNames)

    private fun step(points: Int, limits: List<Int>, names: List<String>): Step {
        val i = limits.indexOfLast { points >= it }.coerceAtLeast(0)
        return Step(i, names[i], points, limits[i], limits.getOrNull(i + 1))
    }

    /** At least this many days between two forgiven days: one "dia de graça" a week, so a miss never breaks a long run. */
    private const val GRACE_SPACING = 7L

    /** A streak: [length] days done, and the forgiven [graceDays] inside it, most recent first. */
    data class Run(val length: Int, val graceDays: List<LocalDate>)

    /**
     * The run of days ending today (or yesterday, while today is still open). A single missed day is
     * forgiven when the day before it was done and no other day was forgiven in the previous week.
     */
    fun run(days: Set<LocalDate>, today: LocalDate): Run {
        var cursor = if (today in days) today else today.minusDays(1)
        var n = 0
        var lastGap: LocalDate? = null
        val gaps = ArrayList<LocalDate>()
        while (true) {
            if (cursor in days) { n++; cursor = cursor.minusDays(1); continue }
            val before = cursor.minusDays(1)
            val spaced = lastGap == null || ChronoUnit.DAYS.between(cursor, lastGap) >= GRACE_SPACING
            if (before in days && spaced) { gaps += cursor; lastGap = cursor; cursor = before } else break
        }
        return Run(n, gaps)
    }

    /** Monday to [today]: the week the duel is played in. */
    fun weekStart(today: LocalDate): LocalDate = today.minusDays((today.dayOfWeek.value - 1).toLong())
}
