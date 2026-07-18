package com.luv.couple.ui.screens

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Schieberegler-Bestätigung: Knopf nach rechts schieben und dort halten.
 * Loslassen → zurück nach links, Timer wird zurückgesetzt.
 */
@Composable
fun HoldSlideConfirmDialog(
    title: String,
    body: String,
    holdSeconds: Int,
    accent: Color = AccentRose,
    confirmHint: String = "Nach rechts schieben und halten",
    enabled: Boolean = true,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val thumbX = remember { Animatable(0f) }
    var travelPx by remember { mutableFloatStateOf(1f) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var pressing by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val holdMs = (holdSeconds.coerceAtLeast(1) * 1000L)
    val atEnd = travelPx > 0f && thumbX.value >= travelPx * 0.92f

    fun snapBack() {
        pressing = false
        holdProgress = 0f
        scope.launch {
            thumbX.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 420f))
        }
    }

    // Timer nur solange gedrückt + am rechten Anschlag
    LaunchedEffect(pressing, atEnd, enabled, finished) {
        if (!enabled || finished || !pressing || !atEnd) {
            holdProgress = 0f
            return@LaunchedEffect
        }
        val started = SystemClock.elapsedRealtime()
        while (pressing && !finished) {
            val stillAtEnd = thumbX.value >= travelPx * 0.92f
            if (!stillAtEnd) {
                holdProgress = 0f
                return@LaunchedEffect
            }
            val held = SystemClock.elapsedRealtime() - started
            holdProgress = (held / holdMs.toFloat()).coerceIn(0f, 1f)
            if (held >= holdMs) {
                finished = true
                onConfirmed()
                onDismiss()
                return@LaunchedEffect
            }
            delay(16)
        }
        holdProgress = 0f
    }

    Dialog(
        onDismissRequest = {
            snapBack()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF2A1F28), BgDeep))
                )
                .border(1.dp, accent.copy(0.35f), RoundedCornerShape(26.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
            Text(
                body,
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            val status = when {
                !enabled -> "Nicht möglich"
                finished -> "Fertig"
                atEnd && pressing -> {
                    val left = ((1f - holdProgress) * holdSeconds).coerceAtLeast(0f)
                    "Halten … noch ${"%.1f".format(left)}s"
                }
                else -> "$confirmHint ($holdSeconds Sek.)"
            }
            Text(
                status,
                color = if (atEnd && pressing) accent else TextMuted,
                fontFamily = BodyFont,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(BgSoft)
                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(28.dp))
            ) {
                val density = LocalDensity.current
                val thumbSize = 48.dp
                val pad = 4.dp
                val travel = with(density) {
                    (maxWidth - thumbSize - pad * 2).toPx().coerceAtLeast(1f)
                }
                LaunchedEffect(travel) { travelPx = travel }

                val fill = ((thumbX.value / travel).coerceIn(0f, 1f) * 0.5f + holdProgress * 0.5f)
                    .coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fill)
                        .background(
                            Brush.horizontalGradient(
                                listOf(accent.copy(0.18f), accent.copy(0.5f))
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(holdProgress)
                        .height(4.dp)
                        .background(accent)
                )

                Box(
                    modifier = Modifier
                        .padding(pad)
                        .size(thumbSize)
                        .offset { IntOffset(thumbX.value.roundToInt(), 0) }
                        .clip(CircleShape)
                        .background(
                            if (enabled) {
                                Brush.radialGradient(listOf(accent, accent.copy(0.8f)))
                            } else {
                                Brush.radialGradient(listOf(Color(0xFF666666), Color(0xFF444444)))
                            }
                        )
                        .border(2.dp, Color.White.copy(0.35f), CircleShape)
                        .pointerInput(enabled, travel) {
                            if (!enabled) return@pointerInput
                            detectHorizontalDragGestures(
                                onDragStart = { pressing = true },
                                onDragEnd = { snapBack() },
                                onDragCancel = { snapBack() },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    pressing = true
                                    scope.launch {
                                        thumbX.snapTo(
                                            (thumbX.value + dragAmount).coerceIn(0f, travel)
                                        )
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("››", color = Color.White, fontFamily = DisplayFont, fontSize = 18.sp)
                }
            }

            Text(
                "Abbrechen",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable {
                        snapBack()
                        onDismiss()
                    }
                    .padding(8.dp)
            )
        }
    }
}
