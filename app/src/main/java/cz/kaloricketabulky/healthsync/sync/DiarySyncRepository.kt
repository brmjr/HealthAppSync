package cz.kaloricketabulky.healthsync.sync

import cz.kaloricketabulky.healthsync.data.api.KalorickeTabulkyClient
import cz.kaloricketabulky.healthsync.data.model.KtDayDiary
import cz.kaloricketabulky.healthsync.data.model.SyncGranularity
import cz.kaloricketabulky.healthsync.data.prefs.CredentialsStore
import cz.kaloricketabulky.healthsync.health.HealthConnectAvailability
import cz.kaloricketabulky.healthsync.health.HealthConnectManager
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

data class DaySyncSummary(
    val diary: KtDayDiary,
    val recordsWritten: Int
)

data class SyncRunResult(
    val syncedDays: List<DaySyncSummary>,
    val totalRecordsWritten: Int,
    val summaryMessage: String
)

class DiarySyncRepository(
    private val credentialsStore: CredentialsStore,
    private val healthConnectManager: HealthConnectManager
) {
    /**
     * Tests login against Kalorické Tabulky and fetches today's diary without writing to Health Connect.
     */
    suspend fun testConnectionAndFetchToday(
        email: String,
        password: String,
        baseUrl: String
    ): KtDayDiary {
        val client = KalorickeTabulkyClient(baseUrl = baseUrl)
        client.login(email, password)
        val today = LocalDate.now(PRAGUE_ZONE)
        return client.getDiary(today)
    }

    /**
     * Logs into Kalorické Tabulky, fetches the last [daysToSync] days (including today),
     * and writes NutritionRecords into Health Connect.
     */
    suspend fun syncToHealthConnect(
        email: String,
        password: String,
        baseUrl: String,
        daysToSync: Int,
        granularity: SyncGranularity,
        onProgress: (String) -> Unit = {}
    ): SyncRunResult {
        if (healthConnectManager.getAvailability() != HealthConnectAvailability.AVAILABLE) {
            throw IllegalStateException("Health Connect není v tomto zařízení dostupný.")
        }
        if (!healthConnectManager.hasAllPermissions()) {
            throw IllegalStateException("Aplikace nemá uděleno oprávnění pro zápis výživy do Health Connect.")
        }

        onProgress("Přihlašování ke Kalorickým Tabulkám…")
        val client = KalorickeTabulkyClient(baseUrl = baseUrl)
        client.login(email, password)

        val today = LocalDate.now(PRAGUE_ZONE)
        val dayCount = daysToSync.coerceIn(1, 30)
        val syncedDays = mutableListOf<DaySyncSummary>()
        var totalWritten = 0

        for (offset in 0 until dayCount) {
            val targetDate = today.minusDays(offset.toLong())
            onProgress("Synchronizuji den $targetDate (${offset + 1}/$dayCount)…")
            val diary = client.getDiary(targetDate)
            val written = healthConnectManager.syncDayDiary(
                diary = diary,
                granularity = granularity,
                zoneId = PRAGUE_ZONE
            )
            syncedDays.add(DaySyncSummary(diary = diary, recordsWritten = written))
            totalWritten += written
        }

        val todayKcal = syncedDays.firstOrNull()?.diary?.totalLoggedKcal?.roundToInt() ?: 0
        val summary = "Zapsáno $totalWritten záznamů za $dayCount dní (dnes: $todayKcal kcal)"
        credentialsStore.saveLastSyncResult(summary)

        return SyncRunResult(
            syncedDays = syncedDays,
            totalRecordsWritten = totalWritten,
            summaryMessage = summary
        )
    }

    companion object {
        val PRAGUE_ZONE: ZoneId = ZoneId.of("Europe/Prague")
    }
}

