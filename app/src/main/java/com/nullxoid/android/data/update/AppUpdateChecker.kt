package com.nullxoid.android.data.update

import com.nullxoid.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

private const val LATEST_DEBUG_RELEASE_URL =
    "https://api.github.com/repos/NullXoid/NullXoidAndroid/releases/tags/latest-debug"

data class AppUpdateInfo(
    val currentVersionName: String,
    val currentVersionCode: Int,
    val latestReleaseName: String,
    val releasePageUrl: String,
    val apkDownloadUrl: String?,
    val updateAvailable: Boolean
)

class AppUpdateChecker(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun checkLatestDebugRelease(): AppUpdateInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_DEBUG_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "NullXoidAndroid/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Update check failed: HTTP ${response.code}")
            }

            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            val releaseName = json.optString("name", "Latest Debug APK")
            val releasePageUrl = json.optString(
                "html_url",
                "https://github.com/NullXoid/NullXoidAndroid/releases/tag/latest-debug"
            )
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val assetName = asset.optString("name")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }

            AppUpdateInfo(
                currentVersionName = BuildConfig.VERSION_NAME,
                currentVersionCode = BuildConfig.VERSION_CODE,
                latestReleaseName = releaseName,
                releasePageUrl = releasePageUrl,
                apkDownloadUrl = apkUrl,
                updateAvailable = true
            )
        }
    }
}
