package cz.kaloricketabulky.healthsync.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cz.kaloricketabulky.healthsync.data.prefs.CredentialsStore
import cz.kaloricketabulky.healthsync.health.HealthConnectManager
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
                granularity = settings.granularity
            )
            Result.success()
        } catch (e: Exception) {
            credentialsStore.saveLastSyncResult("Chyba automatické synchronizace: ${e.localizedMessage}")
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "KalorickeTabulkyPeriodicHealthConnectSync"

        fun updateSchedule(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context.applicationContext)
            if (!enabled) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
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
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}

