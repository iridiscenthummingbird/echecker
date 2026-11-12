package ua.echecker.domain

import java.time.ZonedDateTime

sealed interface PowerStatus {
    /** Power is on. [nextOffAt] = start of next outage, or null if none scheduled (today/tomorrow). */
    data class On(val nextOffAt: ZonedDateTime?) : PowerStatus
    data class Off(val nextOnAt: ZonedDateTime) : PowerStatus
    data object NoScheduleToday : PowerStatus
    data object NoData : PowerStatus
    data object QueueNotSelected : PowerStatus
}

/** One continuous outage in Kyiv time; may cross midnight. */
data class OutageSpan(val start: ZonedDateTime, val end: ZonedDateTime)

object PowerStatusCalculator {
    fun calculate(
        today: Schedule?,
        tomorrow: Schedule?,
        queue: Queue?,
        now: ZonedDateTime,
        hasSynced: Boolean,
    ): PowerStatus {
        if (queue == null) return PowerStatus.QueueNotSelected
        if (today == null) return if (hasSynced) PowerStatus.NoScheduleToday else PowerStatus.NoData
        val merged = spans(today, tomorrow, queue)
        merged.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
            ?.let { return PowerStatus.Off(it.end) }
        return PowerStatus.On(merged.firstOrNull { it.start.isAfter(now) }?.start)
    }

    /**
     * Today's and tomorrow's outages for [queue], merged across midnight, in order. With [yesterday], an outage
     * that started before midnight keeps its real start.
     */
    fun spans(today: Schedule?, tomorrow: Schedule?, queue: Queue, yesterday: Schedule? = null): List<OutageSpan> {
        val spans = listOfNotNull(yesterday, today, tomorrow).flatMap { spansOf(it, queue) }.sortedBy { it.start.toInstant() }
        val merged = mutableListOf<OutageSpan>()
        for (s in spans) {
            val last = merged.lastOrNull()
            if (last != null && !s.start.isAfter(last.end)) {
                if (s.end.isAfter(last.end)) merged[merged.lastIndex] = last.copy(end = s.end)
            } else {
                merged += s
            }
        }
        return merged
    }

    private fun spansOf(s: Schedule, q: Queue): List<OutageSpan> {
        val midnight = s.date.atStartOfDay()
        return s.intervalsFor(q).map {
            OutageSpan(
                midnight.plusMinutes(it.startMinute.toLong()).atZone(KyivTime.ZONE),
                midnight.plusMinutes(it.endMinute.toLong()).atZone(KyivTime.ZONE),
            )
        }
    }
}
