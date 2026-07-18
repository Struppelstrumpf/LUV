package com.luv.couple.net

import android.content.Context
import com.luv.couple.LuvApp
import com.luv.couple.notify.LuvAlertNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-weite Hinweis-Punkte: Freundesanfragen, Erfolge, Markt, Inventar-Neuheiten.
 */
object NotificationBadges {
    private val _friendIncoming = MutableStateFlow(0)
    private val _achievementsClaimable = MutableStateFlow(false)
    private val _pendingSales = MutableStateFlow(0)
    private val _inventoryUnseen = MutableStateFlow(0)
    private val _sozialDot = MutableStateFlow(false)
    private val _marketDot = MutableStateFlow(false)
    private val _inventoryDot = MutableStateFlow(false)
    private val _friendsTabDot = MutableStateFlow(false)
    private val _achievementsTabDot = MutableStateFlow(false)
    private val _totalCount = MutableStateFlow(0)
    /** false = neue Sozial-Aktivität seit letztem Besuch */
    private var sozialSeen = true
    /** Tab-Punkte innerhalb von Sozial (Session) */
    private var friendsTabSeen = true
    private var achievementsTabSeen = true
    /** Erster Sync setzt nur Baseline — keine Push beim App-Start. */
    private var friendsBaselineReady = false
    private var achievementsBaselineReady = false
    private var inventoryBaselineReady = false

    val friendIncoming: StateFlow<Int> = _friendIncoming.asStateFlow()
    val achievementsClaimable: StateFlow<Boolean> = _achievementsClaimable.asStateFlow()
    val pendingSales: StateFlow<Int> = _pendingSales.asStateFlow()
    val hasSozialDot: StateFlow<Boolean> = _sozialDot.asStateFlow()
    val hasMarketDot: StateFlow<Boolean> = _marketDot.asStateFlow()
    val hasInventoryDot: StateFlow<Boolean> = _inventoryDot.asStateFlow()
    val inventoryUnseenCount: StateFlow<Int> = _inventoryUnseen.asStateFlow()
    val hasFriendsTabDot: StateFlow<Boolean> = _friendsTabDot.asStateFlow()
    val hasAchievementsTabDot: StateFlow<Boolean> = _achievementsTabDot.asStateFlow()
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    /** Freunde-Tab-Punkt (Anfragen o. ä.) — weg nach Besuch des Tabs. */
    fun showFriendsTabDot(): Boolean = _friendsTabDot.value

    /** Erfolge-Tab-Punkt — weg nach Besuch des Tabs; Coins bleiben abholbar. */
    fun showAchievementsTabDot(): Boolean = _achievementsTabDot.value

    private fun recompute() {
        val hasSozialNews = _friendIncoming.value > 0 || _achievementsClaimable.value
        _sozialDot.value = hasSozialNews && !sozialSeen
        _marketDot.value = _pendingSales.value > 0
        _inventoryDot.value = _inventoryUnseen.value > 0
        _friendsTabDot.value = _friendIncoming.value > 0 && !friendsTabSeen
        _achievementsTabDot.value = _achievementsClaimable.value && !achievementsTabSeen
        val sozialCount = if (!sozialSeen) {
            _friendIncoming.value + if (_achievementsClaimable.value) 1 else 0
        } else {
            0
        }
        _totalCount.value = sozialCount + _pendingSales.value +
            if (_inventoryUnseen.value > 0) 1 else 0
    }

    fun markSozialSeen() {
        sozialSeen = true
        recompute()
    }

    fun markFriendsTabSeen() {
        friendsTabSeen = true
        recompute()
    }

    fun markAchievementsTabSeen() {
        achievementsTabSeen = true
        recompute()
    }

    fun setFriendIncoming(count: Int) {
        val next = count.coerceAtLeast(0)
        val grew = friendsBaselineReady && next > _friendIncoming.value
        if (grew) {
            sozialSeen = false
            friendsTabSeen = false
            runCatching {
                com.luv.couple.notify.LuvAlertNotifier.onFriendRequest(
                    LuvApp.instance,
                    next
                )
            }
        } else if (next > _friendIncoming.value) {
            sozialSeen = false
            friendsTabSeen = false
        }
        _friendIncoming.value = next
        friendsBaselineReady = true
        recompute()
    }

    fun onAchievementsClaimable(claimable: Boolean) {
        val newly = achievementsBaselineReady && claimable && !_achievementsClaimable.value
        if (claimable && !_achievementsClaimable.value) {
            sozialSeen = false
            achievementsTabSeen = false
            if (newly) {
                runCatching {
                    com.luv.couple.notify.LuvAlertNotifier.onAchievementsReady(LuvApp.instance)
                }
            }
        }
        _achievementsClaimable.value = claimable
        achievementsBaselineReady = true
        recompute()
    }

    fun setAchievementsClaimable(claimable: Boolean) {
        val newly = achievementsBaselineReady && claimable && !_achievementsClaimable.value
        if (claimable && !_achievementsClaimable.value) {
            sozialSeen = false
            achievementsTabSeen = false
            if (newly) {
                runCatching {
                    com.luv.couple.notify.LuvAlertNotifier.onAchievementsReady(LuvApp.instance)
                }
            }
        }
        _achievementsClaimable.value = claimable
        achievementsBaselineReady = true
        AchievementsBadge.update(claimable)
        recompute()
    }

    fun setPendingSales(count: Int) {
        _pendingSales.value = count.coerceAtLeast(0)
        recompute()
    }

    fun setInventoryUnseen(count: Int) {
        val next = count.coerceAtLeast(0)
        if (inventoryBaselineReady && next > _inventoryUnseen.value) {
            runCatching {
                com.luv.couple.notify.LuvAlertNotifier.onInventoryNew(LuvApp.instance, next)
            }
        }
        _inventoryUnseen.value = next
        inventoryBaselineReady = true
        recompute()
    }

    suspend fun syncInventoryUnseenFromPrefs() {
        val n = runCatching {
            LuvApp.instance.prefs.inventoryUnseenIds().size
        }.getOrDefault(0)
        setInventoryUnseen(n)
    }

    fun syncAppBadge(context: Context) {
        LuvAlertNotifier.updateAppBadge(context, _totalCount.value)
    }

    suspend fun refreshAll(context: Context? = null) {
        runCatching {
            val friends = LuvApiClient.fetchFriends()
            setFriendIncoming(friends.incoming.size + friends.marriageProposals.size)
        }
        runCatching {
            val ach = LuvApiClient.fetchAchievements()
            setAchievementsClaimable(ach.hasClaimable)
        }
        runCatching {
            val sales = LuvApiClient.fetchPendingMarketSales()
            setPendingSales(sales.count)
        }
        syncInventoryUnseenFromPrefs()
        context?.let { syncAppBadge(it) }
    }

    suspend fun refreshFriends(context: Context? = null) {
        runCatching {
            val friends = LuvApiClient.fetchFriends()
            setFriendIncoming(friends.incoming.size + friends.marriageProposals.size)
        }
        context?.let { syncAppBadge(it) }
    }

    suspend fun refreshPendingSales(context: Context? = null): PendingSalesResult? {
        val result = runCatching { LuvApiClient.fetchPendingMarketSales() }.getOrNull()
        if (result != null) setPendingSales(result.count)
        context?.let { syncAppBadge(it) }
        return result
    }
}
