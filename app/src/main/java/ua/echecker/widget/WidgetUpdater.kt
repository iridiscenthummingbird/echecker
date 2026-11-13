package ua.echecker.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.flow.first
import ua.echecker.container
import ua.echecker.domain.Queue

internal val REFRESH_NONCE = longPreferencesKey("refresh_nonce")

/** The queue a [QueueWidget] instance shows, chosen when it is added. */
internal val WIDGET_QUEUE = stringPreferencesKey("widget_queue")

object WidgetUpdater {
    private fun widgets(): List<GlanceAppWidget> = listOf(PowerWidget(), QueueWidget())

    /** Bumps each widget's nonce so a running session re-reads its data, then requests an update. */
    suspend fun refreshAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        for (widget in widgets()) {
            manager.getGlanceIds(widget.javaClass).forEach { id ->
                updateAppWidgetState(context, id) { it[REFRESH_NONCE] = System.nanoTime() }
                widget.update(context, id)
            }
        }
    }

    /** Every queue shown on the home screen: the app's queue (main widget) plus each pinned [QueueWidget]'s. */
    suspend fun queuesInUse(context: Context): Set<Queue> {
        val manager = GlanceAppWidgetManager(context)
        val queues = mutableSetOf<Queue>()
        if (manager.getGlanceIds(PowerWidget::class.java).isNotEmpty()) {
            context.container.settings.queue.first()?.let { queues += it }
        }
        for (id in manager.getGlanceIds(QueueWidget::class.java)) {
            val prefs = getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, id)
            prefs[WIDGET_QUEUE]?.let(Queue::parse)?.let { queues += it }
        }
        return queues
    }
}
