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
import com.luv.couple.net.LuvApiClient
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

    LaunchedEffect(Unit) {
        while (true) {
            runCatching {
                LuvApiClient.ceremonyPresence("presence")
                LuvApiClient.fetchCeremony()
            }.onSuccess {
                ceremony = it.ceremony
                marriage = it.marriage
            }
            delay(2500)
        }
    }

    if (showSchedule) {
        WeddingScheduleDialog(
            onDismiss = { showSchedule = false },
            onScheduled = { code ->
                showSchedule = false
                onScheduled(code)
            }
        )
        return
    }

    val c = ceremony
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
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
            TextButton(onClick = onDismiss) {
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
    val cal = remember {
        Calendar.getInstance().apply {
            add(Calendar.MINUTE, 45)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    var label by remember {
        mutableStateOf(formatCeremonyAt(cal.timeInMillis))
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
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
                "Mindestens 30 Minuten, maximal 14 Tage in der Zukunft.",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Text(label, color = Gold, fontFamily = DisplayFont, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        runCatching { LuvApiClient.ceremonySchedule(cal.timeInMillis) }
                            .onSuccess {
                                Toast.makeText(context, "Hochzeit geplant", Toast.LENGTH_SHORT).show()
                                onScheduled(it.ceremony?.ceremonyLobbyCode ?: it.marriage?.ceremonyLobbyCode)
                            }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "Planen fehlgeschlagen",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        busy = false
                    }
                },
                enabled = !busy
            ) {
                Text("Bestätigen", color = AccentRose, fontFamily = DisplayFont)
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

    LaunchedEffect(Unit) {
        while (true) {
            runCatching {
                LuvApiClient.ceremonyPresence("gathering")
            }.onSuccess { ceremony = it }
            delay(2000)
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
                            Text(g.petEmoji, fontSize = 28.sp)
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
                                        runCatching { LuvApiClient.ceremonyReact(emo) }
                                            .onSuccess {
                                                ceremony = it
                                                floatEmoji = emo
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
                LaunchedEffect(emo) {
                    delay(1600)
                    floatEmoji = null
                }
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

fun shareWeddingText(context: android.content.Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Hochzeit teilen"))
}
