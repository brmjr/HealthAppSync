package cz.kaloricketabulky.healthsync.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.kaloricketabulky.healthsync.data.api.KalorickeTabulkyClient
import cz.kaloricketabulky.healthsync.data.model.SyncGranularity
import cz.kaloricketabulky.healthsync.data.prefs.CredentialsStore
import cz.kaloricketabulky.healthsync.health.HealthConnectAvailability
import cz.kaloricketabulky.healthsync.health.HealthConnectManager
import cz.kaloricketabulky.healthsync.sync.DaySyncSummary
import cz.kaloricketabulky.healthsync.sync.DiarySyncRepository
import cz.kaloricketabulky.healthsync.sync.PeriodicSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val email: String = "",
    val password: String = "",
    val baseUrl: String = KalorickeTabulkyClient.DEFAULT_BASE_URL,
    val daysToSync: Int = 2,
    val granularity: SyncGranularity = SyncGranularity.PER_FOOD_ITEM,
    val autoSyncEnabled: Boolean = false,
    val healthConnectAvailability: HealthConnectAvailability = HealthConnectAvailability.AVAILABLE,
    val hasHealthPermissions: Boolean = false,
    val isBusy: Boolean = false,
    val progressMessage: String? = null,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val lastSyncEpochMillis: Long = 0L,
    val syncedDaysPreview: List<DaySyncSummary> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val credentialsStore = CredentialsStore(application)
    val healthConnectManager = HealthConnectManager(application)
    private val repository = DiarySyncRepository(credentialsStore, healthConnectManager)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        val saved = credentialsStore.loadSettings()
        _uiState.update {
            it.copy(
                email = saved.email,
                password = saved.password,
                baseUrl = saved.baseUrl,
                daysToSync = saved.daysToSync,
                granularity = saved.granularity,
                autoSyncEnabled = saved.autoSyncEnabled,
                lastSyncEpochMillis = saved.lastSyncEpochMillis,
                statusMessage = saved.lastSyncSummary
            )
        }
        refreshHealthConnectState()
    }

    fun refreshHealthConnectState() {
        viewModelScope.launch {
            val availability = healthConnectManager.getAvailability()
            val granted = try {
                healthConnectManager.hasAllPermissions()
            } catch (_: Exception) {
                false
            }
            _uiState.update {
                it.copy(
                    healthConnectAvailability = availability,
                    hasHealthPermissions = granted
                )
            }
        }
    }

    fun onEmailChanged(value: String) {
        _uiState.update { it.copy(email = value) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun onBaseUrlChanged(value: String) {
        _uiState.update { it.copy(baseUrl = value) }
    }

    fun onDaysToSyncChanged(days: Int) {
        _uiState.update { it.copy(daysToSync = days) }
        persistOptions()
    }

    fun onGranularityChanged(granularity: SyncGranularity) {
        _uiState.update { it.copy(granularity = granularity) }
        persistOptions()
    }

    fun onAutoSyncChanged(enabled: Boolean) {
        _uiState.update { it.copy(autoSyncEnabled = enabled) }
        persistOptions()
        PeriodicSyncWorker.updateSchedule(getApplication(), enabled)
    }

    private fun persistOptions() {
        val current = _uiState.value
        credentialsStore.saveSyncOptions(
            daysToSync = current.daysToSync,
            granularity = current.granularity,
            autoSyncEnabled = current.autoSyncEnabled
        )
    }

    fun testLoginAndPreviewToday() {
        val current = _uiState.value
        credentialsStore.saveCredentials(current.email, current.password, current.baseUrl)

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    isError = false,
                    progressMessage = "Ověřuji přihlášení a načítám dnešní jídelníček…"
                )
            }
            try {
                val diary = repository.testConnectionAndFetchToday(
                    email = current.email,
                    password = current.password,
                    baseUrl = current.baseUrl
                )
                val previewSummary = DaySyncSummary(
                    diary = diary,
                    recordsWritten = 0
                )
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        progressMessage = null,
                        isError = false,
                        statusMessage = "Přihlášení OK! Načteno ${diary.allFoodItems.size} položek pro dnešek (${diary.date}).",
                        syncedDaysPreview = listOf(previewSummary)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        progressMessage = null,
                        isError = true,
                        statusMessage = e.localizedMessage ?: "Nepodařilo se přihlásit."
                    )
                }
            }
        }
    }

    fun syncNow() {
        val current = _uiState.value
        credentialsStore.saveCredentials(current.email, current.password, current.baseUrl)
        persistOptions()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    isError = false,
                    progressMessage = "Spouštím synchronizaci…"
                )
            }
            try {
                val result = repository.syncToHealthConnect(
                    email = current.email,
                    password = current.password,
                    baseUrl = current.baseUrl,
                    daysToSync = current.daysToSync,
                    granularity = current.granularity,
                    onProgress = { msg ->
                        _uiState.update { state -> state.copy(progressMessage = msg) }
                    }
                )
                val now = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        progressMessage = null,
                        isError = false,
                        statusMessage = result.summaryMessage,
                        lastSyncEpochMillis = now,
                        syncedDaysPreview = result.syncedDays
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        progressMessage = null,
                        isError = true,
                        statusMessage = e.localizedMessage ?: "Chyba při synchronizaci."
                    )
                }
            }
        }
    }
}

