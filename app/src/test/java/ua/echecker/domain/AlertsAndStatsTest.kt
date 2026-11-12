package ua.echecker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class AlertsAndStatsTest {
    private val q = Queue(3, 1)
    private val today = LocalDate.of(2026, 11, 12)
    private val tomorrow = today.plusDays(1)
    private fun t(h: Int, m: Int = 0) = h * 60 + m
    private fun at(day: LocalDate, h: Int, m: Int = 0): ZonedDateTime = day.atTime(h, m).atZone(KyivTime.ZONE)
    private fun sched(day: LocalDate, vararg iv: Interval) = Schedule(day, mapOf(q to iv.toList()))

    // ---- spans ----

    @Test fun `outage crossing midnight is one span`() {
        val spans = PowerStatusCalculator.spans(
            sched(today, Interval(t(22), t(24))), sched(tomorrow, Interval(t(0), t(3)), Interval(t(11, 30), t(16, 30))), q,
        )
        assertEquals(
            listOf(OutageSpan(at(today, 22), at(tomorrow, 3)), OutageSpan(at(tomorrow, 11, 30), at(tomorrow, 16, 30))),
            spans,
        )
    }

    @Test fun `after midnight an outage keeps its start from yesterday`() {
        val spans = PowerStatusCalculator.spans(
            sched(tomorrow, Interval(t(0), t(3))), null, q, yesterday = sched(today, Interval(t(22), t(24))),
        )
        assertEquals(listOf(OutageSpan(at(today, 22), at(tomorrow, 3))), spans)
        val alert = OutageAlerts.upcoming(spans, at(tomorrow, 1), null, true, tomorrow).single()
        assertEquals("Черга 3.1 · відключення 22:00–03:00 закінчилось за графіком", AlertTexts.forAlert(alert, q).body)
    }

    // ---- alerts ----

    private val spans = PowerStatusCalculator.spans(
        sched(today, Interval(t(13), t(18)), Interval(t(22), t(24))), null, q,
    )

    @Test fun `warns lead minutes before each outage and when each ends`() {
        val alerts = OutageAlerts.upcoming(spans, at(today, 10), leadMinutes = 30, backOn = true, lastKnownDay = tomorrow)
        assertEquals(
            listOf(
                OutageAlert.Kind.SOON_OFF to at(today, 12, 30),
                OutageAlert.Kind.BACK_ON to at(today, 18),
                OutageAlert.Kind.SOON_OFF to at(today, 21, 30),
                OutageAlert.Kind.BACK_ON to at(tomorrow, 0),
            ),
            alerts.map { it.kind to it.at },
        )
    }

    @Test fun `no back-on alert for an outage running into an unknown day`() {
        val alerts = OutageAlerts.upcoming(spans, at(today, 19), leadMinutes = 30, backOn = true, lastKnownDay = today)
        assertEquals(listOf(OutageAlert.Kind.SOON_OFF to at(today, 21, 30)), alerts.map { it.kind to it.at })
    }

    @Test fun `past and disabled alerts are left out`() {
        // 12:45: the 13:00 warning (12:30) has passed; during the outage only its end is left.
        val alerts = OutageAlerts.upcoming(spans, at(today, 12, 45), leadMinutes = null, backOn = true, lastKnownDay = tomorrow)
        assertEquals(listOf(at(today, 18), at(tomorrow, 0)), alerts.map { it.at })
        assertTrue(OutageAlerts.upcoming(spans, at(today, 10), leadMinutes = 0, backOn = false, lastKnownDay = tomorrow).isEmpty())
    }

    @Test fun `alert texts`() {
        val soon = OutageAlerts.upcoming(spans, at(today, 10), 30, true, tomorrow)
        assertEquals(
            NotificationText("Через 30 хв вимкнуть світло", "Черга 3.1 · без світла 13:00–18:00"),
            AlertTexts.forAlert(soon[0], q),
        )
        assertEquals(
            NotificationText("Світло має повернутися", "Черга 3.1 · відключення 22:00–24:00 закінчилось за графіком"),
            AlertTexts.forAlert(soon[3], q),
        )
        val hour = OutageAlerts.upcoming(spans, at(today, 10), 60, false, tomorrow)
        assertEquals("Через 1 год вимкнуть світло", AlertTexts.forAlert(hour[0], q).title)
    }

    // ---- schedule changes ----

    @Test fun `detects tomorrow published and today changed for the queue only`() {
        val other = Queue(1, 1)
        val before = mapOf(
            today to Schedule(today, mapOf(q to listOf(Interval(t(13), t(18))), other to listOf(Interval(t(1), t(2))))),
            tomorrow to null,
        )
        val after = mapOf(
            today to Schedule(today, mapOf(q to listOf(Interval(t(14), t(18))), other to listOf(Interval(t(1), t(2))))),
            tomorrow to sched(tomorrow, Interval(t(0), t(3))),
        )
        val changes = ScheduleChanges.detect(today, q, before, after)
        assertEquals(
            listOf(
                ScheduleChange(today, true, ScheduleChange.Kind.CHANGED, listOf(Interval(t(14), t(18)))),
                ScheduleChange(tomorrow, false, ScheduleChange.Kind.PUBLISHED, listOf(Interval(t(0), t(3)))),
            ),
            changes,
        )
        assertEquals(
            NotificationText("Графік на завтра, 13.11", "Черга 3.1: 00:00–03:00 (без світла 3 год)"),
            AlertTexts.forChange(changes[1], q),
        )
        assertEquals("Графік на сьогодні змінився", AlertTexts.forChange(changes[0], q).title)
    }

    @Test fun `a change in another queue is not reported`() {
        val other = Queue(1, 1)
        val before = mapOf(today to Schedule(today, mapOf(q to listOf(Interval(t(13), t(18))))))
        val after = mapOf(today to Schedule(today, mapOf(q to listOf(Interval(t(13), t(18))), other to listOf(Interval(t(1), t(2))))))
        assertTrue(ScheduleChanges.detect(today, q, before, after).isEmpty())
    }

    @Test fun `cancelled outages read as no outages`() {
        val change = ScheduleChange(tomorrow, false, ScheduleChange.Kind.CHANGED, emptyList())
        assertEquals("Черга 3.1: без відключень", AlertTexts.forChange(change, q).body)
    }

    // ---- history stats ----

    @Test fun `weekly and monthly totals count known days only`() {
        val history = mapOf(
            today.minusDays(1) to listOf(Interval(t(10), t(15))),          // 5 h
            today.minusDays(3) to listOf(Interval(t(0), t(4)), Interval(t(20), t(22, 30))), // 6.5 h
            today.minusDays(10) to listOf(Interval(t(0), t(10))),          // 10 h, outside the week
            today.minusDays(40) to listOf(Interval(t(0), t(10))),          // outside the month
        )
        val s = HistoryStats.compute(history, today)
        assertEquals(PeriodStats(2, t(11, 30)), s.week)
        assertEquals(t(5, 45), s.week.avgMinutesPerDay)
        assertEquals(PeriodStats(3, t(21, 30)), s.month)
        assertEquals(
            listOf(today.minusDays(10) to t(10), today.minusDays(3) to t(6, 30), today.minusDays(1) to t(5)),
            s.daily,
        )
    }

    @Test fun `durations`() {
        assertEquals("45 хв", formatDuration(45))
        assertEquals("5 год", formatDuration(300))
        assertEquals("5 год 30 хв", formatDuration(330))
    }
}
