package ua.echecker.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import ua.echecker.R
import ua.echecker.domain.Queue
import ua.echecker.ui.QueuePicker
import ua.echecker.ui.theme.EcheckerTheme

/** Picks the queue of a newly added (or reconfigured) [QueueWidget]. */
class QueueWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out must not leave an unconfigured widget on the home screen.
        setResult(RESULT_CANCELED, resultIntent(appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@QueueWidgetConfigActivity).getGlanceIdBy(appWidgetId)
            val current = getAppWidgetState<Preferences>(this@QueueWidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)[WIDGET_QUEUE]
                ?.let(Queue::parse)
            setContent {
                EcheckerTheme {
                    var selected by remember { mutableStateOf(current) }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(
                            Modifier.safeDrawingPadding().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                stringResource(R.string.widget_setup_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                stringResource(R.string.widget_setup_text),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            QueuePicker(selected, title = stringResource(R.string.widget_setup_pick)) { q ->
                                selected = q
                                save(appWidgetId, q)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun save(appWidgetId: Int, queue: Queue) {
        lifecycleScope.launch {
            val manager = GlanceAppWidgetManager(this@QueueWidgetConfigActivity)
            val glanceId = manager.getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@QueueWidgetConfigActivity, glanceId) { it[WIDGET_QUEUE] = queue.toString() }
            QueueWidget().update(this@QueueWidgetConfigActivity, glanceId)
            WidgetUpdater.refreshAll(applicationContext)
            setResult(RESULT_OK, resultIntent(appWidgetId))
            finish()
        }
    }

    private fun resultIntent(appWidgetId: Int) = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
