package com.luv.couple.net

import android.content.Context
import android.os.Build
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Liest Play Install Referrer (`join=CODE`) einmalig und legt PendingJoin.
 *
 * Wichtig: Play liefert den Referrer für die gesamte Install-Lebensdauer.
 * Nach „Speicher löschen“ wäre KEY_DONE weg — ohne Zeitfenster würde ein alter
 * Einladungs-Code erneut greifen (tote Lobby / falsches Onboarding).
 */
object InstallReferrerJoin {
    private const val TAG = "LuvInstallReferrer"
    private const val PREF = "luv_install_referrer"
    private const val KEY_DONE = "referrer_checked"
    /** Nur kurz nach Erstinstallation akzeptieren (nicht nach Cache/Daten-Wipe). */
    private const val MAX_AGE_MS = 48L * 60L * 60L * 1000L

    suspend fun captureOnce(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) return
        if (!PendingJoin.peek().isNullOrBlank()) {
            prefs.edit().putBoolean(KEY_DONE, true).apply()
            return
        }
        if (!isFreshInstall(app)) {
            Log.i(TAG, "skip referrer: install too old (likely data clear)")
            prefs.edit().putBoolean(KEY_DONE, true).apply()
            return
        }
        val raw = withTimeoutOrNull(8_000L) { fetchReferrer(app) }
        prefs.edit().putBoolean(KEY_DONE, true).apply()
        if (raw.isNullOrBlank()) return
        val code = parseJoinCode(raw)
        if (!code.isNullOrBlank()) {
            Log.i(TAG, "join from referrer: $code")
            PendingJoin.offer(code)
            PendingSplashSkip.offer()
        }
    }

    private fun isFreshInstall(context: Context): Boolean {
        val installedAt = runCatching {
            val pm = context.packageManager
            val pkg = context.packageName
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(pkg, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                    .firstInstallTime
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0).firstInstallTime
            }
        }.getOrDefault(0L)
        if (installedAt <= 0L) return false
        return System.currentTimeMillis() - installedAt <= MAX_AGE_MS
    }

    private fun parseJoinCode(referrer: String): String? {
        // Nur explizites join=… — kein Fallback über den ganzen Referrer-String
        // (sonst können utm_*-Werte als Code missverstanden werden).
        val decoded = runCatching {
            java.net.URLDecoder.decode(referrer, Charsets.UTF_8.name())
        }.getOrDefault(referrer)
        return Regex("""(?:^|[&?])join=([A-Za-z0-9]{4,12})""", RegexOption.IGNORE_CASE)
            .find(decoded)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { LuvApiClient.normalizeCode(it) }
    }

    private suspend fun fetchReferrer(context: Context): String? =
        suspendCancellableCoroutine { cont ->
            val client = InstallReferrerClient.newBuilder(context).build()
            cont.invokeOnCancellation {
                runCatching { client.endConnection() }
            }
            try {
                client.startConnection(object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(responseCode: Int) {
                        try {
                            if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                                val ref = client.installReferrer?.installReferrer
                                if (cont.isActive) cont.resume(ref)
                            } else if (cont.isActive) {
                                cont.resume(null)
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "referrer read failed: ${t.message}")
                            if (cont.isActive) cont.resume(null)
                        } finally {
                            runCatching { client.endConnection() }
                        }
                    }

                    override fun onInstallReferrerServiceDisconnected() {
                        if (cont.isActive) cont.resume(null)
                    }
                })
            } catch (t: Throwable) {
                Log.w(TAG, "referrer connect failed: ${t.message}")
                if (cont.isActive) cont.resume(null)
            }
        }
}
