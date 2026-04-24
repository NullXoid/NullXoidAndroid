package com.nullxoid.android.data.update

import com.nullxoid.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

private const val RELEASES_URL =
    "https://api.github.com/repos/NullXoid/NullXoidAndroid/releases"
private val VERSION_TAG = Regex("""^v(\d+)\.(\d+)\.(\d+)$""")

data class AppUpdateInfo(
    val currentVersionName: String,
    val currentVersionCode: Int,
    val latestReleaseName: String,
    val versionCode: Int,
    val releasePageUrl: String,
    val apkDownloadUrl: String?,
    val updateAvailable: Boolean
)

class AppUpdateChecker(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun checkLatestDebugRelease(): AppUpdateInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "NullXoidAndroid/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Update check failed: HTTP ${response.code}")
            }

            val releases = JSONArray(response.body?.string().orEmpty())
            val latest = releases.versionedReleases()
                .maxByOrNull { it.versionCode }
                ?: error("No versioned APK release found")

            AppUpdateInfo(
                currentVersionName = BuildConfig.VERSION_NAME,
                currentVersionCode = BuildConfig.VERSION_CODE,
                latestReleaseName = latest.displayName,
                versionCode = latest.versionCode,
                releasePageUrl = latest.releasePageUrl,
                apkDownloadUrl = latest.apkDownloadUrl,
                updateAvailable = latest.versionCode > BuildConfig.VERSION_CODE
            )
        }
    }

    private fun JSONArray.versionedReleases(): List<ReleaseCandidate> = buildList {
        for (i in 0 until length()) {
            val release = optJSONObject(i) ?: continue
            val tag = release.optString("tag_name")
            val match = VERSION_TAG.matchEntire(tag) ?: continue
            val versionCode = match.groupValues[3].toIntOrNull() ?: continue
            add(
                ReleaseCandidate(
                    displayName = release.optString("name").ifBlank { tag },
                    versionCode = versionCode,
                    releasePageUrl = release.optString(
                        "html_url",
                        "https://github.com/NullXoid/NullXoidAndroid/releases/tag/$tag"
                    ),
                    apkDownloadUrl = release.findApkDownloadUrl()
                )
            )
        }
    }

    private fun JSONObject.findApkDownloadUrl(): String? {
        val assets = optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val assetName = asset.optString("name")
            if (assetName.endsWith(".apk", ignoreCase = true)) {
                return asset.optString("browser_download_url")
            }
        }
        return null
    }

    private data class ReleaseCandidate(
        val displayName: String,
        val versionCode: Int,
        val releasePageUrl: String,
        val apkDownloadUrl: String?
    )
}
