package cz.kaloricketabulky.healthsync.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class ReleaseUpdateInfo(
    val tagName: String,
    val title: String,
    val apkDownloadUrl: String,
    val htmlUrl: String,
    val isPreRelease: Boolean
)

/**
 * Checks GitHub Releases (`brmjr/HealthAppSync`) for the latest v2 or stable APK build.
 */
class UpdateChecker {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchLatestRelease(preferV2: Boolean = true): ReleaseUpdateInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASES_API_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "KalorickeTabulkyHealthConnectSync/2.0")
            .build()

        val bodyText = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("GitHub API vrátilo HTTP ${response.code}")
            }
            response.body?.string() ?: throw IllegalStateException("Prázdná odpověď z GitHub API")
        }

        val releases = JSONArray(bodyText)
        if (releases.length() == 0) {
            throw IllegalStateException("Na GitHubu zatím nejsou žádné vydané verze.")
        }

        var chosen = releases.getJSONObject(0)
        if (preferV2) {
            for (i in 0 until releases.length()) {
                val rel = releases.getJSONObject(i)
                val tag = rel.optString("tag_name", "")
                if (tag.startsWith("v2.", ignoreCase = true)) {
                    chosen = rel
                    break
                }
            }
        }

        val tagName = chosen.optString("tag_name", "unknown")
        val title = chosen.optString("name", tagName)
        val htmlUrl = chosen.optString("html_url", "https://github.com/brmjr/HealthAppSync/releases")
        val isPreRelease = chosen.optBoolean("prerelease", false)

        var apkUrl = htmlUrl
        val assets = chosen.optJSONArray("assets")
        if (assets != null) {
            for (j in 0 until assets.length()) {
                val asset = assets.getJSONObject(j)
                val downloadUrl = asset.optString("browser_download_url", "")
                if (downloadUrl.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = downloadUrl
                    break
                }
            }
        }

        ReleaseUpdateInfo(
            tagName = tagName,
            title = title,
            apkDownloadUrl = apkUrl,
            htmlUrl = htmlUrl,
            isPreRelease = isPreRelease
        )
    }

    companion object {
        private const val RELEASES_API_URL = "https://api.github.com/repos/brmjr/HealthAppSync/releases?per_page=10"
    }
}
