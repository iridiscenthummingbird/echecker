package ua.echecker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ScheduleParserTest {
    private val REF = LocalDate.of(2026, 11, 11)
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
        val s = ScheduleParser.parse(sample, REF)!!
        assertEquals(LocalDate.of(2026, 11, 11), s.date)
        assertEquals(12, s.queues.size)
    }

    @Test fun `parses multiple intervals and 24_00`() {
        val s = ScheduleParser.parse(sample, REF)!!
        assertEquals(
            listOf(Interval(330, 420), Interval(960, 1170), Interval(1410, 1440)),
            s.intervalsFor(Queue(2, 1))
        )
        assertEquals(listOf(Interval(600, 810)), s.intervalsFor(Queue(6, 2)))
        assertEquals(listOf(Interval(1140, 1440)), s.intervalsFor(Queue(1, 2)))
    }

    @Test fun `parses text with emoji prefix and spaced exclamation`() {
        val text = "🔥 ОНОВЛЕННЯ ! Графік відключення на 09.11.2026 (понеділок) для абонентів:\n\n⚡ Черга 1.1\n з 06:30 до 10:30\n"
        val s = ScheduleParser.parse(text, LocalDate.of(2026, 11, 9))!!
        assertEquals(LocalDate.of(2026, 11, 9), s.date)
        assertEquals(listOf(Interval(390, 630)), s.intervalsFor(Queue(1, 1)))
    }

    @Test fun `returns null for non-schedule post`() {
        assertNull(ScheduleParser.parse("За вказівкою НЕК “Укренерго” оновлено ГПВ на 11 Листопада.", REF))
    }

    @Test fun `queue without intervals is absent`() {
        val text = "Графік відключення на 11.11.2026:\n⚡ Черга 1.1\n⚡ Черга 1.2\n з 10:00 до 12:00\n"
        val s = ScheduleParser.parse(text, REF)!!
        assertFalse(s.queues.containsKey(Queue(1, 1)))
        assertEquals(listOf(Interval(600, 720)), s.intervalsFor(Queue(1, 2)))
    }

    @Test fun `merges overlapping intervals within queue`() {
        val text = "Графік відключення на 11.11.2026:\n⚡ Черга 1.1\n з 15:30 до 19:30\n з 19:00 до 24:00\n"
        assertEquals(listOf(Interval(930, 1440)), ScheduleParser.parse(text, REF)!!.intervalsFor(Queue(1, 1)))
    }

    private fun fixture(id: Int) =
        javaClass.classLoader!!.getResource("posts/post_$id.txt")!!.readText()

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test fun `post 3587 block layout with month name`() {
        val s = ScheduleParser.parse(fixture(3587), d(2026, 11, 11))!!
        assertEquals(d(2026, 11, 11), s.date)
        assertEquals(12, s.queues.size)
        assertEquals(
            listOf(Interval(330, 420), Interval(960, 1170), Interval(1410, 1440)),
            s.intervalsFor(Queue(2, 2))
        )
    }

    @Test fun `post 3588 footer is not attributed to a queue`() {
        val s = ScheduleParser.parse(fixture(3588), d(2026, 11, 11))!!
        assertEquals(d(2026, 11, 12), s.date)
        assertEquals(
            listOf(Interval(240, 540), Interval(780, 1080), Interval(1320, 1440)),
            s.intervalsFor(Queue(6, 2))
        )
        assertEquals(listOf(Interval(780, 1080)), s.intervalsFor(Queue(1, 1)))
    }

    @Test fun `post 3550 compact layout`() {
        val s = ScheduleParser.parse(fixture(3550), d(2026, 11, 5))!!
        assertEquals(d(2026, 11, 5), s.date)
        assertEquals(
            listOf(Interval(0, 300), Interval(540, 840), Interval(1080, 1350)),
            s.intervalsFor(Queue(1, 1))
        )
        assertEquals(listOf(Interval(270, 570), Interval(810, 1110)), s.intervalsFor(Queue(2, 2)))
        assertEquals(12, s.queues.size)
    }

    @Test fun `post 3548 tomorrow compact`() {
        val s = ScheduleParser.parse(fixture(3548), d(2026, 11, 4))!!
        assertEquals(d(2026, 11, 5), s.date)
        assertEquals(12, s.queues.size)
    }

    @Test fun `post 3572 compact`() {
        val s = ScheduleParser.parse(fixture(3572), d(2026, 11, 9))!!
        assertEquals(d(2026, 11, 10), s.date)
        assertEquals(listOf(Interval(600, 900), Interval(1140, 1440)), s.intervalsFor(Queue(1, 1)))
    }

    @Test fun `post 3562 block`() {
        val s = ScheduleParser.parse(fixture(3562), d(2026, 11, 8))!!
        assertEquals(d(2026, 11, 8), s.date)
        assertEquals(listOf(Interval(0, 300), Interval(540, 810)), s.intervalsFor(Queue(1, 1)))
    }

    @Test fun `post 3567 block without leading space`() {
        val s = ScheduleParser.parse(fixture(3567), d(2026, 11, 9))!!
        assertEquals(d(2026, 11, 9), s.date)
        assertEquals(listOf(Interval(390, 630), Interval(870, 1170)), s.intervalsFor(Queue(1, 1)))
    }

    @Test fun `post 3589 numeric format`() {
        val s = ScheduleParser.parse(fixture(3589), d(2026, 11, 11))!!
        assertEquals(d(2026, 11, 11), s.date)
        assertEquals(12, s.queues.size)
    }

    @Test fun `post 3549 news is not a schedule`() {
        assertNull(ScheduleParser.parse(fixture(3549), d(2026, 11, 5)))
    }

    @Test fun `year inference across new year`() {
        assertEquals(
            d(2027, 1, 2),
            ScheduleParser.parse("оновлено ГПВ на 02 січня\n1.1: 10:00 – 12:00", d(2026, 12, 31))!!.date
        )
        assertEquals(
            d(2026, 12, 31),
            ScheduleParser.parse("оновлено ГПВ на 31 грудня\n1.1: 10:00 – 12:00", d(2027, 1, 1))!!.date
        )
    }

    @Test fun `invalid minutes are skipped`() {
        val text = "на 11 листопада\n⚡ Черга 1.1\n з 10:75 до 12:00\n⚡ Черга 1.2\n з 10:00 до 12:00\n"
        val s = ScheduleParser.parse(text, REF)!!
        assertFalse(s.queues.containsKey(Queue(1, 1)))
        assertEquals(listOf(Interval(600, 720)), s.intervalsFor(Queue(1, 2)))
    }

    @Test fun `header only text is null`() {
        assertNull(ScheduleParser.parse("Графік відключення на 11.11.2026:\n⚡ Черга 1.1\n", REF))
    }

    @Test fun `skips an implausible earlier date and picks the schedule date`() {
        val text = "З 03 листопада діють ГПВ. Графік на сьогодні, 12 листопада:\n1.1: 10:00 – 12:00"
        assertEquals(d(2026, 11, 12), ScheduleParser.parse(text, d(2026, 11, 12))!!.date)
    }

    @Test fun `schedule dated far from the reference date is null`() {
        assertNull(ScheduleParser.parse("Графік відключення на 12.12.2026:\n1.1: 10:00 – 12:00", d(2026, 11, 12)))
        assertNull(ScheduleParser.parse("оновлено ГПВ на 12 грудня\n1.1: 10:00 – 12:00", d(2026, 11, 12)))
    }

    @Test fun `year digits are not read as a named-date day`() {
        // "2026 листопада" must not yield day 26 (which would be plausible here); the real date is 25 листопада.
        val text = "Звернення 2026 листопада. ГПВ на 25 листопада\n1.1: 10:00 – 12:00"
        assertEquals(d(2026, 11, 25), ScheduleParser.parse(text, d(2026, 11, 25))!!.date)
    }
}
