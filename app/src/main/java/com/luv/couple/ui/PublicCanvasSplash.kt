package com.luv.couple.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.LuvApp
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.PublicCanvasPreview
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * Start-Splash: Cache sofort zeigen (kein Netz-Warten), parallel neues Bild fürs nächste Mal.
 * Ohne Cache: Netz mit Timeout, sonst kurze Phone-Animation.
 */
@Composable
fun PublicCanvasSplash(
    onFinished: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val prefs = LuvApp.instance.prefs
    var preview by remember { mutableStateOf<PublicCanvasPreview?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var ready by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        suspend fun prefetchNext() {
            withContext(Dispatchers.IO) {
                runCatching {
                    val seen = prefs.seenSplashIds()
                    val fetched = LuvApiClient.fetchRandomPublicCanvas(seen) ?: return@runCatching
                    if (fetched.cycled) prefs.clearSeenSplashIds()
                    val bmp = PublicSplashCache.downloadBitmap(fetched.imageUrl) ?: return@runCatching
                    PublicSplashCache.save(context, fetched, bmp)
                    prefs.markSplashSeen(fetched.id)
                }
            }
        }

        val cached = withContext(Dispatchers.IO) {
            runCatching { PublicSplashCache.loadLast(context) }.getOrNull()
        }

        if (cached != null) {
            preview = cached.preview
            bitmap = cached.bitmap
            ready = true
            // Sofort 2s zeigen — Prefetch blockiert nicht
            launch { prefetchNext() }
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(2_000, easing = LinearEasing))
            onFinished()
            return@LaunchedEffect
        }

        val fresh = withTimeoutOrNull(2_200L) {
            withContext(Dispatchers.IO) {
                val seen = runCatching { prefs.seenSplashIds() }.getOrDefault(emptySet())
                val fetched = LuvApiClient.fetchRandomPublicCanvas(seen) ?: return@withContext null
                if (fetched.cycled) {
                    runCatching { prefs.clearSeenSplashIds() }
                }
                val bmp = PublicSplashCache.downloadBitmap(fetched.imageUrl) ?: return@withContext null
                PublicSplashCache.save(context, fetched, bmp)
                runCatching { prefs.markSplashSeen(fetched.id) }
                fetched to bmp
            }
        }

        if (fresh != null) {
            preview = fresh.first
            bitmap = fresh.second
            ready = true
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(2_000, easing = LinearEasing))
            onFinished()
            return@LaunchedEffect
        }

        delay(1_200)
        onFinished()
    }

    if (!ready || bitmap == null || preview == null) {
        SplashPhonesLoader()
        return
    }

    val nameLine = preview!!.nameLine.ifBlank { preview!!.hostNickname }
    val nameAlpha by animateFloatAsState(
        targetValue = if (ready) 1f else 0f,
        animationSpec = tween(420),
        label = "nameAlpha"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
    ) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Öffentliche Leinwand von $nameLine — von einer anderen Gruppe veröffentlicht",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x660B0E14),
                            Color(0x220B0E14),
                            Color(0x880B0E14)
                        )
                    )
                )
        )
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val inset = 10.dp.toPx()
            val left = inset
            val top = inset
            val right = w - inset
            val bottom = h - inset
            val frame = Path().apply {
                moveTo(left, bottom)
                lineTo(left, top)
                lineTo(right, top)
                lineTo(right, bottom)
            }
            val measure = PathMeasure()
            measure.setPath(frame, false)
            val frameLen = measure.length
            val t = progress.value.coerceIn(0f, 1f)
            val frameVisible = Path()
            measure.getSegment(0f, frameLen * t, frameVisible, true)
            drawSpeckledStroke(frameVisible, Color(0xF0FFFFFF), 3.4f.dp.toPx())
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 48.dp)
                .alpha(nameAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Öffentliche Leinwand",
                color = Color(0xCCF4F1EC),
                fontFamily = BodyFont,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color(0x99000000),
                        offset = Offset(0f, 1f),
                        blurRadius = 10f
                    )
                )
            )
            Text(
                text = nameLine,
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color(0x99000000),
                        offset = Offset(0f, 2f),
                        blurRadius = 16f
                    )
                )
            )
            Text(
                text = "von einer anderen Gruppe veröffentlicht",
                color = Color(0xB3F4F1EC),
                fontFamily = BodyFont,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color(0x99000000),
                        offset = Offset(0f, 1f),
                        blurRadius = 10f
                    )
                )
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpeckledStroke(
    path: Path,
    color: Color,
    width: Float
) {
    drawPath(
        path = path,
        color = color.copy(alpha = 0.55f),
        style = Stroke(width = width * 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    val androidPath = path.asAndroidPath()
    val measure = android.graphics.PathMeasure(androidPath, false)
    val len = measure.length
    if (len <= 0f) return
    val steps = (len / 14f).toInt().coerceIn(8, 80)
    val rnd = Random(len.toBits())
    val pos = FloatArray(2)
    val tan = FloatArray(2)
    for (i in 0..steps) {
        val dist = len * (i / steps.toFloat())
        if (!measure.getPosTan(dist, pos, tan)) continue
        val nx = -tan[1]
        val ny = tan[0]
        val jitter = (rnd.nextFloat() - 0.5f) * width * 2.8f
        val sparkle = rnd.nextFloat()
        if (sparkle < 0.55f) {
            drawCircle(
                color = color.copy(alpha = 0.35f + sparkle * 0.5f),
                radius = width * (0.35f + sparkle * 0.7f),
                center = Offset(pos[0] + nx * jitter, pos[1] + ny * jitter)
            )
        }
    }
}
