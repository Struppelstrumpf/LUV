package com.luv.couple.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.luv.couple.LuvApp
import com.luv.couple.MainActivity
import com.luv.couple.R
import com.luv.couple.lock.LockDrawActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object PartnerStrokeNotifier {
    const val CHANNEL_ID = "luv_partner_draw"
    private const val MIN_INTERVAL_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastNotifyByLobby = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.partner_draw_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.partner_draw_channel_desc)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun onPartnerStroke(
        context: Context,
        lobbyName: String,
        nickname: String,
        lobbyId: String
    ) {
        scope.launch {
            val enabled = runCatching {
                LuvApp.instance.prefs.isPartnerDrawNotifyEnabled()
            }.getOrDefault(true)
            if (!enabled) return@launch

            val now = SystemClock.elapsedRealtime()
            val last = lastNotifyByLobby[lobbyId] ?: 0L
            if (last > 0L && now - last < MIN_INTERVAL_MS) return@launch
            lastNotifyByLobby[lobbyId] = now
            show(context.applicationContext, lobbyName, nickname, lobbyId)
        }
    }

    private fun show(context: Context, lobbyName: String, nickname: String, lobbyId: String) {
        ensureChannel(context)
        val openCanvas = PendingIntent.getActivity(
            context,
            lobbyId.hashCode(),
            Intent(context, LockDrawActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(LockDrawActivity.EXTRA_LOBBY_ID, lobbyId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openApp = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = context.getString(R.string.partner_draw_text_fmt, lobbyName, nickname)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openCanvas)
            .setDeleteIntent(openApp)
            .setAutoCancel(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setDefaults(0)
            .setSound(null)
            .setVibrate(null)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_BASE + (lobbyId.hashCode() and 0xffff), notification)
    }

    private const val NOTIFICATION_BASE = 770
}
