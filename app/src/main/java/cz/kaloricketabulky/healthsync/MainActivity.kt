package cz.kaloricketabulky.healthsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.ViewModelProvider
import cz.kaloricketabulky.healthsync.ui.MainScreen
import cz.kaloricketabulky.healthsync.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestPermissionActivityContract =
            PermissionController.createRequestPermissionResultContract()

        setContent {
            MaterialTheme {
                Surface {
                    val state by viewModel.uiState.collectAsState()

                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = requestPermissionActivityContract
                    ) { _ ->
                        viewModel.refreshHealthConnectState()
                    }

                    MainScreen(
                        state = state,
                        onEmailChanged = viewModel::onEmailChanged,
                        onPasswordChanged = viewModel::onPasswordChanged,
                        onBaseUrlChanged = viewModel::onBaseUrlChanged,
                        onDaysToSyncChanged = viewModel::onDaysToSyncChanged,
                        onGranularityChanged = viewModel::onGranularityChanged,
                        onAutoSyncChanged = viewModel::onAutoSyncChanged,
                        onRequestPermissions = {
                            permissionLauncher.launch(viewModel.healthConnectManager.permissions)
                        },
                        onTestLogin = viewModel::testLoginAndPreviewToday,
                        onSyncNow = viewModel::syncNow
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshHealthConnectState()
    }
}

