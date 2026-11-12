package ua.echecker

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import okhttp3.OkHttpClient
import ua.echecker.data.ChannelFetcher
import ua.echecker.data.ScheduleRepository
import ua.echecker.data.SettingsStore
import ua.echecker.data.db.AppDatabase
import ua.echecker.sync.SyncScheduler
import ua.echecker.update.UpdateChecker
import ua.echecker.update.Updates
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    private val db = AppDatabase.create(context)
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    val repository = ScheduleRepository(db.scheduleDao(), ChannelFetcher(http))
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
}

val Context.container: AppContainer
    get() = (applicationContext as EcheckerApp).container
