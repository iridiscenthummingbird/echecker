package ua.echecker.domain

import java.time.LocalDate

/** Time without power over a period, counting only days whose schedule is known. */
data class PeriodStats(val days: Int, val offMinutes: Int) {
    val avgMinutesPerDay: Int get() = if (days == 0) 0 else Math.round(offMinutes.toDouble() / days).toInt()
}

data class HistoryStats(
    val week: PeriodStats,
    val month: PeriodStats,
    /** Minutes without power per known day, oldest first, for the last [CHART_DAYS] days before today. */
    val daily: List<Pair<LocalDate, Int>>,
) {
    companion object {
        const val CHART_DAYS = 14

        /** [history] = known past days (before [today]) with the queue's outages. */
        fun compute(history: Map<LocalDate, List<Interval>>, today: LocalDate): HistoryStats {
            fun period(days: Long): PeriodStats {
                val from = today.minusDays(days)
                val inRange = history.filterKeys { it >= from && it < today }
                return PeriodStats(inRange.size, inRange.values.sumOf { it.offMinutes() })
            }
            val chartFrom = today.minusDays(CHART_DAYS.toLong())
            val daily = history.filterKeys { it >= chartFrom && it < today }
                .toSortedMap()
                .map { (date, intervals) -> date to intervals.offMinutes() }
            return HistoryStats(period(7), period(30), daily)
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
