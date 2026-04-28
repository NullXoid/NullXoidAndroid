package com.nullxoid.android.data.update

import com.nullxoid.android.BuildConfig
import com.nullxoid.android.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

private val VERSION_TAG = Regex("""^v(\d+)\.(\d+)\.(\d+)$""")

data class AppUpdateInfo(
    val currentVersionName: String,
    val currentVersionCode: Int,
    val releaseSource: String,
    val latestReleaseName: String,
    val versionCode: Int,
    val releasePageUrl: String,
    val apkDownloadUrl: String?,
    val updateAvailable: Boolean
)

class AppUpdateChecker(
    private val sourcePreference: String = SettingsStore.UPDATE_SOURCE_AUTO,
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun checkLatestDebugRelease(): AppUpdateInfo = withContext(Dispatchers.IO) {
        val failures = mutableListOf<String>()
        for (source in updateSources()) {
            try {
                return@withContext checkSource(source)
            } catch (t: Throwable) {
                failures += "${source.name}: ${t.message ?: t::class.java.simpleName}"
            }
        }
        error("Update check failed: ${failures.joinToString("; ")}")
    }

    private fun checkSource(source: UpdateSource): AppUpdateInfo {
        val request = Request.Builder()
            .url(source.releasesUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "NullXoidAndroid/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }

            val releases = JSONArray(response.body?.string().orEmpty())
            val latest = releases.versionedReleases(source)
                .maxByOrNull { it.versionCode }
                ?: error("No versioned APK release found")

            return AppUpdateInfo(
                currentVersionName = BuildConfig.VERSION_NAME,
                currentVersionCode = BuildConfig.VERSION_CODE,
                releaseSource = source.name,
                latestReleaseName = latest.displayName,
                versionCode = latest.versionCode,
                releasePageUrl = latest.releasePageUrl,
                apkDownloadUrl = latest.apkDownloadUrl,
                updateAvailable = latest.versionCode > BuildConfig.VERSION_CODE
            )
        }
    }

    private fun updateSources(): List<UpdateSource> {
        val forgejo = UpdateSource(
            name = "Forgejo",
            releasesUrl = BuildConfig.APP_UPDATE_RELEASES_URL,
            releasePageBase = BuildConfig.APP_UPDATE_RELEASE_PAGE_BASE
        )
        val github = UpdateSource(
            name = "GitHub mirror",
            releasesUrl = BuildConfig.APP_UPDATE_FALLBACK_RELEASES_URL,
            releasePageBase = BuildConfig.APP_UPDATE_FALLBACK_RELEASE_PAGE_BASE
        )
        return when (SettingsStore.normalizeUpdateSource(sourcePreference)) {
            SettingsStore.UPDATE_SOURCE_FORGEJO -> listOf(forgejo)
            SettingsStore.UPDATE_SOURCE_GITHUB -> listOf(github)
            else -> listOf(forgejo, github)
        }.filter { it.releasesUrl.isNotBlank() }
    }

    private fun JSONArray.versionedReleases(source: UpdateSource): List<ReleaseCandidate> = buildList {
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
                        "${source.releasePageBase.trimEnd('/')}/tag/$tag"
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

    private data class UpdateSource(
        val name: String,
        val releasesUrl: String,
        val releasePageBase: String
    )

    private data class ReleaseCandidate(
        val displayName: String,
        val versionCode: Int,
        val releasePageUrl: String,
        val apkDownloadUrl: String?
    )
}
