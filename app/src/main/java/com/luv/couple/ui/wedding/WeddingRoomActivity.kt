package com.luv.couple.ui.wedding

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.R
import com.luv.couple.net.AccountSession
import com.luv.couple.net.LuvApiClient
import com.luv.couple.ui.space.findPath
import com.luv.couple.ui.space.walkAlongPath
import com.luv.couple.ui.space.walkableAt
import com.luv.couple.ui.space.zoneContains
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.LuvTheme
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlin.math.hypot
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Vogelperspektive Hochzeitsraum — eigener Screen, kein Canvas/LockDraw.
 */
class WeddingRoomActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LuvTheme {
                WeddingRoomScreen(onClose = { finish() })
            }
        }
    }
}

private const val DEFAULT_AVATAR_R = 0.028f

@Composable
fun WeddingRoomScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val myId = AccountSession.account.value?.id.orEmpty()
    var ceremony by remember { mutableStateOf<LuvApiClient.CeremonyInfo?>(null) }
    var entered by remember { mutableStateOf(false) }
    // Start nahe der Eingangstür (unten im Kapellenbild)
    var myX by remember { mutableFloatStateOf(0.50f) }
    var myY by remember { mutableFloatStateOf(0.86f) }
    var seated by remember { mutableStateOf(false) }
    var rejectName by remember { mutableStateOf<String?>(null) }
    var married by remember { mutableStateOf(false) }
    var confetti by remember { mutableStateOf(false) }
    var myReaction by remember { mutableStateOf<String?>(null) }
    var layout by remember { mutableStateOf<LuvApiClient.RoomLayout?>(null) }
    var spawned by remember { mutableStateOf(false) }

    val zones = layout?.zones.orEmpty()
    val avatarR = layout?.avatarR?.takeIf { it > 0f } ?: DEFAULT_AVATAR_R
    val sitZones = remember(zones, ceremony) {
        val meCouple = ceremony?.gathering?.find { it.userId == myId }?.isCouple == true
        zones.filter { z ->
            when {
                z.isCoupleSeat -> meCouple
                z.isGuestSeat -> !meCouple
                else -> false
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { LuvApiClient.fetchRoomLayout("wedding") }
            .onSuccess {
                layout = it
                if (!spawned) {
                    myX = it.spawnX
                    myY = it.spawnY
                    spawned = true
                }
            }
        while (!married && rejectName == null) {
            runCatching { LuvApiClient.fetchCeremony() }
                .onSuccess {
                    ceremony = it.ceremony
                    if (it.roomLayout != null) {
                        layout = it.roomLayout
                        if (!spawned) {
                            myX = it.roomLayout.spawnX
                            myY = it.roomLayout.spawnY
                            spawned = true
                        }
                    }
                    val me = it.ceremony?.gathering?.find { g -> g.userId == myId }
                    if (me != null) {
                        if (me.seatedSeatId != null) seated = true
                        if (!seated && spawned) {
                            // Fremd-Sync nur wenn wir nicht lokal laufen
                            if (hypot(me.x - myX, me.y - myY) > 0.08f) {
                                myX = me.x
                                myY = me.y
                            }
                        }
                    }
                    if (it.ceremony?.phase == "altar" || it.ceremony?.phase == "vows") {
                        entered = true
                    }
                }
            delay(1500)
        }
    }

    if (rejectName != null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${rejectName} hat Nein gesagt",
                color = Color.White,
                fontFamily = DisplayFont,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
        LaunchedEffect(rejectName) {
            delay(5000)
            onClose()
        }
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F0E6))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val chapelPainter = painterResource(R.drawable.wedding_chapel_room)
        val aspect = run {
            val s = chapelPainter.intrinsicSize
            if (s.height > 0f) (s.width / s.height) else 0.72f
        }
        val roomH = if (maxWidth / maxHeight > aspect) maxHeight else maxWidth / aspect
        val roomW = roomH * aspect

        // Kapellenbild + Spiel-Layer in exakter Bildgröße (Sitze liegen auf den Bänken)
        Box(
            modifier = Modifier
                .size(roomW, roomH)
                .align(Alignment.Center)
        ) {
            Image(
                painter = chapelPainter,
                contentDescription = "Hochzeitskapelle",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

        if (!entered) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .fillMaxWidth(0.88f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xEE2A1F28))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Ihr steht vor dem Trausaal",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
                SpacerH()
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(28.dp))
                        .background(Brush.horizontalGradient(listOf(AccentRose, Color(0xFFC218A8))))
                        .clickable {
                            entered = true
                            scope.launch {
                                runCatching { LuvApiClient.ceremonyPresence("gathering") }
                                    .onSuccess { c ->
                                        ceremony = c
                                        val me = c?.gathering?.find { it.userId == myId }
                                        if (me != null) {
                                            myX = me.x
                                            myY = me.y
                                            spawned = true
                                        } else if (layout != null) {
                                            myX = layout!!.spawnX
                                            myY = layout!!.spawnY
                                            spawned = true
                                        }
                                    }
                            }
                        }
                        .padding(horizontal = 28.dp, vertical = 14.dp)
                ) {
                    Text("Eintreten", color = Color.White, fontFamily = DisplayFont, fontSize = 18.sp)
                }
            }
        } else {
            val c = ceremony
            // Zonen unsichtbar — nur Avatare
            val avatarDp = (roomW * (avatarR * 2f)).let { s ->
                when {
                    s < 22.dp -> 22.dp
                    s > 72.dp -> 72.dp
                    else -> s
                }
            }
            c?.gathering?.forEach { g ->
                val ax = if (g.userId == myId) myX else g.x
                val ay = if (g.userId == myId) myY else g.y
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            start = (roomW * ax) - avatarDp / 2,
                            top = (roomH * ay) - avatarDp / 2
                        )
                        .size(avatarDp)
                        .clip(CircleShape)
                        .background(Color(0xFF4A3728))
                        .border(2.dp, if (g.isCouple) Color(0xFFFFD54F) else Color.White.copy(0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val showReact = when {
                        g.userId == myId && myReaction != null -> myReaction
                        g.reaction != null && g.reactionUntil > System.currentTimeMillis() -> g.reaction
                        else -> null
                    }
                    val emojiSp = (avatarDp.value * 0.5f).sp
                    Text(showReact ?: g.petEmoji, fontSize = emojiSp)
                    if (g.isCouple && g.vowProgress > 0f) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawArc(
                                color = if (g.vow == "no") Color(0xFFE53935) else Color(0xFF43A047),
                                startAngle = -90f,
                                sweepAngle = 360f * g.vowProgress,
                                useCenter = false,
                                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }

            // Priester am Altar (Bild-Koordinate ~ Mitte oben)
            if (c?.phase == "vows" || (c?.gathering?.all { it.seatedSeatId != null } == true)) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            start = roomW * 0.50f - 28.dp,
                            top = roomH * 0.14f
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(R.drawable.wedding_priest),
                        contentDescription = "Priester",
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color.White.copy(0.7f), CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        "Möchtet ihr?",
                        color = Color(0xFF3E2723),
                        fontFamily = DisplayFont,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(0.85f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Tippen: unsichtbare Sitze / Laufen nur in Grün, um Rot herum
            if (!seated) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(zones, avatarR, sitZones) {
                            detectTapGestures { offset ->
                                val rawX = (offset.x / size.width).coerceIn(0.01f, 0.99f)
                                val rawY = (offset.y / size.height).coerceIn(0.01f, 0.99f)
                                val hitR = (avatarR * 1.8f).coerceAtLeast(0.035f)
                                val hitSit = sitZones.firstOrNull { z ->
                                    val taken =
                                        ceremony?.gathering?.any { it.seatedSeatId == z.id } == true
                                    !taken && (
                                        zoneContains(z, rawX, rawY, 0.02f) ||
                                            hypot(rawX - z.sitX, rawY - z.sitY) < hitR
                                        )
                                }
                                if (hitSit != null) {
                                    scope.launch {
                                        val path = findPath(
                                            zones, myX, myY, hitSit.sitX, hitSit.sitY, avatarR
                                        )
                                        walkAlongPath(path, { x, y -> myX = x; myY = y }, { myX }, { myY })
                                        runCatching { LuvApiClient.ceremonyMove(myX, myY) }
                                        // Auch wenn Blau außerhalb Grün / hinter Rot: setzen
                                        runCatching { LuvApiClient.ceremonySit(hitSit.id) }
                                            .onSuccess {
                                                ceremony = it
                                                seated = true
                                                myX = hitSit.sitX
                                                myY = hitSit.sitY
                                            }
                                            .onFailure {
                                                Toast.makeText(
                                                    context,
                                                    it.message ?: "Sitz belegt",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                    }
                                    return@detectTapGestures
                                }
                                if (!walkableAt(zones, rawX, rawY, avatarR) &&
                                    zones.none { it.isWalk }
                                ) {
                                    return@detectTapGestures
                                }
                                scope.launch {
                                    val path = findPath(zones, myX, myY, rawX, rawY, avatarR)
                                    if (path.isEmpty()) return@launch
                                    walkAlongPath(path, { x, y -> myX = x; myY = y }, { myX }, { myY })
                                    runCatching { LuvApiClient.ceremonyMove(myX, myY) }
                                }
                            }
                        }
                )
            }

            // Vow buttons for couple
            val meGuest = c?.gathering?.find { it.userId == myId }
            if (c?.phase == "vows" && meGuest?.isCouple == true && seated) {
                VowHoldButtons(
                    onYesProgress = { p ->
                        scope.launch {
                            val r = runCatching { LuvApiClient.ceremonyVow("yes", p) }.getOrNull()
                            if (r?.married == true) {
                                married = true
                                confetti = true
                            }
                            ceremony = r?.ceremony ?: ceremony
                        }
                    },
                    onNoProgress = { p ->
                        scope.launch {
                            val r = runCatching { LuvApiClient.ceremonyVow("no", p) }.getOrNull()
                            if (r?.rejected == true) {
                                rejectName = r.rejectedBy
                            }
                            ceremony = r?.ceremony ?: ceremony
                        }
                    }
                )
            }

            // Reactions
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(0.75f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf("❤️", "😍", "🎉", "👏").forEach { emo ->
                    Text(
                        emo,
                        fontSize = 28.sp,
                        modifier = Modifier.clickable {
                            scope.launch {
                                runCatching { LuvApiClient.ceremonyReact(emo) }
                                myReaction = emo
                                delay(2000)
                                if (myReaction == emo) myReaction = null
                            }
                        }
                    )
                }
            }
        }
        } // Kapellen-Box

        if (confetti) {
            ConfettiOverlay()
            LaunchedEffect(Unit) {
                delay(4500)
                onClose()
            }
            Text(
                "Glückwunsch — ihr seid verheiratet!",
                color = Color(0xFFFFD54F),
                fontFamily = DisplayFont,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
        }

        Text(
            "✕",
            color = TextMuted,
            fontSize = 18.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clickable(onClick = onClose)
        )
    }
}

@Composable
private fun SpacerH() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun VowHoldButtons(
    onYesProgress: (Float) -> Unit,
    onNoProgress: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HoldCircleButton(label = "Ja", color = Color(0xFF43A047), onProgress = onYesProgress)
        HoldCircleButton(label = "Nein", color = Color(0xFFE53935), onProgress = onNoProgress)
    }
}

@Composable
private fun HoldCircleButton(
    label: String,
    color: Color,
    onProgress: (Float) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        if (pressed) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(15_000, easing = LinearEasing))
            onProgress(1f)
        } else {
            progress.snapTo(0f)
            onProgress(0f)
        }
    }
    LaunchedEffect(progress.value) {
        if (pressed && progress.value > 0f && progress.value < 1f) {
            onProgress(progress.value)
        }
    }
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(color.copy(0.25f))
            .border(3.dp, color, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress.value,
                useCenter = false,
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Text(label, color = TextPrimary, fontFamily = DisplayFont, fontSize = 22.sp)
    }
}

@Composable
private fun ConfettiOverlay() {
    val bits = remember {
        List(48) {
            Triple(Random.nextFloat(), Random.nextFloat(), Color(
                listOf(0xFFFFD54F, 0xFFFF5A6A, 0xFF7CFF6B, 0xFF3DD6FF, 0xFFFF4FC3).random()
            ))
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        bits.forEach { (x, y, col) ->
            drawCircle(
                color = col,
                radius = 6f,
                center = Offset(size.width * x, size.height * y)
            )
        }
    }
}
