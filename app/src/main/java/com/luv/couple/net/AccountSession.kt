package com.luv.couple.net

import com.luv.couple.data.AccountInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object AccountSession {
    private val _account = MutableStateFlow<AccountInfo?>(null)
    val account: StateFlow<AccountInfo?> = _account.asStateFlow()

    private val _economyBlocks = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val economyBlocks: SharedFlow<String> = _economyBlocks.asSharedFlow()

    private val _clearVotes = MutableSharedFlow<ClearVoteEvent>(extraBufferCapacity = 8)
    val clearVotes: SharedFlow<ClearVoteEvent> = _clearVotes.asSharedFlow()

    fun setAccount(info: AccountInfo?) {
        _account.value = info
    }

    fun emitEconomyBlock(message: String) {
        _economyBlocks.tryEmit(message)
    }

    fun emitClearVote(event: ClearVoteEvent) {
        _clearVotes.tryEmit(event)
    }
}

sealed class ClearVoteEvent {
    data class Open(
        val lobbyId: String,
        val proposalId: String,
        val by: String,
        val endsAt: Long,
        val yes: Int,
        val total: Int
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
