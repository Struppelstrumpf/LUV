package com.luv.couple.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
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
import kotlin.random.Random

/**
 * Vollbild-Wartungsmodus 02:59–03:09 Berlin.
 * Nicht wegklickbar; nach Timer: Belohnung oder Failsafe-Weiter.
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
    var highScore by remember(status.highScore) { mutableIntStateOf(status.highScore) }
    var claiming by remember { mutableStateOf(false) }
    var claimError by remember { mutableStateOf<String?>(null) }

    // Lokaler Countdown (an Server-Ende gekoppelt)
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

    // Failsafe: nie ewig blockieren (Ende + Grace + 90s)
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

    // Server-Status steuert Claim-Phase (nach Fensterende)
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
                ) { /* absorb */ },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Kurz Wartung",
                    color = AccentRose,
                    fontFamily = DisplayFont,
                    fontSize = 28.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = status.joke,
                    color = TextPrimary,
                    fontFamily = BodyFont,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.widthIn(max = 420.dp)
                )
                Spacer(Modifier.height(18.dp))

                when (phase) {
                    Phase.Wait -> {
                        Text(
                            text = formatRemaining(remainingMs),
                            color = MaleBlue,
                            fontFamily = DisplayFont,
                            fontSize = 26.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Shop sortiert · Server putzt · Liebe wartet mit",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    Phase.ReadyClaim, Phase.ReadyExit -> {
                        Text(
                            text = "Fertig. Danke fürs Warten.",
                            color = MaleBlue,
                            fontFamily = DisplayFont,
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    Phase.Thanks -> {
                        Text(
                            text = "🙈 +${status.rewardCoins} Coins",
                            color = AccentRose,
                            fontFamily = DisplayFont,
                            fontSize = 28.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Danke — wir schauen wieder hin. Versprochen.",
                            color = TextPrimary,
                            fontFamily = BodyFont,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Bestscore: $highScore",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))

                HeartHopMinigame(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    enabled = phase == Phase.Wait || phase == Phase.ReadyClaim || phase == Phase.ReadyExit,
                    onScore = { score ->
                        if (score > highScore) highScore = score
                    },
                    onGameOver = { score ->
                        if (score <= 0) return@HeartHopMinigame
                        scope.launch {
                            runCatching {
                                val hs = LuvApiClient.submitMaintenanceScore(score)
                                if (hs > highScore) highScore = hs
                            }
                        }
                    }
                )

                Spacer(Modifier.height(12.dp))

                when (phase) {
                    Phase.ReadyClaim -> {
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
                                    // Zu früh (Uhr-Skew): Button bleibt; sonst Escape-Hatch
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
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Ohne Claim weiter",
                            color = TextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable { onDismiss() }
                                .padding(8.dp)
                        )
                    }
                    Phase.ReadyExit -> {
                        ClaimButton(label = "Weiter", enabled = true, onClick = onDismiss)
                    }
                    Phase.Thanks, Phase.Wait -> Unit
                }
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
            .height(52.dp)
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
            fontSize = 18.sp
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

/** Einfaches Hüpfer-Spiel: Herz springt über Pinsel — Tap = Sprung. */
@Composable
private fun HeartHopMinigame(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onScore: (Int) -> Unit,
    onGameOver: (Int) -> Unit
) {
    var score by remember { mutableIntStateOf(0) }
    var alive by remember { mutableStateOf(true) }
    var playerY by remember { mutableFloatStateOf(0f) }
    var velocity by remember { mutableFloatStateOf(0f) }
    var obstacles by remember { mutableStateOf(listOf<Pair<Float, Float>>()) }
    var groundY by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableFloatStateOf(220f) }
    var spawnAcc by remember { mutableFloatStateOf(0f) }
    var lastFrame by remember { mutableLongStateOf(0L) }

    fun reset() {
        score = 0
        alive = true
        playerY = 0f
        velocity = 0f
        obstacles = emptyList()
        speed = 220f
        spawnAcc = 0f
        lastFrame = 0L
        onScore(0)
    }

    LaunchedEffect(enabled, widthPx, heightPx) {
        if (!enabled || widthPx <= 0f || heightPx <= 0f) return@LaunchedEffect
        groundY = heightPx * 0.78f
        if (playerY == 0f) playerY = groundY
        while (true) {
            withFrameMillis { frame ->
                if (lastFrame == 0L) {
                    lastFrame = frame
                    return@withFrameMillis
                }
                val dt = ((frame - lastFrame).coerceIn(0L, 40L)) / 1000f
                lastFrame = frame
                if (!alive || !enabled) return@withFrameMillis

                // Physik
                velocity += 980f * dt
                playerY = (playerY + velocity * dt).coerceAtMost(groundY)
                if (playerY >= groundY) {
                    playerY = groundY
                    velocity = 0f
                }

                // Hindernisse
                speed = (220f + score * 4f).coerceAtMost(420f)
                spawnAcc += dt
                val spawnEvery = (1.35f - score * 0.01f).coerceAtLeast(0.75f)
                var next = obstacles.map { (x, h) -> x - speed * dt to h }.filter { it.first > -40f }
                if (spawnAcc >= spawnEvery) {
                    spawnAcc = 0f
                    val h = 28f + Random.nextFloat() * 42f
                    next = next + (widthPx + 20f to h)
                }
                obstacles = next

                // Score
                val nextScore = score + (dt * 12f).toInt().coerceAtLeast(0)
                if (nextScore != score) {
                    score = nextScore
                    onScore(score)
                }

                // Kollision (Spieler ~28px, Obstakel Breite 22)
                val px = 56f
                val py = playerY
                val hit = obstacles.any { (ox, oh) ->
                    val ox2 = ox + 22f
                    val oy1 = groundY - oh
                    px + 18f > ox && px < ox2 && py > oy1
                }
                if (hit) {
                    alive = false
                    onGameOver(score)
                }
            }
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (alive) "Herz-Hüpfer · Score $score · Tippen = Sprung"
            else "Autsch · Score $score · Tippen für Neustart",
            color = TextMuted,
            fontFamily = BodyFont,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF1A2230), RoundedCornerShape(18.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    if (!enabled) return@clickable
                    if (!alive) {
                        reset()
                        return@clickable
                    }
                    if (playerY >= groundY - 2f) {
                        velocity = -420f
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                widthPx = size.width
                heightPx = size.height
                val gY = if (groundY > 0f) groundY else size.height * 0.78f

                // Boden
                drawLine(
                    color = Color(0xFF3A4558),
                    start = Offset(0f, gY),
                    end = Offset(size.width, gY),
                    strokeWidth = 3f
                )

                // Hindernisse = Pinsel
                for ((ox, oh) in obstacles) {
                    drawRoundRect(
                        color = MaleBlue,
                        topLeft = Offset(ox, gY - oh),
                        size = Size(22f, oh),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawCircle(
                        color = AccentRose,
                        radius = 7f,
                        center = Offset(ox + 11f, gY - oh - 4f)
                    )
                }

                // Herz
                val cx = 56f
                val cy = if (playerY > 0f) playerY else gY
                val heart = Path().apply {
                    val s = 16f
                    moveTo(cx, cy)
                    cubicTo(cx - s, cy - s, cx - s * 1.6f, cy - s * 2.2f, cx, cy - s * 1.5f)
                    cubicTo(cx + s * 1.6f, cy - s * 2.2f, cx + s, cy - s, cx, cy)
                    close()
                }
                drawPath(heart, AccentRose)
                drawPath(heart, Color.White.copy(alpha = 0.35f), style = Stroke(width = 2f))
            }
        }
    }
}
