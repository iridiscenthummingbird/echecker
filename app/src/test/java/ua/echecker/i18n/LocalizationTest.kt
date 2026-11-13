package ua.echecker.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ua.echecker.data.ChannelFetcher
import ua.echecker.data.SyncIssue
import ua.echecker.domain.AlertTexts
import ua.echecker.domain.Interval
import ua.echecker.domain.KyivTime
import ua.echecker.domain.OutageAlerts
import ua.echecker.domain.PowerStatus
import ua.echecker.domain.PowerStatusCalculator
import ua.echecker.domain.Queue
import ua.echecker.domain.Schedule
import ua.echecker.domain.ScheduleChange
import ua.echecker.domain.StatusFormatter
import ua.echecker.domain.formatDays
import ua.echecker.domain.formatDuration
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class LocalizationTest {
    private val en = TestStrings.en
    private val day = LocalDate.of(2026, 11, 12)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m).atZone(KyivTime.ZONE)

    @Test fun `status in English`() {
        val text = StatusFormatter.format(PowerStatus.On(at(22)), at(10), at(5).toInstant(), en)
        assertEquals("💡 Power is on", text.title)
        assertEquals("goes off at 22:00", text.subtitle)
        assertEquals("updated at 05:00", text.staleNote)
        assertEquals("back on at midnight", StatusFormatter.format(PowerStatus.Off(day.plusDays(1).atStartOfDay(KyivTime.ZONE)), at(23), null, en).subtitle)
    }

    @Test fun `alerts and schedule changes in English`() {
        val q = Queue(3, 1)
        val spans = PowerStatusCalculator.spans(Schedule(day, mapOf(q to listOf(Interval(780, 1080)))), null, q)
        val alerts = OutageAlerts.upcoming(spans, at(10), 30, true, day)
        assertEquals("Power goes off in 30 min", AlertTexts.forAlert(alerts[0], q, en).title)
        assertEquals("Queue 3.1 · the 13:00–18:00 outage has ended per schedule", AlertTexts.forAlert(alerts[1], q, en).body)
        val change = ScheduleChange(day.plusDays(1), false, ScheduleChange.Kind.PUBLISHED, listOf(Interval(0, 180)))
        assertEquals("Schedule for tomorrow, 13.11", AlertTexts.forChange(change, q, en).title)
        assertEquals("Queue 3.1: 00:00–03:00 (3 h without power)", AlertTexts.forChange(change, q, en).body)
    }

    @Test fun `sync issues, durations and day counts in English`() {
        assertEquals("Couldn't update: the source isn't responding (HTTP 503)", SyncIssue(SyncIssue.Kind.SERVER, 503).message(en))
        assertTrue(SyncIssue(SyncIssue.Kind.FORMAT, backupSource = ChannelFetcher.OBLENERGO_CHANNEL).message(en).startsWith("Main source unavailable"))
        assertEquals("5 h 30 min", formatDuration(330, en))
        assertEquals(listOf("1 day", "2 days", "21 days"), listOf(1, 2, 21).map { formatDays(it, en) })
    }

    @Test fun `every Ukrainian string has an English translation`() {
        fun names(path: String) = Regex("""<(string|plurals) name="([^"]+)"""").findAll(File(path).readText()).map { it.groupValues[2] }.toSet()
        // Unit tests run with the module directory as the working directory.
        val uk = names("src/main/res/values/strings.xml")
        val english = names("src/main/res/values-en/strings.xml")
        assertTrue(uk.size > 50)
        assertEquals("missing in values-en", emptySet<String>(), uk - english)
        assertEquals("only in values-en", emptySet<String>(), english - uk)
    }
}
