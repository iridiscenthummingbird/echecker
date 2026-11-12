package ua.echecker.widget

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.CancellationException
import ua.echecker.MainActivity
import ua.echecker.R
import ua.echecker.domain.PowerStatus
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

class PowerWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = WidgetStateLoader.load(context)
        provideContent {
            // A running Glance session only recomposes on update(); bumping REFRESH_NONCE re-runs the loader.
            val nonce = currentState<Preferences>()[REFRESH_NONCE]
            val state by produceState(initialValue = initial, nonce) {
                value = try {
                    WidgetStateLoader.load(context)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Widget state reload failed", e)
                    value
                }
            }
            WidgetContent(state)
        }
    }

    private companion object {
        const val TAG = "PowerWidget"
    }
}

class PowerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PowerWidget()
}

// Same palette as the app's hero card (ui/theme/Theme.kt).
private val OnFg = Color(0xFF1F1500)
private val OffFg = Color.White
private val OffAccent = Color(0xFFFFD25C)
private val NeutralFg = Color(0xFFE6E3DA)
private val MarkerOn = Color(0xFFD9480F)

private data class WidgetStyle(
    val background: Int,
    val iconBackground: Int,
    val fg: Color,
    val accent: Color,
    val marker: Color,
    val title: String,
    val emoji: String,
)

private fun styleFor(state: WidgetState): WidgetStyle = when (state.status) {
    is PowerStatus.On -> WidgetStyle(
        R.drawable.widget_bg_on, R.drawable.widget_icon_bg_dark, OnFg, OnFg, MarkerOn, "Світло є", "💡",
    )
    is PowerStatus.Off -> WidgetStyle(
        R.drawable.widget_bg_off, R.drawable.widget_icon_bg_light, OffFg, OffAccent, OffAccent, "Світла немає", "🌙",
    )
    else -> WidgetStyle(
        R.drawable.widget_bg_neutral, R.drawable.widget_icon_bg_light, NeutralFg, NeutralFg, OffAccent,
        state.text.title, "🔌",
    )
}

/** The moment the countdown runs to: next outage while on, power return while off. */
private fun countdownTarget(status: PowerStatus): ZonedDateTime? = when (status) {
    is PowerStatus.On -> status.nextOffAt
    is PowerStatus.Off -> status.nextOnAt
    else -> null
}

@Composable
private fun WidgetContent(state: WidgetState) {
    val size = LocalSize.current
    val tall = size.height >= 110.dp
    val showIcon = size.width >= 200.dp
    val s = styleFor(state)
    val fg = ColorProvider(s.fg)
    val muted = ColorProvider(s.fg.copy(alpha = 0.7f))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(s.background))
            .cornerRadius(24.dp)
            .padding(horizontal = 16.dp, vertical = if (tall) 14.dp else 8.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = if (tall) Alignment.Top else Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            if (showIcon) {
                val iconSize = if (tall) 44.dp else 38.dp
                Box(
                    GlanceModifier.size(iconSize).background(ImageProvider(s.iconBackground)),
                    contentAlignment = Alignment.Center,
                ) { Text(s.emoji, style = TextStyle(fontSize = if (tall) 20.sp else 17.sp)) }
                Spacer(GlanceModifier.width(12.dp))
            }
            Column {
                if (tall) {
                    Text("ЗАРАЗ", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = muted))
                }
                Text(
                    s.title,
                    style = TextStyle(fontSize = if (tall) 21.sp else 17.sp, fontWeight = FontWeight.Bold, color = fg),
                    maxLines = 1,
                )
                state.text.subtitle?.let {
                    Text(
                        it.replaceFirstChar(Char::uppercase),
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorProvider(s.accent)),
                        maxLines = 1,
                    )
                }
                countdownTarget(state.status)?.let { Countdown(it.toInstant(), s.fg.copy(alpha = 0.8f)) }
                state.text.staleNote?.let {
                    Text("⚠ $it", style = TextStyle(fontSize = 11.sp, color = muted), maxLines = 1)
                }
            }
        }
        val intervals = state.todayIntervals
        if (tall && intervals != null) {
            Spacer(GlanceModifier.defaultWeight())
            Timeline(state, intervals, s, size.width.value - 32)
        }
    }
}

/** A self-ticking "через H:MM:SS" — a RemoteViews Chronometer, so no per-second widget updates. */
@Composable
private fun Countdown(target: Instant, color: Color) {
    val context = LocalContext.current
    val left = Duration.between(Instant.now(), target).toMillis()
    val rv = RemoteViews(context.packageName, R.layout.widget_countdown).apply {
        setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + left, "через %s", true)
        setChronometerCountDown(R.id.countdown, true)
        setTextColor(R.id.countdown, color.toArgb())
    }
    AndroidRemoteViews(rv)
}

@Composable
private fun Timeline(state: WidgetState, intervals: List<ua.echecker.domain.Interval>, s: WidgetStyle, widthDp: Float) {
    val density = LocalContext.current.resources.displayMetrics.density
    val bmp = TimelineBitmap.render(
        intervals = intervals,
        nowMinute = state.nowMinute,
        widthPx = (widthDp * density).toInt(),
        density = density,
        colors = TimelineBitmap.Colors(
            track = s.fg.copy(alpha = 0.2f).toArgb(),
            off = s.fg.copy(alpha = 0.85f).toArgb(),
            marker = s.marker.toArgb(),
            label = s.fg.copy(alpha = 0.7f).toArgb(),
        ),
    )
    Image(
        provider = ImageProvider(bmp),
        contentDescription = null,
        modifier = GlanceModifier.fillMaxWidth().height((bmp.height / density).dp),
    )
}
