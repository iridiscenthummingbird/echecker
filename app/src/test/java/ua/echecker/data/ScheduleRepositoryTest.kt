package ua.echecker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ua.echecker.data.db.AppDatabase
import ua.echecker.domain.Interval
import ua.echecker.domain.Queue
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

    @Test fun `backfill pages back until posts are older than since`() = runTest {
        source.pages[null] = listOf(post(100, "11.11.2026", "з 10:00 до 12:00", daysAgo = 0))
        source.pages[100] = listOf(post(90, "10.10.2026", "з 10:00 до 12:00", daysAgo = 32))
        source.pages[90] = listOf(post(80, "01.10.2026", "з 10:00 до 12:00", daysAgo = 41))
        repo.backfill(since = now.minus(Duration.ofDays(30)))
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

    @Test fun `backfill applies a partial update on top of a base from an older page`() = runTest {
        source.pages[null] = listOf(RawPost(2, now, "Оновлено ГПВ на 11 листопада лише для черги 3.2:\n3.2: 18:00 – 20:00"))
        source.pages[2] = listOf(fullPost(1))
        repo.backfill(since = now.minus(Duration.ofDays(30)), maxPages = 3)
        val s = repo.get(nov11)!!
        assertEquals(listOf(Interval(600, 720)), s.intervalsFor(q11))
        assertEquals(listOf(Interval(1080, 1200)), s.intervalsFor(q32))
    }

    private class FakeSource : PostSource {
        val pages = mutableMapOf<Long?, List<RawPost>>()
        val requests = mutableListOf<Long?>()
        override suspend fun fetch(before: Long?): List<RawPost> {
            requests += before
            return pages[before].orEmpty()
        }
    }
}
