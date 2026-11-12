package ua.echecker.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ua.echecker.MainActivity
import ua.echecker.R
import ua.echecker.domain.NotificationText

object Notifications {
    const val CHANNEL_OUTAGES = "outages"
    const val CHANNEL_SCHEDULE = "schedule-changes"

    const val ID_OUTAGE = 2001
    const val ID_SCHEDULE_TODAY = 2002
    const val ID_SCHEDULE_TOMORROW = 2003

    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** The system screen where the user can allow this app's notifications. */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun ensureChannels(nm: NotificationManager) {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_OUTAGES, "Відключення світла", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Перед відключенням і коли світло має повернутися"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SCHEDULE, "Зміни графіка", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Опубліковано або змінено графік на сьогодні чи завтра"
            },
        )
    }

    /** @return true if posted. */
    fun show(context: Context, channel: String, id: Int, text: NotificationText): Boolean {
        if (!canNotify(context)) return false
        val nm = context.getSystemService(NotificationManager::class.java)
        ensureChannels(nm)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(if (channel == CHANNEL_OUTAGES) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_STATUS)
            .build()
        return try {
            nm.notify(id, n)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
