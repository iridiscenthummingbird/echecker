package ua.echecker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ua.echecker.sync.SyncScheduler
import ua.echecker.ui.MainScreen
import ua.echecker.ui.MainViewModel
import ua.echecker.ui.theme.EcheckerTheme
import ua.echecker.update.Updates

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EcheckerTheme {
                val vm: MainViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                MainScreen(state, vm::selectQueue, vm::refresh, vm::installUpdate)
            }
        }
        askForNotificationsOnce()
    }

    /** Update alerts need POST_NOTIFICATIONS on Android 13+; ask a single time, never nag. */
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
