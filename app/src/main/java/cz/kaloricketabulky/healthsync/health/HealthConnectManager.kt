package cz.kaloricketabulky.healthsync.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import cz.kaloricketabulky.healthsync.data.model.KtDayDiary
import cz.kaloricketabulky.healthsync.data.model.KtFoodItem
import cz.kaloricketabulky.healthsync.data.model.KtMealSlot
import cz.kaloricketabulky.healthsync.data.model.SyncGranularity
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class HealthConnectAvailability {
    AVAILABLE,
    UPDATE_REQUIRED,
    NOT_SUPPORTED
}

class HealthConnectManager(private val context: Context) {

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getWritePermission(NutritionRecord::class)
    )

    fun getAvailability(): HealthConnectAvailability {
        return when (HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE_NAME)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UPDATE_REQUIRED
            else -> HealthConnectAvailability.NOT_SUPPORTED
        }
    }

    private fun getClient(): HealthConnectClient {
        return HealthConnectClient.getOrCreate(context)
    }

    suspend fun hasAllPermissions(): Boolean {
        if (getAvailability() != HealthConnectAvailability.AVAILABLE) return false
        val granted = getClient().permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    /**
     * Synchronizes a single day's [diary] into Health Connect.
     *
     * First clears our app's existing [NutritionRecord] entries for that calendar day
     * (so deleted diary items or granularity changes never leave orphan duplicates),
     * then inserts the fresh records with deterministic `clientRecordId`s.
     *
     * @return Number of [NutritionRecord] entries written for the day.
     */
    suspend fun syncDayDiary(
        diary: KtDayDiary,
        granularity: SyncGranularity,
        zoneId: ZoneId = ZoneId.of("Europe/Prague")
    ): Int {
        val client = getClient()

        val dayStart = diary.date.atStartOfDay(zoneId).toInstant()
        val dayEnd = diary.date.plusDays(1).atStartOfDay(zoneId).minusNanos(1).toInstant()

        // Remove previous records created by this app for the target day
        client.deleteRecords(
            recordType = NutritionRecord::class,
            timeRangeFilter = TimeRangeFilter.between(dayStart, dayEnd)
        )

        val records = buildNutritionRecords(diary, granularity, zoneId)
        if (records.isNotEmpty()) {
            client.insertRecords(records)
        }
        return records.size
    }

    /**
     * Builds the list of [NutritionRecord] objects for a [KtDayDiary] without touching Android APIs,
     * making it easy to unit-test.
     */
    fun buildNutritionRecords(
        diary: KtDayDiary,
        granularity: SyncGranularity,
        zoneId: ZoneId = ZoneId.of("Europe/Prague")
    ): List<NutritionRecord> {
        return when (granularity) {
            SyncGranularity.PER_FOOD_ITEM -> buildPerItemRecords(diary, zoneId)
            SyncGranularity.PER_MEAL_SLOT -> buildPerMealRecords(diary, zoneId)
        }
    }

    private fun buildPerItemRecords(diary: KtDayDiary, zoneId: ZoneId): List<NutritionRecord> {
        val records = mutableListOf<NutritionRecord>()

        for (meal in diary.meals) {
            meal.items.forEachIndexed { index, item ->
                val baseTime = parseOptionalTime(item.time) ?: meal.defaultStartTime.plusMinutes(index.toLong())
                val startZdt = ZonedDateTime.of(diary.date, baseTime, zoneId)
                val endZdt = startZdt.plusMinutes(1)

                records.add(
                    itemToNutritionRecord(
                        item = item,
                        meal = meal,
                        startZdt = startZdt,
                        endZdt = endZdt
                    )
                )
            }
        }
        return records
    }

    private fun buildPerMealRecords(diary: KtDayDiary, zoneId: ZoneId): List<NutritionRecord> {
        val records = mutableListOf<NutritionRecord>()

        for (meal in diary.meals) {
            if (meal.items.isEmpty()) continue

            val startZdt = ZonedDateTime.of(diary.date, meal.defaultStartTime, zoneId)
            val endZdt = startZdt.plusMinutes(15)
            val summaryTitle = "${meal.title}: " + meal.items.joinToString(", ") { it.title }

            val totalKcal = meal.items.sumNullable { it.energyKcal }
            val totalProtein = meal.items.sumNullable { it.proteinGrams }
            val totalCarbs = meal.items.sumNullable { it.carbohydrateGrams }
            val totalFat = meal.items.sumNullable { it.fatGrams }
            val totalFiber = meal.items.sumNullable { it.fiberGrams }
            val totalSugar = meal.items.sumNullable { it.sugarGrams }
            val totalSaturatedFat = meal.items.sumNullable { it.saturatedFatGrams }
            val totalTransFat = meal.items.sumNullable { it.transFatGrams }
            val totalMonoFat = meal.items.sumNullable { it.monounsaturatedFatGrams }
            val totalPolyFat = meal.items.sumNullable { it.polyunsaturatedFatGrams }
            val totalCholesterolMg = meal.items.sumNullable { it.cholesterolMg }
            val totalCalciumMg = meal.items.sumNullable { it.calciumMg }
            val totalSodiumGrams = meal.items.sumNullable { it.sodiumGrams }

            records.add(
                NutritionRecord(
                    startTime = startZdt.toInstant(),
                    startZoneOffset = startZdt.offset,
                    endTime = endZdt.toInstant(),
                    endZoneOffset = endZdt.offset,
                    name = summaryTitle.take(MAX_NAME_LENGTH),
                    mealType = meal.mealType,
                    energy = positiveKcal(totalKcal),
                    protein = positiveGrams(totalProtein),
                    totalCarbohydrate = positiveGrams(totalCarbs),
                    totalFat = positiveGrams(totalFat),
                    dietaryFiber = positiveGrams(totalFiber),
                    sugar = positiveGrams(totalSugar),
                    saturatedFat = positiveGrams(totalSaturatedFat),
                    transFat = positiveGrams(totalTransFat),
                    monounsaturatedFat = positiveGrams(totalMonoFat),
                    polyunsaturatedFat = positiveGrams(totalPolyFat),
                    cholesterol = positiveMilligrams(totalCholesterolMg),
                    calcium = positiveMilligrams(totalCalciumMg),
                    sodium = positiveGrams(totalSodiumGrams),
                    metadata = Metadata.manualEntry(
                        clientRecordId = "kt_meal_${diary.date}_${meal.id}"
                    )
                )
            )
        }
        return records
    }

    private fun itemToNutritionRecord(
        item: KtFoodItem,
        meal: KtMealSlot,
        startZdt: ZonedDateTime,
        endZdt: ZonedDateTime
    ): NutritionRecord {
        return NutritionRecord(
            startTime = startZdt.toInstant(),
            startZoneOffset = startZdt.offset,
            endTime = endZdt.toInstant(),
            endZoneOffset = endZdt.offset,
            name = item.displayName.take(MAX_NAME_LENGTH),
            mealType = meal.mealType,
            energy = positiveKcal(item.energyKcal),
            protein = positiveGrams(item.proteinGrams),
            totalCarbohydrate = positiveGrams(item.carbohydrateGrams),
            totalFat = positiveGrams(item.fatGrams),
            dietaryFiber = positiveGrams(item.fiberGrams),
            sugar = positiveGrams(item.sugarGrams),
            saturatedFat = positiveGrams(item.saturatedFatGrams),
            transFat = positiveGrams(item.transFatGrams),
            monounsaturatedFat = positiveGrams(item.monounsaturatedFatGrams),
            polyunsaturatedFat = positiveGrams(item.polyunsaturatedFatGrams),
            cholesterol = positiveMilligrams(item.cholesterolMg),
            calcium = positiveMilligrams(item.calciumMg),
            sodium = positiveGrams(item.sodiumGrams),
            metadata = Metadata.manualEntry(
                clientRecordId = "kt_item_${item.id}"
            )
        )
    }

    private fun parseOptionalTime(rawTime: String?): LocalTime? {
        if (rawTime.isNullOrBlank()) return null
        return try {
            LocalTime.parse(rawTime.trim(), TIME_FORMATTER)
        } catch (_: Exception) {
            null
        }
    }

    private fun positiveKcal(value: Double?): Energy? =
        value?.takeIf { it > 0.0 && it <= 100_000.0 }?.let { Energy.kilocalories(it) }

    private fun positiveGrams(value: Double?): Mass? =
        value?.takeIf { it > 0.0 && it <= 100_000.0 }?.let { Mass.grams(it) }

    private fun positiveMilligrams(value: Double?): Mass? =
        value?.takeIf { it > 0.0 && it <= 100_000_000.0 }?.let { Mass.milligrams(it) }

    private inline fun <T> Iterable<T>.sumNullable(selector: (T) -> Double?): Double? {
        var sum = 0.0
        var hasAny = false
        for (element in this) {
            val v = selector(element)
            if (v != null) {
                sum += v
                hasAny = true
            }
        }
        return if (hasAny) sum else null
    }

    companion object {
        const val PROVIDER_PACKAGE_NAME = "com.google.android.apps.healthdata"
        private const val MAX_NAME_LENGTH = 200
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")
    }
}

