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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.luv.couple.LuvApp
import com.luv.couple.R
import com.luv.couple.net.AccountSession
import com.luv.couple.net.LuvApiClient
import com.luv.couple.shop.ShopCatalog
import com.luv.couple.ui.ItemGlyph
import com.luv.couple.ui.clipItemId
import com.luv.couple.ui.isImagePetId
import com.luv.couple.ui.space.findPath
import com.luv.couple.ui.space.nearestWalkablePoint
import com.luv.couple.ui.space.walkAlongPath
import com.luv.couple.ui.space.walkableAt
import com.luv.couple.ui.space.zoneContains
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.LuvTheme
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlin.math.hypot
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
private val SitRingBlue = Color(0xFF42A5F5)

@Composable
fun WeddingRoomScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val myId = AccountSession.account.value?.id.orEmpty()
    val chapelMusic = remember { WeddingChapelMusic(context) }
    var musicMuted by remember { mutableStateOf(chapelMusic.muted) }
    var musicVolume by remember { mutableFloatStateOf(chapelMusic.volume) }
    var showMusicPanel by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        chapelMusic.start()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> chapelMusic.pause()
                Lifecycle.Event.ON_RESUME -> chapelMusic.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            chapelMusic.release()
        }
    }
    var ceremony by remember { mutableStateOf<LuvApiClient.CeremonyInfo?>(null) }
    var marriage by remember { mutableStateOf<LuvApiClient.MarriageInfo?>(null) }
    var showGiftPicker by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    // Start nahe der Eingangstür (unten im Kapellenbild)
    var myX by remember { mutableFloatStateOf(0.50f) }
    var myY by remember { mutableFloatStateOf(0.86f) }
    var seated by remember { mutableStateOf(false) }
    var walking by remember { mutableStateOf(false) }
    var rejectName by remember { mutableStateOf<String?>(null) }
    var married by remember { mutableStateOf(false) }
    var confetti by remember { mutableStateOf(false) }
    var myReaction by remember { mutableStateOf<String?>(null) }
    var layout by remember { mutableStateOf<LuvApiClient.RoomLayout?>(null) }
    var spawned by remember { mutableStateOf(false) }
    var reactionExpanded by remember { mutableStateOf(false) }
    var emojiBar by remember { mutableStateOf(ShopCatalog.DEFAULT_BAR) }

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
        emojiBar = withContext(Dispatchers.IO) {
            runCatching { LuvApp.instance.prefs.emojiBar() }
                .getOrDefault(ShopCatalog.DEFAULT_BAR)
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
                    marriage = it.marriage
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
                        // Eigene Position lokal behalten während Laufen/Sitzen
                        if (!walking && !seated && spawned) {
                            if (hypot(me.x - myX, me.y - myY) > 0.12f) {
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

    val giftTargetUserId = remember(ceremony, marriage, myId) {
        ceremony?.gathering?.firstOrNull { it.isCouple }?.userId
            ?: marriage?.partnerId
            ?: myId.takeIf { it.isNotBlank() }
    }
    val canGift = marriage?.canGift == true && !giftTargetUserId.isNullOrBlank()

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
                    val isSeatedHere = if (g.userId == myId) seated else g.seatedSeatId != null
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = (roomW * ax) - avatarDp / 2,
                                top = (roomH * ay) - avatarDp / 2
                            )
                            .size(avatarDp)
                            .clip(CircleShape)
                            .background(
                                if (isImagePetId(clipItemId(g.petEmoji))) Color.White.copy(0.92f)
                                else Color(0xFF4A3728)
                            )
                            .border(
                                2.dp,
                                when {
                                    isSeatedHere -> SitRingBlue
                                    g.isCouple -> Color(0xFFFFD54F)
                                    else -> Color.White.copy(0.55f)
                                },
                                CircleShape
                            )
                            .then(
                                if (isSeatedHere) {
                                    Modifier.border(1.5.dp, SitRingBlue.copy(0.85f), CircleShape)
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val showReact = when {
                            g.userId == myId && myReaction != null -> myReaction
                            g.reaction != null && g.reactionUntil > System.currentTimeMillis() -> g.reaction
                            else -> null
                        }
                        val glyphSp = (avatarDp.value * 0.52f).sp
                        if (showReact != null) {
                            ItemGlyph(id = clipItemId(showReact), fontSize = glyphSp)
                        } else {
                            ItemGlyph(
                                id = clipItemId(g.petEmoji).ifBlank { "🐣" },
                                fontSize = glyphSp,
                            )
                        }
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

                // Priester am Altar
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

                // Tippen: Sitze / Laufen (auch vom Sitz aufstehen)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(zones, avatarR, sitZones, seated) {
                            detectTapGestures { offset ->
                                if (walking) return@detectTapGestures
                                val rawX = (offset.x / size.width).coerceIn(0.01f, 0.99f)
                                val rawY = (offset.y / size.height).coerceIn(0.01f, 0.99f)
                                val hitR = (avatarR * 3.2f).coerceAtLeast(0.05f)

                                if (zones.none { it.isWalk }) {
                                    scope.launch {
                                        Toast.makeText(
                                            context,
                                            "Kein Laufbereich (grün) gesetzt",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                    return@detectTapGestures
                                }

                                suspend fun walkTo(tx: Float, ty: Float): Boolean {
                                    val path = findPath(zones, myX, myY, tx, ty, avatarR)
                                    if (path.isEmpty()) return false
                                    walkAlongPath(
                                        path,
                                        { x, y -> myX = x; myY = y },
                                        { myX },
                                        { myY },
                                    )
                                    runCatching { LuvApiClient.ceremonyMove(myX, myY) }
                                    return true
                                }

                                if (seated) {
                                    scope.launch {
                                        walking = true
                                        // Aufstehen: Server-Sitz lösen, dann laufen
                                        seated = false
                                        ceremony = ceremony?.copy(
                                            gathering = ceremony?.gathering?.map { g ->
                                                if (g.userId == myId) g.copy(seatedSeatId = null) else g
                                            }.orEmpty()
                                        )
                                        walkTo(rawX, rawY)
                                        walking = false
                                    }
                                    return@detectTapGestures
                                }

                                val hitSit = sitZones.firstOrNull { z ->
                                    val taken =
                                        ceremony?.gathering?.any {
                                            it.seatedSeatId == z.id && it.userId != myId
                                        } == true
                                    !taken && (
                                        zoneContains(z, rawX, rawY, 0.04f) ||
                                            hypot(rawX - z.sitX, rawY - z.sitY) < hitR
                                        )
                                }
                                if (hitSit != null) {
                                    scope.launch {
                                        walking = true
                                        val approach = nearestWalkablePoint(
                                            zones, hitSit.sitX, hitSit.sitY, avatarR
                                        ) ?: (hitSit.sitX to hitSit.sitY)
                                        walkTo(approach.first, approach.second)
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
                                        walking = false
                                    }
                                    return@detectTapGestures
                                }

                                if (!walkableAt(zones, rawX, rawY, avatarR) &&
                                    zones.none { it.isWalk }
                                ) {
                                    return@detectTapGestures
                                }
                                scope.launch {
                                    walking = true
                                    val goal = if (walkableAt(zones, rawX, rawY, avatarR)) {
                                        rawX to rawY
                                    } else {
                                        nearestWalkablePoint(zones, rawX, rawY, avatarR)
                                            ?: (rawX to rawY)
                                    }
                                    walkTo(goal.first, goal.second)
                                    walking = false
                                }
                            }
                        }
                )

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

                // Reaktionsleiste (wie Custom-Raum): oben rechts, bleibt offen
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xCC1E2430))
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { reactionExpanded = !reactionExpanded },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("🙂", fontSize = 22.sp)
                    }
                    if (reactionExpanded) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            emojiBar.forEach { emo ->
                                Box(
                                    modifier = Modifier
                                        .size(44.dp, 40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(0.08f))
                                        .clickable {
                                            scope.launch {
                                                runCatching { LuvApiClient.ceremonyReact(emo) }
                                                myReaction = emo
                                                delay(2000)
                                                if (myReaction == emo) myReaction = null
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    ItemGlyph(id = emo, fontSize = 22.sp)
                                }
                            }
                        }
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

        // Untere Leiste: zurück + Lautsprecher
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xE61E2430))
                .padding(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "←",
                    color = Color.White,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(0.10f))
                        .clickable(onClick = onClose)
                        .padding(vertical = 6.dp),
                )
                if (canGift) {
                    Text(
                        "🎁",
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x88E91E63))
                            .clickable { showGiftPicker = true }
                            .padding(vertical = 8.dp),
                    )
                }
                Text(
                    if (musicMuted) "🔇" else "🔊",
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (showMusicPanel) Color(0x88AB47BC) else Color.White.copy(0.10f)
                        )
                        .clickable { showMusicPanel = true }
                        .padding(vertical = 8.dp),
                )
            }
        }

        if (showGiftPicker && !giftTargetUserId.isNullOrBlank()) {
            WeddingGiftPickerDialog(
                targetUserId = giftTargetUserId,
                onDismiss = { showGiftPicker = false },
                onGifted = {
                    scope.launch {
                        runCatching { LuvApiClient.fetchCeremony() }
                            .onSuccess {
                                ceremony = it.ceremony
                                marriage = it.marriage
                            }
                    }
                }
            )
        }

        if (showMusicPanel) {
            AlertDialog(
                onDismissRequest = { showMusicPanel = false },
                containerColor = Color(0xF21E2430),
                title = {
                    Text(
                        "Musik",
                        fontFamily = DisplayFont,
                        color = TextPrimary,
                        fontSize = 20.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (musicMuted) {
                                "Stumm"
                            } else {
                                "Lautstärke ${(musicVolume * 100f).toInt().coerceIn(0, 100)} %"
                            },
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 14.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (musicMuted) AccentRose.copy(0.35f)
                                        else Color.White.copy(0.10f)
                                    )
                                    .clickable {
                                        chapelMusic.setMuted(!musicMuted)
                                        musicMuted = chapelMusic.muted
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (musicMuted) "Ton an" else "Stumm",
                                    color = Color.White,
                                    fontFamily = DisplayFont,
                                    fontSize = 15.sp
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(0.10f))
                                    .clickable {
                                        chapelMusic.nudgeVolume(-0.08f)
                                        musicVolume = chapelMusic.volume
                                        musicMuted = chapelMusic.muted
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Leiser", color = Color.White, fontFamily = DisplayFont)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(0.10f))
                                    .clickable {
                                        chapelMusic.nudgeVolume(0.08f)
                                        musicVolume = chapelMusic.volume
                                        musicMuted = chapelMusic.muted
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Lauter", color = Color.White, fontFamily = DisplayFont)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showMusicPanel = false }) {
                        Text("Fertig", color = AccentRose, fontFamily = DisplayFont)
                    }
                }
            )
        }
    }
}

@Composable
private fun SpacerH() {
    Spacer(modifier = Modifier.height(12.dp))
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
            Triple(
                Random.nextFloat(),
                Random.nextFloat(),
                Color(
                    listOf(0xFFFFD54F, 0xFFFF5A6A, 0xFF7CFF6B, 0xFF3DD6FF, 0xFFFF4FC3).random()
                )
            )
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
