package com.luv.couple.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlin.math.hypot
import com.luv.couple.LuvApp
import com.luv.couple.data.PeerPalette
import com.luv.couple.data.Stroke
import com.luv.couple.data.StrokePoint
import com.luv.couple.lock.CanvasStore
import com.luv.couple.lock.DrawingView
import com.luv.couple.profile.ProfileCatalog
import com.luv.couple.profile.ProfileElType
import com.luv.couple.profile.ProfileLayoutEl
import com.luv.couple.profile.ProfileState
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.LuvWordmark
import com.luv.couple.ui.theme.MaleBlue
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import java.util.UUID
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TUTORIAL_DOG = "🐕"

private enum class TutPage {
    Nickname,
    Draw,
    Profile,
    Celebrate,
    Google
}

data class TutorialFinishPayload(
    val nickname: String,
    val strokes: List<Stroke>,
    val profileJson: String
)

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
    onFinished: (TutorialFinishPayload) -> Unit
) {
    val pages = remember(replay, googleSignedIn) {
        if (replay) {
            listOf(TutPage.Draw, TutPage.Celebrate)
        } else if (googleSignedIn) {
            // Schon per „Ich habe ein Konto“ / Google verknüpft → kein zweites Google
            listOf(
                TutPage.Nickname,
                TutPage.Draw,
                TutPage.Profile,
                TutPage.Celebrate
            )
        } else {
            listOf(
                TutPage.Nickname,
                TutPage.Draw,
                TutPage.Profile,
                TutPage.Celebrate,
                TutPage.Google
            )
        }
    }
    var index by remember { mutableIntStateOf(0) }
    var nickname by remember {
        mutableStateOf(if (replay) existingNickname.trim() else "")
    }
    var showNickConfirm by remember { mutableStateOf(false) }
    val tutorialStrokes = remember { mutableStateListOf<Stroke>() }
    var drawReady by remember { mutableStateOf(false) }
    var dogPlaced by remember { mutableStateOf(false) }
    var dogEl by remember {
        mutableStateOf(
            ProfileLayoutEl(
                id = "stk-tutorial-dog",
                type = ProfileElType.Sticker,
                x = 55f,
                y = 52f,
                scale = 1.15f,
                z = 30,
                emoji = TUTORIAL_DOG
            )
        )
    }
    var profileJson by remember { mutableStateOf("") }
    var fadeCover by remember { mutableFloatStateOf(0f) }
    val finishLock = remember { booleanArrayOf(false) }
    val page = pages.getOrElse(index) { pages.last() }
    val colorIndex = PeerPalette.indexFor(nickname.trim().lowercase().ifBlank { "a" })
    val color = PeerPalette.composeColor(colorIndex)
    val scope = rememberCoroutineScope()

    fun buildProfileJson(nick: String): String {
        if (profileJson.isNotBlank()) return profileJson
        val layout = ProfileCatalog.defaultLayout(nick).toMutableList()
        if (dogPlaced) layout.add(dogEl.copy(emoji = TUTORIAL_DOG))
        return ProfileCatalog.encode(ProfileState(layout = layout).normalized(nick))
    }

    fun emitFinished(payload: TutorialFinishPayload) {
        if (finishLock[0]) return
        finishLock[0] = true
        onFinished(payload)
    }

    fun finishOnboardingNow() {
        val nick = nickname.trim().ifBlank { existingNickname.trim() }.ifBlank { "Luv" }
        emitFinished(
            TutorialFinishPayload(
                nickname = nick,
                strokes = tutorialStrokes.toList(),
                profileJson = buildProfileJson(nick)
            )
        )
    }

    val breathe = rememberInfiniteTransition(label = "tutGlow")
    val glow by breathe.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // Nach Google am Ende -> Onboarding abschliessen
    LaunchedEffect(googleSignedIn, page, replay) {
        if (!replay && googleSignedIn && page == TutPage.Google) {
            finishOnboardingNow()
        }
    }

    // Seitenliste kürzer geworden (Google weg) → Index clampen
    LaunchedEffect(pages.size) {
        if (index > pages.lastIndex) index = pages.lastIndex
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A0E14), BgDeep, Color(0xFF141018))
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentRose.copy(alpha = glow * 0.35f), Color.Transparent),
                        center = Offset(0.15f, 0.08f),
                        radius = 1000f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            TutorialProgress(index = index, total = pages.size)
            Spacer(modifier = Modifier.height(10.dp))

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    (fadeIn(tween(320)) + slideInVertically { it / 14 }) togetherWith
                        (fadeOut(tween(220)) + slideOutVertically { -it / 18 })
                },
                label = "tut",
                modifier = Modifier.weight(1f)
            ) { p ->
                when (p) {
                    TutPage.Nickname -> TutorialNickname(
                        nickname = nickname,
                        onNicknameChange = { nickname = it.take(18) },
                        color = color,
                        googleEnabled = googleEnabled && !googleSignedIn && !replay,
                        googleBusy = busy,
                        onHaveAccount = onGoogleSignIn,
                        onImeDone = {
                            if (nickname.trim().length >= 2) showNickConfirm = true
                        }
                    )
                    TutPage.Draw -> TutorialDrawPad(
                        nickname = nickname.trim().ifBlank { existingNickname.trim() },
                        colorIndex = colorIndex,
                        strokes = tutorialStrokes.toList(),
                        drawReady = drawReady,
                        replay = replay,
                        onUserStroke = { points, widthPx, shortSide ->
                            // Mindestens ein echter Strich (keine Einzelpunkte)
                            if (points.size < 8) return@TutorialDrawPad
                            val stored = CanvasStore.toStoredWidth(widthPx, shortSide)
                            tutorialStrokes.add(
                                Stroke(
                                    id = UUID.randomUUID().toString(),
                                    points = points,
                                    width = stored,
                                    isLocal = true,
                                    nickname = nickname.trim().ifBlank { null },
                                    colorIndex = colorIndex,
                                    colorLocked = true
                                )
                            )
                            drawReady = true
                        },
                        onUndo = {
                            if (tutorialStrokes.isNotEmpty()) {
                                tutorialStrokes.removeAt(tutorialStrokes.lastIndex)
                            }
                            drawReady = tutorialStrokes.any { it.points.size >= 8 }
                        },
                        onContinueToProfile = {
                            if (replay) {
                                index = pages.indexOf(TutPage.Celebrate).coerceAtLeast(0)
                            } else {
                                index = pages.indexOf(TutPage.Profile).coerceAtLeast(index + 1)
                            }
                        }
                    )
                    TutPage.Profile -> TutorialProfileStickerStep(
                        nickname = nickname.trim().ifBlank { "Du" },
                        colorIndex = colorIndex,
                        dogPlaced = dogPlaced,
                        dogEl = dogEl,
                        onDogChanged = { el ->
                            dogEl = el
                            dogPlaced = true
                            val nick = nickname.trim().ifBlank { "Du" }
                            val layout = ProfileCatalog.defaultLayout(nick).toMutableList()
                            layout.add(el.copy(emoji = TUTORIAL_DOG))
                            profileJson = ProfileCatalog.encode(
                                ProfileState(layout = layout).normalized(nick)
                            )
                        },
                        onContinue = {
                            index = pages.indexOf(TutPage.Celebrate).coerceAtLeast(index + 1)
                        }
                    )
                    TutPage.Celebrate -> TutorialCelebrateStep(
                        dogPlaced = dogPlaced || replay,
                        onDone = {
                            scope.launch {
                                fadeCover = 0f
                                val anim = Animatable(0f)
                                anim.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) {
                                    fadeCover = value
                                }
                                if (replay) {
                                    emitFinished(
                                        TutorialFinishPayload(
                                            nickname = existingNickname.trim().ifBlank { "Luv" },
                                            strokes = emptyList(),
                                            profileJson = ""
                                        )
                                    )
                                } else if (googleSignedIn || pages.none { it == TutPage.Google }) {
                                    fadeCover = 0f
                                    finishOnboardingNow()
                                } else {
                                    index = pages.indexOf(TutPage.Google).coerceAtLeast(index)
                                    fadeCover = 0f
                                }
                            }
                        }
                    )
                    TutPage.Google -> TutorialGoogleStep(
                        busy = busy,
                        googleEnabled = googleEnabled,
                        googleSignedIn = googleSignedIn,
                        onGoogleSignIn = onGoogleSignIn
                    )
                }
            }

            if (page != TutPage.Celebrate && page != TutPage.Profile && page != TutPage.Draw) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!error.isNullOrBlank()) {
                        Text(error, color = AccentRose, fontFamily = BodyFont, fontSize = 13.sp)
                    }
                    when (page) {
                        TutPage.Nickname -> {
                            TutorialPrimaryButton(
                                label = if (busy) "…" else "Weiter",
                                enabled = !busy && nickname.trim().length >= 2,
                                onClick = { showNickConfirm = true }
                            )
                        }
                        TutPage.Google -> {
                            if (!googleEnabled) {
                                TutorialPrimaryButton(
                                    label = if (busy) "…" else "Los geht’s",
                                    enabled = !busy,
                                    onClick = { finishOnboardingNow() }
                                )
                            }
                        }
                        else -> Unit
                    }
                    if (replay && onDismiss != null && index == 0) {
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

        if (fadeCover > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(40f)
                    .background(Color(0xFF0B0E14).copy(alpha = fadeCover))
                    .scale(0.98f + 0.02f * fadeCover)
            )
        }
    }

    if (showNickConfirm) {
        val chosen = nickname.trim()
        AlertDialog(
            onDismissRequest = { showNickConfirm = false },
            containerColor = BgSoft,
            title = {
                Text(
                    "So heißt du?",
                    fontFamily = DisplayFont,
                    fontSize = 22.sp,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    "„$chosen“ — später nicht mehr änderbar.",
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
                        index = pages.indexOf(TutPage.Draw).coerceAtLeast(1)
                    }
                ) {
                    Text("Ja", color = AccentRose, fontFamily = DisplayFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNickConfirm = false }) {
                    Text("Ändern", color = TextMuted, fontFamily = BodyFont)
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
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (i <= index) AccentRose.copy(0.85f)
                            else Color.White.copy(0.12f)
                        )
                )
            }
        }
    }
}

@Composable
private fun TutorialNickname(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    color: Color,
    googleEnabled: Boolean,
    googleBusy: Boolean,
    onHaveAccount: () -> Unit,
    onImeDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 28.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.Center, modifier = Modifier.weight(1f)) {
            Text(
                "Wie heißt du?",
                fontFamily = DisplayFont,
                fontSize = 32.sp,
                color = TextPrimary,
                lineHeight = 38.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Dein Name färbt deine Linien.",
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
                                    "Dein Name",
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
        if (googleEnabled) {
            Text(
                if (googleBusy) "Einen Moment…" else "Ich habe ein Konto",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !googleBusy, onClick = onHaveAccount)
                    .padding(vertical = 14.dp)
            )
        }
    }
}

@Composable
private fun TutorialDrawPad(
    nickname: String,
    colorIndex: Int,
    strokes: List<Stroke>,
    drawReady: Boolean,
    replay: Boolean,
    onUserStroke: (List<StrokePoint>, Float, Float) -> Unit,
    onUndo: () -> Unit,
    onContinueToProfile: () -> Unit
) {
    val density = LocalDensity.current
    var showFertig by remember { mutableStateOf(true) }
    var showProfileCoach by remember { mutableStateOf(false) }
    var chipFracX by remember { mutableFloatStateOf(0.5f) }
    var chipFracY by remember { mutableFloatStateOf(0.9f) }
    var rootW by remember { mutableFloatStateOf(1f) }
    var rootH by remember { mutableFloatStateOf(1f) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    val nick = nickname.ifBlank { "Du" }
    val accent = PeerPalette.composeColor(colorIndex)

    LaunchedEffect(drawReady, replay) {
        if (replay) {
            showFertig = false
            showProfileCoach = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                rootW = it.size.width.toFloat().coerceAtLeast(1f)
                rootH = it.size.height.toFloat().coerceAtLeast(1f)
                rootOrigin = it.positionInRoot()
            }
    ) {
        AndroidView(
            factory = { ctx ->
                DrawingView(ctx).apply {
                    myColorIndex = colorIndex
                    myBrushWidth = with(density) { 18.dp.toPx() }
                    canvasBackground = 0xFF1A1F2E.toInt()
                    onStrokeFinished = { pts ->
                        val short = min(width, height).toFloat().coerceAtLeast(1f)
                        onUserStroke(pts, myBrushWidth, short)
                    }
                    onDoubleTapUndo = onUndo
                    onDotPlaced = { }
                }
            },
            update = { view ->
                view.myColorIndex = colorIndex
                view.inputBlocked = !showFertig || showProfileCoach
                view.setStrokes(strokes, animateNew = false)
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showFertig && !replay) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Zeichne etwas",
                    fontFamily = DisplayFont,
                    fontSize = 22.sp,
                    color = TextPrimary
                )
                Text(
                    if (drawReady) "Wenn du magst — tippe auf Fertig."
                    else "Male einen richtigen Strich — ein Punkt reicht nicht.",
                    fontFamily = BodyFont,
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp, start = 28.dp, end = 28.dp)
                    .fillMaxWidth()
            ) {
                TutorialPrimaryButton(
                    label = "Fertig",
                    enabled = drawReady,
                    onClick = {
                        showFertig = false
                        showProfileCoach = true
                    }
                )
            }
        }

        if (!showFertig || replay) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .onGloballyPositioned { coords ->
                        val p = coords.positionInRoot()
                        chipFracX =
                            (p.x + coords.size.width / 2f - rootOrigin.x) / rootW
                        chipFracY =
                            (p.y + coords.size.height / 2f - rootOrigin.y) / rootH
                    }
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .border(2.dp, Color.White.copy(0.35f), CircleShape)
                    .clickable(onClick = onContinueToProfile),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    nick.take(1).uppercase(),
                    color = Color(0xFF10141C),
                    fontFamily = DisplayFont,
                    fontSize = 18.sp
                )
            }
        }

        if (showProfileCoach && (!showFertig || replay)) {
            CoachmarkOverlay(
                holeCenterXFrac = chipFracX,
                holeCenterYFrac = chipFracY,
                holeRadiusFrac = 0.07f,
                label = "Öffne hier dein Profil",
                labelAbove = true,
                onDismiss = onContinueToProfile
            )
        }
    }
}

@Composable
private fun TutorialProfileStickerStep(
    nickname: String,
    @Suppress("UNUSED_PARAMETER") colorIndex: Int,
    dogPlaced: Boolean,
    dogEl: ProfileLayoutEl,
    onDogChanged: (ProfileLayoutEl) -> Unit,
    onContinue: () -> Unit
) {
    var showChest by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(dogPlaced) }
    var boardW by remember { mutableFloatStateOf(1f) }
    var boardH by remember { mutableFloatStateOf(1f) }
    var coachChest by remember { mutableStateOf(Triple(0.88f, 0.12f, 0.07f)) }
    var coachStep by remember { mutableIntStateOf(0) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var rootW by remember { mutableFloatStateOf(1f) }
    var rootH by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(Unit) {
        runCatching { LuvApp.instance.prefs.grantStarterSticker(TUTORIAL_DOG, 1) }
    }
    LaunchedEffect(dogPlaced) {
        if (dogPlaced) {
            selected = true
            coachStep = 2
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                rootW = it.size.width.toFloat().coerceAtLeast(1f)
                rootH = it.size.height.toFloat().coerceAtLeast(1f)
                rootOrigin = it.positionInRoot()
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "Dein Profil",
                fontFamily = DisplayFont,
                fontSize = 22.sp,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                when {
                    !dogPlaced && !showChest -> "Öffne dein Inventar oben rechts"
                    !dogPlaced && showChest -> "Tippe im Sticker-Tab auf den Hund"
                    else -> "Größe, Drehen, Spiegeln — dann Fertig"
                },
                fontFamily = BodyFont,
                color = TextMuted,
                fontSize = 13.sp,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1A2433), Color(0xFF2A3340), Color(0xFF3D4A3A))
                        )
                    )
                    .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(22.dp))
                    .onGloballyPositioned {
                        boardW = it.size.width.toFloat().coerceAtLeast(1f)
                        boardH = it.size.height.toFloat().coerceAtLeast(1f)
                    }
            ) {
                Text(
                    nickname,
                    color = Color.White.copy(0.85f),
                    fontFamily = DisplayFont,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 28.dp)
                )

                if (dogPlaced) {
                    TutorialDogElement(
                        el = dogEl,
                        selected = selected,
                        boardW = boardW,
                        boardH = boardH,
                        onSelect = { selected = true },
                        onChange = onDogChanged
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(44.dp)
                        .onGloballyPositioned { coords ->
                            val p = coords.positionInRoot()
                            val cx = (p.x + coords.size.width / 2f - rootOrigin.x) / rootW
                            val cy = (p.y + coords.size.height / 2f - rootOrigin.y) / rootH
                            val rr = (maxOf(coords.size.width, coords.size.height) / 2f /
                                minOf(rootW, rootH) + 0.02f).coerceIn(0.05f, 0.2f)
                            coachChest = Triple(cx, cy, rr)
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFF3E2A22), Color(0xFF2A1C16)))
                        )
                        .border(1.dp, Color(0xFFD4A574).copy(0.55f), RoundedCornerShape(14.dp))
                        .clickable {
                            showChest = true
                            coachStep = 1
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎒", fontSize = 22.sp)
                }
            }

            if (dogPlaced) {
                Spacer(modifier = Modifier.height(12.dp))
                TutorialPrimaryButton(label = "Fertig", enabled = true, onClick = onContinue)
            }
        }

        if (showChest && !dogPlaced) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC0B0E14))
                    .clickable { showChest = false }
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(BgSoft)
                    .border(
                        1.dp,
                        Color.White.copy(0.1f),
                        RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                    )
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Sticker", fontFamily = DisplayFont, color = TextPrimary, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(0.06f))
                        .border(2.dp, AccentRose, RoundedCornerShape(16.dp))
                        .clickable {
                            onDogChanged(
                                dogEl.copy(
                                    x = 55f,
                                    y = 48f,
                                    scale = 1.15f,
                                    rotation = 0f,
                                    flipX = false,
                                    emoji = TUTORIAL_DOG
                                )
                            )
                            showChest = false
                            selected = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(TUTORIAL_DOG, fontSize = 36.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Hund platzieren", color = TextMuted, fontFamily = BodyFont, fontSize = 13.sp)
            }
        }

        if (!dogPlaced && !showChest && coachStep == 0) {
            CoachmarkOverlay(
                holeCenterXFrac = coachChest.first,
                holeCenterYFrac = coachChest.second,
                holeRadiusFrac = coachChest.third,
                label = "Inventar öffnen",
                labelAbove = false,
                onDismiss = {
                    showChest = true
                    coachStep = 1
                }
            )
        }
    }
}

@Composable
private fun TutorialDogElement(
    el: ProfileLayoutEl,
    selected: Boolean,
    boardW: Float,
    boardH: Float,
    onSelect: () -> Unit,
    onChange: (ProfileLayoutEl) -> Unit
) {
    val density = LocalDensity.current
    var dragEl by remember(el.id) { mutableStateOf(el) }
    LaunchedEffect(el) { dragEl = el }
    val base = ProfileCatalog.baseSizePx(ProfileElType.Sticker, boardW, boardH)
    val sizePx = base * dragEl.scale.coerceIn(
        ProfileCatalog.ELEMENT_SCALE_MIN,
        ProfileCatalog.ELEMENT_SCALE_MAX
    )
    val sizeDp = with(density) { sizePx.toDp() }
    val xPx = dragEl.x / 100f * boardW
    val yPx = dragEl.y / 100f * boardH
    val invScale = 1f / dragEl.scale.coerceAtLeast(0.2f)

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (xPx - sizePx / 2f).roundToInt(),
                    (yPx - sizePx / 2f).roundToInt()
                )
            }
            .size(sizeDp)
            .graphicsLayer {
                rotationZ = dragEl.rotation
                scaleX = if (dragEl.flipX) -1f else 1f
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            .then(
                if (selected) {
                    Modifier.border(
                        1.5.dp,
                        Color.White.copy(0.75f),
                        RoundedCornerShape(8.dp)
                    )
                } else Modifier
            )
            .pointerInput(el.id) {
                detectDragGestures(
                    onDragStart = { onSelect() }
                ) { change, drag ->
                    change.consume()
                    val next = dragEl.copy(
                        x = ((dragEl.x / 100f * boardW + drag.x) / boardW * 100f).coerceIn(8f, 92f),
                        y = ((dragEl.y / 100f * boardH + drag.y) / boardH * 100f).coerceIn(12f, 88f)
                    )
                    dragEl = next
                    onChange(next)
                }
            }
            .clickable(onClick = onSelect),
        contentAlignment = Alignment.Center
    ) {
        Text(TUTORIAL_DOG, fontSize = (28f * dragEl.scale).coerceIn(18f, 48f).sp)

        if (selected) {
            val handleMod = Modifier.graphicsLayer {
                scaleX = invScale * (if (dragEl.flipX) -1f else 1f)
                scaleY = invScale
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            val cornerPad = 14.dp
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = -cornerPad, y = -cornerPad)
                    .then(handleMod)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .pointerInput(el.id) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val next = dragEl.copy(
                                rotation = ProfileCatalog.repairRotation(
                                    dragEl.rotation + (drag.x + drag.y) * 0.4f
                                )
                            )
                            dragEl = next
                            onChange(next)
                        }
                    },
                contentAlignment = Alignment.Center
            ) { Text("↻", fontSize = 14.sp, color = Color.Black) }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = cornerPad, y = -cornerPad)
                    .then(handleMod)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.25f))
                    .border(1.dp, Color.White.copy(0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("✕", color = Color.White.copy(0.35f), fontSize = 13.sp) }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = -cornerPad, y = cornerPad)
                    .then(handleMod)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF7E57C2))
                    .clickable {
                        val next = dragEl.copy(flipX = !dragEl.flipX)
                        dragEl = next
                        onChange(next)
                    },
                contentAlignment = Alignment.Center
            ) { Text("⇄", color = Color.White, fontSize = 13.sp) }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = cornerPad, y = cornerPad)
                    .then(handleMod)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .pointerInput(el.id) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val next = dragEl.copy(
                                scale = (dragEl.scale + drag.x * 0.01f * invScale)
                                    .coerceIn(
                                        ProfileCatalog.ELEMENT_SCALE_MIN,
                                        ProfileCatalog.ELEMENT_SCALE_MAX
                                    )
                            )
                            dragEl = next
                            onChange(next)
                        }
                    },
                contentAlignment = Alignment.Center
            ) { Text("⤡", fontSize = 13.sp, color = Color.Black) }
        }
    }
}

@Composable
private fun TutorialCelebrateStep(
    dogPlaced: Boolean,
    onDone: () -> Unit
) {
    var showBubble by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200)
        showBubble = true
        delay(2200)
        onDone()
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(TUTORIAL_DOG, fontSize = 72.sp)
            Spacer(modifier = Modifier.height(16.dp))
            if (showBubble && dogPlaced) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text(
                        "Toll gemacht!",
                        color = Color(0xFF1A1A1A),
                        fontFamily = DisplayFont,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TutorialGoogleStep(
    busy: Boolean,
    googleEnabled: Boolean,
    googleSignedIn: Boolean,
    onGoogleSignIn: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LuvWordmark(fontSize = 42.sp, showHeart = true)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Fast geschafft",
            fontFamily = DisplayFont,
            fontSize = 28.sp,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Melde dich an —\ndann bleibt alles bei dir.",
            fontFamily = BodyFont,
            color = TextMuted,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(36.dp))
        if (googleEnabled && !googleSignedIn) {
            TutorialPrimaryButton(
                label = if (busy) "Einen Moment…" else "Mit Google anmelden",
                enabled = !busy,
                onClick = onGoogleSignIn
            )
        } else if (googleSignedIn) {
            Text(
                "Wird eingerichtet…",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 14.sp
            )
        }
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
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (enabled) {
                    Brush.horizontalGradient(listOf(MaleBlue, AccentRose))
                } else {
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(0.12f), Color.White.copy(0.08f))
                    )
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White,
            fontFamily = DisplayFont,
            fontSize = 17.sp
        )
    }
}

private fun buildDemoFaceStrokes(colorIndex: Int, nick: String): List<Stroke> {
    val nickKey = nick.ifBlank { "Du" }
    fun circle(cx: Float, cy: Float, r: Float, n: Int = 48): List<StrokePoint> =
        (0..n).map { i ->
            val a = (i.toFloat() / n) * (Math.PI * 2).toFloat()
            StrokePoint(cx + cos(a) * r, cy + sin(a) * r)
        }
    fun smile(): List<StrokePoint> =
        (0..24).map { i ->
            val t = i / 24f
            val x = 0.38f + t * 0.24f
            val y = 0.50f + sin(t * Math.PI.toFloat()) * 0.08f
            StrokePoint(x, y)
        }
    return listOf(
        Stroke(
            id = "demo-face",
            points = circle(0.50f, 0.42f, 0.20f),
            width = 10f,
            isLocal = true,
            nickname = nickKey,
            colorIndex = colorIndex,
            colorLocked = true
        ),
        Stroke(
            id = "demo-eye-l",
            points = listOf(StrokePoint(0.42f, 0.38f)),
            width = 14f,
            isLocal = true,
            nickname = nickKey,
            colorIndex = colorIndex,
            colorLocked = true
        ),
        Stroke(
            id = "demo-eye-r",
            points = listOf(StrokePoint(0.58f, 0.38f)),
            width = 14f,
            isLocal = true,
            nickname = nickKey,
            colorIndex = colorIndex,
            colorLocked = true
        ),
        Stroke(
            id = "demo-smile",
            points = smile(),
            width = 10f,
            isLocal = true,
            nickname = nickKey,
            colorIndex = colorIndex,
            colorLocked = true
        )
    )
}
