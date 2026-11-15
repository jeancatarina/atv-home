package dev.jean.atvhome.devotional

import java.time.LocalDate

/** The one thing the Home reminder says right now. */
sealed interface Reminder {
    /** The couple reached a milestone and has not enjoyed the reward yet. */
    data class Prize(val text: String) : Reminder
    /** [by] reminded [who], who has not checked in yet. */
    data class Called(val by: String, val who: String) : Reminder
    /** It is evening and these people have not checked in. */
    data class Missing(val names: List<String>) : Reminder
}

const val REMIND_FROM_HOUR = 18

/**
 * What the Home reminder shows, by priority: an unclaimed prize, then a reminder from the other
 * person, then (only from [fromHour] on, so the day is not nagged early) who is still missing.
 * Null means say nothing.
 */
fun Devotional.reminder(today: LocalDate, nowMs: Long, hour: Int, fromHour: Int = REMIND_FROM_HOUR): Reminder? {
    unlockedRewards().firstOrNull()?.let { return Reminder.Prize(it.second) }
    val people = snapshot().people
    people.firstNotNullOfOrNull { p -> nudgedBy(p.id, today, nowMs)?.let { Reminder.Called(it.name, p.name) } }?.let { return it }
    if (hour < fromHour) return null
    val missing = pending(today).map { it.name }
    return if (missing.isEmpty()) null else Reminder.Missing(missing)
}
