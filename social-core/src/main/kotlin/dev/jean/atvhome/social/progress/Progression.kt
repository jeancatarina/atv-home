package dev.jean.atvhome.social.progress

import dev.jean.atvhome.social.game.Category
import dev.jean.atvhome.social.game.Highlight
import dev.jean.atvhome.social.game.PlayerId
import dev.jean.atvhome.social.profile.Career
import dev.jean.atvhome.social.profile.Ledger
import dev.jean.atvhome.social.profile.RecordBreak
import dev.jean.atvhome.social.profile.Rival
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.IsoFields

/** A moment worth a party on the TV and on the person's phone: a level, a badge, a record, a challenge. */
@Serializable
data class Celebration(val id: Int, val kind: String, val who: PlayerId, val title: String, val detail: String, val tier: Int = 0)

/** The XP a person just earned, with the totals before and after (the result screen fills a bar between them). */
data class Gain(val xp: Int, val before: Int, val after: Int) {
    val levelBefore get() = Progression.level(before)
    val levelAfter get() = Progression.level(after)
}

data class LevelProgress(val level: Int, val into: Int, val span: Int, val title: String) {
    val fraction: Float get() = if (span <= 0) 1f else (into.toFloat() / span).coerceIn(0f, 1f)
}

/** What happened to one person in one finished game. */
data class PlayerResult(
    val gameId: String,
    val category: Category,
    val coop: Boolean,
    val won: Boolean,
    val chips: Int,
    /** Highlights that belong to this person, plus the ones shared by the whole table. */
    val highlights: List<Highlight>,
    val tablePlayers: Int,
)

/** What the room knows around a game: when, whether it opens the night, and the person's winning run. */
data class NightContext(val now: Long, val firstOfNight: Boolean, val winStreak: Int)

data class Progress(val before: Career?, val after: Career, val gain: Gain, val badges: List<BadgeDef>, val challenges: List<Challenge>)

/**
 * XP, levels and everything a finished game does to a person's career. Pure rules over [Career]:
 * the room only calls [applyGame], so no game needs to know about progression.
 */
object Progression {

    const val MAX_LEVEL = 50
    const val CHALLENGE_XP = 60

    /** The week and the month are the TV's: the household's own calendar. Tests set this to UTC. */
    var zone: ZoneId = ZoneId.systemDefault()

    /** Total XP needed to reach [level]: a gentle curve, about 100 XP per game. */
    fun xpFor(level: Int): Int = if (level <= 1) 0 else (60 * (level - 1) + 8 * (level - 1) * (level - 1))

    fun level(xp: Int): Int {
        var l = 1
        while (l < MAX_LEVEL && xp >= xpFor(l + 1)) l++
        return l
    }

    fun progress(xp: Int): LevelProgress {
        val l = level(xp)
        return if (l >= MAX_LEVEL) LevelProgress(l, 0, 0, title(l)) else LevelProgress(l, xp - xpFor(l), xpFor(l + 1) - xpFor(l), title(l))
    }

    private val TITLES = listOf(
        1 to "Estreante", 5 to "Figurinha Carimbada", 10 to "Alma da Festa", 15 to "Faro Fino", 20 to "Mente Afiada", 25 to "Estrela da Sala",
        30 to "Mestre de Cerimônias", 35 to "Ás do Palco", 40 to "Lenda da Sala", 45 to "Patrimônio da Casa", 50 to "Eterno da Sala",
    )

    /** Titles are about the game, never about the person: nothing here is gendered. */
    fun title(level: Int): String = TITLES.last { it.first <= level }.second

    fun gameXp(chips: Int, won: Boolean, ownedHighlights: Int, firstOfNight: Boolean, winStreak: Int): Int {
        val base = 20 + chips * 10 + (if (won) 15 else 0) + minOf(2, ownedHighlights) * 25 + (if (firstOfNight) 30 else 0)
        return if (winStreak >= 3) base * 3 / 2 else base
    }

    fun weekKey(now: Long): String = Instant.ofEpochMilli(now).atZone(zone).let { "%d-W%02d".format(it.get(IsoFields.WEEK_BASED_YEAR), it.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)) }

    fun seasonKey(now: Long): String = Instant.ofEpochMilli(now).atZone(zone).let { "%d-%02d".format(it.year, it.monthValue) }

    /** The week before [week], to tell a streak from a gap. */
    private fun previousWeek(now: Long): String = weekKey(now - 7 * 86_400_000L)

    /** Applies one finished game to one person and says what is worth celebrating. */
    fun applyGame(ledger: Ledger, device: String, name: String, r: PlayerResult, ctx: NightContext): Progress {
        ledger.rollSeason(seasonKey(ctx.now))
        val before = ledger.get(device)
        val week = weekKey(ctx.now)
        val newBadges = ArrayList<BadgeDef>()
        val done = ArrayList<Challenge>()
        var gain = Gain(0, before?.xp ?: 0, before?.xp ?: 0)
        val after = ledger.mutate(device, name, ctx.now) { c0 ->
            // A new week starts the week's challenges and the variety list afresh
            val newWeek = c0.lastWeek != week
            var c = c0.copy(name = name)
            val streakWeeks = when {
                !ctx.firstOfNight || !newWeek -> c.streakWeeks
                c.lastWeek == previousWeek(ctx.now) -> c.streakWeeks + 1
                else -> 1
            }
            c = c.copy(
                chips = c.chips + r.chips, games = c.games + 1,
                nights = c.nights + if (ctx.firstOfNight) 1 else 0,
                perGame = c.perGame + (r.gameId to (c.perGame[r.gameId] ?: 0) + r.chips),
                wins = c.wins + if (r.won) 1 else 0,
                lastByGame = c.lastByGame + (r.gameId to ctx.now),
                winsPerGame = if (r.won) c.winsPerGame + (r.gameId to (c.winsPerGame[r.gameId] ?: 0) + 1) else c.winsPerGame,
                streakWeeks = streakWeeks, lastWeek = if (ctx.firstOfNight) week else c.lastWeek.ifBlank { week },
                weekGames = (if (c.weekGames.isEmpty() || (newWeek && ctx.firstOfNight)) emptyList() else c.weekGames).let { if (r.gameId in it) it else it + r.gameId },
            )
            val weekBefore = if (c0.weekGames.isEmpty() || (newWeek && ctx.firstOfNight)) emptyList() else c0.weekGames
            // Counters and bests that conquests read
            val counters = c.counters.toMutableMap(); val maxes = c.maxes.toMutableMap(); val mins = c.mins.toMutableMap()
            fun bump(k: String) { counters[k] = (counters[k] ?: 0) + 1 }
            bump("played:${r.gameId}")
            if (r.tablePlayers >= 6) bump("bigTable")
            if (r.coop && r.won) bump("coopWin")
            maxes["streakWins"] = maxOf(maxes["streakWins"] ?: 0, ctx.winStreak)
            maxes["streakWeeks"] = maxOf(maxes["streakWeeks"] ?: 0, streakWeeks)
            r.highlights.forEach { h ->
                bump("hl:${h.kind}")
                maxes["hl:${h.kind}"] = maxOf(maxes["hl:${h.kind}"] ?: 0, h.n)
                mins["hl:${h.kind}"] = minOf(mins["hl:${h.kind}"] ?: Int.MAX_VALUE, h.n)
            }
            c = c.copy(counters = counters, maxes = maxes, mins = mins)
            // Weekly challenges
            val prog = c.challenges.toMutableMap()
            var bonus = 0
            Challenges.forWeek(week).forEach { ch ->
                val key = "$week:${ch.id}"
                val have = prog[key] ?: 0
                if (have >= ch.target) return@forEach
                val now = minOf(ch.target, have + ch.step(r, c.copy(weekGames = weekBefore), ctx))
                if (now != have) prog[key] = now
                if (now >= ch.target) { done += ch; bonus += CHALLENGE_XP }
            }
            c = c.copy(challenges = prog.filterKeys { it.startsWith(week) })
            // XP, level, season
            val xp = gameXp(r.chips, r.won, r.highlights.size, ctx.firstOfNight, ctx.winStreak) + bonus
            c = c.copy(xp = c.xp + xp, seasonKey = seasonKey(ctx.now), seasonXp = (if (c.seasonKey == seasonKey(ctx.now)) c.seasonXp else 0) + xp)
            gain = Gain(xp, c0.xp, c.xp)
            // Badges last, on the updated numbers
            val unlocked = c.badges.toMutableMap()
            Badges.all.filter { it.id !in unlocked && it.test(c) }.forEach { unlocked[it.id] = ctx.now; newBadges += it }
            c.copy(badges = unlocked)
        }
        return Progress(before, after, gain, newBadges, done)
    }

    /** Competitive games: [winner] beat [loser]. Both people's head-to-head tally moves by one. */
    fun rivalry(ledger: Ledger, winner: String, winnerName: String, loser: String, loserName: String, now: Long) {
        fun bump(me: String, myName: String, other: String, otherName: String, won: Boolean) {
            ledger.mutate(me, myName, now) { c ->
                val r = c.rivals[other] ?: Rival(otherName)
                val n = (c.rivals + (other to r.copy(name = otherName, wins = r.wins + if (won) 1 else 0, losses = r.losses + if (won) 0 else 1)))
                c.copy(rivals = if (n.size > 8) n.entries.sortedByDescending { it.value.wins + it.value.losses }.take(8).associate { it.key to it.value } else n)
            }
        }
        if (winner == loser) return
        bump(winner, winnerName, loser, loserName, true)
        bump(loser, loserName, winner, winnerName, false)
    }

    /** The house records a result may beat. Group highlights (no owner) belong to the whole table. */
    fun offerRecords(ledger: Ledger, device: String, holder: String, gameId: String, highlights: List<Highlight>, now: Long): List<RecordBreak> =
        Records.defs.filter { it.gameId == gameId }.mapNotNull { def ->
            val values = highlights.filter { it.kind == def.kind }.map { it.n }
            val v = (if (def.higherBetter) values.maxOrNull() else values.minOrNull()) ?: return@mapNotNull null
            if (def.higherBetter && v <= 0) return@mapNotNull null
            ledger.offer(gameId, def.key, v, def.higherBetter, device, holder, now)
        }
}
