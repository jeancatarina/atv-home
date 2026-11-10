package dev.jean.atvhome.social.profile

import dev.jean.atvhome.social.FakeClock
import dev.jean.atvhome.social.QUESTIONS
import dev.jean.atvhome.social.game.Act
import dev.jean.atvhome.social.game.Action
import dev.jean.atvhome.social.game.HostCmd
import dev.jean.atvhome.social.games.mostlikely.MostLikely
import dev.jean.atvhome.social.room.JoinResult
import dev.jean.atvhome.social.room.PhoneEnvelope
import dev.jean.atvhome.social.room.Room
import dev.jean.atvhome.social.game.SocialJson
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.random.Random

class LedgerTest {

    private val d1 = "device-aaaaaaaaaaaa1"; private val d2 = "device-bbbbbbbbbbbb2"; private val d3 = "device-cccccccccccc3"

    @Test fun pointsAddUpAcrossGamesAndNights() {
        val l = Ledger()
        l.record(d1, "Ana", "mostlikely", 5, 1); l.record(d1, "Ana", "couple", 3, 2); l.addNight(d1, "Ana", 2)
        val c = l.get(d1)!!
        assertEquals(8, c.chips); assertEquals(2, c.games); assertEquals(1, c.nights)
        assertEquals(mapOf("mostlikely" to 5, "couple" to 3), c.perGame)
    }

    @Test fun theBoardIsOrderedByChipsThenByFewerGames() {
        val l = Ledger()
        l.record(d1, "Ana", "g", 10, 1); l.record(d2, "Bia", "g", 12, 1); l.record(d3, "Caio", "g", 10, 1); l.record(d3, "Caio", "g", 0, 2)
        assertEquals(listOf("Bia", "Ana", "Caio"), l.ranking().map { it.name })
        assertEquals(1, l.rankOf(d2)); assertEquals(3, l.rankOf(d3)); assertNull(l.rankOf("nobody-aaaaaaaaaaaaaaaa"))
        assertTrue(l.ranking().all { it.face == null })
    }

    @Test fun theBoardSurvivesARestartOfTheLauncher() {
        val f = File.createTempFile("ledger", ".json").apply { delete() }
        Ledger(f).apply { record(d1, "Ana", "g", 7, 1); setFace(d1, "Ana", ByteArray(500) { 1 }, 1) }
        val again = Ledger(f)
        assertEquals(7, again.get(d1)!!.chips); assertEquals(500, again.faceBytes(d1)!!.size)
        f.delete()
    }

    @Test fun anErasedPersonLeavesNoTrace() {
        val f = File.createTempFile("ledger", ".json").apply { delete() }
        val l = Ledger(f); l.record(d1, "Ana", "g", 7, 1); l.setFace(d1, "Ana", ByteArray(500) { 1 }, 1)
        l.forget(d1)
        assertNull(l.get(d1)); assertTrue(l.ranking().isEmpty())
        assertFalse(f.readText().contains("Ana")); assertFalse(f.readText().contains(d1))
        f.delete()
    }

    @Test fun theOldestProfilesGoFirstPastTheCap() {
        val l = Ledger()
        repeat(Ledger.MAX + 20) { i -> l.record("device-%016d".format(i), "P$i", "g", 1, i.toLong()) }
        assertEquals(Ledger.MAX, l.size())
        assertNull(l.get("device-%016d".format(0))); assertNotNull(l.get("device-%016d".format(Ledger.MAX + 19)))
    }

    @Test fun onlyWellFormedPhoneIdsAreAccepted() {
        assertTrue(Ledger.validDevice(d1)); assertFalse(Ledger.validDevice(null)); assertFalse(Ledger.validDevice("short"))
        assertFalse(Ledger.validDevice("has space and <script> 123456")); assertFalse(Ledger.validDevice("x".repeat(65)))
    }

    // --- Through the room ---------------------------------------------------------------------------

    private val clock = FakeClock()
    private fun room(l: Ledger) = Room(clock = clock, content = { QUESTIONS }, ledger = l, random = Random(4))
    private fun pick(id: String) = Action(Act.PICK, buildJsonObject { put("id", JsonPrimitive(id)) })
    private fun Room.enter(name: String, dev: String?, seat: String? = null) = join(key, name, seat, dev) as JoinResult.Joined

    private fun playOne(r: Room, seats: List<JoinResult.Joined>) {
        r.start(MostLikely, mapOf("rounds" to 0))
        repeat(5) {
            val phase = r.snapshot.value.game!!.phase
            seats.forEach { r.act(it.token, null, phase, pick(seats[0].id)) }
            r.host(HostCmd.CONTINUE)
        }
    }

    @Test fun aFinishedGamePaysEachPhonesAllTimeBoard() {
        val l = Ledger(); val r = room(l)
        val a = r.enter("Ana", d1); val b = r.enter("Bia", d2); val c = r.enter("Caio", null)
        playOne(r, listOf(a, b, c))
        assertTrue(l.get(d1)!!.chips > 0 && l.get(d2)!!.chips > 0)
        assertEquals(1, l.get(d1)!!.games); assertEquals(1, l.get(d1)!!.nights)
        assertEquals("a phone without an id is not remembered", 2, l.size())
        playOne(r, listOf(a, b, c))
        assertEquals(2, l.get(d1)!!.games); assertEquals("a night counts once", 1, l.get(d1)!!.nights)
        r.newNight(); playOne(r, listOf(a, b, c))
        assertEquals(2, l.get(d1)!!.nights)
    }

    @Test fun comingBackAnotherDayTheSamePhoneIsRecognisedAndTheScoreContinues() {
        val f = File.createTempFile("ledger", ".json").apply { delete() }
        val day1 = room(Ledger(f))
        val seats = listOf(day1.enter("Ana", d1), day1.enter("Bia", d2), day1.enter("Caio", d3))
        playOne(day1, seats)
        val before = Ledger(f).get(d1)!!.chips
        // The next day: a new room (new key), the launcher restarted, nobody has a token
        val l2 = Ledger(f); val day2 = room(l2)
        val back = day2.enter("", d1)                       // the page sent no name: the TV remembers it
        assertEquals("Ana", day2.snapshot.value.players.single().player.name)
        day2.enter("Bia", d2); day2.enter("Caio", d3)
        playOne(day2, listOf(back, day2.enter("Bia", d2), day2.enter("Caio", d3)))
        assertTrue(l2.get(d1)!!.chips > before)
        assertEquals(2, l2.get(d1)!!.games); assertEquals(2, l2.get(d1)!!.nights)
        f.delete()
    }

    @Test fun theSamePhoneScanningAgainGetsItsSeatBackInsteadOfASecondOne() {
        val r = room(Ledger())
        val a = r.enter("Ana", d1)
        val again = r.enter("Ana", d1)
        assertEquals(a.token, again.token); assertEquals(1, r.snapshot.value.players.size)
    }

    @Test fun aFaceIsKeptOnlyIfThePersonAsksAndComesBackWithThem() {
        val l = Ledger(); val r = room(l)
        val a = r.enter("Ana", d1)
        val jpeg = ByteArray(3_000).also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte() }
        r.setFace(a.token, jpeg, remember = false)
        assertNull("not asked: tonight only", l.faceBytes(d1))
        r.setFace(a.token, jpeg, remember = true)
        assertEquals(3_000, l.faceBytes(d1)!!.size)
        val next = room(l)
        next.enter("", d1)
        assertEquals(1, next.snapshot.value.players.single().face)
        assertEquals(3_000, next.face(next.snapshot.value.players.single().player.id)!!.size)
        // changing their mind: sending a face without asking removes the saved one
        next.setFace(next.join(next.key, "x", null, d1).let { (it as JoinResult.Joined).token }, jpeg, remember = false)
        assertNull(l.faceBytes(d1))
    }

    @Test fun forgettingErasesPointsFaceAndTheLinkToThePhone() {
        val l = Ledger(); val r = room(l)
        val a = r.enter("Ana", d1); val b = r.enter("Bia", d2); val c = r.enter("Caio", d3)
        playOne(r, listOf(a, b, c))
        r.setFace(a.token, ByteArray(3_000).also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte() }, remember = true)
        assertTrue(r.forget(a.token))
        assertNull(l.get(d1)); assertNull(r.face(a.id))
        r.newNight(); playOne(r, listOf(a, b, c))
        assertNull("no longer recorded", l.get(d1)); assertNotNull(l.get(d2))
    }

    @Test fun thePhoneLearnsItsStandingAndTheTopOfTheBoardWithoutAnyoneElsesId() {
        val l = Ledger(); val r = room(l)
        val a = r.enter("Ana", d1); val b = r.enter("Bia", d2); val c = r.enter("Caio", d3)
        playOne(r, listOf(a, b, c))
        val json = r.view(a.token, 0, 0)!!.json
        val env = SocialJson.decodeFromString<PhoneEnvelope>(json)
        assertNotNull(env.standing); assertEquals(l.get(d1)!!.chips, env.standing!!.chips); assertEquals(l.rankOf(d1, dev.jean.atvhome.social.profile.RankBy.XP), env.standing!!.rank)
        assertEquals(3, env.top.size); assertEquals(1, env.top.count { it.me })
        listOf(d1, d2, d3).forEach { assertFalse("a device id never reaches a phone", json.contains(it)) }
        val snap = r.snapshot.value
        assertEquals(3, snap.ranking.size); assertEquals(3, snap.careers.size)
        assertTrue(snap.ranking.all { it.face == null } && snap.careers.values.all { it.face == null })
    }

    @Test fun phoneIdsSurviveARestartOfTheRoomSoSeatsStayLinkedToTheirPerson() {
        val f = File.createTempFile("room", ".json").apply { delete() }
        val l = Ledger()
        val first = Room(clock = clock, content = { QUESTIONS }, ledger = l, store = dev.jean.atvhome.social.room.FileSeatStore(f))
        val a = first.enter("Ana", d1); first.enter("Bia", d2); first.enter("Caio", d3)
        val second = Room(clock = clock, content = { QUESTIONS }, ledger = l, store = dev.jean.atvhome.social.room.FileSeatStore(f))
        val seats = second.snapshot.value.players
        assertEquals(3, seats.size)
        val ids = seats.map { it.player.id }
        playOne(second, listOf(JoinResult.Joined(a.token, ids[0], false), second.join(second.key, "Bia", null, d2) as JoinResult.Joined, second.join(second.key, "Caio", null, d3) as JoinResult.Joined))
        assertTrue(l.get(d1)!!.games == 1 && l.get(d2)!!.games == 1)
        f.delete()
    }
}
