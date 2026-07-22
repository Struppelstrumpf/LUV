package com.luv.couple.ui

import android.graphics.Bitmap
import android.graphics.RectF
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.LuvApp
import com.luv.couple.data.ConnectionState
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.PairConnectionService
import com.luv.couple.net.PublicCanvasPreview
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private val SplashBg = Color(0xFF0B0E14)
private val FrameTrack = Color(0x33F4F1EC)
private val FrameGlow = Color(0x66FF6B8A)
private val FrameHot = Color(0xFFFFB4C4)
private val FrameCream = Color(0xFFF4F1EC)

/**
 * Start-Splash: öffentliches Bild mit schwarzem Rand + äußerem Fortschrittsbalken.
 * Lobbys verbinden parallel; Splash bleibt bis alle CONNECTED (oder Timeout).
 */
@Composable
fun PublicCanvasSplash(
    onFinished: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val prefs = LuvApp.instance.prefs
    val lobbyStates by PairConnectionService.lobbyStates.collectAsStateWithLifecycle()
    var lobbyIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var preview by remember { mutableStateOf<PublicCanvasPreview?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var ready by remember { mutableStateOf(false) }
    var idsReady by remember { mutableStateOf(false) }
    val barProgress = remember { Animatable(0f) }

    val connectedFrac = remember(lobbyIds, lobbyStates, idsReady) {
        if (!idsReady) 0f
        else if (lobbyIds.isEmpty()) 1f
        else {
            val ok = lobbyIds.count { id ->
                val s = lobbyStates[id]
                s == ConnectionState.CONNECTED || s == ConnectionState.HOSTING
            }
            ok.toFloat() / lobbyIds.size.toFloat()
        }
    }

    LaunchedEffect(Unit) {
        if (!LuvApiClient.sessionToken.isNullOrBlank()) {
            PairConnectionService.startAll(context)
        }
        lobbyIds = withContext(Dispatchers.IO) {
            runCatching { prefs.snapshot().lobbies.map { it.id } }.getOrDefault(emptyList())
        }
        idsReady = true
    }

    LaunchedEffect(connectedFrac) {
        barProgress.animateTo(
            connectedFrac.coerceIn(0f, 1f),
            animationSpec = tween(
                durationMillis = if (connectedFrac >= 1f) 280 else 420,
                easing = LinearEasing
            )
        )
    }

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
            launch { prefetchNext() }
        } else {
            val fresh = withTimeoutOrNull(2_200L) {
                withContext(Dispatchers.IO) {
                    val seen = runCatching { prefs.seenSplashIds() }.getOrDefault(emptySet())
                    val fetched = LuvApiClient.fetchRandomPublicCanvas(seen) ?: return@withContext null
                    if (fetched.cycled) {
                        runCatching { prefs.clearSeenSplashIds() }
                    }
                    val bmp = PublicSplashCache.downloadBitmap(fetched.imageUrl)
                        ?: return@withContext null
                    PublicSplashCache.save(context, fetched, bmp)
                    runCatching { prefs.markSplashSeen(fetched.id) }
                    fetched to bmp
                }
            }
            if (fresh != null) {
                preview = fresh.first
                bitmap = fresh.second
            }
            ready = true
        }
    }

    LaunchedEffect(ready, idsReady, lobbyIds) {
        if (!ready || !idsReady) return@LaunchedEffect
        val shownAt = System.currentTimeMillis()
        val minShowMs = if (bitmap != null) 1_200L else 400L
        val deadline = shownAt + 10_000L
        while (true) {
            val elapsed = System.currentTimeMillis() - shownAt
            val states = PairConnectionService.lobbyStates.value
            val lobbiesDone = lobbyIds.isEmpty() || lobbyIds.all { id ->
                val s = states[id]
                s == ConnectionState.CONNECTED || s == ConnectionState.HOSTING
            }
            val timedOut = System.currentTimeMillis() >= deadline
            if (elapsed >= minShowMs && (lobbiesDone || timedOut)) break
            delay(80)
        }
        if (barProgress.value < 1f) {
            barProgress.animateTo(1f, animationSpec = tween(220, easing = LinearEasing))
        }
        onFinished()
    }

    val nameLine = preview?.nameLine?.ifBlank { preview?.hostNickname }.orEmpty()
    val nameAlpha by animateFloatAsState(
        targetValue = if (ready && bitmap != null) 1f else 0f,
        animationSpec = tween(420),
        label = "nameAlpha"
    )
    val progress = barProgress.value

    Box(
        Modifier
            .fillMaxSize()
            .background(SplashBg)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SplashBg)
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = if (nameLine.isNotBlank()) {
                            "Öffentliche Leinwand von $nameLine — von einer anderen Gruppe veröffentlicht"
                        } else {
                            "Öffentliche Leinwand"
                        },
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
                }
                if (bitmap != null && nameLine.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 36.dp)
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
                            fontSize = 24.sp,
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

            Canvas(Modifier.fillMaxSize()) {
                drawSplashFrameProgress(progress)
            }
        }
    }
}

private fun DrawScope.drawSplashFrameProgress(progress: Float) {
    val stroke = 4.5.dp.toPx()
    val glow = 10.dp.toPx()
    val inset = stroke * 0.5f + 1.dp.toPx()
    val radius = 14.dp.toPx()
    val w = size.width
    val h = size.height
    val frame = Path().apply {
        addRoundRect(
            RoundRect(
                left = inset,
                top = inset,
                right = w - inset,
                bottom = h - inset,
                cornerRadius = CornerRadius(radius, radius)
            )
        )
    }
    val measure = PathMeasure()
    measure.setPath(frame, false)
    val len = measure.length
    if (len <= 0f) return

    drawPath(
        path = frame,
        color = FrameTrack,
        style = Stroke(width = stroke * 0.85f, cap = StrokeCap.Round)
    )

    val t = progress.coerceIn(0f, 1f)
    if (t <= 0.001f) return
    val visible = Path()
    measure.getSegment(0f, len * t, visible, true)

    drawPath(
        path = visible,
        color = FrameGlow,
        style = Stroke(width = glow, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = visible,
        brush = Brush.linearGradient(
            colors = listOf(AccentRose, FrameHot, FrameCream),
            start = Offset.Zero,
            end = Offset(w, h)
        ),
        style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    val endPos = FloatArray(2)
    val endTan = FloatArray(2)
    val fullAndroid = android.graphics.Path().apply {
        addRoundRect(
            RectF(inset, inset, w - inset, h - inset),
            radius,
            radius,
            android.graphics.Path.Direction.CW
        )
    }
    val am = android.graphics.PathMeasure(fullAndroid, false)
    if (am.getPosTan(am.length * t, endPos, endTan)) {
        drawCircle(
            color = AccentRose.copy(alpha = 0.4f),
            radius = stroke * 1.75f,
            center = Offset(endPos[0], endPos[1])
        )
        drawCircle(
            color = FrameCream.copy(alpha = 0.95f),
            radius = stroke * 0.9f,
            center = Offset(endPos[0], endPos[1])
        )
    }
}
