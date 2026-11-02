package cz.kaloricketabulky.healthsync

import androidx.health.connect.client.records.MealType
import cz.kaloricketabulky.healthsync.data.model.KalorickeTabulkyParser
import cz.kaloricketabulky.healthsync.data.model.MealSlotMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

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
    fun `MealSlotMapper maps Czech and Slovak meal titles to Health Connect MealType`() {
        assertEquals(MealType.MEAL_TYPE_BREAKFAST, MealSlotMapper.toHealthConnectMealType("Snídaně"))
        assertEquals(MealType.MEAL_TYPE_BREAKFAST, MealSlotMapper.toHealthConnectMealType("Raňajky"))
        assertEquals(MealType.MEAL_TYPE_SNACK, MealSlotMapper.toHealthConnectMealType("Dopolední svačina"))
        assertEquals(MealType.MEAL_TYPE_LUNCH, MealSlotMapper.toHealthConnectMealType("Oběd"))
        assertEquals(MealType.MEAL_TYPE_SNACK, MealSlotMapper.toHealthConnectMealType("Odpolední svačina"))
        assertEquals(MealType.MEAL_TYPE_DINNER, MealSlotMapper.toHealthConnectMealType("Večeře"))
        assertEquals(MealType.MEAL_TYPE_SNACK, MealSlotMapper.toHealthConnectMealType("Druhá večeře"))
    }

    @Test
    fun `parseDiaryResponse parses meals and foodstuff items accurately`() {
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
                "times": [
                  {
                    "id": "22.527",
                    "title": "Snídaně",
                    "foodstuff": [
                      {
                        "id": "item_1",
                        "type": "foodstuff",
                        "title": "Ovesné vločky",
                        "unit": "1 x porce (50 g)",
                        "energy": "185,5",
                        "energyUnit": "kcal",
                        "protein": 6.5,
                        "carbohydrate": 30.2,
                        "fat": 3.5,
                        "fiber": 5.0,
                        "sugar": 0.7,
                        "saturatedFattyAcid": 0.6,
                        "time": "08:15"
                      }
                    ]
                  },
                  {
                    "id": "98.938",
                    "title": "Oběd",
                    "foodstuff": []
                  }
                ]
              }
            }
        """.trimIndent()

        val diary = KalorickeTabulkyParser.parseDiaryResponse(sampleJson, LocalDate.of(2026, 11, 2))
        assertEquals(2, diary.meals.size)
        assertEquals(1, diary.allFoodItems.size)

        val firstItem = diary.allFoodItems.first()
        assertEquals("item_1", firstItem.id)
        assertEquals("Ovesné vločky (1 x porce (50 g))", firstItem.displayName)
        assertNotNull(firstItem.energyKcal)
        assertEquals(185.5, firstItem.energyKcal!!, 0.001)
        assertEquals(6.5, firstItem.proteinGrams!!, 0.001)
        assertEquals(30.2, firstItem.carbohydrateGrams!!, 0.001)
        assertEquals(3.5, firstItem.fatGrams!!, 0.001)
        assertEquals(5.0, firstItem.fiberGrams!!, 0.001)
        assertEquals("08:15", firstItem.time)
    }
}

