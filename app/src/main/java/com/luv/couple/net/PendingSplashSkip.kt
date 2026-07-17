package com.luv.couple.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Splash überspringen (z. B. Notification-Tap). */
object PendingSplashSkip {
    private val _skip = MutableStateFlow(false)
    val skip: StateFlow<Boolean> = _skip.asStateFlow()

    fun offer() {
        _skip.value = true
    }

    fun consume(): Boolean {
        val v = _skip.value
        _skip.value = false
        return v
    }

    fun peek(): Boolean = _skip.value
}
