package com.luv.couple.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Nach Trial-Exit (Google-Gate / Zurück): Lobby-Code merken,
 * damit nach Anmeldung erneut beigetreten wird.
 */
object PendingInviteRejoin {
    private val _code = MutableStateFlow<String?>(null)
    val code: StateFlow<String?> = _code.asStateFlow()

    fun offer(raw: String?) {
        val normalized = raw?.let { LuvApiClient.normalizeCode(it) }
        if (!normalized.isNullOrBlank()) _code.value = normalized
    }

    fun consume(): String? {
        val value = _code.value
        _code.value = null
        return value
    }

    fun peek(): String? = _code.value
}
