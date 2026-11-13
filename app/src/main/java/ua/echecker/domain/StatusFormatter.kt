package ua.echecker.domain

import ua.echecker.R
import ua.echecker.i18n.Strings
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class StatusText(val title: String, val subtitle: String?, val staleNote: String?)

object StatusFormatter {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val DATE_TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm")
    private val STALE_AFTER: Duration = Duration.ofHours(3)

    fun format(status: PowerStatus, nowAnyZone: ZonedDateTime, lastSyncAt: Instant?, s: Strings): StatusText {
        // Schedules are in Kyiv time: show Kyiv wall-clock times and decide "today/tomorrow" by the Kyiv date.
        val now = nowAnyZone.withZoneSameInstant(KyivTime.ZONE)
        val stale = lastSyncAt
            ?.takeIf { Duration.between(it, now.toInstant()) > STALE_AFTER }
            ?.let {
                val t = it.atZone(now.zone)
                if (t.toLocalDate() == now.toLocalDate()) s.get(R.string.stale_today, TIME.format(t))
                else s.get(R.string.stale_date, DATE_TIME.format(t))
            }
        return when (status) {
            is PowerStatus.On -> StatusText(
                s.get(R.string.status_on_title),
                status.nextOffAt?.let { s.get(R.string.status_turns_off, at(it, now, s)) }
                    ?: s.get(R.string.status_no_more_outages),
                stale,
            )
            is PowerStatus.Off -> StatusText(
                s.get(R.string.status_off_title),
                s.get(R.string.status_turns_on, at(status.nextOnAt, now, s)),
                stale,
            )
            PowerStatus.NoScheduleToday -> StatusText(s.get(R.string.status_no_schedule), null, stale)
            PowerStatus.NoData -> StatusText(s.get(R.string.status_no_data), s.get(R.string.status_no_data_hint), null)
            PowerStatus.QueueNotSelected -> StatusText(s.get(R.string.status_pick_queue), null, null)
        }
    }

    private fun at(t: ZonedDateTime, now: ZonedDateTime, s: Strings): String {
        val local = t.withZoneSameInstant(now.zone)
        val tomorrow = now.toLocalDate().plusDays(1)
        return when {
            local.toLocalDate() == now.toLocalDate() -> s.get(R.string.at_time, TIME.format(local))
            local.toLocalDate() == tomorrow && local.toLocalTime() == LocalTime.MIDNIGHT -> s.get(R.string.at_midnight)
            local.toLocalDate() == tomorrow.plusDays(1) && local.toLocalTime() == LocalTime.MIDNIGHT ->
                s.get(R.string.at_tomorrow_midnight)
            else -> s.get(R.string.at_tomorrow_time, TIME.format(local))
        }
    }
}
