package com.luv.couple.ui.wedding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.R
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import kotlinx.coroutines.delay
import kotlin.math.ceil

@Composable
fun AltarHoldTimerBanner(remainingMs: Long, totalMs: Long) {
    val sec = ceil(remainingMs.coerceAtLeast(0L) / 1000.0).toInt().coerceAtLeast(0)
    val totalSec = ceil(totalMs.coerceAtLeast(1L) / 1000.0).toInt().coerceAtLeast(1)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(0.92f))
            .border(1.dp, Color(0xFFE0B0C8), RoundedCornerShape(999.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Noch $sec s · $totalSec s am Altar",
            color = Color(0xFF5C2A3A),
            fontFamily = DisplayFont,
            fontSize = 15.sp
        )
    }
}

@Composable
fun LiveVowTimerBanner(remainingMs: Long) {
    val totalSec = (remainingMs / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    val label = if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(0.94f))
            .border(1.dp, Color(0xFFE57373), RoundedCornerShape(999.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Ja-Wort · $label",
            color = Color(0xFFB71C1C),
            fontFamily = DisplayFont,
            fontSize = 15.sp
        )
    }
}

@Composable
fun ReceptionTimerBanner(remainingMs: Long) {
    val totalSec = (remainingMs / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    val label = if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(0.92f))
            .border(1.dp, Color(0xFF90CAF9), RoundedCornerShape(999.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Empfang · $label",
            color = Color(0xFF1A237E),
            fontFamily = DisplayFont,
            fontSize = 15.sp
        )
    }
}

@Composable
fun PastorDotsBubble(modifier: Modifier = Modifier) {
    var dots by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(450)
            dots = if (dots >= 3) 1 else dots + 1
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(0.95f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            ".".repeat(dots),
            color = Color(0xFF3E2723),
            fontFamily = DisplayFont,
            fontSize = 18.sp
        )
    }
}

@Composable
fun PastorSpeechTile(
    visibleText: String,
    modifier: Modifier = Modifier,
    fullText: String = "",
    startedAtMs: Long = 0L,
    typeMs: Long = 0L,
) {
    // Lokaler Typewriter (~60fps) — Server-Poll allein wirkt stockend
    var shown by remember(fullText, startedAtMs) {
        mutableStateOf(visibleText.ifBlank { "…" })
    }
    LaunchedEffect(fullText, startedAtMs, typeMs, visibleText) {
        val source = fullText.ifBlank { visibleText }
        if (source.isBlank()) {
            shown = "…"
            return@LaunchedEffect
        }
        if (startedAtMs <= 0L || fullText.isBlank()) {
            shown = source
            return@LaunchedEffect
        }
        val total = if (typeMs > 0L) {
            typeMs
        } else {
            (source.length * 45L).coerceIn(2_500L, 12_000L)
        }
        while (true) {
            val elapsed = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
            val n = ((elapsed.toDouble() / total) * source.length)
                .toInt()
                .coerceIn(0, source.length)
            shown = source.take(n).ifBlank { "…" }
            if (n >= source.length) break
            delay(16)
        }
        shown = source
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(0.96f))
            .border(1.dp, Color(0xFFE8D5C4), RoundedCornerShape(22.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Image(
            painter = painterResource(R.drawable.wedding_priest),
            contentDescription = "Pastor",
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .border(1.5.dp, Color(0xFFBCAAA4), CircleShape),
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Pastor",
                color = Color(0xFF6D4C41),
                fontFamily = DisplayFont,
                fontSize = 13.sp
            )
            Text(
                shown,
                // Dunkel auf weißem Tile — TextPrimary wäre creme/grau und kaum lesbar
                color = Color(0xFF2C1810),
                fontFamily = BodyFont,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.heightIn(min = 34.dp)
            )
        }
    }
}

@Composable
fun FlameDecor(size: Dp, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "flame")
    val flicker by infinite.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flicker"
    )
    Image(
        painter = painterResource(R.drawable.wedding_flame),
        contentDescription = "Flamme",
        modifier = modifier.size(size * flicker.coerceIn(0.85f, 1.2f)),
        contentScale = ContentScale.Fit,
    )
}

@Composable
fun DecorMarker(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.wedding_candle),
        contentDescription = "Kerze",
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}
