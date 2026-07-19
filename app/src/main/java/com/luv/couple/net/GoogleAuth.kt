package com.luv.couple.net

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.luv.couple.billing.findActivity
import kotlinx.coroutines.Dispatchers
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

    /**
     * Google-Konto wählen → ID-Token für den Server.
     * Braucht eine Activity (Credential Manager UI).
     */
    suspend fun signIn(context: Context): Result {
        val activity = context.findActivity()
            ?: throw LuvApiException(
                "Google-Anmeldung ist gerade nicht möglich — App neu öffnen und erneut tippen.",
                error = "no_activity"
            )
        val webClientId = fetchWebClientId()
            ?: throw LuvApiException(
                "Google-Login ist noch nicht eingerichtet. Bitte später erneut versuchen.",
                error = "google_disabled"
            )
        val manager = CredentialManager.create(activity)

        // Button-Flow: zuerst Sign-In-with-Google (zuverlässiger als Bottom-Sheet)
        val attempts = listOf(
            GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetSignInWithGoogleOption.Builder(webClientId).build()
                )
                .build(),
            GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(webClientId)
                        .setAutoSelectEnabled(false)
                        .build()
                )
                .build(),
            GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(true)
                        .setServerClientId(webClientId)
                        .setAutoSelectEnabled(false)
                        .build()
                )
                .build()
        )

        var lastError: Exception? = null
        for ((index, request) in attempts.withIndex()) {
            try {
                val response = manager.getCredential(activity, request)
                return parseIdToken(response)
            } catch (e: GetCredentialCancellationException) {
                // Oft fälschlich bei OAuth/SHA-Fehlern — nicht still schlucken
                Log.w(TAG, "attempt $index cancelled: ${e.message}")
                if (index == 0) {
                    // Einmal Fallback auf Bottom-Sheet versuchen
                    lastError = e
                    continue
                }
                throw LuvApiException(
                    "Google-Anmeldung fehlgeschlagen. Bitte erneut versuchen " +
                        "(oder die App aus dem Play Store nutzen).",
                    error = "google_cancelled"
                )
            } catch (e: NoCredentialException) {
                Log.w(TAG, "attempt $index no credential: ${e.message}")
                lastError = e
                continue
            } catch (e: GetCredentialException) {
                Log.w(TAG, "attempt $index failed: ${e.type} ${e.message}")
                lastError = e
                continue
            }
        }
        throw LuvApiException(
            lastError?.message?.takeIf { it.isNotBlank() }
                ?: "Google-Anmeldung nicht möglich.",
            error = "google_failed"
        )
    }

    private fun parseIdToken(response: GetCredentialResponse): Result {
        val credential = response.credential
        if (
            credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            val token = google.idToken
            if (token.isBlank()) throw LuvApiException("Kein Google-Token erhalten.")
            return Result(
                idToken = token,
                displayName = google.displayName
            )
        }
        throw LuvApiException("Unerwartete Google-Antwort.")
    }

    suspend fun signOut(context: Context) {
        runCatching {
            CredentialManager.create(context.applicationContext)
                .clearCredentialState(ClearCredentialStateRequest())
        }
    }
}
