package com.luv.couple.net

import android.content.Context
import com.luv.couple.notify.LuvAlertNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-weite Hinweis-Punkte: Freundesanfragen, abholbare Erfolge, Marktplatz-Verkäufe.
 */
object NotificationBadges {
    private val _friendIncoming = MutableStateFlow(0)
    private val _achievementsClaimable = MutableStateFlow(false)
    private val _pendingSales = MutableStateFlow(0)
    private val _sozialDot = MutableStateFlow(false)
    private val _marketDot = MutableStateFlow(false)
    private val _totalCount = MutableStateFlow(0)

    val friendIncoming: StateFlow<Int> = _friendIncoming.asStateFlow()
    val achievementsClaimable: StateFlow<Boolean> = _achievementsClaimable.asStateFlow()
    val pendingSales: StateFlow<Int> = _pendingSales.asStateFlow()
    val hasSozialDot: StateFlow<Boolean> = _sozialDot.asStateFlow()
    val hasMarketDot: StateFlow<Boolean> = _marketDot.asStateFlow()
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private fun recompute() {
        _sozialDot.value = _friendIncoming.value > 0 || _achievementsClaimable.value
        _marketDot.value = _pendingSales.value > 0
        val ach = if (_achievementsClaimable.value) 1 else 0
        _totalCount.value = _friendIncoming.value + ach + _pendingSales.value
    }

    fun setFriendIncoming(count: Int) {
        _friendIncoming.value = count.coerceAtLeast(0)
        recompute()
    }

    fun onAchievementsClaimable(claimable: Boolean) {
        _achievementsClaimable.value = claimable
        recompute()
    }

    fun setAchievementsClaimable(claimable: Boolean) {
        _achievementsClaimable.value = claimable
        AchievementsBadge.update(claimable)
        recompute()
    }

    fun setPendingSales(count: Int) {
        _pendingSales.value = count.coerceAtLeast(0)
        recompute()
    }

    fun syncAppBadge(context: Context) {
        LuvAlertNotifier.updateAppBadge(context, _totalCount.value)
    }

    suspend fun refreshAll(context: Context? = null) {
        runCatching {
            val friends = LuvApiClient.fetchFriends()
            setFriendIncoming(friends.incoming.size)
        }
        runCatching {
            val ach = LuvApiClient.fetchAchievements()
            setAchievementsClaimable(ach.hasClaimable)
        }
        runCatching {
            val sales = LuvApiClient.fetchPendingMarketSales()
            setPendingSales(sales.count)
        }
        context?.let { syncAppBadge(it) }
    }

    suspend fun refreshFriends(context: Context? = null) {
        runCatching {
            val friends = LuvApiClient.fetchFriends()
            setFriendIncoming(friends.incoming.size)
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
