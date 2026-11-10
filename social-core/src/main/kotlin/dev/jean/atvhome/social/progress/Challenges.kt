package dev.jean.atvhome.social.progress

import dev.jean.atvhome.social.game.Category
import dev.jean.atvhome.social.profile.Career
import kotlin.random.Random

/** A weekly goal, the same for the whole household. [step] says how far one game moves it (it sees the career as it was before the game). */
class Challenge(val id: String, val text: String, val target: Int, val step: (PlayerResult, Career, NightContext) -> Int)

object Challenges {

    val pool: List<Challenge> = listOf(
        Challenge("win3", "Ganhe 3 partidas", 3) { r, _, _ -> if (r.won) 1 else 0 },
        Challenge("play5", "Jogue 5 partidas", 5) { _, _, _ -> 1 },
        Challenge("deduction2", "Ganhe 2 jogos de dedução e blefe", 2) { r, _, _ -> if (r.won && r.category == Category.DEDUCTION) 1 else 0 },
        Challenge("talk2", "Ganhe 2 jogos de conversa e risadas", 2) { r, _, _ -> if (r.won && r.category == Category.TALK) 1 else 0 },
        Challenge("coop2", "Vença 2 jogos cooperativos", 2) { r, _, _ -> if (r.won && r.coop) 1 else 0 },
        Challenge("variety3", "Jogue 3 jogos diferentes", 3) { r, before, _ -> if (r.gameId in before.weekGames) 0 else 1 },
        Challenge("bomb1", "Desarme uma bomba", 1) { r, _, _ -> if (r.gameId == "bomb" && r.won) 1 else 0 },
        Challenge("bigtable", "Jogue com 6 pessoas ou mais", 1) { r, _, _ -> if (r.tablePlayers >= 6) 1 else 0 },
        Challenge("duo2", "Jogue 2 jogos para dois", 2) { r, _, _ -> if (r.category == Category.DUO) 1 else 0 },
        Challenge("moment", "Faça um momento marcante (que vira troféu)", 1) { r, _, _ -> if (r.highlights.isNotEmpty()) 1 else 0 },
        Challenge("streak2", "Ganhe 2 partidas seguidas", 1) { _, _, ctx -> if (ctx.winStreak >= 2) 1 else 0 },
        Challenge("rich", "Junte 15 fichas em uma semana", 15) { r, _, _ -> r.chips },
    )

    /** Three challenges, picked by the week so every phone in the house sees the same ones. */
    fun forWeek(week: String): List<Challenge> = pool.shuffled(Random(week.hashCode().toLong())).take(3)

    fun progress(c: Career, week: String): List<Pair<Challenge, Int>> = forWeek(week).map { it to (c.challenges["$week:${it.id}"] ?: 0).coerceAtMost(it.target) }
}
