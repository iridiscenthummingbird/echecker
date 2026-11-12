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

object PowerStatusCalculator {
    private data class Span(val start: ZonedDateTime, val end: ZonedDateTime)

    fun calculate(
        today: Schedule?,
        tomorrow: Schedule?,
        queue: Queue?,
        now: ZonedDateTime,
        hasSynced: Boolean,
    ): PowerStatus {
        if (queue == null) return PowerStatus.QueueNotSelected
        if (today == null) return if (hasSynced) PowerStatus.NoScheduleToday else PowerStatus.NoData

        val spans = (spansOf(today, queue) + (tomorrow?.let { spansOf(it, queue) } ?: emptyList()))
            .sortedBy { it.start.toInstant() }
        val merged = mutableListOf<Span>()
        for (s in spans) {
            val last = merged.lastOrNull()
            if (last != null && !s.start.isAfter(last.end)) {
                if (s.end.isAfter(last.end)) merged[merged.lastIndex] = last.copy(end = s.end)
            } else {
                merged += s
            }
        }

        merged.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
            ?.let { return PowerStatus.Off(it.end) }
        return PowerStatus.On(merged.firstOrNull { it.start.isAfter(now) }?.start)
    }

    private fun spansOf(s: Schedule, q: Queue): List<Span> {
        val midnight = s.date.atStartOfDay()
        return s.intervalsFor(q).map {
            Span(
                midnight.plusMinutes(it.startMinute.toLong()).atZone(KyivTime.ZONE),
                midnight.plusMinutes(it.endMinute.toLong()).atZone(KyivTime.ZONE),
            )
        }
    }
}
