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

/** One-shot: Gästebuch/Hochzeitsbild-Popup für User öffnen (nach LUV-Hochzeitshinweis). */
object PendingWeddingGuestbook {
    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId.asStateFlow()

    fun offer(userId: String) {
        val id = userId.trim()
        if (id.isNotBlank()) _userId.value = id
    }

    fun peek(): String? = _userId.value

    fun consume(): String? {
        val id = _userId.value
        _userId.value = null
        return id
    }

    fun consumeIf(userId: String): Boolean {
        val want = userId.trim()
        if (want.isBlank() || _userId.value != want) return false
        _userId.value = null
        return true
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
