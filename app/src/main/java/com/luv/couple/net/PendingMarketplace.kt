package com.luv.couple.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Öffnet den Marktplatz-Tab (z. B. aus Verkaufs-Benachrichtigung). */
object PendingMarketplace {
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()

    private val _openMine = MutableStateFlow(false)
    val openMine: StateFlow<Boolean> = _openMine.asStateFlow()

    fun offer(showMine: Boolean = false) {
        if (showMine) _openMine.value = true
        _open.value = true
    }

    fun consume(): Boolean {
        if (!_open.value) return false
        _open.value = false
        return true
    }

    /** Nach Tab-Öffnung: direkt „Meine Angebote“ zeigen. */
    fun consumeOpenMine(): Boolean {
        if (!_openMine.value) return false
        _openMine.value = false
        return true
    }
}
