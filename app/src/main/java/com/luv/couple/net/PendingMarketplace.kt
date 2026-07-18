package com.luv.couple.net

import java.util.concurrent.atomic.AtomicBoolean

object PendingMarketplace {
    private val pending = AtomicBoolean(false)

    fun offer() {
        pending.set(true)
    }

    fun consume(): Boolean = pending.getAndSet(false)
}
