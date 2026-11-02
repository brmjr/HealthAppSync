package cz.kaloricketabulky.healthsync.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import cz.kaloricketabulky.healthsync.data.prefs.CredentialsStore
import cz.kaloricketabulky.healthsync.health.HealthConnectManager
import cz.kaloricketabulky.healthsync.sync.DiarySyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Android Quick Settings Tile ("Sync KT → Health") in the notification shade.
 * Allows 1-tap background synchronization right after logging a meal in Kalorické Tabulky.
 */
class QuickSyncTileService : TileService() {

    private var serviceScope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        updateTileIdleState()
    }

    override fun onStopListening() {
        serviceScope?.cancel()
        serviceScope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val scope = serviceScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also {
            serviceScope = it
        }

        val credentialsStore = CredentialsStore(applicationContext)
        val settings = credentialsStore.loadSettings()

        if (settings.email.isBlank() || settings.password.isEmpty()) {
            Toast.makeText(
                applicationContext,
                "Nejprve vyplňte přihlašovací údaje v aplikaci KT → Health (v2).",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        setTileState(
            state = Tile.STATE_ACTIVE,
            subtitle = "Synchronizuji…"
        )

        scope.launch {
            val healthConnectManager = HealthConnectManager(applicationContext)
            val repository = DiarySyncRepository(credentialsStore, healthConnectManager)

            try {
                val result = repository.syncToHealthConnect(
                    email = settings.email,
                    password = settings.password,
                    baseUrl = settings.baseUrl,
                    daysToSync = settings.daysToSync,
                    granularity = settings.granularity,
                    mealSlotTimes = settings.mealSlotTimes,
                    syncHydration = settings.syncHydration
                )
                val todayKcal = result.syncedDays.firstOrNull()?.diary?.totalLoggedKcal?.roundToInt() ?: 0
                setTileState(
                    state = Tile.STATE_INACTIVE,
                    subtitle = "Hotovo ($todayKcal kcal)"
                )
                Toast.makeText(
                    applicationContext,
                    "KT → Health Connect: ${result.summaryMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                setTileState(
                    state = Tile.STATE_INACTIVE,
                    subtitle = "Chyba"
                )
                Toast.makeText(
                    applicationContext,
                    "Chyba synchronizace: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateTileIdleState() {
        val settings = CredentialsStore(applicationContext).loadSettings()
        if (settings.email.isBlank()) {
            setTileState(Tile.STATE_INACTIVE, "Nepřihlášeno")
        } else {
            setTileState(Tile.STATE_INACTIVE, "Klepněte pro sync")
        }
    }

    private fun setTileState(state: Int, subtitle: String) {
        val tile = qsTile ?: return
        tile.state = state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }
        tile.updateTile()
    }
}
