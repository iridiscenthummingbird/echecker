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
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import ua.echecker.data.AlertPrefs
import ua.echecker.data.SyncIssue
import ua.echecker.domain.HistoryStats
import ua.echecker.domain.PeriodStats
import ua.echecker.domain.formatDuration
import ua.echecker.domain.Interval
import ua.echecker.domain.KyivTime
import ua.echecker.domain.PowerStatus
import ua.echecker.domain.Queue
import ua.echecker.domain.StatusText
import ua.echecker.ui.theme.Power
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val UK = Locale.forLanguageTag("uk")
private val DAY = DateTimeFormatter.ofPattern("dd.MM, EEE", UK)
private val DAY_SHORT = DateTimeFormatter.ofPattern("dd.MM", UK)
private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", UK)
private val SYNC = DateTimeFormatter.ofPattern("dd.MM HH:mm")

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
                item { StatusHero(state.status, state.statusText, state.today?.intervals, state.nowMinute) }
            }
            item { QueuePicker(state.queue, onSelect = onSelectQueue) }
            if (state.queue == null) {
                item {
                    Text(
                        "Оберіть чергу, щоб бачити графік",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@LazyColumn
            }
            val todayEmpty = if (state.lastSyncAt == null) "Немає даних" else "Відключень не заплановано"
            item { DayCard("Сьогодні", state.today, todayEmpty, nowMinute = state.nowMinute) }
            item { DayCard("Завтра", state.tomorrow, "Графік ще не опубліковано", nowMinute = null) }
            item { SyncRow(state.lastSyncAt, state.refreshing, state.syncIssue, onRefresh) }
            item { SectionTitle("Сповіщення", Modifier.padding(top = 6.dp, start = 4.dp)) }
            item { NotificationsCard(state.alertPrefs, state.notificationsAllowed, onAlertPrefs, onAllowNotifications) }
            item { SectionTitle("Історія", Modifier.padding(top = 6.dp, start = 4.dp)) }
            if (state.history.isEmpty()) {
                item { Text("Історія поки порожня", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
            Text("Графік світла", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                when {
                    !loaded -> ""
                    queue != null -> "Черга $queue"
                    else -> "Черга не обрана"
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
        text.uppercase(UK),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
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
                "Доступна версія ${update.version}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onSecondaryContainer,
            )
            Text(
                when (update.phase) {
                    UpdatePhase.Failed -> "Не вдалося завантажити"
                    is UpdatePhase.Ready -> "Завантажено, можна встановити"
                    else -> "Зараз у вас ${ua.echecker.update.Updates.currentVersion}"
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
                    UpdatePhase.Downloading -> "Завантаження…"
                    is UpdatePhase.Ready -> "Встановити"
                    UpdatePhase.Failed -> "Ще раз"
                    UpdatePhase.Idle -> "Оновити"
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
        is PowerStatus.On -> HeroStyle(pc.heroOn, pc.onHeroOn, "Світло є", "💡")
        is PowerStatus.Off -> HeroStyle(pc.heroOff, pc.onHeroOff, "Світла немає", "🌙")
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
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(fg.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "ЗАРАЗ",
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
                    it.replaceFirstChar { c -> c.uppercase(UK) },
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
                )
                Spacer(Modifier.height(4.dp))
                HourLabels(fg.copy(alpha = 0.65f))
            }
        }
    }
}

// ---- Queue picker ----

@Composable
internal fun QueuePicker(selected: Queue?, title: String = "Ваша черга", onSelect: (Queue) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
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
    Box(
        modifier
            .height(44.dp)
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
            .clickable(onClick = onClick),
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
private fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(40.dp)
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
            .clickable(onClick = onClick),
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
                    "⚠ Сповіщення вимкнені для застосунку",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onAllow) { Text("Дозволити", fontWeight = FontWeight.SemiBold) }
            }
        }
        Text("Перед відключенням", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (minutes in AlertPrefs.LEAD_CHOICES) {
                ChoicePill(
                    label = if (minutes == 0) "Вимк" else formatDuration(minutes),
                    selected = prefs.leadMinutes == minutes,
                    onClick = { onChange(prefs.copy(leadMinutes = minutes)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        SwitchRow("Коли світло має повернутися", prefs.backOn) { onChange(prefs.copy(backOn = it)) }
        SwitchRow("Опубліковано або змінено графік", prefs.scheduleChanges) { onChange(prefs.copy(scheduleChanges = it)) }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
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
            StatTile("7 днів", stats.week, Modifier.weight(1f))
            StatTile("30 днів", stats.month, Modifier.weight(1f))
        }
        if (stats.daily.size >= 2) {
            val maxMinutes = 24 * 60f
            val barColor = pc.off
            val track = cs.surfaceVariant
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                val n = stats.daily.size
                val gap = 4.dp.toPx()
                val w = (size.width - gap * (n - 1)) / n
                stats.daily.forEachIndexed { i, (_, minutes) ->
                    val x = i * (w + gap)
                    drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(3.dp.toPx()))
                    val h = size.height * (minutes / maxMinutes).coerceIn(0f, 1f)
                    if (h > 0f) {
                        drawRoundRect(barColor, Offset(x, size.height - h), Size(w, h), CornerRadius(3.dp.toPx()))
                    }
                }
            }
            Row {
                Text(SHORT_DAY.format(stats.daily.first().first), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("годин без світла на день", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(SHORT_DAY.format(stats.daily.last().first), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatTile(title: String, period: PeriodStats, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.6f))
            .padding(12.dp),
    ) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant)
        Text(
            formatDuration(period.offMinutes),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            if (period.days == 0) "немає даних" else "≈ ${formatDuration(period.avgMinutesPerDay)} на день",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
    }
}

private val SHORT_DAY = DateTimeFormatter.ofPattern("dd.MM")

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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (day != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "${DAY_SHORT.format(day.date)} · ${WEEKDAY.format(day.date)}",
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
        Timeline(day.intervals, nowMinute, 18.dp, pc.on, pc.off, pc.nowMarker)
        Spacer(Modifier.height(4.dp))
        HourLabels(cs.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        if (day.intervals.isEmpty()) {
            Text("Без відключень 🎉", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
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
    Row(
        Modifier
            .clip(shape)
            .background(pc.off)
            .then(if (active) Modifier.border(2.dp, pc.nowMarker, shape) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
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
    Text(
        if (intervals.isEmpty()) "0 год" else "−${formatHours(intervals.offMinutes())}",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = cs.onSecondaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(cs.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// ---- Timeline ----

/** A 24h bar: [onColor] base, [offColor] outage segments, optional "now" marker. */
@Composable
private fun Timeline(
    intervals: List<Interval>,
    nowMinute: Int?,
    height: Dp,
    onColor: Color,
    offColor: Color,
    markerColor: Color,
) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height + 8.dp),
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
    Row(Modifier.fillMaxWidth()) {
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
                lastSyncAt?.let { "Оновлено ${SYNC.format(it.atZone(KyivTime.ZONE))}" } ?: "Ще не оновлювалось",
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
                Text("↻  Оновити", fontWeight = FontWeight.SemiBold)
            }
        }
        if (issue != null && !refreshing) {
            Text(
                (if (issue.isError) "⚠ " else "ℹ ") + issue.message(),
                style = MaterialTheme.typography.bodySmall,
                color = if (issue.isError) cs.error else cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
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
        history.forEachIndexed { idx, day ->
            if (idx > 0) HorizontalDivider(color = cs.outlineVariant)
            Column(Modifier.padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(DAY.format(day.date), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (day.intervals.isEmpty()) "без відключень"
                        else "без світла ${formatHours(day.intervals.offMinutes())}",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Timeline(day.intervals, null, 8.dp, pc.on.copy(alpha = 0.6f), pc.off, pc.nowMarker)
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

private fun formatHours(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "$h год" else "$h год $m хв"
}
