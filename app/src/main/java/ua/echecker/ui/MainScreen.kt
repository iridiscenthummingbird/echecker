package ua.echecker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ua.echecker.domain.KyivTime
import ua.echecker.domain.Queue
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("dd.MM, EEE", Locale.forLanguageTag("uk"))
private val SYNC = DateTimeFormatter.ofPattern("dd.MM HH:mm")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(state: MainUiState, onSelectQueue: (Queue) -> Unit, onRefresh: () -> Unit) {
    LazyColumn(
        modifier = Modifier.safeDrawingPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Ваша черга", style = MaterialTheme.typography.titleMedium) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Queue.ALL.forEach { q ->
                    FilterChip(
                        selected = q == state.queue,
                        onClick = { onSelectQueue(q) },
                        label = { Text(q.toString()) },
                    )
                }
            }
        }
        if (state.queue == null) {
            item { Text("Оберіть чергу, щоб бачити графік") }
            return@LazyColumn
        }
        item { DayCard("Сьогодні", state.today, emptyText = "Відключень не заплановано") }
        item { DayCard("Завтра", state.tomorrow, emptyText = "Графік ще не опубліковано") }
        item { SyncRow(state.lastSyncAt, state.refreshing, onRefresh) }
        item {
            HorizontalDivider()
            Text("Історія", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        }
        if (state.history.isEmpty()) {
            item { Text("Історія поки порожня") }
        }
        items(state.history, key = { it.date.toString() }) { day ->
            Row(Modifier.fillMaxWidth()) {
                Text(DAY.format(day.date), modifier = Modifier.width(110.dp))
                Text(day.intervals.toText())
            }
        }
    }
}

@Composable
private fun DayCard(title: String, day: DaySchedule?, emptyText: String) {
    Card(Modifier.fillMaxWidth()) {
        Text(
            if (day != null) "$title · ${DAY.format(day.date)}" else title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
        )
        Text(
            day?.intervals?.toText() ?: emptyText,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun SyncRow(lastSyncAt: Instant?, refreshing: Boolean, onRefresh: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            lastSyncAt?.let { "Оновлено ${SYNC.format(it.atZone(KyivTime.ZONE))}" } ?: "Ще не оновлювалось",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        if (refreshing) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        }
        Button(onClick = onRefresh, enabled = !refreshing) { Text("Оновити") }
    }
}

private fun List<ua.echecker.domain.Interval>.toText(): String =
    if (isEmpty()) "Без відключень" else joinToString(", ") { it.format() }
