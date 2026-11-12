package ua.echecker.widget

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState

internal val REFRESH_NONCE = longPreferencesKey("refresh_nonce")

object WidgetUpdater {
    /** Bumps each widget's nonce so a running session re-reads its data, then requests an update. */
    suspend fun refreshAll(context: Context) {
        val widget = PowerWidget()
        GlanceAppWidgetManager(context).getGlanceIds(PowerWidget::class.java).forEach { id ->
            updateAppWidgetState(context, id) { it[REFRESH_NONCE] = System.nanoTime() }
            widget.update(context, id)
        }
    }
}
