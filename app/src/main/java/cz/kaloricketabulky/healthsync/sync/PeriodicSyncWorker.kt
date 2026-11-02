package cz.kaloricketabulky.healthsync.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cz.kaloricketabulky.healthsync.data.model.MealSlotMapper
import cz.kaloricketabulky.healthsync.data.prefs.CredentialsStore
import cz.kaloricketabulky.healthsync.health.HealthConnectManager
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class PeriodicSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val credentialsStore = CredentialsStore(applicationContext)
        val settings = credentialsStore.loadSettings()

        if (settings.email.isBlank() || settings.password.isEmpty()) {
            return Result.failure()
        }

        val healthConnectManager = HealthConnectManager(applicationContext)
        val repository = DiarySyncRepository(credentialsStore, healthConnectManager)

        return try {
            repository.syncToHealthConnect(
                email = settings.email,
                password = settings.password,
                baseUrl = settings.baseUrl,
                daysToSync = settings.daysToSync,
                granularity = settings.granularity,
                mealSlotTimes = settings.mealSlotTimes,
                syncHydration = settings.syncHydration
            )
            Result.success()
        } catch (e: Exception) {
            credentialsStore.saveLastSyncResult("Chyba automatické synchronizace: ${e.localizedMessage}")
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_PERIODIC_WORK_NAME = "KalorickeTabulkyPeriodicHealthConnectSyncV2"
        private const val UNIQUE_EVENING_WORK_NAME = "KalorickeTabulkyEveningHealthConnectSyncV2"

        fun updateSchedule(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context.applicationContext)
            if (!enabled) {
                workManager.cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(
                repeatInterval = 6,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /**
         * Schedules a daily evening sync around [timeString] (e.g. "21:30" Europe/Prague)
         * so the full day's food and water diary is synced before midnight.
         */
        fun updateEveningSchedule(context: Context, enabled: Boolean, timeString: String) {
            val workManager = WorkManager.getInstance(context.applicationContext)
            if (!enabled) {
                workManager.cancelUniqueWork(UNIQUE_EVENING_WORK_NAME)
                return
            }

            val targetTime = MealSlotMapper.parseTimeOrDefault(timeString, LocalTime.of(21, 30))
            val now = ZonedDateTime.now(DiarySyncRepository.PRAGUE_ZONE)
            var nextRun = now.withHour(targetTime.hour).withMinute(targetTime.minute).withSecond(0)
            if (!nextRun.isAfter(now)) {
                nextRun = nextRun.plusDays(1)
            }
            val initialDelayMinutes = Duration.between(now, nextRun).toMinutes().coerceAtLeast(1L)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_EVENING_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
