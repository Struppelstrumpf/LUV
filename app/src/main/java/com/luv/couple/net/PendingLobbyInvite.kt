package com.luv.couple.net

/**
 * Offene Freund-Lobby-Einladung (ID), die erst nach Bestätigung im Popup
 * per [LuvApiClient.acceptLobbyInvite] angenommen wird — nötig für Hochzeit
 * (sonst wäre man schon Mitglied, wenn man „Ablehnen“ tippt).
 */
object PendingLobbyInvite {
    @Volatile
    private var inviteId: String? = null

    fun offer(id: String?, roomCode: String?) {
        val cleanId = id?.trim()?.takeIf { it.isNotBlank() }
        inviteId = cleanId
        if (!roomCode.isNullOrBlank()) {
            PendingJoin.offer(roomCode)
        }
    }

    fun peekId(): String? = inviteId

    fun consumeId(): String? {
        val v = inviteId
        inviteId = null
        return v
    }

    fun clear() {
        inviteId = null
    }
}
