package com.luv.couple.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.lock.CanvasMemoryKeeper
import com.luv.couple.net.AccountSession
import com.luv.couple.net.EventContestFeedItem
import com.luv.couple.net.EventContestInfo
import com.luv.couple.net.EventContestWinner
import com.luv.couple.net.EventSession
import com.luv.couple.net.EventsState
import com.luv.couple.net.LuvApiClient
import com.luv.couple.data.AccountInfo
import com.luv.couple.net.EventShopItem
import com.luv.couple.net.SeasonEvent
import com.luv.couple.data.asCleanJsonString
import com.luv.couple.ui.ItemGlyph
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import kotlin.math.max

/** Server liefert oft YYYY-MM-DD — Anzeige als 29.07.2026 (Uhrzeit-Labels bleiben). */
private fun formatEventWhenPart(raw: String?): String? {
    val t = raw?.trim().orEmpty()
    if (t.isEmpty()) return null
    val ymd = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").matchEntire(t)
    if (ymd != null) {
        return "${ymd.groupValues[3]}.${ymd.groupValues[2]}.${ymd.groupValues[1]}"
    }
    // ISO-Datum am Anfang einer längeren Zeichenkette
    val iso = Regex("""^(\d{4})-(\d{2})-(\d{2})([T\s].*)?$""").matchEntire(t)
    if (iso != null && iso.groupValues[4].isBlank()) {
        return "${iso.groupValues[3]}.${iso.groupValues[2]}.${iso.groupValues[1]}"
    }
    return t
}

private fun formatEventWhenLabel(start: String?, end: String?, blank: String = "bald"): String {
    val parts = listOfNotNull(formatEventWhenPart(start), formatEventWhenPart(end)).distinct()
    return parts.joinToString(" – ").ifBlank { blank }
}

@Composable
fun EventsPanel(
    modifier: Modifier = Modifier,
    onCoinsGranted: (Int) -> Unit = {},
    onCreateEventLobby: (SeasonEvent) -> Unit = {},
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
                            },
                            onCreateLobby = { onCreateEventLobby(ev) },
                            onStateUpdated = { state = it },
                            onCoinsGranted = onCoinsGranted,
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
                    var previewEvent by remember { mutableStateOf<SeasonEvent?>(null) }
                    upcoming.forEach { ev ->
                        UpcomingEventRow(ev, onClick = { previewEvent = ev })
                    }
                    previewEvent?.let { ev ->
                        UpcomingEventPreviewDialog(
                            event = ev,
                            onDismiss = { previewEvent = null }
                        )
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
    onCreateLobby: () -> Unit = {},
    onStateUpdated: (EventsState) -> Unit = {},
    onCoinsGranted: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = runCatching {
        Color(android.graphics.Color.parseColor(event.decor.accentHex))
    }.getOrDefault(AccentRose)
    val contest = event.contest
    var showVote by remember { mutableStateOf(false) }
    var voteContest by remember(event.id, contest) { mutableStateOf(contest) }
    var selectedWinner by remember { mutableStateOf<EventContestWinner?>(null) }
    var claimBusy by remember { mutableStateOf(false) }

    val drawHint = event.eventPrompt.asCleanJsonString()
        ?: event.contest?.promptHint.asCleanJsonString()

    if (showVote && voteContest != null) {
        ContestVoteDialog(
            eventId = event.id,
            contest = voteContest!!,
            accent = accent,
            onDismiss = { showVote = false },
            onContestUpdated = { voteContest = it },
            onStateUpdated = onStateUpdated,
            onCoinsGranted = onCoinsGranted,
        )
    }

    selectedWinner?.let { winner ->
        ContestWinnerPopup(
            winner = winner,
            onDismiss = { selectedWinner = null }
        )
    }

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
                val win = formatEventWhenLabel(event.windowStart, event.windowEnd, blank = "")
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
        if (drawHint != null && (event.canCreateLobby || event.contestEnabled)) {
            Text(
                "Zeichne den Begriff „$drawHint“",
                color = accent,
                fontFamily = BodyFont,
                fontSize = 12.sp
            )
        }
        val rewards = event.resolvedRewardItems
        if (rewards.isNotEmpty()) {
            val labels = rewards.joinToString(", ") { it.label.ifBlank { it.itemId } }
            Text(
                if (event.itemGranted) {
                    "Belohnung erhalten: $labels"
                } else {
                    "Ziel-Belohnung: $labels (nach ${event.collectTarget}× Sammeln)"
                },
                color = AccentRose,
                fontFamily = BodyFont,
                fontSize = 12.sp,
                maxLines = 3,
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
        if (event.quests.isNotEmpty()) {
            Text(
                "Aufgaben",
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 14.sp
            )
            event.quests.forEach { q ->
                val qProg = q.progress.coerceIn(0, q.target)
                Text(
                    "${if (q.done) "✓" else "○"} ${q.title} ($qProg/${q.target})" +
                        if (q.rewardCoins > 0) " · +${q.rewardCoins}" else "",
                    color = if (q.done) accent else TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 12.sp
                )
                if (q.hint.isNotBlank() && !q.done) {
                    Text(q.hint, color = TextMuted.copy(0.85f), fontFamily = BodyFont, fontSize = 11.sp)
                }
            }
        }
        if (event.lobbyEnabled || event.contestEnabled) {
            Text(
                buildString {
                    if (event.lobbyEnabled) append("Event-Lobby bereit")
                    if (event.lobbyEnabled && event.contestEnabled) append(" · ")
                    if (event.contestEnabled) append("Wettbewerb aktiv")
                },
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 11.sp
            )
        }
        if (event.canCreateLobby) {
            TextButton(
                onClick = onCreateLobby,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(0.22f))
            ) {
                Text(
                    "Event-Lobby erstellen",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else if (event.lobbyEnabled && (event.lobbyCreated || !event.eventPrompt.isNullOrBlank())) {
            TextButton(
                onClick = onCreateLobby,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(0.22f))
            ) {
                Text(
                    "Event-Lobby öffnen",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (contest?.votingOpen == true) {
            Text(
                "Abstimmung läuft (24h nach Eventende)",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 12.sp
            )
            TextButton(
                onClick = {
                    scope.launch {
                        runCatching { LuvApiClient.fetchEventContest(event.id) }
                            .onSuccess { result ->
                                voteContest = result.contest
                                result.state?.let { onStateUpdated(it) }
                                showVote = true
                            }
                            .onFailure { err ->
                                Toast.makeText(
                                    context,
                                    err.message ?: "Abstimmung nicht verfügbar",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (contest.canVote) accent.copy(0.28f) else Color.White.copy(0.08f)
                    )
            ) {
                Text(
                    when {
                        contest.canVote && contest.votesRemaining > 0 ->
                            "Abstimmen · noch ${contest.votesRemaining}"
                        contest.votesRemaining <= 0 ->
                            "Bewertungslimit erreicht"
                        else -> "Zur Abstimmung"
                    },
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (contest != null && !contest.votingOpen && contest.winners.isNotEmpty()) {
            Text(
                "Gewinner",
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 14.sp
            )
            Text(
                "Tippe auf einen Platz, um das Bild zu sehen",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 12.sp
            )
            contest.winners.forEach { winner ->
                Text(
                    "${winner.place}. ${winner.nickname}",
                    color = accent,
                    fontFamily = BodyFont,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { selectedWinner = winner }
                        .padding(vertical = 4.dp)
                )
            }
        }
        if (contest?.claimablePrize != null && !contest.prizeClaimed) {
            val prize = contest.claimablePrize
            Text(
                "Dein Platz: ${prize.place} · ${prize.coins} Coins" +
                    if (prize.grantMedal || prize.place == 1) " · 🥇" else "",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 12.sp
            )
            TextButton(
                onClick = {
                    if (claimBusy) return@TextButton
                    claimBusy = true
                    scope.launch {
                        runCatching { LuvApiClient.claimContestPrize(event.id) }
                            .onSuccess { result ->
                                onStateUpdated(result.state)
                                if (result.coinsGranted > 0) {
                                    onCoinsGranted(result.coinsGranted)
                                    runCatching { LuvApiClient.me() }
                                        .onSuccess { AccountSession.setAccount(it) }
                                }
                                Toast.makeText(
                                    context,
                                    if (result.coinsGranted > 0) {
                                        "+${result.coinsGranted} Coins · Platz ${result.place}"
                                    } else {
                                        "Belohnung abgeholt"
                                    },
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .onFailure { err ->
                                Toast.makeText(
                                    context,
                                    err.message ?: "Abholen fehlgeschlagen",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        claimBusy = false
                    }
                },
                enabled = !claimBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(0.35f))
            ) {
                Text(
                    if (claimBusy) "…" else "Belohnung abholen",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        EventShopTile(
            event = event,
            accent = accent,
            onCoinsGranted = onCoinsGranted,
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
private fun ContestVoteDialog(
    eventId: String,
    contest: EventContestInfo,
    accent: Color,
    onDismiss: () -> Unit,
    onContestUpdated: (EventContestInfo) -> Unit,
    onStateUpdated: (EventsState) -> Unit,
    onCoinsGranted: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember(contest) { mutableStateOf(contest) }
    var voteBusy by remember { mutableStateOf(false) }
    val feed = current.feedItem

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDeep)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (feed == null) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                if (current.votesRemaining <= 0) {
                                    "Bewertungslimit erreicht (${current.votesMax})."
                                } else {
                                    "Keine weiteren Beiträge zum Bewerten."
                                },
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Noch ${current.votesRemaining} von ${current.votesMax}",
                                color = accent,
                                fontFamily = DisplayFont,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Text(
                        "Noch ${current.votesRemaining} Bewertungen",
                        color = accent,
                        fontFamily = DisplayFont,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ContestEntryImage(
                        item = feed,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(BgSoft)
                    )
                    Text(
                        feed.prompt?.trim().orEmpty().ifBlank { "Zeichnung" },
                        color = TextPrimary,
                        fontFamily = DisplayFont,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "👎",
                            fontSize = 36.sp,
                            modifier = Modifier
                                .clickable(enabled = !voteBusy) {
                                    submitVote(
                                        scope, context, eventId, feed.entryId, -1, voteBusySetter = {
                                            voteBusy = it
                                        },
                                        onSuccess = { result ->
                                            current = result.contest
                                            onContestUpdated(result.contest)
                                            result.state?.let { onStateUpdated(it) }
                                            scope.launch {
                                                runCatching { LuvApiClient.fetchEvents() }
                                                    .onSuccess { onStateUpdated(it) }
                                            }
                                            if (result.voterCoins > 0) {
                                                onCoinsGranted(result.voterCoins)
                                            }
                                        }
                                    )
                                }
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                        Text(
                            "👍",
                            fontSize = 36.sp,
                            modifier = Modifier
                                .clickable(enabled = !voteBusy) {
                                    submitVote(
                                        scope, context, eventId, feed.entryId, 1, voteBusySetter = {
                                            voteBusy = it
                                        },
                                        onSuccess = { result ->
                                            current = result.contest
                                            onContestUpdated(result.contest)
                                            result.state?.let { onStateUpdated(it) }
                                            scope.launch {
                                                runCatching { LuvApiClient.fetchEvents() }
                                                    .onSuccess { onStateUpdated(it) }
                                            }
                                            if (result.voterCoins > 0) {
                                                onCoinsGranted(result.voterCoins)
                                            }
                                        }
                                    )
                                }
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Zurück", color = TextMuted, fontFamily = BodyFont, fontSize = 15.sp)
                    }
                    if (feed != null) {
                        TextButton(
                            onClick = {
                                if (voteBusy) return@TextButton
                                voteBusy = true
                                scope.launch {
                                    runCatching {
                                        LuvApiClient.reportContestEntry(eventId, feed.entryId)
                                    }
                                        .onSuccess { contestFromReport ->
                                            Toast.makeText(context, "Gemeldet", Toast.LENGTH_SHORT).show()
                                            if (contestFromReport != null) {
                                                current = contestFromReport
                                                onContestUpdated(contestFromReport)
                                            } else {
                                                runCatching { LuvApiClient.fetchEventContest(eventId) }
                                                    .onSuccess { result ->
                                                        current = result.contest
                                                        onContestUpdated(result.contest)
                                                        result.state?.let { onStateUpdated(it) }
                                                    }
                                            }
                                        }
                                        .onFailure { err ->
                                            Toast.makeText(
                                                context,
                                                err.message ?: "Melden fehlgeschlagen",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    voteBusy = false
                                }
                            },
                            enabled = !voteBusy
                        ) {
                            Text("Melden", color = Color(0xFFFF5A6A), fontFamily = BodyFont, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun submitVote(
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    eventId: String,
    entryId: String,
    value: Int,
    voteBusySetter: (Boolean) -> Unit,
    onSuccess: (LuvApiClient.EventContestVoteResult) -> Unit,
) {
    voteBusySetter(true)
    scope.launch {
        runCatching { LuvApiClient.voteContest(eventId, entryId, value) }
            .onSuccess(onSuccess)
            .onFailure { err ->
                Toast.makeText(
                    context,
                    err.message ?: "Stimme fehlgeschlagen",
                    Toast.LENGTH_SHORT
                ).show()
            }
        voteBusySetter(false)
    }
}

/** Abstimmung: Bitmaps im Speicher halten (neues OkHttpClient pro Bild war der Bremsklotz). */
private object ContestImageCache {
    private const val MAX_SIDE = 1080
    private val cache = object : LruCache<String, Bitmap>(12) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    fun get(key: String): Bitmap? = synchronized(cache) { cache.get(key) }

    fun put(key: String, bitmap: Bitmap) {
        synchronized(cache) { cache.put(key, bitmap) }
    }

    fun load(entryId: String, imageUrl: String): Bitmap? {
        val key = entryId.ifBlank { imageUrl }
        get(key)?.let { return it }
        val abs = CanvasMemoryKeeper.absoluteImageUrl(imageUrl)
        val req = Request.Builder().url(abs).get().build()
        val bytes = LuvApiClient.httpClient().newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.bytes()
        } ?: return null
        val bmp = decodeSampled(bytes, MAX_SIDE) ?: return null
        put(key, bmp)
        return bmp
    }

    private fun decodeSampled(bytes: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        val largest = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        while (largest / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}

@Composable
private fun ContestEntryImage(
    item: EventContestFeedItem,
    modifier: Modifier = Modifier,
) {
    val cacheKey = item.entryId.ifBlank { item.imageUrl.orEmpty() }
    var bitmap by remember(cacheKey) {
        mutableStateOf(ContestImageCache.get(cacheKey))
    }
    var loading by remember(cacheKey) { mutableStateOf(bitmap == null) }

    LaunchedEffect(item.entryId, item.imageUrl) {
        val url = item.imageUrl
        if (url.isNullOrBlank()) {
            bitmap = null
            loading = false
            return@LaunchedEffect
        }
        ContestImageCache.get(cacheKey)?.let {
            bitmap = it
            loading = false
            return@LaunchedEffect
        }
        loading = true
        bitmap = withContext(Dispatchers.IO) {
            runCatching { ContestImageCache.load(item.entryId, url) }.getOrNull()
        }
        loading = false
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            loading -> Text("Lädt…", color = TextMuted, fontFamily = BodyFont)
            bitmap != null -> Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = item.prompt,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            else -> Text("Kein Bild", color = TextMuted, fontFamily = BodyFont)
        }
    }
}

@Composable
private fun ContestWinnerPopup(
    winner: EventContestWinner,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDeep.copy(0.88f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(BgSoft)
                .clickable(enabled = false) {}
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    "✕",
                    color = TextMuted,
                    fontSize = 20.sp,
                    modifier = Modifier.clickable(onClick = onDismiss)
                )
            }
            Text(
                "${winner.place}. ${winner.nickname}",
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 18.sp
            )
            ContestEntryImage(
                item = EventContestFeedItem(
                    entryId = winner.entryId,
                    nickname = winner.nickname,
                    prompt = winner.prompt,
                    imageUrl = winner.imageUrl,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgDeep)
            )
            Text(
                winner.prompt?.trim().orEmpty().ifBlank { "Zeichnung" },
                color = TextPrimary,
                fontFamily = BodyFont,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
        }
        }
    }
}

@Composable
private fun UpcomingEventRow(event: SeasonEvent, onClick: () -> Unit) {
    val accent = remember(event.decor.accentHex) {
        runCatching { Color(android.graphics.Color.parseColor(event.decor.accentHex)) }
            .getOrDefault(AccentRose)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(accent.copy(0.22f), BgSoft)
                )
            )
            .border(1.dp, accent.copy(0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
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
            Text(
                formatEventWhenLabel(event.windowStart, event.windowEnd),
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 11.sp
            )
        }
        Text("›", color = accent, fontFamily = DisplayFont, fontSize = 18.sp)
    }
}

@Composable
private fun UpcomingEventPreviewDialog(
    event: SeasonEvent,
    onDismiss: () -> Unit,
) {
    val accent = remember(event.decor.accentHex) {
        runCatching { Color(android.graphics.Color.parseColor(event.decor.accentHex)) }
            .getOrDefault(AccentRose)
    }
    val ornament = when (event.decor.ornaments) {
        "wreath" -> "🎄"
        "hearts" -> "💕"
        "spark" -> "✨"
        else -> event.emoji.takeIf { it.isNotBlank() } ?: "🎉"
    }
    val body = when {
        event.description.isNotBlank() -> event.description
        event.hint.isNotBlank() -> event.hint
        else -> "Bald startet dieses Event — Farben und Atmosphäre siehst du hier schon."
    }
    val shopItems = event.shopItems
    var peekItem by remember { mutableStateOf<EventShopItem?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF120E18), BgDeep, Color(0xFF0A1520))
                    )
                )
        ) {
            // Event-Akzent + Partikel über opaker Basis — Menü scheint nicht durch
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accent.copy(0.28f))
            )
            com.luv.couple.ui.MenuAmbientBackground(
                eventDecor = event.decor.copy(
                    particles = event.decor.particles.takeIf { it != "none" } ?: "sparkle",
                    intensity = event.decor.intensity.coerceAtLeast(0.55f),
                ),
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                BgDeep.copy(0.15f),
                                BgDeep.copy(0.35f),
                                BgDeep.copy(0.55f),
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(accent.copy(0.35f))
                            .border(1.dp, accent.copy(0.55f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(event.emoji, fontSize = 30.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            event.title,
                            color = TextPrimary,
                            fontFamily = DisplayFont,
                            fontSize = 24.sp
                        )
                        val whenLabel = formatEventWhenLabel(
                            event.windowStart,
                            event.windowEnd,
                            blank = ""
                        )
                        if (whenLabel.isNotBlank()) {
                            Text(
                                whenLabel,
                                color = accent,
                                fontFamily = DisplayFont,
                                fontSize = 14.sp
                            )
                        }
                    }
                    Text(ornament, fontSize = 24.sp)
                    TextButton(onClick = onDismiss) {
                        Text("✕", color = TextPrimary, fontFamily = DisplayFont, fontSize = 18.sp)
                    }
                }
                event.decor.bannerText.takeIf { it.isNotBlank() }?.let { banner ->
                    Text(
                        banner,
                        color = TextPrimary,
                        fontFamily = DisplayFont,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent.copy(0.28f))
                            .border(1.dp, accent.copy(0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        body,
                        color = TextPrimary.copy(0.9f),
                        fontFamily = BodyFont,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(BgDeep.copy(0.42f))
                            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                    val rewards = event.resolvedRewardItems
                    if (rewards.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(accent.copy(0.16f))
                                .border(1.dp, accent.copy(0.3f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Event-Belohnung",
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = 11.sp
                            )
                            rewards.forEach { reward ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ItemGlyph(
                                        id = reward.itemId.ifBlank { reward.emoji },
                                        fontSize = 28.sp
                                    )
                                    Text(
                                        reward.label.ifBlank { reward.itemId },
                                        color = TextPrimary,
                                        fontFamily = DisplayFont,
                                        fontSize = 16.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                    if (shopItems.isNotEmpty()) {
                        Text(
                            "Event-Items",
                            color = TextPrimary,
                            fontFamily = DisplayFont,
                            fontSize = 16.sp
                        )
                        Text(
                            "Tippe für Preis & Vorschau · nur während dem Event im Shop",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 12.sp
                        )
                        val cols = 4
                        shopItems.chunked(cols).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowItems.forEach { item ->
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(BgSoft.copy(0.92f))
                                            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                                            .clickable { peekItem = item }
                                            .padding(vertical = 12.dp, horizontal = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(BgDeep.copy(0.45f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            EventShopItemGlyph(item, event.emoji, 28.sp)
                                        }
                                        Text(
                                            item.label,
                                            color = TextPrimary,
                                            fontFamily = BodyFont,
                                            fontSize = 11.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                repeat(cols - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(0.3f))
                ) {
                    Text(
                        "Schließen",
                        color = TextPrimary,
                        fontFamily = DisplayFont,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        peekItem?.let { item ->
            EventItemPeekDialog(
                item = item,
                accent = accent,
                eventEmoji = event.emoji,
                onDismiss = { peekItem = null }
            )
        }
    }
}

/** Grobe €-Schätzung aus Coin-Pack „Handvoll“ (60 Coins ≈ 2,99 €). */
private fun coinsToApproxEurLabel(coins: Int): String {
    if (coins <= 0) return "0,00 €"
    val eur = coins * 2.99 / 60.0
    val cents = kotlin.math.round(eur * 100.0).toInt()
    val whole = cents / 100
    val frac = cents % 100
    return "$whole,${frac.toString().padStart(2, '0')} €"
}

@Composable
private fun EventItemPeekDialog(
    item: EventShopItem,
    accent: Color,
    eventEmoji: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDeep.copy(0.9f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(listOf(accent.copy(0.45f), BgSoft, BgDeep))
                )
                .border(1.dp, accent.copy(0.4f), RoundedCornerShape(22.dp))
                .clickable(enabled = false) {}
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(BgDeep.copy(0.5f)),
                contentAlignment = Alignment.Center
            ) {
                EventShopItemGlyph(item, eventEmoji, 48.sp)
            }
            Text(
                item.kindLabel,
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 12.sp
            )
            Text(
                item.label,
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 22.sp,
                textAlign = TextAlign.Center
            )
            Text(
                "${item.priceCoins} Coins",
                color = accent,
                fontFamily = DisplayFont,
                fontSize = 20.sp
            )
            Text(
                "≈ ${coinsToApproxEurLabel(item.priceCoins)}",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 14.sp
            )
            Text(
                "Nur während dem Event im Itemshop & Event-Shop",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onDismiss) {
                Text("Schließen", color = accent, fontFamily = DisplayFont)
            }
        }
        }
    }
}

@Composable
private fun EventShopTile(
    event: SeasonEvent,
    accent: Color,
    onCoinsGranted: (Int) -> Unit = {},
    previewOnly: Boolean = false,
) {
    val items = event.shopItems
    if (items.isEmpty()) return

    var showShop by remember { mutableStateOf(false) }
    val preview = items.take(2)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(0.18f))
            .border(1.dp, accent.copy(0.35f), RoundedCornerShape(14.dp))
            .clickable(enabled = !previewOnly) { showShop = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            preview.forEach { item ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgDeep.copy(0.55f))
                        .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    EventShopItemGlyph(item, eventEmoji = event.emoji, fontSize = 22.sp)
                }
            }
            if (items.size > 2) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgDeep.copy(0.4f))
                        .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+${items.size - 2}", color = TextMuted, fontFamily = DisplayFont, fontSize = 12.sp)
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Event-Shop",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 11.sp
            )
            Text(
                if (items.size == 1) items.first().label
                else "${items.size} Items · tippen",
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (previewOnly) "Nur während dem Event im Shop"
                else "Kaufen hier oder im Itemshop",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 11.sp
            )
        }
        if (!previewOnly) {
            Text("›", color = accent, fontFamily = DisplayFont, fontSize = 20.sp)
        }
    }

    if (showShop && !previewOnly) {
        EventShopBuyDialog(
            event = event,
            accent = accent,
            onDismiss = { showShop = false },
            onCoinsGranted = onCoinsGranted,
        )
    }
}

@Composable
private fun EventShopItemGlyph(
    item: EventShopItem,
    eventEmoji: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    if (item.hasImage || item.itemId.startsWith("img_") || item.kind == "pets" && item.itemId.startsWith("img")) {
        com.luv.couple.ui.ItemGlyph(id = item.itemId, fontSize = fontSize)
    } else if (item.kind == "themes") {
        Text(item.emoji.ifBlank { "🖼️" }, fontSize = fontSize)
    } else {
        Text(item.emoji.ifBlank { eventEmoji }.ifBlank { "🎁" }, fontSize = fontSize)
    }
}

@Composable
private fun EventShopBuyDialog(
    event: SeasonEvent,
    accent: Color,
    onDismiss: () -> Unit,
    onCoinsGranted: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account by AccountSession.account.collectAsStateWithLifecycle()
    var busyId by remember { mutableStateOf<String?>(null) }
    val items = event.shopItems

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDeep.copy(0.9f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(listOf(accent.copy(0.4f), BgSoft, BgDeep))
                )
                .border(1.dp, accent.copy(0.4f), RoundedCornerShape(22.dp))
                .clickable(enabled = false) {}
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(event.emoji, fontSize = 28.sp)
                Spacer(modifier = Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Event-Shop",
                        color = TextPrimary,
                        fontFamily = DisplayFont,
                        fontSize = 20.sp
                    )
                    Text(
                        event.title,
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "${account?.coins ?: 0} 💰",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 14.sp
                )
            }
            Text(
                "Nur während dem Event · danach weg aus dem Shop (Besitz bleibt).",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 12.sp
            )
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEach { item ->
                    val busy = busyId == item.itemId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(BgDeep.copy(0.45f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            EventShopItemGlyph(item, event.emoji, 26.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.kindLabel,
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = 11.sp
                            )
                            Text(
                                item.label,
                                color = TextPrimary,
                                fontFamily = DisplayFont,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${item.priceCoins} Coins",
                                color = accent,
                                fontFamily = BodyFont,
                                fontSize = 12.sp
                            )
                        }
                        TextButton(
                            onClick = {
                                if (busyId != null) return@TextButton
                                busyId = item.itemId
                                scope.launch {
                                    runCatching {
                                        when (item.kind) {
                                            "pets" -> LuvApiClient.buyPet(item.itemId).first
                                            "stickers" -> LuvApiClient.buySticker(item.itemId).first
                                            "emojis" -> LuvApiClient.buyEmoji(item.itemId).first
                                            "themes" -> LuvApiClient.buyTheme(item.itemId)
                                            else -> error("Unbekannte Kategorie")
                                        }
                                    }.onSuccess { acc: AccountInfo ->
                                        AccountSession.setAccount(acc)
                                        Toast.makeText(
                                            context,
                                            "${item.label} gekauft",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }.onFailure { err ->
                                        Toast.makeText(
                                            context,
                                            err.message ?: "Kauf fehlgeschlagen",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    busyId = null
                                }
                            },
                            enabled = busyId == null,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (busy) Color.White.copy(0.08f) else accent.copy(0.35f)
                                )
                        ) {
                            Text(
                                if (busy) "…" else "Kaufen",
                                color = TextPrimary,
                                fontFamily = DisplayFont,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Schließen", color = accent, fontFamily = DisplayFont)
            }
        }
        }
    }
}
