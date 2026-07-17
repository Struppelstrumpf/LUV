package com.luv.couple.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

/**
 * Plant 2–3 sanfte Stimmungs-Hinweise pro Tag (Vormittag, Nachmittag, Abend).
 */
object MoodNudgeScheduler {
    private const val PREFS = "luv_mood"
    private const val KEY_PLAN_DAY = "plan_day"
    private const val KEY_TIMES = "plan_times_ms"
    private const val KEY_SENT = "sent_mask"
    private const val KEY_LAST_LINE = "last_line"
    private const val REQUEST_BASE = 4100

    fun ensureScheduled(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val planned = prefs.getString(KEY_PLAN_DAY, null)
        when {
            planned == null -> planDay(app, today)
            planned < today -> planDay(app, today)
            planned == today -> rebindFutureAlarms(app)
            else -> rebindFutureAlarms(app) // Plan für morgen liegt schon
        }
    }

    fun onNudgeFired(context: Context, slot: Int) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val planned = prefs.getString(KEY_PLAN_DAY, null)
        if (planned != today) {
            // Alarm von gestern / Rollover — heutigen Tag neu planen
            planDay(app, today)
            return
        }
        val mask = prefs.getInt(KEY_SENT, 0)
        if (mask and (1 shl slot) != 0) {
            maybePlanTomorrow(app)
            return
        }
        prefs.edit().putInt(KEY_SENT, mask or (1 shl slot)).apply()
        LuvAlertNotifier.showMoodNudge(app, nextLine(prefs))
        maybePlanTomorrow(app)
    }

    private fun maybePlanTomorrow(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val times = parseTimes(prefs.getString(KEY_TIMES, "") ?: "")
        val mask = prefs.getInt(KEY_SENT, 0)
        val now = System.currentTimeMillis()
        val pending = times.indices.any { i ->
            times[i] > now && mask and (1 shl i) == 0
        }
        if (!pending) {
            planDay(context, LocalDate.now().plusDays(1).toString())
        } else {
            rebindFutureAlarms(context)
        }
    }

    private fun planDay(context: Context, day: String) {
        val zone = ZoneId.systemDefault()
        val date = LocalDate.parse(day)
        val now = System.currentTimeMillis()
        val rng = Random(day.hashCode() xor 0x4C5556)

        val slotCount = if (rng.nextInt(5) == 0) 2 else 3
        val windows = listOf(
            LocalTime.of(10, 0) to 75,
            LocalTime.of(15, 0) to 90,
            LocalTime.of(19, 30) to 75
        ).shuffled(rng).take(slotCount).sortedBy { it.first }

        val times = LongArray(3) { 0L }
        windows.forEachIndexed { index, (base, windowMin) ->
            val offsetMin = rng.nextInt(windowMin.coerceAtLeast(1) + 1)
            val at = LocalDateTime.of(date, base)
                .plusMinutes(offsetMin.toLong())
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
            // Nur Zukunft — für „heute“ schon vergangene Fenster überspringen
            times[index] = if (at > now + 90_000L) at else 0L
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PLAN_DAY, day)
            .putString(KEY_TIMES, times.joinToString(","))
            .putInt(KEY_SENT, 0)
            .apply()

        cancelAll(context)
        var scheduled = 0
        times.forEachIndexed { slot, trigger ->
            if (trigger > 0L) {
                scheduleAlarm(context, slot, trigger)
                scheduled++
            }
        }
        if (scheduled == 0 && day == LocalDate.now().toString()) {
            planDay(context, LocalDate.now().plusDays(1).toString())
        }
    }

    private fun rebindFutureAlarms(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val times = parseTimes(prefs.getString(KEY_TIMES, "") ?: "")
        val mask = prefs.getInt(KEY_SENT, 0)
        val now = System.currentTimeMillis()
        times.forEachIndexed { slot, trigger ->
            if (trigger > now && mask and (1 shl slot) == 0) {
                scheduleAlarm(context, slot, trigger)
            }
        }
    }

    private fun scheduleAlarm(context: Context, slot: Int, triggerAt: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setWindow(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            20 * 60 * 1000L,
            pending(context, slot)
        )
    }

    private fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (slot in 0..2) {
            am.cancel(pending(context, slot))
        }
    }

    private fun pending(context: Context, slot: Int): PendingIntent {
        val intent = Intent(context, MoodNudgeReceiver::class.java).apply {
            action = MoodNudgeReceiver.ACTION
            putExtra(MoodNudgeReceiver.EXTRA_SLOT, slot)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + slot,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun parseTimes(raw: String): LongArray {
        val parts = raw.split(',').mapNotNull { it.toLongOrNull() }
        return LongArray(3) { i -> parts.getOrElse(i) { 0L } }
    }

    private fun nextLine(prefs: android.content.SharedPreferences): String {
        val last = prefs.getInt(KEY_LAST_LINE, -1)
        val (idx, text) = MoodLines.pick(excluding = last)
        prefs.edit().putInt(KEY_LAST_LINE, idx).apply()
        return text
    }
}
