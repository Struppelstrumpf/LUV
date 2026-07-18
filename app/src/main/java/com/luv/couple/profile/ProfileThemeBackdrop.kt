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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hintergrund genau wie auf der Profil-Leinwand:
 * Himmel-Verlauf, Boden ab ~48 %, plus Effekt (Regen/Schnee/Sterne).
 */
@Composable
fun ProfileThemeBackdrop(
    themeId: String,
    modifier: Modifier = Modifier
) {
    ProfileThemeBackdrop(
        theme = ProfileCatalog.theme(themeId),
        modifier = modifier
    )
}

@Composable
fun ProfileThemeBackdrop(
    theme: ProfileTheme,
    modifier: Modifier = Modifier
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
        ThemeFxOverlay(theme.effect)
    }
}

@Composable
private fun ThemeFxOverlay(effect: String) {
    if (effect == "none" || effect.isBlank()) return
    val t = rememberInfiniteTransition(label = "themeFx")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val seeds = remember(effect) { List(28) { Random(it * 17 + effect.hashCode()).nextFloat() } }
    Canvas(modifier = Modifier.fillMaxSize()) {
        when (effect) {
            "rain" -> seeds.forEach { s ->
                val x = size.width * s
                val y = ((phase + s) % 1f) * size.height
                drawLine(
                    Color.White.copy(0.22f),
                    Offset(x, y),
                    Offset(x - 4f, y + 18f),
                    strokeWidth = 1.6f
                )
            }
            "snow" -> seeds.forEach { s ->
                val x = size.width * ((s + phase * 0.15f) % 1f)
                val y = size.height * ((s * 0.7f + phase) % 1f)
                drawCircle(Color.White.copy(0.55f), radius = 2.2f + s * 2.5f, center = Offset(x, y))
            }
            "stars" -> seeds.take(18).forEachIndexed { i, s ->
                val twinkle = 0.35f + 0.65f * absSin(phase * 6.28f + i)
                drawCircle(
                    Color.White.copy(twinkle),
                    radius = 1.4f + s * 2f,
                    center = Offset(size.width * s, size.height * ((s * 1.7f) % 0.55f))
                )
            }
        }
    }
}

private fun absSin(v: Float): Float = abs(sin(v.toDouble())).toFloat()
