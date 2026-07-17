package com.luv.couple.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Öffnet den Shop-Tab (z. B. aus Leinwand / Kein-Coins-Dialog). */
object PendingShop {
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()

    fun offer() {
        _open.value = true
    }

    fun consume(): Boolean {
        if (!_open.value) return false
        _open.value = false
        return true
    }
}
