package dev.jean.atvhome.social.game

import dev.jean.atvhome.social.QUESTIONS
import dev.jean.atvhome.social.people
import dev.jean.atvhome.social.games.GameCatalog
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Every game in [GameCatalog] gets this for free: played by random bots (with garbage actions,
 * people leaving, the clock jumping and the host nudging) it must never throw, always finish,
 * give the same result for the same seed, serialise every view and keep secrets out of them.
 */
val FILM_FIXTURE = (1..12).map { i ->
    "tmdb:$i|Filme Exemplo $i|${1990 + i}|Drama, Aventura|${90 + i}|A grande aventura de Exemplo $i|Atriz $i, Ator $i|Heroi $i|Uma história longa sobre Exemplo $i e seus amigos numa cidade distante que muda tudo.|http://x/p$i.jpg|Netflix"
}

class GameContractTest {

    private fun <S : Any> play(game: Game<S>, seed: Int, n: Int): Outcome {
        val random = Random(seed)
        var players = people(n)
        var now = 0L
        var s = game.setup(Setup(players, emptyMap(), Random(seed), now, contentOf(game.info.id)))
        var steps = 0
        while (game.over(s) == null) {
            check(steps++ < 4_000) { "${game.info.id} seed $seed with $n players did not finish in 4000 steps (phase ${game.phase(s)})" }
            val p = players.random(random)
            val view = game.phone(s, p.id)
            val json = SocialJson.encodeToString(view)
            assertEquals(view, SocialJson.decodeFromString<PhoneView>(json))
            players.forEach { q ->
                game.secrets(s, q.id).forEach { secret ->
                    assertFalse("${game.info.id}: ${q.id}'s view leaks \"$secret\"", SocialJson.encodeToString(game.phone(s, q.id)).contains(secret))
                }
            }
            when (random.nextInt(10)) {
                0 -> s = game.act(s, p.id, garbage(random), now).let { if (it is Step.Ok) it.state else s }
                1 -> { now += random.nextLong(0, 30_000); game.deadline(s)?.let { if (now >= it) s = game.timeout(s, now) } }
                2 -> s = game.host(s, HostCmd.entries.random(random), now)
                3 -> if (players.size > game.info.minPlayers && random.nextInt(8) == 0) {
                    players = players - p
                    s = game.left(s, p.id, now)
                }
                4 -> view.buttons.randomOrNull(random)?.let { b -> game.act(s, p.id, Action(Act.BUTTON, buildJsonObject { put("key", JsonPrimitive(b.key)) }), now).let { if (it is Step.Ok) s = it.state } }
                else -> legal(view.body, random)?.let { a -> game.act(s, p.id, a, now).let { if (it is Step.Ok) s = it.state } }
            }
        }
        return game.over(s)!!
    }

    /** The content pack the app ships for the game (so the shipped data is what gets exercised). */
    private fun contentOf(id: String): List<String> = if (id == "film5") FILM_FIXTURE else contentFile(id)

    private fun contentFile(id: String): List<String> =
        java.io.File("../app/src/main/assets/social/content/$id.json").takeIf { it.isFile }
            ?.let { SocialJson.decodeFromString<List<String>>(it.readText()) } ?: QUESTIONS

    private fun garbage(r: Random) = when (r.nextInt(4)) {
        0 -> Action("zzz")
        1 -> Action(Act.PICK, buildJsonObject { put("id", JsonPrimitive("nobody")) })
        2 -> Action(Act.CHOOSE)
        else -> Action(Act.TEXT, buildJsonObject { put("value", JsonPrimitive("x".repeat(500))) })
    }

    private fun legal(w: Widget, r: Random): Action? = when (w) {
        is Widget.PickPlayer -> Action(Act.PICK, buildJsonObject { put("id", JsonPrimitive(w.options.random(r))) })
        is Widget.Choose -> Action(Act.CHOOSE, buildJsonObject { put("key", JsonPrimitive(w.options.random(r).key)) })
        is Widget.Ready -> Action(Act.READY)
        is Widget.TextInput -> Action(Act.TEXT, buildJsonObject { put("value", JsonPrimitive(listOf("gato", "casa", "mar", "x").random(r))) })
        is Widget.Scale -> Action(Act.SCALE, buildJsonObject { put("value", JsonPrimitive(r.nextInt(0, 11))) })
        else -> null
    }

    @Test
    fun everyGameSurvivesRandomPlayAndFinishes() {
        for (game in GameCatalog.all) for (n in game.info.minPlayers..game.info.maxPlayers) for (seed in 1..25) {
            assertNotNull(play(game, seed * 31 + n, n))
        }
    }

    @Test
    fun theSameSeedGivesTheSameResult() {
        for (game in GameCatalog.all) {
            val n = game.info.idealMin
            assertEquals(play(game, 7, n), play(game, 7, n))
        }
    }

    @Test
    fun infoIsCoherent() {
        val ids = GameCatalog.all.map { it.info.id }
        assertEquals("ids are unique", ids.size, ids.toSet().size)
        for (g in GameCatalog.all.map { it.info }) {
            assertTrue(g.name, g.minPlayers in 2..g.idealMin && g.idealMin <= g.idealMax && g.idealMax <= g.maxPlayers && g.maxPlayers <= 12)
            assertEquals("three steps on the sheet: ${g.id}", 3, g.howTo.size)
            g.settings.forEach { assertTrue("${g.id}.${it.key} default in range", it.default in it.options.indices) }
        }
    }
}
