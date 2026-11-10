package dev.jean.atvhome.social.room

import dev.jean.atvhome.social.game.Action
import dev.jean.atvhome.social.game.Game
import dev.jean.atvhome.social.game.GameInfo
import dev.jean.atvhome.social.game.HostCmd
import dev.jean.atvhome.social.game.Mood
import dev.jean.atvhome.social.game.Outcome
import dev.jean.atvhome.social.game.PhoneView
import dev.jean.atvhome.social.game.Player
import dev.jean.atvhome.social.game.PlayerId
import dev.jean.atvhome.social.game.SocialJson
import dev.jean.atvhome.social.game.Setup
import dev.jean.atvhome.social.game.Step
import dev.jean.atvhome.social.game.Widget
import dev.jean.atvhome.social.night.Award
import dev.jean.atvhome.social.kit.Variety
import dev.jean.atvhome.social.profile.Career
import dev.jean.atvhome.social.profile.HouseRecord
import dev.jean.atvhome.social.profile.Ledger
import dev.jean.atvhome.social.profile.RankBy
import dev.jean.atvhome.social.progress.Celebration
import dev.jean.atvhome.social.progress.Gain
import dev.jean.atvhome.social.progress.NightContext
import dev.jean.atvhome.social.progress.PlayerResult
import dev.jean.atvhome.social.progress.ProfileView
import dev.jean.atvhome.social.progress.ProfileViews
import dev.jean.atvhome.social.progress.Progression
import dev.jean.atvhome.social.progress.Records
import dev.jean.atvhome.social.night.Ceremony
import dev.jean.atvhome.social.night.NightEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.security.SecureRandom
import java.util.Base64
import kotlin.random.Random

fun interface Clock { fun now(): Long }

/** Calls [run] at wall time [whenMs], replacing any earlier request. The room never sleeps on its own. */
fun interface Timer { fun at(whenMs: Long, run: () -> Unit) }

/** [face] counts the photos this person sent (0: none), so the TV knows when to decode a new one. */
data class PlayerInfo(val player: Player, val online: Boolean, val spectator: Boolean, val face: Int = 0)
data class Reaction(val by: PlayerId, val kind: String, val at: Long)

/** The running game as the TV draws it: the immutable [state] plus what the room knows around it. */
data class GameSnap(val info: GameInfo, val state: Any, val phase: String, val endsAt: Long?, val over: Outcome?)

data class RoomSnapshot(
    val version: Long,
    val key: String,
    val players: List<PlayerInfo>,
    val game: GameSnap?,
    val paused: Boolean,
    /** Chips earned across the night's games. */
    val night: Map<PlayerId, Int>,
    val reactions: List<Reaction>,
    /** Memorable moments of the night's games, for the host's lines and the trophies. */
    val events: List<NightEvent> = emptyList(),
    /** The trophies, once the night was closed; empty before. */
    val awards: List<Award> = emptyList(),
    /** The all-time board, best first (without photos). */
    val ranking: List<Career> = emptyList(),
    /** Each person at the table with their all-time numbers, when their phone is known. */
    val careers: Map<PlayerId, Career> = emptyMap(),
    /** The all-time board by XP is [ranking]; this one is the month's. */
    val rankingSeason: List<Career> = emptyList(),
    val records: List<HouseRecord> = emptyList(),
    val season: String = "",
    /** XP each person earned in the game that just ended, for the result screen's bars. */
    val gains: Map<PlayerId, Gain> = emptyMap(),
    /** Parties waiting to be shown: level ups, badges, records, challenges. Newest last. */
    val celebrations: List<Celebration> = emptyList(),
)

/** A line of the all-time board as a phone sees it: no device ids, only whether it is the person themself. */
@Serializable
data class RankLine(val name: String, val chips: Int, val me: Boolean = false, val level: Int = 1)

/** The viewing person's own all-time numbers. */
@Serializable
data class Standing(val chips: Int, val games: Int, val nights: Int, val rank: Int?, val remembered: Boolean)

sealed interface JoinResult {
    data class Joined(val token: String, val id: PlayerId, val spectator: Boolean) : JoinResult
    data object BadKey : JoinResult
    data object Full : JoinResult
}

sealed interface ActResult {
    data object Ok : ActResult
    data class Rejected(val why: String) : ActResult
    data object Unauthorized : ActResult
}

/** What a phone gets back from a poll: the version it now holds and the body to send. */
data class ViewResult(val version: Long, val json: String)

@Serializable
data class PhoneEnvelope(
    val me: PlayerId,
    val spectator: Boolean,
    val paused: Boolean,
    val players: List<Player>,
    val night: Map<PlayerId, Int>,
    val view: PhoneView,
    val awards: List<Award> = emptyList(),
    val standing: Standing? = null,
    val top: List<RankLine> = emptyList(),
    /** Your own career: level, badges, challenges, rivals. */
    val profile: ProfileView? = null,
    /** Parties that are about you, newest last (the phone shows each once, by id). */
    val moments: List<Celebration> = emptyList(),
)

/**
 * One room of people around the TV, and the single owner of its truth. Phones hold a token for
 * their seat; they send actions and read only the view built for them. The running game is a pure
 * state machine the room advances; the room itself knows nothing about any game's rules.
 *
 * Everything happens under one lock: actions take microseconds, and a phone's long poll simply
 * waits on it. The game's own clock stops while the room is paused, so deadlines never fire early.
 */
class Room(
    key: String? = null,
    private val clock: Clock = Clock { System.currentTimeMillis() },
    private val content: (gameId: String) -> List<String> = { emptyList() },
    private val timer: Timer = Timer { _, _ -> },
    private val store: SeatStore? = null,
    private val ledger: Ledger = Ledger(),
    private val random: Random = Random.Default,
) {
    private class Seat(val token: String, var player: Player, var spectator: Boolean, val slot: Int) {
        var lastSeen = 0L
        var polling = 0
        var online = true
        var version = 1L
        var envelope = ""
        val aids = ArrayDeque<String>()
        var windowStart = 0L
        var windowCount = 0
        var lastReact = 0L
        /** A small JPEG the person chose as their face. Memory only: it is never written to disk. */
        var face: ByteArray? = null
        var faceVersion = 0
        /** The phone's own random id, if it sent one: how the all-time board knows it came back. */
        var device: String? = null
    }

    private class Running<S : Any>(val game: Game<S>, var state: S, val settings: Map<String, Int>, val playerIds: List<PlayerId>) {
        var finished: Outcome? = null
        fun act(by: PlayerId, a: Action, now: Long): String? = when (val r = game.act(state, by, a, now)) {
            is Step.Ok -> { state = r.state; null }
            is Step.Rejected -> r.why
        }
        fun host(cmd: HostCmd, now: Long) { state = game.host(state, cmd, now) }
        fun timeout(now: Long) { state = game.timeout(state, now) }
        fun left(p: PlayerId, now: Long) { state = game.left(state, p, now) }
        fun phone(p: PlayerId): PhoneView = game.phone(state, p)
        val deadline: Long? get() = game.deadline(state)
        val phase: String get() = game.phase(state)
        val mood: Mood get() = game.mood(state)
        fun over(): Outcome? = game.over(state)
        fun snap(over: Outcome?, endsAt: Long?) = GameSnap(game.info, state, phase, endsAt, over)
    }

    private val lock = Object()
    private val seats = LinkedHashMap<String, Seat>()
    private var running: Running<*>? = null
    private var closed = false
    private var paused = false
    private var pausedAt = 0L
    private var pausedTotal = 0L
    private var nextId = 1
    private var version = 0L
    private val night = HashMap<PlayerId, Int>()
    private val reactions = ArrayDeque<Reaction>()
    private val events = ArrayList<NightEvent>()
    private val deviceByPlayer = HashMap<PlayerId, String>()
    private val nightCounted = HashSet<String>()
    private val winStreak = HashMap<String, Int>()
    private var gains: Map<PlayerId, Gain> = emptyMap()
    private val celebrations = ArrayList<Celebration>()
    private var celebSeq = 0
    private var awards: List<Award> = emptyList()

    var key: String = key ?: newSecret(); private set

    private val state = MutableStateFlow(snapshot())
    /** The TV draws from this. */
    val snapshot: StateFlow<RoomSnapshot> = state

    init { synchronized(lock) { store?.load()?.takeIf { clock.now() - it.savedAt < RESTORE_MS }?.let(::restore) } }

    // --- Phones ----------------------------------------------------------------------------------

    fun join(key: String, name: String, seat: String? = null, device: String? = null): JoinResult = synchronized(lock) {
        if (closed || !sameSecret(key, this.key)) return JoinResult.BadKey
        seats[seat]?.let { touch(it); return JoinResult.Joined(it.token, it.player.id, it.spectator) }
        val dev = device?.takeIf { Ledger.validDevice(it) }
        // The same phone scanning again (lost its token, cleared the page) gets its own seat back
        dev?.let { d -> seats.values.firstOrNull { it.device == d }?.let { touch(it); return JoinResult.Joined(it.token, it.player.id, it.spectator) } }
        val known = dev?.let { ledger.get(it) }
        val playing = seats.values.count { !it.spectator }
        val watching = seats.size - playing
        val spectator = running != null || playing >= MAX_PLAYERS
        if (spectator && watching >= MAX_SPECTATORS) return JoinResult.Full
        val slot = (0 until MAX_PLAYERS + MAX_SPECTATORS).first { s -> seats.values.none { it.slot == s } }
        val id = "p${nextId++}"
        val player = Player(id, uniqueName(clean(name, known?.name ?: "Pessoa $nextId")), slot % COLORS, slot % SHAPES)
        val s = Seat(newSecret(), player, spectator, slot).also { touch(it) }
        s.device = dev
        dev?.let { deviceByPlayer[id] = it; ledger.rename(it, player.name) }
        // Someone who once asked to be remembered comes back with their face
        known?.face?.let { f -> runCatching { java.util.Base64.getDecoder().decode(f) }.getOrNull()?.let { s.face = it; s.faceVersion = 1 } }
        seats[s.token] = s
        changed(persist = true)
        JoinResult.Joined(s.token, id, spectator)
    }

    fun leave(token: String) = synchronized(lock) { seats[token]?.let { remove(it) } }

    /** The photo a person picked as their face: a small JPEG, kept only in memory and only for this night. */
    fun setFace(token: String, jpeg: ByteArray, remember: Boolean = false): Boolean = synchronized(lock) {
        val seat = seats[token] ?: return false
        touch(seat)
        val isJpeg = jpeg.size in 200..MAX_FACE_BYTES && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte()
        if (!isJpeg) return false
        seat.face = jpeg
        seat.faceVersion++
        // Only with the person's say-so does the photo outlive tonight
        seat.device?.let { d -> if (remember) ledger.setFace(d, seat.player.name, jpeg, clock.now()) else if (ledger.get(d)?.face != null) ledger.setFace(d, seat.player.name, null, clock.now()) }
        publish()
        true
    }

    /** "Esquecer meus dados": the person's points, photo and phone id are erased from the TV. */
    fun forget(token: String): Boolean = synchronized(lock) {
        val seat = seats[token] ?: return false
        seat.device?.let { d -> ledger.forget(d); deviceByPlayer.remove(seat.player.id); nightCounted.remove(d) }
        seat.device = null
        seat.face = null
        seat.faceVersion++
        publish()
        true
    }

    /** The photo of a remembered person who is not at the table, for the all-time board. */
    fun rankedFace(device: String): ByteArray? = ledger.faceBytes(device)

    /** The face of [id] for a phone that holds a seat's [token]; strangers get nothing. */
    fun faceFor(token: String, id: PlayerId): ByteArray? = synchronized(lock) {
        if (seats[token] == null) null else seats.values.firstOrNull { it.player.id == id }?.face
    }

    /** A person's face for the TV to draw, or null. */
    fun face(id: PlayerId): ByteArray? = synchronized(lock) { seats.values.firstOrNull { it.player.id == id }?.face }

    /** The TV removes someone. */
    fun kick(id: PlayerId) = synchronized(lock) { seats.values.firstOrNull { it.player.id == id }?.let { remove(it) } }

    private fun remove(seat: Seat) {
        seats.remove(seat.token)
        if (!seat.spectator) running?.let { if (it.finished == null) it.left(seat.player.id, gameNow()) }
        changed(persist = true)
    }

    fun rename(token: String, name: String): Boolean = synchronized(lock) {
        val seat = seats[token] ?: return false
        if (running != null) return false
        touch(seat)
        seat.player = seat.player.copy(name = uniqueName(clean(name, seat.player.name), except = seat))
        changed(persist = true)
        true
    }

    /**
     * Holds until this phone's view differs from [since] (or [waitMs] passes) and returns it.
     * Null when the token is not a seat of this room (anymore).
     */
    fun view(token: String, since: Long, waitMs: Long): ViewResult? {
        val deadline = System.nanoTime() + waitMs * 1_000_000
        synchronized(lock) {
            val seat = seats[token] ?: return null
            touch(seat)
            seat.polling++
            try {
                while (!closed && seats[token] === seat && seat.version == since) {
                    val left = (deadline - System.nanoTime()) / 1_000_000
                    if (left <= 0) break
                    lock.wait(left)
                }
                if (closed || seats[token] !== seat) return null
                return ViewResult(seat.version, seat.envelope)
            } finally {
                seat.polling--
                seat.lastSeen = clock.now()
            }
        }
    }

    fun act(token: String, aid: String?, phase: String?, a: Action): ActResult = synchronized(lock) {
        val seat = seats[token] ?: return ActResult.Unauthorized
        touch(seat)
        val now = clock.now()
        if (now - seat.windowStart >= 1_000) { seat.windowStart = now; seat.windowCount = 0 }
        if (++seat.windowCount > ACTS_PER_SECOND) return ActResult.Rejected("Devagar, são muitos toques")
        if (aid != null) {
            if (aid in seat.aids) return ActResult.Ok
            seat.aids.addLast(aid)
            if (seat.aids.size > DEDUPE) seat.aids.removeFirst()
        }
        if (seat.spectator) return ActResult.Rejected("Você está só assistindo")
        val g = running ?: return ActResult.Rejected("Nenhum jogo em andamento")
        if (paused) return ActResult.Rejected("O jogo está pausado")
        if (g.finished != null) return ActResult.Rejected("A partida acabou")
        if (phase != null && phase != g.phase) return ActResult.Rejected("Essa etapa já passou")
        g.act(seat.player.id, a, gameNow())?.let { return ActResult.Rejected(it) }
        changed()
        ActResult.Ok
    }

    /** A reaction stamp ("Suspeito!") from the couch: only while people are talking, at most one every 2 s. */
    fun react(token: String, kind: String): Boolean = synchronized(lock) {
        val seat = seats[token] ?: return false
        touch(seat)
        val now = clock.now()
        val talking = running?.let { it.finished == null && it.mood == Mood.TALK } ?: true
        if (kind !in REACTIONS || !talking || paused || now - seat.lastReact < REACT_GAP_MS) return false
        seat.lastReact = now
        reactions.addLast(Reaction(seat.player.id, kind, now))
        while (reactions.size > MAX_REACTIONS) reactions.removeFirst()
        publish()
        true
    }

    // --- The TV ----------------------------------------------------------------------------------

    /** Starts [game] with the people at the table. Returns why it cannot, or null. */
    fun start(game: Game<*>, settings: Map<String, Int> = emptyMap()): String? = synchronized(lock) { begin(game, settings) }

    private fun <S : Any> begin(game: Game<S>, settings: Map<String, Int>): String? {
        promoteSpectators()
        val players = seats.values.filter { !it.spectator }.map { it.player }
        val info = game.info
        if (!info.fits(players.size)) {
            return if (info.minPlayers == info.maxPlayers) "${info.name} é para ${info.minPlayers} pessoas"
            else "${info.name} precisa de ${info.minPlayers} a ${info.maxPlayers} pessoas"
        }
        val all = content(info.id)
        // Only what this house has not played yet; the pack starts over when too little is left
        val pack = Variety.fresh(all, ledger.seen(info.id)) ?: run { ledger.resetSeen(info.id); all }
        game.unavailable(pack)?.let { return it }
        gains = emptyMap()
        paused = false
        pausedTotal = 0
        val s = game.setup(Setup(players, settings, random, gameNow(), pack))
        ledger.markSeen(info.id, Variety.used(pack, s).map(Variety::key))
        running = Running(game, s, settings, players.map { it.id })
        changed()
        return null
    }

    /** "Mais uma": the same game and settings, new draw. */
    fun again(): String? = synchronized(lock) { running?.let { begin(it.game, it.settings) } }

    /** Back to the sheet: the room stays, nobody reconnects. */
    fun toLobby() = synchronized(lock) {
        running = null
        paused = false
        promoteSpectators()
        changed()
    }

    fun host(cmd: HostCmd) = synchronized(lock) {
        val g = running ?: return@synchronized
        if (paused || g.finished != null) return@synchronized
        g.host(cmd, gameNow())
        changed()
    }

    fun pause() = synchronized(lock) {
        if (running == null || paused) return@synchronized
        paused = true
        pausedAt = clock.now()
        changed()
    }

    fun resume() = synchronized(lock) {
        if (!paused) return@synchronized
        pausedTotal += clock.now() - pausedAt
        paused = false
        changed()
    }

    /** Closes the night: the trophies are worked out and shown on the TV and on every phone. */
    fun closeNight() = synchronized(lock) {
        val players = seats.values.filter { !it.spectator }.map { it.player }
        awards = Ceremony.build(players, night.toMap(), events.toList())
        running = null
        paused = false
        changed()
    }

    /** After the ceremony: a clean board for the next night (the people stay). */
    fun newNight() = synchronized(lock) {
        night.clear(); events.clear(); awards = emptyList(); nightCounted.clear(); winStreak.clear(); gains = emptyMap()
        changed()
    }

    /** A new key and no seats: everyone who was in is out. */
    fun newRoom() = synchronized(lock) {
        key = newSecret()
        seats.clear()
        running = null
        paused = false
        night.clear(); events.clear(); awards = emptyList(); nightCounted.clear(); winStreak.clear(); gains = emptyMap(); celebrations.clear()
        reactions.clear()
        changed(persist = true)
    }

    fun close() = synchronized(lock) {
        closed = true
        lock.notifyAll()
    }

    /** Fires the game's deadline if it passed and refreshes who is online. Call it when the timer rings. */
    fun tick() = synchronized(lock) {
        if (closed) return@synchronized
        val g = running
        var moved = false
        var guard = 0
        while (g != null && !paused && g.finished == null && guard++ < MAX_CHAIN) {
            val d = g.deadline ?: break
            if (gameNow() < d) break
            g.timeout(gameNow())
            moved = true
        }
        val flipped = seats.values.count { refreshOnline(it) } > 0
        if (moved || flipped) changed() else rearm()
    }

    // --- Internals -------------------------------------------------------------------------------

    /** The game's clock: wall time minus every pause, frozen while paused. */
    private fun gameNow(): Long = (if (paused) pausedAt else clock.now()) - pausedTotal

    /** Any sign of life; a phone that was grey comes back on the TV at once. */
    private fun touch(seat: Seat) {
        seat.lastSeen = clock.now()
        if (!seat.online) { seat.online = true; publish() }
    }

    private fun refreshOnline(seat: Seat): Boolean {
        val on = seat.polling > 0 || clock.now() - seat.lastSeen < PRESENCE_MS
        val flipped = on != seat.online
        seat.online = on
        return flipped
    }

    private fun promoteSpectators() {
        var room = MAX_PLAYERS - seats.values.count { !it.spectator }
        seats.values.filter { it.spectator }.forEach { if (room > 0) { it.spectator = false; room-- } }
    }

    private fun changed(persist: Boolean = false) {
        running?.let { g ->
            if (g.finished == null) g.over()?.let { outcome ->
                g.finished = outcome
                outcome.chips.forEach { (who, n) -> night.merge(who, n, Int::plus) }
                settle(g.game.info, g.playerIds, outcome)
                outcome.highlights.forEach { events += NightEvent(g.game.info.id, it) }
                while (events.size > 200) events.removeAt(0)
            }
        }
        seats.values.forEach { refreshOnline(it) }
        publish()
        if (persist) store?.save(saved())
    }

    /**
     * The game is over: every phone that played gets its XP, level, badges, challenges, rivalries
     * and the house records, and the parties are queued for the TV and the phones.
     */
    private fun settle(info: GameInfo, playerIds: List<PlayerId>, outcome: Outcome) {
        val now = clock.now()
        val winners = outcome.winners
        val known = playerIds.mapNotNull { pid -> deviceByPlayer[pid]?.let { pid to it } }
        val name = { pid: PlayerId -> seats.values.firstOrNull { it.player.id == pid }?.player?.name ?: "Pessoa" }
        fun party(kind: String, who: PlayerId, title: String, detail: String, tier: Int = 0) {
            celebrations += Celebration(++celebSeq, kind, who, title, detail, tier)
            while (celebrations.size > 24) celebrations.removeAt(0)
        }
        val newGains = HashMap<PlayerId, Gain>()
        known.forEach { (pid, d) ->
            val won = pid in winners
            // A winning run inside one night: three in a row sets the person on fire
            winStreak[d] = if (won) (winStreak[d] ?: 0) + 1 else 0
            val owned = outcome.highlights.filter { it.who == pid || it.who == null }
            val r = PlayerResult(info.id, info.category, info.coop, won, outcome.chips[pid] ?: 0, owned, playerIds.size)
            val p = Progression.applyGame(ledger, d, name(pid), r, NightContext(now, nightCounted.add(d), winStreak[d] ?: 0))
            newGains[pid] = p.gain
            if (p.after.level > (p.before?.level ?: 1)) party("LEVEL", pid, "Nível ${p.after.level}", Progression.title(p.after.level), p.after.level)
            p.badges.forEach { party("BADGE", pid, it.name, it.hint, it.tier) }
            p.challenges.forEach { party("CHALLENGE", pid, "Desafio cumprido", it.text, 1) }
            if (winStreak[d] == 3) party("STREAK", pid, "Em chamas", "3 vitórias seguidas", 2)
        }
        // Head to head, in games where people compete against each other
        if (!info.coop && winners.isNotEmpty()) {
            val won = known.filter { it.first in winners }
            val lost = known.filter { it.first !in winners }
            won.forEach { (wp, wd) -> lost.forEach { (lp, ld) -> Progression.rivalry(ledger, wd, name(wp), ld, name(lp), now) } }
        }
        // Records of the house: each person's own moments, and the ones the whole table shares
        val group = outcome.highlights.filter { it.who == null }
        known.forEach { (pid, d) ->
            Progression.offerRecords(ledger, d, name(pid), info.id, outcome.highlights.filter { it.who == pid }, now).forEach { b ->
                party("RECORD", pid, "Recorde da casa", Records.def(b.gameId, b.key)?.label ?: "Novo recorde", 3)
            }
        }
        if (group.isNotEmpty() && known.isNotEmpty()) {
            val holder = known.joinToString(" e ") { name(it.first) }
            Progression.offerRecords(ledger, known.first().second, holder, info.id, group, now).forEach { b ->
                known.forEach { (pid, _) -> party("RECORD", pid, "Recorde da casa", Records.def(b.gameId, b.key)?.label ?: "Novo recorde", 3) }
            }
        }
        gains = newGains
    }

    private fun publish() {
        version++
        val snap = snapshot()
        state.value = snap
        val players = seats.values.filterNot { it.spectator }.map { it.player } + seats.values.filter { it.spectator }.map { it.player }
        seats.values.forEach { seat ->
            val json = SocialJson.encodeToString(envelope(seat, players))
            if (json != seat.envelope) { seat.envelope = json; seat.version++ }
        }
        lock.notifyAll()
        rearm()
    }

    private fun envelope(seat: Seat, players: List<Player>): PhoneEnvelope {
        val g = running
        val view = when {
            g != null && !seat.spectator -> g.phone(seat.player.id).let {
                it.copy(endsAt = if (paused) null else it.endsAt?.plus(pausedTotal))
            }
            g != null -> PhoneView("watch", body = Widget.Wait("Você tá só assistindo", "Você entra na próxima partida", react = true))
            else -> PhoneView("lobby", body = Widget.Wait("Você tá na sala", "Olha pra TV", react = true))
        }
        val mine = awards.filter { it.who == seat.player.id }
        val shown = if (awards.isNotEmpty() && running == null) PhoneView(
            "ceremony", "Cerimônia da noite",
            body = Widget.Wait(if (mine.isEmpty()) "Valeu por jogar!" else "Seu troféu: ${mine.first().title}", mine.firstOrNull()?.blurb ?: "Olha pra TV", react = true),
        ) else view
        val dev = seat.device
        val me = dev?.let { ledger.get(it) }
        val standing = me?.let { Standing(it.chips, it.games, it.nights, ledger.rankOf(it.device, RankBy.XP), it.face != null) }
        // A phone the TV has not met yet still gets a profile: level 1, every badge locked, the week's challenges
        val profile = (me ?: dev?.let { Career(it, seat.player.name) })?.let { ProfileViews.of(it, ledger.rankOf(it.device, RankBy.SEASON), clock.now()) }
        val moments = celebrations.filter { it.who == seat.player.id }.takeLast(4)
        val present = seats.values.mapNotNull { it.device }.toSet()
        val top = ledger.ranking(5, RankBy.XP).let { list -> list.map { c -> RankLine(c.name, c.chips, me = c.device == dev, level = c.level) } }
        return PhoneEnvelope(seat.player.id, seat.spectator, paused, players, night.toMap(), shown, awards, standing, if (present.isNotEmpty()) top else emptyList(), profile, moments)
    }

    private fun snapshot(): RoomSnapshot {
        val g = running
        return RoomSnapshot(
            version, key,
            seats.values.map { PlayerInfo(it.player, it.online, it.spectator, it.faceVersion) },
            g?.snap(g.finished, g.deadline?.let { if (paused) null else it + pausedTotal }),
            paused, night.toMap(), reactions.toList(), events.toList(), awards,
            ledger.ranking(10, RankBy.XP),
            seats.values.mapNotNull { s -> s.device?.let { d -> ledger.get(d)?.slim()?.let { s.player.id to it } } }.toMap(),
            ledger.ranking(10, RankBy.SEASON), ledger.houseRecords(), ledger.currentSeason(), gains, celebrations.toList(),
        )
    }

    private fun rearm() {
        if (closed) return
        val now = clock.now()
        val g = running
        val next = if (g != null && !paused && g.finished == null) g.deadline?.plus(pausedTotal) else null
        val sweep = if (seats.isNotEmpty()) now + SWEEP_MS else null
        val at = listOfNotNull(next, sweep).minOrNull() ?: return
        timer.at(at, ::tick)
    }

    private fun uniqueName(base: String, except: Seat? = null): String {
        val taken = seats.values.filter { it !== except }.map { it.player.name.lowercase() }.toSet()
        if (base.lowercase() !in taken) return base
        return (2..99).map { "$base $it" }.first { it.lowercase() !in taken }
    }

    private fun saved() = SavedRoom(
        key, clock.now(), nextId,
        seats.values.map { SavedSeat(it.token, it.player, it.spectator, it.slot, it.device) },
    )

    private fun restore(s: SavedRoom) {
        key = s.key
        nextId = s.nextId
        s.seats.forEach { seat ->
            seats[seat.token] = Seat(seat.token, seat.player, seat.spectator, seat.slot).also { it.online = false; it.device = seat.device; seat.device?.let { d -> deviceByPlayer[seat.player.id] = d } }
        }
        publish()
    }

    companion object {
        const val MAX_FACE_BYTES = 24 * 1024
        const val MAX_PLAYERS = 12
        const val MAX_SPECTATORS = 4
        const val COLORS = 12
        const val SHAPES = 6
        const val PRESENCE_MS = 20_000L
        const val SWEEP_MS = 5_000L
        const val RESTORE_MS = 12 * 3_600_000L
        private const val ACTS_PER_SECOND = 20
        private const val DEDUPE = 64
        private const val REACT_GAP_MS = 2_000L
        private const val MAX_REACTIONS = 6
        private const val MAX_CHAIN = 8
        val REACTIONS = setOf("suspect", "lie", "laugh", "wow")

        private val rng = SecureRandom()

        /** 128 bits, URL-safe: used for the room key and for every seat's token. */
        fun newSecret(): String = ByteArray(16).also(rng::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

        fun sameSecret(a: String, b: String): Boolean = java.security.MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

        /** 1–16 printable characters, no control characters or angle brackets. */
        fun clean(raw: String, fallback: String): String =
            raw.filter { !it.isISOControl() && it != '<' && it != '>' }.trim().take(16).ifBlank { fallback }
    }
}
