package dev.jean.atvhome.social.screens

import dev.jean.atvhome.social.game.Highlight
import dev.jean.atvhome.social.game.Player
import dev.jean.atvhome.social.night.NightEvent
import dev.jean.atvhome.social.profile.Career
import dev.jean.atvhome.social.profile.HouseRecord
import dev.jean.atvhome.social.profile.Rival
import dev.jean.atvhome.social.progress.Gain
import dev.jean.atvhome.social.room.PlayerInfo
import dev.jean.atvhome.social.room.RoomSnapshot

/** Fake rooms for screenshots: no server, no clock, just data. */
object Fixtures {
    val names = listOf("Ana", "Bia", "Caio", "Dani", "Edu", "Fernanda", "Gui", "Helena", "Igor", "Júlia", "Kauê", "Maria Eduarda")

    fun players(n: Int, offline: Set<Int> = emptySet()) = (0 until n).map { i ->
        PlayerInfo(Player("p${i + 1}", names[i % names.size], i % 12, i % 6), online = i !in offline, spectator = false)
    }

    /** XP that lands each person in a different metal: platinum, gold, silver, silver, bronze… */
    val xps = listOf(9000, 4300, 2400, 1500, 900, 420, 160, 40, 30, 20, 10, 0)

    fun career(i: Int, xp: Int, chips: Int = xp / 12, games: Int = xp / 90, nights: Int = 3) =
        Career(
            "device-%016d".format(i), names[i % names.size], chips = chips, games = games, nights = nights, xp = xp, v = 2, seasonXp = xp / 3, wins = games / 3,
            badges = (0 until (12 - i).coerceAtLeast(0)).associate { "b$it" to 1L },
            challenges = dev.jean.atvhome.social.progress.Progression.weekKey(System.currentTimeMillis()).let { wk ->
                dev.jean.atvhome.social.progress.Challenges.forWeek(wk).mapIndexed { j, ch -> "$wk:${ch.id}" to if (j == 0 && i < 2) ch.target else minOf(ch.target - 1, (i + j) % ch.target) }.toMap()
            },
        )

    fun room(n: Int, events: List<NightEvent> = emptyList(), ranked: Boolean = false, gains: Map<String, Gain> = emptyMap()): RoomSnapshot {
        val ps = players(n)
        val ranking = if (ranked) (0 until 8).map { career(it, xps[it]) } else emptyList()
        return RoomSnapshot(
            version = 1, key = "KEY0123456789abcdef", players = ps, game = null, paused = false,
            night = ps.take(5).mapIndexed { i, p -> p.player.id to (9 - i * 2).coerceAtLeast(1) }.toMap(),
            reactions = emptyList(), events = events, ranking = ranking, rankingSeason = ranking.take(5),
            careers = if (ranked) ps.mapIndexed { i, p -> p.player.id to career(i, xps[i % xps.size]) }.toMap() else emptyMap(), gains = gains,
        )
    }

    val someEvents = listOf(NightEvent("mostlikely", Highlight("UNANIMOUS", "p2", 3)))
}


object Rich {
    private const val T = 1_700_000_000_000L
    fun career() = Career(
        "device-0000000000000001", "Ana", chips = 180, games = 64, nights = 12, xp = 3100, v = 2, wins = 27, seasonXp = 640, medals = listOf("2026-10:1"),
        streakWeeks = 4, perGame = mapOf("mostlikely" to 40, "bomb" to 30), winsPerGame = mapOf("mostlikely" to 8, "bomb" to 5),
        badges = mapOf("first_win" to T, "wins_10" to T, "games_10" to T, "games_50" to T, "explorer" to T, "defuser" to T, "on_fire" to T, "sync_90" to T, "nerves" to T, "fooled_4" to T),
        counters = mapOf("played:mostlikely" to 20, "played:bomb" to 9, "played:couple" to 6), challenges = mapOf("${dev.jean.atvhome.social.progress.Progression.weekKey(System.currentTimeMillis())}:play5" to 3),
        rivals = mapOf("d2" to Rival("Bia", 7, 5), "d3" to Rival("Caio", 3, 6)),
    )
    val records = listOf(
        HouseRecord("bomb", "spare", 94, "Ana e Bia", "d1", T), HouseRecord("frequency", "sync", 96, "Caio", "d3", T),
        HouseRecord("twotruths", "fooled", 5, "Dani", "d4", T), HouseRecord("film5", "early", 1, "Edu", "d5", T),
    )
}
