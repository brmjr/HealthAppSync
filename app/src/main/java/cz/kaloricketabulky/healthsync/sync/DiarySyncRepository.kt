package cz.kaloricketabulky.healthsync.sync

import cz.kaloricketabulky.healthsync.data.api.KalorickeTabulkyClient
import cz.kaloricketabulky.healthsync.data.model.KtDayDiary
import cz.kaloricketabulky.healthsync.data.model.MealSlotTimes
import cz.kaloricketabulky.healthsync.data.model.SyncGranularity
import cz.kaloricketabulky.healthsync.data.prefs.CredentialsStore
import cz.kaloricketabulky.healthsync.health.HealthConnectAvailability
import cz.kaloricketabulky.healthsync.health.HealthConnectManager
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

data class DaySyncSummary(
    val diary: KtDayDiary,
    val recordsWritten: Int,
    val hydrationLitersWritten: Double? = null
)

data class SyncRunResult(
    val syncedDays: List<DaySyncSummary>,
    val totalRecordsWritten: Int,
    val totalHydrationDays: Int,
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
     * and writes NutritionRecords (and optional HydrationRecords) into Health Connect.
     */
    suspend fun syncToHealthConnect(
        email: String,
        password: String,
        baseUrl: String,
        daysToSync: Int,
        granularity: SyncGranularity,
        mealSlotTimes: MealSlotTimes = MealSlotTimes(),
        syncHydration: Boolean = true,
        onProgress: (String) -> Unit = {}
    ): SyncRunResult {
        val today = LocalDate.now(PRAGUE_ZONE)
        val dayCount = daysToSync.coerceIn(1, 30)
        val startDate = today.minusDays((dayCount - 1).toLong())
        return syncDateRange(
            email = email,
            password = password,
            baseUrl = baseUrl,
            startDate = startDate,
            endDate = today,
            granularity = granularity,
            mealSlotTimes = mealSlotTimes,
            syncHydration = syncHydration,
            onProgress = onProgress
        )
    }

    /**
     * Synchronizes an arbitrary inclusive date range ([startDate] .. [endDate]) from newest to oldest,
     * with polite rate-limiting for multi-week/month historical backfills.
     */
    suspend fun syncDateRange(
        email: String,
        password: String,
        baseUrl: String,
        startDate: LocalDate,
        endDate: LocalDate,
        granularity: SyncGranularity,
        mealSlotTimes: MealSlotTimes = MealSlotTimes(),
        syncHydration: Boolean = true,
        onProgress: (String) -> Unit = {}
    ): SyncRunResult {
        if (healthConnectManager.getAvailability() != HealthConnectAvailability.AVAILABLE) {
            throw IllegalStateException("Health Connect není v tomto zařízení dostupný.")
        }
        if (!healthConnectManager.hasRequiredPermissions(syncHydration)) {
            throw IllegalStateException(
                "Aplikace nemá udělena všechna potřebná oprávnění v Health Connect (Výživa" +
                    (if (syncHydration) " a Pitný režim" else "") + ")."
            )
        }

        val effectiveStart = if (startDate.isBefore(endDate)) startDate else endDate
        val effectiveEnd = if (startDate.isBefore(endDate)) endDate else startDate
        val totalDays = (ChronoUnit.DAYS.between(effectiveStart, effectiveEnd).toInt() + 1)
            .coerceIn(1, MAX_BACKFILL_DAYS)

        onProgress("Přihlašování ke Kalorickým Tabulkám…")
        val client = KalorickeTabulkyClient(baseUrl = baseUrl)
        client.login(email, password)

        val syncedDays = mutableListOf<DaySyncSummary>()
        var totalWritten = 0
        var totalHydrationDays = 0

        for (offset in 0 until totalDays) {
            val targetDate = effectiveEnd.minusDays(offset.toLong())
            onProgress("Synchronizuji $targetDate (${offset + 1}/$totalDays)…")

            val diary = client.getDiary(targetDate)
            val writeCount = healthConnectManager.syncDayDiary(
                diary = diary,
                granularity = granularity,
                mealSlotTimes = mealSlotTimes,
                syncHydration = syncHydration,
                zoneId = PRAGUE_ZONE
            )

            syncedDays.add(
                DaySyncSummary(
                    diary = diary,
                    recordsWritten = writeCount.nutritionRecordsWritten,
                    hydrationLitersWritten = writeCount.hydrationLitersWritten
                )
            )
            totalWritten += writeCount.nutritionRecordsWritten
            if (writeCount.hydrationLitersWritten != null) {
                totalHydrationDays++
            }

            if (offset + 1 < totalDays && totalDays > 3) {
                delay(BACKFILL_RATE_LIMIT_MS)
            }
        }

        val firstDayKcal = syncedDays.firstOrNull()?.diary?.totalLoggedKcal?.roundToInt() ?: 0
        val hydrationNote = if (syncHydration && totalHydrationDays > 0) {
            ", pitný režim: $totalHydrationDays dní"
        } else {
            ""
        }
        val summary = "Zapsáno $totalWritten záznamů jídla za $totalDays dní$hydrationNote (nejnovější den: $firstDayKcal kcal)"
        credentialsStore.saveLastSyncResult(summary)

        return SyncRunResult(
            syncedDays = syncedDays,
            totalRecordsWritten = totalWritten,
            totalHydrationDays = totalHydrationDays,
            summaryMessage = summary
        )
    }

    companion object {
        val PRAGUE_ZONE: ZoneId = ZoneId.of("Europe/Prague")
        const val MAX_BACKFILL_DAYS = 180
        private const val BACKFILL_RATE_LIMIT_MS = 300L
    }
}
