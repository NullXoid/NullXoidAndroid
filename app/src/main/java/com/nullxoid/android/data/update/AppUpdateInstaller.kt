package com.nullxoid.android.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class AppUpdateInstallResult(
    val installerLaunched: Boolean,
    val message: String? = null
)

class AppUpdateInstaller(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun downloadAndInstall(apkUrl: String): AppUpdateInstallResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            openUnknownAppSourcesSettings()
            return AppUpdateInstallResult(
                installerLaunched = false,
                message = "Allow installs from this app, then tap Install again."
            )
        }

        val apk = downloadApk(apkUrl)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        return AppUpdateInstallResult(installerLaunched = true)
    }

    private suspend fun downloadApk(apkUrl: String): File = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(apkUrl)
            .header("User-Agent", "NullXoidAndroid")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("APK download failed: HTTP ${response.code}")
            }

            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apk = File(updateDir, "NullXoidAndroid-update.apk")
            response.body?.byteStream()?.use { input ->
                apk.outputStream().use { output -> input.copyTo(output) }
            } ?: error("APK download returned an empty body")
            apk
        }
    }

    private fun openUnknownAppSourcesSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
