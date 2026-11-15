package dev.jean.atvhome.devotional

import java.time.LocalDate

/** Points, levels and the couple's tree: the playful side of the board. Pure rules, no clock and no disk. */
object Game {
    const val BASE = 10
    const val TOGETHER_BONUS = 5
    private const val RUN_CAP = 7
    private const val RUN_STEP = 2

    private val PERSON = listOf(0, 30, 100, 250, 500, 1000)
    val personLevels = listOf("Iniciante", "Constante", "Dedicado", "Fiel", "Mestre", "Lenda")

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

    /** Monday to [today]: the week the duel is played in. */
    fun weekStart(today: LocalDate): LocalDate = today.minusDays((today.dayOfWeek.value - 1).toLong())
}
