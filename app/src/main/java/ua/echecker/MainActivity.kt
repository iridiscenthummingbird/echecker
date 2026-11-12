package ua.echecker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ua.echecker.sync.SyncScheduler
import ua.echecker.ui.MainScreen
import ua.echecker.ui.MainViewModel
import ua.echecker.ui.theme.EcheckerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EcheckerTheme {
                val vm: MainViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                MainScreen(state, vm::selectQueue, vm::refresh)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { SyncScheduler.syncOnOpen(applicationContext) }
    }
}
