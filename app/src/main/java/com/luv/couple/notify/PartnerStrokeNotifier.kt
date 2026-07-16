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
    private const val NOTIFICATION_ID = 77
    private const val MIN_INTERVAL_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var lastNotifyUptime = 0L

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

    fun onPartnerStroke(context: Context) {
        scope.launch {
            val enabled = runCatching {
                LuvApp.instance.prefs.isPartnerDrawNotifyEnabled()
            }.getOrDefault(true)
            if (!enabled) return@launch

            val now = SystemClock.elapsedRealtime()
            if (lastNotifyUptime > 0L && now - lastNotifyUptime < MIN_INTERVAL_MS) {
                return@launch
            }
            lastNotifyUptime = now
            show(context.applicationContext)
        }
    }

    private fun show(context: Context) {
        ensureChannel(context)
        val openCanvas = PendingIntent.getActivity(
            context,
            1,
            Intent(context, LockDrawActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openApp = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.partner_draw_title))
            .setContentText(context.getString(R.string.partner_draw_text))
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
            .notify(NOTIFICATION_ID, notification)
    }
}
