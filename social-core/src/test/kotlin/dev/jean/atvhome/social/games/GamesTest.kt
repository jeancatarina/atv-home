package dev.jean.atvhome.social.games

import dev.jean.atvhome.social.people
import dev.jean.atvhome.social.game.*
import dev.jean.atvhome.social.games.blackstory.*
import dev.jean.atvhome.social.games.couple.*
import dev.jean.atvhome.social.games.frequency.*
import dev.jean.atvhome.social.games.insider.*
import dev.jean.atvhome.social.games.missions.*
import dev.jean.atvhome.social.games.psychologist.*
import dev.jean.atvhome.social.games.spyfall.*
import dev.jean.atvhome.social.games.twotruths.*
import dev.jean.atvhome.social.games.undercover.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** The rules that win and lose each game, driven move by move with a fixed seed. */
class GamesTest {

    private fun key(k: String, type: String = Act.BUTTON) = Action(type, buildJsonObject { put("key", JsonPrimitive(k)) })
    private fun pick(id: String) = Action(Act.PICK, buildJsonObject { put("id", JsonPrimitive(id)) })
    private fun text(v: String) = Action(Act.TEXT, buildJsonObject { put("value", JsonPrimitive(v)) })
    private fun scale(v: Int) = Action(Act.SCALE, buildJsonObject { put("value", JsonPrimitive(v)) })
    private fun <S : Any> Game<S>.ok(s: S, by: String, a: Action, now: Long = 0): S =
        (act(s, by, a, now) as? Step.Ok)?.state ?: error("rejected: ${(act(s, by, a, now) as Step.Rejected).why}")
    private fun <S : Any> Game<S>.rejected(s: S, by: String, a: Action) = assertTrue(act(s, by, a, 0) is Step.Rejected)
    private fun setup(n: Int, content: List<String>, settings: Map<String, Int> = emptyMap(), seed: Int = 3) =
        Setup(people(n), settings, Random(seed), 0, content)

    // --- Infiltrados -------------------------------------------------------------------------------

    private val pairs = listOf("Gato|Cachorro", "Café|Chá")

    private fun uSetup(n: Int, white: Int = 1) = Undercover.setup(setup(n, pairs, mapOf("white" to white)))
    private fun UndercoverState.ofRole(r: Role) = roles.filterValues { it == r }.keys.toList()

    private fun UndercoverState.toVote(): UndercoverState {
        var s = this
        if (s.stage == UStage.WORDS) s = Undercover.host(s, HostCmd.CONTINUE, 0)
        while (s.stage == UStage.CLUES) s = Undercover.host(s, HostCmd.CONTINUE, 0)
        return s
    }

    /** Everybody alive votes [target]; the target votes the first other person. */
    private fun UndercoverState.voteOut(target: String): UndercoverState {
        var s = this
        alive.forEach { v -> if (s.stage == UStage.VOTE) s = Undercover.ok(s, v, pick(if (v == target) alive.first { it != target } else target)) }
        return s
    }

    @Test fun undercoverRolesFollowThePlayerCount() {
        for (n in 4..10) {
            val s = uSetup(n)
            val inf = s.ofRole(Role.INFILTRADO).size
            assertEquals("n=$n", when { n <= 6 -> 1; n <= 8 -> 2; else -> 3 }, inf)
            assertEquals(if (n >= 5) 1 else 0, s.ofRole(Role.BRANCO).size)
            assertEquals(n, s.roles.size)
        }
        assertEquals(0, uSetup(6, white = 0).ofRole(Role.BRANCO).size)
    }

    @Test fun theSrBrancoNeverSpeaksFirst() {
        repeat(20) { seed ->
            val s = Undercover.setup(setup(6, pairs, seed = seed)).let { Undercover.host(it, HostCmd.CONTINUE, 0) }
            assertNotEquals(Role.BRANCO, s.roles[s.speakerId])
        }
    }

    @Test fun civiliansWinByVotingOutEveryoneOnTheOtherSide() {
        var s = uSetup(5).toVote()
        s = s.voteOut(s.ofRole(Role.INFILTRADO).single())
        assertEquals(UStage.ELIM, s.stage)
        s = Undercover.host(s, HostCmd.CONTINUE, 0)
        assertEquals("a Branco is still in: next round", UStage.CLUES, s.stage)
        s = s.toVote().voteOut(s.ofRole(Role.BRANCO).single())
        s = Undercover.host(s, HostCmd.CONTINUE, 0)
        assertEquals(UStage.GUESS, s.stage)
        s = Undercover.ok(s, s.ofRole(Role.BRANCO).single(), text("errado"))
        assertEquals(Side.CIVIS, s.winner)
        val o = Undercover.over(s)!!
        assertEquals(3, s.roles.filterValues { it == Role.CIVIL }.keys.count { o.chips[it] == 3 })
    }

    @Test fun aSrBrancoWhoGuessesTheCivilWordWinsAlone() {
        var s = uSetup(5).toVote()
        val white = s.ofRole(Role.BRANCO).single()
        s = Undercover.host(s.voteOut(white), HostCmd.CONTINUE, 0)
        assertEquals(UStage.GUESS, s.stage)
        Undercover.rejected(s, s.ofRole(Role.CIVIL).first(), text(s.civilWord))
        s = Undercover.ok(s, white, text("  " + s.civilWord.uppercase() + "! "))
        assertEquals(Side.BRANCO, s.winner)
        assertEquals(mapOf(white to 5), Undercover.over(s)!!.chips)
    }

    @Test fun infiltratorsWinWhenTheyReachParity() {
        var s = uSetup(4, white = 0).toVote()
        val civils = s.ofRole(Role.CIVIL)
        s = Undercover.host(s.voteOut(civils[0]), HostCmd.CONTINUE, 0)
        assertEquals("2 civils vs 1 infiltrator: goes on", UStage.CLUES, s.stage)
        s = Undercover.host(s.toVote().voteOut(civils[1]), HostCmd.CONTINUE, 0)
        assertEquals(Side.INFILTRADOS, s.winner)
    }

    @Test fun aTiedVoteGoesToARevoteAndASecondTieEliminatesNobody() {
        var s = uSetup(4, white = 0).toVote()
        val (a, b, c, d) = s.alive
        listOf(a to b, b to a, c to a, d to b).forEach { (v, t) -> s = Undercover.ok(s, v, pick(t)) }
        assertEquals(UStage.VOTE, s.stage)
        assertTrue(s.revote)
        assertEquals(setOf(a, b), s.candidates.toSet())
        listOf(a to b, b to a, c to a, d to b).forEach { (v, t) -> s = Undercover.ok(s, v, pick(t)) }
        assertEquals(UStage.ELIM, s.stage)
        assertNull(s.eliminated)
        assertEquals(4, s.alive.size)
    }

    @Test fun anUndercoverPlayerSeesOnlyTheirOwnWord() {
        val s = uSetup(5)
        s.players.forEach { p ->
            val json = SocialJson.encodeToString(PhoneView.serializer(), Undercover.phone(s, p.id))
            Undercover.secrets(s, p.id).forEach { assertFalse(json.contains(it)) }
        }
        val civil = s.ofRole(Role.CIVIL).first()
        assertTrue(SocialJson.encodeToString(PhoneView.serializer(), Undercover.phone(s, civil)).contains(s.civilWord))
    }

    // --- Frequência ----------------------------------------------------------------------------------

    private val spectrums = (1..12).map { "A$it|B$it" }

    @Test fun frequencyScoresByHowCloseTheDialIs() {
        for ((dist, gain) in listOf(0 to 4, 4 to 4, 5 to 3, 9 to 3, 10 to 2, 15 to 2, 16 to 0)) {
            var s = Frequency.setup(setup(3, spectrums)).copy(stage = FStage.DIAL, target = 50, dial = 50 + dist)
            s = Frequency.host(s, HostCmd.CONTINUE, 0)
            assertEquals("distance $dist", gain, s.lastGain)
            assertEquals(gain, s.points[0])
        }
    }

    @Test fun theDialMovesWithTheRemoteAndStaysInRange() {
        var s = Frequency.setup(setup(3, spectrums)).copy(stage = FStage.DIAL, dial = 98)
        s = Frequency.host(Frequency.host(s, HostCmd.RIGHT, 0), HostCmd.RIGHT, 0)
        assertEquals(100, s.dial)
        s = s.copy(dial = 1)
        assertEquals(0, Frequency.host(Frequency.host(s, HostCmd.LEFT, 0), HostCmd.LEFT, 0).dial)
    }

    @Test fun onlyThePsychicSeesTheTargetAndTeamsBetOnTheOtherSide() {
        val s = Frequency.setup(setup(6, spectrums))
        assertFalse(s.coop)
        s.players.forEach { p ->
            val v = SocialJson.encodeToString(PhoneView.serializer(), Frequency.phone(s, p.id))
            assertEquals(p.id == s.psychic, v.contains("${s.target}%"))
        }
        var d = Frequency.ok(s, s.psychic, key("told")).copy(target = 70, dial = 40)
        d = Frequency.host(d, HostCmd.CONTINUE, 0)
        assertEquals(FStage.BET, d.stage)
        val dialers = d.players.filter { d.team[it.id] == d.dialTeam }
        Frequency.rejected(d, dialers.first().id, key("1", Act.CHOOSE))
        val bettor = d.players.first { d.team[it.id] != d.dialTeam }
        val r = Frequency.ok(d, bettor.id, key("1", Act.CHOOSE))
        assertEquals(FStage.REVEAL, r.stage)
        assertEquals(true, r.betRight)
        assertEquals(1, r.points[1 - d.dialTeam])
    }

    @Test fun frequencyEndsAfterTheLastCard() {
        var s = Frequency.setup(setup(3, spectrums, mapOf("cards" to 0)))
        repeat(5) { s = Frequency.host(Frequency.host(Frequency.host(s, HostCmd.CONTINUE, 0), HostCmd.CONTINUE, 0), HostCmd.CONTINUE, 0) }
        assertEquals(FStage.DONE, s.stage)
        assertNotNull(Frequency.over(s))
    }

    // --- Sintonia de Casal ----------------------------------------------------------------------------

    private val coupleQs = listOf("C|Qual o jantar de {nome}?|Italiano|Japonês", "S|Quanto {nome} gosta de samba?|Nada|Muito")

    @Test fun coupleScoresMatchesAndScaleCloseness() {
        var s = Couple.setup(setup(2, coupleQs, mapOf("rounds" to 0))).copy(questions = listOf(Question.parse(coupleQs[0])!!, Question.parse(coupleQs[1])!!, Question.parse(coupleQs[0])!!))
        s = Couple.ok(Couple.ok(s, "p1", key("0", Act.CHOOSE)), "p2", key("0", Act.CHOOSE))
        assertEquals(CStage.REVEAL, s.stage); assertEquals(3, s.points)
        s = Couple.host(s, HostCmd.CONTINUE, 0)
        s = Couple.ok(Couple.ok(s, "p1", scale(7)), "p2", scale(9))
        assertEquals("diff 2 scores 1", 4, s.points)
        s = Couple.host(s, HostCmd.CONTINUE, 0)
        s = Couple.ok(Couple.ok(s, "p1", key("1", Act.CHOOSE)), "p2", key("0", Act.CHOOSE))
        assertEquals("no match", 4, s.points)
        assertEquals(CStage.REVEAL, s.stage)
    }

    @Test fun theLastCoupleQuestionCountsDoubleAndTheTitleFollowsThePercentage() {
        var s = Couple.setup(setup(2, coupleQs)).copy(questions = listOf(Question.parse(coupleQs[0])!!))
        s = Couple.ok(Couple.ok(s, "p1", key("1", Act.CHOOSE)), "p2", key("1", Act.CHOOSE))
        assertEquals(6, s.points)
        s = Couple.host(s, HostCmd.CONTINUE, 0)
        assertEquals(CStage.DONE, s.stage)
        assertTrue(Couple.over(s)!!.headline.startsWith("Sintonia 100%"))
        Couple.rejected(s, "p1", key("0", Act.CHOOSE))
    }

    @Test fun theSubjectAlternatesAndNamesAppearInTheQuestion() {
        val s = Couple.setup(setup(2, coupleQs, mapOf("rounds" to 0)))
        assertTrue(s.text.contains(s.subject.name))
        val n = Couple.host(Couple.host(s, HostCmd.CONTINUE, 0), HostCmd.CONTINUE, 0)
        assertNotEquals(s.subject.id, n.subject.id)
    }

    // --- Duas Verdades --------------------------------------------------------------------------------

    @Test fun twoTruthsScoresRightGuessesAndFooledPeople() {
        var s = TwoTruths.setup(setup(4, listOf("Viagens", "Comida", "Medo", "Escola")))
        val teller = s.teller.id
        val others = s.players.map { it.id } - teller
        TwoTruths.rejected(s, others[0], key("0", Act.CHOOSE))
        s = TwoTruths.ok(s, teller, key("1", Act.CHOOSE))
        s = TwoTruths.ok(s, teller, key("go"))
        assertEquals(TStage.TELL, s.stage)
        s = TwoTruths.ok(s, teller, key("vote"))
        assertEquals(TStage.VOTE, s.stage)
        TwoTruths.rejected(s, teller, key("1", Act.CHOOSE))
        s = TwoTruths.ok(s, others[0], key("1", Act.CHOOSE))
        s = TwoTruths.ok(s, others[1], key("0", Act.CHOOSE))
        s = TwoTruths.ok(s, others[2], key("2", Act.CHOOSE))
        assertEquals(TStage.REVEAL, s.stage)
        assertEquals(1, s.scores[others[0]]); assertNull(s.scores[others[1]])
        assertEquals("fooled two people", 2, s.scores[teller])
        assertEquals(2, s.fooled[teller])
    }

    @Test fun anotherTellerTakesOverAndEveryoneTellsOnce() {
        var s = TwoTruths.setup(setup(3, listOf("A", "B", "C")))
        val seen = mutableSetOf<String>()
        while (s.stage != TStage.DONE) {
            seen += s.teller.id
            s = TwoTruths.ok(s, s.teller.id, key("0", Act.CHOOSE))
            repeat(4) { s = TwoTruths.host(s, HostCmd.CONTINUE, 0) }
        }
        assertEquals(3, seen.size)
    }

    @Test fun anAbsentTellerWhoNeverMarksALieIsSkipped() {
        val s = TwoTruths.setup(setup(3, listOf("A", "B", "C")))
        assertEquals(1, TwoTruths.timeout(s, 60_000).turn)
    }

    @Test fun onlyTheTellerKnowsWhichOneIsTheLie() {
        var s = TwoTruths.setup(setup(4, listOf("A")))
        s = TwoTruths.ok(s, s.teller.id, key("2", Act.CHOOSE))
        s.players.filter { it.id != s.teller.id }.forEach { p ->
            TwoTruths.secrets(s, p.id).forEach { assertFalse(SocialJson.encodeToString(PhoneView.serializer(), TwoTruths.phone(s, p.id)).contains(it)) }
        }
        assertTrue(SocialJson.encodeToString(PhoneView.serializer(), TwoTruths.phone(s, s.teller.id)).isNotEmpty())
    }

    // --- Lugar Secreto --------------------------------------------------------------------------------

    private val places = listOf("Padaria|Padeiro|Caixa|Cliente|Entregador", "Praia|Salva-vidas|Turista|Surfista|Vendedor", "Circo|Palhaço|Mágico|Domador|Plateia")

    private fun spyTalk(n: Int = 5): SpyState {
        var s = Spyfall.setup(setup(n, places))
        s.players.forEach { s = Spyfall.ok(s, it.id, key("ready")) }
        assertEquals(SStage.TALK, s.stage)
        return s
    }

    @Test fun everyoneButTheSpyGetsThePlaceAndARole() {
        val s = Spyfall.setup(setup(6, places))
        assertEquals(5, s.roles.size)
        assertFalse(s.spy in s.roles)
        assertEquals(3, s.places.size)
        val spyView = SocialJson.encodeToString(PhoneView.serializer(), Spyfall.phone(s, s.spy))
        assertFalse(spyView.contains(s.location))
    }

    @Test fun aUnanimousAccusationOfTheSpyWinsForTheCivilians() {
        var s = spyTalk()
        val accuser = s.players.first { it.id != s.spy }.id
        s = Spyfall.ok(s, accuser, key("accuse"))
        s = Spyfall.ok(s, accuser, pick(s.spy))
        assertEquals(SStage.VOTE_ACCUSATION, s.stage)
        s.yes.voters.forEach { s = Spyfall.ok(s, it, key("1", Act.CHOOSE)) }
        assertEquals(SWinner.CIVIS, s.winner)
    }

    @Test fun aSingleNoSendsTheTableBackToTalkingWithTheTimeThatWasLeft() {
        var s = spyTalk()
        val deadlineBefore = s.deadline!!
        val accuser = s.players.first { it.id != s.spy }.id
        val target = s.players.first { it.id != accuser && it.id != s.spy }.id
        s = Spyfall.ok(s, accuser, key("accuse"), now = 100_000)
        s = Spyfall.ok(s, accuser, pick(target), now = 101_000)
        val voters = s.yes.voters.toList()
        voters.dropLast(1).forEach { s = Spyfall.ok(s, it, key("1", Act.CHOOSE), now = 102_000) }
        s = Spyfall.ok(s, voters.last(), key("0", Act.CHOOSE), now = 103_000)
        assertEquals(SStage.TALK, s.stage)
        assertEquals("the pause did not eat the talk clock", deadlineBefore - 100_000 + 103_000, s.deadline)
        Spyfall.rejected(s, accuser, key("accuse"))
    }

    @Test fun accusingAnInnocentLosesForTheCiviliansAndTheSpyCanGuessThePlace() {
        var s = spyTalk()
        val accuser = s.players.first { it.id != s.spy }.id
        val target = s.players.first { it.id != accuser && it.id != s.spy }.id
        s = Spyfall.ok(Spyfall.ok(s, accuser, key("accuse")), accuser, pick(target))
        s.yes.voters.forEach { s = Spyfall.ok(s, it, key("1", Act.CHOOSE)) }
        assertEquals(SWinner.ESPIAO, s.winner)

        var g = spyTalk()
        Spyfall.rejected(g, g.players.first { it.id != g.spy }.id, key("guess"))
        g = Spyfall.ok(g, g.spy, key("guess"))
        Spyfall.rejected(g, g.spy, key("99", Act.CHOOSE))
        val right = g.places.indexOf(g.location)
        assertEquals(SWinner.ESPIAO, Spyfall.ok(g, g.spy, key("$right", Act.CHOOSE)).winner)
        val wrong = (right + 1) % g.places.size
        assertEquals(SWinner.CIVIS, Spyfall.ok(g, g.spy, key("$wrong", Act.CHOOSE)).winner)
    }

    @Test fun whenTimeRunsOutTheRoomVotesAndASpyWhoIsNotTheTopSuspectEscapes() {
        var s = Spyfall.timeout(spyTalk(), 999_999_999)
        assertEquals(SStage.FINAL, s.stage)
        s.players.forEach { v -> s = Spyfall.ok(s, v.id, pick(s.players.first { it.id != v.id && it.id != s.spy }.id.takeIf { v.id != s.spy } ?: s.players.first { it.id != v.id }.id)) }
        assertEquals(SWinner.ESPIAO, s.winner)
        var t = Spyfall.timeout(spyTalk(), 999_999_999)
        t.players.forEach { v -> t = Spyfall.ok(t, v.id, pick(if (v.id == t.spy) t.players.first { it.id != v.id }.id else t.spy)) }
        assertEquals(SWinner.CIVIS, t.winner)
    }

    // --- Quem Sabia? -----------------------------------------------------------------------------------

    private fun iPlay(n: Int = 5): InsiderState {
        var s = Insider.setup(setup(n, listOf("Girafa", "Elevador")))
        s.players.forEach { s = Insider.ok(s, it.id, key("ready")) }
        assertEquals(IStage.PLAY, s.stage)
        return s
    }

    @Test fun onlyTheMasterAnswersAndTheAnswersArePublic() {
        var s = iPlay()
        Insider.rejected(s, s.insider, key("yes"))
        s = Insider.ok(Insider.ok(s, s.master, key("yes")), s.master, key("unsure"))
        assertEquals(listOf("yes", "unsure"), s.history)
        Insider.rejected(s, s.master, key("maybe"))
    }

    @Test fun catchingTheInsiderPaysEveryoneElse() {
        var s = Insider.ok(iPlay(), iPlay().master, key("found")).let { it }
        s = Insider.host(iPlay(), HostCmd.CONTINUE, 0)
        assertEquals(IStage.VOTE, s.stage)
        assertFalse(s.master in s.votes.voters)
        s.votes.voters.forEach { v -> s = Insider.ok(s, v, pick(if (v == s.insider) s.votes.voters.first { it != v } else s.insider)) }
        assertEquals(true, s.caught)
        val chips = Insider.over(s)!!.chips
        assertNull(chips[s.insider]); assertEquals(3, chips[s.master])
    }

    @Test fun anInsiderWhoSlipsAwayWinsAndAWordNobodyFoundPaysNothing() {
        var s = Insider.host(iPlay(), HostCmd.CONTINUE, 0)
        val innocent = s.votes.voters.first { it != s.insider }
        s.votes.voters.forEach { v -> s = Insider.ok(s, v, pick(if (v == innocent) s.votes.voters.first { it != v } else innocent)) }
        assertEquals(false, s.caught)
        assertEquals(mapOf(s.insider to 4), Insider.over(s)!!.chips)
        val lost = Insider.timeout(iPlay(), 999_999_999)
        assertEquals(IStage.DONE, lost.stage)
        assertTrue(Insider.over(lost)!!.chips.isEmpty())
    }

    @Test fun nobodyButTheMasterAndTheInsiderCanSeeTheWord() {
        val s = Insider.setup(setup(6, listOf("Girafa")))
        s.players.forEach { p ->
            val json = SocialJson.encodeToString(PhoneView.serializer(), Insider.phone(s, p.id))
            assertEquals(p.id == s.master || p.id == s.insider, json.contains("Girafa"))
        }
    }

    // --- Psicólogo -------------------------------------------------------------------------------------

    private fun pAsk(n: Int = 5): PsychologistState {
        var s = Psychologist.setup(setup(n, listOf("Responda sempre com uma pergunta|ex um|ex dois")))
        s.others.forEach { s = Psychologist.ok(s, it, key("ready")) }
        assertEquals(PStage.ASK, s.stage)
        return s
    }

    @Test fun aMajorityOfYesWinsAndPaysThePsychologistMost() {
        var s = Psychologist.ok(pAsk(), pAsk().psychologist, key("guess"))
        assertEquals(PStage.JUDGE, s.stage)
        s.others.forEachIndexed { i, o -> s = Psychologist.ok(s, o, key(if (i < 3) "1" else "0", Act.CHOOSE)) }
        assertEquals(true, s.won)
        assertEquals(3, Psychologist.over(s)!!.chips[s.psychologist])
    }

    @Test fun threeWrongGuessesLoseAndEachOneCostsALife() {
        var s = pAsk()
        for (life in 3 downTo 1) {
            assertEquals(life, s.lives)
            s = Psychologist.ok(s, s.psychologist, key("guess"))
            s.others.forEach { s = Psychologist.ok(s, it, key("0", Act.CHOOSE)) }
        }
        assertEquals(PStage.DONE, s.stage)
        assertEquals(false, s.won)
        assertTrue("a loss reveals the rule", Psychologist.over(s)!!.headline.contains(s.rule))
    }

    @Test fun thePsychologistNeverSeesTheRule() {
        val s = Psychologist.setup(setup(5, listOf("Regra secreta única|um exemplo|outro exemplo")))
        val json = SocialJson.encodeToString(PhoneView.serializer(), Psychologist.phone(s, s.psychologist))
        Psychologist.secrets(s, s.psychologist).forEach { assertFalse(json.contains(it)) }
        assertTrue(Psychologist.secrets(s, s.psychologist).contains("Regra secreta única"))
    }

    // --- Mistério ---------------------------------------------------------------------------------------

    private val mystery = listOf("Enigma estranho|Solução escondida|dica um|dica dois")

    @Test fun theGuardianAnswersHintsAndResolves() {
        var s = BlackStory.setup(setup(3, mystery))
        BlackStory.rejected(s, s.players.first { it.id != s.guardian }.id, key("yes"))
        s = BlackStory.ok(BlackStory.ok(s, s.guardian, key("almost")), s.guardian, key("hint"))
        assertEquals(1, s.hintsShown); assertEquals(listOf("almost"), s.history)
        s = BlackStory.ok(BlackStory.ok(s, s.guardian, key("hint")), s.guardian, key("solved"))
        BlackStory.rejected(s, s.guardian, key("hint"))
        s = BlackStory.host(s, HostCmd.CONTINUE, 0)
        assertEquals(BStage.DONE, s.stage)
        val o = BlackStory.over(s)!!
        assertEquals(3, o.chips[s.players.first { it.id != s.guardian }.id])
        assertEquals(2, o.chips[s.guardian])
    }

    @Test fun anUnsolvedMysteryPaysTheGuardianAndHidesTheSolutionFromTheOthers() {
        var s = BlackStory.setup(setup(3, mystery))
        val asker = s.players.first { it.id != s.guardian }.id
        BlackStory.secrets(s, asker).forEach { assertFalse(SocialJson.encodeToString(PhoneView.serializer(), BlackStory.phone(s, asker)).contains(it)) }
        s = BlackStory.host(BlackStory.timeout(s, 999_999_999), HostCmd.CONTINUE, 0)
        assertEquals(mapOf(s.guardian to 3), BlackStory.over(s)!!.chips)
    }

    // --- Missões ----------------------------------------------------------------------------------------

    private val missionPack = listOf("alfa","bravo","charlie","delta","eco","foxtrote","golfe","hotel","india","julieta","quilo","lima","mike","novembro","oscar","papa","quebec","romeu","sierra","tango").mapIndexed { i, w -> "Faça alguém dizer $w|${1 + i % 3}" }

    private fun mSetup(n: Int = 3) = Missions.setup(setup(n, missionPack))

    @Test fun completingAMissionScoresItsLevelAndDealsANewOne() {
        var s = mSetup()
        val me = "p1"
        val m = s.hands.getValue(me)[1]
        Missions.rejected(s, me, key("done"))
        s = Missions.ok(Missions.ok(s, me, key("m1", Act.CHOOSE)), me, key("done"))
        assertEquals(m.level, s.points[me])
        assertEquals(3, s.hands.getValue(me).size)
        assertEquals(m.level, s.lastDone[me])
        assertEquals("done", s.feed.last().kind)
    }

    @Test fun anAccusationTheTargetConfirmsBurnsTheirMissionAndPaysTheAccuser() {
        var s = mSetup()
        s = Missions.ok(Missions.ok(s, "p2", key("m0", Act.CHOOSE)), "p2", key("done"))
        val had = s.points.getValue("p2")
        s = Missions.ok(s, "p1", key("accuse"))
        assertTrue("p1" in s.picking)
        s = Missions.ok(s, "p1", pick("p2"))
        assertEquals("p1", s.accusations["p2"])
        val view = SocialJson.encodeToString(PhoneView.serializer(), Missions.phone(s, "p2"))
        assertTrue(view.contains("\"w\":\"choose\""))
        s = Missions.ok(s, "p2", key("1", Act.CHOOSE))
        assertEquals(0, s.points.getValue("p2")); assertTrue(had > 0)
        assertEquals(1, s.points["p1"])
    }

    @Test fun aDeniedAccusationChangesNothingAndTheCooldownBlocksSpamming() {
        var s = mSetup()
        s = Missions.ok(Missions.ok(s, "p1", key("accuse"), now = 0), "p1", pick("p2"), now = 0)
        s = Missions.ok(s, "p2", key("0", Act.CHOOSE), now = 1)
        assertTrue(s.accusations.isEmpty())
        Missions.rejected(s, "p1", key("accuse"))
        assertTrue(Missions.act(s, "p1", key("accuse"), 200_000) is Step.Ok)
    }

    @Test fun missionsOnlyEverShowTheirOwnersTheirText() {
        val s = mSetup(4)
        s.players.forEach { p ->
            val json = SocialJson.encodeToString(PhoneView.serializer(), Missions.phone(s, p.id))
            Missions.secrets(s, p.id).forEach { assertFalse(json.contains(it)) }
        }
    }

    @Test fun theGameEndsOnTimeWithTheBestScorerNamed() {
        var s = mSetup()
        s = Missions.ok(Missions.ok(s, "p3", key("m2", Act.CHOOSE)), "p3", key("done"))
        s = Missions.timeout(s, Long.MAX_VALUE / 2)
        val o = Missions.over(s)!!
        assertTrue(o.headline.contains("Pessoa 3"))
        assertEquals(setOf("p3"), o.chips.keys)
    }
}
