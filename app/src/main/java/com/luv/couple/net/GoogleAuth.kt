package com.luv.couple.net

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
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

    fun signInIntent(activity: Activity, webClientId: String): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(activity, gso).signInIntent
    }

    /**
     * Vor dem Intent-Picker abmelden, damit die Kontoliste zuverlässig erscheint.
     */
    suspend fun prepareSignInIntent(activity: Activity, webClientId: String): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(activity, gso)
        runCatching { client.signOut().await() }
        return client.signInIntent
    }

    fun parseSignInIntentResult(data: Intent?): Result {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            val account = task.getResult(ApiException::class.java)
            val token = account?.idToken?.takeIf { it.isNotBlank() }
                ?: throw LuvApiException(
                    "Kein Google-Token erhalten. Bitte in der Google Cloud Console " +
                        "prüfen, ob die Web-Client-ID und der Android-SHA-1 stimmen.",
                    error = "missing_id_token"
                )
            Result(idToken = token, displayName = account.displayName)
        } catch (e: ApiException) {
            Log.w(TAG, "GoogleSignIn ApiException status=${e.statusCode} ${e.message}")
            when (e.statusCode) {
                // CommonStatusCodes.CANCELED / SIGN_IN_CANCELLED
                12501, 16 -> throw LuvApiException("Abgebrochen.", error = "cancelled")
                else -> throw LuvApiException(
                    "Google-Anmeldung fehlgeschlagen (Code ${e.statusCode}).",
                    error = "google_failed"
                )
            }
        }
    }

    /**
     * Credential Manager (schnell) — bei „No credentials“ soll der Aufrufer
     * auf [prepareSignInIntent] zurückfallen.
     */
    suspend fun signInWithCredentialManager(context: Context): Result {
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

        // 1) Bekannte Konten  2) Alle Konten  3) Sign-in-with-Google-Button
        val attempts = listOf(
            GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(true)
                        .setServerClientId(webClientId)
                        .setAutoSelectEnabled(false)
                        .build()
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
                    GetSignInWithGoogleOption.Builder(webClientId).build()
                )
                .build()
        )

        var sawNoCredential = false
        for ((index, request) in attempts.withIndex()) {
            try {
                val response = manager.getCredential(activity, request)
                val credential = response.credential
                if (
                    credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val google = GoogleIdTokenCredential.createFrom(credential.data)
                    val token = google.idToken
                    if (token.isBlank()) throw LuvApiException("Kein Google-Token erhalten.")
                    return Result(idToken = token, displayName = google.displayName)
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "cm attempt $index cancelled: ${e.message}")
                throw LuvApiException("Abgebrochen.", error = "cancelled")
            } catch (e: NoCredentialException) {
                Log.w(TAG, "cm attempt $index no credential: ${e.message}")
                sawNoCredential = true
                continue
            } catch (e: GetCredentialException) {
                Log.w(TAG, "cm attempt $index failed: ${e.type} ${e.message}")
                continue
            }
        }
        throw LuvApiException(
            if (sawNoCredential) {
                "Kein Google-Konto bereit — wechsle zum klassischen Login…"
            } else {
                "Google-Anmeldung nicht möglich."
            },
            error = "no_credentials"
        )
    }

    suspend fun signOut(context: Context) {
        runCatching {
            CredentialManager.create(context.applicationContext)
                .clearCredentialState(ClearCredentialStateRequest())
        }
        runCatching {
            val activity = context.findActivity() ?: return
            val webClientId = fetchWebClientId() ?: return
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            GoogleSignIn.getClient(activity, gso).signOut().await()
        }
    }
}
