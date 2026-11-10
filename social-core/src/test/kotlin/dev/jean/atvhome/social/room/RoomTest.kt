package dev.jean.atvhome.social.room

import dev.jean.atvhome.social.FakeClock
import dev.jean.atvhome.social.QUESTIONS
import dev.jean.atvhome.social.game.Act
import dev.jean.atvhome.social.game.Action
import dev.jean.atvhome.social.game.HostCmd
import dev.jean.atvhome.social.game.SocialJson
import dev.jean.atvhome.social.games.mostlikely.MostLikely
import dev.jean.atvhome.social.games.mostlikely.MostLikelyState
import dev.jean.atvhome.social.games.mostlikely.Stage
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class RoomTest {

    private val clock = FakeClock()
    private fun room(store: SeatStore? = null) = Room(clock = clock, content = { QUESTIONS }, store = store, random = Random(5))
    private fun Room.enter(name: String, seat: String? = null) = join(key, name, seat) as JoinResult.Joined
    private fun pick(id: String) = Action(Act.PICK, buildJsonObject { put("id", JsonPrimitive(id)) })
    private fun Room.game() = snapshot.value.game!!.state as MostLikelyState
    private fun Room.seatsOf(n: Int) = (1..n).map { enter("P$it") }

    // --- Entering and leaving -------------------------------------------------------------------

    @Test
    fun aWrongKeyCannotJoin() {
        val r = room()
        assertEquals(JoinResult.BadKey, r.join("wrong", "Ana"))
        assertTrue(r.snapshot.value.players.isEmpty())
    }

    @Test
    fun joiningGivesAnIdTokenColourAndShape() {
        val r = room()
        val a = r.enter("Ana")
        val b = r.enter("Bia")
        assertNotEquals(a.token, b.token)
        assertNotEquals(a.id, b.id)
        val players = r.snapshot.value.players.map { it.player }
        assertEquals(2, players.map { it.color }.toSet().size)
        assertTrue(a.token.length >= 22) // 128 bits in base64url
    }

    @Test
    fun namesAreCleanedShortenedAndKeptUnique() {
        val r = room()
        r.enter("  <b>Ana</b>\u0007  ")
        r.enter("Bia")
        r.enter("Bia")
        r.enter("")
        r.enter("x".repeat(40))
        val names = r.snapshot.value.players.map { it.player.name }
        assertEquals("bAna/b", names[0])
        assertEquals("Bia", names[1])
        assertEquals("Bia 2", names[2])
        assertTrue(names[3].startsWith("Pessoa"))
        assertEquals(16, names[4].length)
    }

    @Test
    fun reconnectingWithTheOldTokenGivesTheSameSeat() {
        val r = room()
        val a = r.enter("Ana")
        val again = r.enter("Ana de novo", seat = a.token)
        assertEquals(a.token, again.token)
        assertEquals(a.id, again.id)
        assertEquals(1, r.snapshot.value.players.size)
    }

    @Test
    fun theThirteenthPersonWatchesAndTheTwentiethIsTurnedAway() {
        val r = room()
        val seats = (1..16).map { r.enter("P$it") }
        assertEquals(12, seats.count { !it.spectator })
        assertEquals(4, seats.count { it.spectator })
        assertEquals(JoinResult.Full, r.join(r.key, "extra"))
    }

    @Test
    fun peopleWhoArriveDuringAGameWatchAndPlayFromTheNextOne() {
        val r = room()
        r.seatsOf(3)
        assertNull(r.start(MostLikely))
        val late = r.enter("Tarde")
        assertTrue(late.spectator)
        r.toLobby()
        assertFalse(r.snapshot.value.players.first { it.player.id == late.id }.spectator)
    }

    @Test
    fun anOldKeyAndAllTokensStopWorkingAfterANewRoom() {
        val r = room()
        val a = r.enter("Ana")
        val oldKey = r.key
        r.newRoom()
        assertNotEquals(oldKey, r.key)
        assertEquals(JoinResult.BadKey, r.join(oldKey, "Ana"))
        assertNull(r.view(a.token, 0, 0))
        assertEquals(ActResult.Unauthorized, r.act(a.token, null, null, pick(a.id)))
    }

    @Test
    fun kickingRevokesTheTokenAndTheGameHearsAboutIt() {
        val r = room()
        val s = r.seatsOf(4)
        r.start(MostLikely)
        r.kick(s[3].id)
        assertNull(r.view(s[3].token, 0, 0))
        assertEquals(3, r.game().players.size)
    }

    @Test
    fun renamingWorksOnlyInTheLobby() {
        val r = room()
        val s = r.seatsOf(3)
        assertTrue(r.rename(s[0].token, "Nova"))
        assertEquals("Nova", r.snapshot.value.players[0].player.name)
        r.start(MostLikely)
        assertFalse(r.rename(s[0].token, "Outra"))
    }

    // --- Starting --------------------------------------------------------------------------------

    @Test
    fun aGameThatDoesNotFitSaysWhy() {
        val r = room()
        r.seatsOf(2)
        val why = r.start(MostLikely)
        assertNotNull(why)
        assertTrue(why!!, why.contains("3 a 12"))
        assertNull(r.snapshot.value.game)
    }

    @Test
    fun anotherRoundKeepsEveryoneAndDrawsAgain() {
        val r = room()
        r.seatsOf(4)
        r.start(MostLikely, mapOf("rounds" to 0))
        val first = r.game().questions
        assertNull(r.again())
        assertEquals(4, r.game().players.size)
        assertEquals(5, r.game().questions.size)
        assertNotEquals(first, r.game().questions)
    }

    // --- Acting ----------------------------------------------------------------------------------

    @Test
    fun votesFromThePhonesDriveTheGameToTheReveal() {
        val r = room()
        val s = r.seatsOf(3)
        r.start(MostLikely)
        val phase = r.snapshot.value.game!!.phase
        s.forEach { assertEquals(ActResult.Ok, r.act(it.token, null, phase, pick(s[1].id))) }
        assertEquals(Stage.REVEAL, r.game().stage)
    }

    @Test
    fun actionsForAnEarlierPhaseAreRejected() {
        val r = room()
        val s = r.seatsOf(3)
        r.start(MostLikely)
        val phase = r.snapshot.value.game!!.phase
        s.forEach { r.act(it.token, null, phase, pick(s[0].id)) }
        val late = r.act(s[0].token, null, phase, pick(s[1].id))
        assertTrue(late is ActResult.Rejected)
        assertEquals("Essa etapa já passou", (late as ActResult.Rejected).why)
    }

    @Test
    fun spectatorsAndStrangersCannotAct() {
        val r = room()
        val s = r.seatsOf(3)
        r.start(MostLikely)
        val watcher = r.enter("Plateia")
        assertTrue(r.act(watcher.token, null, null, pick(s[0].id)) is ActResult.Rejected)
        assertEquals(ActResult.Unauthorized, r.act("not-a-token", null, null, pick(s[0].id)))
    }

    @Test
    fun aRepeatedActionIdIsAppliedOnce() {
        val r = room()
        val s = r.seatsOf(3)
        r.start(MostLikely)
        assertEquals(ActResult.Ok, r.act(s[0].token, "a1", null, pick(s[1].id)))
        r.act(s[0].token, "a2", null, pick(s[2].id))
        // the resend of a1 (a slow phone retrying) must not undo the later vote
        assertEquals(ActResult.Ok, r.act(s[0].token, "a1", null, pick(s[1].id)))
        assertEquals(s[2].id, r.game().votes.cast[s[0].id])
    }

    @Test
    fun tooManyTapsInASecondAreRefused() {
        val r = room()
        val s = r.seatsOf(3)
        r.start(MostLikely)
        val results = (1..25).map { r.act(s[0].token, null, null, pick(s[1].id)) }
        assertEquals(20, results.count { it == ActResult.Ok })
        clock.advance(1_001)
        assertEquals(ActResult.Ok, r.act(s[0].token, null, null, pick(s[1].id)))
    }

    // --- What each phone sees --------------------------------------------------------------------

    @Test
    fun aPhoneGetsOnlyItsOwnViewAndOthersVotesDoNotWakeIt() {
        val r = room()
        val s = r.seatsOf(4)
        r.start(MostLikely)
        val before = r.view(s[0].token, 0, 0)!!
        r.act(s[1].token, null, null, pick(s[2].id))
        val after = r.view(s[0].token, 0, 0)!!
        assertEquals("a vote by someone else changes nothing on this phone", before.version, after.version)
        assertEquals(before.json, after.json)
        assertFalse(after.json.contains("\"chosen\""))
        r.act(s[0].token, null, null, pick(s[3].id))
        val mine = r.view(s[0].token, 0, 0)!!
        assertNotEquals(before.version, mine.version)
        assertTrue(mine.json.contains("\"chosen\":\"${s[3].id}\""))
    }

    @Test
    fun theViewSaysWhoIsWhoAndWhatTheNightScoreIs() {
        val r = room()
        val s = r.seatsOf(3)
        val env = SocialJson.decodeFromString<PhoneEnvelope>(r.view(s[0].token, 0, 0)!!.json)
        assertEquals(s[0].id, env.me)
        assertEquals(3, env.players.size)
        assertEquals("lobby", env.view.phase)
    }

    @Test
    fun aPollHoldsUntilTheViewChangesAndThenAnswersAtOnce() {
        val r = room()
        val s = r.seatsOf(3)
        val v0 = r.view(s[0].token, 0, 0)!!.version
        val done = CountDownLatch(1)
        var got: ViewResult? = null
        Thread { got = r.view(s[0].token, v0, 5_000); done.countDown() }.start()
        Thread.sleep(150)
        assertEquals("still waiting", 1L, done.count)
        r.start(MostLikely)
        assertTrue("answered within a second of the change", done.await(1, TimeUnit.SECONDS))
        assertTrue(got!!.version > v0)
        assertTrue(got!!.json.contains("\"w\":\"pick\""))
    }

    @Test
    fun aPollWithNothingNewReturnsTheSameVersionAfterTheWait() {
        val r = room()
        val s = r.seatsOf(3)
        val v0 = r.view(s[0].token, 0, 0)!!.version
        val t = System.nanoTime()
        assertEquals(v0, r.view(s[0].token, v0, 200)!!.version)
        assertTrue((System.nanoTime() - t) / 1_000_000 >= 190)
    }

    // --- Faces ---------------------------------------------------------------------------------------

    private fun jpeg(n: Int = 3_000) = ByteArray(n).also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte() }

    @Test
    fun aFaceMustBeASmallJpegFromAPersonInTheRoom() {
        val r = room()
        val a = r.enter("Ana")
        assertFalse(r.setFace("nobody", jpeg()))
        assertFalse("not a JPEG", r.setFace(a.token, ByteArray(3_000)))
        assertFalse("too big", r.setFace(a.token, jpeg(Room.MAX_FACE_BYTES + 1)))
        assertFalse("too small to be a photo", r.setFace(a.token, jpeg(50)))
        assertNull(r.face(a.id))
        assertTrue(r.setFace(a.token, jpeg()))
        assertEquals(3_000, r.face(a.id)!!.size)
    }

    @Test
    fun theTvIsToldWhenAFaceArrivesAndOtherPhonesAreNotWokenByIt() {
        val r = room()
        val a = r.enter("Ana"); val b = r.enter("Bia")
        val before = r.view(b.token, 0, 0)!!.version
        assertEquals(0, r.snapshot.value.players.first { it.player.id == a.id }.face)
        r.setFace(a.token, jpeg())
        assertEquals(1, r.snapshot.value.players.first { it.player.id == a.id }.face)
        assertEquals("a face is for the TV, not for the phones", before, r.view(b.token, 0, 0)!!.version)
        r.setFace(a.token, jpeg(4_000))
        assertEquals(2, r.snapshot.value.players.first { it.player.id == a.id }.face)
    }

    @Test
    fun aFaceNeverReachesTheDiskNorAnotherPhone() {
        val file = File.createTempFile("room", ".json").apply { delete() }
        val r = room(FileSeatStore(file))
        val a = r.enter("Ana"); val b = r.enter("Bia")
        r.setFace(a.token, ByteArray(3_000) { (it % 251).toByte() }.also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte() })
        r.rename(b.token, "Bia2")   // forces a save after the face arrived
        assertFalse(file.readText().contains("face", ignoreCase = true))
        assertFalse(r.view(b.token, 0, 0)!!.json.contains("face", ignoreCase = true))
        val reloaded = room(FileSeatStore(file))
        assertNull(reloaded.face(a.id))
        file.delete()
    }

    // --- Time ------------------------------------------------------------------------------------

    @Test
    fun thePhaseDeadlineFiresOnTick() {
        val r = room()
        r.seatsOf(3)
        r.start(MostLikely)
        clock.advance(19_999); r.tick()
        assertEquals(Stage.VOTE, r.game().stage)
        clock.advance(2); r.tick()
        assertEquals(Stage.REVEAL, r.game().stage)
    }

    @Test
    fun pausingStopsTheGameClockAndResumingGivesBackTheTime() {
        val r = room()
        val s = r.seatsOf(3)
        r.start(MostLikely)
        clock.advance(10_000)
        r.pause()
        clock.advance(120_000); r.tick()
        assertEquals("nothing fires while paused", Stage.VOTE, r.game().stage)
        assertTrue(r.act(s[0].token, null, null, pick(s[1].id)) is ActResult.Rejected)
        assertNull("no countdown on a paused phone", SocialJson.decodeFromString<PhoneEnvelope>(r.view(s[0].token, 0, 0)!!.json).view.endsAt)
        r.resume()
        clock.advance(9_999); r.tick()
        assertEquals("10 s were already used before the pause, 10 s remain", Stage.VOTE, r.game().stage)
        clock.advance(2); r.tick()
        assertEquals(Stage.REVEAL, r.game().stage)
    }

    @Test
    fun endsAtIsInTheTvsWallClockAndShiftsByThePauses() {
        val r = room()
        val s = r.seatsOf(3)
        val start = clock.now()
        r.start(MostLikely)
        r.pause(); clock.advance(5_000); r.resume()
        val env = SocialJson.decodeFromString<PhoneEnvelope>(r.view(s[0].token, 0, 0)!!.json)
        assertEquals(start + 20_000 + 5_000, env.view.endsAt)
    }

    // --- The night -------------------------------------------------------------------------------

    @Test
    fun finishedGamesAddTheirChipsToTheNight() {
        val r = room()
        val s = r.seatsOf(3)
        r.start(MostLikely, mapOf("rounds" to 0))
        repeat(5) {
            val phase = r.snapshot.value.game!!.phase
            s.forEach { r.act(it.token, null, phase, pick(s[0].id)) }
            r.host(HostCmd.CONTINUE)
        }
        val snap = r.snapshot.value
        assertNotNull(snap.game!!.over)
        assertTrue(snap.night.getValue(s[0].id) > snap.night.getValue(s[1].id))
    }

    // --- Reactions, presence, restart ------------------------------------------------------------

    @Test
    fun reactionsAreLimitedInRateAndKind() {
        val r = room()
        val s = r.seatsOf(3)
        assertTrue(r.react(s[0].token, "laugh"))
        assertFalse("one every 2 s", r.react(s[0].token, "laugh"))
        assertFalse("unknown kind", r.react(s[1].token, "<script>"))
        clock.advance(2_001)
        assertTrue(r.react(s[0].token, "wow"))
        assertEquals(2, r.snapshot.value.reactions.size)
    }

    @Test
    fun reactionsAreHeldBackWhileEveryoneIsVoting() {
        val r = room()
        val s = r.seatsOf(3)
        r.start(MostLikely)
        assertFalse(r.react(s[0].token, "laugh"))
        r.host(HostCmd.CONTINUE)
        assertEquals(Stage.REVEAL, r.game().stage)
        assertTrue(r.react(s[0].token, "laugh"))
    }

    @Test
    fun aPhoneThatStopsAskingGoesGreyAndComesBack() {
        val r = room()
        val s = r.seatsOf(3)
        clock.advance(21_000); r.tick()
        assertFalse(r.snapshot.value.players[0].online)
        r.view(s[0].token, 0, 0)
        assertTrue(r.snapshot.value.players.first { it.player.id == s[0].id }.online)
    }

    @Test
    fun seatsSurviveARestartOfTheLauncherButTheGameDoesNot() {
        val file = File.createTempFile("room", ".json").apply { delete() }
        val store = FileSeatStore(file)
        val first = room(store)
        val a = first.enter("Ana")
        first.enter("Bia")
        val key = first.key

        val second = room(store)
        assertEquals(key, second.key)
        assertEquals(2, second.snapshot.value.players.size)
        assertNull(second.snapshot.value.game)
        assertTrue("the old token still opens its seat", second.view(a.token, 0, 0) != null)
        assertEquals(a.id, (second.join(key, "x", a.token) as JoinResult.Joined).id)
        file.delete()
    }

    @Test
    fun aSavedRoomOlderThanTwelveHoursIsForgotten() {
        val file = File.createTempFile("room", ".json").apply { delete() }
        val store = FileSeatStore(file)
        val first = room(store)
        first.enter("Ana")
        clock.advance(13 * 3_600_000L)
        val second = room(store)
        assertNotEquals(first.key, second.key)
        assertTrue(second.snapshot.value.players.isEmpty())
        file.delete()
    }
}
