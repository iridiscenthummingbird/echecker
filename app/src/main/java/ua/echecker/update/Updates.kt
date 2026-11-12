package ua.echecker.update

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ua.echecker.BuildConfig
import ua.echecker.container
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Checks GitHub releases for a newer APK, remembers it, and notifies once per version. */
object Updates {
    private const val TAG = "Updates"
    private const val PERIODIC = "update-check-periodic"

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** The newest known release, only while it is newer than the installed app. */
    fun available(context: Context): Flow<ReleaseInfo?> =
        context.container.settings.latestRelease.map { r -> r?.takeIf { AppVersion.isNewer(it.version, currentVersion) } }

    /** @throws java.io.IOException on network errors. */
    suspend fun check(context: Context): ReleaseInfo? {
        val c = context.container
        val newer = c.updateChecker.latest()?.takeIf { AppVersion.isNewer(it.version, currentVersion) }
        c.settings.saveUpdateCheck(newer, Instant.now())
        if (newer != null && c.settings.updateNotifiedVersion.first() != newer.version) {
            if (UpdateNotifier.show(context, newer)) c.settings.markUpdateNotified(newer.version)
        }
        return newer
    }

    /** Foreground check, throttled to once per [minAge]; failures are ignored (the periodic worker retries). */
    suspend fun checkOnOpen(context: Context, minAge: Duration = Duration.ofHours(6)) {
        val last = context.container.settings.updateCheckedAt.first()
        if (last != null && Duration.between(last, Instant.now()) < minAge) return
        try {
            check(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Update check on open failed", e)
        }
    }

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        Updates.check(applicationContext)
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("UpdateCheckWorker", "Update check failed (attempt $runAttemptCount)", e)
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
