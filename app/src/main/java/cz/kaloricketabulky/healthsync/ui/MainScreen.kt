package cz.kaloricketabulky.healthsync.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cz.kaloricketabulky.healthsync.BuildConfig
import cz.kaloricketabulky.healthsync.data.model.MealSlotTimes
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
    onSyncHydrationChanged: (Boolean) -> Unit,
    onAutoSyncChanged: (Boolean) -> Unit,
    onEveningSyncChanged: (Boolean) -> Unit,
    onEveningSyncTimeChanged: (String) -> Unit,
    onMealSlotTimesChanged: (MealSlotTimes) -> Unit,
    onResetMealSlotTimes: () -> Unit,
    onBackfillStartDateChanged: (String) -> Unit,
    onBackfillEndDateChanged: (String) -> Unit,
    onSelectBackfillPresetDays: (Int) -> Unit,
    onSelectBackfillCurrentMonth: () -> Unit,
    onSelectBackfillPreviousMonth: () -> Unit,
    onRunHistoricalBackfill: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onRequestPermissions: () -> Unit,
    onTestLogin: () -> Unit,
    onSyncNow: () -> Unit
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var showMealTimesEditor by rememberSaveable { mutableStateOf(false) }
    var showBackfillSection by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "KT → Health Connect (v2)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Výživa, sodík ze soli, pitný režim & historie",
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
                syncHydration = state.syncHydration,
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
                        text = "Počet dní zpětně při rychlé synchronizaci:",
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
                        text = "Způsob zápisu jídla do Health Connect:",
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

                    HorizontalDivider()

                    // Water Intake / Hydration toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Synchronizovat pitný režim (HydrationRecord)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Zapisuje denní objem tekutin (l) z Kalorických Tabulek",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.syncHydration,
                            onCheckedChange = onSyncHydrationChanged
                        )
                    }

                    // 6-hour Periodic Sync toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pravidelná synchronizace každých 6 hodin",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Na pozadí přes Android WorkManager",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.autoSyncEnabled,
                            onCheckedChange = onAutoSyncChanged
                        )
                    }

                    // Smart Evening Sync toggle + time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Večerní denní synchronizace před půlnocí",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Každý večer v nastavený čas (např. 21:30)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.eveningSyncEnabled,
                            onCheckedChange = onEveningSyncChanged
                        )
                    }

                    if (state.eveningSyncEnabled) {
                        OutlinedTextField(
                            value = state.eveningSyncTime,
                            onValueChange = onEveningSyncTimeChanged,
                            label = { Text("Čas večerní synchronizace (HH:mm)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider()

                    // Custom Meal Times section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Vlastní časy chodů (Snídaně, Oběd…)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        TextButton(onClick = { showMealTimesEditor = !showMealTimesEditor }) {
                            Text(if (showMealTimesEditor) "Skrýt" else "Upravit časy")
                        }
                    }

                    if (showMealTimesEditor) {
                        MealSlotTimesEditor(
                            times = state.mealSlotTimes,
                            onChanged = onMealSlotTimesChanged,
                            onReset = onResetMealSlotTimes
                        )
                    }

                    Text(
                        text = "Tip: V horní stahovací liště telefonu (Rychlé nastavení) si můžete přidat dlaždici „Sync KT → Health“ pro okamžitou synchronizaci jedním klepnutím.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 4. Primary Sync Button
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

            // 5. Historical Date-Range Backfill Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "3. Historický import (rozsah dat)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Hromadný přenos starších dnů nebo měsíců",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { showBackfillSection = !showBackfillSection }) {
                            Text(if (showBackfillSection) "Skrýt" else "Otevřít")
                        }
                    }

                    if (showBackfillSection) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = false,
                                onClick = { onSelectBackfillPresetDays(30) },
                                label = { Text("Posledních 30 dní") }
                            )
                            FilterChip(
                                selected = false,
                                onClick = onSelectBackfillCurrentMonth,
                                label = { Text("Tento měsíc") }
                            )
                            FilterChip(
                                selected = false,
                                onClick = onSelectBackfillPreviousMonth,
                                label = { Text("Minulý měsíc") }
                            )
                            FilterChip(
                                selected = false,
                                onClick = { onSelectBackfillPresetDays(90) },
                                label = { Text("90 dní") }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = state.backfillStartDate,
                                onValueChange = onBackfillStartDateChanged,
                                label = { Text("Od (DD.MM.RRRR)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = state.backfillEndDate,
                                onValueChange = onBackfillEndDateChanged,
                                label = { Text("Do (DD.MM.RRRR)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedButton(
                            onClick = onRunHistoricalBackfill,
                            enabled = !state.isBusy &&
                                state.hasHealthPermissions &&
                                state.email.isNotBlank() &&
                                state.password.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Spustit historický import do Health Connect")
                        }
                    }
                }
            }

            // 6. Status Banner
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

            // 7. Synced Diary Preview
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

            // 8. In-App Update Card (GitHub Releases)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Aktualizace aplikace (v${BuildConfig.VERSION_NAME})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Nainstalováno vedle stabilní verze v1 (samostatné ID balíčku .v2).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!state.updateStatusMessage.isNullOrBlank()) {
                        Text(
                            text = state.updateStatusMessage,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onCheckForUpdates,
                            enabled = !state.isCheckingUpdate
                        ) {
                            Text(if (state.isCheckingUpdate) "Kontroluji…" else "Zkontrolovat aktualizaci")
                        }
                        val release = state.latestReleaseInfo
                        if (release != null) {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.apkDownloadUrl))
                                    context.startActivity(intent)
                                }
                            ) {
                                Text("Stáhnout APK (${release.tagName})")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MealSlotTimesEditor(
    times: MealSlotTimes,
    onChanged: (MealSlotTimes) -> Unit,
    onReset: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = times.breakfast,
                onValueChange = { onChanged(times.copy(breakfast = it)) },
                label = { Text("Snídaně") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = times.morningSnack,
                onValueChange = { onChanged(times.copy(morningSnack = it)) },
                label = { Text("Dop. svačina") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = times.lunch,
                onValueChange = { onChanged(times.copy(lunch = it)) },
                label = { Text("Oběd") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = times.afternoonSnack,
                onValueChange = { onChanged(times.copy(afternoonSnack = it)) },
                label = { Text("Odp. svačina") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = times.dinner,
                onValueChange = { onChanged(times.copy(dinner = it)) },
                label = { Text("Večeře") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = times.secondDinner,
                onValueChange = { onChanged(times.copy(secondDinner = it)) },
                label = { Text("Druhá večeře") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        TextButton(onClick = onReset) {
            Text("Obnovit výchozí časy (08:00, 10:30, 12:30, 16:00, 19:00, 21:00)")
        }
    }
}

@Composable
private fun HealthConnectStatusCard(
    availability: HealthConnectAvailability,
    hasPermissions: Boolean,
    syncHydration: Boolean,
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
                        val scopeText = if (syncHydration) {
                            "Výživa (NutritionRecord) + Pitný režim (HydrationRecord)"
                        } else {
                            "Výživa (NutritionRecord)"
                        }
                        Text(
                            text = "✓ Oprávnění pro zápis ($scopeText) je povoleno.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = "Pro zápis jídelníčku" +
                                (if (syncHydration) " a pitného režimu" else "") +
                                " povolte oprávnění v Health Connect.",
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
    val protein = diary.totalLoggedProteinGrams.roundToInt()
    val carbs = diary.totalLoggedCarbohydrateGrams.roundToInt()
    val fat = diary.totalLoggedFatGrams.roundToInt()
    val fiber = diary.totalLoggedFiberGrams.roundToInt()
    val sodiumMg = (items.sumOf { (it.effectiveSodiumGrams ?: 0.0) * 1000.0 }).roundToInt()

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
                val drinkText = diary.drinkRegimeLiters?.takeIf { it > 0.0 }?.let {
                    String.format(Locale.US, " • 💧 %.2f l", it)
                } ?: ""
                Text(
                    text = "${diary.date} — $kcal kcal$drinkText",
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
                text = "Bílkoviny: ${protein}g • Sacharidy: ${carbs}g • Tuky: ${fat}g • Vláknina: ${fiber}g • Sodík: ${sodiumMg}mg",
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
