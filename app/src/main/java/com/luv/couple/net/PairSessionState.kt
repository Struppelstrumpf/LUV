package com.luv.couple.net

import com.luv.couple.data.PeerInfo
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

    fun onPresence(lobbyId: String, peerKey: String, nickname: String, colorIndex: Int, active: Boolean) {
        val flow = peersByLobby.getOrPut(lobbyId) { MutableStateFlow(emptyMap()) }
        val key = peerKey.ifBlank { nickname.ifBlank { "peer" } }
        val wasAnyone = flow.value.values.any { it.active }
        flow.update { current ->
            current + (key to PeerInfo(key, nickname.ifBlank { "Jemand" }, colorIndex, active))
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
        peerCounts.getOrPut(lobbyId) { MutableStateFlow(0) }.value = count
        if (capacity != null && capacity > 0) {
            capacities.getOrPut(lobbyId) { MutableStateFlow(capacity) }.value = capacity
        }
        if (count < 2) {
            val flow = peersByLobby[lobbyId] ?: return
            if (flow.value.values.any { it.active }) {
                goneSinceByLobby[lobbyId] = System.currentTimeMillis()
            }
            flow.update { map -> map.mapValues { it.value.copy(active = false) } }
        }
    }

    fun setCapacity(lobbyId: String, capacity: Int) {
        if (capacity > 0) {
            capacities.getOrPut(lobbyId) { MutableStateFlow(capacity) }.value = capacity
        }
    }

    fun legendPeers(lobbyId: String, myNickname: String?, myColor: Int): List<PeerInfo> {
        val remote = peersByLobby[lobbyId]?.value?.values?.toList().orEmpty()
        val me = PeerInfo(
            peerKey = "me",
            nickname = myNickname?.takeIf { it.isNotBlank() } ?: "Du",
            colorIndex = myColor,
            active = true
        )
        val others = remote.filter {
            !it.nickname.equals(myNickname, ignoreCase = true)
        }
        return listOf(me) + others.distinctBy { it.nickname.lowercase() }
    }

    fun emitNote(text: String) {
        if (text.isNotBlank()) _notes.tryEmit(text.trim())
    }

    fun emitReaction(lobbyId: String, emoji: String, nickname: String?) {
        if (emoji.isBlank()) return
        _reactions.tryEmit(ReactionEvent(lobbyId, emoji, nickname))
    }

    fun takenColorIndices(lobbyId: String, myNickname: String?): Set<Int> {
        val fromPeers = peersByLobby[lobbyId]?.value?.values.orEmpty()
            .filter { !it.nickname.equals(myNickname, ignoreCase = true) }
            .map { it.colorIndex }
        return fromPeers.toSet()
    }

    fun updatePeerColor(lobbyId: String, nickname: String?, colorIndex: Int) {
        if (nickname.isNullOrBlank()) return
        val flow = peersByLobby[lobbyId] ?: return
        flow.update { map ->
            map.mapValues { (_, peer) ->
                if (peer.nickname.equals(nickname, ignoreCase = true)) {
                    peer.copy(colorIndex = colorIndex)
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
}

data class ReactionEvent(
    val lobbyId: String,
    val emoji: String,
    val nickname: String?
)
