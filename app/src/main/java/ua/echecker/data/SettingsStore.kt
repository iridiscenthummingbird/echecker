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
import ua.echecker.update.ReleaseInfo
import java.time.Instant

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val QUEUE = stringPreferencesKey("queue")
    private val LAST_SYNC = longPreferencesKey("last_sync_at")
    // Per-source key: switching channels (old one was Zaporizhzhyaoblenergo_news) must re-run the backfill.
    private val BACKFILL_DONE = booleanPreferencesKey("backfill_done_zoe_alarm")

    private val UPDATE_VERSION = stringPreferencesKey("update_version")
    private val UPDATE_APK_URL = stringPreferencesKey("update_apk_url")
    private val UPDATE_PAGE_URL = stringPreferencesKey("update_page_url")
    private val UPDATE_CHECKED_AT = longPreferencesKey("update_checked_at")
    private val UPDATE_NOTIFIED = stringPreferencesKey("update_notified_version")
    private val NOTIFICATIONS_ASKED = booleanPreferencesKey("notifications_permission_asked")

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

    /** Latest release seen by the last update check (may be older than the installed app after an update). */
    val latestRelease: Flow<ReleaseInfo?> = context.dataStore.data.map { p ->
        val v = p[UPDATE_VERSION] ?: return@map null
        val url = p[UPDATE_APK_URL] ?: return@map null
        ReleaseInfo(v, url, p[UPDATE_PAGE_URL].orEmpty())
    }
    val updateCheckedAt: Flow<Instant?> = context.dataStore.data.map { p -> p[UPDATE_CHECKED_AT]?.let(Instant::ofEpochMilli) }
    val updateNotifiedVersion: Flow<String?> = context.dataStore.data.map { p -> p[UPDATE_NOTIFIED] }
    val notificationsAsked: Flow<Boolean> = context.dataStore.data.map { p -> p[NOTIFICATIONS_ASKED] ?: false }

    suspend fun saveUpdateCheck(latest: ReleaseInfo?, at: Instant) {
        context.dataStore.edit {
            if (latest == null) {
                it.remove(UPDATE_VERSION); it.remove(UPDATE_APK_URL); it.remove(UPDATE_PAGE_URL)
            } else {
                it[UPDATE_VERSION] = latest.version
                it[UPDATE_APK_URL] = latest.apkUrl
                it[UPDATE_PAGE_URL] = latest.pageUrl
            }
            it[UPDATE_CHECKED_AT] = at.toEpochMilli()
        }
    }

    suspend fun markUpdateNotified(version: String) {
        context.dataStore.edit { it[UPDATE_NOTIFIED] = version }
    }

    suspend fun markNotificationsAsked() {
        context.dataStore.edit { it[NOTIFICATIONS_ASKED] = true }
    }
}
