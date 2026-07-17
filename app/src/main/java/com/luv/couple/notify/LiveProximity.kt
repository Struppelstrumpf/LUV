package com.luv.couple.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.luv.couple.lock.LockScreenWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Nähe-Signale ohne Always-On-Streaming:
 * - „malt gerade“-Fenster für Widget / Avatare
 * - häufigerer Widget-Refresh nur während Aktivität
 */
object LiveProximity {
    const val ACTIVE_WINDOW_MS = 90_000L
    const val PAINTING_PULSE_MS = 8_000L
    private const val WIDGET_PULSE_MS = 7_000L
    private const val ACTION_PULSE = "com.luv.couple.ACTION_LIVE_PROXIMITY_PULSE"

    private val lastRemotePaintAt = ConcurrentHashMap<String, Long>()
    private val lastPainter = ConcurrentHashMap<String, String>()
    private val pulseUntil = ConcurrentHashMap<String, Long>()
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun onRemoteStroke(lobbyId: String, nickname: String?) {
        val id = lobbyId.trim()
        if (id.isBlank()) return
        val now = System.currentTimeMillis()
        lastRemotePaintAt[id] = now
        nickname?.trim()?.takeIf { it.isNotBlank() }?.let { lastPainter[id] = it }
        pulseUntil[id] = now + ACTIVE_WINDOW_MS
        _revision.value = now
    }

    fun isLobbyHot(lobbyId: String?): Boolean {
        val id = lobbyId ?: return false
        val until = pulseUntil[id] ?: return false
        return System.currentTimeMillis() < until
    }

    fun painterName(lobbyId: String?): String? {
        val id = lobbyId ?: return null
        if (!isLobbyHot(id)) return null
        return lastPainter[id]
    }

    fun lastPaintAt(lobbyId: String?): Long {
        val id = lobbyId ?: return 0L
        return lastRemotePaintAt[id] ?: 0L
    }

    fun isPeerPainting(lobbyId: String?, nickname: String?): Boolean {
        if (nickname.isNullOrBlank() || lobbyId.isNullOrBlank()) return false
        if (!isLobbyHot(lobbyId)) return false
        val who = lastPainter[lobbyId] ?: return false
        return who.equals(nickname, ignoreCase = true) &&
            System.currentTimeMillis() - (lastRemotePaintAt[lobbyId] ?: 0L) < PAINTING_PULSE_MS
    }

    fun scheduleWidgetPulse(context: Context, lobbyId: String?) {
        if (lobbyId.isNullOrBlank()) return
        if (!isLobbyHot(lobbyId)) return
        val app = context.applicationContext
        LockScreenWidgetProvider.requestUpdate(app)
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(app, LiveProximityPulseReceiver::class.java).setAction(ACTION_PULSE)
        val pi = PendingIntent.getBroadcast(
            app,
            lobbyId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val trigger = SystemClock.elapsedRealtime() + WIDGET_PULSE_MS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
        } else {
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
        }
    }

    fun onPulseAlarm(context: Context) {
        val app = context.applicationContext
        LockScreenWidgetProvider.requestUpdate(app)
        // Weiter pulsen, solange irgendeine Lobby noch heiß ist
        val hot = pulseUntil.entries.any { System.currentTimeMillis() < it.value }
        if (hot) {
            val anyId = pulseUntil.entries.firstOrNull { System.currentTimeMillis() < it.value }?.key
            scheduleWidgetPulse(app, anyId)
        }
    }
}

class LiveProximityPulseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        LiveProximity.onPulseAlarm(context)
    }
}
