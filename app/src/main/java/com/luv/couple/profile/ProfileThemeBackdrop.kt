package com.luv.couple.profile

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hintergrund genau wie auf der Profil-Leinwand:
 * Himmel-Verlauf, Boden ab ~48 %, plus animierter Effekt.
 */
@Composable
fun ProfileThemeBackdrop(
    themeId: String,
    modifier: Modifier = Modifier,
    marriageCelebration: Boolean = false
) {
    // Jedes Mal neu auflösen — ThemeVisualCache / rememberTheme halten visualConfig.
    val theme = ProfileCatalog.theme(themeId)
    ProfileThemeBackdrop(
        theme = theme,
        modifier = modifier,
        marriageCelebration = marriageCelebration
    )
}

@Composable
fun ProfileThemeBackdrop(
    theme: ProfileTheme,
    modifier: Modifier = Modifier,
    marriageCelebration: Boolean = false
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(theme.skyTop), Color(theme.skyBottom)),
                    startY = 0f,
                    endY = size.height * 0.55f
                )
            )
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(theme.groundTop), Color(theme.groundBottom))
                ),
                topLeft = Offset(0f, size.height * 0.48f),
                size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.52f)
            )
        }
        if (theme.effect == "emoji_fx" && theme.particleEmojis.isNotEmpty()) {
            EmojiParticleOverlay(theme)
        } else {
            ThemeFxOverlay(theme.effect)
        }
        if (marriageCelebration) {
            WeddingBlessingRain()
        }
    }
}

@Composable
private fun EmojiParticleOverlay(theme: ProfileTheme) {
    val duration = (4200 / theme.particleSpeed.coerceIn(0.3f, 2.5f)).toInt().coerceIn(1600, 9000)
    val t = rememberInfiniteTransition(label = "emojiFx")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(duration, easing = LinearEasing), RepeatMode.Restart),
        label = "ePhase"
    )
    val glyphs = theme.particleEmojis.ifEmpty { listOf("✨") }
    val count = (18 * theme.particleDensity.coerceIn(0.2f, 1.8f)).roundToInt().coerceIn(6, 40)
    val flakes = remember(theme.id, glyphs, count) {
        List(count) { i ->
            val rnd = Random(i * 31 + theme.id.hashCode())
            WeddingFlake(
                glyph = glyphs[i % glyphs.size],
                xFrac = rnd.nextFloat(),
                speed = 0.55f + rnd.nextFloat() * 0.7f,
                drift = (rnd.nextFloat() - 0.5f) * 0.14f,
                sizeSp = (11f + rnd.nextFloat() * 12f) * theme.particleSize.coerceIn(0.5f, 2f),
                wobble = rnd.nextFloat() * 6.28f,
                delay = rnd.nextFloat()
            )
        }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        flakes.forEach { flake ->
            val p = ((phase * flake.speed) + flake.delay) % 1f
            var xN = flake.xFrac
            var yN = p
            when (theme.particleMotion) {
                "rise" -> yN = 1f - p
                "sway" -> {
                    xN = (flake.xFrac + sin((phase + flake.wobble) * 4.0).toFloat() * 0.08f)
                    yN = 0.2f + ((flake.delay + phase * 0.2f) % 0.6f)
                }
                "drift" -> {
                    xN = (flake.xFrac + phase * 0.2f * flake.speed) % 1f
                    yN = (0.15f + flake.delay * 0.55f + sin((phase + flake.wobble) * 3.0).toFloat() * 0.06f)
                }
                "roll" -> {
                    xN = (flake.xFrac + phase * 0.45f) % 1f
                    yN = 0.55f + sin((phase + flake.wobble) * 6.0).toFloat() * 0.08f
                }
                else -> {
                    xN = (flake.xFrac + flake.drift * p +
                        sin((phase + flake.wobble) * 4.0).toFloat() * 0.03f)
                }
            }
            when (theme.particleCoverage) {
                "band" -> yN = 0.42f + (p % 1f) * 0.12f
                "sky" -> yN = yN * 0.48f
                "ground" -> yN = 0.5f + yN * 0.48f
            }
            while (xN < 0f) xN += 1f
            while (xN > 1f) xN -= 1f
            val alpha = when {
                p < 0.08f -> p / 0.08f
                p > 0.88f -> (1f - p) / 0.12f
                else -> 0.9f
            }.coerceIn(0f, 0.95f)
            Text(
                flake.glyph,
                fontSize = flake.sizeSp.sp,
                modifier = Modifier
                    .graphicsLayer {
                        this.alpha = alpha
                        rotationZ = if (theme.particleMotion == "roll") {
                            phase * 360f + flake.wobble * 40f
                        } else {
                            sin((phase + flake.wobble) * 5.0).toFloat() * 16f
                        }
                    }
                    .offset {
                        IntOffset(
                            x = (xN * w - 10f).roundToInt(),
                            y = (yN * h - 8f).roundToInt()
                        )
                    }
            )
        }
    }
}

/** Herzen, Ringe & Funken — nur bei verheirateten Profilen. */
@Composable
private fun WeddingBlessingRain() {
    val t = rememberInfiniteTransition(label = "weddingRain")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(7200, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "wPhase"
    )
    val glyphs = remember {
        listOf("💕", "💍", "❤️", "💒", "✨", "💗", "💍", "💖", "🤍", "💞")
    }
    val flakes = remember {
        List(22) { i ->
            val rnd = Random(i * 41 + 7)
            WeddingFlake(
                glyph = glyphs[i % glyphs.size],
                xFrac = rnd.nextFloat(),
                speed = 0.55f + rnd.nextFloat() * 0.7f,
                drift = (rnd.nextFloat() - 0.5f) * 0.12f,
                sizeSp = 12f + rnd.nextFloat() * 10f,
                wobble = rnd.nextFloat() * 6.28f,
                delay = rnd.nextFloat()
            )
        }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        flakes.forEach { flake ->
            val p = ((phase * flake.speed) + flake.delay) % 1f
            val xN = (flake.xFrac + flake.drift * p +
                sin((phase + flake.wobble) * 4.0).toFloat() * 0.035f).let {
                var v = it
                while (v < 0f) v += 1f
                while (v > 1f) v -= 1f
                v
            }
            val alpha = when {
                p < 0.08f -> p / 0.08f
                p > 0.88f -> (1f - p) / 0.12f
                else -> 0.88f
            }.coerceIn(0f, 0.92f)
            Text(
                flake.glyph,
                fontSize = flake.sizeSp.sp,
                modifier = Modifier
                    .graphicsLayer {
                        this.alpha = alpha
                        rotationZ = sin((phase + flake.wobble) * 5.0).toFloat() * 18f
                    }
                    .offset {
                        IntOffset(
                            x = (xN * w - 10f).roundToInt(),
                            y = (p * h - 8f).roundToInt()
                        )
                    }
            )
        }
    }
}

private data class WeddingFlake(
    val glyph: String,
    val xFrac: Float,
    val speed: Float,
    val drift: Float,
    val sizeSp: Float,
    val wobble: Float,
    val delay: Float
)

@Composable
private fun ThemeFxOverlay(effect: String) {
    if (effect == "none" || effect.isBlank()) return
    val duration = when (effect) {
        "fire", "sparkles" -> 2800
        "storm" -> 3600
        "meteors" -> 5000
        "aurora" -> 7000
        else -> 4200
    }
    val t = rememberInfiniteTransition(label = "themeFx")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(duration, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val seeds = remember(effect) { List(36) { Random(it * 17 + effect.hashCode()).nextFloat() } }
    Canvas(modifier = Modifier.fillMaxSize()) {
        when (effect) {
            "rain", "storm" -> {
                seeds.forEach { s ->
                    val x = size.width * s
                    val y = ((phase + s) % 1f) * size.height
                    drawLine(
                        Color.White.copy(if (effect == "storm") 0.32f else 0.22f),
                        Offset(x, y),
                        Offset(x - 4f, y + 18f),
                        strokeWidth = if (effect == "storm") 2.1f else 1.6f
                    )
                }
                if (effect == "storm") {
                    val flash = absSin(phase * 40f)
                    if (flash > 0.92f) {
                        drawRect(Color.White.copy(0.18f * flash))
                    }
                }
            }
            "snow" -> seeds.forEach { s ->
                val x = size.width * ((s + phase * 0.15f) % 1f)
                val y = size.height * ((s * 0.7f + phase) % 1f)
                drawCircle(Color.White.copy(0.55f), radius = 2.2f + s * 2.5f, center = Offset(x, y))
            }
            "stars", "galaxy" -> seeds.take(22).forEachIndexed { i, s ->
                val twinkle = 0.35f + 0.65f * absSin(phase * 6.28f + i)
                val col = if (effect == "galaxy" && i % 3 == 0) {
                    Color(0xFFE1BEE7).copy(twinkle)
                } else {
                    Color.White.copy(twinkle)
                }
                drawCircle(
                    col,
                    radius = 1.4f + s * 2.2f,
                    center = Offset(size.width * s, size.height * ((s * 1.7f) % 0.55f))
                )
            }
            "fire" -> seeds.take(24).forEach { s ->
                val x = size.width * (0.15f + s * 0.7f)
                val rise = ((phase + s) % 1f)
                val y = size.height * (0.95f - rise * 0.55f)
                val alpha = (1f - rise) * 0.55f
                drawCircle(
                    Color(0xFFFF6D00).copy(alpha),
                    radius = 3f + s * 5f * (1f - rise),
                    center = Offset(x + sin((phase + s) * 12.0).toFloat() * 6f, y)
                )
                drawCircle(
                    Color(0xFFFFD54F).copy(alpha * 0.7f),
                    radius = 1.5f + s * 2.5f * (1f - rise),
                    center = Offset(x, y - 4f)
                )
            }
            "petals" -> seeds.take(20).forEach { s ->
                val x = size.width * ((s + phase * 0.12f + sin(s * 20.0).toFloat() * 0.05f) % 1f)
                val y = size.height * ((s * 0.55f + phase * 0.85f) % 1f)
                drawCircle(
                    Color(0xFFF8BBD0).copy(0.65f),
                    radius = 3.2f + s * 3f,
                    center = Offset(x, y)
                )
                drawCircle(
                    Color(0xFFFCE4EC).copy(0.5f),
                    radius = 1.8f + s * 1.5f,
                    center = Offset(x - 2f, y + 1f)
                )
            }
            "leaves" -> seeds.take(18).forEach { s ->
                val x = size.width * ((s + phase * 0.2f) % 1f)
                val y = size.height * ((s * 0.6f + phase) % 1f)
                val col = if (s > 0.5f) Color(0xFFE65100).copy(0.55f) else Color(0xFFFFB74D).copy(0.55f)
                drawCircle(col, radius = 2.8f + s * 2.2f, center = Offset(x, y))
            }
            "bubbles" -> seeds.take(22).forEach { s ->
                val x = size.width * ((s + sin((phase + s) * 8.0).toFloat() * 0.04f) % 1f)
                val y = size.height * (1f - ((phase * 0.7f + s) % 1f))
                val r = 2.5f + s * 5f
                drawCircle(Color.White.copy(0.18f), radius = r, center = Offset(x, y))
                drawCircle(Color.White.copy(0.35f), radius = r * 0.25f, center = Offset(x - r * 0.3f, y - r * 0.3f))
            }
            "sparkles" -> seeds.take(26).forEachIndexed { i, s ->
                val twinkle = absSin(phase * 10f + i * 1.7f)
                if (twinkle < 0.35f) return@forEachIndexed
                drawCircle(
                    Color(0xFFFFF59D).copy(twinkle * 0.85f),
                    radius = 1.2f + s * 2.8f * twinkle,
                    center = Offset(
                        size.width * ((s + phase * 0.05f) % 1f),
                        size.height * ((s * 1.3f + i * 0.03f) % 1f)
                    )
                )
            }
            "meteors" -> {
                seeds.take(6).forEachIndexed { i, s ->
                    val p = (phase + s) % 1f
                    val x0 = size.width * (0.1f + s * 0.8f)
                    val y0 = size.height * (0.05f + (i % 3) * 0.08f)
                    val x = x0 + p * size.width * 0.35f
                    val y = y0 + p * size.height * 0.45f
                    val alpha = (1f - p) * 0.7f
                    drawLine(
                        Color.White.copy(alpha),
                        Offset(x, y),
                        Offset(x - 28f, y - 14f),
                        strokeWidth = 2.2f
                    )
                    drawCircle(Color(0xFFFFE082).copy(alpha), radius = 2.4f, center = Offset(x, y))
                }
                seeds.take(14).forEachIndexed { i, s ->
                    val twinkle = 0.3f + 0.7f * absSin(phase * 5f + i)
                    drawCircle(
                        Color.White.copy(twinkle * 0.6f),
                        radius = 1.2f + s,
                        center = Offset(size.width * s, size.height * ((s * 1.4f) % 0.5f))
                    )
                }
            }
            "aurora" -> {
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color(0x3348FF9A),
                            Color(0x3340C4FF),
                            Color(0x33E040FB),
                            Color(0x3348FF9A)
                        ),
                        startX = size.width * (phase - 0.3f),
                        endX = size.width * (phase + 0.7f)
                    ),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.52f)
                )
                seeds.take(16).forEachIndexed { i, s ->
                    val a = 0.15f + 0.35f * absSin(phase * 4f + i)
                    drawCircle(
                        Color(0xFF80D8FF).copy(a),
                        radius = 1.5f + s * 2f,
                        center = Offset(
                            size.width * ((s + phase * 0.08f) % 1f),
                            size.height * (0.08f + (s * 0.35f) % 0.4f)
                        )
                    )
                }
            }
            "fog" -> {
                drawRect(Color.White.copy(0.07f + 0.05f * absSin(phase * 6.28f)))
                seeds.take(12).forEach { s ->
                    val x = size.width * ((s + phase * 0.08f) % 1f)
                    val y = size.height * (0.45f + s * 0.35f)
                    drawCircle(
                        Color.White.copy(0.06f + s * 0.04f),
                        radius = 40f + s * 50f,
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}

private fun absSin(v: Float): Float = abs(sin(v.toDouble())).toFloat()
