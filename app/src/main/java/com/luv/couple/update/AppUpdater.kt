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
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import com.luv.couple.BuildConfig
import com.luv.couple.LuvApp
import com.luv.couple.MainActivity
import com.luv.couple.R
import com.luv.couple.billing.findActivity
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

enum class UpdateChannel {
    /** Google Play In-App-Update (App aus dem Play Store) */
    PlayInApp,
    /** Play-Store-Seite öffnen (z. B. alte Sideload-APK) */
    PlayStore,
    /** Legacy: APK von der Website (nur Fallback, wenn kein Play) */
    WebsiteApk
}

data class AppRelease(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String?,
    val notes: String,
    val channel: UpdateChannel = UpdateChannel.WebsiteApk
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
    private const val PLAY_PACKAGE = "com.android.vending"
    private const val PLAY_LISTING =
        "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"
    private const val CHANNEL_ID = "luv_updates"
    private const val NOTIFICATION_ID = 71
    const val REQUEST_PLAY_UPDATE = 7142

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
    @Volatile
    private var lastNavCheckAt = 0L
    private const val NAV_CHECK_MIN_MS = 8_000L

    @Volatile
    private var playUpdateManager: AppUpdateManager? = null
    @Volatile
    private var cachedPlayUpdateInfo: AppUpdateInfo? = null

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

    /** true = App wurde über Google Play installiert / aktualisiert */
    fun isInstalledFromPlay(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(context.packageName)
            }
            installer == PLAY_PACKAGE
        } catch (_: Throwable) {
            false
        }
    }

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

    fun openPlayStoreListing(context: Context) {
        val market = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=${BuildConfig.APPLICATION_ID}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(PLAY_LISTING)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(market) }
            .recoverCatching { context.startActivity(web) }
    }

    fun currentReleaseOrNull(): AppRelease? = when (val s = _state.value) {
        is UpdateUiState.Available -> s.release
        is UpdateUiState.Downloading -> s.release
        is UpdateUiState.Ready -> s.release
        is UpdateUiState.Error -> s.release
        else -> null
    }

    /**
     * Leichter Check bei Tab-/Menüwechseln.
     * Throttled — unterbricht keine laufenden Käufe/Downloads.
     */
    suspend fun checkOnNavigate(context: Context): AppRelease? {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastNavCheckAt < NAV_CHECK_MIN_MS) {
            return currentReleaseOrNull()
        }
        when (_state.value) {
            is UpdateUiState.Downloading, is UpdateUiState.Ready ->
                return currentReleaseOrNull()
            else -> Unit
        }
        lastNavCheckAt = now
        return check(context, notify = false)
    }

    suspend fun check(context: Context, notify: Boolean = true): AppRelease? = withContext(Dispatchers.IO) {
        val installed = installedVersionCode(context).coerceAtLeast(BuildConfig.VERSION_CODE)

        val current = _state.value
        if (current is UpdateUiState.Downloading) {
            return@withContext current.release
        }
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
            if (isInstalledFromPlay(context)) {
                // Nur anzeigen, wenn Google Play das Update wirklich ausliefert
                // (nicht schon bei Deploy / version.json, solange Review/Rollout läuft).
                val play = checkPlayUpdate(context, installed, notify)
                if (play != null) return@withContext play
                if (_state.value !is UpdateUiState.Downloading &&
                    _state.value !is UpdateUiState.Ready
                ) {
                    _state.value = UpdateUiState.UpToDate
                }
                null
            } else {
                // Sideload: version.json → Hinweis, Update über Play Store
                checkManifestThenPlayStore(context, installed, notify)
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

    private suspend fun checkPlayUpdate(
        context: Context,
        installed: Int,
        notify: Boolean
    ): AppRelease? {
        val manager = playUpdateManager
            ?: AppUpdateManagerFactory.create(context.applicationContext).also {
                playUpdateManager = it
            }
        val info = try {
            manager.requestAppUpdateInfo()
        } catch (_: Throwable) {
            // Play Services / Simulator — kein Update erzwingen
            return null
        }
        cachedPlayUpdateInfo = info

        val availability = info.updateAvailability()
        val remoteCode = info.availableVersionCode()

        if (availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
            val release = playRelease(info, installed)
            _state.value = UpdateUiState.Available(release)
            withContext(Dispatchers.Main) {
                startPlayInAppUpdate(context)
            }
            return release
        }

        // Nur wenn Play das Update wirklich anbietet (Review/Rollout fertig)
        if (availability == UpdateAvailability.UPDATE_AVAILABLE && remoteCode > installed) {
            val release = playRelease(info, installed)
            if (_state.value !is UpdateUiState.Downloading &&
                _state.value !is UpdateUiState.Ready
            ) {
                _state.value = UpdateUiState.Available(release)
            }
            if (notify) maybeNotify(context, release)
            return release
        }

        return null
    }

    private fun playRelease(info: AppUpdateInfo, installed: Int): AppRelease {
        val code = info.availableVersionCode().coerceAtLeast(installed + 1)
        return AppRelease(
            versionCode = code,
            versionName = AppChangelog.entries.firstOrNull()?.version ?: "Play #$code",
            apkUrl = PLAY_LISTING,
            sha256 = null,
            notes = "Neue Version im Google Play Store — bitte aktualisieren.",
            channel = UpdateChannel.PlayInApp
        )
    }

    private suspend fun checkManifestThenPlayStore(
        context: Context,
        installed: Int,
        notify: Boolean
    ): AppRelease? {
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
                return known
            }
            val json = JSONObject(response.body?.string().orEmpty())
            val remoteCode = json.optInt("versionCode", 0)
            if (remoteCode <= installed) {
                _state.value = UpdateUiState.UpToDate
                return null
            }
            val release = AppRelease(
                versionCode = remoteCode,
                versionName = json.optString("versionName", "?"),
                apkUrl = PLAY_LISTING,
                sha256 = null,
                notes = json.optString(
                    "notes",
                    "Neue Version — bitte über den Google Play Store aktualisieren."
                ),
                channel = UpdateChannel.PlayStore
            )
            if (_state.value !is UpdateUiState.Downloading &&
                _state.value !is UpdateUiState.Ready
            ) {
                _state.value = UpdateUiState.Available(release)
            }
            if (notify) maybeNotify(context, release)
            return release
        }
    }

    /**
     * Update starten: Play In-App, Play-Store-Seite oder (Legacy) APK.
     */
    suspend fun downloadAndInstall(context: Context, release: AppRelease? = null): Boolean =
        withContext(Dispatchers.Main) {
            val targetRelease = release
                ?: (_state.value as? UpdateUiState.Available)?.release
                ?: (_state.value as? UpdateUiState.Error)?.release
                ?: (_state.value as? UpdateUiState.Ready)?.release
                ?: return@withContext false

            when (targetRelease.channel) {
                UpdateChannel.PlayInApp -> startPlayInAppUpdate(context)
                UpdateChannel.PlayStore -> {
                    openPlayStoreListing(context)
                    true
                }
                UpdateChannel.WebsiteApk -> {
                    // Kein Sideload-Install mehr (Play-Policy) — immer über Play Store.
                    withContext(Dispatchers.Main) { openPlayStoreListing(context) }
                    true
                }
            }
        }

    private fun startPlayInAppUpdate(context: Context): Boolean {
        val activity = context.findActivity() ?: run {
            openPlayStoreListing(context)
            return true
        }
        val manager = playUpdateManager
            ?: AppUpdateManagerFactory.create(context.applicationContext).also {
                playUpdateManager = it
            }
        // Info ggf. neu laden — cached kann abgelaufen sein
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                cachedPlayUpdateInfo = info
                val available =
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
                        info.updateAvailability() ==
                        UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                if (!available) {
                    openPlayStoreListing(context)
                    return@addOnSuccessListener
                }
                val updateType = when {
                    info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> AppUpdateType.IMMEDIATE
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> AppUpdateType.FLEXIBLE
                    else -> {
                        openPlayStoreListing(context)
                        return@addOnSuccessListener
                    }
                }
                runCatching {
                    manager.startUpdateFlowForResult(
                        info,
                        activity,
                        AppUpdateOptions.newBuilder(updateType).build(),
                        REQUEST_PLAY_UPDATE
                    )
                }.onFailure {
                    openPlayStoreListing(context)
                }
            }
            .addOnFailureListener {
                openPlayStoreListing(context)
            }
        return true
    }

    private suspend fun downloadWebsiteApk(context: Context, targetRelease: AppRelease): Boolean {
        if (!downloading.compareAndSet(false, true)) return false
        try {
            if (!canRequestPackageInstalls(context)) {
                withContext(Dispatchers.Main) { openInstallPermissionSettings(context) }
                _state.value = UpdateUiState.Error(
                    "Bitte Installation aus unbekannten Quellen erlauben, dann erneut tippen.",
                    targetRelease
                )
                return false
            }

            _state.value = UpdateUiState.Downloading(targetRelease, 0f)
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "luv-${targetRelease.versionCode}.apk")
            if (target.exists()) target.delete()

            val request = Request.Builder().url(targetRelease.apkUrl).get().build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _state.value = UpdateUiState.Error("Download fehlgeschlagen", targetRelease)
                    return false
                }
                val body = response.body ?: run {
                    _state.value = UpdateUiState.Error("Leere Antwort", targetRelease)
                    return false
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
                    _state.value = UpdateUiState.Error(
                        "Checksumme ungültig — bitte erneut versuchen.",
                        targetRelease
                    )
                    return false
                }
            }

            _state.value = UpdateUiState.Ready(targetRelease, target)
            withContext(Dispatchers.Main) {
                installApkFile(context, target)
            }
            return true
        } catch (t: Throwable) {
            _state.value = UpdateUiState.Error(t.message ?: "Download fehlgeschlagen", targetRelease)
            return false
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
