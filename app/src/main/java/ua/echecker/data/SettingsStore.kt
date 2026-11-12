package ua.echecker.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.echecker.domain.Queue
import java.time.Instant

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val QUEUE = stringPreferencesKey("queue")
    private val LAST_SYNC = longPreferencesKey("last_sync_at")
    private val BACKFILL_DONE = booleanPreferencesKey("backfill_done")

    val queue: Flow<Queue?> = context.dataStore.data.map { p -> p[QUEUE]?.let(Queue::parse) }
    val lastSyncAt: Flow<Instant?> = context.dataStore.data.map { p -> p[LAST_SYNC]?.let(Instant::ofEpochMilli) }
    val backfillDone: Flow<Boolean> = context.dataStore.data.map { p -> p[BACKFILL_DONE] ?: false }

    suspend fun setQueue(q: Queue) {
        context.dataStore.edit { it[QUEUE] = q.toString() }
    }

    suspend fun markSynced(at: Instant) {
        context.dataStore.edit { it[LAST_SYNC] = at.toEpochMilli() }
    }

    suspend fun markBackfillDone() {
        context.dataStore.edit { it[BACKFILL_DONE] = true }
    }
}
