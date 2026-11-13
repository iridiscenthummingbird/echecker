package ua.echecker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ScheduleDao {
    @Query("SELECT postId, contentHash, source FROM schedules WHERE date = :date")
    protected abstract suspend fun versionFor(date: String): ScheduleVersion?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertSchedule(schedule: ScheduleEntity)

    @Query("DELETE FROM intervals WHERE date = :date")
    protected abstract suspend fun deleteIntervals(date: String)

    @Insert
    protected abstract suspend fun insertIntervals(intervals: List<IntervalEntity>)

    /** Stores the schedule if [shouldReplace] accepts the version currently stored for its date (null = none). */
    @Transaction
    open suspend fun upsertIf(
        schedule: ScheduleEntity,
        intervals: List<IntervalEntity>,
        shouldReplace: (ScheduleVersion?) -> Boolean,
    ): Boolean {
        if (!shouldReplace(versionFor(schedule.date))) return false
        deleteIntervals(schedule.date)
        insertSchedule(schedule)
        insertIntervals(intervals)
        return true
    }

    @Transaction
    @Query("SELECT * FROM schedules WHERE date = :date")
    abstract suspend fun get(date: String): ScheduleWithIntervals?

    @Transaction
    @Query("SELECT * FROM schedules WHERE date = :date")
    abstract fun observe(date: String): Flow<ScheduleWithIntervals?>

    @Transaction
    @Query("SELECT * FROM schedules WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    abstract fun observeRange(from: String, to: String): Flow<List<ScheduleWithIntervals>>
}
