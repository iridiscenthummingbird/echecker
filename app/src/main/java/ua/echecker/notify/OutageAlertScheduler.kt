package ua.echecker.notify

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
import ua.echecker.domain.AlertTexts
import ua.echecker.domain.KyivTime
import ua.echecker.domain.OutageAlert
import ua.echecker.domain.OutageAlerts
import ua.echecker.domain.PowerStatusCalculator
import ua.echecker.widget.WidgetUpdater
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

private const val TAG = "OutageAlerts"
private const val EXTRA_KEY = "alert_key"

/**
 * Keeps one exact alarm armed for the next outage alert of the selected queue. Re-run after every sync,
 * queue or settings change, boot and time change; the receiver re-arms it after each alert.
 */
object OutageAlertScheduler {
    /** Alarms can fire a little early or late; an alert this close to its time still counts. */
    private val MATCH_WINDOW: Duration = Duration.ofMinutes(2)

    suspend fun upcoming(context: Context, now: ZonedDateTime): List<OutageAlert> {
        val c = context.container
        val queue = c.settings.queue.first() ?: return emptyList()
        val prefs = c.settings.alertPrefs.first()
        val today = now.withZoneSameInstant(KyivTime.ZONE).toLocalDate()
        val todaySchedule = c.repository.get(today)
        val tomorrowSchedule = c.repository.get(today.plusDays(1))
        val lastKnown = when {
            tomorrowSchedule != null -> today.plusDays(1)
            todaySchedule != null -> today
            else -> return emptyList()
        }
        return OutageAlerts.upcoming(
            PowerStatusCalculator.spans(todaySchedule, tomorrowSchedule, queue, yesterday = c.repository.get(today.minusDays(1))),
            now, prefs.leadMinutes, prefs.backOn, lastKnown,
        )
    }

    /** Arms the alarm for the first alert after max(now, [notBefore]), or cancels it if there is none. */
    suspend fun reschedule(context: Context, notBefore: Instant? = null) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        var now = ZonedDateTime.now(KyivTime.ZONE)
        if (notBefore != null && notBefore.isAfter(now.toInstant())) now = notBefore.atZone(KyivTime.ZONE)
        val next = if (Notifications.canNotify(context)) upcoming(context, now).firstOrNull() else null
        if (next == null) {
            alarms.cancel(pendingIntent(context, null))
            return
        }
        alarms.setPrecise(next.at.toInstant().toEpochMilli(), pendingIntent(context, next.key))
    }

    /** Called when the alarm fires: show the alert if it still holds (schedules may have changed), then re-arm. */
    suspend fun onAlarm(context: Context, key: String?) {
        val now = ZonedDateTime.now(KyivTime.ZONE)
        val due = upcoming(context, now.minus(MATCH_WINDOW))
            .firstOrNull { it.key == key && Duration.between(it.at, now).abs() <= MATCH_WINDOW }
        if (due != null) {
            val c = context.container
            val queue = c.settings.queue.first()
            if (queue != null && c.settings.lastAlertKey.first() != due.key) {
                Notifications.show(context, Notifications.CHANNEL_OUTAGES, Notifications.ID_OUTAGE, AlertTexts.forAlert(due, queue))
                c.settings.markAlertShown(due.key)
            }
        }
        reschedule(context, notBefore = (due?.at?.toInstant() ?: now.toInstant()).plusSeconds(1))
    }

    private fun pendingIntent(context: Context, key: String?): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, OutageAlertReceiver::class.java).apply { key?.let { putExtra(EXTRA_KEY, it) } },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

class OutageAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY)
        runAsync(context) { OutageAlertScheduler.onAlarm(context, key) }
    }
}

/** Alarms are cleared on reboot and drift on clock changes: re-arm them and refresh the widgets. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync(context) {
            OutageAlertScheduler.reschedule(context)
            WidgetUpdater.refreshAll(context)
        }
    }
}

private fun BroadcastReceiver.runAsync(context: Context, block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Receiver work failed", e)
        } finally {
            pending.finish()
        }
    }
}
