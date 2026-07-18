package com.luv.couple.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.LuvApp
import com.luv.couple.data.PeerPalette
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenQuietHours: () -> Unit,
    onOpenHelp: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    val accent = PeerPalette.menuAccent()
    val prefs = LuvApp.instance.prefs
    val scope = rememberCoroutineScope()
    val partnerNotify by prefs.partnerDrawNotifyFlow.collectAsStateWithLifecycle(initialValue = true)
    val partnerHaptic by prefs.partnerHapticFlow.collectAsStateWithLifecycle(initialValue = true)
    val liveProximityRich by prefs.liveProximityRichFlow.collectAsStateWithLifecycle(initialValue = true)
    val liveProximityWake by prefs.liveProximityWakeFlow.collectAsStateWithLifecycle(initialValue = false)
    var showDeleteHold by remember { mutableStateOf(false) }

    ScreenBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Zurück",
                color = TextMuted,
                fontFamily = BodyFont,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(bottom = 4.dp)
            )
            Text("Einstellungen", fontFamily = DisplayFont, fontSize = 34.sp, color = TextPrimary)
            Text(
                "Ruhe, Hinweise und Nähe — alles an einem Ort.",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 14.sp
            )

            MenuButton("Ruhezeiten", BgSoft, onOpenQuietHours, bordered = true)
            MenuButton("Hilfe", BgSoft, onOpenHelp, bordered = true)

            SettingsToggleRow(
                title = if (partnerNotify) "Glocke an" else "Glocke aus",
                subtitle = if (partnerNotify) {
                    "Beitritt, Malen und sanfte Impulse erreichen dich"
                } else {
                    "Ruhe — keine Hinweise, keine Impulse"
                },
                checked = partnerNotify,
                accent = accent,
                onCheckedChange = { enabled ->
                    scope.launch { prefs.setPartnerDrawNotifyEnabled(enabled) }
                },
                leading = if (partnerNotify) "🔔" else "🔕"
            )
            SettingsToggleRow(
                title = "Vibration beim Malen",
                subtitle = "Leichter Impuls auf der Leinwand",
                checked = partnerHaptic,
                accent = accent,
                onCheckedChange = { enabled ->
                    scope.launch { prefs.setPartnerHapticEnabled(enabled) }
                }
            )
            SettingsToggleRow(
                title = "Lebendige Nähe",
                subtitle = if (liveProximityRich) {
                    "Wenn die Glocke bei einer Lobby an ist: Vorschau & Widget"
                } else {
                    "Wenn die Glocke an ist: nur einfache, ruhige Hinweise"
                },
                checked = liveProximityRich,
                accent = accent,
                onCheckedChange = { enabled ->
                    scope.launch { prefs.setLiveProximityRichEnabled(enabled) }
                }
            )
            SettingsToggleRow(
                title = "Bildschirm wecken",
                subtitle = if (liveProximityWake) {
                    "Intensiv — Display geht kurz an, wenn jemand malt"
                } else {
                    "Aus — besser für Akku & Hosentasche"
                },
                checked = liveProximityWake,
                accent = accent,
                onCheckedChange = { enabled ->
                    scope.launch { prefs.setLiveProximityWakeEnabled(enabled) }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("Gefahrenzone", fontFamily = DisplayFont, fontSize = 20.sp, color = TextPrimary)
            Text(
                "Konto löschen entfernt Coins, Lobbys und Google-Verknüpfung unwiderruflich.",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 13.sp
            )
            if (!showDeleteHold) {
                MenuButton("Konto löschen", Color(0xFF3A2430), { showDeleteHold = true })
            } else {
                HoldToDeleteSlider(
                    onConfirmed = {
                        showDeleteHold = false
                        onDeleteAccount()
                    },
                    onCancel = { showDeleteHold = false }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HoldToDeleteSlider(
    onConfirmed: () -> Unit,
    onCancel: () -> Unit
) {
    val thumbSize = 48.dp
    val trackHeight = 56.dp
    var offsetX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var confirmed by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxTravel = with(density) {
            (maxWidth - thumbSize).coerceAtLeast(0.dp).toPx()
        }
        val atEnd = maxTravel > 0f && offsetX >= maxTravel * 0.96f

        LaunchedEffect(atEnd, dragging, confirmed) {
            if (confirmed) return@LaunchedEffect
            if (atEnd && dragging) {
                val start = System.nanoTime()
                while (true) {
                    val elapsed = (System.nanoTime() - start) / 1_000_000_000f
                    holdProgress = (elapsed / 10f).coerceIn(0f, 1f)
                    if (holdProgress >= 1f) {
                        confirmed = true
                        onConfirmed()
                        break
                    }
                    delay(16)
                }
            } else {
                holdProgress = 0f
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                when {
                    confirmed -> "Wird gelöscht…"
                    atEnd && dragging ->
                        "Noch ${(10 - (holdProgress * 10)).coerceAtLeast(0f).roundToInt()} Sek. halten"
                    else -> "Schieberegler nach rechts ziehen und 10 Sekunden halten"
                },
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 13.sp
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF2A1820))
                    .border(1.dp, AccentRose.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(holdProgress.coerceIn(0f, 1f))
                        .background(AccentRose.copy(alpha = 0.35f))
                )
                Text(
                    "Löschen",
                    color = TextMuted.copy(alpha = 0.7f),
                    fontFamily = DisplayFont,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetX.roundToInt(), 0) }
                        .size(thumbSize)
                        .align(Alignment.CenterStart)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(AccentRose)
                        .pointerInput(maxTravel) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragging = true },
                                onDragEnd = {
                                    dragging = false
                                    if (!confirmed) {
                                        offsetX = 0f
                                        holdProgress = 0f
                                    }
                                },
                                onDragCancel = {
                                    dragging = false
                                    if (!confirmed) {
                                        offsetX = 0f
                                        holdProgress = 0f
                                    }
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    if (confirmed) return@detectHorizontalDragGestures
                                    offsetX = (offsetX + dragAmount).coerceIn(0f, maxTravel)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("›", color = Color.White, fontFamily = DisplayFont, fontSize = 22.sp)
                }
            }
            Text(
                "Abbrechen",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable(enabled = !confirmed) {
                        offsetX = 0f
                        holdProgress = 0f
                        dragging = false
                        onCancel()
                    }
                    .padding(vertical = 4.dp)
            )
        }
    }
}
