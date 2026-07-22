package com.luv.couple.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luv.couple.net.LuvApiClient
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.MaleBlue
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Vollbild-Wartungsmodus 02:59–03:09 Berlin.
 * Statt Minispiel: Random-Mal-Lobby unten (Toolbar unter der Leinwand).
 */
@Composable
fun ForcedMaintenanceDialog(
    status: LuvApiClient.MaintenanceStatus,
    onDismiss: () -> Unit,
    onClaimed: (balance: Int) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var clockOffsetMs by remember(status.serverNow) {
        mutableLongStateOf(status.serverNow - System.currentTimeMillis())
    }
    var remainingMs by remember(status.endsAt, status.serverNow) {
        mutableLongStateOf(
            max(0L, status.endsAt - (System.currentTimeMillis() + clockOffsetMs))
        )
    }
    var phase by remember(status.nightKey) {
        mutableStateOf(
            when {
                status.active && remainingMs > 0L -> Phase.Wait
                status.canClaim -> Phase.ReadyClaim
                else -> Phase.ReadyExit
            }
        )
    }
    var claiming by remember { mutableStateOf(false) }
    var claimError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(status.endsAt, status.serverNow) {
        clockOffsetMs = status.serverNow - System.currentTimeMillis()
        while (true) {
            val nowApprox = System.currentTimeMillis() + clockOffsetMs
            val left = max(0L, status.endsAt - nowApprox)
            remainingMs = left
            if (left <= 0L) {
                phase = when {
                    status.canClaim || phase == Phase.Wait -> Phase.ReadyClaim
                    else -> Phase.ReadyExit
                }
                break
            }
            delay(250)
        }
    }

    LaunchedEffect(status.endsAt, status.claimGraceMs) {
        val hardLimit = status.endsAt + status.claimGraceMs + 90_000L
        while (true) {
            val nowApprox = System.currentTimeMillis() + clockOffsetMs
            if (nowApprox >= hardLimit) {
                onDismiss()
                return@LaunchedEffect
            }
            delay(5_000)
        }
    }

    LaunchedEffect(status.active, status.canClaim, status.claimed, remainingMs) {
        if (phase == Phase.Thanks) return@LaunchedEffect
        when {
            status.active && remainingMs > 0L -> phase = Phase.Wait
            status.canClaim -> phase = Phase.ReadyClaim
            !status.active && remainingMs <= 0L -> phase = Phase.ReadyExit
        }
    }

    BackHandler(enabled = true) { /* blockiert */ }

    Dialog(
        onDismissRequest = { /* nicht schließbar */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
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
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* absorb */ }
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Kurz Wartung",
                    color = AccentRose,
                    fontFamily = DisplayFont,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = status.joke,
                    color = TextPrimary,
                    fontFamily = BodyFont,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                    modifier = Modifier.widthIn(max = 420.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                when (phase) {
                    Phase.Wait -> {
                        Text(
                            text = formatRemaining(remainingMs),
                            color = MaleBlue,
                            fontFamily = DisplayFont,
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Derweil in der Random-Lobby malen",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Phase.ReadyClaim, Phase.ReadyExit -> {
                        Text(
                            text = "Fertig. Danke fürs Warten.",
                            color = MaleBlue,
                            fontFamily = DisplayFont,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    Phase.Thanks -> {
                        Text(
                            text = "+${status.rewardCoins} Coins",
                            color = AccentRose,
                            fontFamily = DisplayFont,
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Danke — wir schauen wieder hin.",
                            color = TextPrimary,
                            fontFamily = BodyFont,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                when (phase) {
                    Phase.ReadyClaim -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        ClaimButton(
                            label = if (claiming) "…" else "+${status.rewardCoins} Coins abholen",
                            enabled = !claiming
                        ) {
                            if (claiming) return@ClaimButton
                            claiming = true
                            claimError = null
                            scope.launch {
                                val result = runCatching { LuvApiClient.claimMaintenanceReward() }
                                claiming = false
                                result.onSuccess { r ->
                                    onClaimed(r.balance)
                                    phase = Phase.Thanks
                                    delay(1600)
                                    onDismiss()
                                }.onFailure { e ->
                                    val msg = e.message.orEmpty()
                                    claimError = msg.ifBlank { "Konnte nicht abholen" }
                                    if (!msg.contains("läuft noch", ignoreCase = true) &&
                                        !msg.contains("too_early", ignoreCase = true)
                                    ) {
                                        phase = Phase.ReadyExit
                                    }
                                }
                            }
                        }
                        claimError?.let {
                            Text(
                                text = it,
                                color = AccentRose,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Text(
                            text = "Ohne Claim weiter",
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clickable { onDismiss() }
                                .padding(6.dp)
                        )
                    }
                    Phase.ReadyExit -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        ClaimButton(label = "Weiter", enabled = true, onClick = onDismiss)
                    }
                    Phase.Thanks, Phase.Wait -> Unit
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Random-Malfläche: nimmt restlichen Platz, Toolbar darunter (kein Overlap)
                MaintenanceRandomDrawPanel(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
}

private enum class Phase { Wait, ReadyClaim, ReadyExit, Thanks }

@Composable
private fun ClaimButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .height(48.dp)
            .background(
                if (enabled) AccentRose else AccentRose.copy(alpha = 0.4f),
                RoundedCornerShape(16.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontFamily = DisplayFont,
            fontSize = 17.sp
        )
    }
}

private fun formatRemaining(ms: Long): String {
    val totalSec = max(0L, ms / 1000L)
    val m = totalSec / 60L
    val s = totalSec % 60L
    return when {
        m <= 0L -> "Noch $s Sekunden"
        m == 1L && s == 1L -> "Noch 1 Minute und 1 Sekunde"
        m == 1L -> "Noch 1 Minute und $s Sekunden"
        s == 1L -> "Noch $m Minuten und 1 Sekunde"
        else -> "Noch $m Minuten und $s Sekunden"
    }
}
