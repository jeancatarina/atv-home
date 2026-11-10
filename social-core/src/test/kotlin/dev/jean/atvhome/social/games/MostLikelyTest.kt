package dev.jean.atvhome.social.games

import dev.jean.atvhome.social.QUESTIONS
import dev.jean.atvhome.social.people
import dev.jean.atvhome.social.game.Act
import dev.jean.atvhome.social.game.Action
import dev.jean.atvhome.social.game.HostCmd
import dev.jean.atvhome.social.game.Setup
import dev.jean.atvhome.social.game.SocialJson
import dev.jean.atvhome.social.game.Step
import dev.jean.atvhome.social.game.Widget
import dev.jean.atvhome.social.games.mostlikely.MostLikely
import dev.jean.atvhome.social.games.mostlikely.MostLikelyState
import dev.jean.atvhome.social.games.mostlikely.Stage
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MostLikelyTest {

    private val t0 = 0L

    private fun start(n: Int = 4, rounds: Int = 1): MostLikelyState =
        MostLikely.setup(Setup(people(n), mapOf("rounds" to rounds), Random(1), t0, QUESTIONS))

    private fun pick(id: String) = Action(Act.PICK, buildJsonObject { put("id", JsonPrimitive(id)) })

    private fun MostLikelyState.vote(by: String, target: String, now: Long = 0): MostLikelyState =
        (MostLikely.act(this, by, pick(target), now) as Step.Ok).state

    @Test
    fun setupDrawsTheChosenNumberOfQuestions() {
        assertEquals(5, start(rounds = 0).questions.size)
        assertEquals(10, start(rounds = 1).questions.size)
        assertEquals(15, start(rounds = 2).questions.size)
        assertEquals(Stage.VOTE, start().stage)
    }

    @Test
    fun revealsAsSoonAsEveryoneHasVoted() {
        var s = start()
        s = s.vote("p1", "p2").vote("p2", "p2").vote("p3", "p2")
        assertEquals(Stage.VOTE, s.stage)
        s = s.vote("p4", "p1")
        assertEquals(Stage.REVEAL, s.stage)
        assertEquals(listOf("p2"), s.reveal!!.leaders)
        assertEquals(3, s.reveal!!.tally["p2"])
    }

    @Test
    fun votingWithTheMajorityScoresOnePoint() {
        val s = start().vote("p1", "p2").vote("p2", "p2").vote("p3", "p2").vote("p4", "p1")
        assertEquals(mapOf("p1" to 1, "p2" to 1, "p3" to 1), s.scores)
    }

    @Test
    fun aTieMakesEveryLeaderAWinnerAndTheirVotersScore() {
        val s = start().vote("p1", "p2").vote("p2", "p1").vote("p3", "p2").vote("p4", "p1")
        assertEquals(setOf("p1", "p2"), s.reveal!!.leaders.toSet())
        assertEquals(setOf("p1", "p2", "p3", "p4"), s.scores.keys)
    }

    @Test
    fun everyoneVotingForTheSamePersonIsUnanimous() {
        val s = start().vote("p1", "p3").vote("p2", "p3").vote("p3", "p3").vote("p4", "p3")
        assertEquals("p3", s.reveal!!.unanimous)
        assertEquals("UNANIMOUS", s.unanimous.single().kind)
        assertEquals("p3", s.unanimous.single().who)
    }

    @Test
    fun aVoteCanBeChangedUntilTheReveal() {
        var s = start().vote("p1", "p2").vote("p1", "p3")
        assertEquals("p3", s.votes.cast["p1"])
        s = s.vote("p2", "p3").vote("p3", "p3").vote("p4", "p3")
        assertEquals("p3", s.reveal!!.unanimous)
    }

    @Test
    fun theVotingDeadlineRevealsWithWhoeverVoted() {
        var s = start().vote("p1", "p2").vote("p2", "p2")
        assertEquals(20_000L, MostLikely.deadline(s))
        s = MostLikely.timeout(s, 20_000)
        assertEquals(Stage.REVEAL, s.stage)
        assertEquals(listOf("p2"), s.reveal!!.leaders)
    }

    @Test
    fun nobodyVotingRevealsWithNoLeader() {
        val s = MostLikely.timeout(start(), 20_000)
        assertEquals(Stage.REVEAL, s.stage)
        assertTrue(s.reveal!!.leaders.isEmpty())
        assertTrue(s.scores.isEmpty())
    }

    @Test
    fun theRevealMovesOnAfterItsDeadlineOrWhenTheHostContinues() {
        val revealed = MostLikely.timeout(start(rounds = 0), 20_000)
        assertEquals(29_000L, MostLikely.deadline(revealed))
        assertEquals(1, MostLikely.timeout(revealed, 29_000).round)
        assertEquals(Stage.VOTE, MostLikely.host(revealed, HostCmd.CONTINUE, 21_000).stage)
    }

    @Test
    fun theLastTwoQuestionsCountDouble() {
        var s = start(rounds = 0) // 5 questions: rounds 3 and 4 are double
        repeat(3) { s = MostLikely.host(MostLikely.host(s, HostCmd.CONTINUE, 0), HostCmd.CONTINUE, 0) }
        assertEquals(3, s.round)
        assertTrue(s.double)
        s = s.vote("p1", "p2").vote("p2", "p2").vote("p3", "p2").vote("p4", "p2")
        assertEquals(2, s.scores["p1"])
    }

    @Test
    fun theGameEndsAfterTheLastQuestionAndPaysTheMostPointedAt() {
        var s = start(rounds = 0)
        repeat(5) {
            assertNull(MostLikely.over(s))
            s = s.vote("p1", "p2").vote("p2", "p2").vote("p3", "p2").vote("p4", "p2")
            s = MostLikely.host(s, HostCmd.CONTINUE, 0)
        }
        assertEquals(Stage.DONE, s.stage)
        val outcome = MostLikely.over(s)!!
        assertNotNull(outcome)
        // 1 point for 3 normal + 2 double rounds = 3 + 4 = 7, plus 3 for the most pointed at (p2 himself voted p2)
        assertEquals(7 + 3, outcome.chips["p2"])
        assertEquals(7, outcome.chips["p1"])
        assertTrue(outcome.headline.contains("Pessoa 2"))
        assertEquals(5, outcome.highlights.count { it.kind == "UNANIMOUS" })
        assertEquals("MOST_VOTED", outcome.highlights.last().kind)
    }

    @Test
    fun rejectsVotesOutsideTheVotingPhaseOrForStrangers() {
        val s = start()
        assertTrue(MostLikely.act(s, "p1", pick("nope"), 0) is Step.Rejected)
        assertTrue(MostLikely.act(s, "ghost", pick("p1"), 0) is Step.Rejected)
        assertTrue(MostLikely.act(s, "p1", Action("zzz"), 0) is Step.Rejected)
        assertTrue(MostLikely.act(s, "p1", Action(Act.PICK), 0) is Step.Rejected)
        val revealed = MostLikely.timeout(s, 20_000)
        assertTrue(MostLikely.act(revealed, "p1", pick("p2"), 0) is Step.Rejected)
    }

    @Test
    fun aPhoneSeesOnlyItsOwnVote() {
        val s = start().vote("p1", "p2")
        val mine = MostLikely.phone(s, "p1").body as Widget.PickPlayer
        val theirs = MostLikely.phone(s, "p2").body as Widget.PickPlayer
        assertEquals("p2", mine.chosen)
        assertNull(theirs.chosen)
        assertFalse(SocialJson.encodeToString(MostLikely.phone(s, "p3")).contains("\"chosen\""))
    }

    @Test
    fun whenTheLastPendingVoterLeavesTheQuestionIsRevealed() {
        val s = start().vote("p1", "p2").vote("p2", "p2").vote("p3", "p2")
        val after = MostLikely.left(s, "p4", 5_000)
        assertEquals(Stage.REVEAL, after.stage)
        assertEquals(3, after.players.size)
    }

    @Test
    fun withFewerThanTwoPeopleLeftTheGameEnds() {
        var s = start(n = 3)
        s = MostLikely.left(s, "p1", 0)
        assertEquals(Stage.VOTE, s.stage)
        s = MostLikely.left(s, "p2", 0)
        assertEquals(Stage.DONE, s.stage)
        assertNotNull(MostLikely.over(s))
    }

    @Test
    fun anEmptyContentPackStillMakesAPlayableGame() {
        val s = MostLikely.setup(Setup(people(3), emptyMap(), Random(1), 0, emptyList()))
        assertEquals(1, s.questions.size)
    }

    @Test
    fun phaseNamesChangeWithEveryQuestionSoLateTapsAreDropped() {
        val a = start(rounds = 0)
        val b = MostLikely.host(MostLikely.host(a, HostCmd.CONTINUE, 0), HostCmd.CONTINUE, 0)
        assertTrue(MostLikely.phase(a) != MostLikely.phase(b))
    }
}
