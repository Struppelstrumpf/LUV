package com.luv.couple.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Team-Verwarnungen (Admin): einmalig als Popup + dauerhaft unter Sozial · Freunde.
 */
object StaffWarningBus {
    private val _pending = MutableStateFlow<LuvApiClient.StaffWarning?>(null)
    val pending: StateFlow<LuvApiClient.StaffWarning?> = _pending.asStateFlow()

    private val _inbox = MutableStateFlow<List<LuvApiClient.StaffWarning>>(emptyList())
    val inbox: StateFlow<List<LuvApiClient.StaffWarning>> = _inbox.asStateFlow()

    fun offer(pending: LuvApiClient.StaffWarning?, warnings: List<LuvApiClient.StaffWarning>) {
        _inbox.value = warnings
        if (pending != null && pending.id.isNotBlank() && pending.message.isNotBlank()) {
            if (_pending.value?.id != pending.id) {
                _pending.value = pending
            }
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
