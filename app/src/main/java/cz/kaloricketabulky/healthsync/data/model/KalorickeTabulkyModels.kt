package cz.kaloricketabulky.healthsync.data.model

import androidx.health.connect.client.records.MealType
import org.json.JSONObject
import java.security.MessageDigest
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime

/**
 * Granularity when writing nutrition records into Health Connect.
 */
enum class SyncGranularity {
    /** Write one NutritionRecord per logged food item (recommended for full item history). */
    PER_FOOD_ITEM,

    /** Aggregate food items into one NutritionRecord per meal slot (Breakfast, Lunch, etc.). */
    PER_MEAL_SLOT
}

data class KtFoodItem(
    val id: String,
    val title: String,
    val unit: String?,
    val energyValue: Double?,
    val energyUnit: String,
    val proteinGrams: Double?,
    val carbohydrateGrams: Double?,
    val fatGrams: Double?,
    val fiberGrams: Double?,
    val sugarGrams: Double?,
    val saturatedFatGrams: Double?,
    val transFatGrams: Double?,
    val monounsaturatedFatGrams: Double?,
    val polyunsaturatedFatGrams: Double?,
    val cholesterolMg: Double?,
    val sodiumGrams: Double?,
    val calciumMg: Double?,
    val saltGrams: Double?,
    val time: String?
) {
    /** Energy normalized to kilocalories (kcal). */
    val energyKcal: Double?
        get() = energyValue?.let { value ->
            if (energyUnit.equals("kj", ignoreCase = true)) {
                value / KJ_PER_KCAL
            } else {
                value
            }
        }

    /** Display name combining food title and portion unit, e.g. "Ovesné vločky (50 g)". */
    val displayName: String
        get() = if (!unit.isNullOrBlank()) "$title ($unit)" else title

    companion object {
        const val KJ_PER_KCAL = 4.184
    }
}

data class KtMealSlot(
    val id: String,
    val title: String,
    val items: List<KtFoodItem>
) {
    val mealType: Int
        get() = MealSlotMapper.toHealthConnectMealType(title)

    val defaultStartTime: LocalTime
        get() = MealSlotMapper.defaultStartTimeFor(title)
}

data class KtDayDiary(
    val date: LocalDate,
    val energyUnit: String,
    val meals: List<KtMealSlot>,
    val energyTotal: Double?,
    val proteinTotalGrams: Double?,
    val carbohydrateTotalGrams: Double?,
    val fatTotalGrams: Double?,
    val fiberTotalGrams: Double?,
    val sugarTotalGrams: Double?
) {
    val allFoodItems: List<KtFoodItem>
        get() = meals.flatMap { it.items }

    val totalLoggedKcal: Double
        get() = allFoodItems.sumOf { it.energyKcal ?: 0.0 }
}

object MealSlotMapper {
    private fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val decomposed = Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    fun toHealthConnectMealType(title: String?): Int {
        return when (normalize(title)) {
            "snidane", "ranajky", "breakfast" -> MealType.MEAL_TYPE_BREAKFAST
            "obed", "lunch" -> MealType.MEAL_TYPE_LUNCH
            "vecere", "vecera", "dinner" -> MealType.MEAL_TYPE_DINNER
            "dopoledni svacina", "odpoledni svacina", "druha vecere",
            "desiata", "olovrant", "druha vecera", "svacina", "snack" -> MealType.MEAL_TYPE_SNACK
            else -> MealType.MEAL_TYPE_UNKNOWN
        }
    }

    fun defaultStartTimeFor(title: String?): LocalTime {
        return when (normalize(title)) {
            "snidane", "ranajky", "breakfast" -> LocalTime.of(8, 0)
            "dopoledni svacina", "desiata" -> LocalTime.of(10, 30)
            "obed", "lunch" -> LocalTime.of(12, 30)
            "odpoledni svacina", "olovrant" -> LocalTime.of(16, 0)
            "vecere", "vecera", "dinner" -> LocalTime.of(19, 0)
            "druha vecere", "druha vecera" -> LocalTime.of(21, 0)
            else -> LocalTime.of(12, 0)
        }
    }
}

object KalorickeTabulkyParser {
    private val THOUSANDS_SEPARATORS = charArrayOf(' ', '\u00a0', '\u2009', '\u202f')

    /**
     * Parses a Czech-localized number string or JSON numeric value into a Double.
     * Handles thin/non-breaking spaces and decimal commas (e.g. "1 234,56" -> 1234.56).
     */
    fun parseCzechDouble(raw: Any?): Double? {
        if (raw == null || raw == JSONObject.NULL) return null
        if (raw is Number) return raw.toDouble()
        val str = raw.toString().trim()
        if (str.isEmpty() || str.equals("null", ignoreCase = true)) return null

        val sb = StringBuilder(str.length)
        for (ch in str) {
            if (ch in THOUSANDS_SEPARATORS) continue
            sb.append(if (ch == ',') '.' else ch)
        }
        return sb.toString().toDoubleOrNull()
    }

    /**
     * Computes lowercase hexadecimal MD5 digest of a UTF-8 string, as required by
     * Kalorické Tabulky's `/login/create` endpoint.
     */
    fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Parses the JSON response from `/user/diary/{dd.MM.yyyy}/get?format=json`.
     */
    fun parseDiaryResponse(jsonString: String, date: LocalDate): KtDayDiary {
        val root = JSONObject(jsonString)
        val code = root.optInt("code", -1)
        if (code != 0) {
            val message = root.optString("message").takeIf { it.isNotBlank() }
                ?: "Kalorické Tabulky API returned error code $code"
            throw IllegalStateException(message)
        }

        val data = root.optJSONObject("data")
            ?: throw IllegalStateException("Missing 'data' object in diary response")

        val dayEnergyUnit = data.optStringOrNull("energyUnit") ?: "kcal"
        val timesArray = data.optJSONArray("times")
        val meals = mutableListOf<KtMealSlot>()

        if (timesArray != null) {
            for (i in 0 until timesArray.length()) {
                val mealObj = timesArray.optJSONObject(i) ?: continue
                val mealId = mealObj.optString("id", "meal_$i")
                val mealTitle = mealObj.optStringOrNull("title") ?: "Jídlo ${i + 1}"
                val foodstuffArray = mealObj.optJSONArray("foodstuff")
                val items = mutableListOf<KtFoodItem>()

                if (foodstuffArray != null) {
                    for (j in 0 until foodstuffArray.length()) {
                        val itemObj = foodstuffArray.optJSONObject(j) ?: continue
                        val itemType = itemObj.optString("type", "foodstuff")
                        if (itemType != "foodstuff") continue

                        val itemId = itemObj.optString("id", "${mealId}_$j")
                        val itemTitle = itemObj.optStringOrNull("title") ?: "Potravina"
                        val itemEnergyUnit = itemObj.optStringOrNull("energyUnit") ?: dayEnergyUnit

                        items.add(
                            KtFoodItem(
                                id = itemId,
                                title = itemTitle,
                                unit = itemObj.optStringOrNull("unit"),
                                energyValue = parseCzechDouble(itemObj.opt("energy")),
                                energyUnit = itemEnergyUnit,
                                proteinGrams = parseCzechDouble(itemObj.opt("protein")),
                                carbohydrateGrams = parseCzechDouble(itemObj.opt("carbohydrate")),
                                fatGrams = parseCzechDouble(itemObj.opt("fat")),
                                fiberGrams = parseCzechDouble(itemObj.opt("fiber")),
                                sugarGrams = parseCzechDouble(itemObj.opt("sugar")),
                                saturatedFatGrams = parseCzechDouble(itemObj.opt("saturatedFattyAcid")),
                                transFatGrams = parseCzechDouble(itemObj.opt("transFattyAcid")),
                                monounsaturatedFatGrams = parseCzechDouble(itemObj.opt("monoSaturated")),
                                polyunsaturatedFatGrams = parseCzechDouble(itemObj.opt("polySaturated")),
                                cholesterolMg = parseCzechDouble(itemObj.opt("cholesterol")),
                                sodiumGrams = parseCzechDouble(itemObj.opt("sodium")),
                                calciumMg = parseCzechDouble(itemObj.opt("calcium")),
                                saltGrams = parseCzechDouble(itemObj.opt("salt")),
                                time = itemObj.optStringOrNull("time")
                            )
                        )
                    }
                }

                meals.add(
                    KtMealSlot(
                        id = mealId,
                        title = mealTitle,
                        items = items
                    )
                )
            }
        }

        return KtDayDiary(
            date = date,
            energyUnit = dayEnergyUnit,
            meals = meals,
            energyTotal = parseCzechDouble(data.opt("energyTotal")),
            proteinTotalGrams = parseCzechDouble(data.opt("proteinTotal")),
            carbohydrateTotalGrams = parseCzechDouble(data.opt("carbohydrateTotal")),
            fatTotalGrams = parseCzechDouble(data.opt("fatTotal")),
            fiberTotalGrams = parseCzechDouble(data.opt("fiberTotal")),
            sugarTotalGrams = parseCzechDouble(data.opt("sugarTotal"))
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (isNull(key)) return null
        val value = optString(key, "").trim()
        return value.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }
}

