package ua.echecker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.echecker.domain.Interval
import ua.echecker.domain.KyivTime
import ua.echecker.domain.Queue
import ua.echecker.domain.ScheduleParser
import java.time.LocalDate

/** Real snapshot of https://t.me/s/zoe_alarm taken 2026-11-12. */
class ZoeAlarmPageTest {
    private val posts = ChannelFetcher.parseHtml(
        javaClass.classLoader!!.getResource("zoe_alarm_page.html")!!.readText()
    )
    private val schedules = posts.mapNotNull { ScheduleParser.parse(it.text, KyivTime.dateOf(it.publishedAt)) }

    private fun t(h: Int, m: Int = 0) = h * 60 + m

    @Test fun `extracts posts from the page`() {
        assertTrue(posts.size > 5)
    }

    @Test fun `outage notices and file posts are not schedules`() {
        assertTrue(schedules.all { it.queues.size == 12 })
    }

    @Test fun `parses the 13 November schedule`() {
        val s = schedules.last { it.date == LocalDate.of(2026, 11, 13) }
        assertEquals(listOf(Interval(t(13), t(18))), s.intervalsFor(Queue(1, 1)))
        assertEquals(listOf(Interval(t(4), t(9)), Interval(t(17, 30), t(22, 30))), s.intervalsFor(Queue(2, 1)))
        assertEquals(
            listOf(Interval(t(0), t(3)), Interval(t(7), t(12)), Interval(t(20, 30), t(24))),
            s.intervalsFor(Queue(6, 2)),
        )
    }

    @Test fun `parses the updated 12 November schedule`() {
        val s = schedules.last { it.date == LocalDate.of(2026, 11, 12) }
        assertEquals(listOf(Interval(t(7), t(9)), Interval(t(17, 30), t(22, 30))), s.intervalsFor(Queue(1, 2)))
        assertEquals(listOf(Interval(t(14, 30), t(19, 30)), Interval(t(23, 30), t(24))), s.intervalsFor(Queue(4, 1)))
    }
}
