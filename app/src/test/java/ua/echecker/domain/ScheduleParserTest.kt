package ua.echecker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ScheduleParserTest {
    private val sample = """
        ОНОВЛЕННЯ! Графік відключення на 11.11.2026 (середа) для абонентів ЗАПОРІЖЖЯОБЛЕНЕРГО:

        ⚡ Черга 1.1
         з 15:30 до 19:30

        ⚡ Черга 1.2
         з 19:00 до 24:00

        ⚡ Черга 2.1
         з 05:30 до 07:00
         з 16:00 до 19:30
         з 23:30 до 24:00

        ⚡ Черга 2.2
         з 05:30 до 07:00
         з 16:00 до 19:30

        ⚡ Черга 3.1
         з 10:00 до 12:30

        ⚡ Черга 3.2
         з 19:00 до 22:00

        ⚡ Черга 4.1
         з 00:00 до 01:00
         з 05:30 до 10:30
         з 23:30 до 24:00

        ⚡ Черга 4.2
         з 00:00 до 01:00
         з 05:30 до 10:30
         з 23:30 до 24:00

        ⚡ Черга 5.1
         з 15:30 до 19:30

        ⚡ Черга 5.2
         з 19:00 до 24:00

        ⚡ Черга 6.1
         з 19:00 до 22:00

        ⚡ Черга 6.2
         з 10:00 до 13:30
    """.trimIndent()

    @Test fun `parses date and all queues`() {
        val s = ScheduleParser.parse(sample)!!
        assertEquals(LocalDate.of(2026, 11, 11), s.date)
        assertEquals(12, s.queues.size)
    }

    @Test fun `parses multiple intervals and 24_00`() {
        val s = ScheduleParser.parse(sample)!!
        assertEquals(
            listOf(Interval(330, 420), Interval(960, 1170), Interval(1410, 1440)),
            s.intervalsFor(Queue(2, 1))
        )
        assertEquals(listOf(Interval(600, 810)), s.intervalsFor(Queue(6, 2)))
        assertEquals(listOf(Interval(1140, 1440)), s.intervalsFor(Queue(1, 2)))
    }

    @Test fun `parses text with emoji prefix and spaced exclamation`() {
        val text = "🔥 ОНОВЛЕННЯ ! Графік відключення на 09.11.2026 (понеділок) для абонентів:\n\n⚡ Черга 1.1\n з 06:30 до 10:30\n"
        val s = ScheduleParser.parse(text)!!
        assertEquals(LocalDate.of(2026, 11, 9), s.date)
        assertEquals(listOf(Interval(390, 630)), s.intervalsFor(Queue(1, 1)))
    }

    @Test fun `returns null for non-schedule post`() {
        assertNull(ScheduleParser.parse("За вказівкою НЕК “Укренерго” оновлено ГПВ на 11 Листопада."))
    }

    @Test fun `queue without intervals is absent`() {
        val text = "Графік відключення на 11.11.2026:\n⚡ Черга 1.1\n⚡ Черга 1.2\n з 10:00 до 12:00\n"
        val s = ScheduleParser.parse(text)!!
        assertFalse(s.queues.containsKey(Queue(1, 1)))
        assertEquals(listOf(Interval(600, 720)), s.intervalsFor(Queue(1, 2)))
    }

    @Test fun `merges overlapping intervals within queue`() {
        val text = "Графік відключення на 11.11.2026:\n⚡ Черга 1.1\n з 15:30 до 19:30\n з 19:00 до 24:00\n"
        assertEquals(listOf(Interval(930, 1440)), ScheduleParser.parse(text)!!.intervalsFor(Queue(1, 1)))
    }
}
