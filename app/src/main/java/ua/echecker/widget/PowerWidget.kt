package ua.echecker.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.currentState
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.TextStyle
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CancellationException
import ua.echecker.MainActivity
import ua.echecker.domain.StatusText

class PowerWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = WidgetStateLoader.load(context)
        provideContent {
            // A running Glance session only recomposes on update(); bumping REFRESH_NONCE re-runs the loader.
            val nonce = currentState<Preferences>()[REFRESH_NONCE]
            val text by produceState(initialValue = initial, nonce) {
                value = try {
                    WidgetStateLoader.load(context)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Widget state reload failed", e)
                    value
                }
            }
            GlanceTheme { WidgetContent(text) }
        }
    }

    private companion object {
        const val TAG = "PowerWidget"
    }
}

class PowerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PowerWidget()
}

@Composable
private fun WidgetContent(text: StatusText) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.title,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
            maxLines = 1,
        )
        text.subtitle?.let {
            Text(it, style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurface), maxLines = 1)
        }
        text.staleNote?.let {
            Text(it, style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
        }
    }
}
