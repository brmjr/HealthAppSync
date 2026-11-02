package cz.kaloricketabulky.healthsync

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
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
            val darkTheme = isSystemInDarkTheme()
            val context = LocalContext.current
            val colorScheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                darkTheme -> darkColorScheme()
                else -> lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
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
                        onSyncHydrationChanged = viewModel::onSyncHydrationChanged,
                        onAutoSyncChanged = viewModel::onAutoSyncChanged,
                        onEveningSyncChanged = viewModel::onEveningSyncChanged,
                        onEveningSyncTimeChanged = viewModel::onEveningSyncTimeChanged,
                        onMealSlotTimesChanged = viewModel::onMealSlotTimesChanged,
                        onResetMealSlotTimes = viewModel::resetMealSlotTimes,
                        onBackfillStartDateChanged = viewModel::onBackfillStartDateChanged,
                        onBackfillEndDateChanged = viewModel::onBackfillEndDateChanged,
                        onSelectBackfillPresetDays = viewModel::selectBackfillPresetDays,
                        onSelectBackfillCurrentMonth = viewModel::selectBackfillCurrentMonth,
                        onSelectBackfillPreviousMonth = viewModel::selectBackfillPreviousMonth,
                        onRunHistoricalBackfill = viewModel::runHistoricalBackfill,
                        onCheckForUpdates = viewModel::checkForUpdates,
                        onRequestPermissions = {
                            permissionLauncher.launch(
                                viewModel.healthConnectManager.requiredPermissions(state.syncHydration)
                            )
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
