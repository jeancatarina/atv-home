package dev.jean.atvhome.social.games

import dev.jean.atvhome.social.people
import dev.jean.atvhome.social.game.*
import dev.jean.atvhome.social.games.mafia.*
import dev.jean.atvhome.social.games.tribunal.*
import dev.jean.atvhome.social.kit.Votes
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class MoreGamesTest {

    private fun key(k: String, type: String = Act.BUTTON) = Action(type, buildJsonObject { put("key", JsonPrimitive(k)) })
    private fun pick(id: String) = Action(Act.PICK, buildJsonObject { put("id", JsonPrimitive(id)) })
    private fun <S : Any> Game<S>.ok(s: S, by: String, a: Action, now: Long = 0): S = (act(s, by, a, now) as? Step.Ok)?.state ?: error("rejected: ${(act(s, by, a, now) as Step.Rejected).why}")
    private fun <S : Any> Game<S>.rejected(s: S, by: String, a: Action) = assertTrue(act(s, by, a, 0) is Step.Rejected)
    private fun setup(n: Int, content: List<String>, settings: Map<String, Int> = emptyMap(), seed: Int = 3) = Setup(people(n), settings, Random(seed), 0, content)

    // --- Máfia ---------------------------------------------------------------------------------------

    private fun mafia(n: Int = 6, seed: Int = 3, mode: Int = 0) = Mafia.setup(setup(n, emptyList(), mapOf("mode" to mode), seed))
    private fun MafiaState.at(step: Int) = copy(stage = MStage.NIGHT, step = step)
    private fun MafiaState.holder(r: MRole) = original.entries.firstOrNull { it.value == r }?.key

    @Test fun everyoneGetsACardAndThreeLieInTheCenter() {
        for (n in 5..10) {
            val s = mafia(n)
            assertEquals(n, s.original.size); assertEquals(3, s.center.size)
            assertEquals(s.original, s.current)
        }
        val beginner = mafia(7, mode = 1)
        assertTrue((beginner.original.values + beginner.center).all { it in listOf(MRole.MAFIOSO, MRole.DETETIVE, MRole.CIDADAO) })
    }

    @Test fun theNightScreenHasTheSameShapeForEveryRoleAtEveryStep() {
        val s = mafia(8)
        for (step in NIGHT_ORDER.indices) {
            val shapes = s.players.map { p ->
                val body = Mafia.phone(s.at(step), p.id).body
                assertTrue(body is Widget.Choose)
                (body as Widget.Choose).options.size
            }.toSet()
            assertEquals("step $step", 1, shapes.size)
        }
    }

    @Test fun aDecoyTapIsAcceptedAndChangesNothing() {
        val s = mafia(7)
        val citizen = s.original.entries.first { it.value == MRole.CIDADAO || it.value == MRole.INFORMANTE }.key
        val before = s.at(2)
        val after = Mafia.ok(before, citizen, key(before.players.first { it.id != citizen }.id, Act.CHOOSE))
        assertEquals(before.current, after.current); assertEquals(before.notes, after.notes)
    }

    @Test fun theBatedorSwapsAndSeesTheNewRole() {
        val s = mafia(8, seed = 5)
        val b = s.holder(MRole.BATEDOR) ?: return
        val target = s.players.first { it.id != b }.id
        val was = s.current.getValue(target)
        val n = Mafia.ok(s.at(3), b, key(target, Act.CHOOSE))
        assertEquals(was, n.current.getValue(b)); assertEquals(s.current.getValue(b), n.current.getValue(target))
        assertTrue(n.notes.getValue(b).contains(was.label))
        assertEquals("only one swap per night", n.current, Mafia.ok(n, b, key(s.players.last().id, Act.CHOOSE)).current)
    }

    @Test fun theFofoqueiroSwapsTwoOthersAndTheSonambuloSwapsWithTheCenter() {
        val s = mafia(9, seed = 11)
        s.holder(MRole.FOFOQUEIRO)?.let { f ->
            val (a, b) = s.players.map { it.id }.filter { it != f }.take(2)
            var n = Mafia.ok(s.at(4), f, key(a, Act.CHOOSE))
            assertEquals(s.current, n.current)
            n = Mafia.ok(n, f, key(b, Act.CHOOSE))
            assertEquals(s.current.getValue(b), n.current.getValue(a)); assertEquals(s.current.getValue(a), n.current.getValue(b))
        }
        s.holder(MRole.SONAMBULO)?.let { z ->
            val n = Mafia.ok(s.at(5), z, key("c1", Act.CHOOSE))
            assertEquals(s.center[1], n.current.getValue(z)); assertEquals(s.current.getValue(z), n.center[1])
        }
    }

    @Test fun mafiososSeeEachOtherAndTheInformanteSeesThemAll() {
        var found = false
        for (seed in 1..40) {
            val s = mafia(8, seed)
            val mafs = s.original.filterValues { it == MRole.MAFIOSO }.keys
            val inf = s.holder(MRole.INFORMANTE)
            if (mafs.size < 2 || inf == null) continue
            found = true
            val night = Mafia.host(s, HostCmd.CONTINUE, 0)  // enters step 0
            mafs.forEach { m -> assertTrue(night.notes.getValue(m).contains("comparsas")) }
            val step1 = Mafia.host(night, HostCmd.CONTINUE, 0)
            mafs.forEach { assertTrue(step1.notes.getValue(inf).contains(s.name(it))) }
            break
        }
        assertTrue(found)
    }

    @Test fun theTownWinsByEliminatingAMafiosoOtherwiseTheMafiaWins() {
        val s = mafia(6, seed = 9).copy(stage = MStage.VOTE)
        val maf = s.current.entries.firstOrNull { it.value == MRole.MAFIOSO }?.key ?: return
        var vote = s.copy(votes = Votes(s.players.map { it.id }.toSet()))
        s.players.forEach { v -> vote = Mafia.ok(vote, v.id, pick(if (v.id == maf) s.players.first { it.id != v.id }.id else maf)) }
        assertEquals(true, vote.townWon)
        val innocent = s.players.first { s.current[it.id] != MRole.MAFIOSO }.id
        var wrong = s.copy(votes = Votes(s.players.map { it.id }.toSet()))
        s.players.forEach { v -> wrong = Mafia.ok(wrong, v.id, pick(if (v.id == innocent) s.players.first { it.id != v.id }.id else innocent)) }
        assertEquals(false, wrong.townWon)
        assertTrue(Mafia.over(wrong)!!.chips.keys.all { s.current[it] == MRole.MAFIOSO })
    }

    @Test fun withNoMafiosoInPlayEveryoneWins() {
        val s = mafia(6).let { st -> st.copy(current = st.current.mapValues { if (it.value == MRole.MAFIOSO) MRole.CIDADAO else it.value }, stage = MStage.VOTE, votes = Votes(st.players.map { it.id }.toSet())) }
        var v = s
        s.players.forEach { p -> v = Mafia.ok(v, p.id, pick(s.players.first { it.id != p.id }.id)) }
        assertEquals(true, v.townWon)
        assertTrue(Mafia.over(v)!!.headline.contains("todo mundo ganhou"))
    }

    // --- Tribunal -------------------------------------------------------------------------------------

    private val caseRow = listOf("O caso|a1,a2,a3,a4,a5,a6|b1,b2,b3,b4,b5,b6|c1,c2,c3,c4,c5,c6")

    @Test fun afterThreeProofsExactlyOneSuspectFitsInEveryCase() {
        for (n in 4..8) for (seed in 1..300) {
            val s = Tribunal.setup(setup(n, caseRow, seed = seed))
            assertEquals("alibis are unique", n, s.alibis.values.toSet().size)
            val after = s.copy(stage = TrStage.PROOF, proof = 2)
            assertEquals("n=$n seed=$seed", listOf(s.culprit), after.suspects())
            assertEquals(3, after.proofs().size)
        }
    }

    @Test fun proofsNarrowTheFieldStepByStep() {
        val s = Tribunal.setup(setup(8, caseRow, seed = 4))
        val sizes = (0..2).map { s.copy(stage = TrStage.PROOF, proof = it).suspects().size }
        assertTrue(sizes.zipWithNext().all { (a, b) -> b <= a })
        assertTrue(s.culprit in s.copy(stage = TrStage.PROOF, proof = 0).suspects())
    }

    @Test fun everyFactIsTrueAndAboutSomeoneElse() {
        repeat(50) { seed ->
            val s = Tribunal.setup(setup(6, caseRow, seed = seed))
            s.facts.forEach { (who, list) ->
                assertEquals(2, list.size)
                list.forEach { f ->
                    val other = s.players.first { f.contains(it.name + " ") || f.contains(it.name + ":") }
                    assertNotEquals(who, other.id)
                    val a = s.alibis.getValue(other.id)
                    assertTrue(f, f.contains(a.place) || f.contains(a.thing) || f.contains(a.time))
                }
            }
        }
    }

    @Test fun theDossierOnlyTellsTheCulpritTheyAreGuilty() {
        val s = Tribunal.setup(setup(6, caseRow, seed = 2))
        s.players.forEach { p ->
            val json = SocialJson.encodeToString(PhoneView.serializer(), Tribunal.phone(s, p.id))
            assertEquals(p.id == s.culprit, json.contains("VOCÊ É O CULPADO"))
            Tribunal.secrets(s, p.id).forEach { assertFalse(json.contains(it)) }
        }
    }

    @Test fun objectionsAreLimitedAndBuyTheTableTenSeconds() {
        var s = Tribunal.setup(setup(5, caseRow))
        s.players.forEach { s = Tribunal.ok(s, it.id, key("ready")) }
        assertEquals(TrStage.PROOF, s.stage)
        val p = s.players[0].id; val before = s.deadline!!
        s = Tribunal.ok(Tribunal.ok(s, p, key("objection"), 1), p, key("objection"), 2)
        assertEquals(before + 20_000, s.deadline); assertEquals(p, s.lastObjection)
        Tribunal.rejected(s, p, key("objection"))
        assertTrue(Tribunal.phone(s, p).buttons.isEmpty())
    }

    @Test fun theVerdictPaysTheTownOrTheCulprit() {
        var s = Tribunal.setup(setup(5, caseRow, seed = 6)).copy(stage = TrStage.VERDICT)
        s = s.copy(votes = Votes(s.players.map { it.id }.toSet()))
        s.players.forEach { v -> s = Tribunal.ok(s, v.id, pick(if (v.id == s.culprit) s.players.first { it.id != v.id }.id else s.culprit)) }
        val o = Tribunal.over(s)!!
        assertTrue(o.chips.keys.none { it == s.culprit } && o.chips.size == 4)
        assertTrue(o.highlights.any { it.kind == "GUILTY_CAUGHT" })
        val free = Tribunal.setup(setup(5, caseRow, seed = 6)).copy(stage = TrStage.VERDICT)
        val innocent = free.players.first { it.id != free.culprit }.id
        var f = free.copy(votes = Votes(free.players.map { it.id }.toSet()))
        free.players.forEach { v -> f = Tribunal.ok(f, v.id, pick(if (v.id == innocent) free.players.first { it.id != v.id }.id else innocent)) }
        assertEquals(mapOf(free.culprit to 4), Tribunal.over(f)!!.chips)
    }

}
