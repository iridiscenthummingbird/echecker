package ua.echecker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ua.echecker.data.db.AppDatabase
import ua.echecker.domain.Interval
import ua.echecker.domain.Queue
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ScheduleRepositoryTest {
    private val now = Instant.parse("2026-11-11T08:00:00Z")
    private val q11 = Queue(1, 1)
    private lateinit var db: AppDatabase
    private val source = FakeSource()
    private lateinit var repo: ScheduleRepository

    private fun post(id: Long, date: String, interval: String, daysAgo: Long = 0) = RawPost(
        id,
        now.minus(Duration.ofDays(daysAgo)),
        "Графік відключення на $date (середа) для абонентів:\n\n⚡ Черга 1.1\n $interval\n",
    )

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = ScheduleRepository(db.scheduleDao(), source)
    }

    @After fun tearDown() = db.close()

    @Test fun `sync stores parsed schedule and ignores other posts`() = runTest {
        source.pages[null] = listOf(
            RawPost(1, now, "Просто новина"),
            post(2, "11.11.2026", "з 15:30 до 19:30"),
        )
        repo.sync()
        val s = repo.get(LocalDate.of(2026, 11, 11))!!
        assertEquals(listOf(Interval(930, 1170)), s.intervalsFor(q11))
        assertNull(repo.get(LocalDate.of(2026, 11, 12)))
    }

    @Test fun `newer post for the same date wins regardless of order`() = runTest {
        source.pages[null] = listOf(post(11, "11.11.2026", "з 10:00 до 12:00"))
        repo.sync()
        source.pages[null] = listOf(post(10, "11.11.2026", "з 15:30 до 19:30"))
        repo.sync()
        assertEquals(listOf(Interval(600, 720)), repo.get(LocalDate.of(2026, 11, 11))!!.intervalsFor(q11))

        source.pages[null] = listOf(post(12, "11.11.2026", "з 20:00 до 21:00"))
        repo.sync()
        assertEquals(listOf(Interval(1200, 1260)), repo.get(LocalDate.of(2026, 11, 11))!!.intervalsFor(q11))
    }

    @Test fun `edited post with the same id replaces its schedule`() = runTest {
        source.pages[null] = listOf(post(20, "11.11.2026", "з 10:00 до 12:00"))
        repo.sync()
        source.pages[null] = listOf(post(20, "11.11.2026", "з 14:00 до 16:00"))
        repo.sync()
        assertEquals(listOf(Interval(840, 960)), repo.get(LocalDate.of(2026, 11, 11))!!.intervalsFor(q11))
    }

    @Test fun `same post id with same content is a no-op`() = runTest {
        source.pages[null] = listOf(post(20, "11.11.2026", "з 10:00 до 12:00"))
        repo.sync()
        source.pages[null] = listOf(post(20, "11.11.2026", "з 10:00 до 12:00"))
        repo.sync()
        assertEquals(listOf(Interval(600, 720)), repo.get(LocalDate.of(2026, 11, 11))!!.intervalsFor(q11))
    }

    @Test fun `older post id is still ignored even with different content`() = runTest {
        source.pages[null] = listOf(post(20, "11.11.2026", "з 10:00 до 12:00"))
        repo.sync()
        source.pages[null] = listOf(post(19, "11.11.2026", "з 14:00 до 16:00"))
        repo.sync()
        assertEquals(listOf(Interval(600, 720)), repo.get(LocalDate.of(2026, 11, 11))!!.intervalsFor(q11))
    }

    @Test fun `sync pages back until posts are older than since`() = runTest {
        source.pages[null] = listOf(post(100, "11.11.2026", "з 10:00 до 12:00", daysAgo = 0))
        source.pages[100] = listOf(post(90, "10.10.2026", "з 10:00 до 12:00", daysAgo = 32))
        source.pages[90] = listOf(post(80, "01.10.2026", "з 10:00 до 12:00", daysAgo = 41))
        repo.sync(since = now.minus(Duration.ofDays(30)), now = now)
        assertEquals(listOf<Long?>(null, 100), source.requests)
        assertEquals(2, repo.observeRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)).first().size)
    }

    @Test fun `observeRange returns schedules date descending`() = runTest {
        source.pages[null] = listOf(
            post(1, "09.11.2026", "з 10:00 до 12:00", daysAgo = 2),
            post(2, "10.11.2026", "з 10:00 до 12:00", daysAgo = 1),
            post(3, "11.11.2026", "з 10:00 до 12:00"),
        )
        repo.sync()
        val dates = repo.observeRange(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10)).first().map { it.date }
        assertEquals(listOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 9)), dates)
    }

    private val q32 = Queue(3, 2)
    private val nov11 = LocalDate.of(2026, 11, 11)

    private fun fullPost(id: Long) = RawPost(
        id, now,
        "Графік відключення на 11.11.2026:\n1.1: 10:00 – 12:00\n3.2: 14:00 – 16:00\n",
    )

    @Test fun `cancellation post clears the schedule for that date`() = runTest {
        source.pages[null] = listOf(fullPost(1), RawPost(2, now, "ГПВ на 11 листопада скасовано."))
        repo.sync()
        val s = repo.get(nov11)!!
        assertEquals(emptyMap<Queue, List<Interval>>(), s.queues)
    }

    @Test fun `partial update replaces only the listed queues`() = runTest {
        source.pages[null] = listOf(
            fullPost(1),
            RawPost(2, now, "Оновлено ГПВ на 11 листопада лише для черги 3.2:\n3.2: 18:00 – 20:00"),
        )
        repo.sync()
        val s = repo.get(nov11)!!
        assertEquals(listOf(Interval(600, 720)), s.intervalsFor(q11))
        assertEquals(listOf(Interval(1080, 1200)), s.intervalsFor(q32))
    }

    @Test fun `partial cancellation for a queue removes its outages`() = runTest {
        source.pages[null] = listOf(
            fullPost(1),
            RawPost(2, now, "На 11 листопада для черги 3.2 ГПВ скасовано, решта черг без змін."),
        )
        repo.sync()
        val s = repo.get(nov11)!!
        assertEquals(listOf(Interval(600, 720)), s.intervalsFor(q11))
        assertEquals(emptyList<Interval>(), s.intervalsFor(q32))
    }

    @Test fun `re-syncing a partial update is idempotent and an older partial is ignored`() = runTest {
        val partial = RawPost(3, now, "Оновлено ГПВ на 11 листопада лише для черги 3.2:\n3.2: 18:00 – 20:00")
        source.pages[null] = listOf(fullPost(1), partial)
        repo.sync()
        repo.sync()
        source.pages[null] = listOf(RawPost(2, now, "Оновлено ГПВ на 11 листопада лише для черги 1.1:\n1.1: 01:00 – 02:00"))
        repo.sync()
        val s = repo.get(nov11)!!
        assertEquals(listOf(Interval(600, 720)), s.intervalsFor(q11))
        assertEquals(listOf(Interval(1080, 1200)), s.intervalsFor(q32))
    }

    @Test fun `paged sync applies a partial update on top of a base from an older page`() = runTest {
        source.pages[null] = listOf(RawPost(2, now, "Оновлено ГПВ на 11 листопада лише для черги 3.2:\n3.2: 18:00 – 20:00"))
        source.pages[2] = listOf(fullPost(1))
        repo.sync(since = now.minus(Duration.ofDays(30)), maxPages = 3, now = now)
        val s = repo.get(nov11)!!
        assertEquals(listOf(Interval(600, 720)), s.intervalsFor(q11))
        assertEquals(listOf(Interval(1080, 1200)), s.intervalsFor(q32))
    }

    // ---- Incremental sync ----

    @Test fun `sync without since reads only the newest page`() = runTest {
        source.pages[null] = listOf(post(100, "11.11.2026", "з 10:00 до 12:00"))
        source.pages[100] = listOf(post(90, "11.11.2026", "з 10:00 до 12:00"))
        repo.sync(now = now)
        assertEquals(listOf<Long?>(null), source.requests)
    }

    @Test fun `incremental sync catches up on posts missed while offline`() = runTest {
        // Three pages of posts since the last sync (12 h ago); the full schedule is on the oldest one.
        source.pages[null] = listOf(RawPost(300, now, "Новина"))
        source.pages[300] = listOf(RawPost(200, now.minus(Duration.ofHours(6)), "Новина"))
        source.pages[200] = listOf(
            RawPost(100, now.minus(Duration.ofHours(13)), "Новина"),
            post(150, "11.11.2026", "з 10:00 до 12:00").copy(publishedAt = now.minus(Duration.ofHours(11))),
        )
        source.pages[100] = listOf(post(50, "10.11.2026", "з 10:00 до 12:00", daysAgo = 1))
        repo.sync(since = now.minus(Duration.ofHours(12)), now = now)
        assertEquals(listOf<Long?>(null, 300, 200), source.requests)
        assertEquals(listOf(Interval(600, 720)), repo.get(nov11)!!.intervalsFor(q11))
    }

    @Test fun `a failing later page still saves the pages before it`() = runTest {
        val failing = object : PostSource {
            override val id = "zoe_alarm"
            override suspend fun fetch(before: Long?): List<RawPost> =
                if (before == null) listOf(post(100, "11.11.2026", "з 10:00 до 12:00")) else throw IOException("boom")
        }
        val r = ScheduleRepository(db.scheduleDao(), failing)
        assertTrue(runCatching { r.sync(since = now.minus(Duration.ofDays(1)), now = now) }.exceptionOrNull() is IOException)
        assertEquals(listOf(Interval(600, 720)), r.get(nov11)!!.intervalsFor(q11))
    }

    // ---- Partial update without a base ----

    @Test fun `partial update without a base schedule is not stored as the whole day`() = runTest {
        source.pages[null] = listOf(RawPost(2, now, "Оновлено ГПВ на 11 листопада лише для черги 3.2:\n3.2: 18:00 – 20:00"))
        repo.sync(now = now)
        assertNull(repo.get(nov11))
    }

    // ---- Backup sources ----

    private val backup = FakeSource("backup")
    private val backup2 = FakeSource("backup2")
    private fun repoWithBackups() = ScheduleRepository(db.scheduleDao(), source, listOf(backup, backup2))

    @Test fun `backup fills in when the primary fails`() = runTest {
        source.error = SourceFormatException("layout changed")
        backup.pages[null] = listOf(fullPost(7))
        val result = repoWithBackups().sync(now = now)
        assertTrue(result.primaryError is SourceFormatException)
        assertEquals(listOf("backup", "backup2"), result.backupsAnswered)
        assertEquals(listOf(Interval(600, 720)), repo.get(nov11)!!.intervalsFor(q11))
    }

    @Test fun `primary error is thrown when no backup answers`() = runTest {
        source.error = IOException("offline")
        backup.error = IOException("offline")
        backup2.error = IOException("offline")
        val e = runCatching { repoWithBackups().sync(now = now) }.exceptionOrNull()
        assertEquals("offline", e?.message)
    }

    @Test fun `backups are not asked when the primary has today's schedule`() = runTest {
        source.pages[null] = listOf(fullPost(1))
        val result = repoWithBackups().sync(now = now)
        assertEquals(false, result.backupsChecked)
        assertTrue(backup.requests.isEmpty())
    }

    @Test fun `backups are asked about a missing today only when allowed`() = runTest {
        source.pages[null] = listOf(RawPost(1, now, "Новина"))
        assertEquals(false, repoWithBackups().sync(now = now, checkBackupsForGaps = false).backupsChecked)
        backup.pages[null] = listOf(fullPost(7))
        assertEquals(true, repoWithBackups().sync(now = now, checkBackupsForGaps = true).backupsChecked)
        assertEquals(listOf(Interval(600, 720)), repo.get(nov11)!!.intervalsFor(q11))
    }

    @Test fun `primary schedule replaces a backup one, never the other way round`() = runTest {
        // The backup's post id is far higher: ids are not comparable across sources.
        backup.pages[null] = listOf(RawPost(9000, now, "Графік відключення на 11.11.2026:\n1.1: 01:00 – 02:00\n"))
        source.error = IOException("offline")
        repoWithBackups().sync(now = now)
        assertEquals(listOf(Interval(60, 120)), repo.get(nov11)!!.intervalsFor(q11))

        source.error = null
        source.pages[null] = listOf(fullPost(5))
        repoWithBackups().sync(now = now)
        assertEquals(listOf(Interval(600, 720)), repo.get(nov11)!!.intervalsFor(q11))

        source.error = IOException("offline")
        backup.pages[null] = listOf(RawPost(9001, now, "Графік відключення на 11.11.2026:\n1.1: 03:00 – 04:00\n"))
        repoWithBackups().sync(now = now)
        assertEquals(listOf(Interval(600, 720)), repo.get(nov11)!!.intervalsFor(q11))
    }

    @Test fun `first backup outranks the second`() = runTest {
        source.error = IOException("offline")
        backup2.pages[null] = listOf(RawPost(1, now, "Графік відключення на 11.11.2026:\n1.1: 03:00 – 04:00\n"))
        backup.pages[null] = listOf(RawPost(1, now, "Графік відключення на 11.11.2026:\n1.1: 01:00 – 02:00\n"))
        repoWithBackups().sync(now = now)
        assertEquals(listOf(Interval(60, 120)), repo.get(nov11)!!.intervalsFor(q11))
    }

    @Test fun `primary partial update waits for a base from a backup`() = runTest {
        source.pages[null] = listOf(RawPost(2, now, "Оновлено ГПВ на 11 листопада лише для черги 3.2:\n3.2: 18:00 – 20:00"))
        backup.pages[null] = listOf(fullPost(500))
        repoWithBackups().sync(now = now, checkBackupsForGaps = false)
        val s = repo.get(nov11)!!
        assertEquals(listOf(Interval(600, 720)), s.intervalsFor(q11))
        assertEquals(listOf(Interval(1080, 1200)), s.intervalsFor(q32))
    }

    private class FakeSource(override val id: String = "zoe_alarm") : PostSource {
        val pages = mutableMapOf<Long?, List<RawPost>>()
        val requests = mutableListOf<Long?>()
        var error: Exception? = null
        override suspend fun fetch(before: Long?): List<RawPost> {
            requests += before
            error?.let { throw it }
            return pages[before].orEmpty()
        }
    }
}
