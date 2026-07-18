package com.luv.couple.ui.screens

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Schmaler goldener Glow mit Laufstreifen — analog zum weißen Lobby-Glow.
 */
@Composable
fun ItemNewGlowBorder(
    active: Boolean,
    corner: Dp = 14.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
        if (active) {
            val infinite = rememberInfiniteTransition(label = "itemNewGlow")
            val phase by infinite.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "itemNewGlowPhase"
            )
            val gold = Color(0xFFFFD54F)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val inset = 1.dp.toPx()
                val cornerPx = corner.toPx()
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = inset,
                            top = inset,
                            right = size.width - inset,
                            bottom = size.height - inset,
                            cornerRadius = CornerRadius(cornerPx, cornerPx)
                        )
                    )
                }
                drawPath(
                    path = path,
                    color = gold.copy(alpha = 0.45f),
                    style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                )
                val measure = PathMeasure().apply { setPath(path, forceClosed = false) }
                val len = measure.length
                if (len <= 1f) return@Canvas
                val head = phase * len
                val trailLen = len * 0.45f
                val steps = 48
                val maxStroke = 4.dp.toPx()
                for (i in 0 until steps) {
                    val t = i / steps.toFloat()
                    val tNext = (i + 1) / steps.toFloat()
                    val d0 = (head - t * trailLen + len * 8f) % len
                    val d1 = (head - tNext * trailLen + len * 8f) % len
                    if (d1 >= d0) continue
                    val seg = Path()
                    if (!measure.getSegment(d1, d0, seg, startWithMoveTo = true)) continue
                    val fade = (1f - t).coerceIn(0f, 1f)
                    val stroke = maxStroke * (0.22f + 0.78f * fade)
                    drawPath(
                        path = seg,
                        color = gold.copy(alpha = 0.28f * fade),
                        style = Stroke(width = stroke * 2.2f, cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = seg,
                        color = gold.copy(alpha = 0.55f + 0.4f * fade),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}
