package com.luv.couple.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.luv.couple.BuildConfig
import com.luv.couple.LuvApp
import com.luv.couple.MainActivity
import com.luv.couple.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class AppRelease(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String?,
    val notes: String
)

sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data object Checking : UpdateUiState()
    data object UpToDate : UpdateUiState()
    data class Available(val release: AppRelease) : UpdateUiState()
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateUiState()
    data class Ready(val release: AppRelease, val file: File) : UpdateUiState()
    data class Error(val message: String, val release: AppRelease? = null) : UpdateUiState()
}

object AppUpdater {
    private const val MANIFEST_URL = "https://reineke.pro/downloads/luv/version.json"
    private const val CHANNEL_ID = "luv_updates"
    private const val NOTIFICATION_ID = 71

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val _focusRequest = MutableStateFlow(false)
    val focusRequest: StateFlow<Boolean> = _focusRequest.asStateFlow()

    private val checking = AtomicBoolean(false)
    private val downloading = AtomicBoolean(false)

    fun offerFocus() {
        _focusRequest.value = true
    }

    fun consumeFocus(): Boolean {
        if (!_focusRequest.value) return false
        _focusRequest.value = false
        return true
    }

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "App-Updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Hinweis, wenn eine neue LUV-Version bereitsteht"
        }
        mgr.createNotificationChannel(channel)
    }

    fun versionLabel(): String = BuildConfig.VERSION_NAME

    /** Installierte Version (PackageManager) — nach Update auch vor Prozess-Neustart korrekt. */
    fun installedVersionCode(context: Context): Int {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        } catch (_: Throwable) {
            BuildConfig.VERSION_CODE
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun currentReleaseOrNull(): AppRelease? = when (val s = _state.value) {
        is UpdateUiState.Available -> s.release
        is UpdateUiState.Downloading -> s.release
        is UpdateUiState.Ready -> s.release
        is UpdateUiState.Error -> s.release
        else -> null
    }

    suspend fun check(context: Context, notify: Boolean = true): AppRelease? = withContext(Dispatchers.IO) {
        val installed = installedVersionCode(context).coerceAtLeast(BuildConfig.VERSION_CODE)

        // Download läuft — nicht abbrechen
        val current = _state.value
        if (current is UpdateUiState.Downloading) {
            return@withContext current.release
        }
        // Ready nur behalten, wenn die Version wirklich noch fehlt
        if (current is UpdateUiState.Ready) {
            if (current.release.versionCode > installed) {
                return@withContext current.release
            }
            _state.value = UpdateUiState.UpToDate
            return@withContext null
        }

        if (!checking.compareAndSet(false, true)) {
            return@withContext currentReleaseOrNull()
        }
        val keepForcedUi = _state.value is UpdateUiState.Available ||
            _state.value is UpdateUiState.Error
        if (!keepForcedUi) {
            _state.value = UpdateUiState.Checking
        }
        try {
            val request = Request.Builder()
                .url(MANIFEST_URL)
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val known = currentReleaseOrNull()
                    if (known == null) {
                        _state.value = UpdateUiState.Error("Update-Check fehlgeschlagen")
                    }
                    return@withContext known
                }
                val json = JSONObject(response.body?.string().orEmpty())
                val release = AppRelease(
                    versionCode = json.optInt("versionCode", 0),
                    versionName = json.optString("versionName", "?"),
                    apkUrl = json.optString("apkUrl", "https://reineke.pro/downloads/luv/LUV.apk"),
                    sha256 = json.optString("sha256").takeIf { it.isNotBlank() },
                    notes = json.optString("notes", "Neue Version verfügbar")
                )
                if (release.versionCode > installed) {
                    if (_state.value !is UpdateUiState.Downloading &&
                        _state.value !is UpdateUiState.Ready
                    ) {
                        _state.value = UpdateUiState.Available(release)
                    }
                    if (notify) maybeNotify(context, release)
                    release
                } else {
                    _state.value = UpdateUiState.UpToDate
                    null
                }
            }
        } catch (t: Throwable) {
            val known = currentReleaseOrNull()
            if (known == null) {
                _state.value = UpdateUiState.Error(t.message ?: "Update-Check fehlgeschlagen")
            }
            known
        } finally {
            checking.set(false)
        }
    }

    suspend fun downloadAndInstall(context: Context, release: AppRelease? = null): Boolean =
        withContext(Dispatchers.IO) {
            val targetRelease = release
                ?: (_state.value as? UpdateUiState.Available)?.release
                ?: (_state.value as? UpdateUiState.Error)?.release
                ?: (_state.value as? UpdateUiState.Ready)?.release
                ?: return@withContext false

            if (!downloading.compareAndSet(false, true)) return@withContext false
            try {
                if (!canRequestPackageInstalls(context)) {
                    withContext(Dispatchers.Main) { openInstallPermissionSettings(context) }
                    _state.value = UpdateUiState.Error(
                        "Bitte Installation aus unbekannten Quellen erlauben, dann erneut tippen.",
                        targetRelease
                    )
                    return@withContext false
                }

                _state.value = UpdateUiState.Downloading(targetRelease, 0f)
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val target = File(dir, "luv-${targetRelease.versionCode}.apk")
                if (target.exists()) target.delete()

                val request = Request.Builder().url(targetRelease.apkUrl).get().build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _state.value = UpdateUiState.Error("Download fehlgeschlagen", targetRelease)
                        return@withContext false
                    }
                    val body = response.body ?: run {
                        _state.value = UpdateUiState.Error("Leere Antwort", targetRelease)
                        return@withContext false
                    }
                    val total = body.contentLength()
                    val digest = MessageDigest.getInstance("SHA-256")
                    body.byteStream().use { input ->
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var read: Int
                            var done = 0L
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                done += read
                                if (total > 0) {
                                    _state.value = UpdateUiState.Downloading(
                                        targetRelease,
                                        (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                    )
                                }
                            }
                        }
                    }
                    val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                    val expected = targetRelease.sha256?.lowercase()
                    if (!expected.isNullOrBlank() && expected != actualSha) {
                        target.delete()
                        _state.value = UpdateUiState.Error("Checksumme ungültig — bitte erneut versuchen.", targetRelease)
                        return@withContext false
                    }
                }

                _state.value = UpdateUiState.Ready(targetRelease, target)
                withContext(Dispatchers.Main) {
                    installApkFile(context, target)
                }
                true
            } catch (t: Throwable) {
                _state.value = UpdateUiState.Error(t.message ?: "Download fehlgeschlagen", targetRelease)
                false
            } finally {
                downloading.set(false)
            }
        }

    fun installApkFile(context: Context, file: File) {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Legacy: APK aus Dateiauswahl installieren */
    fun installUpdate(context: Context, sourceUri: Uri): Result<Unit> = runCatching {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "luv-update.apk")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: error("APK konnte nicht gelesen werden")
        installApkFile(context, target)
    }

    private suspend fun maybeNotify(context: Context, release: AppRelease) {
        val prefs = LuvApp.instance.prefs
        val last = prefs.lastNotifiedUpdateCode()
        if (last >= release.versionCode) return
        prefs.setLastNotifiedUpdateCode(release.versionCode)

        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_UPDATE, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("LUV ${release.versionName} ist da")
            .setContentText(release.notes.ifBlank { "Tippe zum Aktualisieren" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(release.notes))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        context.getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, notification)
    }

    const val EXTRA_OPEN_UPDATE = "open_update"
}
