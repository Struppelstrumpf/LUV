package com.luv.couple.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Nach Trial + neuem Google-Konto: Tutorial/Name starten,
 * aber Session behalten (nicht ausloggen).
 */
object PendingTutorialKeepAuth {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun offer() {
        _pending.value = true
    }

    fun consume(): Boolean {
        val v = _pending.value
        _pending.value = false
        return v
    }

    fun peek(): Boolean = _pending.value
}
