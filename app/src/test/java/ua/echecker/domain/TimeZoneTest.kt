package ua.echecker.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ua.echecker.i18n.TestStrings
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone

/**
 * The channel publishes schedules in Kyiv / Zaporizhzhia (Ukraine) local time.
 * Everything must be computed in Europe/Kyiv no matter what time zone the phone is set to.
 */
@RunWith(RobolectricTestRunner::class)
class TimeZoneTest {
    private val originalDefault: TimeZone = TimeZone.getDefault()
    private val q11 = Queue(1, 1)

    @After fun restoreDefaultZone() = TimeZone.setDefault(originalDefault)

    private fun withDeviceZone(id: String, block: () -> Unit) {
        TimeZone.setDefault(TimeZone.getTimeZone(id))
        try { block() } finally { TimeZone.setDefault(originalDefault) }
    }

    private fun kyiv(date: LocalDate, h: Int, m: Int): ZonedDateTime =
        LocalDateTime.of(date, LocalTime.of(h, m)).atZone(KyivTime.ZONE)

    // --- Zone definition ---------------------------------------------------------------

    @Test fun `zone is Kyiv with Ukrainian offsets`() {
        assertTrue(KyivTime.ZONE.id in setOf("Europe/Kyiv", "Europe/Kiev"))
        assertEquals(ZoneOffset.ofHours(2), KyivTime.ZONE.rules.getOffset(Instant.parse("2026-01-15T12:00:00Z")))
        assertEquals(ZoneOffset.ofHours(3), KyivTime.ZONE.rules.getOffset(Instant.parse("2026-07-15T12:00:00Z")))
    }

    // --- Independence from the device time zone ------------------------------------------

    private val deviceZones = listOf("America/New_York", "Asia/Tokyo", "UTC", "Pacific/Kiritimati")

    @Test fun `post date is the Kyiv calendar date, not UTC or device date`() {
        // 22:30 UTC on 11.11 is already 00:30 on 12.11 in Kyiv.
        for (zone in deviceZones) withDeviceZone(zone) {
            assertEquals(zone, LocalDate.of(2026, 11, 12), KyivTime.dateOf(Instant.parse("2026-11-11T22:30:00Z")))
        }
    }

    @Test fun `status is the same whatever zone the phone and the given now are in`() {
        val day = LocalDate.of(2026, 11, 11)
        val today = Schedule(day, mapOf(q11 to listOf(Interval(930, 1170)))) // 15:30–19:30 Kyiv
        val nowInstant = kyiv(day, 16, 0).toInstant()
        for (zone in deviceZones) withDeviceZone(zone) {
            val status = PowerStatusCalculator.calculate(
                today, null, q11, nowInstant.atZone(ZoneId.of(zone)), hasSynced = true,
            )
            assertEquals(zone, PowerStatus.Off(kyiv(day, 19, 30)).nextOnAt.toInstant(), (status as PowerStatus.Off).nextOnAt.toInstant())
        }
    }

    @Test fun `widget text shows Kyiv wall-clock times even if now is in another zone`() {
        val day = LocalDate.of(2026, 11, 11)
        val nowInstant = kyiv(day, 16, 0).toInstant()
        for (zone in deviceZones) withDeviceZone(zone) {
            val text = StatusFormatter.format(
                PowerStatus.Off(kyiv(day, 19, 30)),
                nowInstant.atZone(ZoneId.of(zone)),
                lastSyncAt = nowInstant.minusSeconds(4 * 3600),
                s = TestStrings.uk,
            )
            assertEquals(zone, "увімкнуть о 19:30", text.subtitle)
            assertEquals(zone, "оновлено о 12:00", text.staleNote)
        }
    }

    @Test fun `today vs tomorrow is decided by the Kyiv date`() {
        // 23:30 Kyiv on 11.11 = 21:30 UTC = 06:30 on 12.11 in Tokyo; next change 01:00 Kyiv on 12.11 is "tomorrow".
        val day = LocalDate.of(2026, 11, 11)
        val now = kyiv(day, 23, 30).withZoneSameInstant(ZoneId.of("Asia/Tokyo"))
        val text = StatusFormatter.format(PowerStatus.Off(kyiv(day.plusDays(1), 1, 0)), now, null, TestStrings.uk)
        assertEquals("увімкнуть завтра о 01:00", text.subtitle)
    }

    // --- Daylight saving transitions (Ukraine: last Sunday of March / October) -----------

    @Test fun `spring forward day keeps schedule wall-clock times`() {
        val day = LocalDate.of(2026, 3, 29) // 03:00 -> 04:00
        val schedule = Schedule(day, mapOf(q11 to listOf(Interval(120, 300)))) // 02:00–05:00
        val status = PowerStatusCalculator.calculate(schedule, null, q11, kyiv(day, 2, 30), hasSynced = true)
        val nextOn = (status as PowerStatus.Off).nextOnAt.withZoneSameInstant(KyivTime.ZONE)
        assertEquals(LocalDateTime.of(day, LocalTime.of(5, 0)), nextOn.toLocalDateTime())
    }

    @Test fun `fall back day keeps schedule wall-clock times`() {
        val day = LocalDate.of(2026, 10, 25) // 04:00 -> 03:00
        val schedule = Schedule(day, mapOf(q11 to listOf(Interval(120, 300)))) // 02:00–05:00
        val afterFallback = kyiv(day, 4, 30)
        val status = PowerStatusCalculator.calculate(schedule, null, q11, afterFallback, hasSynced = true)
        val nextOn = (status as PowerStatus.Off).nextOnAt.withZoneSameInstant(KyivTime.ZONE)
        assertEquals(LocalDateTime.of(day, LocalTime.of(5, 0)), nextOn.toLocalDateTime())
    }

    // --- Guard: no device-zone clocks in production code -------------------------------

    @Test fun `production code never uses the device time zone`() {
        val forbidden = listOf(
            Regex("""systemDefault\("""),
            Regex("""TimeZone\.getDefault"""),
            Regex("""Calendar\.getInstance"""),
            Regex("""\b(LocalDate|LocalDateTime|LocalTime|ZonedDateTime|OffsetDateTime)\.now\(\s*\)"""),
            Regex("""\bDate\(\s*\)"""),
        )
        val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("no sources found — wrong working dir?", sources.isNotEmpty())
        val offenders = sources.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                if (forbidden.any { it.containsMatchIn(line) }) "${f.path}:${i + 1}: ${line.trim()}" else null
            }
        }
        assertEquals("Use KyivTime.ZONE instead:\n" + offenders.joinToString("\n"), emptyList<String>(), offenders)
    }
}
