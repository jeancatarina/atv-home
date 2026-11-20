package dev.jean.atvhome.social.room

import dev.jean.atvhome.social.games.GameCatalog
import dev.jean.atvhome.social.profile.Ledger
import dev.jean.atvhome.social.FakeClock
import dev.jean.atvhome.social.game.SocialJson
import org.junit.Test
import kotlin.random.Random

/**
 * How many plays of each game (at its default settings, with the content the app ships) the house gets
 * before its content has to start over. Prints a table; fails below the target (365 plays; less for the two games listed in [minimum]). Run with `-i` to see the table.
 */
class YearTest {
    private val target = 365
    /**
     * The honest floor for the games where more content would mean worse content: a prompt that reads badly is
     * worse than one that comes back after two months. Raising these means writing better prompts, not combining more.
     */
    private val minimum = mapOf("blackstory" to 20, "missions" to 25, "frequency" to 55, "couple" to 60, "twotruths" to 50, "mostlikely" to 180, "tribunal" to 240, "psychologist" to 320)
    private val skip = setOf("film5", "bomb", "mafia") // content comes from the network or is generated on the spot

    private fun pack(id: String) = java.io.File("../app/src/main/assets/social/content/$id.json").takeIf { it.isFile }
        ?.let { SocialJson.decodeFromString<List<String>>(it.readText()) } ?: emptyList()

    @Test fun everyGameLastsAYearWithoutRepeating() {
        val short = ArrayList<String>()
        println("\nGAME PLAYS-BEFORE-RESTART (target $target)")
        for (g in GameCatalog.all.filter { it.info.id !in skip }) {
            val id = g.info.id
            val content = pack(id)
            val ledger = Ledger()
            val room = Room(clock = FakeClock(), content = { content }, ledger = ledger, random = Random(7))
            repeat(maxOf(g.info.minPlayers, 5).coerceAtMost(g.info.maxPlayers)) { room.join(room.key, "P$it", null, "device-bbbbbbbbbbbbb$it") }
            var plays = 0; var repeatAt: Int? = null
            val dealt = HashSet<String>()
            while (plays < target + 5 && repeatAt == null) {
                if (room.start(g) != null) break
                plays++
                @Suppress("UNCHECKED_CAST")
                val keys = (g as dev.jean.atvhome.social.game.Game<Any>).used(room.snapshot.value.game!!.state, content).map(dev.jean.atvhome.social.kit.Variety::key)
                if (keys.any { it in dealt }) repeatAt = plays - 1 else dealt += keys
                room.toLobby()
            }
            val restartedAt = repeatAt
            val lasted = restartedAt ?: plays
            println("%-14s %4d   (pack ${content.size})".format(id, lasted))
            if (lasted < (minimum[id] ?: target)) short += "$id=$lasted"
        }
        if (short.isNotEmpty()) throw AssertionError("below $target plays: $short")
    }
}
