package com.luv.couple.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextPrimary
import kotlin.math.roundToInt

/**
 * Highend-Coachmark: Scrim mit kreisförmigem Spotlight, kurzer Text, Pfeil.
 * Anker in Viewport-Fraction (0–1) — layout-stabil auf allen Displays.
 */
@Composable
fun CoachmarkOverlay(
    holeCenterXFrac: Float,
    holeCenterYFrac: Float,
    holeRadiusFrac: Float,
    label: String,
    labelAbove: Boolean = true,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val pulse = rememberInfiniteTransition(label = "coachPulse")
    val ring by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring"
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (onDismiss != null) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss
                    )
                } else Modifier
            )
    ) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        // Wenig clampen — sonst wandert der Kreis von echten Top-Bar-Targets weg
        val cx = holeCenterXFrac.coerceIn(0.04f, 0.96f) * w
        val cy = holeCenterYFrac.coerceIn(0.04f, 0.96f) * h
        val r = (holeRadiusFrac.coerceIn(0.04f, 0.22f) * minOf(w, h)) * ring

        val density = LocalDensity.current
        // Automatisch umklappen, wenn oben/unten kein Platz
        val placeAbove = with(density) {
            val wantAbove = labelAbove
            val topSpace = cy - r
            val botSpace = h - (cy + r)
            when {
                wantAbove && topSpace < 56.dp.toPx() -> false
                !wantAbove && botSpace < 56.dp.toPx() -> true
                else -> wantAbove
            }
        }
        val dens = density.density
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            drawRect(Color(0xE60B0E14))
            drawCircle(
                color = Color.Transparent,
                radius = r,
                center = Offset(cx, cy),
                blendMode = BlendMode.Clear
            )
            drawCircle(
                color = Color(0x66FFFFFF),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 2.5f * dens)
            )
            val arrowY = if (placeAbove) cy - r - 28f * dens else cy + r + 28f * dens
            val path = Path().apply {
                if (placeAbove) {
                    moveTo(cx, cy - r - 4f * dens)
                    lineTo(cx - 10f * dens, arrowY + 8f * dens)
                    lineTo(cx + 10f * dens, arrowY + 8f * dens)
                    close()
                } else {
                    moveTo(cx, cy + r + 4f * dens)
                    lineTo(cx - 10f * dens, arrowY - 8f * dens)
                    lineTo(cx + 10f * dens, arrowY - 8f * dens)
                    close()
                }
            }
            drawPath(path, Color(0xFFF4F1EC).copy(alpha = 0.9f))
        }
        val labelY = with(density) {
            if (placeAbove) {
                (cy - r - 56.dp.toPx()).coerceAtLeast(12.dp.toPx())
            } else {
                (cy + r + 28.dp.toPx()).coerceAtMost(h - 64.dp.toPx())
            }
        }
        Text(
            text = label,
            color = TextPrimary,
            fontFamily = DisplayFont,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            softWrap = true,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, labelY.roundToInt()) }
                .widthIn(max = 260.dp)
                .padding(horizontal = 20.dp)
        )
    }
}

/** Bounds → Fraction-Anker für Coachmarks. */
fun Rect.toCoachHole(
    parentW: Float,
    parentH: Float,
    padFrac: Float = 0.02f
): Triple<Float, Float, Float> {
    val pw = parentW.coerceAtLeast(1f)
    val ph = parentH.coerceAtLeast(1f)
    val cx = ((left + right) / 2f) / pw
    val cy = ((top + bottom) / 2f) / ph
    val rr = (maxOf(width, height) / 2f / minOf(pw, ph) + padFrac)
    return Triple(cx, cy, rr.coerceIn(0.05f, 0.2f))
}
