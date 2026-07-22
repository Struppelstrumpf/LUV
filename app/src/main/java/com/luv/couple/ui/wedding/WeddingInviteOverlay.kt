package com.luv.couple.ui.wedding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.data.RoomPreview
import com.luv.couple.ui.screens.PrimaryButton
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Gold = Color(0xFFFFD54F)
private val Blush = Color(0xFFFFB6C8)
private val Ivory = Color(0xFFFFF6F0)

/** Countdown als „2 Tage, 5 Stunden, 12 Minuten“. */
fun formatInviteCountdown(ms: Long): String {
    val rem = ms.coerceAtLeast(0L)
    val d = rem / (24 * 60 * 60 * 1000L)
    val h = (rem % (24 * 60 * 60 * 1000L)) / (60 * 60 * 1000L)
    val m = (rem % (60 * 60 * 1000L)) / 60_000L
    return buildString {
        if (d > 0L) {
            append(d)
            append(if (d == 1L) " Tag" else " Tage")
            append(", ")
        }
        append(h)
        append(if (h == 1L) " Stunde" else " Stunden")
        append(", ")
        append(m)
        append(if (m == 1L) " Minute" else " Minuten")
    }
}

@Composable
fun WeddingInviteOverlay(
    preview: RoomPreview?,
    loading: Boolean,
    error: String?,
    busy: Boolean = false,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    var cardBmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val imageUrl = preview?.inviteImageUrl
    val notFound = !loading && preview == null && !error.isNullOrBlank()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(preview?.ceremonyAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    LaunchedEffect(imageUrl) {
        cardBmp = null
        val url = imageUrl?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        cardBmp = withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val req = okhttp3.Request.Builder().url(url).get().build()
                com.luv.couple.net.LuvApiClient.httpClient().newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    resp.body?.byteStream()?.use { android.graphics.BitmapFactory.decodeStream(it) }
                }
            }.getOrNull()
        }
    }

    val couple = listOfNotNull(
        preview?.coupleNameA?.trim()?.takeIf { it.isNotBlank() },
        preview?.coupleNameB?.trim()?.takeIf { it.isNotBlank() }
    ).joinToString(" & ").ifBlank {
        preview?.hostNickname?.takeIf { it.isNotBlank() } ?: "Ein Paar"
    }
    val until = ((preview?.ceremonyAt ?: 0L) - now).coerceAtLeast(0L)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A0E18), BgDeep, Color(0xFF120C14))
                )
            )
    ) {
        if (cardBmp != null) {
            Image(
                bitmap = cardBmp!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC120A12))
            )
        }
        // Dekorative Blüten-Ecken
        Text(
            "🌸",
            fontSize = 42.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(18.dp)
                .systemBarsPadding()
        )
        Text(
            "💐",
            fontSize = 42.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(18.dp)
                .systemBarsPadding()
        )
        Text(
            "🌹",
            fontSize = 36.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
                .systemBarsPadding()
        )
        Text(
            "💮",
            fontSize = 36.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .systemBarsPadding()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Ivory.copy(alpha = 0.14f), BgSoft.copy(alpha = 0.95f))
                        )
                    )
                    .border(1.5.dp, Gold.copy(0.55f), RoundedCornerShape(28.dp))
                    .padding(horizontal = 22.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("💒", fontSize = 36.sp)
                Text(
                    "Hochzeitseinladung",
                    color = Gold,
                    fontFamily = DisplayFont,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                when {
                    notFound -> Text(
                        error ?: "Einladung nicht gefunden.",
                        color = TextPrimary,
                        fontFamily = DisplayFont,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center
                    )
                    loading -> Text(
                        "Einladung wird geladen…",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    else -> {
                        Text(
                            couple,
                            color = Blush,
                            fontFamily = DisplayFont,
                            fontSize = 28.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 34.sp
                        )
                        Text(
                            "laden dich zu ihrer Hochzeit ein",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        if ((preview?.ceremonyAt ?: 0L) > 0L) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                formatCeremonyAt(preview!!.ceremonyAt),
                                color = TextPrimary,
                                fontFamily = DisplayFont,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Noch ${formatInviteCountdown(until)}",
                                color = Gold,
                                fontFamily = DisplayFont,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        Text(
                            "Komm als Gast mit — die Kapelle öffnet rechtzeitig vor der Trauung.",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
                if (!error.isNullOrBlank() && preview != null) {
                    Text(
                        error,
                        color = AccentRose,
                        fontFamily = BodyFont,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (notFound) {
                    PrimaryButton("Zurück", AccentRose, onDecline, enabled = !busy)
                } else {
                    PrimaryButton(
                        label = if (busy) "…" else "An Hochzeit teilnehmen",
                        color = AccentRose,
                        onClick = onAccept,
                        enabled = preview != null && !loading && !busy
                    )
                    PrimaryButton(
                        "Ablehnen",
                        BgSoft,
                        onDecline,
                        bordered = true,
                        enabled = !busy
                    )
                }
            }
        }
    }
}
