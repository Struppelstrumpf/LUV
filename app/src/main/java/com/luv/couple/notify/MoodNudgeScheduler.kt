package com.luv.couple.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.luv.couple.net.LuvApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Sanfte Stimmungs-Hinweise — maximal eine alle 12 Stunden.
 */
object MoodNudgeScheduler {
    private const val PREFS = "luv_mood"
    private const val KEY_LAST_SHOWN_MS = "last_shown_ms"
    private const val KEY_NEXT_AT_MS = "next_at_ms"
    private const val KEY_LAST_LINE = "last_line"
    private const val KEY_LAST_PHRASE_ID = "last_phrase_id"
    private const val REQUEST_CODE = 4100
    private const val INTERVAL_MS = 12 * 60 * 60 * 1000L
    private const val MIN_DELAY_MS = 15 * 60 * 1000L

    fun ensureScheduled(context: Context) {
        val app = context.applicationContext
        cancelLegacySlotAlarms(app)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastShown = prefs.getLong(KEY_LAST_SHOWN_MS, 0L)
        val nextAt = prefs.getLong(KEY_NEXT_AT_MS, 0L)
        val earliest = if (lastShown > 0L) lastShown + INTERVAL_MS else now + MIN_DELAY_MS
        val trigger = when {
            nextAt > now && nextAt >= earliest -> nextAt
            else -> earliest.coerceAtLeast(now + MIN_DELAY_MS)
        }
        prefs.edit().putLong(KEY_NEXT_AT_MS, trigger).apply()
        scheduleAlarm(app, trigger)
    }

    /** Alte 2–3×/Tag-Alarme (Request-Codes 4101/4102) entfernen. */
    private fun cancelLegacySlotAlarms(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (slot in 1..2) {
            val intent = Intent(context, MoodNudgeReceiver::class.java).apply {
                action = MoodNudgeReceiver.ACTION
                putExtra(MoodNudgeReceiver.EXTRA_SLOT, slot)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE + slot,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
        }
    }

    fun onNudgeFired(context: Context, slot: Int) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastShown = prefs.getLong(KEY_LAST_SHOWN_MS, 0L)
        if (lastShown > 0L && now - lastShown < INTERVAL_MS - 60_000L) {
            // Zu früh (Doppel-Alarm / Rebind) — nur neu planen
            scheduleNext(app, lastShown + INTERVAL_MS)
            return
        }
        prefs.edit()
            .putLong(KEY_LAST_SHOWN_MS, now)
            .putLong(KEY_NEXT_AT_MS, now + INTERVAL_MS)
            .apply()
        val picked = nextPhrase(prefs)
        LuvAlertNotifier.showMoodNudge(
            app,
            text = picked.text,
            subtitle = picked.subtitle,
            deepTarget = picked.target
        )
        scheduleAlarm(app, now + INTERVAL_MS)
    }

    private fun scheduleNext(context: Context, triggerAt: Long) {
        val now = System.currentTimeMillis()
        val at = triggerAt.coerceAtLeast(now + MIN_DELAY_MS)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_NEXT_AT_MS, at)
            .apply()
        scheduleAlarm(context, at)
    }

    private fun scheduleAlarm(context: Context, triggerAt: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context))
        am.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pending(context)
        )
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, MoodNudgeReceiver::class.java).apply {
            action = MoodNudgeReceiver.ACTION
            putExtra(MoodNudgeReceiver.EXTRA_SLOT, 0)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private data class Picked(
        val text: String,
        val subtitle: String?,
        val target: String?
    )

    private fun nextPhrase(prefs: android.content.SharedPreferences): Picked {
        val excluding = prefs.getString(KEY_LAST_PHRASE_ID, null)
        val remote = runCatching {
            if (LuvApiClient.sessionToken.isNullOrBlank()) null
            else {
                runBlocking {
                    withContext(Dispatchers.IO) {
                        LuvApiClient.fetchNotifyPhrase(pool = "mood", excludingId = excluding)
                    }
                }
            }
        }.getOrNull()
        if (remote != null && remote.text.isNotBlank()) {
            prefs.edit().putString(KEY_LAST_PHRASE_ID, remote.id).apply()
            return Picked(remote.text, remote.subtitle, remote.target)
        }
        val last = prefs.getInt(KEY_LAST_LINE, -1)
        val (idx, text) = MoodLines.pick(excluding = last)
        prefs.edit().putInt(KEY_LAST_LINE, idx).apply()
        return Picked(text, null, "home")
    }
}
