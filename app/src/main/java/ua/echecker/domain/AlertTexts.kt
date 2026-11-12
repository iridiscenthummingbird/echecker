package ua.echecker.domain

import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Notification title and body (Ukrainian). */
data class NotificationText(val title: String, val body: String)

object AlertTexts {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val DATE = DateTimeFormatter.ofPattern("dd.MM")

    fun forAlert(alert: OutageAlert, queue: Queue): NotificationText {
        val range = range(alert.span)
        return when (alert.kind) {
            OutageAlert.Kind.SOON_OFF -> {
                val lead = Duration.between(alert.at, alert.span.start).toMinutes().toInt()
                NotificationText("Через ${formatDuration(lead)} вимкнуть світло", "Черга $queue · без світла $range")
            }
            OutageAlert.Kind.BACK_ON ->
                NotificationText("Світло має повернутися", "Черга $queue · відключення $range закінчилось за графіком")
        }
    }

    fun forChange(change: ScheduleChange, queue: Queue): NotificationText {
        val day = if (change.isToday) "сьогодні" else "завтра"
        val title = when (change.kind) {
            ScheduleChange.Kind.PUBLISHED -> "Графік на $day, ${DATE.format(change.date)}"
            ScheduleChange.Kind.CHANGED -> "Графік на $day змінився"
        }
        val body = if (change.intervals.isEmpty()) {
            "Черга $queue: без відключень"
        } else {
            val total = change.intervals.sumOf { it.endMinute - it.startMinute }
            "Черга $queue: ${change.intervals.joinToString(", ") { it.format() }} (без світла ${formatDuration(total)})"
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
