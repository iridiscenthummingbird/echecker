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
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    private val db = AppDatabase.create(context)
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    val repository = ScheduleRepository(db.scheduleDao(), ChannelFetcher(http))
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
    }
}

val Context.container: AppContainer
    get() = (applicationContext as EcheckerApp).container
