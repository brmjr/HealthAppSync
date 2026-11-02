package cz.kaloricketabulky.healthsync.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import cz.kaloricketabulky.healthsync.data.model.KtDayDiary
import cz.kaloricketabulky.healthsync.data.model.KtFoodItem
import cz.kaloricketabulky.healthsync.data.model.KtMealSlot
import cz.kaloricketabulky.healthsync.data.model.MealSlotTimes
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

data class DayWriteCount(
    val nutritionRecordsWritten: Int,
    val hydrationLitersWritten: Double?
)

class HealthConnectManager(private val context: Context) {

    val nutritionPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getWritePermission(NutritionRecord::class)
    )

    val hydrationPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(HydrationRecord::class),
        HealthPermission.getWritePermission(HydrationRecord::class)
    )

    val allPermissions: Set<String> = nutritionPermissions + hydrationPermissions

    fun requiredPermissions(syncHydration: Boolean): Set<String> =
        if (syncHydration) allPermissions else nutritionPermissions

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

    suspend fun hasRequiredPermissions(syncHydration: Boolean): Boolean {
        if (getAvailability() != HealthConnectAvailability.AVAILABLE) return false
        val granted = getClient().permissionController.getGrantedPermissions()
        return granted.containsAll(requiredPermissions(syncHydration))
    }

    /**
     * Synchronizes a single day's [diary] into Health Connect (NutritionRecord + optional HydrationRecord).
     */
    suspend fun syncDayDiary(
        diary: KtDayDiary,
        granularity: SyncGranularity,
        mealSlotTimes: MealSlotTimes = MealSlotTimes(),
        syncHydration: Boolean = true,
        zoneId: ZoneId = ZoneId.of("Europe/Prague")
    ): DayWriteCount {
        val client = getClient()

        val dayStart = diary.date.atStartOfDay(zoneId).toInstant()
        val dayEnd = diary.date.plusDays(1).atStartOfDay(zoneId).minusNanos(1).toInstant()

        // 1. Nutrition records
        client.deleteRecords(
            recordType = NutritionRecord::class,
            timeRangeFilter = TimeRangeFilter.between(dayStart, dayEnd)
        )

        val nutritionRecords = buildNutritionRecords(diary, granularity, mealSlotTimes, zoneId)
        if (nutritionRecords.isNotEmpty()) {
            client.insertRecords(nutritionRecords)
        }

        // 2. Hydration record (Pitný režim)
        var hydrationLitersWritten: Double? = null
        if (syncHydration) {
            client.deleteRecords(
                recordType = HydrationRecord::class,
                timeRangeFilter = TimeRangeFilter.between(dayStart, dayEnd)
            )
            val hydrationRecord = buildHydrationRecord(diary, zoneId)
            if (hydrationRecord != null) {
                client.insertRecords(listOf(hydrationRecord))
                hydrationLitersWritten = diary.drinkRegimeLiters
            }
        }

        return DayWriteCount(
            nutritionRecordsWritten = nutritionRecords.size,
            hydrationLitersWritten = hydrationLitersWritten
        )
    }

    fun buildHydrationRecord(
        diary: KtDayDiary,
        zoneId: ZoneId = ZoneId.of("Europe/Prague")
    ): HydrationRecord? {
        val liters = diary.drinkRegimeLiters?.takeIf { it > 0.0 && it <= 50.0 } ?: return null
        val startZdt = ZonedDateTime.of(diary.date, LocalTime.of(10, 0), zoneId)
        val endZdt = startZdt.plusHours(8)

        return HydrationRecord(
            startTime = startZdt.toInstant(),
            startZoneOffset = startZdt.offset,
            endTime = endZdt.toInstant(),
            endZoneOffset = endZdt.offset,
            volume = Volume.liters(liters),
            metadata = Metadata.manualEntry(
                clientRecordId = "kt_hydration_${diary.date}"
            )
        )
    }

    /**
     * Builds the list of [NutritionRecord] objects for a [KtDayDiary].
     */
    fun buildNutritionRecords(
        diary: KtDayDiary,
        granularity: SyncGranularity,
        mealSlotTimes: MealSlotTimes = MealSlotTimes(),
        zoneId: ZoneId = ZoneId.of("Europe/Prague")
    ): List<NutritionRecord> {
        return when (granularity) {
            SyncGranularity.PER_FOOD_ITEM -> buildPerItemRecords(diary, mealSlotTimes, zoneId)
            SyncGranularity.PER_MEAL_SLOT -> buildPerMealRecords(diary, mealSlotTimes, zoneId)
        }
    }

    private fun buildPerItemRecords(
        diary: KtDayDiary,
        mealSlotTimes: MealSlotTimes,
        zoneId: ZoneId
    ): List<NutritionRecord> {
        val records = mutableListOf<NutritionRecord>()

        for (meal in diary.meals) {
            val slotStart = meal.startTime(mealSlotTimes)
            meal.items.forEachIndexed { index, item ->
                val baseTime = parseOptionalTime(item.time) ?: slotStart.plusMinutes(index.toLong())
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

    private fun buildPerMealRecords(
        diary: KtDayDiary,
        mealSlotTimes: MealSlotTimes,
        zoneId: ZoneId
    ): List<NutritionRecord> {
        val records = mutableListOf<NutritionRecord>()

        for (meal in diary.meals) {
            if (meal.items.isEmpty()) continue

            val startZdt = ZonedDateTime.of(diary.date, meal.startTime(mealSlotTimes), zoneId)
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
            val totalSodiumGrams = meal.items.sumNullable { it.effectiveSodiumGrams }

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
            sodium = positiveGrams(item.effectiveSodiumGrams),
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
