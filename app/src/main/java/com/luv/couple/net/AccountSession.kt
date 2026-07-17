package com.luv.couple.net

import com.luv.couple.data.AccountInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

object AccountSession {
    private val _account = MutableStateFlow<AccountInfo?>(null)
    val account: StateFlow<AccountInfo?> = _account.asStateFlow()

    private val _economyBlocks = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val economyBlocks: SharedFlow<String> = _economyBlocks.asSharedFlow()

    private val _clearVotes = MutableSharedFlow<ClearVoteEvent>(extraBufferCapacity = 8)
    val clearVotes: SharedFlow<ClearVoteEvent> = _clearVotes.asSharedFlow()

    private val _publicVotes = MutableSharedFlow<PublicVoteEvent>(extraBufferCapacity = 8)
    val publicVotes: SharedFlow<PublicVoteEvent> = _publicVotes.asSharedFlow()

    private val _pendingClearVote = MutableStateFlow<ClearVoteEvent.Open?>(null)
    val pendingClearVote: StateFlow<ClearVoteEvent.Open?> = _pendingClearVote.asStateFlow()

    private val _pendingPublicVote = MutableStateFlow<PublicVoteEvent.Open?>(null)
    val pendingPublicVote: StateFlow<PublicVoteEvent.Open?> = _pendingPublicVote.asStateFlow()

    private val votedClearIds = ConcurrentHashMap.newKeySet<String>()
    private val votedPublicIds = ConcurrentHashMap.newKeySet<String>()

    fun setAccount(info: AccountInfo?) {
        _account.value = info
    }

    fun emitEconomyBlock(message: String) {
        _economyBlocks.tryEmit(message)
    }

    fun emitClearVote(event: ClearVoteEvent) {
        when (event) {
            is ClearVoteEvent.Open -> {
                _pendingClearVote.value = event
                if (event.alreadyVoted || event.isInitiator) {
                    votedClearIds.add(event.proposalId)
                }
            }
            is ClearVoteEvent.Update -> {
                val cur = _pendingClearVote.value
                if (cur != null && cur.proposalId == event.proposalId) {
                    _pendingClearVote.value = cur.copy(yes = event.yes, total = event.total)
                }
            }
            is ClearVoteEvent.Result -> {
                if (_pendingClearVote.value?.proposalId == event.proposalId) {
                    _pendingClearVote.value = null
                }
                votedClearIds.remove(event.proposalId)
            }
        }
        _clearVotes.tryEmit(event)
    }

    fun emitPublicVote(event: PublicVoteEvent) {
        when (event) {
            is PublicVoteEvent.Open -> {
                _pendingPublicVote.value = event
                if (event.alreadyVoted || event.isInitiator) {
                    votedPublicIds.add(event.proposalId)
                }
            }
            is PublicVoteEvent.Update -> {
                val cur = _pendingPublicVote.value
                if (cur != null && cur.proposalId == event.proposalId) {
                    _pendingPublicVote.value = cur.copy(
                        yes = event.yes,
                        total = event.total,
                        rewardCoins = event.rewardCoins
                    )
                }
            }
            is PublicVoteEvent.Result -> {
                if (_pendingPublicVote.value?.proposalId == event.proposalId) {
                    _pendingPublicVote.value = null
                }
                votedPublicIds.remove(event.proposalId)
            }
            is PublicVoteEvent.CaptureRequest -> Unit
        }
        _publicVotes.tryEmit(event)
    }

    fun markClearVoted(proposalId: String) {
        votedClearIds.add(proposalId)
    }

    fun hasVotedClear(proposalId: String): Boolean = proposalId in votedClearIds

    fun markPublicVoted(proposalId: String) {
        votedPublicIds.add(proposalId)
    }

    fun hasVotedPublic(proposalId: String): Boolean = proposalId in votedPublicIds
}

sealed class ClearVoteEvent {
    data class Open(
        val lobbyId: String,
        val proposalId: String,
        val by: String,
        val byPeerId: String? = null,
        val endsAt: Long,
        val yes: Int,
        val total: Int,
        val alreadyVoted: Boolean = false,
        val isInitiator: Boolean = false
    ) : ClearVoteEvent()

    data class Update(
        val lobbyId: String,
        val proposalId: String,
        val yes: Int,
        val no: Int,
        val total: Int
    ) : ClearVoteEvent()

    data class Result(
        val lobbyId: String,
        val proposalId: String,
        val approved: Boolean,
        val yes: Int,
        val total: Int
    ) : ClearVoteEvent()
}

sealed class PublicVoteEvent {
    data class Open(
        val lobbyId: String,
        val proposalId: String,
        val by: String,
        val byPeerId: String? = null,
        val endsAt: Long,
        val yes: Int,
        val total: Int,
        val rewardCoins: Int,
        val alreadyVoted: Boolean = false,
        val isInitiator: Boolean = false
    ) : PublicVoteEvent()

    data class Update(
        val lobbyId: String,
        val proposalId: String,
        val yes: Int,
        val no: Int,
        val total: Int,
        val rewardCoins: Int
    ) : PublicVoteEvent()

    data class Result(
        val lobbyId: String,
        val proposalId: String,
        val approved: Boolean,
        val yes: Int,
        val total: Int,
        val rewardCoins: Int,
        val reason: String = ""
    ) : PublicVoteEvent()

    data class CaptureRequest(
        val lobbyId: String,
        val proposalId: String
    ) : PublicVoteEvent()
}
