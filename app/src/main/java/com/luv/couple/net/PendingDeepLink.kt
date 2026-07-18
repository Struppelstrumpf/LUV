package com.luv.couple.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Wohin eine System-Benachrichtigung die App führen soll. */
enum class DeepLinkTarget {
    Home,
    SozialFriends,
    SozialAchievements,
    Inventar,
    Marketplace,
    Shop
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
