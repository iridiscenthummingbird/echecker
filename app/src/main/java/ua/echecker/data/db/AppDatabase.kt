package ua.echecker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ScheduleEntity::class, IntervalEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        /** v3: schedules remember which source they came from (backup sources). Keeps the history. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `schedules` ADD COLUMN `source` TEXT NOT NULL DEFAULT '$DEFAULT_SOURCE'")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "echecker.db")
                .addMigrations(MIGRATION_2_3)
                // v1 → v2: data source switched to t.me/zoe_alarm — drop schedules cached from the old channel.
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
                .build()
    }
}
