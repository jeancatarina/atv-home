package dev.jean.atvhome.social.progress

import dev.jean.atvhome.social.games.GameCatalog
import dev.jean.atvhome.social.profile.Career
import kotlinx.serialization.Serializable

@Serializable data class BadgeLine(val id: String, val name: String, val hint: String, val tier: Int, val at: Long? = null)
@Serializable data class ChallengeLine(val text: String, val progress: Int, val target: Int)
@Serializable data class RivalLine(val name: String, val wins: Int, val losses: Int)
@Serializable data class GameLine(val id: String, val name: String, val wins: Int, val played: Int, val chips: Int)

/** Everything a phone's profile tab shows about its own person. Never carries a device id. */
@Serializable
data class ProfileView(
    val level: Int, val title: String, val xp: Int, val into: Int, val span: Int,
    val seasonXp: Int, val seasonRank: Int? = null, val medals: List<String> = emptyList(),
    val streakWeeks: Int = 0, val wins: Int = 0,
    val badges: List<BadgeLine> = emptyList(), val challenges: List<ChallengeLine> = emptyList(),
    val rivals: List<RivalLine> = emptyList(), val games: List<GameLine> = emptyList(),
)

object ProfileViews {
    fun of(c: Career, seasonRank: Int?, now: Long): ProfileView {
        val p = Progression.progress(c.xp)
        val week = Progression.weekKey(now)
        return ProfileView(
            p.level, p.title, c.xp, p.into, p.span, c.seasonXp, seasonRank, c.medals, c.streakWeeks, c.wins,
            badges = Badges.all.map { BadgeLine(it.id, it.name, it.hint, it.tier, c.badges[it.id]) },
            challenges = Challenges.progress(c, week).map { (ch, n) -> ChallengeLine(ch.text, n, ch.target) },
            rivals = c.rivals.values.sortedByDescending { it.wins + it.losses }.take(5).map { RivalLine(it.name, it.wins, it.losses) },
            games = GameCatalog.all.map { g -> GameLine(g.info.id, g.info.name, c.winsPerGame[g.info.id] ?: 0, c.counters["played:${g.info.id}"] ?: 0, c.perGame[g.info.id] ?: 0) }
                .filter { it.played > 0 }.sortedByDescending { it.played },
        )
    }
}
