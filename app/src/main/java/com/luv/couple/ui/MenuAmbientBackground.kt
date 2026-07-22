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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.luv.couple.net.EventDecor
import com.luv.couple.ui.theme.AccentRose
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hintergrund-Animation für Haupt-Tabs (Home/Sozial/Markt/Zahnrad).
 * Bei aktivem Event: Event-Partikel über den ganzen Bildschirm.
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
            animation = tween(durationMillis = 14_000, easing = LinearEasing),
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
    val intensity = if (useEvent) {
        (eventDecor?.intensity ?: 0.55f).coerceIn(0.28f, 0.9f)
    } else {
        0.35f
    }
    val seeds = remember(eventDecor?.particles, useEvent) {
        List(if (useEvent) 36 else 28) {
            Random(it * 41 + (eventDecor?.particles?.hashCode() ?: 7)).nextFloat()
        }
    }
    // Idle: längsamer Drift als Event-Partikel
    val idlePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "idleAmbientPhase"
    )
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
            drawSoftIdleAmbient(phase = idlePhase, seeds = seeds)
        }
    }
}

/** Langsam driftende weiße Partikel — Standard-Menü ohne Event. */
private fun DrawScope.drawSoftIdleAmbient(phase: Float, seeds: List<Float>) {
    val w = size.width
    val h = size.height
    seeds.forEachIndexed { i, s ->
        val speedX = 0.04f + (i % 5) * 0.012f
        val speedY = 0.03f + ((i + 2) % 4) * 0.01f
        val dir = if (i % 2 == 0) 1f else -1f
        val x = ((s * 1.61f + phase * speedX * dir + i * 0.03f) % 1f + 1f) % 1f * w
        val yBase = ((s * 0.87f + phase * speedY) % 1f)
        val sway = sin(phase * 6.28f * 0.7f + i * 0.9f) * (8f + s * 10f)
        val y = yBase * h + sway * 0.15f
        val r = 1.6f + s * 2.4f
        val twinkle = (0.55f + 0.45f * cos(phase * 6.28f * 1.2f + i * 1.3f)).coerceIn(0.4f, 1f)
        val alpha = (0.14f + s * 0.22f) * twinkle
        drawCircle(Color.White.copy(alpha = alpha), r, Offset(x, y))
        // ganz leichter Kern
        drawCircle(Color.White.copy(alpha = alpha * 0.55f), r * 0.45f, Offset(x, y))
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
        val x = ((s * 1.37f + phase * 0.14f * ((i % 5) + 1)) % 1f) * w
        val fall = ((s * 0.7f + phase * 0.65f) % 1f)
        val y = fall * (h + 40f) - 20f
        val sway = sin((phase * 6.28f) + i) * 12f
        val r = 2.0f + (s * 2.6f)
        val alpha = (0.22f + s * 0.38f) * intensity.coerceAtLeast(0.25f)
        when (kind) {
            "snow" -> drawCircle(Color.White.copy(alpha), r, Offset(x + sway, y))
            "hearts" -> drawCircle(accent.copy(alpha), r * 1.2f, Offset(x + sway * 0.5f, y))
            "leaves" -> drawCircle(
                Color(0xFFD4A574).copy(alpha),
                r * 1.2f,
                Offset(x + sway, y)
            )
            "sparkle" -> {
                val twinkle = (0.5f + 0.5f * cos(phase * 12f + i)).coerceIn(0.3f, 1f)
                drawCircle(accent.copy(alpha * twinkle), r * 0.75f, Offset(x, y))
                drawCircle(Color.White.copy(alpha * 0.6f * twinkle), r * 0.35f, Offset(x + 1f, y - 1f))
            }
            else -> drawCircle(accent.copy(alpha * 0.85f), r * 0.9f, Offset(x + sway * 0.3f, y))
        }
    }
}
