package com.luv.couple.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Item aus dem Menü-Inventar → Profil gestalten und dort platzieren. */
sealed class ProfilePlaceAction {
    data class Theme(val themeId: String) : ProfilePlaceAction()
    data class Sticker(val emoji: String) : ProfilePlaceAction()
    data class Buddy(val emoji: String) : ProfilePlaceAction()
    data object Glass : ProfilePlaceAction()
    data object Bio : ProfilePlaceAction()
}

object PendingProfilePlace {
    private val _pending = MutableStateFlow<ProfilePlaceAction?>(null)
    val pending: StateFlow<ProfilePlaceAction?> = _pending.asStateFlow()

    fun offer(action: ProfilePlaceAction) {
        _pending.value = action
    }

    fun peek(): ProfilePlaceAction? = _pending.value

    fun consume(): ProfilePlaceAction? {
        val v = _pending.value ?: return null
        _pending.value = null
        return v
    }
}
