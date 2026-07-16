package com.luv.couple.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.data.PeerPalette
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary

@Composable
fun TutorialFlow(
    busy: Boolean,
    error: String?,
    onFinished: (nickname: String) -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var nickname by remember { mutableStateOf("") }
    val color = PeerPalette.composeColor(PeerPalette.indexFor(nickname.trim().lowercase().ifBlank { "a" }))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF121821), BgDeep, Color(0xFF1A1220)))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(4) { i ->
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (i <= step) AccentRose else Color.White.copy(0.12f))
                    )
                }
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tut"
            ) { s ->
                when (s) {
                    0 -> TutorialPane(
                        emojiDot = AccentRose,
                        title = "Hey, schÃ¶n dass du da bist",
                        body = "LUV verbindet bis zu vier Herzen auf einer gemeinsamen Sperrbildschirm-Leinwand â€” live, sÃ¼ÃŸ, ohne Chaos."
                    )
                    1 -> TutorialPane(
                        emojiDot = color,
                        title = "Wie sollen wir dich nennen?",
                        body = "Dein Spitzname fÃ¤rbt deine Linien. So sieht jeder sofort, wer was gemalt hat."
                    ) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(color),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    (nickname.trim().take(1).ifBlank { "â™¥" }).uppercase(),
                                    color = Color(0xFF1A1F2E),
                                    fontFamily = DisplayFont,
                                    fontSize = 20.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(BgSoft)
                                    .padding(18.dp)
                            ) {
                                BasicTextField(
                                    value = nickname,
                                    onValueChange = { nickname = it.take(18) },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = TextPrimary,
                                        fontFamily = BodyFont,
                                        fontSize = 18.sp
                                    ),
                                    cursorBrush = SolidColor(AccentRose),
                                    decorationBox = { inner ->
                                        if (nickname.isBlank()) {
                                            Text("z.â€¯B. Jane", color = TextMuted, fontFamily = BodyFont, fontSize = 18.sp)
                                        }
                                        inner()
                                    }
                                )
                            }
                        }
                    }
                    2 -> TutorialPane(
                        emojiDot = Color(0xFFFFE29A),
                        title = "Jeden Tag ein bisschen Magie",
                        body = "Du bekommst tÃ¤glich 10 Coins und 5 freie Lobby-Sessions. Danach kostet eine Session 1 Coin â€” Zuschauen bleibt immer gratis."
                    )
                    else -> TutorialPane(
                        emojiDot = Color(0xFFA8E6CF),
                        title = "Gemeinsam lÃ¶schen",
                        body = "2 Sekunden halten startet eine Abstimmung. Erst wenn mehr als die HÃ¤lfte Ja sagt, wird die Leinwand geleert. Fair fÃ¼r alle."
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!error.isNullOrBlank()) {
                    Text(error, color = AccentRose, fontFamily = BodyFont, fontSize = 13.sp)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(AccentRose)
                        .clickable(enabled = !busy) {
                            when {
                                step < 3 -> {
                                    if (step == 1 && nickname.trim().length < 2) return@clickable
                                    step += 1
                                }
                                else -> onFinished(nickname.trim())
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when {
                            busy -> "Einen Momentâ€¦"
                            step < 3 -> "Weiter"
                            else -> "Los gehtâ€™s â™¥"
                        },
                        color = TextPrimary,
                        fontFamily = DisplayFont,
                        fontSize = 18.sp
                    )
                }
                if (step > 0) {
                    Text(
                        "ZurÃ¼ck",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clickable(enabled = !busy) { step -= 1 }
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TutorialPane(
    emojiDot: Color,
    title: String,
    body: String,
    extra: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(emojiDot.copy(alpha = 0.25f))
                .border(1.dp, emojiDot.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(emojiDot)
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            "LUV",
            fontFamily = DisplayFont,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            letterSpacing = 3.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, fontFamily = DisplayFont, fontSize = 28.sp, color = TextPrimary, lineHeight = 34.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(body, fontFamily = BodyFont, color = TextMuted, fontSize = 16.sp, lineHeight = 24.sp)
        extra?.invoke()
    }
}

