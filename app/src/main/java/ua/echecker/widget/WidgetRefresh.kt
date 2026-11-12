package ua.echecker.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ua.echecker.container
import ua.echecker.domain.Interval
import ua.echecker.domain.KyivTime
import ua.echecker.domain.PowerStatus
import ua.echecker.domain.PowerStatusCalculator
import ua.echecker.domain.StatusFormatter
import ua.echecker.domain.StatusText
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

private const val TAG = "WidgetRefresh"

/** Everything the widget renders, computed once per refresh. */
data class WidgetState(
    val status: PowerStatus,
    val text: StatusText,
    /** Today's outages for the selected queue, or null when today's schedule is unknown. */
    val todayIntervals: List<Interval>?,
    /** Kyiv minute of day at load time, for the timeline's "now" marker. */
    val nowMinute: Int,
)

object WidgetStateLoader {
    suspend fun load(context: Context): WidgetState {
        val c = context.container
        val now = ZonedDateTime.now(KyivTime.ZONE)
        // Fallback alarm so the self-refresh chain survives a failed read below; replaced by the call after calculation.
        WidgetRefreshScheduler.scheduleNext(context, PowerStatus.NoData, now)
        val today = now.toLocalDate()
        val lastSync = c.settings.lastSyncAt.first()
        val queue = c.settings.queue.first()
        val todaySchedule = c.repository.get(today)
        val status = PowerStatusCalculator.calculate(
            today = todaySchedule,
            tomorrow = c.repository.get(today.plusDays(1)),
            queue = queue,
            now = now,
            hasSynced = lastSync != null,
        )
        WidgetRefreshScheduler.scheduleNext(context, status, now)
        return WidgetState(
            status = status,
            text = StatusFormatter.format(status, now, lastSync),
            todayIntervals = if (queue != null) todaySchedule?.intervalsFor(queue) else null,
            nowMinute = now.hour * 60 + now.minute,
        )
    }
}

/**
 * Re-renders the widget from cache at the next status change, next midnight, or after [TICK]
 * (keeps the timeline's "now" marker roughly current), whichever is first.
 *
 * Status changes and midnight use an exact alarm that also fires in Doze, so the widget flips on time;
 * the tick stays inexact to spare the battery.
 */
object WidgetRefreshScheduler {
    private val SLACK: Duration = Duration.ofSeconds(2)
    private val TICK: Duration = Duration.ofMinutes(15)
    /** Window for the inexact tick. */
    private const val WINDOW_MS = 10 * 60_000L

    data class Plan(val at: Instant, val exact: Boolean)

    fun plan(status: PowerStatus, now: ZonedDateTime): Plan {
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(KyivTime.ZONE)
        val change = when (status) {
            is PowerStatus.On -> status.nextOffAt
            is PowerStatus.Off -> status.nextOnAt
            else -> null
        }
        val event = listOfNotNull(change, nextMidnight).minOf { it.toInstant() }.plus(SLACK)
        val tick = now.toInstant().plus(TICK)
        return if (event <= tick) Plan(event, exact = true) else Plan(tick, exact = false)
    }

    fun nextRefreshAt(status: PowerStatus, now: ZonedDateTime): Instant = plan(status, now).at

    fun scheduleNext(context: Context, status: PowerStatus, now: ZonedDateTime) {
        val plan = plan(status, now)
        val pi = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WidgetRefreshReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarms = context.getSystemService(AlarmManager::class.java)
        val at = plan.at.toEpochMilli()
        when {
            !plan.exact -> alarms.setWindow(AlarmManager.RTC, at, WINDOW_MS, pi)
            canScheduleExact(alarms) -> try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (e: SecurityException) {
                // Permission revoked between the check and the call.
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
            else -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun canScheduleExact(alarms: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
}

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WidgetUpdater.refreshAll(context)
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
