package ua.echecker.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.echecker.domain.Queue
import ua.echecker.update.ReleaseInfo
import java.time.Instant
import java.time.LocalDate

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

    /** Last time the primary source was read in full; incremental syncs page back to just before it. */
    private val PRIMARY_SYNC = longPreferencesKey("primary_sync_at")
    private val BACKUP_CHECK = longPreferencesKey("backup_check_at")
    private val ISSUE_KIND = stringPreferencesKey("sync_issue_kind")
    private val ISSUE_HTTP = intPreferencesKey("sync_issue_http")
    private val ISSUE_BACKUP = stringPreferencesKey("sync_issue_backup")

    private val ALERT_LEAD = intPreferencesKey("alert_lead_minutes")
    private val ALERT_BACK_ON = booleanPreferencesKey("alert_back_on")
    private val ALERT_SCHEDULE = booleanPreferencesKey("alert_schedule_changes")
    private val LAST_ALERT = stringPreferencesKey("last_alert_key")
    /** First Kyiv day the backfill covered; days from here on without a schedule had no outages scheduled. */
    private val HISTORY_START = stringPreferencesKey("history_start")

    /** Null on installs that backfilled before this was recorded; callers fall back to the earliest stored day. */
    val historyStart: Flow<LocalDate?> = context.dataStore.data.map { p ->
        p[HISTORY_START]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    suspend fun setHistoryStart(date: LocalDate) {
        context.dataStore.edit { it[HISTORY_START] = date.toString() }
    }

    val queue: Flow<Queue?> = context.dataStore.data.map { p -> p[QUEUE]?.let(Queue::parse) }

    /** What to notify about; defaults are on so the feature is discoverable, each can be turned off in the app. */
    val alertPrefs: Flow<AlertPrefs> = context.dataStore.data.map { p ->
        AlertPrefs(
            leadMinutes = p[ALERT_LEAD] ?: AlertPrefs.DEFAULT_LEAD,
            backOn = p[ALERT_BACK_ON] ?: true,
            scheduleChanges = p[ALERT_SCHEDULE] ?: true,
        )
    }
    val lastAlertKey: Flow<String?> = context.dataStore.data.map { p -> p[LAST_ALERT] }

    suspend fun setAlertPrefs(prefs: AlertPrefs) {
        context.dataStore.edit {
            it[ALERT_LEAD] = prefs.leadMinutes
            it[ALERT_BACK_ON] = prefs.backOn
            it[ALERT_SCHEDULE] = prefs.scheduleChanges
        }
    }

    suspend fun markAlertShown(key: String) {
        context.dataStore.edit { it[LAST_ALERT] = key }
    }
    val lastSyncAt: Flow<Instant?> = context.dataStore.data.map { p -> p[LAST_SYNC]?.let(Instant::ofEpochMilli) }
    val backfillDone: Flow<Boolean> = context.dataStore.data.map { p -> p[BACKFILL_DONE] ?: false }

    suspend fun setQueue(q: Queue) {
        context.dataStore.edit { it[QUEUE] = q.toString() }
    }

    suspend fun markSynced(at: Instant) {
        context.dataStore.edit { it[LAST_SYNC] = at.toEpochMilli() }
    }

    val primarySyncedAt: Flow<Instant?> = context.dataStore.data.map { p -> p[PRIMARY_SYNC]?.let(Instant::ofEpochMilli) }
    val backupCheckedAt: Flow<Instant?> = context.dataStore.data.map { p -> p[BACKUP_CHECK]?.let(Instant::ofEpochMilli) }

    val syncIssue: Flow<SyncIssue?> = context.dataStore.data.map { p ->
        val kind = p[ISSUE_KIND]?.let { k -> SyncIssue.Kind.entries.firstOrNull { it.name == k } } ?: return@map null
        SyncIssue(kind, p[ISSUE_HTTP], p[ISSUE_BACKUP])
    }

    suspend fun markPrimarySynced(at: Instant) {
        context.dataStore.edit { it[PRIMARY_SYNC] = at.toEpochMilli() }
    }

    suspend fun markBackupChecked(at: Instant) {
        context.dataStore.edit { it[BACKUP_CHECK] = at.toEpochMilli() }
    }

    /** null clears the issue (after a fully successful sync). */
    suspend fun setSyncIssue(issue: SyncIssue?) {
        context.dataStore.edit {
            it.remove(ISSUE_KIND); it.remove(ISSUE_HTTP); it.remove(ISSUE_BACKUP)
            if (issue != null) {
                it[ISSUE_KIND] = issue.kind.name
                issue.httpCode?.let { c -> it[ISSUE_HTTP] = c }
                issue.backupSource?.let { b -> it[ISSUE_BACKUP] = b }
            }
        }
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
