package cz.kaloricketabulky.healthsync.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cz.kaloricketabulky.healthsync.data.model.SyncGranularity
import cz.kaloricketabulky.healthsync.health.HealthConnectAvailability
import cz.kaloricketabulky.healthsync.sync.DaySyncSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onDaysToSyncChanged: (Int) -> Unit,
    onGranularityChanged: (SyncGranularity) -> Unit,
    onAutoSyncChanged: (Boolean) -> Unit,
    onRequestPermissions: () -> Unit,
    onTestLogin: () -> Unit,
    onSyncNow: () -> Unit
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Kalorické Tabulky → Health Connect",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Synchronizace jídelníčku a makroživin",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Health Connect Status Card
            HealthConnectStatusCard(
                availability = state.healthConnectAvailability,
                hasPermissions = state.hasHealthPermissions,
                onRequestPermissions = onRequestPermissions
            )

            // 2. Kalorické Tabulky Credentials Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "1. Účet Kalorické Tabulky",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val czUrl = "https://www.kaloricketabulky.cz"
                        val skUrl = "https://www.kaloricketabulky.sk"
                        FilterChip(
                            selected = state.baseUrl == czUrl,
                            onClick = { onBaseUrlChanged(czUrl) },
                            label = { Text("kaloricketabulky.cz") }
                        )
                        FilterChip(
                            selected = state.baseUrl == skUrl,
                            onClick = { onBaseUrlChanged(skUrl) },
                            label = { Text("kaloricketabulky.sk") }
                        )
                    }

                    OutlinedTextField(
                        value = state.email,
                        onValueChange = onEmailChanged,
                        label = { Text("E-mail") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = state.password,
                        onValueChange = onPasswordChanged,
                        label = { Text("Heslo") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(if (passwordVisible) "Skrýt" else "Zobrazit")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedButton(
                        onClick = onTestLogin,
                        enabled = !state.isBusy && state.email.isNotBlank() && state.password.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Otestovat přihlášení a načíst dnešek")
                    }
                }
            }

            // 3. Sync Settings Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "2. Nastavení synchronizace",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "Počet dní zpětně k synchronizaci:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            1 to "Dnes (1 den)",
                            2 to "Dnes + Včera (2 dny)",
                            7 to "7 dní",
                            14 to "14 dní"
                        ).forEach { (days, label) ->
                            FilterChip(
                                selected = state.daysToSync == days,
                                onClick = { onDaysToSyncChanged(days) },
                                label = { Text(label) }
                            )
                        }
                    }

                    Text(
                        text = "Způsob zápisu do Health Connect:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.granularity == SyncGranularity.PER_FOOD_ITEM,
                            onClick = { onGranularityChanged(SyncGranularity.PER_FOOD_ITEM) },
                            label = { Text("Jednotlivé potraviny") }
                        )
                        FilterChip(
                            selected = state.granularity == SyncGranularity.PER_MEAL_SLOT,
                            onClick = { onGranularityChanged(SyncGranularity.PER_MEAL_SLOT) },
                            label = { Text("Souhrn za chod (Snídaně, Oběd…)") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automatická synchronizace na pozadí",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Každých 6 hodin přes Android WorkManager",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.autoSyncEnabled,
                            onCheckedChange = onAutoSyncChanged
                        )
                    }
                }
            }

            // 4. Sync Trigger & Status
            Button(
                onClick = onSyncNow,
                enabled = !state.isBusy &&
                    state.hasHealthPermissions &&
                    state.email.isNotBlank() &&
                    state.password.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(state.progressMessage ?: "Pracuji…")
                } else {
                    Text("Synchronizovat do Health Connect")
                }
            }

            if (!state.statusMessage.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.isError) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = state.statusMessage,
                            color = if (state.isError) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        if (state.lastSyncEpochMillis > 0L) {
                            val formattedTime = SimpleDateFormat(
                                "dd.MM.yyyy HH:mm:ss",
                                Locale.getDefault()
                            ).format(Date(state.lastSyncEpochMillis))
                            Text(
                                text = "Poslední synchronizace: $formattedTime",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // 5. Synced Diary Preview
            if (state.syncedDaysPreview.isNotEmpty()) {
                Text(
                    text = "Náhled načteného jídelníčku",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                state.syncedDaysPreview.forEach { daySummary ->
                    DayPreviewCard(daySummary = daySummary)
                }
            }
        }
    }
}

@Composable
private fun HealthConnectStatusCard(
    availability: HealthConnectAvailability,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit
) {
    val containerColor = if (availability == HealthConnectAvailability.AVAILABLE && hasPermissions) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Stav Health Connect",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            when (availability) {
                HealthConnectAvailability.NOT_SUPPORTED -> {
                    Text(
                        text = "Health Connect není v tomto zařízení podporován.",
                        color = MaterialTheme.colorScheme.error
                    )
                }

                HealthConnectAvailability.UPDATE_REQUIRED -> {
                    Text(
                        text = "Je potřeba nainstalovat nebo aktualizovat aplikaci Health Connect z Google Play.",
                        color = MaterialTheme.colorScheme.error
                    )
                }

                HealthConnectAvailability.AVAILABLE -> {
                    if (hasPermissions) {
                        Text(
                            text = "✓ Oprávnění pro čtení a zápis výživy (NutritionRecord) je povoleno.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = "Pro zápis jídelníčku je nutné povolit oprávnění k výživě v Health Connect.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = onRequestPermissions) {
                            Text("Povolit oprávnění Health Connect")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayPreviewCard(daySummary: DaySyncSummary) {
    val diary = daySummary.diary
    val items = diary.allFoodItems
    val kcal = diary.totalLoggedKcal.roundToInt()
    val protein = (diary.proteinTotalGrams ?: items.sumOf { it.proteinGrams ?: 0.0 }).roundToInt()
    val carbs = (diary.carbohydrateTotalGrams ?: items.sumOf { it.carbohydrateGrams ?: 0.0 }).roundToInt()
    val fat = (diary.fatTotalGrams ?: items.sumOf { it.fatGrams ?: 0.0 }).roundToInt()
    val fiber = (diary.fiberTotalGrams ?: items.sumOf { it.fiberGrams ?: 0.0 }).roundToInt()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${diary.date} — $kcal kcal",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (daySummary.recordsWritten > 0) {
                    Text(
                        text = "Zapsáno: ${daySummary.recordsWritten}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = "Bílkoviny: ${protein}g • Sacharidy: ${carbs}g • Tuky: ${fat}g • Vláknina: ${fiber}g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            diary.meals.filter { it.items.isNotEmpty() }.forEach { meal ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                val mealKcal = meal.items.sumOf { it.energyKcal ?: 0.0 }.roundToInt()
                Text(
                    text = "${meal.title} ($mealKcal kcal)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                meal.items.forEach { food ->
                    val foodKcal = food.energyKcal?.roundToInt() ?: 0
                    Text(
                        text = "• ${food.displayName} — $foodKcal kcal " +
                            "(B:${food.proteinGrams?.roundToInt() ?: 0}g, " +
                            "S:${food.carbohydrateGrams?.roundToInt() ?: 0}g, " +
                            "T:${food.fatGrams?.roundToInt() ?: 0}g)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

