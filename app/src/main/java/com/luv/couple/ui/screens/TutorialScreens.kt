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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.data.PeerPalette
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.LuvWordmark
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary

private enum class TutPage {
    Welcome,
    Nickname,
    Canvas,
    Gallery,
    Clear
}

@Composable
fun TutorialFlow(
    busy: Boolean,
    error: String?,
    googleEnabled: Boolean = false,
    googleSignedIn: Boolean = false,
    onGoogleSignIn: () -> Unit = {},
    replay: Boolean = false,
    existingNickname: String = "",
    onDismiss: (() -> Unit)? = null,
    onFinished: (nickname: String) -> Unit
) {
    val pages = remember(replay) {
        if (replay) {
            listOf(TutPage.Welcome, TutPage.Canvas, TutPage.Gallery, TutPage.Clear)
        } else {
            listOf(
                TutPage.Welcome,
                TutPage.Nickname,
                TutPage.Canvas,
                TutPage.Gallery,
                TutPage.Clear
            )
        }
    }
    var index by remember { mutableIntStateOf(0) }
    var nickname by remember {
        mutableStateOf(if (replay) existingNickname.trim() else "")
    }
    var showNickConfirm by remember { mutableStateOf(false) }
    val color = PeerPalette.composeColor(
        PeerPalette.indexFor(nickname.trim().lowercase().ifBlank { "a" })
    )
    val page = pages.getOrElse(index) { pages.last() }
    val lastIndex = pages.lastIndex

    LaunchedEffect(googleSignedIn, replay) {
        if (!replay && googleSignedIn && index == 0) {
            val nickIdx = pages.indexOf(TutPage.Nickname)
            if (nickIdx >= 0) index = nickIdx
        }
    }

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
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Seite ${index + 1} von ${pages.size}",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pages.indices.forEach { i ->
                        Box(
                            modifier = Modifier
                                .height(4.dp)
                                .weight(1f)
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (i <= index) AccentRose else Color.White.copy(0.12f))
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = page,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tut"
            ) { p ->
                when (p) {
                    TutPage.Welcome -> TutorialPane(
                        emojiDot = AccentRose,
                        title = if (replay) "Noch einmal kurz…" else "Hey… schön, dass du da bist",
                        body = "Hier teilen sich zwei Herzen eine Leinwand — oder bis zu zehn, wenn ihr mehr seid. Leise, warm, nur für euch."
                    )
                    TutPage.Nickname -> TutorialPane(
                        emojiDot = color,
                        title = "Und wie heißt du hier?",
                        body = "Dein Spitzname färbt deine Linien. Er kann später nicht mehr geändert werden."
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
                                    nickname.trim().take(1).uppercase().ifBlank { "?" },
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
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (nickname.trim().length >= 2) {
                                                showNickConfirm = true
                                            }
                                        }
                                    ),
                                    textStyle = TextStyle(
                                        color = TextPrimary,
                                        fontFamily = BodyFont,
                                        fontSize = 18.sp
                                    ),
                                    cursorBrush = SolidColor(AccentRose),
                                    decorationBox = { inner ->
                                        if (nickname.isBlank()) {
                                            Text(
                                                "z.B. Jane",
                                                color = TextMuted,
                                                fontFamily = BodyFont,
                                                fontSize = 18.sp
                                            )
                                        }
                                        inner()
                                    }
                                )
                            }
                        }
                    }
                    TutPage.Canvas -> TutorialPane(
                        emojiDot = Color(0xFFFFE29A),
                        title = "Eine Leinwand. Für euch.",
                        body = "In eurer Lobby malt ihr live zusammen. Ein Herzchen, ein Strich, ein kleines Hi — und plötzlich fühlt sich alles näher an."
                    )
                    TutPage.Gallery -> TutorialPane(
                        emojiDot = Color(0xFF9BB8FF),
                        title = "Eure Galerie",
                        body = "Was du auf der Leinwand speicherst, landet hier in der App — nur bei dir. Ansehen, teilen oder wieder löschen."
                    ) {
                        Spacer(modifier = Modifier.height(22.dp))
                        TutorialGalleryPreview()
                    }
                    TutPage.Clear -> TutorialPane(
                        emojiDot = Color(0xFFA8E6CF),
                        title = "Frisch anfangen — zusammen",
                        body = "Kurz halten, und ihr stimmt ab. Zwei Ja reichen — egal wie viele ihr seid. Ein Nein stoppt es."
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!error.isNullOrBlank()) {
                    Text(error, color = AccentRose, fontFamily = BodyFont, fontSize = 13.sp)
                }
                val needGoogle =
                    !replay && page == TutPage.Welcome && googleEnabled && !googleSignedIn
                if (needGoogle) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(AccentRose)
                            .clickable(enabled = !busy, onClick = onGoogleSignIn),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (busy) "Einen Moment…" else "Mit Google anmelden",
                            color = TextPrimary,
                            fontFamily = DisplayFont,
                            fontSize = 18.sp
                        )
                    }
                    Text(
                        "Nur ein Tippen — dann gehört dieser Platz dir.",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else {
                    val canAdvance = when {
                        needGoogle -> false
                        page == TutPage.Nickname && nickname.trim().length < 2 -> false
                        else -> true
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (canAdvance) AccentRose else AccentRose.copy(alpha = 0.35f))
                            .clickable(enabled = !busy && canAdvance) {
                                when {
                                    page == TutPage.Nickname -> showNickConfirm = true
                                    index < lastIndex -> index += 1
                                    else -> onFinished(
                                        nickname.trim().ifBlank { existingNickname.trim() }
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            when {
                                busy -> "Einen Moment…"
                                index < lastIndex -> "Weiter"
                                replay -> "Fertig"
                                else -> "Komm rein"
                            },
                            color = TextPrimary,
                            fontFamily = DisplayFont,
                            fontSize = 18.sp
                        )
                    }
                }
                when {
                    index > 0 -> {
                        Text(
                            "Zurück",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clickable(enabled = !busy) { index -= 1 }
                                .padding(8.dp)
                        )
                    }
                    replay && onDismiss != null -> {
                        Text(
                            "Abbrechen",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clickable(enabled = !busy, onClick = onDismiss)
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
    }

    if (showNickConfirm) {
        val chosen = nickname.trim()
        AlertDialog(
            onDismissRequest = { showNickConfirm = false },
            containerColor = BgSoft,
            title = {
                Text(
                    "Spitzname festlegen?",
                    fontFamily = DisplayFont,
                    fontSize = 22.sp,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    "„$chosen“ wird dein Name in LUV. Das lässt sich später nicht mehr ändern — bist du sicher?",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNickConfirm = false
                        if (index < lastIndex) index += 1
                        else onFinished(chosen)
                    }
                ) {
                    Text("Ja, so heißt ich", color = AccentRose, fontFamily = DisplayFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNickConfirm = false }) {
                    Text("Nochmal ändern", color = TextMuted, fontFamily = BodyFont)
                }
            }
        )
    }
}

@Composable
private fun TutorialGalleryPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TutorialMomentCard(
                modifier = Modifier.weight(1f),
                tint = Color(0xFF3D4A66),
                accent = AccentRose,
                label = "Ansehen"
            )
            TutorialMomentCard(
                modifier = Modifier.weight(1f),
                tint = Color(0xFF2F3D52),
                accent = Color(0xFF9BB8FF),
                label = "Teilen"
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BgSoft)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TutorialHintChip("Auswählen")
            TutorialHintChip("Löschen")
            Text(
                "Home · Sozial · Inventar · Markt",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TutorialMomentCard(
    modifier: Modifier,
    tint: Color,
    accent: Color,
    label: String
) {
    Box(
        modifier = modifier
            .height(112.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(tint)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(18.dp)
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(accent.copy(alpha = 0.55f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = 28.dp, end = 36.dp, top = 8.dp)
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.White.copy(alpha = 0.22f))
        )
        Text(
            label,
            color = TextPrimary,
            fontFamily = DisplayFont,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun TutorialHintChip(label: String) {
    Text(
        label,
        color = TextPrimary,
        fontFamily = DisplayFont,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
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
            .padding(vertical = 20.dp),
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
        LuvWordmark(fontSize = 42.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, fontFamily = DisplayFont, fontSize = 28.sp, color = TextPrimary, lineHeight = 34.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(body, fontFamily = BodyFont, color = TextMuted, fontSize = 16.sp, lineHeight = 24.sp)
        extra?.invoke()
    }
}
