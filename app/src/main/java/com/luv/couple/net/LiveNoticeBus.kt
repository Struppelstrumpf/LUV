package com.luv.couple.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveNotice(
    val id: String,
    val message: String,
    val authorNickname: String,
    val createdAt: Long
)

/** Kurzlebige Team-Hinweise für alle Clients (Popup ~5s). */
object LiveNoticeBus {
    private val _pending = MutableStateFlow<LiveNotice?>(null)
    val pending: StateFlow<LiveNotice?> = _pending.asStateFlow()

    private var lastShownId: String? = null

    fun offer(notice: LiveNotice?) {
        if (notice == null) return
        if (notice.id.isBlank() || notice.message.isBlank()) return
        if (notice.id == lastShownId) return
        _pending.value = notice
    }

    fun consumeShown(id: String) {
        lastShownId = id
        if (_pending.value?.id == id) _pending.value = null
    }

    fun clear() {
        _pending.value = null
    }
}
