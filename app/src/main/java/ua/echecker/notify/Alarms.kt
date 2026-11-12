package ua.echecker.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build

/**
 * An alarm that fires on time, also in Doze: exact when the app may (USE_EXACT_ALARM / SCHEDULE_EXACT_ALARM),
 * otherwise the closest inexact equivalent.
 */
fun AlarmManager.setPrecise(atMillis: Long, pi: PendingIntent) {
    val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExactAlarms()
    if (exactAllowed) {
        try {
            setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            return
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call; fall through.
        }
    }
    setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
}
