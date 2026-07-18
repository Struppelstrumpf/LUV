package com.luv.couple.net

import com.luv.couple.LuvApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LiveNotice(
    val id: String,
    val message: String,
    val authorNickname: String,
    val createdAt: Long
)

/**
 * Team-/Admin-Hinweise: einmal pro Gerät anzeigen (auch wenn die App später geöffnet wird).
 * Absender = Spitzname der Person, die gesendet hat.
 */
object LiveNoticeBus {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _pending = MutableStateFlow<LiveNotice?>(null)
    val pending: StateFlow<LiveNotice?> = _pending.asStateFlow()

    @Volatile
    private var lastShownId: String? = null

    init {
        scope.launch {
            lastShownId = runCatching {
                LuvApp.instance.prefs.lastShownLiveNoticeId()
            }.getOrNull()
        }
    }

    fun offer(notice: LiveNotice?) {
        if (notice == null) return
        if (notice.id.isBlank() || notice.message.isBlank()) return
        if (notice.id == lastShownId) return
        if (_pending.value?.id == notice.id) return
        _pending.value = notice
    }

    fun consumeShown(id: String) {
        lastShownId = id
        if (_pending.value?.id == id) _pending.value = null
        scope.launch {
            runCatching { LuvApp.instance.prefs.setLastShownLiveNoticeId(id) }
        }
    }

    fun clear() {
        _pending.value = null
    }
}
