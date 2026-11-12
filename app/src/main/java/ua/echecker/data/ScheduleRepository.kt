package ua.echecker.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.echecker.data.db.IntervalEntity
import ua.echecker.data.db.ScheduleDao
import ua.echecker.data.db.ScheduleEntity
import ua.echecker.data.db.ScheduleVersion
import ua.echecker.data.db.ScheduleWithIntervals
import ua.echecker.domain.Interval
import ua.echecker.domain.KyivTime
import ua.echecker.domain.Queue
import ua.echecker.domain.Schedule
import ua.echecker.domain.ScheduleParser
import java.time.Instant
import java.time.LocalDate

/** What one [ScheduleRepository.sync] did. */
data class SyncResult(
    /** Why the primary source could not be read in full, or null if it was. */
    val primaryError: Exception?,
    /** Whether the backup sources were consulted. */
    val backupsChecked: Boolean,
    /** Ids of the backup sources that answered. */
    val backupsAnswered: List<String>,
    /** Per backup id, why it failed. */
    val backupErrors: Map<String, Exception> = emptyMap(),
)

/**
 * Schedules from a [primary] source, with [backups] (in priority order) filling the gaps.
 *
 * Every stored day remembers its source. A post from a higher-priority source always replaces one from a
 * lower-priority source, never the other way round; within one source the newer post (higher id) wins.
 */
class ScheduleRepository(
    private val dao: ScheduleDao,
    private val primary: PostSource,
    private val backups: List<PostSource> = emptyList(),
) {
    /**
     * Reads the primary source, newest page first, paging back until posts are older than [since]
     * (null = the newest page only) or [maxPages] pages were read. zoe_alarm posts ~3 pages/day.
     *
     * Backups are consulted when the primary fails, when a partial update has no base schedule to apply to,
     * or — if [checkBackupsForGaps] — when there is still no schedule for today.
     *
     * @throws Exception the primary's error, when it failed and no backup answered.
     */
    suspend fun sync(
        since: Instant? = null,
        maxPages: Int = 100,
        now: Instant = Instant.now(),
        checkBackupsForGaps: Boolean = true,
    ): SyncResult {
        // Pages arrive newest first; save oldest first so a partial update lands on top of its base schedule.
        // Whatever was fetched is still saved if a later page fails.
        val fetched = mutableListOf<RawPost>()
        var primaryError: Exception? = null
        try {
            var before: Long? = null
            for (page in 1..maxPages) {
                val posts = primary.fetch(before)
                if (posts.isEmpty()) break
                fetched += posts
                if (since == null || posts.minOf { it.publishedAt } < since) break
                before = posts.minOf { it.id }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            primaryError = e
        }
        val orphans = save(primary.id, fetched.distinctBy { it.id }.sortedBy { it.id })

        val today = KyivTime.dateOf(now).toString()
        val checkBackups = backups.isNotEmpty() &&
            (primaryError != null || orphans.isNotEmpty() || (checkBackupsForGaps && dao.get(today) == null))
        val answered = mutableListOf<String>()
        val backupErrors = linkedMapOf<String, Exception>()
        if (checkBackups) {
            for (backup in backups) {
                try {
                    save(backup.id, backup.fetch(null).sortedBy { it.id })
                    answered += backup.id
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    backupErrors[backup.id] = e
                }
            }
            // A backup may have supplied the base schedule these partial updates were waiting for.
            if (orphans.isNotEmpty()) save(primary.id, orphans)
        }
        if (primaryError != null && answered.isEmpty()) throw primaryError
        return SyncResult(primaryError, checkBackups, answered, backupErrors)
    }

    suspend fun get(date: LocalDate): Schedule? = dao.get(date.toString())?.toDomain()

    fun observe(date: LocalDate): Flow<Schedule?> = dao.observe(date.toString()).map { it?.toDomain() }

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<Schedule>> =
        dao.observeRange(from.toString(), to.toString()).map { list -> list.map { it.toDomain() } }

    /** 0 = primary, then backups in order; sources no longer configured rank last. */
    private fun rank(sourceId: String): Int = when (sourceId) {
        primary.id -> 0
        else -> backups.indexOfFirst { it.id == sourceId }.let { if (it < 0) Int.MAX_VALUE else it + 1 }
    }

    /**
     * Saves [posts] (oldest first) from source [sourceId].
     * @return partial updates that were skipped because their day has no base schedule yet.
     */
    private suspend fun save(sourceId: String, posts: List<RawPost>): List<RawPost> {
        val orphans = mutableListOf<RawPost>()
        val myRank = rank(sourceId)
        for (post in posts) {
            val update = ScheduleParser.parseUpdate(post.text, KyivTime.dateOf(post.publishedAt)) ?: continue
            val date = update.schedule.date.toString()
            val queues = if (update.partial) {
                // Without a base, the queues this post does not list would wrongly read as "no outages".
                val base = dao.get(date)
                if (base == null) {
                    orphans += post
                    continue
                }
                // A newer post from this source already defines this day; an older partial update must not override it.
                if (base.schedule.source == sourceId && base.schedule.postId > post.id) continue
                base.toDomain().queues + update.schedule.queues
            } else {
                update.schedule.queues
            }.filterValues { it.isNotEmpty() }
            val hash = queues.toSortedMap().toString().hashCode()
            dao.upsertIf(
                ScheduleEntity(date, post.id, hash, sourceId),
                queues.flatMap { (queue, intervals) ->
                    intervals.map { IntervalEntity(date, queue.toString(), it.startMinute, it.endMinute) }
                },
            ) { existing -> replaces(existing, sourceId, myRank, post.id, hash) }
        }
        return orphans
    }

    private fun replaces(existing: ScheduleVersion?, sourceId: String, myRank: Int, postId: Long, hash: Int): Boolean {
        if (existing == null) return true
        val theirRank = rank(existing.source)
        if (myRank != theirRank) return myRank < theirRank
        if (existing.source != sourceId) return false
        return postId > existing.postId || (postId == existing.postId && hash != existing.contentHash)
    }

    private fun ScheduleWithIntervals.toDomain() = Schedule(
        date = LocalDate.parse(schedule.date),
        queues = intervals
            .mapNotNull { e -> Queue.parse(e.queue)?.let { it to Interval(e.startMinute, e.endMinute) } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, list) -> list.sortedBy { it.startMinute } },
    )
}
