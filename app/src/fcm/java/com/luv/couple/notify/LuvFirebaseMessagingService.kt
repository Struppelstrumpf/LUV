package com.luv.couple.notify

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.luv.couple.net.AccountEventRouter
import com.luv.couple.net.LuvApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LuvFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        if (token.isBlank() || LuvApiClient.sessionToken.isNullOrBlank()) return
        scope.launch {
            runCatching { LuvApiClient.registerDeviceToken(token) }
                .onFailure { Log.w(TAG, "token upload: ${it.message}") }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data.isNotEmpty()) {
            AccountEventRouter.onFcmData(applicationContext, data)
            return
        }
        // notification-only payload: system tray already shows it when app is backgrounded
        val n = message.notification ?: return
        Log.d(TAG, "fcm notify ${n.title}: ${n.body}")
    }

    companion object {
        private const val TAG = "LuvFcmSvc"
    }
}
