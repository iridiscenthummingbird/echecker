package ua.echecker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ua.echecker.domain.Interval
import ua.echecker.domain.KyivTime
import ua.echecker.domain.Queue
import ua.echecker.domain.ScheduleParser
import java.time.Instant
import java.time.LocalDate

/** Shape of the WordPress REST API (/wp-json/wp/v2/posts?_fields=id,date_gmt,title,content). */
@RunWith(RobolectricTestRunner::class)
class WordPressSourceTest {
    private val json = """
        [
          {"id": 371002, "date_gmt": "2026-11-12T18:40:00",
           "title": {"rendered": "ГПВ на 13 листопада"},
           "content": {"rendered": "<p>Відповідно до команди НЕК &laquo;Укренерго&raquo;, 13 листопада по Запорізькій області будуть застосовані графіки погодинних відключень (ГПВ).</p>\n<p>Години відсутності електропостачання:</p>\n<p>1.1: 13:00 &#8211; 18:00<br />\n1.2: 11:30 &#8211; 16:30<br />\n2.1: 04:00 &#8211; 09:00, 17:30 &#8211; 22:30</p>"}},
          {"id": 371001, "date_gmt": "2026-11-12T10:00:00",
           "title": {"rendered": "Новини компанії"},
           "content": {"rendered": "<p>Просто новина</p>"}},
          {"id": "bad"}
        ]
    """.trimIndent()

    private val posts = WordPressSource.parseJson(json)

    @Test fun `parses id, GMT publish time and sorts by id`() {
        assertEquals(listOf(371001L, 371002L), posts.map { it.id })
        assertEquals(Instant.parse("2026-11-12T18:40:00Z"), posts[1].publishedAt)
    }

    @Test fun `text has the title and one line per paragraph or br, entities decoded`() {
        val text = posts[1].text
        assertTrue(text, text.startsWith("ГПВ на 13 листопада\n\n"))
        assertTrue(text, text.contains("«Укренерго»"))
        assertTrue(text, text.contains("1.1: 13:00 – 18:00\n1.2: 11:30 – 16:30\n"))
    }

    @Test fun `post text is understood by ScheduleParser`() {
        val s = ScheduleParser.parse(posts[1].text, KyivTime.dateOf(posts[1].publishedAt))!!
        assertEquals(LocalDate.of(2026, 11, 13), s.date)
        assertEquals(listOf(Interval(780, 1080)), s.intervalsFor(Queue(1, 1)))
        assertEquals(listOf(Interval(240, 540), Interval(1050, 1350)), s.intervalsFor(Queue(2, 1)))
    }

    @Test fun `non-array body is a format error`() {
        assertTrue(runCatching { WordPressSource.parseJson("<html>blocked</html>") }.exceptionOrNull() is SourceFormatException)
    }

    @Test fun `source id is the bare host`() {
        assertEquals("zoe.com.ua", WordPressSource(okhttp3.OkHttpClient()).id)
    }
}
