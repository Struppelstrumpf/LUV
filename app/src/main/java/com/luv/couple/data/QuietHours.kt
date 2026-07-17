package com.luv.couple.data

import java.util.Calendar

/**
 * Ruhezeiten: pro Wochentag (1=Mo … 7=So) beliebig viele Fenster.
 * Währenddessen keine Haptik, keine Nutzer-Notifications, keine Nähe-Impulse.
 */
data class QuietWindow(
    val startMinutes: Int,
    val endMinutes: Int
) {
    init {
        require(startMinutes in 0 until MINUTES_PER_DAY)
        require(endMinutes in 0 until MINUTES_PER_DAY)
    }

    fun contains(minuteOfDay: Int): Boolean {
        if (startMinutes == endMinutes) return false
        return if (startMinutes < endMinutes) {
            minuteOfDay in startMinutes until endMinutes
        } else {
            // Über Mitternacht, z. B. 22:00–07:00
            minuteOfDay >= startMinutes || minuteOfDay < endMinutes
        }
    }

    fun label(): String = "${fmt(startMinutes)}–${fmt(endMinutes)}"

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        fun fmt(minutes: Int): String {
            val h = (minutes / 60).coerceIn(0, 23)
            val m = (minutes % 60).coerceIn(0, 59)
            return "%02d:%02d".format(h, m)
        }
    }
}

data class QuietHoursSchedule(
    /** Tag 1=Montag … 7=Sonntag → Fenster */
    val byDay: Map<Int, List<QuietWindow>> = emptyMap()
) {
    fun windowsFor(day: Int): List<QuietWindow> =
        byDay[day.coerceIn(1, 7)].orEmpty()

    fun withDay(day: Int, windows: List<QuietWindow>): QuietHoursSchedule {
        val next = byDay.toMutableMap()
        if (windows.isEmpty()) next.remove(day) else next[day] = windows
        return copy(byDay = next)
    }

    fun isActiveAt(nowMs: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val javaDay = cal.get(Calendar.DAY_OF_WEEK) // So=1 … Sa=7
        val day = when (javaDay) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7
        }
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return windowsFor(day).any { it.contains(minuteOfDay) }
    }

    companion object {
        val EMPTY = QuietHoursSchedule()
        val DAY_LABELS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    }
}

/** In-Memory-Cache für schnelle Checks in Notifier/Haptik. */
object QuietHoursGate {
    @Volatile
    private var schedule: QuietHoursSchedule = QuietHoursSchedule.EMPTY

    fun update(schedule: QuietHoursSchedule) {
        this.schedule = schedule
    }

    fun isQuietNow(): Boolean = schedule.isActiveAt()
}
