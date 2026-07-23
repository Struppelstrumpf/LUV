package com.luv.couple.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Team-Hinweise (Admin): Popup einmalig.
 * Verwarnungen zusätzlich unter Sozial · Freunde; Geschenke nur Popup.
 */
object StaffWarningBus {
    private val _pending = MutableStateFlow<LuvApiClient.StaffWarning?>(null)
    val pending: StateFlow<LuvApiClient.StaffWarning?> = _pending.asStateFlow()

    private val _inbox = MutableStateFlow<List<LuvApiClient.StaffWarning>>(emptyList())
    val inbox: StateFlow<List<LuvApiClient.StaffWarning>> = _inbox.asStateFlow()

    fun offer(pending: LuvApiClient.StaffWarning?, warnings: List<LuvApiClient.StaffWarning>) {
        _inbox.value = warnings.filter { it.severity != "gift" }
        if (pending != null && pending.id.isNotBlank() && pending.message.isNotBlank()) {
            if (_pending.value?.id != pending.id) {
                _pending.value = pending
            }
        }
    }

    /** Sofort aus Account-Push (ohne HTTP-Roundtrip). */
    fun offerFromPush(notice: LuvApiClient.StaffWarning) {
        if (notice.id.isBlank() || notice.message.isBlank()) return
        if (notice.severity != "gift") {
            val rest = _inbox.value.filter { it.id != notice.id }
            _inbox.value = listOf(notice.copy(seen = false)) + rest
        }
        if (_pending.value?.id != notice.id) {
            _pending.value = notice
        }
    }

    fun consume(id: String) {
        if (_pending.value?.id == id) _pending.value = null
    }

    fun clear() {
        _pending.value = null
        _inbox.value = emptyList()
    }
}
