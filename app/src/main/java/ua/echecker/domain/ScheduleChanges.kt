package ua.echecker.domain

import java.time.LocalDate

/** A schedule for the user's queue appeared or changed during a sync. */
data class ScheduleChange(
    val date: LocalDate,
    val isToday: Boolean,
    val kind: Kind,
    val intervals: List<Interval>,
) {
    enum class Kind { PUBLISHED, CHANGED }
}

object ScheduleChanges {
    /** Compares today's and tomorrow's schedules for [queue] before and after a sync. */
    fun detect(
        today: LocalDate,
        queue: Queue,
        before: Map<LocalDate, Schedule?>,
        after: Map<LocalDate, Schedule?>,
    ): List<ScheduleChange> = listOf(today, today.plusDays(1)).mapNotNull { day ->
        val old = before[day]
        val new = after[day] ?: return@mapNotNull null
        val newIntervals = new.intervalsFor(queue)
        when {
            old == null -> ScheduleChange(day, day == today, ScheduleChange.Kind.PUBLISHED, newIntervals)
            old.intervalsFor(queue) != newIntervals ->
                ScheduleChange(day, day == today, ScheduleChange.Kind.CHANGED, newIntervals)
            else -> null
        }
    }
}
