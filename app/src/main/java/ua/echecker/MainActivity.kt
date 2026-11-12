package ua.echecker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import ua.echecker.notify.Notifications
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ua.echecker.sync.SyncScheduler
import ua.echecker.ui.MainScreen
import ua.echecker.ui.MainViewModel
import ua.echecker.ui.theme.EcheckerTheme
import ua.echecker.update.Updates

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onResume() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EcheckerTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                MainScreen(
                    state = state,
                    onSelectQueue = vm::selectQueue,
                    onRefresh = vm::refresh,
                    onInstallUpdate = vm::installUpdate,
                    onAlertPrefs = vm::setAlertPrefs,
                    onAllowNotifications = { startActivity(Notifications.settingsIntent(this)) },
                )
            }
        }
        askForNotificationsOnce()
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    /** Outage and update alerts need POST_NOTIFICATIONS on Android 13+; ask a single time, never nag. */
    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        lifecycleScope.launch {
            val settings = container.settings
            if (settings.notificationsAsked.first()) return@launch
            settings.markNotificationsAsked()
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { SyncScheduler.syncOnOpen(applicationContext) }
        lifecycleScope.launch { Updates.checkOnOpen(applicationContext) }
    }
}
