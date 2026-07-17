package com.luv.couple.net

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
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
     */
    suspend fun signIn(activityContext: Context): Result {
        val webClientId = fetchWebClientId()
            ?: throw LuvApiException(
                "Google-Login ist noch nicht eingerichtet. Bitte später erneut versuchen.",
                error = "google_disabled"
            )
        val manager = CredentialManager.create(activityContext)

        // Zuerst bekannte Konten, dann voller Google-Button
        val attempts = listOf(
            GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(true)
                        .setServerClientId(webClientId)
                        .setAutoSelectEnabled(true)
                        .build()
                )
                .build(),
            GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(webClientId)
                        .build()
                )
                .build(),
            GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetSignInWithGoogleOption.Builder(webClientId)
                        .build()
                )
                .build()
        )

        var lastError: Exception? = null
        for (request in attempts) {
            try {
                val response = manager.getCredential(activityContext, request)
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
            } catch (e: GetCredentialCancellationException) {
                throw LuvApiException("Abgebrochen.", error = "cancelled")
            } catch (e: NoCredentialException) {
                lastError = e
                continue
            } catch (e: GetCredentialException) {
                lastError = e
                Log.w(TAG, "credential attempt failed: ${e.message}")
                continue
            }
        }
        throw LuvApiException(
            lastError?.message?.takeIf { it.isNotBlank() }
                ?: "Google-Anmeldung nicht möglich.",
            error = "google_failed"
        )
    }

    suspend fun signOut(context: Context) {
        runCatching {
            CredentialManager.create(context)
                .clearCredentialState(ClearCredentialStateRequest())
        }
    }
}
