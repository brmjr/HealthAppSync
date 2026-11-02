# Kalorické Tabulky → Health Connect (Android App)

Standalone Android application (Kotlin + Jetpack Compose + Health Connect SDK) that logs into **Kalorické Tabulky** (`kaloricketabulky.cz` / `kaloricketabulky.sk`), downloads your logged meals and macronutrients, and writes them into **Android Health Connect** (`NutritionRecord`).

## Why a Native Android App?
While Kalorické Tabulky's official Android app *reads* burned calories and weight from Health Connect, it does not export full meal/macronutrient records (`NutritionRecord`) into Health Connect, and Google Health Connect is strictly an **on-device Android API** (no cloud REST API). Running this lightweight companion app on your phone bridges `kaloricketabulky.cz` directly to Health Connect.

## Features
- **Direct Kalorické Tabulky API Client** ([`KalorickeTabulkyClient.kt`](app/src/main/java/cz/kaloricketabulky/healthsync/data/api/KalorickeTabulkyClient.kt)):
  - Authenticates via `POST /login/create?format=json&voucher=false` with MD5 password hashing and session cookie management (`kaloricketabulky_token`).
  - Fetches daily food diaries via `GET /user/diary/DD.MM.YYYY/get?format=json`.
  - Automatically converts `kJ` → `kcal` if your account is configured in kilojoules, and handles Czech decimal comma/space number formatting.
- **Full `NutritionRecord` Mapping** ([`HealthConnectManager.kt`](app/src/main/java/cz/kaloricketabulky/healthsync/health/HealthConnectManager.kt)):
  - Writes energy (`kcal`), protein (`g`), total carbohydrate (`g`), total fat (`g`), dietary fiber (`g`), sugar (`g`), saturated/trans/mono/polyunsaturated fat (`g`), cholesterol (`mg`), calcium (`mg`), and sodium (`g`).
  - Maps Czech & Slovak meal slots (*Snídaně*, *Dopolední svačina*, *Oběd*, *Odpolední svačina*, *Večeře*, *Druhá večeře*) to Health Connect `MealType` (`BREAKFAST`, `LUNCH`, `DINNER`, `SNACK`) with realistic meal time windows.
  - Supports two write modes:
    1. **Per Food Item (`PER_FOOD_ITEM`)**: One `NutritionRecord` per logged food item (`"Ovesné vločky (50 g)"`).
    2. **Per Meal Slot (`PER_MEAL_SLOT`)**: One aggregated `NutritionRecord` per meal slot (`"Snídaně: Ovesné vločky, Banán"`).
  - Cleanly replaces previous app-written records for synced days so deleted food items or updated portions never create duplicates in Health Connect.
- **Background Auto-Sync** ([`PeriodicSyncWorker.kt`](app/src/main/java/cz/kaloricketabulky/healthsync/sync/PeriodicSyncWorker.kt)):
  - Optional periodic background sync every 6 hours using Android `WorkManager`.
- **Encrypted Credentials Storage** ([`CredentialsStore.kt`](app/src/main/java/cz/kaloricketabulky/healthsync/data/prefs/CredentialsStore.kt)):
  - Stores your email and password locally on your device using Jetpack `EncryptedSharedPreferences`.

## Building & Installing
1. Open `c:\Antigravity\HealthAppSync` in **Android Studio** (Ladybug / Meerkat or newer).
2. Let Gradle sync dependencies.
3. Connect your Android phone (with USB debugging enabled) and click **Run (`app`)**.
4. In the app:
   - Tap **Povolit oprávnění Health Connect** and grant Nutrition read/write permissions.
   - Enter your **Kalorické Tabulky** e-mail and password.
   - Tap **Otestovat přihlášení a načíst dnešek** to verify your diary loads, or **Synchronizovat do Health Connect** to write your meals to Health Connect.

