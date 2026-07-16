package com.luv.couple.net

import com.luv.couple.data.Gender
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object PairSessionState {
    private val _partnerPresent = MutableStateFlow(false)
    val partnerPresent: StateFlow<Boolean> = _partnerPresent.asStateFlow()

    private val _partnerGender = MutableStateFlow<Gender?>(null)
    val partnerGender: StateFlow<Gender?> = _partnerGender.asStateFlow()

    private val _notes = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notes: SharedFlow<String> = _notes.asSharedFlow()

    private val _missedYou = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
    val missedYou: SharedFlow<Unit> = _missedYou.asSharedFlow()

    private var partnerGoneSince: Long = 0L
    private const val MISS_THRESHOLD_MS = 2 * 60_000L

    fun onPresence(active: Boolean, genderName: String?) {
        genderName?.let {
            runCatching { Gender.valueOf(it) }.getOrNull()?.let { g ->
                _partnerGender.value = g
            }
        }
        val wasPresent = _partnerPresent.value
        if (!active) {
            if (wasPresent) partnerGoneSince = System.currentTimeMillis()
            _partnerPresent.value = false
            return
        }
        if (!wasPresent && partnerGoneSince > 0L) {
            val away = System.currentTimeMillis() - partnerGoneSince
            if (away >= MISS_THRESHOLD_MS) {
                _missedYou.tryEmit(Unit)
            }
        }
        partnerGoneSince = 0L
        _partnerPresent.value = true
    }

    fun onPeers(count: Int) {
        if (count < 2) {
            if (_partnerPresent.value) partnerGoneSince = System.currentTimeMillis()
            _partnerPresent.value = false
        }
    }

    fun emitNote(text: String) {
        if (text.isNotBlank()) _notes.tryEmit(text.trim())
    }

    fun reset() {
        _partnerPresent.value = false
        _partnerGender.value = null
        partnerGoneSince = 0L
    }
}
