package com.luv.couple.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.net.LuvApiClient
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
    var state by remember { mutableStateOf<LuvApiClient.AchievementsState?>(null) }
    var loading by remember { mutableStateOf(true) }
    var expandedCategory by remember { mutableStateOf<String?>("sozial") }

    fun reload() {
        scope.launch {
            loading = state == null
            runCatching { LuvApiClient.fetchAchievements() }
                .onSuccess { state = it }
                .onFailure {
                    Toast.makeText(
                        context,
                        it.message ?: "Erfolge laden fehlgeschlagen",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    val s = state
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
            coinsRemaining = s.coinsRemainingToday
        )

        DailyTasksCard(
            daily = s.daily,
            onAllDone = { onCoinsGranted(1) }
        )

        Text(
            "Erfolge",
            fontFamily = DisplayFont,
            fontSize = 22.sp,
            color = TextPrimary
        )

        CATEGORY_ORDER.forEach { (catId, catLabel) ->
            val items = s.achievements.filter { it.category == catId }
            if (items.isEmpty()) return@forEach
            val unlockedInCat = items.count { it.unlocked }
            val expanded = expandedCategory == catId
            CategorySection(
                label = catLabel,
                unlocked = unlockedInCat,
                total = items.size,
                expanded = expanded,
                onToggle = {
                    expandedCategory = if (expanded) null else catId
                },
                items = items
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun StreakHeader(
    streak: Int,
    unlocked: Int,
    total: Int,
    coinsEarned: Int,
    coinsCap: Int,
    coinsRemaining: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1E2430), BgSoft, Color(0xFF2A1A22))
                )
            )
            .border(1.dp, AccentRose.copy(0.22f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(AccentRose.copy(0.18f))
                    .border(1.dp, AccentRose.copy(0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔥", fontSize = 18.sp)
                    Text(
                        "$streak",
                        color = AccentRose,
                        fontFamily = DisplayFont,
                        fontSize = 16.sp
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Day Streak",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 18.sp
                )
                Text(
                    "$unlocked / $total Erfolge freigeschaltet",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Heute $coinsEarned / $coinsCap Coins · noch $coinsRemaining",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = {
                        if (coinsCap <= 0) 0f
                        else (coinsEarned.toFloat() / coinsCap).coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
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
    onAllDone: () -> Unit
) {
    val allDone = daily.completed || daily.tasks.all { it.done }
    LaunchedEffect(allDone) {
        if (allDone) onAllDone()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BgSoft)
            .border(
                1.dp,
                if (allDone) AccentRose.copy(0.35f) else Color.White.copy(0.08f),
                RoundedCornerShape(18.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Tagesaufgaben",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 17.sp
                )
                Text(
                    daily.date.ifBlank { "Heute" },
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 12.sp
                )
            }
            if (allDone) {
                Text(
                    "+1 Coin",
                    color = AccentRose,
                    fontFamily = DisplayFont,
                    fontSize = 14.sp
                )
            }
        }
        daily.tasks.forEach { task ->
            DailyTaskRow(task = task)
        }
        if (allDone) {
            Text(
                "Alle erledigt — Belohnung kommt automatisch.",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun DailyTaskRow(task: LuvApiClient.AchievementDailyTask) {
    val progress = if (task.target <= 0) 1f
    else (task.progress.toFloat() / task.target).coerceIn(0f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
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
                fontSize = 11.sp
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                task.title,
                color = if (task.done) TextMuted else TextPrimary,
                fontFamily = BodyFont,
                fontSize = 14.sp,
                softWrap = true
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (task.done) AccentRose.copy(0.7f) else AccentRose,
                trackColor = BgDeep
            )
        }
        Text(
            "${task.progress.coerceAtMost(task.target)}/${task.target}",
            color = TextMuted,
            fontFamily = BodyFont,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun CategorySection(
    label: String,
    unlocked: Int,
    total: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    items: List<LuvApiClient.AchievementItem>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgSoft.copy(0.85f))
            .border(1.dp, Color.White.copy(0.06f), RoundedCornerShape(16.dp))
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
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = TextPrimary, fontFamily = DisplayFont, fontSize = 16.sp)
                Text(
                    "$unlocked / $total",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 12.sp
                )
            }
            Text(
                if (expanded) "▾" else "▸",
                color = TextMuted,
                fontSize = 16.sp
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEach { item ->
                    AchievementRow(item = item)
                }
            }
        }
    }
}

@Composable
private fun AchievementRow(item: LuvApiClient.AchievementItem) {
    val progress = if (item.target <= 0) 1f
    else (item.progress.toFloat() / item.target).coerceIn(0f, 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (item.unlocked) AccentRose.copy(0.08f) else BgDeep)
            .border(
                1.dp,
                if (item.unlocked) AccentRose.copy(0.25f) else Color.White.copy(0.05f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (item.unlocked) AccentRose.copy(0.2f) else Color.White.copy(0.06f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (item.unlocked) "★" else "○",
                color = if (item.unlocked) AccentRose else TextMuted,
                fontSize = 14.sp
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                color = if (item.unlocked) TextPrimary else TextPrimary.copy(0.85f),
                fontFamily = DisplayFont,
                fontSize = 14.sp,
                softWrap = true
            )
            Text(
                item.desc,
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 11.sp,
                softWrap = true
            )
            if (!item.unlocked) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = AccentRose.copy(0.8f),
                    trackColor = Color.White.copy(0.06f)
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${item.coins}🪙",
                color = AccentRose,
                fontFamily = DisplayFont,
                fontSize = 12.sp
            )
            if (!item.unlocked) {
                Text(
                    "${item.progress}/${item.target}",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 10.sp
                )
            }
        }
    }
}
