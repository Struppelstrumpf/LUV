package com.luv.couple.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Punkt an Sozial / Erfolge, wenn Coins abholbereit sind. */
object AchievementsBadge {
    private val _hasClaimable = MutableStateFlow(false)
    val hasClaimable: StateFlow<Boolean> = _hasClaimable.asStateFlow()

    /** Letzter erfolgreicher Fetch — Panel zeigt sofort, Refresh im Hintergrund. */
    private val _latest = MutableStateFlow<LuvApiClient.AchievementsState?>(null)
    val latest: StateFlow<LuvApiClient.AchievementsState?> = _latest.asStateFlow()

    fun update(hasClaimable: Boolean) {
        _hasClaimable.value = hasClaimable
    }

    fun updateFrom(state: LuvApiClient.AchievementsState?) {
        if (state != null) _latest.value = state
        val claimable = state?.hasClaimable == true
        _hasClaimable.value = claimable
        val fp = if (state != null) NotificationBadges.claimableFingerprint(state) else ""
        NotificationBadges.onAchievementsClaimable(claimable, fp)
    }

    suspend fun refresh() {
        NotificationBadges.ensureAchievementsFpLoaded()
        runCatching { LuvApiClient.fetchAchievements() }
            .onSuccess { updateFrom(it) }
    }
}
