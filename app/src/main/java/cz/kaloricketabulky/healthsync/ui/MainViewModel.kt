package cz.kaloricketabulky.healthsync.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.kaloricketabulky.healthsync.data.api.KalorickeTabulkyClient
import cz.kaloricketabulky.healthsync.data.model.MealSlotTimes
import cz.kaloricketabulky.healthsync.data.model.SyncGranularity
import cz.kaloricketabulky.healthsync.data.prefs.CredentialsStore
import cz.kaloricketabulky.healthsync.health.HealthConnectAvailability
import cz.kaloricketabulky.healthsync.health.HealthConnectManager
import cz.kaloricketabulky.healthsync.sync.DaySyncSummary
import cz.kaloricketabulky.healthsync.sync.DiarySyncRepository
import cz.kaloricketabulky.healthsync.sync.PeriodicSyncWorker
import cz.kaloricketabulky.healthsync.update.ReleaseUpdateInfo
import cz.kaloricketabulky.healthsync.update.UpdateChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class MainUiState(
    val email: String = "",
    val password: String = "",
    val baseUrl: String = KalorickeTabulkyClient.DEFAULT_BASE_URL,
    val daysToSync: Int = 2,
    val granularity: SyncGranularity = SyncGranularity.PER_FOOD_ITEM,
    val syncHydration: Boolean = true,
    val autoSyncEnabled: Boolean = false,
    val eveningSyncEnabled: Boolean = false,
    val eveningSyncTime: String = "21:30",
    val mealSlotTimes: MealSlotTimes = MealSlotTimes(),
    val backfillStartDate: String = "",
    val backfillEndDate: String = "",
    val healthConnectAvailability: HealthConnectAvailability = HealthConnectAvailability.AVAILABLE,
    val hasHealthPermissions: Boolean = false,
    val isBusy: Boolean = false,
    val progressMessage: String? = null,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val lastSyncEpochMillis: Long = 0L,
    val syncedDaysPreview: List<DaySyncSummary> = emptyList(),
    val isCheckingUpdate: Boolean = false,
    val latestReleaseInfo: ReleaseUpdateInfo? = null,
    val updateStatusMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val credentialsStore = CredentialsStore(application)
    val healthConnectManager = HealthConnectManager(application)
    private val repository = DiarySyncRepository(credentialsStore, healthConnectManager)
    private val updateChecker = UpdateChecker()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        val saved = credentialsStore.loadSettings()
        val today = LocalDate.now(DiarySyncRepository.PRAGUE_ZONE)
        val defaultStart = today.minusDays(29).format(DMY_FORMATTER)
        val defaultEnd = today.format(DMY_FORMATTER)

        _uiState.update {
            it.copy(
                email = saved.email,
                password = saved.password,
                baseUrl = saved.baseUrl,
                daysToSync = saved.daysToSync,
                granularity = saved.granularity,
                syncHydration = saved.syncHydration,
                autoSyncEnabled = saved.autoSyncEnabled,
                eveningSyncEnabled = saved.eveningSyncEnabled,
                eveningSyncTime = saved.eveningSyncTime,
                mealSlotTimes = saved.mealSlotTimes,
                backfillStartDate = defaultStart,
                backfillEndDate = defaultEnd,
                lastSyncEpochMillis = saved.lastSyncEpochMillis,
                statusMessage = saved.lastSyncSummary
            )
        }
        refreshHealthConnectState()
    }

    fun refreshHealthConnectState() {
        viewModelScope.launch {
            val availability = healthConnectManager.getAvailability()
            val syncHydration = _uiState.value.syncHydration
            val granted = try {
                healthConnectManager.hasRequiredPermissions(syncHydration)
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

    fun onSyncHydrationChanged(enabled: Boolean) {
        _uiState.update { it.copy(syncHydration = enabled) }
        persistOptions()
        refreshHealthConnectState()
    }

    fun onAutoSyncChanged(enabled: Boolean) {
        _uiState.update { it.copy(autoSyncEnabled = enabled) }
        persistOptions()
        PeriodicSyncWorker.updateSchedule(getApplication(), enabled)
    }

    fun onEveningSyncChanged(enabled: Boolean) {
        _uiState.update { it.copy(eveningSyncEnabled = enabled) }
        persistOptions()
        PeriodicSyncWorker.updateEveningSchedule(
            getApplication(),
            enabled,
            _uiState.value.eveningSyncTime
        )
    }

    fun onEveningSyncTimeChanged(time: String) {
        _uiState.update { it.copy(eveningSyncTime = time) }
        persistOptions()
        if (_uiState.value.eveningSyncEnabled) {
            PeriodicSyncWorker.updateEveningSchedule(getApplication(), true, time)
        }
    }

    fun onMealSlotTimesChanged(newTimes: MealSlotTimes) {
        _uiState.update { it.copy(mealSlotTimes = newTimes) }
        persistOptions()
    }

    fun resetMealSlotTimes() {
        onMealSlotTimesChanged(MealSlotTimes())
    }

    fun onBackfillStartDateChanged(value: String) {
        _uiState.update { it.copy(backfillStartDate = value) }
    }

    fun onBackfillEndDateChanged(value: String) {
        _uiState.update { it.copy(backfillEndDate = value) }
    }

    fun selectBackfillPresetDays(daysBack: Int) {
        val today = LocalDate.now(DiarySyncRepository.PRAGUE_ZONE)
        val start = today.minusDays((daysBack - 1).toLong())
        _uiState.update {
            it.copy(
                backfillStartDate = start.format(DMY_FORMATTER),
                backfillEndDate = today.format(DMY_FORMATTER)
            )
        }
    }

    fun selectBackfillCurrentMonth() {
        val today = LocalDate.now(DiarySyncRepository.PRAGUE_ZONE)
        val start = today.withDayOfMonth(1)
        _uiState.update {
            it.copy(
                backfillStartDate = start.format(DMY_FORMATTER),
                backfillEndDate = today.format(DMY_FORMATTER)
            )
        }
    }

    fun selectBackfillPreviousMonth() {
        val today = LocalDate.now(DiarySyncRepository.PRAGUE_ZONE)
        val prevMonth = today.minusMonths(1)
        val start = prevMonth.withDayOfMonth(1)
        val end = prevMonth.withDayOfMonth(prevMonth.lengthOfMonth())
        _uiState.update {
            it.copy(
                backfillStartDate = start.format(DMY_FORMATTER),
                backfillEndDate = end.format(DMY_FORMATTER)
            )
        }
    }

    private fun persistOptions() {
        val current = _uiState.value
        credentialsStore.saveSyncOptions(
            daysToSync = current.daysToSync,
            granularity = current.granularity,
            syncHydration = current.syncHydration,
            autoSyncEnabled = current.autoSyncEnabled,
            eveningSyncEnabled = current.eveningSyncEnabled,
            eveningSyncTime = current.eveningSyncTime,
            mealSlotTimes = current.mealSlotTimes
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
                    recordsWritten = 0,
                    hydrationLitersWritten = diary.drinkRegimeLiters
                )
                val drinkStr = diary.drinkRegimeLiters?.let { ", pitný režim: ${it}l" } ?: ""
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        progressMessage = null,
                        isError = false,
                        statusMessage = "Přihlášení OK! Načteno ${diary.allFoodItems.size} položek pro dnešek (${diary.date}$drinkStr).",
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
                    mealSlotTimes = current.mealSlotTimes,
                    syncHydration = current.syncHydration,
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

    fun runHistoricalBackfill() {
        val current = _uiState.value
        credentialsStore.saveCredentials(current.email, current.password, current.baseUrl)
        persistOptions()

        val startDate = try {
            LocalDate.parse(current.backfillStartDate.trim(), DMY_FORMATTER)
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    isError = true,
                    statusMessage = "Neplatné počáteční datum (${current.backfillStartDate}). Použijte formát DD.MM.RRRR."
                )
            }
            return
        }

        val endDate = try {
            LocalDate.parse(current.backfillEndDate.trim(), DMY_FORMATTER)
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    isError = true,
                    statusMessage = "Neplatné koncové datum (${current.backfillEndDate}). Použijte formát DD.MM.RRRR."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    isError = false,
                    progressMessage = "Spouštím historický import…"
                )
            }
            try {
                val result = repository.syncDateRange(
                    email = current.email,
                    password = current.password,
                    baseUrl = current.baseUrl,
                    startDate = startDate,
                    endDate = endDate,
                    granularity = current.granularity,
                    mealSlotTimes = current.mealSlotTimes,
                    syncHydration = current.syncHydration,
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
                        syncedDaysPreview = result.syncedDays.take(14)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        progressMessage = null,
                        isError = true,
                        statusMessage = e.localizedMessage ?: "Chyba při historickém importu."
                    )
                }
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCheckingUpdate = true,
                    updateStatusMessage = "Kontroluji GitHub Releases…"
                )
            }
            try {
                val info = updateChecker.fetchLatestRelease(preferV2 = true)
                _uiState.update {
                    it.copy(
                        isCheckingUpdate = false,
                        latestReleaseInfo = info,
                        updateStatusMessage = "Nejnovější dostupný build na GitHubu: ${info.title} (${info.tagName})"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCheckingUpdate = false,
                        updateStatusMessage = "Nepodařilo se zkontrolovat aktualizace: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    companion object {
        private val DMY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d.M.yyyy")
    }
}
