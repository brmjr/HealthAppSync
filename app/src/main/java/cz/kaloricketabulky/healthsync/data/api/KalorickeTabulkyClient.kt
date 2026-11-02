package cz.kaloricketabulky.healthsync.data.api

import cz.kaloricketabulky.healthsync.data.model.KalorickeTabulkyParser
import cz.kaloricketabulky.healthsync.data.model.KtDayDiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class KalorickeTabulkyAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)
class KalorickeTabulkyApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Asynchronous client for the internal JSON API of kaloricketabulky.cz.
 */
class KalorickeTabulkyClient(
    private val baseUrl: String = DEFAULT_BASE_URL
) {
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val hostCookies = cookieStore.getOrPut(url.host) { mutableListOf() }
            synchronized(hostCookies) {
                for (newCookie in cookies) {
                    hostCookies.removeAll { it.name == newCookie.name }
                    hostCookies.add(newCookie)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val hostCookies = cookieStore[url.host] ?: return emptyList()
            synchronized(hostCookies) {
                val now = System.currentTimeMillis()
                hostCookies.removeAll { it.expiresAt < now }
                return hostCookies.toList()
            }
        }
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    /**
     * Authenticates with email and plaintext password (which is MD5-hashed before sending,
     * as expected by kaloricketabulky.cz) and stores the session cookies in the CookieJar.
     */
    suspend fun login(email: String, password: String) = withContext(Dispatchers.IO) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || password.isEmpty()) {
            throw KalorickeTabulkyAuthException("Vyplňte e-mail a heslo ke Kalorickým Tabulkám.")
        }

        val payload = JSONObject().apply {
            put("email", trimmedEmail)
            put("password", KalorickeTabulkyParser.md5Hex(password))
        }

        val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val url = "$baseUrl/login/create?format=json&voucher=false"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()

        val bodyText = try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw KalorickeTabulkyAuthException("Přihlášení selhalo (HTTP ${response.code})")
                }
                response.body?.string()
                    ?: throw KalorickeTabulkyAuthException("Prázdná odpověď při přihlašování")
            }
        } catch (e: IOException) {
            throw KalorickeTabulkyAuthException("Chyba sítě při přihlašování: ${e.localizedMessage}", e)
        }

        val json = try {
            JSONObject(bodyText)
        } catch (e: Exception) {
            throw KalorickeTabulkyAuthException("Neplatná JSON odpověď při přihlášení", e)
        }

        val code = json.optInt("code", -1)
        if (code != 0) {
            val msg = json.optString("message").takeIf { it.isNotBlank() }
                ?: "Nesprávný e-mail nebo heslo (kód $code)"
            throw KalorickeTabulkyAuthException(msg)
        }

        val hasTokenCookie = cookieStore.values.flatten().any { it.name == TOKEN_COOKIE_NAME }
        if (!hasTokenCookie) {
            throw KalorickeTabulkyAuthException(
                "Přihlášení proběhlo, ale server nenastavil session cookie ($TOKEN_COOKIE_NAME)."
            )
        }
    }

    /**
     * Fetches the food diary for a given [date] (`GET /user/diary/DD.MM.YYYY/get?format=json`).
     */
    suspend fun getDiary(date: LocalDate): KtDayDiary = withContext(Dispatchers.IO) {
        val formattedDate = date.format(PATH_DATE_FORMATTER)
        val url = "$baseUrl/user/diary/$formattedDate/get?format=json"

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()

        val bodyText = try {
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    throw KalorickeTabulkyAuthException("Relace vypršela (HTTP ${response.code})")
                }
                if (!response.isSuccessful) {
                    throw KalorickeTabulkyApiException(
                        "Nepodařilo se načíst jídelníček pro $formattedDate (HTTP ${response.code})"
                    )
                }
                response.body?.string()
                    ?: throw KalorickeTabulkyApiException("Prázdná odpověď pro den $formattedDate")
            }
        } catch (e: IOException) {
            throw KalorickeTabulkyApiException(
                "Chyba sítě při načítání dne $formattedDate: ${e.localizedMessage}",
                e
            )
        }

        try {
            KalorickeTabulkyParser.parseDiaryResponse(bodyText, date)
        } catch (e: Exception) {
            throw KalorickeTabulkyApiException(
                "Chyba při zpracování dat pro $formattedDate: ${e.localizedMessage}",
                e
            )
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://www.kaloricketabulky.cz"
        const val TOKEN_COOKIE_NAME = "kaloricketabulky_token"
        private const val USER_AGENT = "KalorickeTabulkyHealthConnectSync/1.0 (Android)"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val PATH_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    }
}

