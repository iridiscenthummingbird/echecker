package ua.echecker

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import ua.echecker.widget.WidgetUpdater
import ua.echecker.data.ChannelFetcher
import ua.echecker.data.ScheduleRepository
import ua.echecker.data.SettingsStore
import ua.echecker.data.WordPressSource
import ua.echecker.data.db.AppDatabase
import ua.echecker.sync.SyncScheduler
import ua.echecker.update.UpdateChecker
import ua.echecker.update.Updates
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    private val db = AppDatabase.create(context)
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    val repository = ScheduleRepository(
        db.scheduleDao(),
        primary = ChannelFetcher(http),
        // Consulted only when zoe_alarm fails or leaves a gap; zoe_alarm data always wins over theirs.
        backups = listOf(ChannelFetcher(http, ChannelFetcher.OBLENERGO_CHANNEL), WordPressSource(http)),
    )
    val updateChecker = UpdateChecker(http)

    /** APK downloads can take a while on slow mobile data; no overall call timeout. */
    val downloadHttp: OkHttpClient = http.newBuilder().callTimeout(0, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).build()
}

class EcheckerApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncScheduler.schedulePeriodic(this)
        Updates.schedulePeriodic(this)
    }

    /** Language (system or per-app) changed: re-render the widgets so they don't keep the old one. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            runCatching { WidgetUpdater.refreshAll(this@EcheckerApp) }
        }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as EcheckerApp).container
