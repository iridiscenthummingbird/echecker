package ua.echecker.domain

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

    fun format(status: PowerStatus, now: ZonedDateTime, lastSyncAt: Instant?): StatusText {
        val stale = lastSyncAt
            ?.takeIf { Duration.between(it, now.toInstant()) > STALE_AFTER }
            ?.let {
                val t = it.atZone(now.zone)
                if (t.toLocalDate() == now.toLocalDate()) "оновлено о ${TIME.format(t)}"
                else "оновлено ${DATE_TIME.format(t)}"
            }
        return when (status) {
            is PowerStatus.On -> StatusText(
                "💡 Світло є",
                status.nextOffAt?.let { "вимкнуть ${at(it, now)}" } ?: "відключень більше нема",
                stale,
            )
            is PowerStatus.Off -> StatusText("🌑 Світла нема", "увімкнуть ${at(status.nextOnAt, now)}", stale)
            PowerStatus.NoScheduleToday -> StatusText("Відключень не заплановано", null, stale)
            PowerStatus.NoData -> StatusText("Немає даних", "відкрийте застосунок", null)
            PowerStatus.QueueNotSelected -> StatusText("Оберіть чергу", null, null)
        }
    }

    private fun at(t: ZonedDateTime, now: ZonedDateTime): String {
        val local = t.withZoneSameInstant(now.zone)
        val tomorrow = now.toLocalDate().plusDays(1)
        return when {
            local.toLocalDate() == now.toLocalDate() -> "о ${TIME.format(local)}"
            local.toLocalDate() == tomorrow && local.toLocalTime() == LocalTime.MIDNIGHT -> "о 24:00"
            else -> "завтра о ${TIME.format(local)}"
        }
    }
}
