package dev.jean.atvhome.social.games

import dev.jean.atvhome.social.game.Game
import dev.jean.atvhome.social.games.blackstory.BlackStory
import dev.jean.atvhome.social.games.bomb.Bomb
import dev.jean.atvhome.social.games.couple.Couple
import dev.jean.atvhome.social.games.mafia.Mafia
import dev.jean.atvhome.social.games.tribunal.Tribunal
import dev.jean.atvhome.social.games.film5.Film5
import dev.jean.atvhome.social.games.frequency.Frequency
import dev.jean.atvhome.social.games.insider.Insider
import dev.jean.atvhome.social.games.missions.Missions
import dev.jean.atvhome.social.games.mostlikely.MostLikely
import dev.jean.atvhome.social.games.psychologist.Psychologist
import dev.jean.atvhome.social.games.spyfall.Spyfall
import dev.jean.atvhome.social.games.twotruths.TwoTruths
import dev.jean.atvhome.social.games.undercover.Undercover

/** Every game of the Social area. Adding a game is one line here (plus its stage in the app). */
object GameCatalog {
    val all: List<Game<*>> = listOf(MostLikely, Undercover, Frequency, Couple, TwoTruths, Spyfall, Insider, Psychologist, BlackStory, Missions, Mafia, Tribunal, Film5, Bomb)

    fun byId(id: String): Game<*>? = all.firstOrNull { it.info.id == id }
}
