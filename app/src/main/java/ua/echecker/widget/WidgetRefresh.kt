package ua.echecker.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ua.echecker.container
import ua.echecker.domain.KyivTime
import ua.echecker.domain.PowerStatus
import ua.echecker.domain.PowerStatusCalculator
import ua.echecker.domain.StatusFormatter
import ua.echecker.domain.StatusText
import java.time.ZonedDateTime

private const val TAG = "WidgetRefresh"

object WidgetStateLoader {
    suspend fun load(context: Context): StatusText {
        val c = context.container
        val now = ZonedDateTime.now(KyivTime.ZONE)
        // Fallback alarm (next midnight) so the self-refresh chain survives a failed read below; replaced by the call after calculation.
        WidgetRefreshScheduler.scheduleNext(context, PowerStatus.NoData, now)
        val today = now.toLocalDate()
        val lastSync = c.settings.lastSyncAt.first()
        val status = PowerStatusCalculator.calculate(
            today = c.repository.get(today),
            tomorrow = c.repository.get(today.plusDays(1)),
            queue = c.settings.queue.first(),
            now = now,
            hasSynced = lastSync != null,
        )
        WidgetRefreshScheduler.scheduleNext(context, status, now)
        return StatusFormatter.format(status, now, lastSync)
    }
}

/** Re-renders the widget from cache at the next status change or next midnight, whichever is first. */
object WidgetRefreshScheduler {
    private const val SLACK_MS = 5_000L

    fun scheduleNext(context: Context, status: PowerStatus, now: ZonedDateTime) {
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(KyivTime.ZONE)
        val change = when (status) {
            is PowerStatus.On -> status.nextOffAt
            is PowerStatus.Off -> status.nextOnAt
            else -> null
        }
        val at = listOfNotNull(change, nextMidnight).minOf { it.toInstant() }
        val pi = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WidgetRefreshReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC, at.toEpochMilli() + SLACK_MS, pi)
    }
}

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                PowerWidget().updateAll(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Widget refresh failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
