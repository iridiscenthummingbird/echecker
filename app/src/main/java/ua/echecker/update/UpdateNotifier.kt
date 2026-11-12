package ua.echecker.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import ua.echecker.MainActivity
import ua.echecker.R
import ua.echecker.notify.Notifications

object UpdateNotifier {
    private const val CHANNEL = "app-updates"
    private const val ID = 1001

    fun canNotify(context: Context): Boolean = Notifications.canNotify(context)

    /** @return true if the notification was posted. */
    fun show(context: Context, release: ReleaseInfo): Boolean {
        if (!canNotify(context)) return false
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Оновлення застосунку", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Доступна версія ${release.version}")
            .setContentText("Відкрийте застосунок, щоб оновитися")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return try {
            nm.notify(ID, n)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
