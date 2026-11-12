package ua.echecker.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ua.echecker.container
import ua.echecker.data.SyncIssue
import ua.echecker.widget.WidgetUpdater
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val now = Instant.now()
        val backfilled = c.settings.backfillDone.first()
        val lastPrimary = c.settings.primarySyncedAt.first() ?: c.settings.lastSyncAt.first()
        val lastBackupCheck = c.settings.backupCheckedAt.first()
        return try {
            val result = c.repository.sync(
                since = sinceFor(now, lastPrimary.takeIf { backfilled }),
                now = now,
                checkBackupsForGaps = lastBackupCheck == null || Duration.between(lastBackupCheck, now) >= BACKUP_GAP_INTERVAL,
            )
            if (result.backupsChecked) c.settings.markBackupChecked(now)
            result.backupErrors.forEach { (id, e) -> Log.w(TAG, "Backup source $id failed", e) }
            val primaryError = result.primaryError
            if (primaryError == null) {
                c.settings.markPrimarySynced(now)
                if (!backfilled) c.settings.markBackfillDone()
                c.settings.setSyncIssue(null)
            } else {
                Log.w(TAG, "Primary source failed; used backups ${result.backupsAnswered}", primaryError)
                c.settings.setSyncIssue(SyncIssue.from(primaryError).copy(backupSource = result.backupsAnswered.first()))
            }
            c.settings.markSynced(now)
            refreshWidgets()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Sync failed (attempt $runAttemptCount)", e)
            c.settings.setSyncIssue(SyncIssue.from(e))
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun refreshWidgets() {
        try {
            WidgetUpdater.refreshAll(applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Widget update failed", e)
        }
    }

    companion object {
        const val TAG = "SyncWorker"
        private val HISTORY: Duration = Duration.ofDays(30)
        private val OVERLAP: Duration = Duration.ofHours(1)
        /** Without a primary failure, ask backups about a missing today schedule at most this often (mobile data). */
        val BACKUP_GAP_INTERVAL: Duration = Duration.ofHours(2)

        /**
         * How far back to page the primary source: to just before the last full read (posts may have been
         * edited meanwhile), but never more than [HISTORY]; the first sync backfills all of [HISTORY].
         */
        fun sinceFor(now: Instant, lastPrimarySync: Instant?): Instant {
            val floor = now.minus(HISTORY)
            return lastPrimarySync?.minus(OVERLAP)?.let { maxOf(it, floor) } ?: floor
        }
    }
}

object SyncScheduler {
    private const val PERIODIC = "sync-periodic"
    private const val NOW = "sync-now"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    /** Sync when the app comes to the foreground, unless one is already pending/running or ran within [minAge]. */
    suspend fun syncOnOpen(context: Context, minAge: Duration = Duration.ofMinutes(2)) {
        val last = context.container.settings.lastSyncAt.first()
        if (last != null && Duration.between(last, Instant.now()) < minAge) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
    }

    fun observeSyncNowRunning(context: Context): Flow<Boolean> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(NOW).map { infos ->
            infos.any { it.state == WorkInfo.State.RUNNING }
        }
}
