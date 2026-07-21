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
import com.luv.couple.net.SeasonEvent
import com.luv.couple.data.asCleanJsonString
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

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
        if (drawHint != null && (event.canCreateLobby || event.contestEnabled)) {
            Text(
                "Zeichne den Begriff „$drawHint“",
                color = accent,
                fontFamily = BodyFont,
                fontSize = 12.sp
            )
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
                enabled = contest.canVote,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (contest.canVote) accent.copy(0.28f) else Color.White.copy(0.06f)
                    )
            ) {
                Text(
                    when {
                        contest.canVote && contest.votesRemaining > 0 ->
                            "Abstimmen · noch ${contest.votesRemaining}"
                        contest.votesRemaining <= 0 ->
                            "Bewertungslimit erreicht"
                        else -> "Abstimmung — keine Beiträge mehr"
                    },
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (contest?.prizesReady == true && contest.winners.isNotEmpty()) {
            Text(
                "Gewinner",
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 14.sp
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

@Composable
private fun ContestEntryImage(
    item: EventContestFeedItem,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(item.entryId, item.imageUrl) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(item.entryId) { mutableStateOf(true) }

    LaunchedEffect(item.entryId, item.imageUrl) {
        loading = true
        val url = item.imageUrl
        bitmap = if (url.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val abs = CanvasMemoryKeeper.absoluteImageUrl(url)
                    val client = OkHttpClient.Builder()
                        .connectTimeout(20, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build()
                    val req = Request.Builder().url(abs).get().build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@runCatching null
                        resp.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
                    }
                }.getOrNull()
            }
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
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(BgSoft)
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
            val whenLabel = listOfNotNull(event.windowStart, event.windowEnd)
                .distinct()
                .joinToString(" – ")
                .ifBlank { "bald" }
            Text(whenLabel, color = TextMuted, fontFamily = BodyFont, fontSize = 11.sp)
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
    val reward = event.rewardItem
    val shopPet = event.shopPet
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(accent.copy(0.42f), BgSoft, BgDeep.copy(0.55f))
                    )
                )
                .border(1.dp, accent.copy(0.45f), RoundedCornerShape(22.dp))
        ) {
            com.luv.couple.ui.MenuAmbientBackground(
                eventDecor = event.decor,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(22.dp))
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(event.emoji, fontSize = 32.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            event.title,
                            color = TextPrimary,
                            fontFamily = DisplayFont,
                            fontSize = 22.sp
                        )
                        val whenLabel = listOfNotNull(event.windowStart, event.windowEnd)
                            .distinct()
                            .joinToString(" – ")
                        if (whenLabel.isNotBlank()) {
                            Text(whenLabel, color = TextMuted, fontFamily = BodyFont, fontSize = 12.sp)
                        }
                    }
                    Text(ornament, fontSize = 22.sp)
                }
                event.decor.bannerText.takeIf { it.isNotBlank() }?.let { banner ->
                    Text(
                        banner,
                        color = accent,
                        fontFamily = DisplayFont,
                        fontSize = 15.sp
                    )
                }
                Text(
                    body,
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                if (reward != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgDeep.copy(0.55f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(reward.emoji.ifBlank { "🎁" }, fontSize = 22.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Event-Belohnung",
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = 11.sp
                            )
                            Text(
                                reward.label.ifBlank { reward.itemId },
                                color = TextPrimary,
                                fontFamily = DisplayFont,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                if (shopPet != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent.copy(0.18f))
                            .border(1.dp, accent.copy(0.35f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (shopPet.hasImage || shopPet.itemId.startsWith("img_")) {
                            com.luv.couple.ui.ItemGlyph(
                                id = shopPet.itemId,
                                fontSize = 28.sp
                            )
                        } else {
                            Text(shopPet.emoji.ifBlank { event.emoji }, fontSize = 26.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Event-Begleiter",
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = 11.sp
                            )
                            Text(
                                shopPet.label,
                                color = TextPrimary,
                                fontFamily = DisplayFont,
                                fontSize = 14.sp
                            )
                            Text(
                                "Nur während dem Event · ${shopPet.priceCoins} Coins · danach weg aus dem Shop",
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Schließen", color = accent, fontFamily = DisplayFont)
                }
            }
        }
    }
}
