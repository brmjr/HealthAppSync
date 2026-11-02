package cz.kaloricketabulky.healthsync

import androidx.health.connect.client.records.MealType
import cz.kaloricketabulky.healthsync.data.model.KalorickeTabulkyParser
import cz.kaloricketabulky.healthsync.data.model.MealSlotMapper
import cz.kaloricketabulky.healthsync.data.model.MealSlotTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class KalorickeTabulkyParserTest {

    @Test
    fun `parseCzechDouble handles commas and thin spaces`() {
        assertEquals(28.25, KalorickeTabulkyParser.parseCzechDouble("28,25") ?: 0.0, 0.001)
        assertEquals(1234.5, KalorickeTabulkyParser.parseCzechDouble("1\u00a0234,5") ?: 0.0, 0.001)
        assertEquals(1234.5, KalorickeTabulkyParser.parseCzechDouble("1\u202f234.5") ?: 0.0, 0.001)
        assertEquals(61.222, KalorickeTabulkyParser.parseCzechDouble(61.222) ?: 0.0, 0.001)
    }

    @Test
    fun `md5Hex produces expected lowercase hex digest`() {
        assertEquals(
            "5d41402abc4b2a76b9719d911017c592",
            KalorickeTabulkyParser.md5Hex("hello")
        )
    }

    @Test
    fun `MealSlotMapper maps Czech and Slovak meal titles and custom times`() {
        assertEquals(MealType.MEAL_TYPE_BREAKFAST, MealSlotMapper.toHealthConnectMealType("Snídaně"))
        assertEquals(MealType.MEAL_TYPE_BREAKFAST, MealSlotMapper.toHealthConnectMealType("Raňajky"))
        assertEquals(MealType.MEAL_TYPE_SNACK, MealSlotMapper.toHealthConnectMealType("Dopolední svačina"))
        assertEquals(MealType.MEAL_TYPE_LUNCH, MealSlotMapper.toHealthConnectMealType("Oběd"))
        assertEquals(MealType.MEAL_TYPE_SNACK, MealSlotMapper.toHealthConnectMealType("Odpolední svačina"))
        assertEquals(MealType.MEAL_TYPE_DINNER, MealSlotMapper.toHealthConnectMealType("Večeře"))
        assertEquals(MealType.MEAL_TYPE_SNACK, MealSlotMapper.toHealthConnectMealType("Druhá večeře"))

        val customTimes = MealSlotTimes(breakfast = "07:15", lunch = "13:00")
        assertEquals(LocalTime.of(7, 15), MealSlotMapper.startTimeFor("Snídaně", customTimes))
        assertEquals(LocalTime.of(13, 0), MealSlotMapper.startTimeFor("Oběd", customTimes))
    }

    @Test
    fun `parseDiaryResponse parses meals, drinkRegime, and converts salt to sodium`() {
        val sampleJson = """
            {
              "requestId": null,
              "code": 0,
              "message": null,
              "data": {
                "date": 1607317075415,
                "energyUnit": "kcal",
                "proteinTotal": 42.5,
                "carbohydrateTotal": 110.0,
                "fatTotal": 25.0,
                "fiberTotal": 12.0,
                "sugarTotal": 18.0,
                "energyTotal": 850.0,
                "drinkRegime": "2,35",
                "times": [
                  {
                    "id": "22.527",
                    "title": "Snídaně",
                    "foodstuff": [
                      {
                        "id": "item_1",
                        "type": "foodstuff",
                        "title": "Žitný chléb",
                        "unit": "1 x krajíc (60 g)",
                        "energy": "145,0",
                        "energyUnit": "kcal",
                        "protein": 4.5,
                        "carbohydrate": 28.0,
                        "fat": 1.0,
                        "fiber": 4.2,
                        "sugar": 1.1,
                        "sodium": null,
                        "salt": "1,2",
                        "time": "08:15"
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val diary = KalorickeTabulkyParser.parseDiaryResponse(sampleJson, LocalDate.of(2026, 11, 2))
        assertEquals(1, diary.meals.size)
        assertEquals(1, diary.allFoodItems.size)
        assertNotNull(diary.drinkRegimeLiters)
        assertEquals(2.35, diary.drinkRegimeLiters!!, 0.001)

        val item = diary.allFoodItems.first()
        assertEquals("item_1", item.id)
        assertEquals(1.2, item.saltGrams!!, 0.001)
        assertNotNull(item.effectiveSodiumGrams)
        assertEquals(1.2 * 0.3934, item.effectiveSodiumGrams!!, 0.0001)
    }
}
