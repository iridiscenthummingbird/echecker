package ua.echecker.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
import ua.echecker.domain.Queue
import ua.echecker.domain.StatusFormatter
import ua.echecker.domain.StatusText
import ua.echecker.i18n.strings
import ua.echecker.notify.setPrecise
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

private const val TAG = "WidgetRefresh"

/** Everything the widget renders, computed once per refresh. */
data class WidgetState(
    val status: PowerStatus,
    val text: StatusText,
    /** Today's outages for the widget's queue, or null when today's schedule is unknown. */
    val todayIntervals: List<Interval>?,
    /** Kyiv minute of day at load time, for the timeline's "now" marker. */
    val nowMinute: Int,
    /** The queue of a widget pinned to its own queue; null on the main widget. */
    val queue: Queue? = null,
)

object WidgetStateLoader {
    /** @param showQueue label the widget with its queue (widgets pinned to a queue of their own). */
    suspend fun load(context: Context, queue: Queue?, showQueue: Boolean = false): WidgetState {
        val c = context.container
        val now = ZonedDateTime.now(KyivTime.ZONE)
        // Fallback alarm so the self-refresh chain survives a failed read below; replaced once statuses are known.
        WidgetRefreshScheduler.scheduleNext(context, PowerStatus.NoData, now)
        val lastSync = c.settings.lastSyncAt.first()
        val todaySchedule = c.repository.get(now.toLocalDate())
        val status = statusFor(context, queue, now)
        WidgetRefreshScheduler.rescheduleAll(context, now)
        return WidgetState(
            status = status,
            text = StatusFormatter.format(status, now, lastSync, context.strings),
            todayIntervals = if (queue != null) todaySchedule?.intervalsFor(queue) else null,
            nowMinute = now.hour * 60 + now.minute,
            queue = queue.takeIf { showQueue },
        )
    }

    suspend fun statusFor(context: Context, queue: Queue?, now: ZonedDateTime): PowerStatus {
        val c = context.container
        val today = now.toLocalDate()
        return PowerStatusCalculator.calculate(
            today = c.repository.get(today),
            tomorrow = c.repository.get(today.plusDays(1)),
            queue = queue,
            now = now,
            hasSynced = c.settings.lastSyncAt.first() != null,
        )
    }
}

/**
 * Re-renders the widgets from cache at the next status change, next midnight, or after [TICK]
 * (keeps the timeline's "now" marker roughly current), whichever is first.
 *
 * Status changes and midnight use an exact alarm that also fires in Doze, so the widgets flip on time;
 * the tick stays inexact to spare the battery. One alarm serves every widget, so it is planned over all
 * the queues the widgets show.
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

    /** The earliest of several plans (one per queue shown on a widget). */
    fun earliest(plans: List<Plan>): Plan? = plans.minByOrNull { it.at }

    fun nextRefreshAt(status: PowerStatus, now: ZonedDateTime): Instant = plan(status, now).at

    fun scheduleNext(context: Context, status: PowerStatus, now: ZonedDateTime) = schedule(context, plan(status, now))

    suspend fun rescheduleAll(context: Context, now: ZonedDateTime) {
        val queues = WidgetUpdater.queuesInUse(context)
        val plans = queues.map { plan(WidgetStateLoader.statusFor(context, it, now), now) }
        schedule(context, earliest(plans) ?: plan(PowerStatus.NoData, now))
    }

    private fun schedule(context: Context, plan: Plan) {
        val pi = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WidgetRefreshReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarms = context.getSystemService(AlarmManager::class.java)
        val at = plan.at.toEpochMilli()
        if (plan.exact) alarms.setPrecise(at, pi) else alarms.setWindow(AlarmManager.RTC, at, WINDOW_MS, pi)
    }
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
