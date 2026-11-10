package dev.jean.atvhome.social.profile

import dev.jean.atvhome.social.game.SocialJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.Base64

/** How one pair of people fared against each other in competitive games. */
@Serializable
data class Rival(val name: String, val wins: Int = 0, val losses: Int = 0)

/** Everything the TV remembers about one phone over all the nights: points, level, badges, and (only if asked) a face. */
@Serializable
data class Career(
    val device: String,
    val name: String,
    val chips: Int = 0,
    val games: Int = 0,
    val nights: Int = 0,
    /** Chips earned in each game, by game id. */
    val perGame: Map<String, Int> = emptyMap(),
    val lastPlayed: Long = 0,
    /** A small JPEG in base64, kept only when the person ticked "remember my face". */
    val face: String? = null,
    // --- Progression (v2). Old files lack these and decode to the defaults; see Ledger.migrate ---
    val v: Int = 1,
    val xp: Int = 0,
    val wins: Int = 0,
    val winsPerGame: Map<String, Int> = emptyMap(),
    val seasonKey: String = "",
    val seasonXp: Int = 0,
    /** "2026-10:1" for first place of October 2026, kept forever. */
    val medals: List<String> = emptyList(),
    /** Badge id → when it was unlocked. */
    val badges: Map<String, Long> = emptyMap(),
    /** Running counts the badges and challenges read ("hl:UNANIMOUS", "bigTable"…). */
    val counters: Map<String, Int> = emptyMap(),
    /** Best values ("hl:FOOLED", "streakWins") and smallest values ("hl:DEFUSED"). */
    val maxes: Map<String, Int> = emptyMap(),
    val mins: Map<String, Int> = emptyMap(),
    /** Consecutive weeks with a night played, and the last week counted ("2026-W45"). */
    val streakWeeks: Int = 0,
    val lastWeek: String = "",
    /** Progress on weekly challenges, "2026-W45:win3" → count. */
    val challenges: Map<String, Int> = emptyMap(),
    /** Games played this week, for the variety challenge. */
    val weekGames: List<String> = emptyList(),
    val rivals: Map<String, Rival> = emptyMap(),
    /** When the person last played each game (game id → time), so the TV can suggest what they have not played in a while. */
    val lastByGame: Map<String, Long> = emptyMap(),
) {
    /** The same career without the photo, for lists and snapshots. */
    fun slim() = copy(face = null)

    val level: Int get() = dev.jean.atvhome.social.progress.Progression.level(xp)
}

/** A record of the house: the best of everybody in one game, with its owner and date. */
@Serializable
data class HouseRecord(val gameId: String, val key: String, val value: Int, val holder: String, val device: String, val at: Long)

@Serializable
private class LedgerFile(
    val v: Int = 2, val careers: List<Career> = emptyList(), val records: List<HouseRecord> = emptyList(), val season: String = "",
    /** Content already played in each game (fingerprints of its questions, words, cases), so a pack is used up before anything repeats. */
    val seen: Map<String, List<String>> = emptyMap(),
)

enum class RankBy { CHIPS, XP, SEASON }

/**
 * The all-time board. A phone is recognised by a random id it keeps in its own storage, so coming
 * back another day finds the same person and the points go on. Everything is a small JSON file,
 * written atomically; the oldest profiles go first past [MAX]. A person can erase theirs.
 */
class Ledger(private val file: File? = null) {

    private val careers = LinkedHashMap<String, Career>()
    private val records = LinkedHashMap<String, HouseRecord>()
    private var season = ""
    private val seen = HashMap<String, LinkedHashSet<String>>()

    init {
        file?.let { f ->
            val text = runCatching { f.readText() }.getOrNull()
            if (text != null) {
                // The first version wrote a bare list of careers; the second an object. Both load.
                val loaded = runCatching { SocialJson.decodeFromString<LedgerFile>(text) }.getOrNull()
                    ?: runCatching { LedgerFile(careers = SocialJson.decodeFromString<List<Career>>(text)) }.getOrNull()
                loaded?.let { l ->
                    l.careers.forEach { careers[it.device] = migrate(it) }
                    l.records.forEach { records["${it.gameId}:${it.key}"] = it }
                    season = l.season
                    l.seen.forEach { (g, keys) -> seen[g] = LinkedHashSet(keys) }
                }
            }
        }
    }

    /** Careers saved before levels existed get the XP their chips, games and nights would have earned. */
    private fun migrate(c: Career): Career =
        if (c.v >= 2) c else c.copy(v = 2, xp = c.chips * 10 + c.games * 20 + c.nights * 30)

    @Synchronized fun get(device: String): Career? = careers[device]

    /** Fingerprints of the content of [gameId] this house has already played. */
    @Synchronized fun seen(gameId: String): Set<String> = seen[gameId]?.toSet() ?: emptySet()

    /** Remembers what a game just used. When everything in a pack has been played, [reset] starts it over. */
    @Synchronized fun markSeen(gameId: String, keys: Collection<String>) {
        if (keys.isEmpty()) return
        seen.getOrPut(gameId) { LinkedHashSet() }.addAll(keys); save()
    }

    @Synchronized fun resetSeen(gameId: String) { if (seen.remove(gameId) != null) save() }

    /** Changes one career (creating it if new) and saves. Everything that updates a person goes through here. */
    @Synchronized
    fun mutate(device: String, name: String, now: Long, change: (Career) -> Career): Career {
        val c = careers[device] ?: Career(device, name, v = 2)
        val n = change(c).copy(lastPlayed = now)
        careers[device] = n
        save()
        return n
    }

    @Synchronized
    fun record(device: String, name: String, gameId: String, chips: Int, now: Long) {
        val c = careers[device] ?: Career(device, name)
        careers[device] = c.copy(
            name = name, chips = c.chips + chips, games = c.games + 1, lastPlayed = now,
            perGame = c.perGame + (gameId to (c.perGame[gameId] ?: 0) + chips),
        )
        save()
    }

    /** One more night for this person (called once per night, with their first game). */
    @Synchronized
    fun addNight(device: String, name: String, now: Long) {
        val c = careers[device] ?: Career(device, name)
        careers[device] = c.copy(nights = c.nights + 1, lastPlayed = now)
        save()
    }

    @Synchronized
    fun rename(device: String, name: String) {
        careers[device]?.let { careers[device] = it.copy(name = name); save() }
    }

    /** Keeps (or with null, drops) the photo of someone who agreed to be remembered. */
    @Synchronized
    fun setFace(device: String, name: String, jpeg: ByteArray?, now: Long) {
        val c = careers[device] ?: Career(device, name, lastPlayed = now)
        careers[device] = c.copy(face = jpeg?.let { Base64.getEncoder().encodeToString(it) })
        save()
    }

    fun faceBytes(device: String): ByteArray? = get(device)?.face?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }

    @Synchronized fun forget(device: String) {
        val removed = careers.remove(device)
        // A record held by someone who erased themself goes with them
        val gone = records.filterValues { it.device == device }.keys
        gone.forEach { records.remove(it) }
        if (removed != null || gone.isNotEmpty()) save()
    }

    private fun comparator(by: RankBy) = when (by) {
        RankBy.CHIPS -> compareByDescending<Career> { it.chips }.thenBy { it.games }.thenBy { it.name.lowercase() }
        RankBy.XP -> compareByDescending<Career> { it.xp }.thenBy { it.games }.thenBy { it.name.lowercase() }
        RankBy.SEASON -> compareByDescending<Career> { it.seasonXp }.thenBy { it.games }.thenBy { it.name.lowercase() }
    }

    private fun ranked(by: RankBy) = careers.values.filter { it.games > 0 && (by != RankBy.SEASON || it.seasonXp > 0) }.sortedWith(comparator(by))

    /** Best first. Without photos. */
    @Synchronized
    fun ranking(limit: Int = 10, by: RankBy = RankBy.CHIPS): List<Career> = ranked(by).take(limit).map { it.slim() }

    @Synchronized
    fun rankOf(device: String, by: RankBy = RankBy.CHIPS): Int? = ranked(by).indexOfFirst { it.device == device }.takeIf { it >= 0 }?.plus(1)

    @Synchronized fun size() = careers.size

    // --- Records of the house ----------------------------------------------------------------------

    @Synchronized fun houseRecords(): List<HouseRecord> = records.values.sortedBy { it.gameId }

    /** Offers a result: when it beats the record (or there is none) it becomes the record. Returns the old holder's record on a break. */
    @Synchronized
    fun offer(gameId: String, key: String, value: Int, higherBetter: Boolean, device: String, name: String, now: Long): RecordBreak? {
        val id = "$gameId:$key"
        val old = records[id]
        val better = old == null || (if (higherBetter) value > old.value else value < old.value)
        if (!better) return null
        records[id] = HouseRecord(gameId, key, value, name, device, now)
        save()
        return RecordBreak(gameId, key, value, old)
    }

    // --- Seasons -------------------------------------------------------------------------------------

    /**
     * Makes [key] (like "2026-11") the current season. When it changed, the top three of the one that
     * ended get their medal, and everyone's season points start again from zero.
     */
    @Synchronized
    fun rollSeason(key: String): Boolean {
        if (season == key) return false
        val ended = season
        if (ended.isNotBlank()) {
            careers.values.filter { it.seasonKey == ended && it.seasonXp > 0 }.sortedByDescending { it.seasonXp }.take(3).forEachIndexed { i, c ->
                careers[c.device] = c.copy(medals = c.medals + "$ended:${i + 1}")
            }
        }
        careers.keys.toList().forEach { d -> careers[d] = careers.getValue(d).copy(seasonKey = key, seasonXp = 0) }
        season = key
        save()
        return true
    }

    @Synchronized fun currentSeason() = season

    private fun save() {
        while (careers.size > MAX) careers.values.minByOrNull { it.lastPlayed }?.let { careers.remove(it.device) } ?: break
        val f = file ?: return
        runCatching {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, "${f.name}.tmp")
            tmp.writeText(SocialJson.encodeToString(LedgerFile(2, careers.values.toList(), records.values.toList(), season, seen.mapValues { it.value.toList() })))
            tmp.renameTo(f)
        }
    }

    companion object {
        const val MAX = 500
        private val VALID = Regex("[A-Za-z0-9_-]{16,64}")
        /** A phone's id must look like what the page generates: 16–64 URL-safe characters. */
        fun validDevice(d: String?): Boolean = d != null && VALID.matches(d)
    }
}

/** A record that was just beaten (or set for the first time, when [old] is null). */
data class RecordBreak(val gameId: String, val key: String, val value: Int, val old: HouseRecord?)
