package com.luv.couple.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.net.AccountSession
import com.luv.couple.net.EventSession
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.SeasonEvent
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@Composable
fun EventsPanel(
    modifier: Modifier = Modifier,
    onCoinsGranted: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cached by EventSession.state.collectAsStateWithLifecycle()
    var state by remember { mutableStateOf(cached) }
    var loading by remember { mutableStateOf(state == null) }
    var busyId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = state == null
        runCatching { LuvApiClient.fetchEvents() }
            .onSuccess {
                state = it
                loading = false
            }
            .onFailure {
                loading = false
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("📅", fontSize = 22.sp)
            Text(
                "Eventkalender",
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 20.sp
            )
        }
        Text(
            "Wiederkehrende Feste mit kleinen Sammel-Belohnungen — und etwas Schmuck in der App.",
            color = TextMuted,
            fontFamily = BodyFont,
            fontSize = 13.sp
        )

        when {
            loading -> Text("Lädt…", color = TextMuted, fontFamily = BodyFont)
            state == null -> Text(
                "Events gerade nicht erreichbar.",
                color = TextMuted,
                fontFamily = BodyFont
            )
            else -> {
                val active = state!!.active
                if (active.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(BgSoft)
                            .padding(16.dp)
                    ) {
                        Text(
                            "Gerade ist kein Event aktiv. Schau unter „Demnächst“, was als Nächstes kommt.",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    active.forEach { ev ->
                        EventHeroCard(
                            event = ev,
                            busy = busyId == ev.id,
                            onCollect = {
                                if (busyId != null) return@EventHeroCard
                                busyId = ev.id
                                scope.launch {
                                    runCatching { LuvApiClient.collectEvent(ev.id) }
                                        .onSuccess { result ->
                                            state = result.state
                                            if (result.coinsGranted > 0) {
                                                onCoinsGranted(result.coinsGranted)
                                                runCatching { LuvApiClient.me() }
                                                    .onSuccess { AccountSession.setAccount(it) }
                                            }
                                            val msg = buildString {
                                                if (result.coinsGranted > 0) {
                                                    append("+${result.coinsGranted} Coins")
                                                }
                                                if (!result.itemLabel.isNullOrBlank()) {
                                                    if (isNotEmpty()) append(" · ")
                                                    append("Item: ${result.itemLabel}")
                                                }
                                                if (isEmpty()) append("Gesammelt!")
                                            }
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                        .onFailure { err ->
                                            Toast.makeText(
                                                context,
                                                err.message ?: "Sammeln fehlgeschlagen",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    busyId = null
                                }
                            }
                        )
                    }
                }

                val upcoming = state!!.upcoming
                if (upcoming.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Demnächst",
                        color = TextPrimary,
                        fontFamily = DisplayFont,
                        fontSize = 16.sp
                    )
                    upcoming.forEach { ev ->
                        UpcomingEventRow(ev)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun EventHeroCard(
    event: SeasonEvent,
    busy: Boolean,
    onCollect: () -> Unit,
) {
    val accent = runCatching {
        Color(android.graphics.Color.parseColor(event.decor.accentHex))
    }.getOrDefault(AccentRose)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(0.28f), BgSoft, BgDeep.copy(0.35f))
                )
            )
            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(event.emoji, fontSize = 36.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    event.title,
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val win = listOfNotNull(event.windowStart, event.windowEnd)
                    .distinct()
                    .joinToString(" – ")
                if (win.isNotBlank()) {
                    Text(win, color = TextMuted, fontFamily = BodyFont, fontSize = 11.sp)
                }
            }
        }
        Text(
            event.description,
            color = TextPrimary.copy(0.92f),
            fontFamily = BodyFont,
            fontSize = 13.sp
        )
        if (event.hint.isNotBlank()) {
            Text(event.hint, color = TextMuted, fontFamily = BodyFont, fontSize = 12.sp)
        }
        val reward = event.rewardItem
        if (reward != null) {
            Text(
                if (event.itemGranted) {
                    "Belohnung erhalten: ${reward.emoji} ${reward.label}"
                } else {
                    "Ziel-Belohnung: ${reward.emoji} ${reward.label} (nach ${event.collectTarget}× Sammeln)"
                },
                color = AccentRose,
                fontFamily = BodyFont,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        val target = event.collectTarget.coerceAtLeast(1)
        val prog = event.progress.coerceIn(0, target)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(prog / target.toFloat())
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent)
            )
        }
        Text(
            "$prog / $target gesammelt · +${event.rewardCoinsPerCollect} Coins / Tag" +
                if (event.milestoneBonusCoins > 0) " · Ziel +${event.milestoneBonusCoins}" else "",
            color = TextMuted,
            fontFamily = BodyFont,
            fontSize = 11.sp
        )
        TextButton(
            onClick = onCollect,
            enabled = event.canCollect && !busy && !event.collectedToday,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (event.canCollect && !event.collectedToday) accent.copy(0.35f)
                    else Color.White.copy(0.06f)
                )
        ) {
            Text(
                when {
                    busy -> "…"
                    event.collectedToday -> "Heute schon gesammelt"
                    event.canCollect -> "Heute sammeln"
                    else -> "Nicht verfügbar"
                },
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun UpcomingEventRow(event: SeasonEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgSoft)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(event.emoji, fontSize = 22.sp, modifier = Modifier.size(28.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                event.title,
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val whenLabel = listOfNotNull(event.windowStart, event.windowEnd)
                .distinct()
                .joinToString(" – ")
                .ifBlank { "bald" }
            Text(whenLabel, color = TextMuted, fontFamily = BodyFont, fontSize = 11.sp)
        }
    }
}
