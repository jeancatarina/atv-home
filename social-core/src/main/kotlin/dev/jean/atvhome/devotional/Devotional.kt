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
class Devotional(data: DevotionalData = DevotionalData(), private val onChange: (DevotionalData) -> Unit = {}) {

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
