package com.luv.couple.ui.wedding

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.net.CeremonyRefreshBus
import com.luv.couple.net.LuvApiClient
import com.luv.couple.ui.ItemGlyph
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.MaleBlue
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Gold = Color(0xFFFFD54F)

fun formatCeremonyAt(ms: Long): String {
    if (ms <= 0L) return "—"
    return SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.GERMANY).format(Date(ms))
}

fun formatCountdown(ms: Long): String {
    val rem = ms.coerceAtLeast(0L)
    val d = rem / (24 * 60 * 60 * 1000L)
    val h = (rem % (24 * 60 * 60 * 1000L)) / (60 * 60 * 1000L)
    val m = (rem % (60 * 60 * 1000L)) / 60_000L
    val s = (rem % 60_000L) / 1000L
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

@Composable
fun WeddingPresenceDialog(
    onDismiss: () -> Unit,
    onScheduled: (lobbyCode: String?) -> Unit,
    onShareRemind: (text: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ceremony by remember { mutableStateOf<LuvApiClient.CeremonyInfo?>(null) }
    var marriage by remember { mutableStateOf<LuvApiClient.MarriageInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showSchedule by remember { mutableStateOf(false) }
    var showBooking by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var leftPresence by remember { mutableStateOf(false) }

    fun lobbyCodeOf(
        m: LuvApiClient.MarriageInfo?,
        c: LuvApiClient.CeremonyInfo?,
    ): String? =
        c?.ceremonyLobbyCode?.takeIf { it.isNotBlank() }
            ?: m?.ceremonyLobbyCode?.takeIf { it.isNotBlank() }

    fun closePresence() {
        if (!leftPresence) {
            leftPresence = true
            scope.launch { runCatching { LuvApiClient.ceremonyPresenceLeave() } }
        }
        onDismiss()
    }

    val ceremonyRev by com.luv.couple.net.CeremonyRefreshBus.revision.collectAsStateWithLifecycle()
    LaunchedEffect(Unit, ceremonyRev) {
        runCatching {
            LuvApiClient.ceremonyPresence("presence")
            LuvApiClient.fetchCeremony()
        }.onSuccess {
            ceremony = it.ceremony
            marriage = it.marriage
            loaded = true
            val code = lobbyCodeOf(it.marriage, it.ceremony)
            if (!code.isNullOrBlank() && it.marriage?.status == "ceremony_scheduled") {
                onScheduled(code)
                return@LaunchedEffect
            }
            if (it.ceremony?.booking?.active == true) {
                showBooking = true
            }
        }
    }
    // Keepalive: Anwesenheit frisch halten, Partner sieht 2/2 live
    LaunchedEffect(loaded, showSchedule, showBooking) {
        if (!loaded || showSchedule || showBooking) return@LaunchedEffect
        while (true) {
            delay(12_000)
            runCatching { LuvApiClient.ceremonyPresence("presence") }
                .onSuccess { ceremony = it }
        }
    }

    if (showBooking) {
        WeddingBookingDialog(
            onDismiss = { closePresence() },
            onBooked = { code ->
                showBooking = false
                leftPresence = true
                if (!code.isNullOrBlank()) onScheduled(code)
            }
        )
        return
    }

    if (showSchedule) {
        WeddingScheduleDialog(
            onDismiss = { showSchedule = false },
            onScheduled = { code ->
                showSchedule = false
                leftPresence = true
                if (!code.isNullOrBlank()) onScheduled(code)
            }
        )
        return
    }

    if (!loaded) {
        Dialog(
            onDismissRequest = { if (!busy) closePresence() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(BgSoft)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Hochzeit wird geladen…",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 14.sp,
                )
            }
        }
        return
    }

    val c = ceremony
    Dialog(
        onDismissRequest = { if (!busy) closePresence() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2A1F28), BgDeep)))
                .border(1.dp, Gold.copy(0.45f), RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Hochzeit", color = TextPrimary, fontFamily = DisplayFont, fontSize = 24.sp)
            Text(
                "Anwesenheit Brautpaar",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 13.sp
            )
            val present = c?.couplePresent ?: 0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "$present/2",
                    color = Gold,
                    fontFamily = DisplayFont,
                    fontSize = 28.sp
                )
                if (present >= 2) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E7D32)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓", color = Color.White, fontSize = 20.sp)
                    }
                }
            }
            if (present < 2) {
                Text(
                    "${c?.partnerNickname ?: "Dein:e Verlobte:r"} ist nicht anwesend.",
                    color = AccentRose,
                    fontFamily = BodyFont,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                TextButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { LuvApiClient.ceremonyRemind() }
                                .onSuccess { onShareRemind(it.shareText) }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        it.message ?: "Erinnern fehlgeschlagen",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            busy = false
                        }
                    },
                    enabled = !busy
                ) {
                    Text("Erinnern", color = MaleBlue, fontFamily = DisplayFont)
                }
            } else {
                val mine = c?.startConfirmMine == true
                TextButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { LuvApiClient.ceremonyStartConfirm() }
                                .onSuccess { ceremony = it }
                            busy = false
                        }
                    },
                    enabled = !busy && !mine
                ) {
                    Text(
                        if (mine) "Wartest auf Partner…" else "Zur Hochzeit starten",
                        color = Gold,
                        fontFamily = DisplayFont
                    )
                }
                if (c?.startConfirmReady == true) {
                    TextButton(
                        onClick = { showSchedule = true },
                        enabled = !busy
                    ) {
                        Text("Datum & Zeit wählen", color = AccentRose, fontFamily = DisplayFont)
                    }
                }
            }
            TextButton(onClick = { closePresence() }) {
                Text("Schließen", color = TextMuted, fontFamily = BodyFont)
            }
        }
    }
}

@Composable
fun WeddingScheduleDialog(
    onDismiss: () -> Unit,
    onScheduled: (String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var ceremony by remember { mutableStateOf<LuvApiClient.CeremonyInfo?>(null) }
    val cal = remember {
        Calendar.getInstance().apply {
            add(Calendar.MINUTE, 15)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    var label by remember {
        mutableStateOf(formatCeremonyAt(cal.timeInMillis))
    }

    var showBooking by remember { mutableStateOf(false) }

    fun refreshFrom(bundle: LuvApiClient.CeremonyBundle) {
        ceremony = bundle.ceremony
        val code = bundle.ceremony?.ceremonyLobbyCode?.takeIf { it.isNotBlank() }
            ?: bundle.marriage?.ceremonyLobbyCode?.takeIf { it.isNotBlank() }
        if (!code.isNullOrBlank() && bundle.marriage?.status == "ceremony_scheduled") {
            Toast.makeText(context, "Hochzeit gebucht", Toast.LENGTH_SHORT).show()
            onScheduled(code)
            return
        }
        if (bundle.ceremony?.booking?.active == true) {
            showBooking = true
        }
    }

    val scheduleRev by CeremonyRefreshBus.revision.collectAsStateWithLifecycle()
    LaunchedEffect(Unit, scheduleRev) {
        runCatching {
            LuvApiClient.ceremonyPresence("presence")
            LuvApiClient.fetchCeremony()
        }.onSuccess { bundle ->
            ceremony = bundle.ceremony
            val code = bundle.ceremony?.ceremonyLobbyCode?.takeIf { it.isNotBlank() }
                ?: bundle.marriage?.ceremonyLobbyCode?.takeIf { it.isNotBlank() }
            if (!code.isNullOrBlank() && bundle.marriage?.status == "ceremony_scheduled") {
                onScheduled(code)
                return@LaunchedEffect
            }
            if (bundle.ceremony?.booking?.active == true) {
                showBooking = true
            }
        }
    }

    if (showBooking) {
        WeddingBookingDialog(
            onDismiss = onDismiss,
            onBooked = { code ->
                showBooking = false
                if (!code.isNullOrBlank()) onScheduled(code)
            }
        )
        return
    }

    val proposals = ceremony?.timeProposals.orEmpty()
    val matches = ceremony?.matchingProposalAts.orEmpty()

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(BgSoft)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Hochzeit planen", color = TextPrimary, fontFamily = DisplayFont, fontSize = 20.sp)
            Text(
                "Zeit vorschlagen — beide sehen die Vorschläge. Termin gilt erst nach Annehmen.",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Text(label, color = Gold, fontFamily = DisplayFont, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    cal.add(Calendar.MINUTE, 10)
                    label = formatCeremonyAt(cal.timeInMillis)
                }) { Text("+10 Min", color = TextPrimary) }
                TextButton(onClick = {
                    cal.add(Calendar.MINUTE, 30)
                    label = formatCeremonyAt(cal.timeInMillis)
                }) { Text("+30 Min", color = TextPrimary) }
                TextButton(onClick = {
                    cal.add(Calendar.HOUR_OF_DAY, 1)
                    label = formatCeremonyAt(cal.timeInMillis)
                }) { Text("+1 Std", color = TextPrimary) }
                TextButton(onClick = {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    label = formatCeremonyAt(cal.timeInMillis)
                }) { Text("+1 Tag", color = TextPrimary) }
            }
            TextButton(
                onClick = {
                    busy = true
                    scope.launch {
                        runCatching { LuvApiClient.ceremonyPropose(cal.timeInMillis) }
                            .onSuccess {
                                ceremony = it.ceremony
                                Toast.makeText(context, "Vorschlag gesendet", Toast.LENGTH_SHORT)
                                    .show()
                            }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "Vorschlagen fehlgeschlagen",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        busy = false
                    }
                },
                enabled = !busy
            ) {
                Text("Vorschlagen", color = AccentRose, fontFamily = DisplayFont)
            }

            if (matches.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x332E7D32))
                        .border(1.dp, Gold.copy(0.5f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Treffer — gleiche Zeit",
                        color = Gold,
                        fontFamily = DisplayFont,
                        fontSize = 14.sp
                    )
                    matches.forEach { at ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                formatCeremonyAt(at),
                                color = TextPrimary,
                                fontFamily = BodyFont,
                                fontSize = 14.sp
                            )
                            TextButton(
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        runCatching { LuvApiClient.ceremonyProposeAccept(at) }
                                            .onSuccess { refreshFrom(it) }
                                            .onFailure {
                                                Toast.makeText(
                                                    context,
                                                    it.message ?: "Festlegen fehlgeschlagen",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        busy = false
                                    }
                                },
                                enabled = !busy
                            ) {
                                Text("Gemeinsam festlegen", color = Gold, fontFamily = DisplayFont)
                            }
                        }
                    }
                }
            }

            if (proposals.isEmpty()) {
                Text(
                    "Noch keine Vorschläge — tippt Zeiten und „Vorschlagen“.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    "Vorschläge",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                proposals.forEach { p ->
                    val isMatch = matches.contains(p.ceremonyAt)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isMatch) Color(0x222E7D32) else Color(0x22000000))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (p.mine) "Du" else p.nickname,
                                color = if (p.mine) MaleBlue else AccentRose,
                                fontFamily = DisplayFont,
                                fontSize = 13.sp
                            )
                            Text(
                                formatCeremonyAt(p.ceremonyAt),
                                color = TextPrimary,
                                fontFamily = BodyFont,
                                fontSize = 14.sp
                            )
                        }
                        if (p.mine) {
                            TextButton(
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        runCatching {
                                            LuvApiClient.ceremonyProposeWithdraw(p.ceremonyAt)
                                        }
                                            .onSuccess { ceremony = it.ceremony }
                                            .onFailure {
                                                Toast.makeText(
                                                    context,
                                                    it.message ?: "Zurücknehmen fehlgeschlagen",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        busy = false
                                    }
                                },
                                enabled = !busy
                            ) {
                                Text("Zurück", color = TextMuted, fontSize = 12.sp)
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        runCatching {
                                            LuvApiClient.ceremonyProposeAccept(p.ceremonyAt)
                                        }
                                            .onSuccess { refreshFrom(it) }
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
                                Text("Annehmen", color = Gold, fontFamily = DisplayFont)
                            }
                        }
                    }
                }
            }

            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Abbrechen", color = TextMuted)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeddingGatheringDialog(
    onDismiss: () -> Unit,
    onEnterAltar: () -> Unit,
    onShareRemind: (String) -> Unit,
    isCouple: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ceremony by remember { mutableStateOf<LuvApiClient.CeremonyInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var floatEmoji by remember { mutableStateOf<String?>(null) }
    var floatEmojiToken by remember { mutableLongStateOf(0L) }

    val gatherRev by CeremonyRefreshBus.revision.collectAsStateWithLifecycle()
    LaunchedEffect(Unit, gatherRev) {
        runCatching { LuvApiClient.ceremonyPresence("gathering") }
            .onSuccess { ceremony = it }
    }
    // Presence-TTL: seltenes Keepalive, kein 2s-Spam
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            runCatching { LuvApiClient.ceremonyPresence("gathering") }
                .onSuccess { ceremony = it }
        }
    }

    val c = ceremony
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(BgDeep.copy(0.94f))) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Zur Hochzeit", color = TextPrimary, fontFamily = DisplayFont, fontSize = 24.sp)
                Text(
                    "Anwesend: ${c?.gatheringPresentCount ?: 0}/${c?.gatheringTotal ?: 0}",
                    color = Gold,
                    fontFamily = DisplayFont,
                    fontSize = 16.sp
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(88.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(c?.gathering.orEmpty(), key = { it.userId }) { g ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (g.present) Color(0x332E7D32) else BgSoft)
                                .padding(8.dp)
                        ) {
                            ItemGlyph(id = g.petEmoji, fontSize = 28.sp)
                            Text(
                                g.nickname,
                                color = TextPrimary,
                                fontFamily = BodyFont,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                            Text(
                                if (g.present) "da" else "fehlt",
                                color = if (g.present) Gold else TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                // Reaktionsleiste (vereinfacht)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("❤️", "😍", "🎉", "👏", "😭", "🙏").forEach { emo ->
                        Text(
                            emo,
                            fontSize = 26.sp,
                            modifier = Modifier
                                .clickable {
                                    scope.launch {
                                        floatEmoji = emo
                                        val token = System.currentTimeMillis()
                                        floatEmojiToken = token
                                        runCatching { LuvApiClient.ceremonyReact(emo) }
                                            .onSuccess { ceremony = it }
                                        delay(2000)
                                        if (floatEmojiToken == token && floatEmoji == emo) {
                                            floatEmoji = null
                                        }
                                    }
                                }
                                .padding(4.dp)
                        )
                    }
                }
                if (isCouple) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                busy = true
                                scope.launch {
                                    runCatching { LuvApiClient.ceremonyKickAbsent() }
                                        .onSuccess { ceremony = it }
                                    busy = false
                                }
                            },
                            enabled = !busy
                        ) {
                            Text("Abwesende ausladen", color = TextMuted, fontSize = 12.sp)
                        }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    runCatching { LuvApiClient.ceremonyRemind() }
                                        .onSuccess { onShareRemind(it.shareText) }
                                }
                            }
                        ) {
                            Text("Erinnern", color = MaleBlue, fontSize = 12.sp)
                        }
                    }
                    if (c?.allGathered == true) {
                        TextButton(
                            onClick = {
                                busy = true
                                scope.launch {
                                    runCatching { LuvApiClient.ceremonyEnterAltar() }
                                        .onSuccess {
                                            ceremony = it
                                            onEnterAltar()
                                        }
                                        .onFailure {
                                            Toast.makeText(
                                                context,
                                                it.message ?: "Noch nicht bereit",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    busy = false
                                }
                            },
                            enabled = !busy
                        ) {
                            Text("Zum Altar", color = Gold, fontFamily = DisplayFont, fontSize = 18.sp)
                        }
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Zurück", color = TextMuted)
                }
            }
            floatEmoji?.let { emo ->
                Text(
                    emo,
                    fontSize = 64.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun WeddingColdFeetDialog(
    onDismiss: () -> Unit,
    onLeft: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var holdMs by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var sliderX by remember { mutableFloatStateOf(0f) }
    var trackW by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(dragging) {
        if (!dragging) {
            holdMs = 0L
            sliderX = 0f
            return@LaunchedEffect
        }
        while (dragging) {
            delay(100)
            // Nur zählen wenn am rechten Anschlag
            if (sliderX >= trackW * 0.85f) {
                holdMs += 100
                if (holdMs >= 30_000L) {
                    runCatching { LuvApiClient.ceremonyLeave(holdMs) }
                        .onSuccess {
                            Toast.makeText(context, "Hochzeit verlassen", Toast.LENGTH_SHORT).show()
                            onLeft()
                        }
                        .onFailure {
                            Toast.makeText(
                                context,
                                it.message ?: "Fehler",
                                Toast.LENGTH_SHORT
                            ).show()
                            holdMs = 0L
                        }
                    dragging = false
                }
            } else {
                holdMs = 0L
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(BgDeep)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("💔", fontSize = 48.sp)
            Text(
                "Kriegst du kalte Füße?",
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 22.sp,
                textAlign = TextAlign.Center
            )
            Text(
                "Schieberegler 30 Sekunden nach rechts halten.\nLoslassen setzt den Timer zurück.",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Text(
                "${(holdMs / 1000).coerceAtMost(30)}/30 s",
                color = AccentRose,
                fontFamily = DisplayFont,
                fontSize = 18.sp
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(BgSoft)
                    .pointerInput(Unit) {
                        trackW = size.width.toFloat()
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                            onDrag = { change, drag ->
                                change.consume()
                                sliderX = (sliderX + drag.x).coerceIn(0f, trackW)
                            }
                        )
                    }
            ) {
                val frac = (sliderX / trackW.coerceAtLeast(1f)).coerceIn(0f, 1f)
                val thumb = 48.dp
                val travel = (maxWidth - thumb).coerceAtLeast(0.dp)
                Box(
                    modifier = Modifier
                        .padding(start = travel * frac)
                        .size(thumb)
                        .clip(CircleShape)
                        .background(AccentRose)
                        .align(Alignment.CenterStart)
                )
            }
            TextButton(onClick = onDismiss) {
                Text("Doch bleiben", color = Gold, fontFamily = DisplayFont)
            }
        }
    }
}

@Composable
fun WeddingLeftNoticeDialog(
    partnerName: String?,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(BgSoft)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("💔", fontSize = 40.sp)
            Text(
                "${partnerName ?: "Dein Brautpartner"} hat die Hochzeit verlassen.",
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
            Text(
                "Das tut weh — und es ist okay, traurig zu sein. " +
                    "Nehmt euch Zeit. Wenn ihr bereit seid, könnt ihr euch " +
                    "nach der Wartezeit wieder finden. Ihr seid mehr als dieser Moment.",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            TextButton(
                onClick = {
                    scope.launch {
                        runCatching { LuvApiClient.dismissCeremonyLeftNotice() }
                        onDismiss()
                    }
                }
            ) {
                Text("Danke", color = AccentRose, fontFamily = DisplayFont, fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun WeddingBookingDialog(
    onDismiss: () -> Unit,
    onBooked: (lobbyCode: String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ceremony by remember { mutableStateOf<LuvApiClient.CeremonyInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showCoinGap by remember { mutableStateOf(false) }
    var needCoins by remember { mutableStateOf(0) }
    var packs by remember { mutableStateOf<List<com.luv.couple.net.ShopPack>>(emptyList()) }

    fun applyCer(
        c: LuvApiClient.CeremonyInfo?,
        marriageStatus: String? = null,
        marriageLobby: String? = null,
    ) {
        ceremony = c
        val code = c?.ceremonyLobbyCode?.takeIf { it.isNotBlank() }
            ?: marriageLobby?.takeIf { it.isNotBlank() }
        val scheduled = marriageStatus == "ceremony_scheduled" ||
            (code != null && c?.phase == "scheduled")
        if (!code.isNullOrBlank() && scheduled) {
            onBooked(code)
        }
    }

    val bookRev by CeremonyRefreshBus.revision.collectAsStateWithLifecycle()
    LaunchedEffect(Unit, bookRev) {
        runCatching { LuvApiClient.fetchCeremony() }
            .onSuccess {
                applyCer(
                    it.ceremony,
                    marriageStatus = it.marriage?.status,
                    marriageLobby = it.marriage?.ceremonyLobbyCode,
                )
            }
    }

    val b = ceremony?.booking
    val bill = b?.billPerPerson ?: 0

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(BgSoft)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Hochzeit buchen",
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 22.sp,
            )
            // Rechnung-Kachel oben
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0x332A1F28), Color(0x22FFD54F))
                        )
                    )
                    .border(1.dp, Gold.copy(0.35f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Rechnung",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 11.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if ((b?.ceremonyAt ?: 0L) > 0L) {
                            formatCeremonyAt(b!!.ceremonyAt)
                        } else {
                            "Termin folgt"
                        },
                        color = TextPrimary,
                        fontFamily = BodyFont,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "$bill 🪙 je",
                        color = Gold,
                        fontFamily = DisplayFont,
                        fontSize = 18.sp,
                    )
                }
            }

            when (b?.step) {
                "trees" -> {
                    Text(
                        "Geldbäume",
                        color = TextPrimary,
                        fontFamily = DisplayFont,
                        fontSize = 17.sp,
                    )
                    Text(
                        "Für je ${b.moneyTreesPerPerson} Coins (50 gesamt) stehen in der Kapelle " +
                            "Geldbäume. Gäste können jeden Baum einmal tippen und erhalten 1 Coin. " +
                            "Das Brautpaar erntet nicht.",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 13.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BookingVoteButton(
                            label = "Ablehnen ${b.moneyTreesDeclineCount}/2",
                            selected = b.moneyTreesMine == "decline",
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                busy = true
                                scope.launch {
                                    runCatching {
                                        LuvApiClient.ceremonyBookingMoneyTrees("decline")
                                    }.onSuccess {
                                        applyCer(
                                            it.ceremony,
                                            marriageStatus = it.marriage?.status,
                                            marriageLobby = it.marriage?.ceremonyLobbyCode,
                                        )
                                    }
                                        .onFailure {
                                            // Schritt evtl. schon weiter (Partner) — frisch laden
                                            scope.launch {
                                                runCatching { LuvApiClient.fetchCeremony() }
                                                    .onSuccess { bundle ->
                                                        applyCer(
                                                            bundle.ceremony,
                                                            marriageStatus = bundle.marriage?.status,
                                                            marriageLobby =
                                                                bundle.marriage?.ceremonyLobbyCode,
                                                        )
                                                    }
                                            }
                                            Toast.makeText(
                                                context,
                                                it.message ?: "Fehler",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    busy = false
                                }
                            },
                        )
                        BookingVoteButton(
                            label = "Buchen ${b.moneyTreesBookCount}/2",
                            selected = b.moneyTreesMine == "book",
                            enabled = !busy,
                            accent = true,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                busy = true
                                scope.launch {
                                    runCatching {
                                        LuvApiClient.ceremonyBookingMoneyTrees("book")
                                    }.onSuccess {
                                        applyCer(
                                            it.ceremony,
                                            marriageStatus = it.marriage?.status,
                                            marriageLobby = it.marriage?.ceremonyLobbyCode,
                                        )
                                    }
                                        .onFailure {
                                            Toast.makeText(
                                                context,
                                                it.message ?: "Fehler",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    busy = false
                                }
                            },
                        )
                    }
                }

                "room" -> {
                    Text(
                        "Raum wählen",
                        color = TextPrimary,
                        fontFamily = DisplayFont,
                        fontSize = 17.sp,
                    )
                    Text(
                        "Beide tippen denselben Raum — gewählt n/2 bis Match.",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 12.sp,
                    )
                    b.rooms.forEach { room ->
                        val votes = b.roomVoteCounts[room.id] ?: 0
                        val mine = b.roomMine == room.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (mine) Color(0x44FFD54F) else Color(0xFF1A2030)
                                )
                                .border(
                                    1.5.dp,
                                    if (mine) Gold else Color.White.copy(0.12f),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable(enabled = !busy) {
                                    busy = true
                                    scope.launch {
                                        runCatching {
                                            LuvApiClient.ceremonyBookingRoom(room.id)
                                        }.onSuccess {
                                            applyCer(
                                                it.ceremony,
                                                marriageStatus = it.marriage?.status,
                                                marriageLobby = it.marriage?.ceremonyLobbyCode,
                                            )
                                        }
                                            .onFailure {
                                                Toast.makeText(
                                                    context,
                                                    it.message ?: "Fehler",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        busy = false
                                    }
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    room.shortLabel.ifBlank { room.name },
                                    color = TextPrimary,
                                    fontFamily = DisplayFont,
                                    fontSize = 16.sp,
                                )
                                Text(
                                    "${room.guestSlots} Gäste-Slots · Cap ${room.capacity}",
                                    color = TextMuted,
                                    fontFamily = BodyFont,
                                    fontSize = 12.sp,
                                )
                                if (room.blurb.isNotBlank()) {
                                    Text(
                                        room.blurb,
                                        color = TextMuted,
                                        fontFamily = BodyFont,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (room.perPerson <= 0) "gratis" else "${room.perPerson} 🪙",
                                    color = Gold,
                                    fontFamily = DisplayFont,
                                    fontSize = 18.sp,
                                )
                                if (room.totalCost > 0) {
                                    Text(
                                        "${room.totalCost} gesamt",
                                        color = TextMuted,
                                        fontFamily = BodyFont,
                                        fontSize = 10.sp,
                                    )
                                }
                                Text(
                                    "gewählt $votes/2",
                                    color = if (votes >= 2) Color(0xFF81C784) else AccentRose,
                                    fontFamily = BodyFont,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }

                "confirm" -> {
                    val room = b.rooms.firstOrNull { it.id == b.roomId }
                    Text(
                        "Bestätigen",
                        color = TextPrimary,
                        fontFamily = DisplayFont,
                        fontSize = 17.sp,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1A2030))
                            .border(1.dp, Gold.copy(0.3f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ConfirmBillRow("Termin", formatCeremonyAt(b.ceremonyAt))
                        ConfirmBillRow(
                            "Geldbäume",
                            if (b.moneyTrees == true) "ja (+${b.moneyTreesPerPerson} je)" else "nein",
                        )
                        ConfirmBillRow(
                            "Raum",
                            "${room?.name ?: b.roomId} · ${room?.guestSlots ?: "?"} Gäste",
                        )
                        ConfirmBillRow("Summe je Person", "$bill 🪙", emphasize = true)
                    }
                    Text(
                        "Beide müssen buchen — erst dann wird abgebucht und die Lobby erstellt.",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 12.sp,
                    )
                    BookingVoteButton(
                        label = if (b.confirmMine) {
                            "Wartest auf Partner… ${b.confirmCount}/2"
                        } else {
                            "Hochzeit buchen · $bill Coins (${b.confirmCount}/2)"
                        },
                        selected = b.confirmMine,
                        enabled = !busy && !b.confirmMine,
                        accent = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            busy = true
                            scope.launch {
                                runCatching { LuvApiClient.ceremonyBookingConfirm(true) }
                                    .onSuccess { r ->
                                        if (r.noCoins) {
                                            needCoins = r.needCoins.coerceAtLeast(bill)
                                            runCatching { LuvApiClient.shopPacks() }
                                                .onSuccess { packs = it.second }
                                            showCoinGap = true
                                            applyCer(
                                                r.ceremony,
                                                marriageStatus = r.marriage?.status,
                                                marriageLobby = r.marriage?.ceremonyLobbyCode,
                                            )
                                        } else if (!r.ceremonyLobbyCode.isNullOrBlank()) {
                                            Toast.makeText(
                                                context,
                                                "Hochzeit gebucht!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            onBooked(r.ceremonyLobbyCode)
                                        } else {
                                            applyCer(
                                                r.ceremony,
                                                marriageStatus = r.marriage?.status,
                                                marriageLobby = r.marriage?.ceremonyLobbyCode,
                                            )
                                        }
                                    }
                                    .onFailure {
                                        Toast.makeText(
                                            context,
                                            it.message ?: "Buchung fehlgeschlagen",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                busy = false
                            }
                        },
                    )
                }

                else -> {
                    Text(
                        "Buchung wird geladen…",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 13.sp,
                    )
                }
            }

            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Schließen", color = TextMuted, fontFamily = BodyFont)
            }
        }
    }

    if (showCoinGap) {
        val pack = packs
            .filter { it.coins > 0 }
            .sortedBy { it.coins }
            .firstOrNull { it.coins >= needCoins }
            ?: packs.maxByOrNull { it.coins }
        Dialog(onDismissRequest = { showCoinGap = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgSoft)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Nicht genug Coins",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 18.sp,
                )
                Text(
                    "Ihr braucht je $needCoins Coins für die Buchung.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp,
                )
                if (pack != null) {
                    Text(
                        "Empfohlen: ${pack.label.ifBlank { "${pack.coins} Coins" }} " +
                            "(${pack.displayPrice ?: pack.amountEur})",
                        color = Gold,
                        fontFamily = DisplayFont,
                        fontSize = 14.sp,
                    )
                    Text(
                        "Öffne den Markt → Coins-Tab, kaufe das Paket, und tippe danach erneut auf „Hochzeit buchen“.",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 12.sp,
                    )
                }
                TextButton(onClick = { showCoinGap = false }) {
                    Text("Verstanden", color = AccentRose, fontFamily = DisplayFont)
                }
            }
        }
    }
}

@Composable
private fun ConfirmBillRow(
    label: String,
    value: String,
    emphasize: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextMuted, fontFamily = BodyFont, fontSize = 13.sp)
        Text(
            value,
            color = if (emphasize) Gold else TextPrimary,
            fontFamily = if (emphasize) DisplayFont else BodyFont,
            fontSize = if (emphasize) 16.sp else 13.sp,
        )
    }
}

@Composable
private fun BookingVoteButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = if (accent || selected) Color.White else TextPrimary,
        fontFamily = DisplayFont,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    selected && accent -> AccentRose
                    selected -> Color(0xFF5C6BC0)
                    accent -> AccentRose.copy(0.75f)
                    else -> Color.White.copy(0.10f)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
    )
}

fun shareWeddingText(context: android.content.Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Hochzeit teilen"))
}
