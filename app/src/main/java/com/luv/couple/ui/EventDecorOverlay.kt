package com.luv.couple.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.net.EventDecor
import com.luv.couple.net.EventSession
import com.luv.couple.net.LuvApiClient
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextPrimary
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun EventDecorHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val events by EventSession.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (EventSession.state.value == null) {
            runCatching { LuvApiClient.fetchEvents() }
        }
    }
    val decor = events?.primaryDecor?.takeIf {
        it.particles != "none" || it.bannerText.isNotBlank() || it.ornaments != "none"
    }
    Box(modifier = modifier.fillMaxSize()) {
        // Partikel hinter Content, damit Touches nicht blockiert werden
        if (decor != null && decor.particles != "none") {
            EventParticleLayer(decor = decor)
        }
        content()
        if (decor != null) {
            EventBannerAndOrnaments(decor = decor)
        }
    }
}

@Composable
private fun EventParticleLayer(decor: EventDecor) {
    val accent = remember(decor.accentHex) {
        runCatching { Color(android.graphics.Color.parseColor(decor.accentHex)) }
            .getOrDefault(Color(0xFFE94E77))
    }
    val intensity = decor.intensity.coerceIn(0.15f, 1f)
    val transition = rememberInfiniteTransition(label = "eventDecor")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val seeds = remember(decor.particles) {
        List(28) { Random(it * 97 + decor.particles.hashCode()).nextFloat() }
    }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 48.dp)
    ) {
        drawEventParticles(
            kind = decor.particles,
            phase = phase,
            seeds = seeds,
            accent = accent,
            intensity = intensity
        )
    }
}

@Composable
private fun EventBannerAndOrnaments(decor: EventDecor) {
    val accent = remember(decor.accentHex) {
        runCatching { Color(android.graphics.Color.parseColor(decor.accentHex)) }
            .getOrDefault(Color(0xFFE94E77))
    }
    val intensity = decor.intensity.coerceIn(0.15f, 1f)
    Box(modifier = Modifier.fillMaxWidth()) {
        if (decor.bannerText.isNotBlank()) {
            Text(
                decor.bannerText,
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .background(
                        accent.copy(0.22f * intensity + 0.15f),
                        RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
        when (decor.ornaments) {
            "wreath", "hearts", "spark" -> {
                val glyph = when (decor.ornaments) {
                    "wreath" -> "🎄"
                    "hearts" -> "💕"
                    else -> "✨"
                }
                Text(
                    glyph,
                    fontSize = 16.sp,
                    fontFamily = BodyFont,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                )
                Text(
                    glyph,
                    fontSize = 16.sp,
                    fontFamily = BodyFont,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                )
            }
        }
    }
}

private fun DrawScope.drawEventParticles(
    kind: String,
    phase: Float,
    seeds: List<Float>,
    accent: Color,
    intensity: Float,
) {
    val w = size.width
    val h = size.height
    seeds.forEachIndexed { i, s ->
        val x = ((s * 1.37f + phase * 0.15f * ((i % 5) + 1)) % 1f) * w
        val fall = ((s + phase) % 1f)
        val y = fall * (h + 40f) - 20f
        val sway = sin((phase * 6.28f) + i) * 10f
        val r = 2.2f + (s * 2.8f)
        val alpha = (0.25f + s * 0.45f) * intensity
        when (kind) {
            "snow" -> drawCircle(Color.White.copy(alpha), r, Offset(x + sway, y))
            "hearts" -> drawCircle(accent.copy(alpha), r * 1.15f, Offset(x + sway * 0.6f, y))
            "leaves" -> drawCircle(
                Color(0xFFD4A574).copy(alpha),
                r * 1.2f,
                Offset(x + sway, y)
            )
            "sparkle" -> {
                drawCircle(accent.copy(alpha), r * 0.7f, Offset(x, y))
                drawCircle(Color.White.copy(alpha * 0.7f), r * 0.35f, Offset(x + 1f, y - 1f))
            }
            else -> Unit
        }
    }
}
