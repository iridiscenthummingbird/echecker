package ua.echecker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime

class StatusFormatterTest {
    private val now: ZonedDateTime = LocalDateTime.of(2026, 11, 11, 10, 0).atZone(KyivTime.ZONE)
    private fun t(dayOffset: Long, h: Int, m: Int) =
        now.toLocalDate().plusDays(dayOffset).atTime(h, m).atZone(KyivTime.ZONE)

    private fun fmt(s: PowerStatus, lastSyncAgo: Duration? = Duration.ofMinutes(10)) =
        StatusFormatter.format(s, now, lastSyncAgo?.let { now.toInstant().minus(it) })

    @Test fun `on with next off today`() {
        assertEquals(StatusText("💡 Світло є", "вимкнуть о 15:30", null), fmt(PowerStatus.On(t(0, 15, 30))))
    }

    @Test fun `on with next off tomorrow`() {
        assertEquals("вимкнуть завтра о 05:30", fmt(PowerStatus.On(t(1, 5, 30))).subtitle)
    }

    @Test fun `on with nothing more`() {
        assertEquals("відключень більше нема", fmt(PowerStatus.On(null)).subtitle)
    }

    @Test fun `off until midnight shows 24_00`() {
        assertEquals(StatusText("🌑 Світла нема", "увімкнуть о 24:00", null), fmt(PowerStatus.Off(t(1, 0, 0))))
    }

    @Test fun `off until tomorrow`() {
        assertEquals("увімкнуть завтра о 01:00", fmt(PowerStatus.Off(t(1, 1, 0))).subtitle)
    }

    @Test fun `off until the end of tomorrow shows tomorrow 24_00`() {
        assertEquals("увімкнуть завтра о 24:00", fmt(PowerStatus.Off(t(2, 0, 0))).subtitle)
    }

    @Test fun `other states`() {
        assertEquals(StatusText("Відключень не заплановано", null, null), fmt(PowerStatus.NoScheduleToday))
        assertEquals(StatusText("Немає даних", "відкрийте застосунок", null), fmt(PowerStatus.NoData, null))
        assertEquals(StatusText("Оберіть чергу", null, null), fmt(PowerStatus.QueueNotSelected))
    }

    @Test fun `stale note after 3 hours`() {
        assertNull(fmt(PowerStatus.On(null), Duration.ofHours(1)).staleNote)
        assertEquals("оновлено о 06:00", fmt(PowerStatus.On(null), Duration.ofHours(4)).staleNote)
        assertEquals("оновлено 09.11 10:00", fmt(PowerStatus.On(null), Duration.ofDays(2)).staleNote)
    }
}
