package ua.echecker.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ua.echecker.data.db.AppDatabase
import ua.echecker.domain.Interval
import ua.echecker.domain.Queue
import java.time.LocalDate

/**
 * Migrations are tested against the schemas committed in app/schemas/ (Room validates the migrated database
 * against the target schema). Add a test here for every new migration.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun `2 to 3 keeps the history and marks it as coming from the primary channel`() = runTest {
        helper.createDatabase(dbName, 2).apply {
            execSQL("INSERT INTO schedules VALUES ('2026-11-10', 3500, 1)")
            execSQL("INSERT INTO intervals VALUES ('2026-11-10', '1.1', 600, 720)")
            close()
        }

        helper.runMigrationsAndValidate(dbName, 3, true, AppDatabase.MIGRATION_2_3).apply {
            query("SELECT source FROM schedules WHERE date = '2026-11-10'").use {
                it.moveToFirst()
                assertEquals("zoe_alarm", it.getString(0))
            }
            close()
        }

        // And the app reads it through Room as before.
        val room = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = ScheduleRepository(room.scheduleDao(), object : PostSource {
                override val id = "zoe_alarm"
                override suspend fun fetch(before: Long?) = emptyList<RawPost>()
            })
            assertEquals(listOf(Interval(600, 720)), repo.get(LocalDate.of(2026, 11, 10))!!.intervalsFor(Queue(1, 1)))
        } finally {
            room.close()
        }
    }
}
