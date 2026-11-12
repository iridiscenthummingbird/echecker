package ua.echecker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ua.echecker.data.db.AppDatabase
import ua.echecker.domain.Interval
import ua.echecker.domain.Queue
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @After fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test fun `v2 database keeps its history after migrating to v3`() = runTest {
        // The exact schema Room 2.7 generated for v2 (identity hash included).
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `schedules` (`date` TEXT NOT NULL, `postId` INTEGER NOT NULL, `contentHash` INTEGER NOT NULL, PRIMARY KEY(`date`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `intervals` (`date` TEXT NOT NULL, `queue` TEXT NOT NULL, `startMinute` INTEGER NOT NULL, `endMinute` INTEGER NOT NULL, PRIMARY KEY(`date`, `queue`, `startMinute`), FOREIGN KEY(`date`) REFERENCES `schedules`(`date`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_intervals_date` ON `intervals` (`date`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'e8ccc7ef5e6b5ead39b2afc770daa18c')")
            db.execSQL("INSERT INTO schedules VALUES ('2026-11-10', 3500, 1)")
            db.execSQL("INSERT INTO intervals VALUES ('2026-11-10', '1.1', 600, 720)")
            db.version = 2
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = ScheduleRepository(room.scheduleDao(), object : PostSource {
                override val id = "zoe_alarm"
                override suspend fun fetch(before: Long?) = emptyList<RawPost>()
            })
            val s = repo.get(LocalDate.of(2026, 11, 10))!!
            assertEquals(listOf(Interval(600, 720)), s.intervalsFor(Queue(1, 1)))
            assertEquals("zoe_alarm", room.scheduleDao().get("2026-11-10")!!.schedule.source)
        } finally {
            room.close()
        }
    }
}
