package cz.kaloricketabulky.healthsync.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import cz.kaloricketabulky.healthsync.data.api.KalorickeTabulkyClient
import cz.kaloricketabulky.healthsync.data.model.SyncGranularity

data class SyncSettings(
    val email: String = "",
    val password: String = "",
    val baseUrl: String = KalorickeTabulkyClient.DEFAULT_BASE_URL,
    val daysToSync: Int = 2,
    val granularity: SyncGranularity = SyncGranularity.PER_FOOD_ITEM,
    val autoSyncEnabled: Boolean = false,
    val lastSyncEpochMillis: Long = 0L,
    val lastSyncSummary: String? = null
)

class CredentialsStore(context: Context) {
    private val prefs: SharedPreferences = createPreferences(context.applicationContext)

    private fun createPreferences(appContext: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            appContext.getSharedPreferences(PREFS_FILE_NAME_FALLBACK, Context.MODE_PRIVATE)
        }
    }

    fun loadSettings(): SyncSettings {
        val granularityStr = prefs.getString(KEY_GRANULARITY, SyncGranularity.PER_FOOD_ITEM.name)
        val granularity = try {
            SyncGranularity.valueOf(granularityStr ?: SyncGranularity.PER_FOOD_ITEM.name)
        } catch (_: IllegalArgumentException) {
            SyncGranularity.PER_FOOD_ITEM
        }

        return SyncSettings(
            email = prefs.getString(KEY_EMAIL, "") ?: "",
            password = prefs.getString(KEY_PASSWORD, "") ?: "",
            baseUrl = prefs.getString(KEY_BASE_URL, KalorickeTabulkyClient.DEFAULT_BASE_URL)
                ?: KalorickeTabulkyClient.DEFAULT_BASE_URL,
            daysToSync = prefs.getInt(KEY_DAYS_TO_SYNC, 2).coerceIn(1, 30),
            granularity = granularity,
            autoSyncEnabled = prefs.getBoolean(KEY_AUTO_SYNC, false),
            lastSyncEpochMillis = prefs.getLong(KEY_LAST_SYNC_TIME, 0L),
            lastSyncSummary = prefs.getString(KEY_LAST_SYNC_SUMMARY, null)
        )
    }

    fun saveCredentials(email: String, password: String, baseUrl: String) {
        prefs.edit()
            .putString(KEY_EMAIL, email.trim())
            .putString(KEY_PASSWORD, password)
            .putString(KEY_BASE_URL, baseUrl.trim().removeSuffix("/"))
            .apply()
    }

    fun saveSyncOptions(daysToSync: Int, granularity: SyncGranularity, autoSyncEnabled: Boolean) {
        prefs.edit()
            .putInt(KEY_DAYS_TO_SYNC, daysToSync.coerceIn(1, 30))
            .putString(KEY_GRANULARITY, granularity.name)
            .putBoolean(KEY_AUTO_SYNC, autoSyncEnabled)
            .apply()
    }

    fun saveLastSyncResult(summary: String, epochMillis: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putString(KEY_LAST_SYNC_SUMMARY, summary)
            .putLong(KEY_LAST_SYNC_TIME, epochMillis)
            .apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "kt_health_sync_secure_prefs"
        private const val PREFS_FILE_NAME_FALLBACK = "kt_health_sync_prefs"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_DAYS_TO_SYNC = "days_to_sync"
        private const val KEY_GRANULARITY = "granularity"
        private const val KEY_AUTO_SYNC = "auto_sync"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_LAST_SYNC_SUMMARY = "last_sync_summary"
    }
}

