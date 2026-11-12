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

    suspend fun backfill(since: Instant, maxPages: Int = 20) {
        var before: Long? = null
        repeat(maxPages) {
            val posts = source.fetch(before)
            if (posts.isEmpty()) return
            save(posts)
            if (posts.minOf { it.publishedAt } < since) return
            before = posts.minOf { it.id }
        }
    }

    suspend fun get(date: LocalDate): Schedule? = dao.get(date.toString())?.toDomain()

    fun observe(date: LocalDate): Flow<Schedule?> = dao.observe(date.toString()).map { it?.toDomain() }

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<Schedule>> =
        dao.observeRange(from.toString(), to.toString()).map { list -> list.map { it.toDomain() } }

    private suspend fun save(posts: List<RawPost>) {
        for (post in posts) {
            val schedule = ScheduleParser.parse(post.text, post.publishedAt.atZone(KyivTime.ZONE).toLocalDate()) ?: continue
            val date = schedule.date.toString()
            dao.upsertIfNewer(
                ScheduleEntity(date, post.id, schedule.queues.toSortedMap().toString().hashCode()),
                schedule.queues.flatMap { (queue, intervals) ->
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
