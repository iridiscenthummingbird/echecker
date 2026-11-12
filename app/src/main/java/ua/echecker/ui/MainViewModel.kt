package ua.echecker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.echecker.container
import ua.echecker.domain.Interval
import ua.echecker.domain.KyivTime
import ua.echecker.domain.PowerStatus
import ua.echecker.domain.PowerStatusCalculator
import ua.echecker.domain.StatusFormatter
import ua.echecker.domain.StatusText
import ua.echecker.domain.Queue
import ua.echecker.domain.Schedule
import ua.echecker.sync.SyncScheduler
import ua.echecker.widget.WidgetUpdater
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

data class DaySchedule(val date: LocalDate, val intervals: List<Interval>)

data class MainUiState(
    /** False until settings and schedules have been read once; avoids flashing "pick a queue". */
    val loaded: Boolean = false,
    val queue: Queue? = null,
    val today: DaySchedule? = null,
    val tomorrow: DaySchedule? = null,
    val history: List<DaySchedule> = emptyList(),
    val lastSyncAt: Instant? = null,
    val refreshing: Boolean = false,
    /** Kyiv minute of day, for the "now" marker on today's timeline. */
    val nowMinute: Int = 0,
    val status: PowerStatus = PowerStatus.QueueNotSelected,
    val statusText: StatusText? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container

    /** Today's Kyiv date; re-emits just after each Kyiv midnight. */
    private val today: Flow<LocalDate> = flow {
        while (true) {
            val now = ZonedDateTime.now(KyivTime.ZONE)
            emit(now.toLocalDate())
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(KyivTime.ZONE)
            delay(Duration.between(now, nextMidnight).toMillis() + 1_000)
        }
    }.distinctUntilChanged()

    /** Kyiv wall clock; ticks at the start of every minute. */
    private val now: Flow<ZonedDateTime> = flow {
        while (true) {
            val now = ZonedDateTime.now(KyivTime.ZONE)
            emit(now)
            delay(60_000L - (now.second * 1_000L + now.nano / 1_000_000) + 50)
        }
    }

    private val schedules: Flow<Pair<LocalDate, List<Schedule>>> = today.flatMapLatest { day ->
        c.repository.observeRange(day.minusDays(30), day.plusDays(1)).map { day to it }
    }

    val state: StateFlow<MainUiState> = combine(
        c.settings.queue,
        schedules,
        c.settings.lastSyncAt,
        SyncScheduler.observeSyncNowRunning(app),
        now,
    ) { queue, (today, schedules), lastSync, refreshing, now ->
        fun Schedule.forQueue() = DaySchedule(date, queue?.let { intervalsFor(it) }.orEmpty())
        val byDate = schedules.associateBy { it.date }
        val status = PowerStatusCalculator.calculate(
            byDate[today], byDate[today.plusDays(1)], queue, now, hasSynced = lastSync != null,
        )
        MainUiState(
            loaded = true,
            queue = queue,
            today = byDate[today]?.forQueue(),
            tomorrow = byDate[today.plusDays(1)]?.forQueue(),
            history = schedules.filter { it.date < today }.map { it.forQueue() },
            lastSyncAt = lastSync,
            refreshing = refreshing,
            nowMinute = now.hour * 60 + now.minute,
            status = status,
            statusText = StatusFormatter.format(status, now, lastSync),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun selectQueue(q: Queue) {
        viewModelScope.launch {
            c.settings.setQueue(q)
            WidgetUpdater.refreshAll(getApplication())
        }
    }

    fun refresh() {
        SyncScheduler.syncNow(getApplication())
    }
}
