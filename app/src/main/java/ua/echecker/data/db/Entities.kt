package ua.echecker.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * [date] is ISO yyyy-MM-dd (sorts lexicographically). [contentHash] detects in-place edits of the same post.
 * [source] is the [ua.echecker.data.PostSource.id] the post came from; [postId] is only comparable within one source.
 */
@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey val date: String,
    val postId: Long,
    val contentHash: Int,
    @ColumnInfo(defaultValue = DEFAULT_SOURCE) val source: String = DEFAULT_SOURCE,
)

/** Rows written before v3 all came from the primary channel. */
const val DEFAULT_SOURCE = "zoe_alarm"

data class ScheduleVersion(val postId: Long, val contentHash: Int, val source: String)

@Entity(
    tableName = "intervals",
    primaryKeys = ["date", "queue", "startMinute"],
    foreignKeys = [ForeignKey(
        entity = ScheduleEntity::class,
        parentColumns = ["date"],
        childColumns = ["date"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("date")],
)
data class IntervalEntity(
    val date: String,
    val queue: String,
    val startMinute: Int,
    val endMinute: Int,
)

data class ScheduleWithIntervals(
    @Embedded val schedule: ScheduleEntity,
    @Relation(parentColumn = "date", entityColumn = "date")
    val intervals: List<IntervalEntity>,
)
