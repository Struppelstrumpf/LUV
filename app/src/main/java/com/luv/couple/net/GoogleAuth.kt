package com.luv.couple.net

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.luv.couple.billing.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object GoogleAuth {
    private const val TAG = "LuvGoogleAuth"

    data class Result(
        val idToken: String,
        val displayName: String?
    )

    suspend fun fetchWebClientId(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val cfg = LuvApiClient.authConfig()
            cfg.googleWebClientId?.takeIf { cfg.googleEnabled && it.isNotBlank() }
        }.getOrNull()
    }

    private fun client(activity: Activity, webClientId: String) =
        GoogleSignIn.getClient(
            activity,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
        )

    /**
     * Sign-In-Intent. Kein vorheriges signOut — das führt oft zu „Abgebrochen“
     * direkt nach der Kontoauswahl.
     */
    fun signInIntent(activity: Activity, webClientId: String): Intent =
        client(activity, webClientId).signInIntent

    /**
     * Ergebnis auswerten — auch wenn resultCode != RESULT_OK
     * (Google liefert bei SHA-/OAuth-Fehlern oft CANCELED + ApiException).
     */
    fun parseSignInIntentResult(data: Intent?, resultCode: Int = Activity.RESULT_OK): Result {
        if (data == null) {
            throw LuvApiException(
                if (resultCode == Activity.RESULT_CANCELED) "Abgebrochen."
                else "Google-Anmeldung fehlgeschlagen.",
                error = if (resultCode == Activity.RESULT_CANCELED) "cancelled" else "google_failed"
            )
        }
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            val account = task.getResult(ApiException::class.java)
            val token = account?.idToken?.takeIf { it.isNotBlank() }
                ?: throw LuvApiException(
                    "Kein Google-Token. In Google Cloud Console muss ein Android-OAuth-Client " +
                        "für com.luv.couple mit dem SHA-1 der App-Signatur existieren " +
                        "(Upload-Key und Play App Signing).",
                    error = "missing_id_token"
                )
            Result(idToken = token, displayName = account.displayName)
        } catch (e: ApiException) {
            Log.w(TAG, "GoogleSignIn ApiException status=${e.statusCode} ${e.message}")
            throw LuvApiException(messageForStatus(e.statusCode), error = errorForStatus(e.statusCode))
        }
    }

    private fun messageForStatus(code: Int): String = when (code) {
        GoogleSignInStatusCodes.SIGN_IN_CANCELLED,
        CommonStatusCodes.CANCELED -> "Abgebrochen."
        GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS ->
            "Anmeldung läuft schon — bitte kurz warten."
        GoogleSignInStatusCodes.SIGN_IN_FAILED ->
            "Google-Anmeldung fehlgeschlagen. Bitte erneut versuchen."
        CommonStatusCodes.NETWORK_ERROR ->
            "Netzwerkfehler bei Google — bitte Verbindung prüfen."
        CommonStatusCodes.DEVELOPER_ERROR, 10 ->
            "Google-Login: App-Signatur stimmt nicht (SHA-1). " +
                "Bitte Play-Store-Version nutzen oder Support kontaktieren."
        CommonStatusCodes.INVALID_ACCOUNT ->
            "Dieses Google-Konto ist ungültig — bitte ein anderes wählen."
        else -> "Google-Anmeldung fehlgeschlagen (Code $code)."
    }

    private fun errorForStatus(code: Int): String = when (code) {
        GoogleSignInStatusCodes.SIGN_IN_CANCELLED,
        CommonStatusCodes.CANCELED -> "cancelled"
        CommonStatusCodes.DEVELOPER_ERROR, 10 -> "developer_error"
        else -> "google_failed"
    }

    suspend fun signOut(context: Context) {
        runCatching {
            CredentialManager.create(context.applicationContext)
                .clearCredentialState(ClearCredentialStateRequest())
        }
        runCatching {
            val activity = context.findActivity() ?: return
            val webClientId = fetchWebClientId() ?: return
            client(activity, webClientId).signOut().await()
        }
    }
}
