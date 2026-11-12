package ua.echecker.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.echecker.data.db.IntervalEntity
import ua.echecker.data.db.ScheduleDao
import ua.echecker.data.db.ScheduleEntity
import ua.echecker.data.db.ScheduleWithIntervals
import ua.echecker.domain.Interval
import ua.echecker.domain.KyivTime
import ua.echecker.domain.Queue
import ua.echecker.domain.Schedule
import ua.echecker.domain.ScheduleParser
import java.time.Instant
import java.time.LocalDate

class ScheduleRepository(
    private val dao: ScheduleDao,
    private val source: PostSource,
) {
    suspend fun sync() {
        save(source.fetch(null))
    }

    /** zoe_alarm posts ~3 pages/day, so 100 pages ≈ 30 days of history. */
    suspend fun backfill(since: Instant, maxPages: Int = 100) {
        // Pages arrive newest first; save oldest first so a partial update lands on top of its base schedule.
        // Whatever was fetched is still saved if a later page fails.
        val fetched = mutableListOf<RawPost>()
        try {
            var before: Long? = null
            for (page in 1..maxPages) {
                val posts = source.fetch(before)
                if (posts.isEmpty()) break
                fetched += posts
                if (posts.minOf { it.publishedAt } < since) break
                before = posts.minOf { it.id }
            }
        } finally {
            save(fetched.sortedBy { it.id })
        }
    }

    suspend fun get(date: LocalDate): Schedule? = dao.get(date.toString())?.toDomain()

    fun observe(date: LocalDate): Flow<Schedule?> = dao.observe(date.toString()).map { it?.toDomain() }

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<Schedule>> =
        dao.observeRange(from.toString(), to.toString()).map { list -> list.map { it.toDomain() } }

    private suspend fun save(posts: List<RawPost>) {
        for (post in posts) {
            val update = ScheduleParser.parseUpdate(post.text, KyivTime.dateOf(post.publishedAt)) ?: continue
            val date = update.schedule.date.toString()
            val queues = if (update.partial) {
                val base = dao.get(date)
                // A newer post already defines this day; an older partial update must not override it.
                if (base != null && base.schedule.postId > post.id) continue
                base?.toDomain()?.queues.orEmpty() + update.schedule.queues
            } else {
                update.schedule.queues
            }.filterValues { it.isNotEmpty() }
            dao.upsertIfNewer(
                ScheduleEntity(date, post.id, queues.toSortedMap().toString().hashCode()),
                queues.flatMap { (queue, intervals) ->
                    intervals.map { IntervalEntity(date, queue.toString(), it.startMinute, it.endMinute) }
                },
            )
        }
    }

    private fun ScheduleWithIntervals.toDomain() = Schedule(
        date = LocalDate.parse(schedule.date),
        queues = intervals
            .mapNotNull { e -> Queue.parse(e.queue)?.let { it to Interval(e.startMinute, e.endMinute) } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, list) -> list.sortedBy { it.startMinute } },
    )
}
