package ua.echecker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ScheduleEntity::class, IntervalEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "echecker.db")
                // v2: data source switched to t.me/zoe_alarm — drop schedules cached from the old channel.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
