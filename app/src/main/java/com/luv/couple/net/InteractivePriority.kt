package com.luv.couple.net

import java.util.concurrent.atomic.AtomicInteger

/**
 * User-Taps haben Vorrang vor Hintergrund-Polls.
 * Solange eine interaktive Aktion läuft, sollen Live-Notice / Friends-Badge /
 * Maintenance / Market-Warm usw. warten — nicht den JSON-Pool vollstopfen.
 */
object InteractivePriority {
    private val inFlight = AtomicInteger(0)

    /** true = Hintergrund darf netzwerken. */
    fun allowBackground(): Boolean = inFlight.get() <= 0

    fun begin() {
        inFlight.incrementAndGet()
    }

    fun end() {
        inFlight.updateAndGet { (it - 1).coerceAtLeast(0) }
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
