package com.luv.couple.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.luv.couple.net.EventDecor
import com.luv.couple.ui.theme.AccentRose
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Seichte Hintergrund-Animation nur für Haupt-Tabs (Home/Sozial/Markt/Zahnrad).
 * Bei aktivem Event: dezente Event-Partikel; sonst sanfte Lichtflecken.
 */
@Composable
fun MenuAmbientBackground(
    eventDecor: EventDecor?,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "menuAmbient")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambientPhase"
    )
    val useEvent = eventDecor != null && eventDecor.particles != "none"
    val accent = remember(eventDecor?.accentHex) {
        val hex = eventDecor?.accentHex
        if (hex.isNullOrBlank()) AccentRose
        else runCatching { Color(android.graphics.Color.parseColor(hex)) }
            .getOrDefault(AccentRose)
    }
    val intensity = (eventDecor?.intensity ?: 0.35f).coerceIn(0.12f, 0.55f) * 0.45f
    val seeds = remember(eventDecor?.particles, useEvent) {
        List(18) { Random(it * 41 + (eventDecor?.particles?.hashCode() ?: 7)).nextFloat() }
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        if (useEvent) {
            drawSoftEventParticles(
                kind = eventDecor!!.particles,
                phase = phase,
                seeds = seeds,
                accent = accent,
                intensity = intensity
            )
        } else {
            drawSoftIdleAmbient(phase = phase, seeds = seeds)
        }
    }
}

private fun DrawScope.drawSoftIdleAmbient(phase: Float, seeds: List<Float>) {
    val w = size.width
    val h = size.height
    // Langsame, sehr dezente Lichtflecken
    seeds.take(6).forEachIndexed { i, s ->
        val cx = ((s + phase * (0.08f + i * 0.01f)) % 1f) * w
        val cy = ((0.2f + s * 0.55f + sin(phase * 6.28f + i) * 0.04f) % 1f) * h
        val r = w * (0.12f + s * 0.1f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    AccentRose.copy(alpha = 0.045f + s * 0.03f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = r
            ),
            radius = r,
            center = Offset(cx, cy)
        )
    }
    // Sanfte Glitzerpunkte
    seeds.drop(6).take(10).forEachIndexed { i, s ->
        val x = ((s * 1.7f + phase * 0.06f * ((i % 3) + 1)) % 1f) * w
        val y = ((s * 0.9f + phase * 0.05f) % 1f) * h
        val alpha = 0.04f + s * 0.06f
        drawCircle(Color.White.copy(alpha), 1.4f + s, Offset(x, y))
    }
}

private fun DrawScope.drawSoftEventParticles(
    kind: String,
    phase: Float,
    seeds: List<Float>,
    accent: Color,
    intensity: Float,
) {
    val w = size.width
    val h = size.height
    seeds.forEachIndexed { i, s ->
        val x = ((s * 1.37f + phase * 0.12f * ((i % 5) + 1)) % 1f) * w
        val fall = ((s * 0.7f + phase * 0.55f) % 1f)
        val y = fall * (h + 30f) - 15f
        val sway = sin((phase * 6.28f) + i) * 8f
        val r = 1.6f + (s * 2.0f)
        val alpha = (0.12f + s * 0.22f) * intensity.coerceAtLeast(0.15f)
        when (kind) {
            "snow" -> drawCircle(Color.White.copy(alpha), r, Offset(x + sway, y))
            "hearts" -> drawCircle(accent.copy(alpha), r * 1.1f, Offset(x + sway * 0.5f, y))
            "leaves" -> drawCircle(
                Color(0xFFD4A574).copy(alpha),
                r * 1.15f,
                Offset(x + sway, y)
            )
            "sparkle" -> {
                val twinkle = (0.5f + 0.5f * cos(phase * 12f + i)).coerceIn(0.3f, 1f)
                drawCircle(accent.copy(alpha * twinkle), r * 0.65f, Offset(x, y))
                drawCircle(Color.White.copy(alpha * 0.55f * twinkle), r * 0.3f, Offset(x + 1f, y - 1f))
            }
            else -> drawCircle(accent.copy(alpha * 0.7f), r * 0.8f, Offset(x + sway * 0.3f, y))
        }
    }
}
