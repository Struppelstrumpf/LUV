package com.luv.couple.notify

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Platzhalter ohne Firebase — wird nie von FCM gestartet (kein google-services.json).
 */
class LuvFirebaseMessagingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
