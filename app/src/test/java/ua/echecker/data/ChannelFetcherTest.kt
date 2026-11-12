package ua.echecker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.echecker.domain.Interval
import ua.echecker.domain.Queue
import ua.echecker.domain.ScheduleParser
import java.time.Instant
import java.time.LocalDate

class ChannelFetcherTest {
    private val html = javaClass.classLoader!!.getResource("channel_page.html")!!.readText()
    private val posts = ChannelFetcher.parseHtml(html)

    @Test fun `skips posts without text and sorts by id`() {
        assertEquals(listOf(3570L, 3571L), posts.map { it.id })
    }

    @Test fun `uses main text not reply quote`() {
        assertEquals("За вказівкою НЕК “Укренерго” оновлено ГПВ на 11 Листопада.", posts[0].text)
    }

    @Test fun `parses publish time`() {
        assertEquals(Instant.parse("2026-11-09T16:55:31Z"), posts[1].publishedAt)
    }

    @Test fun `converts br to newlines and decodes entities`() {
        val text = posts[1].text
        assertTrue(text, text.contains("ОНОВЛЕННЯ !"))
        assertTrue(text, text.contains("Черга 1.1\n з 06:30 до 10:30\n з 14:30 до 19:00"))
    }

    @Test fun `parsed text is understood by ScheduleParser`() {
        val s = ScheduleParser.parse(posts[1].text)!!
        assertEquals(LocalDate.of(2026, 11, 9), s.date)
        assertEquals(listOf(Interval(1140, 1440)), s.intervalsFor(Queue(1, 2)))
    }
}
