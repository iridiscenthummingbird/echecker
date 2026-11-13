package ua.echecker.domain

import java.time.LocalDate
import java.time.ZonedDateTime

/** A moment to notify about: an outage is about to start, or power should be back. */
data class OutageAlert(val kind: Kind, val at: ZonedDateTime, val span: OutageSpan) {
    enum class Kind { SOON_OFF, BACK_ON }

    /** Identifies this alert across reschedules, so it is shown once. */
    val key: String get() = "$kind@${at.toInstant().epochSecond}"
}

object OutageAlerts {
    /**
     * Alerts after [now], soonest first.
     * @param leadMinutes how long before an outage to warn; null or 0 = no warning.
     * @param backOn also alert when an outage ends.
     * @param lastKnownDay the last day whose schedule is known: an outage running to its midnight may continue
     *   into the unknown next day, so no "power is back" alert is promised for it.
     */
    fun upcoming(
        spans: List<OutageSpan>,
        now: ZonedDateTime,
        leadMinutes: Int?,
        backOn: Boolean,
        lastKnownDay: LocalDate?,
    ): List<OutageAlert> {
        val horizon = lastKnownDay?.plusDays(1)?.atStartOfDay(KyivTime.ZONE)
        val alerts = mutableListOf<OutageAlert>()
        for (span in spans) {
            if (leadMinutes != null && leadMinutes > 0) {
                val at = span.start.minusMinutes(leadMinutes.toLong())
                if (at.isAfter(now)) alerts += OutageAlert(OutageAlert.Kind.SOON_OFF, at, span)
            }
            val openEnded = horizon != null && !span.end.isBefore(horizon)
            if (backOn && span.end.isAfter(now) && !openEnded) {
                alerts += OutageAlert(OutageAlert.Kind.BACK_ON, span.end, span)
            }
        }
        return alerts.sortedBy { it.at.toInstant() }
    }
}
