package ua.echecker.domain

import ua.echecker.R
import ua.echecker.i18n.Strings
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Notification title and body. */
data class NotificationText(val title: String, val body: String)

object AlertTexts {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val DATE = DateTimeFormatter.ofPattern("dd.MM")

    fun forAlert(alert: OutageAlert, queue: Queue, s: Strings): NotificationText {
        val range = range(alert.span)
        return when (alert.kind) {
            OutageAlert.Kind.SOON_OFF -> {
                val lead = Duration.between(alert.at, alert.span.start).toMinutes().toInt()
                NotificationText(
                    s.get(R.string.alert_soon_title, formatDuration(lead, s)),
                    s.get(R.string.alert_soon_body, queue.toString(), range),
                )
            }
            OutageAlert.Kind.BACK_ON -> NotificationText(
                s.get(R.string.alert_back_title),
                s.get(R.string.alert_back_body, queue.toString(), range),
            )
        }
    }

    fun forChange(change: ScheduleChange, queue: Queue, s: Strings): NotificationText {
        val date = DATE.format(change.date)
        val title = when (change.kind) {
            ScheduleChange.Kind.PUBLISHED ->
                s.get(if (change.isToday) R.string.change_published_today else R.string.change_published_tomorrow, date)
            ScheduleChange.Kind.CHANGED ->
                s.get(if (change.isToday) R.string.change_changed_today else R.string.change_changed_tomorrow)
        }
        val body = if (change.intervals.isEmpty()) {
            s.get(R.string.change_body_none, queue.toString())
        } else {
            val total = change.intervals.sumOf { it.endMinute - it.startMinute }
            s.get(
                R.string.change_body,
                queue.toString(),
                change.intervals.joinToString(", ") { it.format() },
                formatDuration(total, s),
            )
        }
        return NotificationText(title, body)
    }

    private fun range(span: OutageSpan) = "${TIME.format(span.start)}–${end(span)}"

    /** 24:00 for an outage that ends at the midnight after it started. */
    private fun end(span: OutageSpan): String {
        val e: ZonedDateTime = span.end
        val endsAtNextMidnight = e.toLocalTime() == LocalTime.MIDNIGHT && e.toLocalDate() == span.start.toLocalDate().plusDays(1)
        return if (endsAtNextMidnight) "24:00" else TIME.format(e)
    }
}
