package ua.echecker.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Time without power over the calendar days of a period that the app has history for.
 * A day without a published schedule (no outages were scheduled) counts as 0 and is still a day.
 */
data class PeriodStats(
    /** Calendar days covered: the period, cut to the start of the app's history. */
    val days: Int,
    val offMinutes: Int,
    /** Of [days], how many had no published schedule. */
    val daysWithoutSchedule: Int = 0,
) {
    val avgMinutesPerDay: Int get() = if (days == 0) 0 else Math.round(offMinutes.toDouble() / days).toInt()
}

/** One chart bar; [hasSchedule] = false for a day without a published schedule (drawn empty). */
data class DayBar(val date: LocalDate, val offMinutes: Int, val hasSchedule: Boolean)

data class HistoryStats(
    val week: PeriodStats,
    val month: PeriodStats,
    /** Every covered calendar day of the last [CHART_DAYS] days before today, oldest first. */
    val daily: List<DayBar>,
) {
    companion object {
        const val WEEK = 7
        const val MONTH = 30
        const val CHART_DAYS = 14

        /**
         * @param history known past days (before [today]) with the queue's outages.
         * @param historyStart first day the app has history for; days before it are unknown and left out.
         *   Null = the earliest day in [history].
         */
        fun compute(history: Map<LocalDate, List<Interval>>, today: LocalDate, historyStart: LocalDate? = null): HistoryStats {
            val start = historyStart ?: history.keys.minOrNull() ?: today
            fun days(n: Int): List<LocalDate> {
                val from = maxOf(today.minusDays(n.toLong()), start)
                return (0 until ChronoUnit.DAYS.between(from, today)).map { from.plusDays(it) }
            }
            fun period(n: Int): PeriodStats {
                val range = days(n)
                return PeriodStats(
                    days = range.size,
                    offMinutes = range.sumOf { history[it]?.offMinutes() ?: 0 },
                    daysWithoutSchedule = range.count { it !in history },
                )
            }
            val daily = days(CHART_DAYS).map { DayBar(it, history[it]?.offMinutes() ?: 0, it in history) }
            return HistoryStats(period(WEEK), period(MONTH), daily)
        }

        private fun List<Interval>.offMinutes() = sumOf { it.endMinute - it.startMinute }
    }
}

/** "5 год", "5 год 30 хв", "45 хв". */
fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m хв"
        m == 0 -> "$h год"
        else -> "$h год $m хв"
    }
}

/** "1 день", "3 дні", "7 днів", "11 днів", "21 день". */
fun formatDays(n: Int): String {
    val word = when {
        n % 100 in 11..14 -> "днів"
        n % 10 == 1 -> "день"
        n % 10 in 2..4 -> "дні"
        else -> "днів"
    }
    return "$n $word"
}
