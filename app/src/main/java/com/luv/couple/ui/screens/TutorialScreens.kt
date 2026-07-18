package com.luv.couple.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.luv.couple.data.PeerPalette
import com.luv.couple.data.Stroke
import com.luv.couple.data.StrokePoint
import com.luv.couple.lock.CanvasStore
import com.luv.couple.lock.DrawingView
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.FemalePurple
import com.luv.couple.ui.theme.LuvWordmark
import com.luv.couple.ui.theme.MaleBlue
import com.luv.couple.ui.theme.SketchedHeart
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import java.util.UUID
import kotlin.math.min

private enum class TutPage {
    Welcome,
    Nickname,
    Draw,
    Invite
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
    onFinished: (nickname: String, strokes: List<Stroke>) -> Unit
) {
    val pages = remember(replay) {
        if (replay) {
            listOf(TutPage.Welcome, TutPage.Draw, TutPage.Invite)
        } else {
            listOf(TutPage.Welcome, TutPage.Nickname, TutPage.Draw, TutPage.Invite)
        }
    }
    var index by remember { mutableIntStateOf(0) }
    var nickname by remember {
        mutableStateOf(if (replay) existingNickname.trim() else "")
    }
    var showNickConfirm by remember { mutableStateOf(false) }
    val tutorialStrokes = remember { mutableStateListOf<Stroke>() }
    var canvasShortSide by remember { mutableFloatStateOf(1000f) }
    val colorIndex = PeerPalette.indexFor(nickname.trim().lowercase().ifBlank { "a" })
    val color = PeerPalette.composeColor(colorIndex)
    val page = pages.getOrElse(index) { pages.last() }
    val lastIndex = pages.lastIndex

    val breathe = rememberInfiniteTransition(label = "tutGlow")
    val glow by breathe.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(3400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    val heartPulse by breathe.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heart"
    )

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
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF10151C),
                        BgDeep,
                        Color(0xFF16101A)
                    )
                )
            )
    ) {
        // Atmosphäre — weiche Lichtflecken, kein flaches Flat
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AccentRose.copy(alpha = glow * 0.55f),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(0.15f, 0.08f),
                        radius = 900f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaleBlue.copy(alpha = glow * 0.35f),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(0.92f, 0.78f),
                        radius = 780f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            TutorialProgress(index = index, total = pages.size)

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    (fadeIn(tween(320)) + slideInHorizontally { it / 14 }) togetherWith
                        (fadeOut(tween(220)) + slideOutHorizontally { -it / 18 })
                },
                label = "tut",
                modifier = Modifier.weight(1f, fill = false)
            ) { p ->
                when (p) {
                    TutPage.Welcome -> TutorialWelcome(
                        replay = replay,
                        heartScale = heartPulse
                    )
                    TutPage.Nickname -> TutorialNickname(
                        nickname = nickname,
                        onNicknameChange = { nickname = it.take(18) },
                        color = color,
                        onImeDone = {
                            if (nickname.trim().length >= 2) showNickConfirm = true
                        }
                    )
                    TutPage.Draw -> TutorialDrawPad(
                        colorIndex = colorIndex,
                        strokes = tutorialStrokes.toList(),
                        onStroke = { points, widthPx, shortSide ->
                            canvasShortSide = shortSide
                            val stored = CanvasStore.toStoredWidth(widthPx, shortSide)
                            tutorialStrokes.add(
                                Stroke(
                                    id = UUID.randomUUID().toString(),
                                    points = points,
                                    width = stored,
                                    isLocal = true,
                                    nickname = nickname.trim().ifBlank { existingNickname.trim() }
                                        .ifBlank { null },
                                    colorIndex = colorIndex,
                                    colorLocked = true
                                )
                            )
                        },
                        onUndo = {
                            if (tutorialStrokes.isNotEmpty()) {
                                tutorialStrokes.removeAt(tutorialStrokes.lastIndex)
                            }
                        },
                        onDot = { point, widthPx, shortSide ->
                            canvasShortSide = shortSide
                            val stored = CanvasStore.toStoredWidth(widthPx, shortSide)
                            tutorialStrokes.add(
                                Stroke(
                                    id = UUID.randomUUID().toString(),
                                    points = listOf(point),
                                    width = stored,
                                    isLocal = true,
                                    nickname = nickname.trim().ifBlank { existingNickname.trim() }
                                        .ifBlank { null },
                                    colorIndex = colorIndex,
                                    colorLocked = true
                                )
                            )
                        },
                        replay = replay
                    )
                    TutPage.Invite -> TutorialInvitePane(replay = replay)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!error.isNullOrBlank()) {
                    Text(error, color = AccentRose, fontFamily = BodyFont, fontSize = 13.sp)
                }
                val needGoogle =
                    !replay && page == TutPage.Welcome && googleEnabled && !googleSignedIn
                if (needGoogle) {
                    TutorialPrimaryButton(
                        label = if (busy) "Einen Moment…" else "Mit Google anmelden",
                        enabled = !busy,
                        onClick = onGoogleSignIn
                    )
                    Text(
                        "Nur ein Tippen — dann gehört dieser Platz dir.",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else {
                    val needStroke = page == TutPage.Draw && tutorialStrokes.isEmpty() && !replay
                    val canAdvance = when {
                        needGoogle -> false
                        page == TutPage.Nickname && nickname.trim().length < 2 -> false
                        needStroke -> false
                        else -> true
                    }
                    TutorialPrimaryButton(
                        label = when {
                            busy -> "Einen Moment…"
                            page == TutPage.Draw && needStroke -> "Zeichne etwas…"
                            index < lastIndex -> "Weiter"
                            replay -> "Fertig"
                            else -> "Los geht’s"
                        },
                        enabled = !busy && canAdvance,
                        onClick = {
                            when {
                                page == TutPage.Nickname -> showNickConfirm = true
                                index < lastIndex -> index += 1
                                else -> onFinished(
                                    nickname.trim().ifBlank { existingNickname.trim() },
                                    tutorialStrokes.toList()
                                )
                            }
                        }
                    )
                    if (page == TutPage.Draw && !replay) {
                        Text(
                            "Dein erstes Bild wird eine echte Lobby im Hauptmenü.",
                            color = TextMuted.copy(alpha = 0.9f),
                            fontFamily = BodyFont,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                    if (page == TutPage.Draw && replay) {
                        Text(
                            "Nur zum Ausprobieren — wird nicht als Lobby gespeichert.",
                            color = TextMuted.copy(alpha = 0.9f),
                            fontFamily = BodyFont,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
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
                        else onFinished(chosen, tutorialStrokes.toList())
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
private fun TutorialProgress(index: Int, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LuvWordmark(fontSize = 22.sp, showHeart = true)
            Text(
                "${index + 1} / $total",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 13.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { i ->
                Box(
                    modifier = Modifier
                        .height(3.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(99.dp))
                        .then(
                            if (i <= index) {
                                Modifier.background(
                                    Brush.horizontalGradient(
                                        listOf(MaleBlue, AccentRose, FemalePurple)
                                    )
                                )
                            } else {
                                Modifier.background(Color.White.copy(0.12f))
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun TutorialWelcome(replay: Boolean, heartScale: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .scale(heartScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(AccentRose.copy(0.35f), Color.Transparent)
                    )
                )
                .border(1.dp, Color.White.copy(0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            SketchedHeart(size = 42.dp, color = Color.White.copy(0.92f))
        }
        Spacer(modifier = Modifier.height(28.dp))
        LuvWordmark(fontSize = 48.sp, showHeart = false)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            if (replay) "Noch einmal kurz…" else "Schön, dass du da bist",
            fontFamily = DisplayFont,
            fontSize = 30.sp,
            color = TextPrimary,
            lineHeight = 36.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            if (replay) {
                "Ein paar Schritte, damit du dich wieder zurechtfindest — ganz ohne neue Lobby."
            } else {
                "Eine gemeinsame Leinwand für zwei Herzen — oder bis zu zehn. Zeichne, speichere Momente, bleibt unter euch."
            },
            fontFamily = BodyFont,
            color = TextMuted,
            fontSize = 16.sp,
            lineHeight = 24.sp
        )
        Spacer(modifier = Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TutorialSoftChip("Live zeichnen", MaleBlue)
            TutorialSoftChip("Galerie", AccentRose)
            TutorialSoftChip("Zusammen", FemalePurple)
        }
    }
}

@Composable
private fun TutorialNickname(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    color: Color,
    onImeDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp)
    ) {
        Text(
            "Wie heißt du hier?",
            fontFamily = DisplayFont,
            fontSize = 28.sp,
            color = TextPrimary,
            lineHeight = 34.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Dein Spitzname färbt deine Linien. Einmal gewählt — für immer deiner.",
            fontFamily = BodyFont,
            color = TextMuted,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(28.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(color, color.copy(0.55f)))
                    )
                    .border(2.dp, Color.White.copy(0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    nickname.trim().take(1).uppercase().ifBlank { "?" },
                    color = Color(0xFF12161E),
                    fontFamily = DisplayFont,
                    fontSize = 22.sp
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(BgSoft)
                    .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                BasicTextField(
                    value = nickname,
                    onValueChange = onNicknameChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onImeDone() }),
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontFamily = BodyFont,
                        fontSize = 18.sp
                    ),
                    cursorBrush = SolidColor(AccentRose),
                    decorationBox = { inner ->
                        if (nickname.isBlank()) {
                            Text(
                                "z.B. Emrys",
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
}

@Composable
private fun TutorialDrawPad(
    colorIndex: Int,
    strokes: List<Stroke>,
    onStroke: (List<StrokePoint>, Float, Float) -> Unit,
    onUndo: () -> Unit,
    onDot: (StrokePoint, Float, Float) -> Unit,
    replay: Boolean
) {
    val density = LocalDensity.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp)
    ) {
        Text(
            if (replay) "Nochmal tippen & ziehen" else "Zeichne etwas Kleines",
            fontFamily = DisplayFont,
            fontSize = 26.sp,
            color = TextPrimary,
            lineHeight = 32.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            if (replay) {
                "Probier die Leinwand — danach bist du wieder im Menü."
            } else {
                "Ein Herz, ein Hi, ein Strich. Danach wird daraus deine erste Lobby."
            },
            fontFamily = BodyFont,
            color = TextMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .border(
                    1.5.dp,
                    Brush.linearGradient(listOf(MaleBlue.copy(0.45f), AccentRose.copy(0.55f), FemalePurple.copy(0.4f))),
                    RoundedCornerShape(28.dp)
                )
                .background(Color(0xFF0C1218))
        ) {
            var drawView by remember { mutableStateOf<DrawingView?>(null) }
            AndroidView(
                factory = { ctx ->
                    DrawingView(ctx).apply {
                        myColorIndex = colorIndex
                        myBrushWidth = with(density) { 18.dp.toPx() }
                        canvasBackground = 0xFF0C1218.toInt()
                        drawView = this
                        onStrokeFinished = { pts ->
                            val short = min(width, height).toFloat().coerceAtLeast(1f)
                            onStroke(pts, myBrushWidth, short)
                        }
                        onDoubleTapUndo = onUndo
                        onDotPlaced = { pt ->
                            val short = min(width, height).toFloat().coerceAtLeast(1f)
                            onDot(pt, myBrushWidth, short)
                        }
                    }
                },
                update = { view ->
                    view.myColorIndex = colorIndex
                    view.setStrokes(strokes, animateNew = false)
                },
                modifier = Modifier.fillMaxSize()
            )
            DisposableEffect(Unit) {
                onDispose { drawView = null }
            }
            if (strokes.isEmpty()) {
                Text(
                    "Hier tippen & zeichnen",
                    color = TextMuted.copy(0.7f),
                    fontFamily = BodyFont,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .alpha(0.85f)
                )
            }
            if (strokes.isNotEmpty()) {
                Text(
                    "Doppeltipp = rückgängig",
                    color = TextMuted.copy(0.75f),
                    fontFamily = BodyFont,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(0.35f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun TutorialInvitePane(replay: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(MaleBlue.copy(0.28f), FemalePurple.copy(0.28f))
                    )
                )
                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("💌", fontSize = 30.sp)
        }
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            if (replay) "Und dann zusammen" else "Als Nächstes: jemanden einladen",
            fontFamily = DisplayFont,
            fontSize = 28.sp,
            color = TextPrimary,
            lineHeight = 34.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            if (replay) {
                "Im Hauptmenü öffnest du Lobbys, speicherst Momente und lädst Freunde ein."
            } else {
                "Deine Skizze liegt gleich als Lobby im Hauptmenü. Tippe darauf, um weiterzumalen — oder lade jemanden mit dem Code ein."
            },
            fontFamily = BodyFont,
            color = TextMuted,
            fontSize = 16.sp,
            lineHeight = 24.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TutorialFeatureRow("Lobby tippen", "Leinwand öffnen")
            TutorialFeatureRow("Code teilen", "Zusammen zeichnen")
            TutorialFeatureRow("Moment speichern", "In der Galerie behalten")
        }
    }
}

@Composable
private fun TutorialFeatureRow(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgSoft.copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(0.06f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(AccentRose)
        )
        Column {
            Text(title, color = TextPrimary, fontFamily = DisplayFont, fontSize = 15.sp)
            Text(subtitle, color = TextMuted, fontFamily = BodyFont, fontSize = 13.sp)
        }
    }
}

@Composable
private fun TutorialSoftChip(label: String, tint: Color) {
    Text(
        label,
        color = TextPrimary,
        fontFamily = DisplayFont,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = 0.18f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun TutorialPrimaryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (enabled) {
                    Modifier.background(
                        Brush.horizontalGradient(
                            listOf(AccentRose, AccentRose.copy(0.88f), FemalePurple.copy(0.85f))
                        )
                    )
                } else {
                    Modifier.background(AccentRose.copy(alpha = 0.32f))
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = TextPrimary,
            fontFamily = DisplayFont,
            fontSize = 18.sp
        )
    }
}
