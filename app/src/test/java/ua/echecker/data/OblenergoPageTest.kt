package ua.echecker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.echecker.domain.Interval
import ua.echecker.domain.KyivTime
import ua.echecker.domain.Queue
import ua.echecker.domain.ScheduleParser
import java.time.LocalDate

/** Real snapshot of https://t.me/s/Zaporizhzhyaoblenergo_news (backup source) taken 2026-11-12. */
class OblenergoPageTest {
    private val posts = ChannelFetcher.parseHtml(
        javaClass.classLoader!!.getResource("oblenergo_page.html")!!.readText()
    )
    private val schedules = posts.mapNotNull { ScheduleParser.parse(it.text, KyivTime.dateOf(it.publishedAt)) }

    @Test fun `extracts posts and schedules`() {
        assertTrue(posts.size > 5)
        assertTrue(schedules.isNotEmpty())
    }

    @Test fun `parses the 13 November schedule in the compact format`() {
        val s = schedules.last { it.date == LocalDate.of(2026, 11, 13) }
        assertEquals(listOf(Interval(780, 1080)), s.intervalsFor(Queue(1, 1)))
        assertEquals(listOf(Interval(240, 540), Interval(1050, 1350)), s.intervalsFor(Queue(2, 1)))
    }
}
