package com.luv.couple.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.data.PeerPalette
import com.luv.couple.net.AccountSession
import com.luv.couple.net.AchievementsBadge
import com.luv.couple.net.LuvApiClient
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@Composable
fun SocialScreen(
    onOpenFriendProfile: (userId: String, nickname: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    val hasClaimable by AchievementsBadge.hasClaimable.collectAsStateWithLifecycle()
    val friendIncoming by com.luv.couple.net.NotificationBadges.friendIncoming
        .collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        runCatching { LuvApiClient.pingAchievement("social_opens") }
        AchievementsBadge.refresh()
        com.luv.couple.net.NotificationBadges.refreshFriends()
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
                listOf("Freunde", "Erfolge").forEachIndexed { index, label ->
                    val active = tab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (active) AccentRose.copy(0.28f) else BgSoft)
                            .clickable { tab = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = TextPrimary,
                            fontFamily = if (active) DisplayFont else BodyFont,
                            fontSize = 14.sp,
                            softWrap = false
                        )
                        val showDot = (index == 0 && friendIncoming > 0) ||
                            (index == 1 && hasClaimable)
                        if (showDot) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 4.dp, end = 10.dp)
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
    var loading by remember { mutableStateOf(true) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragHoverIndex by remember { mutableIntStateOf(-1) }
    val rowHeights = remember { mutableMapOf<String, Int>() }
    val gapPx = with(density) { 10.dp.toPx() }
    var showAddFriend by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            loading = true
            runCatching { LuvApiClient.fetchFriends() }
                .onSuccess {
                    friends = it.friends
                    incoming = it.incoming
                    outgoing = it.outgoing
                    com.luv.couple.net.NotificationBadges.setFriendIncoming(it.incoming.size)
                    com.luv.couple.net.NotificationBadges.syncAppBadge(context)
                }
                .onFailure {
                    Toast.makeText(
                        context,
                        it.message ?: "Freunde laden fehlgeschlagen",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState(),
                enabled = dragId == null
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Runde Plus-Kachel: Spitzname suchen
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

@Composable
private fun FriendRequestRow(
    card: LuvApiClient.FriendCard,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpen: () -> Unit
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
            modifier = Modifier.clickable(onClick = onOpen)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onAccept, enabled = !busy) {
                Text("Annehmen", color = AccentRose, fontFamily = DisplayFont)
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgSoft)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(card.petEmoji, fontSize = 22.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(card.nickname, color = TextPrimary, fontFamily = DisplayFont, fontSize = 17.sp)
            if (subtitle != null) {
                Text(subtitle, color = TextMuted, fontFamily = BodyFont, fontSize = 12.sp)
            }
        }
        trailing?.invoke()
    }
}
