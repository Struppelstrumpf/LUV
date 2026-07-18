package com.luv.couple.net

import com.luv.couple.data.PeerInfo
import com.luv.couple.data.PeerPalette
import com.luv.couple.data.RosterMember
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

object PairSessionState {
    private val peersByLobby = ConcurrentHashMap<String, MutableStateFlow<Map<String, PeerInfo>>>()
    private val peerCounts = ConcurrentHashMap<String, MutableStateFlow<Int>>()
    private val capacities = ConcurrentHashMap<String, MutableStateFlow<Int>>()

    private val _notes = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notes: SharedFlow<String> = _notes.asSharedFlow()

    private val _reactions = MutableSharedFlow<ReactionEvent>(extraBufferCapacity = 16)
    val reactions: SharedFlow<ReactionEvent> = _reactions.asSharedFlow()

    private val _missedYou = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
    val missedYou: SharedFlow<Unit> = _missedYou.asSharedFlow()

    private val goneSinceByLobby = ConcurrentHashMap<String, Long>()
    private const val MISS_THRESHOLD_MS = 2 * 60_000L

    fun peers(lobbyId: String): StateFlow<Map<String, PeerInfo>> =
        peersByLobby.getOrPut(lobbyId) { MutableStateFlow(emptyMap()) }.asStateFlow()

    fun peerCount(lobbyId: String): StateFlow<Int> =
        peerCounts.getOrPut(lobbyId) { MutableStateFlow(0) }.asStateFlow()

    fun capacity(lobbyId: String): StateFlow<Int> =
        capacities.getOrPut(lobbyId) { MutableStateFlow(0) }.asStateFlow()

    fun anyonePresent(lobbyId: String): Boolean =
        peersByLobby[lobbyId]?.value?.values?.any { it.active } == true

    fun onPresence(
        lobbyId: String,
        peerKey: String,
        nickname: String,
        colorIndex: Int,
        active: Boolean,
        userId: String? = null
    ) {
        val flow = peersByLobby.getOrPut(lobbyId) { MutableStateFlow(emptyMap()) }
        val nick = nickname.trim().ifBlank { "Jemand" }
        val uid = userId?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        val wasAnyone = flow.value.values.any { it.active }
        flow.update { current ->
            upsertPeer(
                current = current,
                userId = uid,
                peerKey = peerKey,
                nickname = nick,
                colorIndex = colorIndex.coerceIn(0, PeerPalette.COLOR_COUNT - 1),
                active = active
            )
        }
        val nowAnyone = flow.value.values.any { it.active }
        if (!active && wasAnyone && !nowAnyone) {
            goneSinceByLobby[lobbyId] = System.currentTimeMillis()
        }
        if (active && !wasAnyone) {
            val since = goneSinceByLobby[lobbyId] ?: 0L
            if (since > 0L && System.currentTimeMillis() - since >= MISS_THRESHOLD_MS) {
                _missedYou.tryEmit(Unit)
            }
            goneSinceByLobby.remove(lobbyId)
        }
    }

    fun onPeers(lobbyId: String, count: Int, capacity: Int? = null) {
        peerCounts.getOrPut(lobbyId) { MutableStateFlow(0) }.value = count.coerceAtLeast(0)
        if (capacity != null && capacity > 0) {
            capacities.getOrPut(lobbyId) { MutableStateFlow(capacity) }.value = capacity
        }
    }

    /** Verbundene Lobby-Mitglieder — Quelle der Wahrheit für Kachel + Avatare. */
    fun onRoster(
        lobbyId: String,
        members: List<RosterMember>,
        count: Int,
        capacity: Int? = null
    ) {
        val safeCount = maxOf(count, members.size).coerceAtLeast(0)
        onPeers(lobbyId, safeCount, capacity)
        val flow = peersByLobby.getOrPut(lobbyId) { MutableStateFlow(emptyMap()) }
        if (members.isEmpty()) {
            // Leere Members nur leeren, wenn wirklich niemand verbunden ist
            if (safeCount <= 0) {
                if (flow.value.isNotEmpty()) {
                    goneSinceByLobby[lobbyId] = System.currentTimeMillis()
                }
                flow.value = emptyMap()
            }
            return
        }
        val previous = flow.value
        val next = linkedMapOf<String, PeerInfo>()
        members.forEach { m ->
            val nick = m.nickname.trim().ifBlank { "Jemand" }
            val uid = m.userId?.trim()?.takeIf { it.isNotBlank() && it != "null" }
            val key = uid ?: nick.lowercase()
            val prev = previous[key]
                ?: previous.values.firstOrNull { p ->
                    (uid != null && p.userId == uid) ||
                        p.nickname.equals(nick, ignoreCase = true)
                }
            val color = when {
                m.colorIndex in 0 until PeerPalette.COLOR_COUNT -> m.colorIndex
                prev != null -> prev.colorIndex
                else -> PeerPalette.indexFor(nick)
            }
            next[key] = PeerInfo(
                peerKey = key,
                nickname = nick,
                colorIndex = color,
                active = m.active,
                userId = uid ?: prev?.userId,
                online = m.online,
                departed = false,
                petEmoji = m.petEmoji.ifBlank { prev?.petEmoji ?: "🐣" }
            )
        }
        flow.value = next
    }

    fun removePeer(lobbyId: String, userId: String?, nickname: String?) {
        val flow = peersByLobby[lobbyId] ?: return
        val uid = userId?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        val nick = nickname?.trim().orEmpty()
        flow.update { current ->
            current.filterValues { peer ->
                when {
                    uid != null && peer.userId == uid -> false
                    nick.isNotBlank() && peer.nickname.equals(nick, ignoreCase = true) &&
                        (uid == null || peer.userId.isNullOrBlank()) -> false
                    else -> true
                }
            }
        }
    }

    fun rememberPeer(lobbyId: String, nickname: String, peerKey: String? = null, userId: String? = null) {
        val nick = nickname.trim().ifBlank { return }
        val prev = peersByLobby[lobbyId]?.value?.values
            ?.firstOrNull {
                (userId != null && it.userId == userId) ||
                    it.nickname.equals(nick, ignoreCase = true)
            }
        onPresence(
            lobbyId = lobbyId,
            peerKey = peerKey?.takeIf { it.isNotBlank() }
                ?: userId?.takeIf { it.isNotBlank() }
                ?: nick.lowercase(),
            nickname = nick,
            colorIndex = prev?.colorIndex ?: PeerPalette.indexFor(nick),
            active = prev?.active == true,
            userId = userId ?: prev?.userId
        )
    }

    fun setCapacity(lobbyId: String, capacity: Int) {
        if (capacity > 0) {
            capacities.getOrPut(lobbyId) { MutableStateFlow(capacity) }.value = capacity
        }
    }

    fun legendPeers(
        lobbyId: String,
        myNickname: String?,
        myColor: Int,
        myUserId: String? = null,
        myPetEmoji: String = "🐣"
    ): List<PeerInfo> {
        val remote = peersByLobby[lobbyId]?.value?.values?.toList().orEmpty()
        val me = PeerInfo(
            peerKey = "me",
            nickname = myNickname?.takeIf { it.isNotBlank() } ?: "Du",
            colorIndex = myColor,
            active = true,
            userId = myUserId,
            online = true,
            departed = false,
            petEmoji = myPetEmoji.ifBlank { "🐣" }
        )
        // Für die Legende: nur noch aktive Lobby-Mitglieder (nicht departed)
        val others = remote.filter { peer ->
            !peer.departed && !isSelf(peer, myNickname, myUserId)
        }.distinctBy { it.nickname.trim().lowercase() }
        return listOf(me) + others
    }

    /**
     * Belegte Sitze im Menü — alle Lobby-Mitglieder, auch kurz offline
     * (Update, App zu). Freier Slot nur nach echtem Leave ([removePeer] / departed).
     */
    fun seatNicknames(
        lobbyId: String,
        myNickname: String?,
        myUserId: String?,
        hostNickname: String?
    ): List<String> {
        val others = peersByLobby[lobbyId]?.value?.values.orEmpty()
            .filter { !it.departed && !isSelf(it, myNickname, myUserId) }
            .map { it.nickname.trim() }
            .filter { it.isNotBlank() && !it.equals("Du", ignoreCase = true) }
            .distinctBy { it.lowercase() }
        return buildList {
            add("Du")
            val host = hostNickname?.trim().orEmpty()
            if (host.isNotBlank() && others.any { it.equals(host, ignoreCase = true) }) {
                add(host)
                others.filter { !it.equals(host, ignoreCase = true) }.forEach { add(it) }
            } else {
                addAll(others)
            }
        }
    }

    fun emitNote(text: String) {
        if (text.isNotBlank()) _notes.tryEmit(text.trim())
    }

    fun emitReaction(lobbyId: String, emoji: String, nickname: String?) {
        if (emoji.isBlank()) return
        _reactions.tryEmit(ReactionEvent(lobbyId, emoji, nickname))
    }

    fun takenColorIndices(lobbyId: String, myNickname: String?, myUserId: String? = null): Set<Int> {
        return peersByLobby[lobbyId]?.value?.values.orEmpty()
            .filter { !isSelf(it, myNickname, myUserId) }
            .map { it.colorIndex }
            .toSet()
    }

    fun updatePeerColor(lobbyId: String, nickname: String?, colorIndex: Int) {
        if (nickname.isNullOrBlank()) return
        val flow = peersByLobby[lobbyId] ?: return
        val safe = colorIndex.coerceIn(0, PeerPalette.COLOR_COUNT - 1)
        flow.update { map ->
            map.mapValues { (_, peer) ->
                if (peer.nickname.equals(nickname, ignoreCase = true)) {
                    peer.copy(colorIndex = safe)
                } else {
                    peer
                }
            }
        }
    }

    fun resetLobby(lobbyId: String) {
        peersByLobby[lobbyId]?.value = emptyMap()
        peerCounts[lobbyId]?.value = 0
        capacities[lobbyId]?.value = 0
        goneSinceByLobby.remove(lobbyId)
    }

    fun reset() {
        peersByLobby.clear()
        peerCounts.clear()
        capacities.clear()
        goneSinceByLobby.clear()
    }

    private fun isSelf(peer: PeerInfo, myNickname: String?, myUserId: String?): Boolean {
        // Nur per User-ID — Nickname allein filtert Partner mit gleichem/Default-Namen weg
        if (!myUserId.isNullOrBlank() && !peer.userId.isNullOrBlank()) {
            return peer.userId == myUserId
        }
        if (!myUserId.isNullOrBlank() && peer.userId.isNullOrBlank()) {
            // Fremder ohne ID: nicht als ich behandeln
            return false
        }
        if (!myNickname.isNullOrBlank() && peer.userId.isNullOrBlank()) {
            return peer.nickname.equals(myNickname, ignoreCase = true)
        }
        return false
    }

    /** Presence/Join ohne doppelte Einträge (gleiche Person unter peerId + Nick). */
    private fun upsertPeer(
        current: Map<String, PeerInfo>,
        userId: String?,
        peerKey: String,
        nickname: String,
        colorIndex: Int,
        active: Boolean
    ): Map<String, PeerInfo> {
        val key = userId
            ?: peerKey.trim().takeIf { it.isNotBlank() }?.let { pk ->
                // Alte peerKeys waren oft der Nickname — normalisieren
                if (pk.equals(nickname, ignoreCase = true)) nickname.lowercase() else pk
            }
            ?: nickname.lowercase()
        val next = current.toMutableMap()
        val stale = next.filter { (k, p) ->
            k != key && (
                (userId != null && p.userId == userId) ||
                    p.nickname.equals(nickname, ignoreCase = true) ||
                    p.peerKey.equals(peerKey, ignoreCase = true)
                )
        }.keys
        stale.forEach { next.remove(it) }
        val prev = next[key]
        next[key] = PeerInfo(
            peerKey = key,
            nickname = nickname,
            colorIndex = colorIndex,
            active = active,
            userId = userId ?: prev?.userId,
            online = prev?.online != false,
            departed = false,
            // Presence hat kein Pet — Roster-Wert behalten (sonst fällt Avatar auf 🐣 zurück)
            petEmoji = prev?.petEmoji?.takeIf { it.isNotBlank() } ?: "🐣"
        )
        return next
    }
}

data class ReactionEvent(
    val lobbyId: String,
    val emoji: String,
    val nickname: String?
)
