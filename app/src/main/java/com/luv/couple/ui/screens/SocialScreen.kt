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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.luv.couple.data.PeerPalette
import com.luv.couple.net.LuvApiClient
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SocialScreen(
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

    fun reload() {
        scope.launch {
            loading = true
            runCatching { LuvApiClient.fetchFriends() }
                .onSuccess {
                    friends = it.friends
                    incoming = it.incoming
                    outgoing = it.outgoing
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
        // Approximate with average row height
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

    MenuBackdrop(includeNavigationBars = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 8.dp)
                .verticalScroll(
                    rememberScrollState(),
                    enabled = dragId == null
                ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Sozial", fontFamily = DisplayFont, fontSize = 34.sp, color = TextPrimary)
            Text(
                "Freunde, Anfragen und gemütliche Begleiter-Kraulis.",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 14.sp
            )

            if (incoming.isNotEmpty()) {
                Text("Anfragen", fontFamily = DisplayFont, fontSize = 20.sp, color = TextPrimary)
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

            Text("Freunde", fontFamily = DisplayFont, fontSize = 20.sp, color = TextPrimary)
            when {
                loading && friends.isEmpty() && incoming.isEmpty() -> {
                    Text("Lade…", color = TextMuted, fontFamily = BodyFont)
                }
                friends.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(BgSoft)
                            .padding(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Noch keine Freunde",
                                color = TextPrimary,
                                fontFamily = DisplayFont,
                                fontSize = 18.sp
                            )
                            Text(
                                "Auf der Leinwand unter einem Profil eine Anfrage senden.",
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                else -> {
                    Text(
                        "Lange drücken zum Umsortieren",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 12.sp
                    )
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
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Gesendet",
                    fontFamily = DisplayFont,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
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
    }
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
        Box(modifier = Modifier.size(44.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    card.nickname.take(1).uppercase(),
                    color = Color(0xFF1A1F2E),
                    fontFamily = DisplayFont,
                    fontSize = 18.sp
                )
            }
            Text(
                card.petEmoji,
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
            )
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
