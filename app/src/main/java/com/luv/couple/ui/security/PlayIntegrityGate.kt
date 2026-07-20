package com.luv.couple.ui.security

import android.content.Context
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.luv.couple.net.LuvApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Play Integrity: Token beweist grob „echte Play-App auf echtem Gerät“.
 * Kein Captcha — läuft unsichtbar vor Signup / Coin-Kauf.
 */
object PlayIntegrityGate {
    private const val TAG = "LuvPlayIntegrity"

    data class Attestation(
        val nonce: String,
        val integrityToken: String
    )

    /**
     * Signup: nur wenn Server Enforcement verlangt.
     */
    suspend fun attestForSignup(context: Context): Attestation? =
        attest(context, onlyWhenRequired = true)

    /**
     * Coin-Kauf: Token holen sobald Server einen Nonce ausstellt (auch Soft-Mode).
     */
    suspend fun attestForPurchase(context: Context): Attestation? =
        attest(context, onlyWhenRequired = false)

    /**
     * @param onlyWhenRequired wenn true und Server `required=false`, kein Token (spart Play-API).
     */
    private suspend fun attest(
        context: Context,
        onlyWhenRequired: Boolean
    ): Attestation? = withContext(Dispatchers.IO) {
        val challenge = runCatching { LuvApiClient.fetchIntegrityNonce() }.getOrNull()
            ?: return@withContext null
        val nonce = challenge.nonce?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        if (onlyWhenRequired && !challenge.required) {
            return@withContext null
        }
        val projectNumber = challenge.cloudProjectNumber?.toLongOrNull()
            ?: return@withContext null
        try {
            val manager = IntegrityManagerFactory.create(context.applicationContext)
            val request = IntegrityTokenRequest.builder()
                .setNonce(nonce)
                .setCloudProjectNumber(projectNumber)
                .build()
            val token = manager.requestIntegrityToken(request).await().token()
            if (token.isNullOrBlank()) {
                Log.w(TAG, "empty integrity token")
                return@withContext null
            }
            Attestation(nonce = nonce, integrityToken = token)
        } catch (t: Throwable) {
            Log.w(TAG, "integrity request failed: ${t.message}")
            null
        }
    }
}
