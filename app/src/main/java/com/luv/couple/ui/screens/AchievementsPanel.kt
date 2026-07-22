package com.luv.couple.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.net.AchievementsBadge
import com.luv.couple.net.LuvApiClient
import com.luv.couple.shop.ItemLabels
import com.luv.couple.profile.ProfileThemeBackdrop
import com.luv.couple.ui.ItemGlyph
import com.luv.couple.ui.rememberUiScale
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.launch

private val CATEGORY_ORDER = listOf(
    "sozial" to "Sozial",
    "begleiter" to "Begleiter",
    "malen" to "Malen",
    "lobby" to "Lobby",
    "markt" to "Markt",
    "profil" to "Profil"
)

@Composable
fun AchievementsPanel(
    modifier: Modifier = Modifier,
    onCoinsGranted: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cached by AchievementsBadge.latest.collectAsStateWithLifecycle()
    var state by remember { mutableStateOf(cached) }
    var loading by remember { mutableStateOf(cached == null) }
    var expandedCategory by remember { mutableStateOf<String?>("sozial") }
    var busyId by remember { mutableStateOf<String?>(null) }
    var previewItem by remember { mutableStateOf<LuvApiClient.AchievementRewardItem?>(null) }

    LaunchedEffect(cached) {
        if (cached != null) {
            state = cached
            loading = false
        }
    }

    fun reload(forceSpinner: Boolean = false) {
        scope.launch {
            if (forceSpinner || state == null) loading = true
            runCatching { LuvApiClient.fetchAchievements() }
                .onSuccess {
                    state = it
                    AchievementsBadge.updateFrom(it)
                }
                .onFailure {
                    if (
                        state == null &&
                        it !is kotlinx.coroutines.CancellationException
                    ) {
                        Toast.makeText(
                            context,
                            it.message ?: "Erfolge laden fehlgeschlagen",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            loading = false
        }
    }

    fun claimAchievement(id: String) {
        busyId = id
        scope.launch {
            runCatching { LuvApiClient.claimAchievementReward(id) }
                .onSuccess { result ->
                    state = result.state
                    AchievementsBadge.updateFrom(result.state)
                    onCoinsGranted(result.coinsGranted)
                    val msg = when {
                        result.itemGranted != null -> {
                            val g = result.itemGranted
                            val name = g.label.takeIf { it.isNotBlank() && !ItemLabels.looksLikeRawId(it) }
                                ?: ItemLabels.forKind(g.kind, g.emoji)
                            "$name erhalten"
                        }
                        result.coinsGranted > 0 -> "+${result.coinsGranted} Coins abgeholt"
                        else -> "Abgeholt"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(
                        context,
                        it.message ?: "Abholen fehlgeschlagen",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            busyId = null
        }
    }

    // Soft-Refresh: Cache sofort zeigen, Netzwerk im Hintergrund
    LaunchedEffect(Unit) { reload(forceSpinner = state == null) }

    previewItem?.let { item ->
        AchievementItemPreviewDialog(
            item = item,
            onDismiss = { previewItem = null }
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val ui = rememberUiScale()
        val s = state
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ui.s(14.dp))
        ) {
            if (loading && s == null) {
                Text("Lade Erfolge…", color = TextMuted, fontFamily = BodyFont)
                return@Column
            }
            if (s == null) return@Column

            StreakHeader(
                streak = s.streak,
                unlocked = s.unlockedCount,
                total = s.totalCount,
                coinsEarned = s.coinsEarnedToday,
                coinsCap = s.coinsCapToday,
                coinsRemaining = s.coinsRemainingToday,
                scale = ui.value
            )

            DailyTasksCard(
                daily = s.daily,
                busy = busyId == "daily",
                scale = ui.value,
                onClaim = {
                    busyId = "daily"
                    scope.launch {
                        runCatching { LuvApiClient.claimDailyAchievementReward() }
                            .onSuccess { (coins, next) ->
                                state = next
                                onCoinsGranted(coins)
                                Toast.makeText(
                                    context,
                                    if (coins > 0) "+$coins Coin abgeholt" else "Abgeholt",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "Abholen fehlgeschlagen",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        busyId = null
                    }
                }
            )

            val claimable = s.achievements.filter { it.claimable }
            if (claimable.isNotEmpty()) {
                Text(
                    "Abholen",
                    fontFamily = DisplayFont,
                    fontSize = ui.ts(22.sp),
                    color = TextPrimary
                )
                claimable.forEach { item ->
                    AchievementRow(
                        item = item,
                        busy = busyId == item.id,
                        scale = ui.value,
                        onClaim = { claimAchievement(item.id) },
                        onPreviewItem = { previewItem = it }
                    )
                }
            }

            Text(
                "Erfolge",
                fontFamily = DisplayFont,
                fontSize = ui.ts(22.sp),
                color = TextPrimary
            )

            CATEGORY_ORDER.forEach { (catId, catLabel) ->
                val items = s.achievements.filter { it.category == catId && !it.claimable }
                if (items.isEmpty()) return@forEach
                val unlockedInCat = items.count { it.unlocked }
                val expanded = expandedCategory == catId
                CategorySection(
                    label = catLabel,
                    unlocked = unlockedInCat,
                    total = items.size,
                    expanded = expanded,
                    scale = ui.value,
                    onToggle = {
                        expandedCategory = if (expanded) null else catId
                    },
                    items = items,
                    busyId = busyId,
                    onClaim = { claimAchievement(it) },
                    onPreviewItem = { previewItem = it }
                )
            }

            Spacer(modifier = Modifier.height(ui.s(8.dp)))
        }
    }
}

@Composable
private fun StreakHeader(
    streak: Int,
    unlocked: Int,
    total: Int,
    coinsEarned: Int,
    coinsCap: Int,
    coinsRemaining: Int,
    scale: Float
) {
    fun s(v: Dp) = v * scale
    fun ts(v: TextUnit) = (v.value * scale).sp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(s(20.dp)))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1E2430), BgSoft, Color(0xFF2A1A22))
                )
            )
            .border(1.dp, AccentRose.copy(0.22f), RoundedCornerShape(s(20.dp)))
            .padding(s(16.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(s(14.dp))
        ) {
            Box(
                modifier = Modifier
                    .size(s(56.dp))
                    .clip(CircleShape)
                    .background(AccentRose.copy(0.18f))
                    .border(1.dp, AccentRose.copy(0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔥", fontSize = ts(18.sp))
                    Text(
                        "$streak",
                        color = AccentRose,
                        fontFamily = DisplayFont,
                        fontSize = ts(16.sp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Day Streak",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = ts(18.sp)
                )
                Text(
                    "$unlocked / $total Erfolge freigeschaltet",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = ts(13.sp),
                    softWrap = true
                )
                Spacer(modifier = Modifier.height(s(8.dp)))
                Text(
                    "Heute $coinsEarned / $coinsCap Coins · noch $coinsRemaining",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = ts(12.sp),
                    softWrap = true
                )
                Spacer(modifier = Modifier.height(s(4.dp)))
                LinearProgressIndicator(
                    progress = {
                        if (coinsCap <= 0) 0f
                        else (coinsEarned.toFloat() / coinsCap).coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(s(4.dp))
                        .clip(RoundedCornerShape(s(2.dp))),
                    color = AccentRose,
                    trackColor = BgDeep
                )
            }
        }
    }
}

@Composable
private fun DailyTasksCard(
    daily: LuvApiClient.AchievementDaily,
    busy: Boolean,
    scale: Float,
    onClaim: () -> Unit
) {
    fun s(v: Dp) = v * scale
    fun ts(v: TextUnit) = (v.value * scale).sp
    val allDone = daily.completed || daily.tasks.all { it.done }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(s(18.dp)))
            .background(BgSoft)
            .border(
                1.dp,
                if (daily.claimable) AccentRose.copy(0.45f)
                else if (allDone) AccentRose.copy(0.25f)
                else Color.White.copy(0.08f),
                RoundedCornerShape(s(18.dp))
            )
            .padding(s(14.dp)),
        verticalArrangement = Arrangement.spacedBy(s(10.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Tagesaufgaben",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = ts(17.sp)
                )
                Text(
                    daily.date.ifBlank { "Heute" },
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = ts(12.sp)
                )
            }
            Text(
                "+${daily.rewardCoins} Coins",
                color = AccentRose,
                fontFamily = DisplayFont,
                fontSize = ts(14.sp),
                softWrap = false
            )
        }
        Text(
            "Erledige alle Aufgaben — danach kannst du die Belohnung abholen.",
            color = TextMuted,
            fontFamily = BodyFont,
            fontSize = ts(12.sp)
        )
        daily.tasks.forEach { task ->
            DailyTaskRow(task = task, scale = scale)
        }
        when {
            daily.claimable -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(s(12.dp)))
                        .background(AccentRose.copy(0.85f))
                        .clickable(enabled = !busy, onClick = onClaim)
                        .padding(vertical = s(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (busy) "…" else "Belohnung abholen",
                        color = Color.White,
                        fontFamily = DisplayFont,
                        fontSize = ts(15.sp),
                        softWrap = false
                    )
                }
            }
            daily.rewardClaimed -> {
                Text(
                    "Tagesbelohnung abgeholt.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = ts(12.sp)
                )
            }
            allDone -> {
                Text(
                    "Fertig — hol dir die Belohnung.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = ts(12.sp)
                )
            }
        }
    }
}

@Composable
private fun DailyTaskRow(task: LuvApiClient.AchievementDailyTask, scale: Float) {
    fun s(v: Dp) = v * scale
    fun ts(v: TextUnit) = (v.value * scale).sp
    val progress = if (task.target <= 0) 1f
    else (task.progress.toFloat() / task.target).coerceIn(0f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s(10.dp))
    ) {
        Box(
            modifier = Modifier
                .size(s(22.dp))
                .clip(CircleShape)
                .background(
                    if (task.done) AccentRose.copy(0.25f) else Color.White.copy(0.06f)
                )
                .border(
                    1.dp,
                    if (task.done) AccentRose else Color.White.copy(0.12f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (task.done) "✓" else "○",
                color = if (task.done) AccentRose else TextMuted,
                fontSize = ts(11.sp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                task.title,
                color = if (task.done) TextMuted else TextPrimary,
                fontFamily = BodyFont,
                fontSize = ts(14.sp),
                softWrap = true
            )
            if (task.hint.isNotBlank() && !task.done) {
                Text(
                    task.hint,
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = ts(11.sp),
                    softWrap = true,
                    lineHeight = ts(14.sp)
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(s(3.dp))
                    .clip(RoundedCornerShape(s(2.dp))),
                color = if (task.done) AccentRose.copy(0.7f) else AccentRose,
                trackColor = BgDeep
            )
        }
        Text(
            "${task.progress.coerceAtMost(task.target)}/${task.target}",
            color = TextMuted,
            fontFamily = BodyFont,
            fontSize = ts(11.sp),
            softWrap = false
        )
    }
}

@Composable
private fun CategorySection(
    label: String,
    unlocked: Int,
    total: Int,
    expanded: Boolean,
    scale: Float,
    onToggle: () -> Unit,
    items: List<LuvApiClient.AchievementItem>,
    busyId: String?,
    onClaim: (String) -> Unit,
    onPreviewItem: (LuvApiClient.AchievementRewardItem) -> Unit
) {
    fun s(v: Dp) = v * scale
    fun ts(v: TextUnit) = (v.value * scale).sp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(s(16.dp)))
            .background(BgSoft.copy(0.85f))
            .border(1.dp, Color.White.copy(0.06f), RoundedCornerShape(s(16.dp)))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onToggle,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
                .background(Color.White.copy(if (expanded) 0.04f else 0f))
                .padding(horizontal = s(14.dp), vertical = s(12.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = TextPrimary, fontFamily = DisplayFont, fontSize = ts(16.sp))
                Text(
                    "$unlocked / $total",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = ts(12.sp)
                )
            }
            Text(
                if (expanded) "▾" else "▸",
                color = TextMuted,
                fontSize = ts(16.sp)
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(
                    start = s(14.dp),
                    end = s(14.dp),
                    bottom = s(12.dp)
                ),
                verticalArrangement = Arrangement.spacedBy(s(8.dp))
            ) {
                items.forEach { item ->
                    AchievementRow(
                        item = item,
                        busy = busyId == item.id,
                        scale = scale,
                        onClaim = { onClaim(item.id) },
                        onPreviewItem = onPreviewItem
                    )
                }
            }
        }
    }
}

@Composable
private fun AchievementRow(
    item: LuvApiClient.AchievementItem,
    busy: Boolean,
    scale: Float,
    onClaim: () -> Unit,
    onPreviewItem: (LuvApiClient.AchievementRewardItem) -> Unit
) {
    fun s(v: Dp) = v * scale
    fun ts(v: TextUnit) = (v.value * scale).sp
    val progress = if (item.target <= 0) 1f
    else (item.progress.toFloat() / item.target).coerceIn(0f, 1f)
    val reward = item.rewardItem
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(s(12.dp)))
            .background(
                when {
                    item.claimable -> AccentRose.copy(0.14f)
                    item.unlocked -> AccentRose.copy(0.08f)
                    else -> BgDeep
                }
            )
            .border(
                1.dp,
                when {
                    item.claimable -> AccentRose.copy(0.45f)
                    item.unlocked -> AccentRose.copy(0.25f)
                    else -> Color.White.copy(0.05f)
                },
                RoundedCornerShape(s(12.dp))
            )
            .padding(horizontal = s(12.dp), vertical = s(10.dp)),
        verticalArrangement = Arrangement.spacedBy(s(8.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(s(10.dp))
        ) {
            Box(
                modifier = Modifier
                    .size(s(36.dp))
                    .clip(CircleShape)
                    .background(
                        if (item.unlocked) AccentRose.copy(0.2f) else Color.White.copy(0.06f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when {
                        item.claimable -> "🎁"
                        item.unlocked -> "★"
                        else -> "○"
                    },
                    color = if (item.unlocked) AccentRose else TextMuted,
                    fontSize = ts(14.sp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = ts(14.sp),
                    softWrap = true
                )
                Text(
                    item.desc,
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = ts(11.sp),
                    softWrap = true
                )
                if (!item.unlocked) {
                    Spacer(modifier = Modifier.height(s(4.dp)))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(s(3.dp))
                            .clip(RoundedCornerShape(s(2.dp))),
                        color = AccentRose.copy(0.8f),
                        trackColor = Color.White.copy(0.06f)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (reward != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(s(10.dp)))
                            .background(AccentRose.copy(0.16f))
                            .border(1.dp, AccentRose.copy(0.35f), RoundedCornerShape(s(10.dp)))
                            .clickable { onPreviewItem(reward) }
                            .padding(horizontal = s(8.dp), vertical = s(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        ItemGlyph(id = reward.emoji, fontSize = ts(18.sp))
                    }
                } else {
                    Text(
                        "${item.coins}🪙",
                        color = AccentRose,
                        fontFamily = DisplayFont,
                        fontSize = ts(12.sp),
                        softWrap = false
                    )
                }
                when {
                    item.claimed -> Text(
                        "geholt",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = ts(10.sp)
                    )
                    !item.unlocked -> Text(
                        "${item.progress}/${item.target}",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = ts(10.sp),
                        softWrap = false
                    )
                }
            }
        }
        if (item.claimable) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(s(10.dp)))
                    .background(AccentRose.copy(0.9f))
                    .clickable(enabled = !busy, onClick = onClaim)
                    .padding(vertical = s(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (busy) "…"
                    else if (reward != null) "Belohnung abholen"
                    else "Coins abholen",
                    color = Color.White,
                    fontFamily = DisplayFont,
                    fontSize = ts(13.sp),
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun AchievementItemPreviewDialog(
    item: LuvApiClient.AchievementRewardItem,
    onDismiss: () -> Unit
) {
    val kindLabel = when (item.kind) {
        "pets" -> "Begleiter"
        "themes" -> "Hintergrund"
        "stickers" -> "Sticker"
        "emojis" -> "Reaktion"
        else -> "Item"
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(BgSoft)
                .border(1.dp, AccentRose.copy(0.28f), RoundedCornerShape(22.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Belohnung",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 12.sp
            )
            if (item.kind == "themes") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.2f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(16.dp))
                ) {
                    ProfileThemeBackdrop(
                        themeId = item.itemId,
                        modifier = Modifier.fillMaxSize()
                    )
                    Text(
                        item.emoji,
                        fontSize = 36.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(AccentRose.copy(0.14f))
                        .border(1.dp, AccentRose.copy(0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    ItemGlyph(id = item.emoji, fontSize = 44.sp)
                }
            }
            Text(
                item.label,
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
            Text(
                kindLabel,
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 13.sp
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentRose.copy(0.85f))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Schließen",
                    color = Color.White,
                    fontFamily = DisplayFont,
                    fontSize = 14.sp
                )
            }
        }
    }
}
