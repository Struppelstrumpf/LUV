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
 * App-weite Hinweis-Punkte: Freundesanfragen, Heirat, Erfolge, Markt, Inventar-Neuheiten.
 */
object NotificationBadges {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _friendOnly = MutableStateFlow(0)
    private val _marriageIncoming = MutableStateFlow(0)
    private val _lobbyInviteIncoming = MutableStateFlow(0)
    private val _socialIncomingTotal = MutableStateFlow(0)
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
    private var ceremonyLobbyBaselineReady = false
    @Volatile private var lastCeremonyLobbyCode: String? = null

    private fun socialIncomingTotal(): Int =
        _friendOnly.value + _marriageIncoming.value + _lobbyInviteIncoming.value

    /** Summe offener Freundes-/Heirats-/Lobby-Anfragen (für Badges). */
    val friendIncoming: StateFlow<Int> = _socialIncomingTotal.asStateFlow()
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
        val socialIn = socialIncomingTotal()
        _socialIncomingTotal.value = socialIn
        val hasSozialNews = socialIn > 0 || hasAchievementsNews()
        _sozialDot.value = hasSozialNews && !sozialSeen
        _marketDot.value = _pendingSales.value > 0
        _inventoryDot.value = _inventoryUnseen.value > 0
        _friendsTabDot.value = socialIn > 0 && !friendsTabSeen
        _achievementsTabDot.value = hasAchievementsNews()
        val sozialCount = if (!sozialSeen) {
            socialIn + if (hasAchievementsNews()) 1 else 0
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

    /**
     * Offene Sozial-Anfragen getrennt tracken — sonst wird ein Heiratsantrag
     * als „Neue Freundschaftsanfrage“ gepusht.
     */
    fun setSocialIncoming(
        friendRequests: Int,
        marriageProposals: Int = 0,
        lobbyInvites: Int = 0,
        weddingCeremonyInvites: Boolean = false,
    ) {
        val friends = friendRequests.coerceAtLeast(0)
        val marriages = marriageProposals.coerceAtLeast(0)
        val invites = lobbyInvites.coerceAtLeast(0)
        val ctx = LuvApp.instance
        val hadFriends = _friendOnly.value
        val hadMarriages = _marriageIncoming.value
        val hadInvites = _lobbyInviteIncoming.value

        if (friendsBaselineReady) {
            var anyNew = false
            if (friends > hadFriends) {
                anyNew = true
                runCatching { LuvAlertNotifier.onFriendRequest(ctx, friends) }
            }
            if (marriages > hadMarriages) {
                anyNew = true
                runCatching { LuvAlertNotifier.onMarriageProposal(ctx, marriages) }
            }
            if (invites > hadInvites) {
                anyNew = true
                runCatching {
                    LuvAlertNotifier.onLobbyInvite(
                        ctx,
                        invites,
                        weddingCeremony = weddingCeremonyInvites
                    )
                }
            }
            if (anyNew) {
                sozialSeen = false
                friendsTabSeen = false
            }
        } else if (friends + marriages + invites > 0) {
            sozialSeen = false
            friendsTabSeen = false
        }

        _friendOnly.value = friends
        _marriageIncoming.value = marriages
        _lobbyInviteIncoming.value = invites
        friendsBaselineReady = true
        recompute()
    }

    /** @deprecated Nutze [setSocialIncoming]. */
    fun setFriendIncoming(count: Int) {
        setSocialIncoming(friendRequests = count.coerceAtLeast(0))
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
                LuvAlertNotifier.onAchievementsReady(LuvApp.instance)
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

    /**
     * Neue Hochzeit-Lobby (ceremony_scheduled): Android-Hinweis + Sozial-Dot.
     * Erster Sync nur Baseline — kein Spam beim App-Start.
     */
    fun onCeremonyLobbyScheduled(lobbyCode: String?, ceremonyAtMs: Long) {
        val code = lobbyCode?.trim()?.uppercase()?.removePrefix("LUV-")
            ?.takeIf { it.isNotBlank() }
        if (code == null) {
            lastCeremonyLobbyCode = null
            ceremonyLobbyBaselineReady = true
            return
        }
        val isNew = ceremonyLobbyBaselineReady && code != lastCeremonyLobbyCode
        lastCeremonyLobbyCode = code
        ceremonyLobbyBaselineReady = true
        if (!isNew) return
        sozialSeen = false
        friendsTabSeen = false
        runCatching {
            LuvAlertNotifier.onCeremonyLobbyReady(LuvApp.instance, ceremonyAtMs)
        }
        recompute()
    }

    fun setInventoryUnseen(count: Int) {
        val next = count.coerceAtLeast(0)
        if (inventoryBaselineReady && next > _inventoryUnseen.value) {
            runCatching {
                LuvAlertNotifier.onInventoryNew(LuvApp.instance, next)
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

    /** Kurzer Klartext fürs App-Icon / stille Badge-Benachrichtigung (nur was die Zahl zählt). */
    fun badgeSummary(): String {
        val parts = ArrayList<String>(4)
        if (!sozialSeen) {
            val friends = _friendOnly.value
            if (friends > 0) {
                parts += if (friends == 1) "Freundschaftsanfrage" else "$friends Freundschaftsanfragen"
            }
            val marriages = _marriageIncoming.value
            if (marriages > 0) {
                parts += if (marriages == 1) "Heiratsantrag" else "$marriages Heiratsanträge"
            }
            val invites = _lobbyInviteIncoming.value
            if (invites > 0) {
                parts += if (invites == 1) "Lobby-Einladung" else "$invites Lobby-Einladungen"
            }
            if (hasAchievementsNews()) {
                parts += "Erfolg abholen"
            }
        }
        val sales = _pendingSales.value
        if (sales > 0) {
            parts += if (sales == 1) "Marktplatz-Verkauf" else "$sales Marktplatz-Verkäufe"
        }
        if (_inventoryUnseen.value > 0) {
            parts += "Neues Item"
        }
        return parts.joinToString(" · ").ifBlank { "Etwas Neues in LUV" }
    }

    fun syncAppBadge(context: Context) {
        LuvAlertNotifier.updateAppBadge(context, _totalCount.value, badgeSummary())
    }

    suspend fun refreshAll(context: Context? = null) {
        ensureAchievementsFpLoaded()
        runCatching {
            val friends = LuvApiClient.fetchFriends(force = true)
            setSocialIncoming(
                friendRequests = friends.incoming.size,
                marriageProposals = friends.marriageProposals.size,
                lobbyInvites = friends.lobbyInvites.size,
                weddingCeremonyInvites = friends.lobbyInvites.any { it.isWeddingCeremony },
            )
            val m = friends.myMarriage
            if (m?.status == "ceremony_scheduled") {
                onCeremonyLobbyScheduled(m.ceremonyLobbyCode, m.ceremonyAt)
            } else {
                onCeremonyLobbyScheduled(null, 0L)
            }
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

    /**
     * Freunde / Heirat / Lobby-Einladungen.
     * [force]=false nutzt den 45s-Client-Cache — gut für Hintergrund-Poll.
     */
    suspend fun refreshFriends(context: Context? = null, force: Boolean = true) {
        runCatching {
            val friends = LuvApiClient.fetchFriends(force = force)
            setSocialIncoming(
                friendRequests = friends.incoming.size,
                marriageProposals = friends.marriageProposals.size,
                lobbyInvites = friends.lobbyInvites.size,
                weddingCeremonyInvites = friends.lobbyInvites.any { it.isWeddingCeremony },
            )
            val m = friends.myMarriage
            if (m?.status == "ceremony_scheduled") {
                onCeremonyLobbyScheduled(m.ceremonyLobbyCode, m.ceremonyAt)
            } else {
                onCeremonyLobbyScheduled(null, 0L)
            }
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
