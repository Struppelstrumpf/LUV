package com.luv.couple.net

import android.content.Context
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Liest Play Install Referrer (`join=CODE`) einmalig und legt PendingJoin.
 */
object InstallReferrerJoin {
    private const val TAG = "LuvInstallReferrer"
    private const val PREF = "luv_install_referrer"
    private const val KEY_DONE = "referrer_checked"

    suspend fun captureOnce(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) return
        if (!PendingJoin.peek().isNullOrBlank()) {
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

    private fun parseJoinCode(referrer: String): String? {
        // join=ABC12 oder utm_content=join%3DABC12 etc.
        val decoded = runCatching {
            java.net.URLDecoder.decode(referrer, Charsets.UTF_8.name())
        }.getOrDefault(referrer)
        Regex("""(?:^|[&?])join=([A-Za-z0-9]{4,12})""", RegexOption.IGNORE_CASE)
            .find(decoded)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return LuvApiClient.normalizeCode(it) }
        return LuvApiClient.normalizeCode(decoded)
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
