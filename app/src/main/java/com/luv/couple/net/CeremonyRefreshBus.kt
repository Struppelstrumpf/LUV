package com.luv.couple.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/** Account-WS / Mutationen → Kapelle & Planungs-Dialoge einmal neu laden. */
object CeremonyRefreshBus {
    private val counter = AtomicInteger(0)
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun bump() {
        _revision.value = counter.incrementAndGet()
    }
}
