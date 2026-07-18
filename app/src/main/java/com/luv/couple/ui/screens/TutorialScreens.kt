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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
    Vow,
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
            listOf(TutPage.Welcome, TutPage.Draw, TutPage.Vow, TutPage.Invite)
        } else {
            listOf(TutPage.Welcome, TutPage.Nickname, TutPage.Draw, TutPage.Vow, TutPage.Invite)
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
        initialValue = 0.18f,
        targetValue = 0.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    val heartPulse by breathe.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heart"
    )
    val shimmer by breathe.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
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
                        Color(0xFF0A0E14),
                        BgDeep,
                        Color(0xFF141018)
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AccentRose.copy(alpha = glow * 0.42f),
                            Color.Transparent
                        ),
                        center = Offset(0.12f, 0.05f),
                        radius = 1100f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaleBlue.copy(alpha = glow * 0.28f),
                            Color.Transparent
                        ),
                        center = Offset(0.95f, 0.82f),
                        radius = 920f
                    )
                )
        )
        // Leichter Lichtstreifen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.TopCenter)
                .padding(top = 120.dp)
                .alpha(0.25f + shimmer * 0.35f)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(0.35f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TutorialProgress(index = index, total = pages.size)

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    (fadeIn(tween(380)) + slideInVertically { it / 18 }) togetherWith
                        (fadeOut(tween(240)) + slideOutVertically { -it / 22 })
                },
                label = "tut",
                modifier = Modifier.weight(1f)
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
                    TutPage.Vow -> TutorialVowPane(shimmer = shimmer)
                    TutPage.Invite -> TutorialInvitePane(replay = replay)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        "Damit Lobbys und Ehe geräteübergreifend bleiben.",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        textAlign = TextAlign.Center
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
                                .padding(6.dp)
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
                                .padding(6.dp)
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LuvWordmark(fontSize = 20.sp, showHeart = true)
            Text(
                "${index + 1} · $total",
                color = TextMuted.copy(0.9f),
                fontFamily = BodyFont,
                fontSize = 12.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(total) { i ->
                Box(
                    modifier = Modifier
                        .height(2.5.dp)
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
                                Modifier.background(Color.White.copy(0.1f))
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
            .fillMaxSize()
            .padding(top = 20.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(108.dp)
                .scale(heartScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            AccentRose.copy(0.4f),
                            MaleBlue.copy(0.12f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            SketchedHeart(size = 52.dp, color = Color.White.copy(0.95f))
        }
        Spacer(modifier = Modifier.height(28.dp))
        LuvWordmark(fontSize = 56.sp, showHeart = false)
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            if (replay) "Willkommen zurück" else "Für euch beide",
            fontFamily = DisplayFont,
            fontSize = 28.sp,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            if (replay) {
                "Kurz die Leinwand, die Hochzeit und das Einladen — dann bist du wieder im Menü."
            } else {
                "Gemeinsam zeichnen. Momente behalten. Und wenn ihr wollt: heiraten."
            },
            fontFamily = BodyFont,
            color = TextMuted,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
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
            .fillMaxSize()
            .padding(top = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Dein Name in LUV",
            fontFamily = DisplayFont,
            fontSize = 30.sp,
            color = TextPrimary,
            lineHeight = 36.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Er färbt deine Linien. Einmal gewählt — für immer.",
            fontFamily = BodyFont,
            color = TextMuted,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(36.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(color, color.copy(0.5f))))
                    .border(2.dp, Color.White.copy(0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    nickname.trim().take(1).uppercase().ifBlank { "?" },
                    color = Color(0xFF10141C),
                    fontFamily = DisplayFont,
                    fontSize = 26.sp
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(0.04f))
                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 20.dp)
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
                        fontSize = 20.sp
                    ),
                    cursorBrush = SolidColor(AccentRose),
                    decorationBox = { inner ->
                        if (nickname.isBlank()) {
                            Text(
                                "z. B. Emrys",
                                color = TextMuted.copy(0.7f),
                                fontFamily = BodyFont,
                                fontSize = 20.sp
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
            .fillMaxSize()
            .padding(top = 4.dp)
    ) {
        Text(
            if (replay) "Kurz zeichnen" else "Deine erste Leinwand",
            fontFamily = DisplayFont,
            fontSize = 24.sp,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            if (replay) {
                "Nur zum Ausprobieren — wird nicht gespeichert."
            } else {
                "Ein Strich reicht. Daraus wird deine erste Lobby."
            },
            fontFamily = BodyFont,
            color = TextMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val side = min(maxWidth.value, maxHeight.value).dp
            Box(
                modifier = Modifier
                    .size(side)
                    .clip(RoundedCornerShape(26.dp))
                    .border(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(
                                MaleBlue.copy(0.55f),
                                AccentRose.copy(0.65f),
                                FemalePurple.copy(0.45f)
                            )
                        ),
                        RoundedCornerShape(26.dp)
                    )
                    .background(Color(0xFF0B1016))
            ) {
                var drawView by remember { mutableStateOf<DrawingView?>(null) }
                AndroidView(
                    factory = { ctx ->
                        DrawingView(ctx).apply {
                            myColorIndex = colorIndex
                            myBrushWidth = with(density) { 18.dp.toPx() }
                            canvasBackground = 0xFF0B1016.toInt()
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
                        "Tippen & ziehen",
                        color = TextMuted.copy(0.65f),
                        fontFamily = BodyFont,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .alpha(0.9f)
                    )
                } else {
                    Text(
                        "Doppeltipp = rückgängig",
                        color = TextMuted.copy(0.8f),
                        fontFamily = BodyFont,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(0.4f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

/** Ablauf Heirat + volle Hochzeitsleinwand-Vorschau (nicht zugeschnitten). */
@Composable
private fun TutorialVowPane(shimmer: Float) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 6.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Vom Freund zur Ehe",
            fontFamily = DisplayFont,
            fontSize = 26.sp,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Level 100 → Antrag gratis. Dann 7 Tage warten, 7 Tage malen — erst dann verheiratet. Überspringen kostet Coins.",
            fontFamily = BodyFont,
            color = TextMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Volle quadratische Hochzeitsleinwand — Fit, kein Crop
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val side = min(maxWidth.value, 340f).dp
            Box(
                modifier = Modifier
                    .size(side)
                    .clip(RoundedCornerShape(22.dp))
                    .border(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(
                                Color(0xFFFFD54F).copy(0.75f),
                                AccentRose.copy(0.55f),
                                Color(0xFFFFF8E7).copy(0.25f)
                            )
                        ),
                        RoundedCornerShape(22.dp)
                    )
            ) {
                TutorialWeddingCanvasPreview(shimmer = shimmer)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        TutorialTimelineStep("1", "Freundschaftslevel 100", "Heiratsanfrage kostenlos")
        Spacer(modifier = Modifier.height(8.dp))
        TutorialTimelineStep("2", "7 Tage Verlobung", "Danach öffnet die Hochzeitsleinwand")
        Spacer(modifier = Modifier.height(8.dp))
        TutorialTimelineStep("3", "7 Tage malen", "Gemeinsam — dann seid ihr verheiratet")
    }
}

@Composable
private fun TutorialWeddingCanvasPreview(shimmer: Float) {
    val ink = Color(0xFF2A1F28)
    val blush = Color(0xFFFFE4EC)
    val gold = Color(0xFFFFD54F)
    val rose = AccentRose
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Gesamte Fläche sichtbar — Portrait/Square-Lobby
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFFFFF8F2),
                    blush,
                    Color(0xFFFFF0F5)
                )
            )
        )
        // Weiches Licht
        drawCircle(
            brush = Brush.radialGradient(
                listOf(gold.copy(0.22f + shimmer * 0.08f), Color.Transparent)
            ),
            radius = w * 0.42f,
            center = Offset(w * 0.5f, h * 0.38f)
        )
        // Herz in der Mitte (vollständig im Frame)
        val heart = Path().apply {
            val cx = w * 0.5f
            val cy = h * 0.42f
            val s = min(w, h) * 0.22f
            moveTo(cx, cy + s * 0.35f)
            cubicTo(cx - s * 1.1f, cy - s * 0.15f, cx - s * 0.85f, cy - s * 0.95f, cx, cy - s * 0.45f)
            cubicTo(cx + s * 0.85f, cy - s * 0.95f, cx + s * 1.1f, cy - s * 0.15f, cx, cy + s * 0.35f)
            close()
        }
        drawPath(
            heart,
            brush = Brush.verticalGradient(listOf(rose.copy(0.85f), FemalePurple.copy(0.7f)))
        )
        drawPath(heart, color = gold.copy(0.55f), style = DrawStroke(width = 3.5f, cap = StrokeCap.Round))

        // Zwei Ringe unten — komplett sichtbar
        val r = min(w, h) * 0.07f
        val yR = h * 0.72f
        val x1 = w * 0.42f
        val x2 = w * 0.58f
        drawCircle(color = gold.copy(0.9f), radius = r, center = Offset(x1, yR), style = DrawStroke(5f))
        drawCircle(color = rose.copy(0.9f), radius = r, center = Offset(x2, yR), style = DrawStroke(5f))

        // Namen-Zeile
        drawRoundRect(
            color = ink.copy(0.08f),
            topLeft = Offset(w * 0.18f, h * 0.84f),
            size = Size(w * 0.64f, h * 0.07f),
            cornerRadius = CornerRadius(20f, 20f)
        )
        // Dezente Randlinie der „Lobby“
        drawRoundRect(
            color = gold.copy(0.35f),
            topLeft = Offset(w * 0.04f, h * 0.04f),
            size = Size(w * 0.92f, h * 0.92f),
            cornerRadius = CornerRadius(28f, 28f),
            style = DrawStroke(width = 2.5f)
        )
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            "Hochzeitsleinwand",
            color = Color(0xFF5A3040).copy(0.75f),
            fontFamily = DisplayFont,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}

@Composable
private fun TutorialTimelineStep(num: String, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(Color(0xFFFFD54F).copy(0.35f), AccentRose.copy(0.3f)))
                )
                .border(1.dp, Color(0xFFFFD54F).copy(0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(num, color = TextPrimary, fontFamily = DisplayFont, fontSize = 13.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontFamily = DisplayFont, fontSize = 15.sp)
            Text(body, color = TextMuted, fontFamily = BodyFont, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun TutorialInvitePane(replay: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (replay) "Zusammen bleiben" else "Jemanden einladen",
            fontFamily = DisplayFont,
            fontSize = 28.sp,
            color = TextPrimary,
            lineHeight = 34.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            if (replay) {
                "Im Menü öffnest du Lobbys, speicherst Momente und lädst Freunde ein."
            } else {
                "Deine Skizze liegt gleich als Lobby im Hauptmenü. Tippe drauf — oder teile den Code."
            },
            fontFamily = BodyFont,
            color = TextMuted,
            fontSize = 15.sp,
            lineHeight = 23.sp
        )
        Spacer(modifier = Modifier.height(28.dp))
        TutorialInviteLine("01", "Lobby öffnen", MaleBlue)
        Spacer(modifier = Modifier.height(14.dp))
        TutorialInviteLine("02", "Code teilen", AccentRose)
        Spacer(modifier = Modifier.height(14.dp))
        TutorialInviteLine("03", "Moment speichern", FemalePurple)
    }
}

@Composable
private fun TutorialInviteLine(num: String, title: String, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            num,
            color = tint.copy(0.9f),
            fontFamily = DisplayFont,
            fontSize = 18.sp,
            modifier = Modifier.width(36.dp)
        )
        Box(
            modifier = Modifier
                .height(1.dp)
                .width(28.dp)
                .background(tint.copy(0.45f))
        )
        Text(title, color = TextPrimary, fontFamily = DisplayFont, fontSize = 17.sp)
    }
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
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (enabled) {
                    Modifier.background(
                        Brush.horizontalGradient(
                            listOf(AccentRose, AccentRose.copy(0.9f), FemalePurple.copy(0.82f))
                        )
                    )
                } else {
                    Modifier.background(AccentRose.copy(alpha = 0.28f))
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = TextPrimary,
            fontFamily = DisplayFont,
            fontSize = 17.sp
        )
    }
}
