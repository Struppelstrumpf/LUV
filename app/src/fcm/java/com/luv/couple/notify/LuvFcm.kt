package com.luv.couple.notify

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.luv.couple.net.LuvApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object LuvFcm {
    private const val TAG = "LuvFcm"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun registerIfAvailable(context: Context) {
        if (LuvApiClient.sessionToken.isNullOrBlank()) return
        scope.launch {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                if (token.isNullOrBlank()) return@runCatching
                LuvApiClient.registerDeviceToken(token)
                Log.i(TAG, "device token registered")
            }.onFailure {
                Log.w(TAG, "register failed: ${it.message}")
            }
        }
    }

    fun clearLocalToken(context: Context) {
        scope.launch {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                if (!token.isNullOrBlank()) {
                    LuvApiClient.unregisterDeviceToken(token)
                }
                FirebaseMessaging.getInstance().deleteToken().await()
            }
        }
    }
}
