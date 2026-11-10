package dev.jean.atvhome.social.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.jean.atvhome.social.SocialViewModel
import dev.jean.atvhome.social.game.Action
import dev.jean.atvhome.social.game.Game
import dev.jean.atvhome.social.game.HostCmd
import dev.jean.atvhome.social.game.Outcome
import dev.jean.atvhome.social.game.Player
import dev.jean.atvhome.social.game.Setup
import dev.jean.atvhome.social.game.SocialJson
import dev.jean.atvhome.social.game.Step
import dev.jean.atvhome.social.games.GameCatalog
import dev.jean.atvhome.social.games.bomb.Bomb
import dev.jean.atvhome.social.games.bomb.BombState
import dev.jean.atvhome.social.games.frequency.FStage
import dev.jean.atvhome.social.games.frequency.FrequencyState
import dev.jean.atvhome.social.night.Tone
import dev.jean.atvhome.social.progress.Gain
import dev.jean.atvhome.social.room.GameSnap
import dev.jean.atvhome.social.room.RoomSnapshot
import dev.jean.atvhome.social.ui.GamePage
import dev.jean.atvhome.social.ui.NightTab
import dev.jean.atvhome.social.ui.ProfileDetail
import dev.jean.atvhome.social.ui.ProfilesTab
import dev.jean.atvhome.social.ui.RankingTab
import dev.jean.atvhome.social.ui.WithLevels
import dev.jean.atvhome.social.ui.SettingsTab
import dev.jean.atvhome.social.ui.StageScreen
import dev.jean.atvhome.social.ui.Backdrop
import dev.jean.atvhome.social.ui.HomeBackdrop
import dev.jean.atvhome.social.ui.LocalFaces
import dev.jean.atvhome.social.ui.Tab
import dev.jean.atvhome.social.ui.TopBar
import dev.jean.atvhome.ui.theme.InicioTheme
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/** One screenshot per screen of the TV, at 960x540 dp like the BRAVIA. Run with -Proborazzi.test.record=true. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi", application = android.app.Application::class)
class ScreenShotsTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { InicioTheme { Box(Modifier.fillMaxSize()) { content() } } }
        rule.onRoot().captureRoboImage("build/shots/$name.png")
    }

    private fun content(id: String): List<String> =
        File("src/main/assets/social/content/$id.json").takeIf { it.isFile }?.let { SocialJson.decodeFromString<List<String>>(it.readText()) } ?: emptyList()

    private fun players(n: Int) = Fixtures.players(n).map { it.player }

    @Suppress("UNCHECKED_CAST")
    private fun <S : Any> start(id: String, n: Int, settings: Map<String, Int> = emptyMap()): Pair<Game<S>, S> {
        val g = GameCatalog.byId(id) as Game<S>
        val pack = if (id == "film5") (1..12).map { "k$it|Filme Exemplo $it|${2000 + it}|Drama|120|Uma frase|Ana Atriz, Beto Ator|Heroi|Uma história longa de $it|http://x/$it.jpg|Netflix" } else content(id)
        return g to g.setup(Setup(players(n), settings, Random(7), 0, pack))
    }

    private fun <S : Any> stage(name: String, id: String, n: Int, settings: Map<String, Int> = emptyMap(), tune: (Game<S>, S) -> S = { _, s -> s }) {
        val (g, s0) = start<S>(id, n, settings)
        val s = tune(g, s0)
        val snap = Fixtures.room(n, ranked = true)
        val game = GameSnap(g.info, s, g.phase(s), System.currentTimeMillis() + 17_000, g.over(s))
        shot(name) { StageScreen(snap, game, g.tvAction(s), null, {}, {}, {}) }
    }

    private fun pick(id: String) = Action("pick", buildJsonObject { put("id", JsonPrimitive(id)) })
    private fun key(k: String, type: String = "button") = Action(type, buildJsonObject { put("key", JsonPrimitive(k)) })
    private fun <S : Any> Game<S>.ok(s: S, by: String, a: Action): S = (act(s, by, a, 0) as Step.Ok).state

    // --- Game pages ------------------------------------------------------------------------------------

    @Test fun gamePageBomb() = shot("page-bomb") { GamePage(GameCatalog.byId("bomb")!!, Fixtures.room(3).copy(records = Rich.records), false, {}, {}) }
    @Test fun gamePageMostLikely() = shot("page-mostlikely") { GamePage(GameCatalog.byId("mostlikely")!!, Fixtures.room(5), false, {}, {}) }
    @Test fun gamePageTooFew() = shot("page-undercover-few") { GamePage(GameCatalog.byId("undercover")!!, Fixtures.room(2), false, {}, {}) }

    // --- Stages ------------------------------------------------------------------------------------------

    @Test fun stageMostLikelyVoting() = stage<dev.jean.atvhome.social.games.mostlikely.MostLikelyState>("stage-mostlikely-vote", "mostlikely", 6) { g, s -> g.ok(g.ok(g.ok(s, "p1", pick("p3")), "p2", pick("p3")), "p4", pick("p1")) }
    @Test fun stageMostLikelyReveal() = stage<dev.jean.atvhome.social.games.mostlikely.MostLikelyState>("stage-mostlikely-reveal", "mostlikely", 5) { g, s -> s.players.fold(s) { a, p -> g.ok(a, p.id, pick("p3")) } }
    @Test fun stageUndercoverClues() = stage<dev.jean.atvhome.social.games.undercover.UndercoverState>("stage-undercover-clues", "undercover", 6) { g, s -> g.host(s, HostCmd.CONTINUE, 0) }
    @Test fun stageFrequencyDial() = stage<FrequencyState>("stage-frequency-dial", "frequency", 5) { _, s -> s.copy(stage = FStage.DIAL, dial = 64) }
    @Test fun stageCoupleReveal() = stage<dev.jean.atvhome.social.games.couple.CoupleState>("stage-couple", "couple", 2) { g, s -> s.players.fold(s) { a, p -> g.ok(a, p.id, key("1", "choose")) } }
    @Test fun stageTwoTruthsVote() = stage<dev.jean.atvhome.social.games.twotruths.TwoTruthsState>("stage-twotruths-vote", "twotruths", 5) { g, s -> g.host(g.ok(g.ok(s, s.teller.id, key("1", "choose")), s.teller.id, key("go")), HostCmd.CONTINUE, 0).let { g.host(it, HostCmd.CONTINUE, 0) } }
    @Test fun stageSpyfallTalk() = stage<dev.jean.atvhome.social.games.spyfall.SpyState>("stage-spyfall", "spyfall", 6) { g, s -> g.host(s, HostCmd.CONTINUE, 0) }
    @Test fun stageInsiderPlay() = stage<dev.jean.atvhome.social.games.insider.InsiderState>("stage-insider", "insider", 5) { g, s -> g.ok(g.ok(g.host(s, HostCmd.CONTINUE, 0), s.master, key("yes")), s.master, key("no")) }
    @Test fun stagePsychologist() = stage<dev.jean.atvhome.social.games.psychologist.PsychologistState>("stage-psychologist", "psychologist", 6) { g, s -> g.host(s, HostCmd.CONTINUE, 0) }
    @Test fun stageBlackStory() = stage<dev.jean.atvhome.social.games.blackstory.BlackStoryState>("stage-blackstory", "blackstory", 3) { g, s -> g.ok(g.ok(s, s.guardian, key("yes")), s.guardian, key("almost")) }
    @Test fun stageMissions() = stage<dev.jean.atvhome.social.games.missions.MissionsState>("stage-missions", "missions", 5) { g, s -> g.ok(g.ok(s, "p2", key("m0", "choose")), "p2", key("done")) }
    @Test fun stageMafiaNight() = stage<dev.jean.atvhome.social.games.mafia.MafiaState>("stage-mafia-night", "mafia", 7) { g, s -> g.host(g.host(s, HostCmd.CONTINUE, 0), HostCmd.CONTINUE, 0).let { g.host(it, HostCmd.CONTINUE, 0) } }
    @Test fun stageTribunalProof() = stage<dev.jean.atvhome.social.games.tribunal.TribunalState>("stage-tribunal", "tribunal", 6) { g, s -> g.host(g.host(s, HostCmd.CONTINUE, 0), HostCmd.CONTINUE, 0) }
    @Test fun stageFilm() = stage<dev.jean.atvhome.social.games.film5.FilmState>("stage-film5", "film5", 3) { g, s -> g.host(g.host(s, HostCmd.CONTINUE, 0), HostCmd.CONTINUE, 0) }
    @Test fun stageBombOverview() = stage<BombState>("stage-bomb-overview", "bomb", 3, mapOf("level" to 2)) { _, s -> s.copy(strikes = 1) }
    @Test fun stageBombWires() = stage<BombState>("stage-bomb-wires", "bomb", 3, mapOf("level" to 2)) { _, s -> s.copy(focus = s.modules.indexOfFirst { it is dev.jean.atvhome.social.games.bomb.Wires }.coerceAtLeast(0)) }

    // --- Results -----------------------------------------------------------------------------------------

    private fun result(name: String, id: String, n: Int, out: Outcome) {
        val (g, s) = start<Any>(id, n)
        // Some rise a level, some only fill their bar
        val gains = Fixtures.players(n).take(6).mapIndexed { i, p -> val before = Fixtures.xps[i] - (if (i % 2 == 0) 40 else -10); p.player.id to Gain(120 - i * 12, before, before + 120 - i * 12) }.toMap()
        val snap = Fixtures.room(n, ranked = true, gains = gains)
        shot(name) { StageScreen(snap, GameSnap(g.info, s, g.phase(s), null, out), null, "Cara de pau nível profissional.", {}, {}, {}) }
    }

    @Test fun resultMostLikely() = result("result-mostlikely", "mostlikely", 6, Outcome(mapOf("p3" to 14, "p1" to 9, "p5" to 7, "p2" to 5, "p4" to 3, "p6" to 1), "Caio recebeu mais votos da noite"))
    @Test fun resultBombWon() = result("result-bomb", "bomb", 3, Outcome(mapOf("p1" to 4, "p2" to 4, "p3" to 4), "Bomba desarmada com 38 segundos de sobra", coopWon = true))

    // --- Tabs and overlays ---------------------------------------------------------------------------------

    private fun home(tab: Tab, snap: RoomSnapshot, body: @androidx.compose.runtime.Composable () -> Unit) = shot("tab-${tab.name.lowercase()}") {
        WithLevels(snap) {
            HomeBackdrop(snap)
            androidx.compose.foundation.layout.Column { TopBar(tab, {}, snap, {}); body() }
        }
    }

    @Test fun tabRanking() { val s = Fixtures.room(5, ranked = true).copy(records = Rich.records); home(Tab.RANKING, s) { RankingTab(s, {}) } }
    @Test fun tabProfiles() { val s = Fixtures.room(5, ranked = true); home(Tab.PROFILES, s) { ProfilesTab(s, {}) } }
    @Test fun tabNight() { val s = Fixtures.room(5, Fixtures.someEvents); home(Tab.NIGHT, s) { NightTab(s, {}) } }
    @Test fun tabSettings() { val s = Fixtures.room(5); home(Tab.SETTINGS, s) { SettingsTab(Tone.FAMILY, {}, true, {}, {}) } }
    @Test fun profileDetail() = shot("profile-detail") { ProfileDetail(Rich.career(), {}) }
    @Test fun celebrations() {
        val ps = players(3)
        shot("celebrations") {
            androidx.compose.foundation.layout.Column(Modifier.padding(40.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                dev.jean.atvhome.social.ui.CelebrationCard(dev.jean.atvhome.social.progress.Celebration(1, "LEVEL", ps[0].id, "Nível 7", "Alma da Festa"), ps[0])
                dev.jean.atvhome.social.ui.CelebrationCard(dev.jean.atvhome.social.progress.Celebration(2, "BADGE", ps[1].id, "Na mosca", "Acertou em cheio em Frequência", 3), ps[1])
                dev.jean.atvhome.social.ui.CelebrationCard(dev.jean.atvhome.social.progress.Celebration(3, "RECORD", ps[2].id, "Bomba desarmada em 0:41", "Novo recorde da casa"), ps[2])
            }
        }
    }
    @Test fun resultWithCelebration() {
        val (g, s) = start<Any>("mostlikely", 5)
        val ps = players(5)
        val snap = Fixtures.room(5, ranked = true, gains = ps.mapIndexed { i, p -> p.id to Gain(100, Fixtures.xps[i], Fixtures.xps[i] + 100) }.toMap())
        shot("result-celebration") {
            StageScreen(snap, GameSnap(g.info, s, g.phase(s), null, Outcome(mapOf("p1" to 8, "p2" to 6, "p3" to 4), "A turma ficou dividida")), null, "Que rodada boa!", {}, {}, {})
            androidx.compose.foundation.layout.Box(Modifier.padding(start = 48.dp, top = 100.dp)) {
                dev.jean.atvhome.social.ui.CelebrationCard(dev.jean.atvhome.social.progress.Celebration(1, "BADGE", ps[1].id, "Cara de Pau", "Recebeu todos os votos 3 vezes", 2), ps[1], compact = true)
            }
        }
    }
    private fun ceremonySnap(): RoomSnapshot {
        val ps = Fixtures.players(6)
        val awards = listOf(
            dev.jean.atvhome.social.night.Award("FOOLED", "Cara de Pau", "Bia enganou 4 pessoas de uma vez", "p2"),
            dev.jean.atvhome.social.night.Award("SYNC", "Sintonia Total", "Caio chegou a 92% de sintonia", "p3"),
            dev.jean.atvhome.social.night.Award("PRESENCE", "Presença de Palco", "Dani marcou presença em todas as partidas", "p4"),
            dev.jean.atvhome.social.night.Award("CHAMPION", "Destaque da Noite", "Ana terminou na frente com 21 fichas", "p1"),
        )
        return Fixtures.room(6, ranked = true).copy(awards = awards)
    }

    @Test fun ceremonyTrophy() = shot("ceremony-trophy") { dev.jean.atvhome.social.ui.WithLevels(ceremonySnap()) { dev.jean.atvhome.social.ui.CeremonyView(ceremonySnap(), 1, {}, {}, androidx.compose.ui.focus.FocusRequester()) } }
    @Test fun ceremonyChampion() = shot("ceremony-champion") { dev.jean.atvhome.social.ui.WithLevels(ceremonySnap()) { dev.jean.atvhome.social.ui.CeremonyView(ceremonySnap(), 3, {}, {}, androidx.compose.ui.focus.FocusRequester()) } }
    @Test fun ceremonyShelf() = shot("ceremony-shelf") { dev.jean.atvhome.social.ui.WithLevels(ceremonySnap()) { dev.jean.atvhome.social.ui.CeremonyView(ceremonySnap(), 4, {}, {}, androidx.compose.ui.focus.FocusRequester()) } }
    @Test fun pauseMenu() {
        val (g, s) = start<Any>("mostlikely", 5)
        val snap = Fixtures.room(5, ranked = true)
        shot("pause") {
            StageScreen(snap, GameSnap(g.info, s, g.phase(s), System.currentTimeMillis() + 15_000, null), g.tvAction(s), null, {}, {}, {})
            dev.jean.atvhome.social.ui.PauseView({}, {}, {}, androidx.compose.ui.focus.FocusRequester())
        }
    }
}
