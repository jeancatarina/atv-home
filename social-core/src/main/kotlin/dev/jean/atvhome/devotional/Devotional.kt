package dev.jean.atvhome.devotional

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

@Serializable
data class Person(val id: String, val name: String)

/** Everything that is saved: who is in the house and the days each one did the devotional. */
@Serializable
data class DevotionalData(
    val people: List<Person> = emptyList(),
    /** person id -> ISO dates (yyyy-MM-dd) done. */
    val done: Map<String, Set<String>> = emptyMap(),
)

data class Standing(
    val person: Person,
    val streak: Int,
    val best: Int,
    val total: Int,
    val last30: Int,
    val doneToday: Boolean,
)

/**
 * The rules of the devotional board, with no clock and no disk: [today] always comes from the caller.
 * A streak survives until the end of the next day, so someone who has not checked in yet today keeps
 * yesterday's streak.
 */
class Devotional(
    data: DevotionalData = DevotionalData(),
    /** Called after a nudge, which is not saved: a screen open on the board redraws. */
    private val onEvent: () -> Unit = {},
    private val onChange: (DevotionalData) -> Unit = {},
) {

    private var data = data

    @Synchronized fun snapshot(): DevotionalData = data

    @Synchronized
    fun addPerson(name: String): Person? {
        val clean = name.trim().take(NAME_MAX)
        if (clean.isEmpty() || data.people.size >= MAX_PEOPLE) return null
        if (data.people.any { it.name.equals(clean, ignoreCase = true) }) return null
        val p = Person("p${(data.people.mapNotNull { it.id.drop(1).toIntOrNull() }.maxOrNull() ?: 0) + 1}", clean)
        change(data.copy(people = data.people + p))
        return p
    }

    @Synchronized
    fun removePerson(id: String) {
        if (data.people.none { it.id == id }) return
        change(DevotionalData(data.people.filter { it.id != id }, data.done - id))
    }

    /** Marks or unmarks [date]. Only today and the two days before are allowed. Returns false when refused. */
    @Synchronized
    fun mark(id: String, date: LocalDate, today: LocalDate, done: Boolean): Boolean {
        if (data.people.none { it.id == id }) return false
        if (date.isAfter(today) || date.isBefore(today.minusDays(GRACE_DAYS))) return false
        val days = data.done[id].orEmpty()
        val key = date.toString()
        val next = if (done) days + key else days - key
        if (next != days) change(data.copy(done = data.done + (id to next)))
        return true
    }

    @Synchronized
    fun daysOf(id: String): Set<LocalDate> = data.done[id].orEmpty().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()

    @Synchronized
    fun standings(today: LocalDate): List<Standing> = data.people.map { standing(it, today) }
        .sortedWith(compareByDescending<Standing> { it.streak }.thenByDescending { it.last30 }.thenByDescending { it.total }.thenBy { it.person.name })

    /** Who has not done it on [today] yet, in the order they joined. */
    @Synchronized
    fun pending(today: LocalDate): List<Person> = data.people.filter { today !in daysOf(it.id) }

    /**
     * Days in a row that everyone did it (two people or more): the couple's own streak. Like a single
     * streak it survives until the end of the next day, so it does not drop while the day is still open.
     */
    @Synchronized
    fun together(today: LocalDate): Int {
        if (data.people.size < 2) return 0
        val all = data.people.map { daysOf(it.id) }
        fun everyone(d: LocalDate) = all.all { d in it }
        var cursor = if (everyone(today)) today else today.minusDays(1)
        var n = 0
        while (everyone(cursor)) { n++; cursor = cursor.minusDays(1) }
        return n
    }

    /** Points of one person: each day counts more inside a run, and days everyone did earn a bonus. */
    @Synchronized
    fun points(id: String): Int {
        val everyone = if (data.people.size >= 2) data.people.map { daysOf(it.id) }.reduce { a, b -> a intersect b } else null
        return Game.points(daysOf(id), everyone)
    }

    /** The couple's points together: what the tree grows with. */
    @Synchronized
    fun couplePoints(): Int = data.people.sumOf { points(it.id) }

    /** Days done in the week that began on Monday, up to [today]. */
    @Synchronized
    fun weekCount(id: String, today: LocalDate): Int {
        val from = Game.weekStart(today)
        return daysOf(id).count { !it.isBefore(from) && !it.isAfter(today) }
    }

    /** The week's duel: the one person ahead, or null when it is a tie or nobody has done a day. */
    @Synchronized
    fun weekLeader(today: LocalDate): Person? {
        if (data.people.size < 2) return null
        val counts = data.people.map { it to weekCount(it.id, today) }
        val top = counts.maxOf { it.second }
        return counts.filter { it.second == top }.takeIf { top > 0 && it.size == 1 }?.single()?.first
    }

    private val nudges = HashMap<String, Pair<String, Long>>()

    /**
     * [from] calls [to] for the devotional. Refused when it is the same person, when [to] already did
     * today, or when [from] already called in the last [NUDGE_COOLDOWN_MS]. Kept in memory only.
     */
    @Synchronized
    fun nudge(from: String, to: String, today: LocalDate, now: Long): Boolean {
        if (from == to || data.people.none { it.id == from } || data.people.none { it.id == to }) return false
        if (today in daysOf(to)) return false
        val last = nudges[to]
        if (last != null && last.first == from && now - last.second < NUDGE_COOLDOWN_MS) return false
        nudges[to] = from to now
        onEvent()
        return true
    }

    /** Who called [id] and is still waiting: within [NUDGE_LIFETIME_MS] and while [id] has not done today. */
    @Synchronized
    fun nudgedBy(id: String, today: LocalDate, now: Long): Person? {
        val n = nudges[id] ?: return null
        if (now - n.second > NUDGE_LIFETIME_MS || today in daysOf(id)) return null
        return data.people.firstOrNull { it.id == n.first }
    }

    private fun standing(p: Person, today: LocalDate): Standing {
        val days = daysOf(p.id)
        var cursor = if (today in days) today else today.minusDays(1)
        var streak = 0
        while (cursor in days) { streak++; cursor = cursor.minusDays(1) }
        return Standing(p, streak, best(days), days.size, days.count { !it.isBefore(today.minusDays(29)) && !it.isAfter(today) }, today in days)
    }

    private fun best(days: Set<LocalDate>): Int {
        var best = 0
        for (d in days) {
            if (d.minusDays(1) in days) continue
            var n = 0; var c = d
            while (c in days) { n++; c = c.plusDays(1) }
            best = maxOf(best, n)
        }
        return best
    }

    private fun change(next: DevotionalData) { data = next; onChange(next) }

    companion object {
        const val MAX_PEOPLE = 4
        const val NAME_MAX = 20
        const val GRACE_DAYS = 2L
        const val NUDGE_COOLDOWN_MS = 10 * 60_000L
        const val NUDGE_LIFETIME_MS = 3 * 3_600_000L
    }
}

/** The board's file: written whole through a temporary file, so a power cut never leaves half of it. */
class DevotionalStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): DevotionalData = runCatching { json.decodeFromString<DevotionalData>(file.readText()) }.getOrDefault(DevotionalData())

    @Synchronized
    fun save(data: DevotionalData) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.encodeToString(data))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }
}
