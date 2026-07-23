package com.luv.couple.net

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicInteger

/**
 * User-Taps haben Vorrang vor Hintergrund-Polls.
 * Solange eine interaktive Aktion läuft, sollen Live-Notice / Friends-Badge /
 * Maintenance / Market-Warm usw. warten — nicht den JSON-Pool vollstopfen.
 */
object InteractivePriority {
    private val inFlight = AtomicInteger(0)
    private val main = Handler(Looper.getMainLooper())

    /** true = Hintergrund darf netzwerken. */
    fun allowBackground(): Boolean = inFlight.get() <= 0

    fun begin() {
        inFlight.incrementAndGet()
    }

    fun end() {
        inFlight.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    /** Nach Mutation kurz halten — Follow-up-GETs gewinnen gegen Polls. */
    fun endDeferred(delayMs: Long = 450L) {
        main.postDelayed({ end() }, delayMs.coerceAtLeast(0L))
    }

    suspend fun <T> run(block: suspend () -> T): T {
        begin()
        return try {
            block()
        } finally {
            end()
        }
    }
}
