package ua.echecker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import ua.echecker.R
import ua.echecker.i18n.Strings
import ua.echecker.i18n.strings
import ua.echecker.data.AlertPrefs
import ua.echecker.data.SyncIssue
import ua.echecker.domain.HistoryStats
import ua.echecker.domain.PeriodStats
import ua.echecker.domain.formatDays
import ua.echecker.domain.formatDuration
import ua.echecker.domain.Interval
import ua.echecker.domain.KyivTime
import ua.echecker.domain.PowerStatus
import ua.echecker.domain.Queue
import ua.echecker.domain.StatusFormatter
import ua.echecker.domain.StatusText
import ua.echecker.ui.theme.Power
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_SHORT = DateTimeFormatter.ofPattern("dd.MM")
private val SYNC = DateTimeFormatter.ofPattern("dd.MM HH:mm")

/** Language of the app's resources (follows the per-app language), for weekday names and casing. */
@Composable
private fun appLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
private fun strings(): Strings = LocalContext.current.strings

/** "Відключення: 13:00–18:00, 22:00–24:00" — what TalkBack reads for a timeline bar. */
@Composable
private fun timelineDescription(intervals: List<Interval>): String =
    if (intervals.isEmpty()) stringResource(R.string.a11y_timeline_none)
    else stringResource(R.string.a11y_timeline, intervals.joinToString(", ") { it.format() })

private val CardShape = RoundedCornerShape(24.dp)

@Composable
fun MainScreen(
    state: MainUiState,
    onSelectQueue: (Queue) -> Unit,
    onRefresh: () -> Unit,
    onInstallUpdate: () -> Unit = {},
    onAlertPrefs: (AlertPrefs) -> Unit = {},
    onAllowNotifications: () -> Unit = {},
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.safeDrawingPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Header(state.queue, state.loaded) }
            state.update?.let { u -> item { UpdateBanner(u, onInstallUpdate) } }
            if (!state.loaded) return@LazyColumn
            if (state.queue != null) {
                item {
                    // Formatted here, not in the ViewModel, so a language change applies at once.
                    val text = state.now?.let { StatusFormatter.format(state.status, it, state.lastSyncAt, strings()) }
                    StatusHero(state.status, text, state.today?.intervals, state.nowMinute)
                }
            }
            item { QueuePicker(state.queue, onSelect = onSelectQueue) }
            if (state.queue == null) {
                item {
                    Text(
                        stringResource(R.string.pick_queue_prompt),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@LazyColumn
            }
            item {
                val todayEmpty = stringResource(if (state.lastSyncAt == null) R.string.status_no_data else R.string.status_no_schedule)
                DayCard(stringResource(R.string.today), state.today, todayEmpty, nowMinute = state.nowMinute)
            }
            item { DayCard(stringResource(R.string.tomorrow), state.tomorrow, stringResource(R.string.not_published_yet), nowMinute = null) }
            item { SyncRow(state.lastSyncAt, state.refreshing, state.syncIssue, onRefresh) }
            item { SectionTitle(stringResource(R.string.section_notifications), Modifier.padding(top = 6.dp, start = 4.dp)) }
            item { NotificationsCard(state.alertPrefs, state.notificationsAllowed, onAlertPrefs, onAllowNotifications) }
            item { SectionTitle(stringResource(R.string.section_history), Modifier.padding(top = 6.dp, start = 4.dp)) }
            if (state.history.isEmpty()) {
                item { Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                state.historyStats?.takeIf { it.month.days > 0 }?.let { stats -> item { StatsCard(stats) } }
                item { HistoryCard(state.history) }
            }
        }
    }
}

@Composable
private fun Header(queue: Queue?, loaded: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(Power.colors.heroOn)),
            contentAlignment = Alignment.Center,
        ) { Bolt(Power.colors.onHeroOn, Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.title_schedule),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                when {
                    !loaded -> ""
                    queue != null -> stringResource(R.string.queue_label, queue.toString())
                    else -> stringResource(R.string.queue_not_selected)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Bolt(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.58f, 0f)
            lineTo(w * 0.12f, h * 0.58f)
            lineTo(w * 0.48f, h * 0.58f)
            lineTo(w * 0.40f, h)
            lineTo(w * 0.88f, h * 0.40f)
            lineTo(w * 0.52f, h * 0.40f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(appLocale()),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // Lets TalkBack users jump between sections.
        modifier = modifier.semantics { heading() },
    )
}

// ---- Update banner ----

@Composable
private fun UpdateBanner(update: UpdateUi, onInstall: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val busy = update.phase is UpdatePhase.Downloading
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(cs.secondaryContainer)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.update_available, update.version),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onSecondaryContainer,
            )
            Text(
                when (update.phase) {
                    UpdatePhase.Failed -> stringResource(R.string.update_failed)
                    is UpdatePhase.Ready -> stringResource(R.string.update_ready)
                    else -> stringResource(R.string.update_current, ua.echecker.update.Updates.currentVersion)
                },
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSecondaryContainer.copy(alpha = 0.75f),
            )
        }
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = onInstall,
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = cs.secondary, contentColor = cs.onSecondary),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = cs.onSecondary)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                when (update.phase) {
                    UpdatePhase.Downloading -> stringResource(R.string.update_downloading)
                    is UpdatePhase.Ready -> stringResource(R.string.update_install)
                    UpdatePhase.Failed -> stringResource(R.string.update_retry)
                    UpdatePhase.Idle -> stringResource(R.string.update_action)
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ---- Hero: current status ----

private data class HeroStyle(val gradient: List<Color>, val fg: Color, val title: String, val emoji: String)

@Composable
private fun StatusHero(status: PowerStatus, text: StatusText?, today: List<Interval>?, nowMinute: Int) {
    val pc = Power.colors
    val (gradient, fg, title, emoji) = when (status) {
        is PowerStatus.On -> HeroStyle(pc.heroOn, pc.onHeroOn, stringResource(R.string.status_on), "💡")
        is PowerStatus.Off -> HeroStyle(pc.heroOff, pc.onHeroOff, stringResource(R.string.status_off), "🌙")
        else -> HeroStyle(pc.heroNeutral, pc.onHeroNeutral, text?.title.orEmpty(), "🔌")
    }
    val accent = if (status is PowerStatus.Off) pc.heroOffAccent else fg
    Box(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Brush.linearGradient(gradient)),
    ) {
        // Decorative concentric rings in the top-right corner.
        Canvas(Modifier.matchParentSize()) {
            val c = Offset(size.width * 0.95f, size.height * 0.05f)
            listOf(0.45f, 0.75f, 1.05f).forEach { k ->
                drawCircle(fg.copy(alpha = 0.07f), radius = size.height * k, center = c)
            }
        }
        // Read as one item: "Зараз, Світла немає, Увімкнуть завтра о 03:00, Відключення: …".
        Column(Modifier.padding(20.dp).semantics(mergeDescendants = true) {}) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(fg.copy(alpha = 0.12f))
                        .clearAndSetSemantics {}, // decorative emoji
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        stringResource(R.string.now_label).uppercase(appLocale()),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = fg.copy(alpha = 0.65f),
                    )
                    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = fg)
                }
            }
            text?.subtitle?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it.replaceFirstChar { c -> c.uppercase(appLocale()) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
            }
            text?.staleNote?.let {
                Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.75f))
            }
            if (!today.isNullOrEmpty()) {
                Spacer(Modifier.height(16.dp))
                Timeline(
                    intervals = today,
                    nowMinute = nowMinute,
                    height = 10.dp,
                    onColor = fg.copy(alpha = 0.2f),
                    offColor = fg.copy(alpha = 0.85f),
                    markerColor = if (status is PowerStatus.Off) pc.heroOffAccent else pc.nowMarker,
                    description = timelineDescription(today),
                )
                Spacer(Modifier.height(4.dp))
                HourLabels(fg.copy(alpha = 0.65f))
            }
        }
    }
}

// ---- Queue picker ----

@Composable
internal fun QueuePicker(selected: Queue?, title: String = stringResource(R.string.your_queue), onSelect: (Queue) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
            .selectableGroup(),
    ) {
        SectionTitle(title)
        Spacer(Modifier.height(12.dp))
        Queue.ALL.chunked(6).forEachIndexed { i, row ->
            if (i > 0) Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { q -> QueuePill(q, q == selected, { onSelect(q) }, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun QueuePill(q: Queue, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val spoken = stringResource(R.string.a11y_queue, q.toString())
    Box(
        modifier
            .height(48.dp)
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(Brush.verticalGradient(Power.colors.heroOn))
                } else {
                    Modifier
                        .background(cs.surfaceVariant.copy(alpha = 0.6f))
                        .border(1.dp, cs.outlineVariant, shape)
                },
            )
            // A radio choice: TalkBack says "Черга 3.1, вибрано" instead of a bare "3.1".
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            q.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (selected) Power.colors.onHeroOn else cs.onSurface,
        )
    }
}

@Composable
private fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, spoken: String = label) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(48.dp)
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(Brush.verticalGradient(Power.colors.heroOn))
                } else {
                    Modifier
                        .background(cs.surfaceVariant.copy(alpha = 0.6f))
                        .border(1.dp, cs.outlineVariant, shape)
                },
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (selected) Power.colors.onHeroOn else cs.onSurface,
        )
    }
}

// ---- Notifications ----

@Composable
private fun NotificationsCard(
    prefs: AlertPrefs,
    allowed: Boolean,
    onChange: (AlertPrefs) -> Unit,
    onAllow: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(cs.surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!allowed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.notifications_blocked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onAllow) { Text(stringResource(R.string.notifications_allow), fontWeight = FontWeight.SemiBold) }
            }
        }
        Text(stringResource(R.string.notify_before_outage), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        val s = strings()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
            for (minutes in AlertPrefs.LEAD_CHOICES) {
                val label = if (minutes == 0) stringResource(R.string.notify_lead_off) else formatDuration(minutes, s)
                ChoicePill(
                    label = label,
                    selected = prefs.leadMinutes == minutes,
                    onClick = { onChange(prefs.copy(leadMinutes = minutes)) },
                    modifier = Modifier.weight(1f),
                    spoken = stringResource(R.string.a11y_lead, label),
                )
            }
        }
        SwitchRow(stringResource(R.string.notify_back_on), prefs.backOn) { onChange(prefs.copy(backOn = it)) }
        SwitchRow(stringResource(R.string.notify_schedule_changes), prefs.scheduleChanges) { onChange(prefs.copy(scheduleChanges = it)) }
    }
}

/** The whole row is one switch for TalkBack (label + state), not a text and a separate unlabeled switch. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

// ---- History stats ----

@Composable
private fun StatsCard(stats: HistoryStats) {
    val cs = MaterialTheme.colorScheme
    val pc = Power.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(cs.surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(stats.week, Modifier.weight(1f))
            StatTile(stats.month, Modifier.weight(1f))
        }
        if (stats.daily.size >= 2) {
            val maxMinutes = 24 * 60f
            val barColor = pc.off
            val track = cs.surfaceVariant
            // A day without a published schedule: an empty, fainter slot, so the gap stays visible.
            val noSchedule = cs.surfaceVariant.copy(alpha = 0.35f)
            val s = strings()
            val chartSpoken = stringResource(
                R.string.a11y_chart,
                stats.daily.joinToString("; ") { day ->
                    val date = DAY_SHORT.format(day.date)
                    if (day.hasSchedule) s.get(R.string.a11y_chart_day, date, formatDuration(day.offMinutes, s))
                    else s.get(R.string.a11y_chart_day_no_schedule, date)
                },
            )
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics { contentDescription = chartSpoken },
            ) {
                val n = stats.daily.size
                val gap = 4.dp.toPx()
                val w = (size.width - gap * (n - 1)) / n
                stats.daily.forEachIndexed { i, day ->
                    val x = i * (w + gap)
                    drawRoundRect(if (day.hasSchedule) track else noSchedule, Offset(x, 0f), Size(w, size.height), CornerRadius(3.dp.toPx()))
                    val h = size.height * (day.offMinutes / maxMinutes).coerceIn(0f, 1f)
                    if (h > 0f) {
                        drawRoundRect(barColor, Offset(x, size.height - h), Size(w, h), CornerRadius(3.dp.toPx()))
                    }
                }
            }
            Row(Modifier.clearAndSetSemantics {}) { // the chart's description already covers the axis
                Text(DAY_SHORT.format(stats.daily.first().date), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.stats_chart_caption), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(DAY_SHORT.format(stats.daily.last().date), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
            if (stats.daily.any { !it.hasSchedule }) {
                Text(
                    stringResource(R.string.stats_chart_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatTile(period: PeriodStats, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val s = strings()
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.6f))
            .padding(12.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        // The real number of days: shorter than 7/30 when the app's history does not reach that far back.
        Text(
            formatDays(period.days, s).uppercase(appLocale()),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = cs.onSurfaceVariant,
        )
        Text(
            formatDuration(period.offMinutes, s),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            if (period.days == 0) stringResource(R.string.stats_no_data)
            else stringResource(R.string.stats_avg_per_day, formatDuration(period.avgMinutesPerDay, s)),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
        if (period.daysWithoutSchedule > 0) {
            Text(
                stringResource(R.string.stats_days_without_schedule, formatDays(period.daysWithoutSchedule, s)),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

// ---- Day cards ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayCard(title: String, day: DaySchedule?, emptyText: String, nowMinute: Int?) {
    val cs = MaterialTheme.colorScheme
    val pc = Power.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(cs.surface)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (day != null) {
                Spacer(Modifier.width(8.dp))
                val weekday = DateTimeFormatter.ofPattern("EEE", appLocale())
                Text(
                    "${DAY_SHORT.format(day.date)} · ${weekday.format(day.date)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                OffBadge(day.intervals)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (day == null) {
            Text(emptyText, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            return@Column
        }
        // The interval pills below say the same; the bar is a visual summary only.
        Timeline(day.intervals, nowMinute, 18.dp, pc.on, pc.off, pc.nowMarker, description = null)
        Spacer(Modifier.height(4.dp))
        HourLabels(cs.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        if (day.intervals.isEmpty()) {
            Text(stringResource(R.string.no_outages_party), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                day.intervals.forEach {
                    IntervalPill(it, active = nowMinute != null && nowMinute in it.startMinute until it.endMinute)
                }
            }
        }
    }
}

@Composable
private fun IntervalPill(i: Interval, active: Boolean) {
    val pc = Power.colors
    val shape = RoundedCornerShape(50)
    val spoken = if (active) stringResource(R.string.a11y_now, i.format()) else i.format()
    Row(
        Modifier
            .clip(shape)
            .background(pc.off)
            .then(if (active) Modifier.border(2.dp, pc.nowMarker, shape) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            // The highlighted pill is the current outage: say so, the border alone is visual only.
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (active) pc.nowMarker else pc.onOff.copy(alpha = 0.5f)),
        )
        Spacer(Modifier.width(8.dp))
        Text(i.format(), style = MaterialTheme.typography.labelLarge, color = pc.onOff)
    }
}

@Composable
private fun OffBadge(intervals: List<Interval>) {
    val cs = MaterialTheme.colorScheme
    val s = strings()
    val total = formatDuration(intervals.offMinutes(), s)
    // "−9 год" reads as "minus 9 hours"; TalkBack gets "без світла 9 год" instead.
    val spoken = if (intervals.isEmpty()) stringResource(R.string.no_outages) else stringResource(R.string.without_power, total)
    Text(
        if (intervals.isEmpty()) s.get(R.string.duration_h, 0) else stringResource(R.string.off_badge, total),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = cs.onSecondaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(cs.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = spoken },
    )
}

// ---- Timeline ----

/**
 * A 24h bar: [onColor] base, [offColor] outage segments, optional "now" marker.
 * [description] is what TalkBack reads; null hides the bar when the same information is shown as text nearby.
 */
@Composable
private fun Timeline(
    intervals: List<Interval>,
    nowMinute: Int?,
    height: Dp,
    onColor: Color,
    offColor: Color,
    markerColor: Color,
    description: String?,
) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height + 8.dp)
            .clearAndSetSemantics { if (description != null) contentDescription = description },
    ) {
        val barTop = 4.dp.toPx()
        val barH = height.toPx()
        drawRoundRect(
            onColor,
            topLeft = Offset(0f, barTop),
            size = Size(size.width, barH),
            cornerRadius = CornerRadius(barH / 2),
        )
        for (h in listOf(6, 12, 18)) {
            val x = size.width * h / 24f
            drawLine(offColor.copy(alpha = 0.2f), Offset(x, barTop), Offset(x, barTop + barH), strokeWidth = 1.dp.toPx())
        }
        intervals.forEach { i ->
            val x0 = size.width * i.startMinute / 1440f
            val x1 = size.width * i.endMinute / 1440f
            drawRoundRect(
                offColor,
                topLeft = Offset(x0, barTop),
                size = Size((x1 - x0).coerceAtLeast(2f), barH),
                cornerRadius = CornerRadius(barH / 3),
            )
        }
        if (nowMinute != null) {
            val x = (size.width * nowMinute / 1440f).coerceIn(2.dp.toPx(), size.width - 2.dp.toPx())
            drawLine(markerColor, Offset(x, 2.dp.toPx()), Offset(x, size.height), strokeWidth = 3.dp.toPx())
            drawCircle(markerColor, radius = 4.dp.toPx(), center = Offset(x, 4.dp.toPx()))
        }
    }
}

@Composable
private fun HourLabels(color: Color) {
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) { // axis ticks: noise for TalkBack
        listOf("00", "06", "12", "18", "24").forEachIndexed { i, h ->
            val edge = i == 0 || i == 4
            Text(
                h,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                textAlign = when (i) {
                    0 -> TextAlign.Start
                    4 -> TextAlign.End
                    else -> TextAlign.Center
                },
                modifier = Modifier.weight(if (edge) 0.5f else 1f),
            )
        }
    }
}

// ---- Sync ----

@Composable
private fun SyncRow(lastSyncAt: Instant?, refreshing: Boolean, issue: SyncIssue?, onRefresh: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val dot = when {
        issue?.isError == true -> cs.error
        issue != null -> Color(0xFFE0A030)
        lastSyncAt != null -> Color(0xFF3BB273)
        else -> cs.outline
    }
    Column(Modifier.padding(horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dot),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                lastSyncAt?.let { stringResource(R.string.updated_at, SYNC.format(it.atZone(KyivTime.ZONE))) }
                    ?: stringResource(R.string.never_updated),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onRefresh,
                enabled = !refreshing,
                colors = ButtonDefaults.buttonColors(containerColor = cs.secondary, contentColor = cs.onSecondary),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            ) {
                if (refreshing) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = cs.onSecondary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.refresh), fontWeight = FontWeight.SemiBold)
            }
        }
        if (issue != null && !refreshing) {
            Text(
                (if (issue.isError) "⚠ " else "ℹ ") + issue.message(strings()),
                style = MaterialTheme.typography.bodySmall,
                color = if (issue.isError) cs.error else cs.onSurfaceVariant,
                // Announced when it appears, e.g. right after tapping Refresh while offline.
                modifier = Modifier
                    .padding(top = 6.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

// ---- History ----

@Composable
private fun HistoryCard(history: List<DaySchedule>) {
    val cs = MaterialTheme.colorScheme
    val pc = Power.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(cs.surface)
            .padding(horizontal = 18.dp, vertical = 4.dp),
    ) {
        val s = strings()
        val dayFormat = DateTimeFormatter.ofPattern("dd.MM, EEE", appLocale())
        history.forEachIndexed { idx, day ->
            if (idx > 0) HorizontalDivider(color = cs.outlineVariant)
            // One TalkBack item per day: "11.11, ср, без світла 5 год, 05:30–07:00 16:00–19:30".
            Column(Modifier.padding(vertical = 12.dp).semantics(mergeDescendants = true) {}) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dayFormat.format(day.date), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (day.intervals.isEmpty()) stringResource(R.string.no_outages)
                        else stringResource(R.string.without_power, formatDuration(day.intervals.offMinutes(), s)),
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Timeline(day.intervals, null, 8.dp, pc.on.copy(alpha = 0.6f), pc.off, pc.nowMarker, description = null)
                if (day.intervals.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        day.intervals.joinToString("   ") { it.format() },
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun List<Interval>.offMinutes(): Int = sumOf { it.endMinute - it.startMinute }
