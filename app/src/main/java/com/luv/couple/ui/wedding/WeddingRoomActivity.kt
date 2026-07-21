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
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BodyFont
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

private const val AVATAR_R = 0.028f

private fun zoneContains(z: LuvApiClient.RoomZone, x: Float, y: Float, pad: Float = 0f): Boolean {
    return if (z.shape == "circle") {
        hypot(x - z.cx, y - z.cy) <= z.r + pad
    } else {
        x >= z.x - pad && x <= z.x + z.w + pad &&
            y >= z.y - pad && y <= z.y + z.h + pad
    }
}

private fun blockedAt(zones: List<LuvApiClient.RoomZone>, x: Float, y: Float): Boolean {
    return zones.any { it.isBlock && zoneContains(it, x, y, AVATAR_R * 0.35f) }
}

private fun walkableAt(zones: List<LuvApiClient.RoomZone>, x: Float, y: Float): Boolean {
    if (blockedAt(zones, x, y)) return false
    val greens = zones.filter { it.isWalk }
    if (greens.isEmpty()) return true
    return greens.any { zoneContains(it, x, y, AVATAR_R) }
}

/** Läuft in Richtung Ziel; stoppt vor roten Bereichen. */
private fun walkStepToward(
    fromX: Float,
    fromY: Float,
    toX: Float,
    toY: Float,
    zones: List<LuvApiClient.RoomZone>,
): Pair<Float, Float> {
    val dist = hypot(toX - fromX, toY - fromY).coerceAtLeast(0.0001f)
    val step = 0.012f
    val nx = fromX + (toX - fromX) / dist * step.coerceAtMost(dist)
    val ny = fromY + (toY - fromY) / dist * step.coerceAtMost(dist)
    return if (walkableAt(zones, nx, ny)) nx to ny else fromX to fromY
}

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

    val fallbackZones = remember {
        listOf(
            LuvApiClient.RoomZone("altar_a", "yellow", "circle", cx = 0.4f, cy = 0.3f, r = 0.04f),
            LuvApiClient.RoomZone("altar_b", "yellow", "circle", cx = 0.6f, cy = 0.3f, r = 0.04f),
            LuvApiClient.RoomZone("bench_0", "blue", "circle", cx = 0.3f, cy = 0.44f, r = 0.035f),
            LuvApiClient.RoomZone("bench_1", "blue", "circle", cx = 0.3f, cy = 0.54f, r = 0.035f),
            LuvApiClient.RoomZone("bench_2", "blue", "circle", cx = 0.3f, cy = 0.63f, r = 0.035f),
            LuvApiClient.RoomZone("bench_3", "blue", "circle", cx = 0.3f, cy = 0.72f, r = 0.035f),
            LuvApiClient.RoomZone("bench_4", "blue", "circle", cx = 0.7f, cy = 0.44f, r = 0.035f),
            LuvApiClient.RoomZone("bench_5", "blue", "circle", cx = 0.7f, cy = 0.54f, r = 0.035f),
            LuvApiClient.RoomZone("bench_6", "blue", "circle", cx = 0.7f, cy = 0.63f, r = 0.035f),
            LuvApiClient.RoomZone("bench_7", "blue", "circle", cx = 0.7f, cy = 0.72f, r = 0.035f),
        )
    }
    val zones = layout?.zones?.takeIf { it.isNotEmpty() } ?: fallbackZones
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
        // Layout kommt mit der Zeremonie-API (Admin-Speichern → sofort live)
        runCatching { LuvApiClient.fetchRoomLayout("wedding") }
            .onSuccess { layout = it }
        while (!married && rejectName == null) {
            runCatching { LuvApiClient.fetchCeremony() }
                .onSuccess {
                    ceremony = it.ceremony
                    if (it.roomLayout != null && it.roomLayout.zones.isNotEmpty()) {
                        layout = it.roomLayout
                    }
                    val me = it.ceremony?.gathering?.find { g -> g.userId == myId }
                    if (me != null) {
                        if (me.seatedSeatId != null) seated = true
                        if (!seated) {
                            myX = me.x
                            myY = me.y
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
                            }
                        }
                        .padding(horizontal = 28.dp, vertical = 14.dp)
                ) {
                    Text("Eintreten", color = Color.White, fontFamily = DisplayFont, fontSize = 18.sp)
                }
            }
        } else {
            val c = ceremony
            // Gelbe/blaue Sitz-Zonen aus Admin-Layout
            sitZones.forEach { seat ->
                val taken = c?.gathering?.any { it.seatedSeatId == seat.id } == true
                val markerColor = when {
                    seat.isCoupleSeat -> Color(0xFFFFD54F)
                    else -> Color(0xFF64B5F6)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            start = (roomW * seat.sitX) - 18.dp,
                            top = (roomH * seat.sitY) - 18.dp
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(
                                2.dp,
                                if (taken) markerColor else markerColor.copy(0.55f),
                                CircleShape
                            )
                            .background(Color.Black.copy(0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!taken) Text("·", color = Color.White.copy(0.5f))
                    }
                }
            }

            // Guests / couple avatars
            c?.gathering?.forEach { g ->
                val ax = if (g.userId == myId) myX else g.x
                val ay = if (g.userId == myId) myY else g.y
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            start = (roomW * ax) - 22.dp,
                            top = (roomH * ay) - 22.dp
                        )
                        .size(44.dp)
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
                    Text(showReact ?: g.petEmoji, fontSize = 22.sp)
                    // Vow progress ring
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

            // Tippen → Schritttempo laufen; an roten Bereichen stoppen
            if (!seated) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(zones) {
                            detectTapGestures { offset ->
                                val rawX = (offset.x / size.width).coerceIn(0.02f, 0.98f)
                                val rawY = (offset.y / size.height).coerceIn(0.02f, 0.98f)
                                // Blau/Gelb tippen → in die Nähe laufen und setzen
                                val hitSit = sitZones.firstOrNull { z ->
                                    val taken = ceremony?.gathering?.any { it.seatedSeatId == z.id } == true
                                    !taken && (
                                        zoneContains(z, rawX, rawY, 0.025f) ||
                                            hypot(rawX - z.sitX, rawY - z.sitY) < 0.045f
                                        )
                                }
                                if (hitSit != null) {
                                    scope.launch {
                                        val tx = hitSit.sitX
                                        val ty = hitSit.sitY
                                        var guard = 0
                                        while (hypot(tx - myX, ty - myY) > 0.04f && guard < 220) {
                                            val (nx, ny) = walkStepToward(myX, myY, tx, ty, zones)
                                            if (nx == myX && ny == myY) break
                                            myX = nx
                                            myY = ny
                                            delay(55)
                                            guard++
                                        }
                                        runCatching { LuvApiClient.ceremonyMove(myX, myY) }
                                        if (hypot(tx - myX, ty - myY) <= 0.09f) {
                                            runCatching { LuvApiClient.ceremonySit(hitSit.id) }
                                                .onSuccess {
                                                    ceremony = it
                                                    seated = true
                                                    myX = tx
                                                    myY = ty
                                                }
                                                .onFailure {
                                                    Toast.makeText(
                                                        context,
                                                        it.message ?: "Sitz belegt",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                        }
                                    }
                                    return@detectTapGestures
                                }
                                var tx = rawX
                                var ty = rawY
                                if (!walkableAt(zones, tx, ty)) {
                                    var found = false
                                    for (r in 1..12) {
                                        val d = r * 0.02f
                                        val candidates = listOf(
                                            tx to (ty - d), tx to (ty + d),
                                            (tx - d) to ty, (tx + d) to ty,
                                        )
                                        val ok = candidates.firstOrNull { (x, y) ->
                                            walkableAt(
                                                zones,
                                                x.coerceIn(0.02f, 0.98f),
                                                y.coerceIn(0.02f, 0.98f)
                                            )
                                        }
                                        if (ok != null) {
                                            tx = ok.first.coerceIn(0.02f, 0.98f)
                                            ty = ok.second.coerceIn(0.02f, 0.98f)
                                            found = true
                                            break
                                        }
                                    }
                                    if (!found) return@detectTapGestures
                                }
                                scope.launch {
                                    var guard = 0
                                    while (hypot(tx - myX, ty - myY) > 0.015f && guard < 280) {
                                        val (nx, ny) = walkStepToward(myX, myY, tx, ty, zones)
                                        if (nx == myX && ny == myY) break
                                        myX = nx
                                        myY = ny
                                        delay(55)
                                        guard++
                                    }
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
