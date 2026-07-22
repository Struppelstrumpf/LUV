package com.luv.couple.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.FemalePurple
import com.luv.couple.ui.theme.LuvWordmark
import com.luv.couple.ui.theme.MaleBlue
import com.luv.couple.ui.theme.TextMuted
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Warte-Animation wie auf der Website: zwei Handys —
 * links wird ein Herz gemalt, rechts erscheint es live nach.
 */
@Composable
fun SplashPhonesLoader(
    modifier: Modifier = Modifier,
    statusText: String = "Verbunden...",
) {
    val transition = rememberInfiniteTransition(label = "splashPhones")
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cycle"
    )
    val floatA by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatA"
    )
    val floatB by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatB"
    )

    // DRAW 0–0.46, HOLD 0.46–0.71, FADE 0.71–0.84, GAP 0.84–1
    val drawProgress = when {
        cycle < 0.46f -> (cycle / 0.46f).coerceIn(0f, 1f)
        cycle < 0.71f -> 1f
        cycle < 0.84f -> (1f - (cycle - 0.71f) / 0.13f).coerceIn(0f, 1f)
        else -> 0f
    }
    val points = remember { heartPoints(140) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            LuvWordmark(fontSize = 36.sp, showHeart = true)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PhoneFrame(
                    caption = "Du",
                    screenBrush = Brush.linearGradient(
                        listOf(Color(0xFF19C8EF), Color(0xFF0099C4), Color(0xFF007AA0))
                    ),
                    drawProgress = drawProgress,
                    points = points,
                    showHand = true,
                    modifier = Modifier
                        .graphicsLayer {
                            translationY = -8f * floatA
                            rotationZ = -2.5f + floatA
                        }
                )
                SyncDots()
                PhoneFrame(
                    caption = "Dein Herz",
                    screenBrush = Brush.linearGradient(
                        listOf(Color(0xFFDE2FBC), Color(0xFFB01596), Color(0xFF8A0F74))
                    ),
                    drawProgress = drawProgress,
                    points = points,
                    showHand = false,
                    modifier = Modifier
                        .offset(y = 12.dp)
                        .graphicsLayer {
                            translationY = -10f * floatB
                            rotationZ = 2.8f - floatB
                        }
                )
            }
            Text(
                statusText,
                color = TextMuted.copy(0.75f),
                fontFamily = BodyFont,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SyncDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(3) { i ->
            val phase = ((t + i * 0.33f) % 1f)
            val a = 0.25f + 0.55f * (1f - kotlin.math.abs(phase - 0.5f) * 2f)
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(MaleBlue.copy(a), FemalePurple.copy(a))
                        )
                    )
            )
        }
    }
}

@Composable
private fun PhoneFrame(
    caption: String,
    screenBrush: Brush,
    drawProgress: Float,
    points: List<Offset>,
    showHand: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(118.dp)
                .aspectRatio(9f / 19.2f)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF2A3140), Color(0xFF12161E), Color(0xFF0A0C10))
                    )
                )
                .padding(7.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(screenBrush)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (drawProgress <= 0.01f || points.size < 2) return@Canvas
                    val count = (points.size * drawProgress).toInt().coerceAtLeast(2)
                    val path = Path()
                    for (i in 0 until count) {
                        val p = points[i]
                        val x = p.x * size.width
                        val y = p.y * size.height
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    val stroke = (size.minDimension * 0.045f).coerceAtLeast(4f)
                    drawPath(
                        path,
                        color = Color.White.copy(0.95f),
                        style = Stroke(
                            width = stroke,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    if (!showHand && drawProgress in 0.05f..0.98f) {
                        val tip = points[(count - 1).coerceIn(0, points.lastIndex)]
                        drawCircle(
                            color = Color.White,
                            radius = 5f,
                            center = Offset(tip.x * size.width, tip.y * size.height)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                        .width(36.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF050608))
                )
                if (showHand && drawProgress in 0.02f..0.98f) {
                    val idx = ((points.size - 1) * drawProgress).toInt()
                        .coerceIn(0, points.lastIndex)
                    val tip = points[idx]
                    Text(
                        "👆",
                        fontSize = 15.sp,
                        modifier = Modifier
                            .offset(
                                x = maxWidth * tip.x - 10.dp,
                                y = maxHeight * tip.y - 6.dp
                            )
                            .rotate(-14f)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            caption.uppercase(),
            color = Color.White.copy(0.45f),
            fontFamily = BodyFont,
            fontSize = 10.sp,
            letterSpacing = 1.6.sp
        )
    }
}

private fun heartPoints(steps: Int): List<Offset> {
    return List(steps) { i ->
        val t = i / (steps - 1).toFloat()
        val a = (Math.PI * 2.0 * t).toFloat()
        val x = 16f * sin(a).toDouble().pow(3.0).toFloat()
        val y = -(
            13f * cos(a) -
                5f * cos(2f * a) -
                2f * cos(3f * a) -
                cos(4f * a)
            )
        Offset(
            x = 0.5f + x / 42f,
            y = 0.52f + y / 42f
        )
    }
}
