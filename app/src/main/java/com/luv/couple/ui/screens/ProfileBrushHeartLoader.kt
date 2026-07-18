package com.luv.couple.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.FemalePurple
import com.luv.couple.ui.theme.MaleBlue
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private val LoaderPalette = listOf(
    AccentRose,
    MaleBlue,
    FemalePurple,
    Color(0xFFFFD54F),
    Color(0xFF81C784),
    Color(0xFFFF8A65)
)

/**
 * Kurzer Ladescreen: Pinsel holt Farbe von der Palette und malt ein Herz.
 */
@Composable
fun ProfileBrushHeartLoader(
    modifier: Modifier = Modifier,
    label: String = "Profil wird gemalt…"
) {
    val transition = rememberInfiniteTransition(label = "profileLoader")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loaderProgress"
    )

    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF161C28), BgDeep, Color(0xFF121018))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(modifier = Modifier.size(220.dp)) {
                val w = size.width
                val h = size.height
                val cx = w * 0.56f
                val cy = h * 0.46f
                val heartR = w * 0.28f

                val pickEnd = 0.16f
                val travelEnd = 0.28f
                val drawEnd = 0.90f

                val colorIdx = ((t / pickEnd) * LoaderPalette.size)
                    .toInt()
                    .coerceIn(0, LoaderPalette.lastIndex)
                val strokeColor = LoaderPalette[colorIdx]

                val points = heartPoints(cx, cy, heartR, steps = 96)

                // Farbpalette (unten links)
                val palLeft = w * 0.08f
                val palBottom = h * 0.88f
                val swatch = w * 0.085f
                val gap = w * 0.018f
                drawRoundRect(
                    color = Color.White.copy(0.08f),
                    topLeft = Offset(palLeft - gap, palBottom - swatch - gap * 2.2f),
                    size = Size(
                        LoaderPalette.size * (swatch + gap) + gap,
                        swatch + gap * 3.2f
                    ),
                    cornerRadius = CornerRadius(swatch * 0.55f)
                )
                LoaderPalette.forEachIndexed { i, c ->
                    val x = palLeft + i * (swatch + gap) + swatch / 2f
                    val y = palBottom - swatch / 2f - gap
                    val selected = i == colorIdx && t < travelEnd
                    drawCircle(
                        color = c,
                        radius = if (selected) swatch * 0.58f else swatch * 0.48f,
                        center = Offset(x, y)
                    )
                    if (selected) {
                        drawCircle(
                            color = Color.White,
                            radius = swatch * 0.58f,
                            center = Offset(x, y),
                            style = Stroke(width = 2.2f)
                        )
                    }
                }

                val drawProgress = when {
                    t < travelEnd -> 0f
                    t < drawEnd -> ((t - travelEnd) / (drawEnd - travelEnd)).coerceIn(0f, 1f)
                    else -> 1f
                }

                if (drawProgress > 0.001f) {
                    val drawn = Path()
                    val count = (1 + (points.lastIndex * drawProgress)).toInt()
                        .coerceIn(1, points.size)
                    points.take(count).forEachIndexed { i, p ->
                        if (i == 0) drawn.moveTo(p.x, p.y) else drawn.lineTo(p.x, p.y)
                    }
                    drawPath(
                        path = drawn,
                        color = strokeColor,
                        style = Stroke(
                            width = w * 0.045f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    if (drawProgress >= 0.999f) {
                        val full = Path().apply {
                            points.forEachIndexed { i, p ->
                                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                            }
                            close()
                        }
                        drawPath(
                            path = full,
                            color = strokeColor.copy(alpha = 0.18f + 0.12f * sin(t * 40f)),
                            style = Stroke(
                                width = w * 0.055f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                val startPoint = points.first()
                val brushPos: Offset
                val brushAngle: Float
                when {
                    t < pickEnd -> {
                        val px = palLeft + colorIdx * (swatch + gap) + swatch / 2f
                        val py = palBottom - swatch / 2f - gap - swatch * 0.85f
                        brushPos = Offset(px, py)
                        brushAngle = -55f
                    }
                    t < travelEnd -> {
                        val u = (t - pickEnd) / (travelEnd - pickEnd)
                        val ease = u * u * (3f - 2f * u)
                        val start = Offset(
                            palLeft + colorIdx * (swatch + gap) + swatch / 2f,
                            palBottom - swatch / 2f - gap - swatch * 0.85f
                        )
                        brushPos = Offset(
                            start.x + (startPoint.x - start.x) * ease,
                            start.y + (startPoint.y - start.y) * ease
                        )
                        brushAngle = Math.toDegrees(
                            atan2(
                                (startPoint.y - start.y).toDouble(),
                                (startPoint.x - start.x).toDouble()
                            )
                        ).toFloat() - 90f
                    }
                    else -> {
                        val idx = (points.lastIndex * drawProgress)
                            .toInt()
                            .coerceIn(0, points.lastIndex)
                        val next = points[(idx + 1).coerceAtMost(points.lastIndex)]
                        val cur = points[idx]
                        brushPos = cur
                        brushAngle = Math.toDegrees(
                            atan2(
                                (next.y - cur.y).toDouble(),
                                (next.x - cur.x).toDouble()
                            )
                        ).toFloat() - 90f
                    }
                }

                drawBrushTip(
                    center = brushPos,
                    angleDeg = brushAngle,
                    tipColor = strokeColor,
                    size = w * 0.11f
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                label,
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "gleich fertig",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 13.sp
            )
        }
    }
}

private fun heartPoints(cx: Float, cy: Float, radius: Float, steps: Int): List<Offset> {
    val out = ArrayList<Offset>(steps + 1)
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val a = (Math.PI * 2.0 * t).toFloat()
        val s = sin(a)
        val hx = 16f * s * s * s
        val hy = -(
            13f * cos(a) -
                5f * cos(2f * a) -
                2f * cos(3f * a) -
                cos(4f * a)
        )
        out += Offset(
            cx + (hx / 18f) * radius,
            cy + (hy / 18f) * radius * 0.95f
        )
    }
    return out
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBrushTip(
    center: Offset,
    angleDeg: Float,
    tipColor: Color,
    size: Float
) {
    rotate(degrees = angleDeg, pivot = center) {
        translate(left = center.x, top = center.y) {
            drawRoundRect(
                color = Color(0xFF6D4C41),
                topLeft = Offset(-size * 0.12f, -size * 0.15f),
                size = Size(size * 0.24f, size * 1.15f),
                cornerRadius = CornerRadius(size * 0.12f)
            )
            drawRoundRect(
                color = Color(0xFFB0BEC5),
                topLeft = Offset(-size * 0.16f, -size * 0.28f),
                size = Size(size * 0.32f, size * 0.18f),
                cornerRadius = CornerRadius(size * 0.06f)
            )
            val tip = Path().apply {
                moveTo(0f, -size * 0.85f)
                lineTo(size * 0.22f, -size * 0.14f)
                lineTo(-size * 0.22f, -size * 0.14f)
                close()
            }
            drawPath(tip, tipColor)
            drawPath(
                tip,
                Color.White.copy(0.25f),
                style = Stroke(width = 1.2f, join = StrokeJoin.Round)
            )
        }
    }
}
