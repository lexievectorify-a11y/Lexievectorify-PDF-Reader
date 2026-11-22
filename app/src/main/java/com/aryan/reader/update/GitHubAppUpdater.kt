package com.aryan.reader.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.aryan.reader.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val RELEASES_URL =
    "https://api.github.com/repos/lexievectorify-a11y/Lexievectorify-PDF-Reader/releases/latest"
private const val EXPECTED_SIGNATURE_SHA256 =
    "54E26E0B69EDCE0368A8237598C468204623A6F91551AE345279D6778798AA99"
private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

data class ReleaseInfo(
    val tag: String,
    val versionCode: Int,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
)

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    object UpToDate : UpdateState
    object NotPersonal : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data class NeedPermission(val release: ReleaseInfo) : UpdateState
    data class Error(val message: String) : UpdateState
}

object GitHubAppUpdater {
    private const val PREFS_NAME = "personal_updater"
    private const val KEY_LAST_CHECK = "last_check"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun check(context: Context, force: Boolean = false) {
        if (BuildConfig.PERSONAL_BUILD_NUMBER <= 0) {
            _state.value = UpdateState.NotPersonal
            return
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return
        scope.launch {
            _state.value = UpdateState.Checking
            runCatching { fetchLatestRelease() }
                .onSuccess { release ->
                    prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                    _state.value =
                        if (UpdateVersionParser.isNewer(release.versionCode, BuildConfig.PERSONAL_BUILD_NUMBER)) {
                            UpdateState.Available(release)
                        } else {
                            UpdateState.UpToDate
                        }
                }
                .onFailure { _state.value = UpdateState.Error(it.message ?: "Update check failed") }
        }
    }

    fun downloadAndInstall(activity: Activity, release: ReleaseInfo) {
        scope.launch {
            _state.value = UpdateState.Downloading(0)
            runCatching {
                val file = download(activity.applicationContext, release)
                verify(activity.applicationContext, file)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !activity.packageManager.canRequestPackageInstalls()
                ) {
                    _state.value = UpdateState.NeedPermission(release)
                    return@launch
                }
                install(activity, file)
            }.onFailure { error ->
                _state.value = UpdateState.Error(error.message ?: "Update install failed")
            }
        }
    }

    fun openPermissionSettings(activity: Activity) {
        runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}"),
            )
            activity.startActivity(intent)
        }.onFailure { _state.value = UpdateState.Error(it.message ?: "Cannot open install settings") }
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    private fun fetchLatestRelease(): ReleaseInfo {
        val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Episteme-Personal-Updater")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            val code = connection.responseCode
            if (code !in 200..299) error("Update check failed (HTTP $code)")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name")
            val versionCode = UpdateVersionParser.buildNumberFromTag(tag)
                ?: error("Latest release is not a personal build")
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            var apkName: String? = null
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    val name = asset.optString("name")
                    if (!name.endsWith(".apk", ignoreCase = true)) continue
                    apkUrl = asset.optString("browser_download_url")
                    apkName = name
                    break
                }
            }
            ReleaseInfo(
                tag = tag,
                versionCode = versionCode,
                notes = json.optString("body"),
                apkUrl = apkUrl.takeUnless { it.isNullOrBlank() } ?: error("Latest release has no APK"),
                apkName = apkName ?: "update.apk",
            )
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun download(context: Context, release: ReleaseInfo): File = withContext(Dispatchers.IO) {
        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(updateDir, "update.apk")
        if (file.exists()) file.delete()
        val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
        return@withContext try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Episteme-Personal-Updater")
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            val code = connection.responseCode
            if (code !in 200..299) error("Download failed (HTTP $code)")
            val total = connection.contentLength
            var lastPercent = -1
            FileOutputStream(file).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(32 * 1024)
                    var readCount: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { readCount = it } != -1) {
                        output.write(buffer, 0, readCount)
                        totalRead += readCount
                        if (total > 0) {
                            val percent = (totalRead * 100 / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _state.value = UpdateState.Downloading(percent)
                            }
                        }
                    }
                }
            }
            file
        } finally {
            connection.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun verify(context: Context, file: File) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_META_DATA
        } else {
            PackageManager.GET_META_DATA
        }
        val packageInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("Downloaded file is not a valid APK")
        if (packageInfo.packageName != context.packageName) {
            error("Downloaded APK has a different package name")
        }
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            packageInfo.signatures
        }
        if (signatures.isNullOrEmpty()) error("Downloaded APK is not signed")
        val digest = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray())
        val sha256 = digest.joinToString("") { "%02X".format(it.toInt() and 0xff) }
        if (sha256 != EXPECTED_SIGNATURE_SHA256) {
            error("Downloaded APK has an unexpected signature")
        }
    }

    private fun install(activity: Activity, file: File) {
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.provider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(intent)
    }
}
