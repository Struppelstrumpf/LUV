package com.luv.couple.net

import android.content.Context
import com.luv.couple.LuvApp
import com.luv.couple.notify.LuvAlertNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-weite Hinweis-Punkte: Freundesanfragen, Erfolge, Markt, Inventar-Neuheiten.
 */
object NotificationBadges {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
    /** Fingerprint der zuletzt im Erfolge-Tab gesehenen Claimables (persistiert). */
    @Volatile private var achievementsSeenFp = ""
    @Volatile private var achievementsCurrentFp = ""
    private var achievementsFpLoaded = false
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

    private fun hasAchievementsNews(): Boolean =
        _achievementsClaimable.value &&
            achievementsCurrentFp.isNotBlank() &&
            achievementsCurrentFp != achievementsSeenFp

    private fun recompute() {
        val hasSozialNews = _friendIncoming.value > 0 || hasAchievementsNews()
        _sozialDot.value = hasSozialNews && !sozialSeen
        _marketDot.value = _pendingSales.value > 0
        _inventoryDot.value = _inventoryUnseen.value > 0
        _friendsTabDot.value = _friendIncoming.value > 0 && !friendsTabSeen
        _achievementsTabDot.value = hasAchievementsNews()
        val sozialCount = if (!sozialSeen) {
            _friendIncoming.value + if (hasAchievementsNews()) 1 else 0
        } else {
            0
        }
        _totalCount.value = sozialCount + _pendingSales.value +
            if (_inventoryUnseen.value > 0) 1 else 0
    }

    fun ensureAchievementsFpLoaded() {
        if (achievementsFpLoaded) return
        achievementsFpLoaded = true
        scope.launch {
            achievementsSeenFp = runCatching {
                LuvApp.instance.prefs.achievementsSeenFingerprint()
            }.getOrDefault("")
            recompute()
        }
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
        achievementsSeenFp = achievementsCurrentFp
        recompute()
        scope.launch {
            runCatching {
                LuvApp.instance.prefs.setAchievementsSeenFingerprint(achievementsSeenFp)
            }
        }
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

    /**
     * @param fingerprint stabile ID-Liste der abholbaren Erfolge (leer = nichts abholbar)
     */
    fun onAchievementsClaimable(claimable: Boolean, fingerprint: String = "") {
        ensureAchievementsFpLoaded()
        val fp = if (claimable) fingerprint.trim() else ""
        val newly = achievementsBaselineReady &&
            claimable &&
            fp.isNotBlank() &&
            fp != achievementsSeenFp &&
            fp != achievementsCurrentFp
        achievementsCurrentFp = fp
        if (newly) {
            sozialSeen = false
            runCatching {
                com.luv.couple.notify.LuvAlertNotifier.onAchievementsReady(LuvApp.instance)
            }
        }
        _achievementsClaimable.value = claimable
        achievementsBaselineReady = true
        recompute()
    }

    fun setAchievementsClaimable(claimable: Boolean, fingerprint: String = "") {
        onAchievementsClaimable(claimable, fingerprint)
        AchievementsBadge.update(claimable)
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
        ensureAchievementsFpLoaded()
        runCatching {
            val friends = LuvApiClient.fetchFriends()
            setFriendIncoming(friends.incoming.size + friends.marriageProposals.size)
        }
        runCatching {
            val ach = LuvApiClient.fetchAchievements()
            setAchievementsClaimable(ach.hasClaimable, claimableFingerprint(ach))
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

    fun claimableFingerprint(state: LuvApiClient.AchievementsState): String {
        val ids = state.achievements
            .filter { it.claimable }
            .map { it.id }
            .sorted()
        val daily = if (state.daily.claimable) "daily:${state.daily.date}" else null
        return (listOfNotNull(daily) + ids).joinToString("|")
    }
}
