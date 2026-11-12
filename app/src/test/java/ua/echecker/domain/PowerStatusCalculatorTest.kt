package ua.echecker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

class PowerStatusCalculatorTest {
    private val day = LocalDate.of(2026, 11, 11)
    private val q11 = Queue(1, 1)
    private val q21 = Queue(2, 1)

    private val today = Schedule(
        day,
        mapOf(
            q11 to listOf(Interval(930, 1170)),                 // 15:30–19:30
            q21 to listOf(Interval(330, 420), Interval(1410, 1440)) // 05:30–07:00, 23:30–24:00
        )
    )
    private val tomorrow = Schedule(
        day.plusDays(1),
        mapOf(q21 to listOf(Interval(0, 60), Interval(330, 420)))  // 00:00–01:00, 05:30–07:00
    )

    private fun at(d: LocalDate, h: Int, m: Int): ZonedDateTime =
        LocalDateTime.of(d.year, d.month, d.dayOfMonth, h, m).atZone(KyivTime.ZONE)

    private fun calc(now: ZonedDateTime, queue: Queue? = q11, t: Schedule? = today, tm: Schedule? = null, synced: Boolean = true) =
        PowerStatusCalculator.calculate(t, tm, queue, now, synced)

    @Test fun `before outage is On with next off time`() {
        assertEquals(PowerStatus.On(at(day, 15, 30)), calc(at(day, 10, 0)))
    }

    @Test fun `during outage is Off with next on time`() {
        assertEquals(PowerStatus.Off(at(day, 19, 30)), calc(at(day, 16, 0)))
    }

    @Test fun `exact start is Off and exact end is On`() {
        assertEquals(PowerStatus.Off(at(day, 19, 30)), calc(at(day, 15, 30)))
        assertEquals(PowerStatus.On(null), calc(at(day, 19, 30)))
    }

    @Test fun `after last outage with no tomorrow is On null`() {
        assertEquals(PowerStatus.On(null), calc(at(day, 21, 0)))
    }

    @Test fun `outage spanning midnight is merged`() {
        assertEquals(
            PowerStatus.Off(at(day.plusDays(1), 1, 0)),
            calc(at(day, 23, 45), queue = q21, tm = tomorrow)
        )
    }

    @Test fun `next off can be tomorrow`() {
        val todayOnlyEarly = Schedule(day, mapOf(q21 to listOf(Interval(330, 420))))
        assertEquals(
            PowerStatus.On(at(day.plusDays(1), 0, 0)),
            calc(at(day, 12, 0), queue = q21, t = todayOnlyEarly, tm = tomorrow)
        )
    }

    @Test fun `queue absent from schedule is On null`() {
        assertEquals(PowerStatus.On(null), calc(at(day, 12, 0), queue = Queue(6, 2)))
    }

    @Test fun `no schedule today`() {
        assertEquals(PowerStatus.NoScheduleToday, calc(at(day, 12, 0), t = null, synced = true))
        assertEquals(PowerStatus.NoData, calc(at(day, 12, 0), t = null, synced = false))
    }

    @Test fun `queue not selected`() {
        assertEquals(PowerStatus.QueueNotSelected, calc(at(day, 12, 0), queue = null))
    }
}
