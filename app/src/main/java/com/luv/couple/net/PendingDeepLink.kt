package com.luv.couple.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Wohin eine System-Benachrichtigung die App führen soll. */
enum class DeepLinkTarget {
    Home,
    SozialFriends,
    SozialAchievements,
    SozialWedding,
    Inventar,
    Marketplace,
    Shop,
    CoinShop,
    Profile,
    LastCanvas
}

/** One-shot: Hochzeits-Presence-/Gathering-Popup in Sozial öffnen. */
object PendingWeddingCeremony {
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()

    fun offer() {
        _open.value = true
    }

    fun consume(): Boolean {
        val v = _open.value
        _open.value = false
        return v
    }
}

object PendingDeepLink {
    private val _target = MutableStateFlow<DeepLinkTarget?>(null)
    val target: StateFlow<DeepLinkTarget?> = _target.asStateFlow()

    fun offer(target: DeepLinkTarget) {
        _target.value = target
    }

    fun peek(): DeepLinkTarget? = _target.value

    fun consume(): DeepLinkTarget? {
        val t = _target.value ?: return null
        _target.value = null
        return t
    }
}
