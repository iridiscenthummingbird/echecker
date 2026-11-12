package ua.echecker.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.echecker.domain.KyivTime
import ua.echecker.domain.PowerStatus
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime

class WidgetRefreshSchedulerTest {
    private val now: ZonedDateTime = LocalDateTime.of(2026, 11, 12, 10, 0).atZone(KyivTime.ZONE)
    private fun at(h: Int, m: Int) = now.toLocalDate().atTime(h, m).atZone(KyivTime.ZONE)

    @Test fun `status change sooner than the tick wins`() {
        val change = at(10, 7)
        assertEquals(
            change.toInstant().plusSeconds(5),
            WidgetRefreshScheduler.nextRefreshAt(PowerStatus.On(change), now),
        )
    }

    @Test fun `far status change falls back to the 15 minute tick`() {
        assertEquals(
            now.toInstant().plus(Duration.ofMinutes(15)),
            WidgetRefreshScheduler.nextRefreshAt(PowerStatus.Off(at(17, 30)), now),
        )
    }

    @Test fun `midnight is honoured when it comes before the tick`() {
        val lateNow = at(23, 55)
        val midnight = lateNow.toLocalDate().plusDays(1).atStartOfDay(KyivTime.ZONE)
        assertEquals(
            midnight.toInstant().plusSeconds(5),
            WidgetRefreshScheduler.nextRefreshAt(PowerStatus.On(null), lateNow),
        )
    }
}
