package com.luv.couple.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.data.PeerPalette
import com.luv.couple.net.AccountSession
import com.luv.couple.net.AchievementsBadge
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.SeasonEvent
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SocialScreen(
    initialTab: Int = 0,
    onOpenFriendProfile: (userId: String, nickname: String) -> Unit,
    onCreateEventLobby: (SeasonEvent) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    val friendsTabDot by com.luv.couple.net.NotificationBadges.hasFriendsTabDot
        .collectAsStateWithLifecycle()
    val achievementsTabDot by com.luv.couple.net.NotificationBadges.hasAchievementsTabDot
        .collectAsStateWithLifecycle()

    LaunchedEffect(initialTab) {
        tab = initialTab.coerceIn(0, 2)
    }

    LaunchedEffect(Unit) {
        runCatching { LuvApiClient.pingAchievement("social_opens") }
        com.luv.couple.net.NotificationBadges.markSozialSeen()
        AchievementsBadge.refresh()
        com.luv.couple.net.NotificationBadges.refreshFriends()
        com.luv.couple.net.NotificationBadges.markSozialSeen()
    }

    LaunchedEffect(tab) {
        when (tab) {
            0 -> com.luv.couple.net.NotificationBadges.markFriendsTabSeen()
            2 -> com.luv.couple.net.NotificationBadges.markAchievementsTabSeen()
        }
    }

    MenuBackdrop(includeNavigationBars = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Freunde", "Events", "Erfolge").forEachIndexed { index, label ->
                    val active = tab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (active) AccentRose.copy(0.28f) else BgSoft)
                            .clickable {
                                tab = index
                                when (index) {
                                    0 -> com.luv.couple.net.NotificationBadges.markFriendsTabSeen()
                                    2 -> com.luv.couple.net.NotificationBadges.markAchievementsTabSeen()
                                }
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = TextPrimary,
                            fontFamily = if (active) DisplayFont else BodyFont,
                            fontSize = 13.sp,
                            softWrap = false
                        )
                        val showDot = (index == 0 && friendsTabDot) ||
                            (index == 2 && achievementsTabDot)
                        if (showDot) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 4.dp, end = 8.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AccentRose)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            when (tab) {
                0 -> FriendsPanel(
                    modifier = Modifier.weight(1f),
                    onOpenFriendProfile = onOpenFriendProfile
                )
                1 -> EventsPanel(
                    modifier = Modifier.weight(1f),
                    onCoinsGranted = { amount ->
                        if (amount > 0) {
                            scope.launch {
                                runCatching { LuvApiClient.me() }
                                    .onSuccess { AccountSession.setAccount(it) }
                            }
                        }
                    },
                    onCreateEventLobby = onCreateEventLobby
                )
                else -> AchievementsPanel(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    onCoinsGranted = { amount ->
                        if (amount > 0) {
                            scope.launch {
                                runCatching { LuvApiClient.me() }
                                    .onSuccess { AccountSession.setAccount(it) }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FriendsPanel(
    modifier: Modifier = Modifier,
    onOpenFriendProfile: (userId: String, nickname: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    var friends by remember { mutableStateOf<List<LuvApiClient.FriendCard>>(emptyList()) }
    var incoming by remember { mutableStateOf<List<LuvApiClient.FriendCard>>(emptyList()) }
    var outgoing by remember { mutableStateOf<List<LuvApiClient.FriendCard>>(emptyList()) }
    var marriageProposals by remember { mutableStateOf<List<LuvApiClient.FriendCard>>(emptyList()) }
    var lobbyInvites by remember { mutableStateOf<List<LuvApiClient.LobbyInvite>>(emptyList()) }
    var myMarriage by remember { mutableStateOf<LuvApiClient.MarriageInfo?>(null) }
    var showSkipWait by remember { mutableStateOf(false) }
    var showCeremonyPresence by remember { mutableStateOf(false) }
    var showCeremonyGathering by remember { mutableStateOf(false) }
    var leftNoticeName by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragHoverIndex by remember { mutableIntStateOf(-1) }
    val rowHeights = remember { mutableMapOf<String, Int>() }
    val gapPx = with(density) { 10.dp.toPx() }
    var showAddFriend by remember { mutableStateOf(false) }
    var pendingFriendshipCoins by remember { mutableIntStateOf(0) }
    var pendingBugCoins by remember { mutableIntStateOf(0) }
    var claimingBug by remember { mutableStateOf(false) }
    var claimingCoins by remember { mutableStateOf(false) }
    var liveCount by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) {
        while (true) {
            runCatching { LuvApiClient.fetchLiveCount() }.onSuccess { liveCount = it }
            delay(15_000)
        }
    }

    LaunchedEffect(Unit) {
        if (com.luv.couple.net.PendingWeddingCeremony.consume()) {
            showCeremonyPresence = true
        }
        while (true) {
            runCatching { LuvApiClient.fetchCeremony() }.onSuccess { bundle ->
                if (bundle.ceremony?.leftNotify == true) {
                    leftNoticeName = bundle.ceremony.leftByNickname
                }
            }
            delay(8_000)
        }
    }

    fun applyFriendsSnap(it: LuvApiClient.FriendsBag) {
        friends = it.friends
        incoming = it.incoming
        outgoing = it.outgoing
        marriageProposals = it.marriageProposals
        lobbyInvites = it.lobbyInvites
        myMarriage = it.myMarriage
        pendingFriendshipCoins = it.pendingFriendshipCoins
        com.luv.couple.net.NotificationBadges.setFriendIncoming(
            it.incoming.size + it.marriageProposals.size + it.lobbyInvites.size
        )
        com.luv.couple.net.NotificationBadges.syncAppBadge(context)
    }

    fun reload(force: Boolean = false) {
        scope.launch {
            // Cache-First: sofort anzeigen, Loading nur ohne Cache
            val cached = LuvApiClient.peekFriendsCache()
            if (cached != null) {
                applyFriendsSnap(cached)
                loading = false
            } else {
                loading = true
            }
            runCatching { LuvApiClient.fetchFriends(force = force) }
                .onSuccess { applyFriendsSnap(it) }
                .onFailure {
                    if (cached == null) {
                        Toast.makeText(
                            context,
                            it.message ?: "Freunde laden fehlgeschlagen",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            runCatching { LuvApiClient.pendingBugRewards() }
                .onSuccess { (_, reports) ->
                    pendingBugCoins = reports.sumOf { it.rewardCoins.coerceAtLeast(10) }
                }
                .onFailure { /* ignore */ }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload(force = false) }

    fun persistOrder(next: List<LuvApiClient.FriendCard>) {
        friends = next
        scope.launch {
            runCatching {
                friends = LuvApiClient.reorderFriends(next.map { it.userId })
            }
        }
    }

    fun hoverIndexFor(from: Int, offsetY: Float): Int {
        if (friends.isEmpty()) return 0
        var acc = 0f
        val center = offsetY
        val avg = rowHeights.values.average().toFloat().takeIf { it > 0f }
            ?: with(density) { 64.dp.toPx() }
        val target = from * (avg + gapPx) + center + avg / 2f
        var idx = 0
        while (idx < friends.lastIndex) {
            val h = (rowHeights[friends[idx].userId] ?: avg.toInt()).toFloat() + gapPx
            if (target < acc + h) break
            acc += h
            idx++
        }
        return idx.coerceIn(0, friends.lastIndex)
    }

        val staffInbox by com.luv.couple.net.StaffWarningBus.inbox.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState(),
                enabled = dragId == null
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (pendingBugCoins > 0) {
            val gold = Color(0xFFFFD54F)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(gold.copy(0.18f))
                    .border(1.dp, gold.copy(0.55f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Hilfreicher Bug belohnt",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 17.sp
                )
                Text(
                    "Danke für deine Meldung — hol dir $pendingBugCoins Coins ab.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(gold.copy(0.35f))
                        .clickable(enabled = !claimingBug) {
                            claimingBug = true
                            scope.launch {
                                runCatching { LuvApiClient.claimBugReportReward() }
                                    .onSuccess { coins ->
                                        pendingBugCoins = 0
                                        Toast.makeText(
                                            context,
                                            if (coins > 0) "+$coins Coins abgeholt"
                                            else "Bereits abgeholt",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        reload()
                                    }
                                    .onFailure {
                                        Toast.makeText(
                                            context,
                                            it.message ?: "Abholen fehlgeschlagen",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                claimingBug = false
                            }
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (claimingBug) "…" else "Belohnung abholen  +$pendingBugCoins 🪙",
                        color = TextPrimary,
                        fontFamily = DisplayFont,
                        fontSize = 15.sp
                    )
                }
            }
        }
        // Nur Verwarnungen — Geschenke sind einmaliges Popup, nicht dauerhaft hier
        val warnInbox = staffInbox.filter { it.severity != "gift" }
        if (warnInbox.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Hinweise vom Team",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 18.sp
                )
                warnInbox.take(5).forEach { w ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                when (w.severity) {
                                    "final" -> Color(0x33FF6B7A)
                                    else -> Color(0x22E94E77)
                                }
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            if (w.severity == "final") "Letzte Verwarnung" else "Verwarnung",
                            color = AccentRose,
                            fontFamily = DisplayFont,
                            fontSize = 13.sp
                        )
                        Text(
                            w.message,
                            color = TextPrimary,
                            fontFamily = BodyFont,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            "Vom Team",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
        // Plus links · Coins optional · Live-Zähler rechts (gleiche Höhe)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(AccentRose)
                    .clickable { showAddFriend = true },
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = Color.White, fontFamily = DisplayFont, fontSize = 28.sp)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pendingFriendshipCoins > 0) {
                    val gold = Color(0xFFFFD54F)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(gold.copy(0.22f))
                            .border(1.dp, gold.copy(0.7f), RoundedCornerShape(20.dp))
                            .clickable(enabled = !claimingCoins) {
                                claimingCoins = true
                                scope.launch {
                                    runCatching { LuvApiClient.claimFriendshipLevelCoins() }
                                        .onSuccess { claimed ->
                                            pendingFriendshipCoins = 0
                                            Toast.makeText(
                                                context,
                                                if (claimed > 0) "+$claimed Coins abgeholt"
                                                else "Bereits abgeholt",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            reload()
                                        }
                                        .onFailure {
                                            Toast.makeText(
                                                context,
                                                it.message ?: "Abholen fehlgeschlagen",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    claimingCoins = false
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            if (claimingCoins) "…" else "🪙 $pendingFriendshipCoins abholen",
                            color = gold,
                            fontFamily = DisplayFont,
                            fontSize = 14.sp,
                            softWrap = false
                        )
                    }
                }
                LiveAppCounter(count = liveCount)
            }
        }

        val waitM = myMarriage
        if (waitM != null && (waitM.status == "engaged" || waitM.status == "wedding")) {
            val label = if (waitM.status == "engaged") {
                waitM.engageRemainingLabel ?: "…"
            } else {
                waitM.weddingRemainingLabel ?: "…"
            }
            val cost = waitM.weddingSkipCost
            val need = waitM.weddingStrokesRequired.coerceAtLeast(1)
            val strokesReady = waitM.status != "wedding" || waitM.weddingStrokesReady
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x22FFD54F))
                    .border(1.dp, Color(0xFFFFD54F).copy(0.45f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (waitM.status == "engaged") "💝 Verlobt mit ${waitM.partnerNickname ?: "…"}"
                    else "💒 Hochzeitsbild mit ${waitM.partnerNickname ?: "…"}",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 16.sp
                )
                Text(
                    if (waitM.status == "engaged") {
                        "Timer: $label (7 Tage). Einmal gratis die Hochzeitsbild-Lobby öffnen."
                    } else {
                        "Timer: $label · Striche: du ${waitM.weddingMyStrokes.coerceAtMost(need)}/$need · " +
                            "Partner ${waitM.weddingPartnerStrokes.coerceAtMost(need)}/$need."
                    },
                    color = Color(0xFFFFD54F),
                    fontFamily = BodyFont,
                    fontSize = 13.sp
                )
                if (waitM.status == "engaged" && waitM.engageFreeSkipAvailable) {
                    Text(
                        "Hochzeits-Lobby öffnen",
                        color = AccentRose,
                        fontFamily = DisplayFont,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .clickable {
                                busyId = "open-wedding"
                                scope.launch {
                                    runCatching { LuvApiClient.openWeddingLobbyFree() }
                                        .onSuccess {
                                            myMarriage = it.marriage
                                            Toast.makeText(
                                                context,
                                                "Hochzeitsbild-Lobby ist bereit — schau unter Home.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        .onFailure { e ->
                                            Toast.makeText(
                                                context,
                                                e.message ?: "Öffnen fehlgeschlagen",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    busyId = null
                                }
                            }
                            .padding(vertical = 4.dp)
                    )
                } else if (waitM.status == "wedding" && strokesReady) {
                    Text(
                        if (cost > 0) "Wartezeit überspringen · $cost Coins" else "Weiter zur Hochzeit",
                        color = AccentRose,
                        fontFamily = DisplayFont,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable { showSkipWait = true }
                            .padding(vertical = 4.dp)
                    )
                } else if (waitM.status == "wedding") {
                    Text(
                        "Coin-Skip erst nach je $need Strichen",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 13.sp
                    )
                }
            }
        }

        if (myMarriage?.ceremonyReady == true ||
            myMarriage?.status == "ceremony_pending" ||
            myMarriage?.status == "ceremony_scheduled"
        ) {
            Text(
                "Hochzeit",
                color = AccentRose,
                fontFamily = DisplayFont,
                fontSize = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x33FFD54F))
                    .clickable { showCeremonyPresence = true }
                    .padding(14.dp)
            )
        }

        if (lobbyInvites.isNotEmpty()) {
            Text("Lobby-Einladungen", color = TextPrimary, fontFamily = DisplayFont, fontSize = 16.sp)
            lobbyInvites.forEach { inv ->
                FriendRequestRow(
                    card = LuvApiClient.FriendCard(
                        userId = inv.fromUserId,
                        nickname = inv.fromNickname,
                        petEmoji = inv.fromPetEmoji
                    ),
                    busy = busyId == "l-${inv.id}",
                    acceptLabel = "Beitreten",
                    subtitle = "Einladung zu „${inv.lobbyName}“",
                    onAccept = {
                        busyId = "l-${inv.id}"
                        scope.launch {
                            val code = runCatching { LuvApiClient.acceptLobbyInvite(inv.id) }
                                .getOrNull()
                            if (code != null) {
                                // Sofort joinen — kein reload/Toast (Composition verlässt Sozial)
                                com.luv.couple.net.PendingJoin.offer(code)
                            }
                            // busyId ggf. schon unmounted — absichtlich nicht setzen
                        }
                    },
                    onDecline = {
                        busyId = "l-${inv.id}"
                        scope.launch {
                            runCatching { LuvApiClient.declineLobbyInvite(inv.id) }
                                .onSuccess {
                                    lobbyInvites = lobbyInvites.filter { it.id != inv.id }
                                }
                            busyId = null
                        }
                    },
                    onOpen = {
                        if (inv.fromUserId.isNotBlank()) {
                            onOpenFriendProfile(inv.fromUserId, inv.fromNickname)
                        }
                    }
                )
            }
        }

        if (marriageProposals.isNotEmpty()) {
            Text("Heiratsanfragen", color = TextPrimary, fontFamily = DisplayFont, fontSize = 16.sp)
            marriageProposals.forEach { card ->
                FriendRequestRow(
                    card = card,
                    busy = busyId == "m-${card.userId}",
                    acceptLabel = "Annehmen 💍",
                    onAccept = {
                        busyId = "m-${card.userId}"
                        scope.launch {
                            runCatching { LuvApiClient.acceptMarriage(card.userId) }
                                .onSuccess { reload() }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        it.message ?: "Annehmen fehlgeschlagen",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            busyId = null
                        }
                    },
                    onDecline = {
                        busyId = "m-${card.userId}"
                        scope.launch {
                            runCatching { LuvApiClient.declineMarriage(card.userId) }
                                .onSuccess { reload() }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        it.message ?: "Ablehnen fehlgeschlagen",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            busyId = null
                        }
                    },
                    onOpen = { onOpenFriendProfile(card.userId, card.nickname) }
                )
            }
        }

        if (incoming.isNotEmpty()) {
            incoming.forEach { card ->
                FriendRequestRow(
                    card = card,
                    busy = busyId == card.userId,
                    onAccept = {
                        busyId = card.userId
                        scope.launch {
                            runCatching { LuvApiClient.acceptFriend(card.userId) }
                                .onSuccess { reload() }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        it.message ?: "Annehmen fehlgeschlagen",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            busyId = null
                        }
                    },
                    onDecline = {
                        busyId = card.userId
                        scope.launch {
                            runCatching { LuvApiClient.declineFriend(card.userId) }
                                .onSuccess { reload() }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        it.message ?: "Ablehnen fehlgeschlagen",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            busyId = null
                        }
                    },
                    onOpen = { onOpenFriendProfile(card.userId, card.nickname) }
                )
            }
        }

        when {
            loading && friends.isEmpty() && incoming.isEmpty() -> {
                Text("Lade…", color = TextMuted, fontFamily = BodyFont)
            }
            friends.isEmpty() && incoming.isEmpty() -> Unit
            else -> {
                friends.forEachIndexed { index, card ->
                    val dragging = dragId == card.userId
                    val lift = if (dragging) dragOffsetY else 0f
                    FriendRow(
                        card = card,
                        subtitle = friendSubtitle(card),
                        modifier = Modifier
                            .zIndex(if (dragging) 10f else 0f)
                            .graphicsLayer {
                                translationY = lift
                                shadowElevation = if (dragging) 12f else 0f
                                alpha = if (
                                    dragId != null &&
                                    !dragging &&
                                    dragHoverIndex == index
                                ) 0.55f else 1f
                            }
                            .onSizeChanged { rowHeights[card.userId] = it.height }
                            .pointerInput(card.userId, friends.map { it.userId }) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        dragId = card.userId
                                        dragOffsetY = 0f
                                        dragHoverIndex = index
                                    },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        dragOffsetY += drag.y
                                        dragHoverIndex = hoverIndexFor(index, dragOffsetY)
                                    },
                                    onDragEnd = {
                                        val from = friends.indexOfFirst { it.userId == dragId }
                                        val to = dragHoverIndex
                                        if (from >= 0 && to >= 0 && from != to) {
                                            val next = friends.toMutableList()
                                            val item = next.removeAt(from)
                                            next.add(to.coerceIn(0, next.size), item)
                                            persistOrder(next)
                                        }
                                        dragId = null
                                        dragOffsetY = 0f
                                        dragHoverIndex = -1
                                    },
                                    onDragCancel = {
                                        dragId = null
                                        dragOffsetY = 0f
                                        dragHoverIndex = -1
                                    }
                                )
                            }
                            .clickable { onOpenFriendProfile(card.userId, card.nickname) },
                        trailing = {
                            Text("≡", color = TextMuted, fontSize = 18.sp)
                        }
                    )
                }
            }
        }

        if (outgoing.isNotEmpty()) {
            outgoing.forEach { card ->
                FriendRow(
                    card = card,
                    subtitle = "Anfrage ausstehend",
                    modifier = Modifier.clickable {
                        onOpenFriendProfile(card.userId, card.nickname)
                    }
                )
            }
        }
    }

    if (showAddFriend) {
        AddFriendByNicknameDialog(
            onDismiss = { showAddFriend = false },
            onSent = {
                showAddFriend = false
                reload()
            },
            onOpenProfile = { id, nick ->
                showAddFriend = false
                onOpenFriendProfile(id, nick)
            }
        )
    }

    val skipM = myMarriage
    if (showSkipWait && skipM != null) {
        MarriageSkipWaitDialog(
            marriage = skipM,
            onDismiss = { showSkipWait = false },
            onSkipped = {
                myMarriage = it
                reload()
            }
        )
    }

    if (showCeremonyPresence) {
        com.luv.couple.ui.wedding.WeddingPresenceDialog(
            onDismiss = { showCeremonyPresence = false },
            onScheduled = {
                showCeremonyPresence = false
                reload()
                Toast.makeText(context, "Hochzeit-Lobby oben im Home", Toast.LENGTH_SHORT).show()
            },
            onShareRemind = { text ->
                com.luv.couple.ui.wedding.shareWeddingText(context, text)
            }
        )
    }

    if (showCeremonyGathering) {
        com.luv.couple.ui.wedding.WeddingGatheringDialog(
            onDismiss = { showCeremonyGathering = false },
            onEnterAltar = {
                showCeremonyGathering = false
                context.startActivity(
                    android.content.Intent(
                        context,
                        com.luv.couple.ui.wedding.WeddingRoomActivity::class.java
                    )
                )
            },
            onShareRemind = { text ->
                com.luv.couple.ui.wedding.shareWeddingText(context, text)
            },
            isCouple = myMarriage?.partnerId != null
        )
    }

    leftNoticeName?.let { name ->
        com.luv.couple.ui.wedding.WeddingLeftNoticeDialog(
            partnerName = name,
            onDismiss = { leftNoticeName = null }
        )
    }
}

@Composable
private fun LiveAppCounter(count: Int) {
    val liveGreen = Color(0xFF3DDC84)
    val pulse = rememberInfiniteTransition(label = "liveDot")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveScale"
    )
    val haloAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveHalo"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .height(56.dp)
            .padding(horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = haloAlpha
                    }
                    .clip(CircleShape)
                    .background(liveGreen)
            )
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(liveGreen)
            )
        }
        Text(
            text = if (count < 0) "…" else count.toString(),
            color = TextPrimary,
            fontFamily = DisplayFont,
            fontSize = 18.sp,
            softWrap = false
        )
    }
}

@Composable
private fun AddFriendByNicknameDialog(
    onDismiss: () -> Unit,
    onSent: () -> Unit,
    onOpenProfile: (userId: String, nickname: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<LuvApiClient.UserLookup?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun search() {
        val q = query.trim()
        if (q.length < 2) {
            error = "Mindestens 2 Zeichen"
            found = null
            return
        }
        scope.launch {
            busy = true
            error = null
            found = null
            runCatching { LuvApiClient.lookupUserByNickname(q) }
                .onSuccess { found = it }
                .onFailure {
                    error = it.message ?: "Nicht gefunden"
                }
            busy = false
        }
    }

    fun sendRequest(userId: String) {
        scope.launch {
            busy = true
            runCatching { LuvApiClient.sendFriendRequest(userId) }
                .onSuccess {
                    Toast.makeText(context, "Anfrage gesendet", Toast.LENGTH_SHORT).show()
                    onSent()
                }
                .onFailure {
                    Toast.makeText(
                        context,
                        it.message ?: "Anfrage fehlgeschlagen",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgSoft,
        title = {
            Text(
                "Freund finden",
                fontFamily = DisplayFont,
                fontSize = 22.sp,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicTextField(
                    value = query,
                    onValueChange = {
                        query = it.take(18)
                        error = null
                        found = null
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontFamily = BodyFont,
                        fontSize = 16.sp
                    ),
                    cursorBrush = SolidColor(AccentRose),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(0.06f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        Box {
                            if (query.isBlank()) {
                                Text(
                                    "Spitzname",
                                    color = TextMuted,
                                    fontFamily = BodyFont,
                                    fontSize = 16.sp
                                )
                            }
                            inner()
                        }
                    }
                )
                if (error != null) {
                    Text(error!!, color = AccentRose, fontFamily = BodyFont, fontSize = 13.sp)
                }
                found?.let { hit ->
                    FriendRow(
                        card = hit.card,
                        subtitle = when (hit.friendStatus) {
                            "friends" -> "Schon befreundet"
                            "outgoing" -> "Anfrage schon gesendet"
                            "incoming" -> "Hat dich angefragt"
                            else -> null
                        },
                        modifier = Modifier.clickable {
                            onOpenProfile(hit.card.userId, hit.card.nickname)
                        }
                    )
                    when (hit.friendStatus) {
                        "none" -> TextButton(
                            onClick = { sendRequest(hit.card.userId) },
                            enabled = !busy
                        ) {
                            Text("Anfrage senden", color = AccentRose, fontFamily = DisplayFont)
                        }
                        "incoming" -> TextButton(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    runCatching { LuvApiClient.acceptFriend(hit.card.userId) }
                                        .onSuccess {
                                            Toast.makeText(
                                                context,
                                                "Freundschaft angenommen",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            onSent()
                                        }
                                        .onFailure {
                                            Toast.makeText(
                                                context,
                                                it.message ?: "Annehmen fehlgeschlagen",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    busy = false
                                }
                            },
                            enabled = !busy
                        ) {
                            Text("Annehmen", color = AccentRose, fontFamily = DisplayFont)
                        }
                        else -> Unit
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { search() }, enabled = !busy) {
                Text(
                    if (busy) "…" else "Suchen",
                    color = AccentRose,
                    fontFamily = DisplayFont
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Schließen", color = TextMuted, fontFamily = BodyFont)
            }
        }
    )
}

private fun friendSubtitle(card: LuvApiClient.FriendCard): String {
    val m = card.marriage
    return when {
        card.isSpouse -> "Ehepartner · Lv. ${card.friendshipLevel}"
        card.isFiance && m?.status == "engaged" -> {
            val d = (m.engageRemainingMs / (24 * 60 * 60 * 1000L)).coerceAtLeast(0)
            "Verlobt · noch ${d + 1} Tage · Lv. ${card.friendshipLevel}"
        }
        card.isFiance && m?.status == "wedding" -> {
            val d = (m.weddingRemainingMs / (24 * 60 * 60 * 1000L)).coerceAtLeast(0)
            "Hochzeit · noch ${d + 1} Tage · Lv. ${card.friendshipLevel}"
        }
        else -> "Lv. ${card.friendshipLevel} / 100"
    }
}

@Composable
private fun FriendRequestRow(
    card: LuvApiClient.FriendCard,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpen: () -> Unit,
    acceptLabel: String = "Annehmen",
    subtitle: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BgSoft)
            .border(1.dp, AccentRose.copy(0.25f), RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FriendRow(
            card = card,
            subtitle = subtitle,
            modifier = Modifier.clickable(onClick = onOpen)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onAccept, enabled = !busy) {
                Text(acceptLabel, color = AccentRose, fontFamily = DisplayFont)
            }
            TextButton(onClick = onDecline, enabled = !busy) {
                Text("Ablehnen", color = TextMuted, fontFamily = BodyFont)
            }
        }
    }
}

@Composable
private fun FriendRow(
    card: LuvApiClient.FriendCard,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val color = Color(PeerPalette.strokeColor(PeerPalette.indexFor(card.nickname)))
    val gold = Color(0xFFFFD54F)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (card.isSpouse) Color(0x22FFD54F) else BgSoft)
            .then(
                if (card.isSpouse) {
                    Modifier.border(2.dp, gold, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (card.isSpouse) Modifier.border(2.dp, gold, CircleShape) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            com.luv.couple.ui.CompanionGlyph(petId = card.petEmoji, fontSize = 22.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (card.isSpouse) "💍 ${card.nickname}" else card.nickname,
                color = if (card.isSpouse) gold else TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 17.sp
            )
            if (subtitle != null) {
                Text(subtitle, color = TextMuted, fontFamily = BodyFont, fontSize = 12.sp)
            }
        }
        trailing?.invoke()
    }
}
