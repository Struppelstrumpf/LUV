package com.luv.couple.ui.wedding

import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlin.math.ceil
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Vogelperspektive Hochzeitsraum — eigener Screen, kein Canvas/LockDraw.
 */
class WeddingRoomActivity : ComponentActivity() {
    private var exitSent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            LuvTheme {
                WeddingRoomScreen(
                    onClose = { leaveRoomThenFinish() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        if (isFinishing) {
            // Fallback, falls finish ohne await (z. B. System)
            fireExitRoomAsync()
        }
        super.onDestroy()
    }

    private fun leaveRoomThenFinish() {
        if (exitSent) {
            if (!isFinishing) finish()
            return
        }
        exitSent = true
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { LuvApiClient.ceremonyExitRoom() }
            }
            finish()
        }
    }

    private fun fireExitRoomAsync() {
        if (exitSent) return
        exitSent = true
        Thread {
            runCatching {
                kotlinx.coroutines.runBlocking {
                    LuvApiClient.ceremonyExitRoom()
                }
            }
        }.start()
    }
}

private const val DEFAULT_AVATAR_R = 0.056f
private val AvatarFill = Color(0xE6FFFFFF)
private val SitRingBlue = Color(0xFF42A5F5)
private val IdleRing = Color(0x99FFFFFF)
/** Priester am Altar (oben auf dem Podest), nicht zwischen den vorderen Bänken */
private const val PRIEST_X = 0.50f
private const val PRIEST_Y = 0.175f
private val PriestSize = 30.dp
private val MoneyTreeSize = 40.dp
/** Enger Tip-Radius — Geldbäume dürfen Sitze/Laufen nicht abfangen */
private const val TREE_SNAP_R = 0.05f

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
    /** Lokal geerntete Bäume — sofort, ohne auf Gesture-Restart zu warten */
    var claimedMoneyTrees by remember { mutableStateOf(emptySet<String>()) }
    val ceremonyLatest by rememberUpdatedState(ceremony)
    val claimedTreesLatest by rememberUpdatedState(claimedMoneyTrees)
    val remoteX = remember { mutableStateMapOf<String, Float>() }
    val remoteY = remember { mutableStateMapOf<String, Float>() }
    var marriage by remember { mutableStateOf<LuvApiClient.MarriageInfo?>(null) }
    var showGiftPicker by remember { mutableStateOf(false) }
    var showGuestbook by remember { mutableStateOf(false) }
    var applauseBursts by remember { mutableStateOf<List<Triple<Float, Float, Long>>>(emptyList()) }
    var confettiBursts by remember { mutableStateOf<List<Triple<Float, Float, Long>>>(emptyList()) }
    var seenApplauseAts by remember { mutableStateOf(setOf<Long>()) }
    var seenConfettiAts by remember { mutableStateOf(setOf<Long>()) }
    var entered by remember { mutableStateOf(false) }
    val cachedLayout = remember { WeddingChapelCache.layout }
    // Start nahe der Eingangstür (unten im Kapellenbild) — Cache → sofort sichtbare Position
    var myX by remember { mutableFloatStateOf(cachedLayout?.spawnX ?: 0.50f) }
    var myY by remember { mutableFloatStateOf(cachedLayout?.spawnY ?: 0.86f) }
    var seated by remember { mutableStateOf(false) }
    var walking by remember { mutableStateOf(false) }
    var rejectName by remember { mutableStateOf<String?>(null) }
    var married by remember { mutableStateOf(false) }
    var confetti by remember { mutableStateOf(false) }
    var showReceptionCongrats by remember { mutableStateOf(false) }
    /** Einmalig — verhindert Wiederholung des Glückwunsch-Popups */
    var congratsShownOnce by remember { mutableStateOf(false) }
    var showGuestApplausePrompt by remember { mutableStateOf(false) }
    var guestApplausePromptDone by remember { mutableStateOf(false) }
    var receptionStartedAt by remember { mutableStateOf(0L) }
    var myReaction by remember { mutableStateOf<String?>(null) }
    var layout by remember { mutableStateOf(cachedLayout) }
    var spawned by remember { mutableStateOf(cachedLayout != null) }
    var reactionExpanded by remember { mutableStateOf(false) }
    var emojiBar by remember { mutableStateOf(ShopCatalog.DEFAULT_BAR) }
    var leaving by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }

    fun leaveRoom() {
        if (leaving) return
        leaving = true
        onClose()
    }

    BackHandler(enabled = !leaving) {
        showLeaveConfirm = true
    }

    val zones = layout?.zones.orEmpty()
    val avatarR = layout?.avatarR?.takeIf { it > 0f } ?: DEFAULT_AVATAR_R

    /** Ring/Sitz schon bei leichter Überlappung (Avatar-Rand reicht). */
    fun inRoleSeatZone(isCouple: Boolean, x: Float, y: Float): Boolean {
        val pad = avatarR.coerceAtLeast(0.02f)
        return zones.any { z ->
            val ok = if (isCouple) z.isCoupleSeat else z.isGuestSeat
            ok && zoneContains(z, x, y, pad)
        }
    }

    fun applyLayout(next: LuvApiClient.RoomLayout?) {
        if (next == null) return
        layout = next
        WeddingChapelCache.put(next)
        if (!spawned) {
            myX = next.spawnX
            myY = next.spawnY
            spawned = true
        }
    }

    fun applyCeremonyBundle(it: LuvApiClient.CeremonyBundle) {
        ceremony = it.ceremony
        marriage = it.marriage
        applyLayout(it.roomLayout)
        val fromServer = it.ceremony?.claimedMoneyTreeIds.orEmpty()
        if (fromServer.isNotEmpty()) {
            claimedMoneyTrees = claimedMoneyTrees + fromServer
        }
        val me = it.ceremony?.gathering?.find { g -> g.userId == myId }
        if (me != null && !walking) {
            // Immer vom Server — sonst bleibt blauer Ring nach Aufstehen „kleben“
            seated = me.seatedSeatId != null
            if (seated) {
                myX = me.x
                myY = me.y
                spawned = true
            } else if (spawned && hypot(me.x - myX, me.y - myY) > 0.14f) {
                myX = me.x
                myY = me.y
            }
        }
        // Auto-Enter: Brautpaar immer; Gäste nur Reconnect mit vorhandenem Sitz
        // (nicht bloß Gathering-Heartbeat vom Warte-Dialog → Latecomer-Bypass)
        if (
            it.ceremony?.phase == "altar" ||
            it.ceremony?.phase == "vows" ||
            it.ceremony?.phase == "gifts" ||
            it.ceremony?.phase == "reception" ||
            it.ceremony?.phase == "gathering" ||
            it.ceremony?.seatingLocked == true
        ) {
            val meNow = it.ceremony.gathering.find { g -> g.userId == myId }
            when {
                meNow?.isCouple == true -> entered = true
                meNow?.present == true && meNow.seatedSeatId != null -> entered = true
            }
        }
        if (
            it.ceremony?.pastorPhase == "gifts_claim" ||
            it.ceremony?.phase == "gifts_claim"
        ) {
            // Empfang vorbei — Claim auf dem Home, nicht als Abbruch
            Toast.makeText(context, "Empfang vorbei — Geschenke auf dem Home abholen", Toast.LENGTH_LONG).show()
            onClose()
            return
        }
        if (
            it.ceremony?.pastorPhase == "ended" ||
            it.ceremony?.phase == "ended"
        ) {
            // Echtes Abbruch-Ende (Nein), nicht Empfang
            if (rejectName == null && it.ceremony?.leftByNickname != null) {
                rejectName = it.ceremony.leftByNickname
            } else if (rejectName == null) {
                rejectName = "Die Trauung"
            }
        }
    }

    /** Gäste zu spät / nach Verlassen — Brautpaar darf immer wieder rein. */
    fun shouldKickLatecomer(c: LuvApiClient.CeremonyInfo?): Boolean {
        if (c == null) return false
        if (c.gathering.any { it.userId == myId && it.isCouple }) return false
        val locked = c.seatingLocked &&
            c.pastorPhase != "reception" &&
            c.phase != "reception" &&
            c.pastorPhase != "married" &&
            c.phase != "gifts"
        if (!locked) return false
        val mePresent = c.gathering.any { it.userId == myId && it.present }
        return !mePresent
    }

    LaunchedEffect(Unit) {
        emojiBar = withContext(Dispatchers.IO) {
            runCatching { LuvApp.instance.prefs.emojiBar() }
                .getOrDefault(ShopCatalog.DEFAULT_BAR)
        }
    }

    LaunchedEffect(
        ceremony?.altarHoldActive,
        ceremony?.seatingLocked,
        ceremony?.pastorPhase,
        ceremony?.phase,
        myX,
        myY,
        seated,
        ceremony?.gathering,
        zones,
    ) {
        val couple = ceremony?.gathering?.filter { it.isCouple }.orEmpty()
        // Music-Box NUR wenn beide live anwesend und auf gelbem Altar sitzen
        // (altarHoldActive allein reicht nicht — Ghost-Reseat-Bug)
        fun guestOnCoupleAltar(g: LuvApiClient.CeremonyGuest): Boolean {
            if (!g.present) return false
            val seatId = g.seatedSeatId
            if (seatId != null) {
                val seatZone = zones.firstOrNull { it.id == seatId }
                if (seatZone?.isCoupleSeat == true) return true
            }
            return inRoleSeatZone(true, g.x, g.y)
        }
        val bothOnAltar = couple.size >= 2 && couple.all { g ->
            if (g.userId == myId) {
                g.present && seated && inRoleSeatZone(true, myX, myY)
            } else {
                guestOnCoupleAltar(g)
            }
        }
        chapelMusic.sync(ceremony, bothCoupleOnAltar = bothOnAltar)
    }

    // Presence-Heartbeat — sonst verschwinden Idle-Gäste nach dem TTL
    LaunchedEffect(entered, leaving) {
        if (!entered || leaving) return@LaunchedEffect
        while (true) {
            runCatching { LuvApiClient.ceremonyPresence("gathering") }
                .onSuccess { c -> if (c != null) ceremony = c }
            delay(18_000)
        }
    }

    // Remote-Avatare weich zur Server-Position (schnell genug für flüssiges Laufen)
    LaunchedEffect(Unit) {
        while (true) {
            ceremony?.gathering
                ?.filter { it.userId != myId && it.present }
                ?.forEach { g ->
                    val cx = remoteX[g.userId] ?: g.x
                    val cy = remoteY[g.userId] ?: g.y
                    val dx = g.x - cx
                    val dy = g.y - cy
                    val dist = hypot(dx, dy)
                    val t = when {
                        dist > 0.20f -> 0.55f
                        dist > 0.08f -> 0.42f
                        else -> 0.28f
                    }
                    remoteX[g.userId] = cx + dx * t
                    remoteY[g.userId] = cy + dy * t
                }
            delay(16)
        }
    }

    LaunchedEffect(Unit) {
        // Ceremony zuerst (enthält oft schon roomLayout) + Layout per layoutId als Fallback
        coroutineScope {
            val firstResult = runCatching { LuvApiClient.fetchCeremony() }
            firstResult.onFailure { e ->
                val err = (e as? com.luv.couple.net.LuvApiException)?.error
                if (err == "reception_over") {
                    Toast.makeText(
                        context,
                        e.message ?: "Empfang vorbei",
                        Toast.LENGTH_LONG
                    ).show()
                    onClose()
                }
            }
            val first = firstResult.getOrNull()
            if (first != null) {
                if (shouldKickLatecomer(first.ceremony)) {
                    Toast.makeText(
                        context,
                        "Die Zeremonie ist im Gange — es wäre unhöflich, jetzt hereinzuplatzen.",
                        Toast.LENGTH_LONG
                    ).show()
                    onClose()
                    return@coroutineScope
                }
                applyCeremonyBundle(first)
            }
            if (layout == null) {
                val lid = first?.ceremony?.ceremonyLayoutId?.ifBlank { null }
                    ?: first?.roomLayout?.id?.ifBlank { null }
                    ?: "wedding"
                val cached = WeddingChapelCache.layout?.takeIf { it.id == lid }
                applyLayout(
                    cached
                        ?: runCatching { LuvApiClient.fetchRoomLayout(lid) }.getOrNull()
                )
            }
        }
        while (rejectName == null) {
            runCatching { LuvApiClient.fetchCeremony() }
                .onSuccess { bundle ->
                    if (shouldKickLatecomer(bundle.ceremony)) {
                        Toast.makeText(
                            context,
                            "Die Zeremonie ist im Gange — es wäre unhöflich, jetzt hereinzuplatzen.",
                            Toast.LENGTH_LONG
                        ).show()
                        onClose()
                        return@LaunchedEffect
                    }
                    applyCeremonyBundle(bundle)
                    val phase = bundle.ceremony?.pastorPhase
                    if (phase == "married" || phase == "reception") {
                        if (!married) {
                            married = true
                            confetti = true
                        }
                    }
                    if (phase == "reception") {
                        if (receptionStartedAt == 0L) {
                            receptionStartedAt = System.currentTimeMillis()
                        }
                        val iAmCouple = bundle.ceremony?.gathering
                            ?.any { it.userId == myId && it.isCouple } == true
                        if (iAmCouple && !congratsShownOnce) {
                            congratsShownOnce = true
                            showReceptionCongrats = true
                        }
                    }
                    // Remote Applaus / Konfetti
                    bundle.ceremony?.applauseBursts?.forEach { b ->
                        if (b.at > 0L && b.at !in seenApplauseAts) {
                            seenApplauseAts = seenApplauseAts + b.at
                            applauseBursts = applauseBursts + Triple(b.x, b.y, b.at)
                        }
                    }
                    bundle.ceremony?.confettiBursts?.forEach { b ->
                        if (b.at > 0L && b.at !in seenConfettiAts) {
                            seenConfettiAts = seenConfettiAts + b.at
                            confettiBursts = confettiBursts + Triple(b.x, b.y, b.at)
                        }
                    }
                    // Pastor-Rede + Empfang weiter pollen, nicht nach dem Ja abbrechen
                    if (
                        phase == "reception" ||
                        bundle.ceremony?.phase == "reception" ||
                        phase == "gifts_claim" ||
                        bundle.ceremony?.phase == "gifts_claim" ||
                        phase == "ended"
                    ) {
                        // Empfang läuft — langsam weiter pollen reicht
                    }
                }
                .onFailure { e ->
                    val err = (e as? com.luv.couple.net.LuvApiException)?.error
                    if (err == "reception_over") {
                        Toast.makeText(
                            context,
                            e.message ?: "Empfang vorbei",
                            Toast.LENGTH_LONG
                        ).show()
                        onClose()
                        return@LaunchedEffect
                    }
                    if (
                        err == "ceremony_aborted" ||
                        e.message?.contains("abgebrochen", ignoreCase = true) == true
                    ) {
                        if (rejectName == null) rejectName = "Die Trauung"
                    }
                }
            val cNow = ceremony
            val fast =
                cNow?.altarHoldActive == true ||
                    cNow?.pastorPhase == "dots" ||
                    cNow?.pastorPhase == "speech" ||
                    cNow?.pastorPhase == "vows" ||
                    cNow?.pastorPhase == "closing_no" ||
                    cNow?.pastorPhase == "married"
            // Im Raum oft pollen = flüssigeres Laufen für andere
            delay(if (fast) 280 else if (entered) 380 else 900)
        }
    }

    val giftTargetUserId = remember(ceremony, marriage, myId) {
        ceremony?.gathering?.firstOrNull { it.isCouple }?.userId
            ?: marriage?.partnerId
            ?: myId.takeIf { it.isNotBlank() }
    }
    val canGift = ceremony?.showGiftButton == true && !giftTargetUserId.isNullOrBlank()
    val canGuestbook = ceremony?.showGuestbookButton == true && !giftTargetUserId.isNullOrBlank()
    val canApplause = ceremony?.showApplause == true
    val meIsCouple = ceremony?.gathering?.find { it.userId == myId }?.isCouple == true

    // Gäste: Applaus-Prompt einmal nach Glückwunsch-Zeitfenster (~6s Empfang)
    LaunchedEffect(receptionStartedAt, canApplause, meIsCouple, guestApplausePromptDone) {
        if (meIsCouple || guestApplausePromptDone || !canApplause) return@LaunchedEffect
        if (receptionStartedAt <= 0L) return@LaunchedEffect
        val wait = (6_000L - (System.currentTimeMillis() - receptionStartedAt)).coerceAtLeast(0L)
        delay(wait)
        if (!guestApplausePromptDone && canApplause) {
            showGuestApplausePrompt = true
        }
    }

    // Auch aus laufendem Poll Applaus/Konfetti mergen
    LaunchedEffect(ceremony?.applauseBursts, ceremony?.confettiBursts) {
        ceremony?.applauseBursts?.forEach { b ->
            if (b.at > 0L && b.at !in seenApplauseAts) {
                seenApplauseAts = seenApplauseAts + b.at
                applauseBursts = applauseBursts + Triple(b.x, b.y, b.at)
            }
        }
        ceremony?.confettiBursts?.forEach { b ->
            if (b.at > 0L && b.at !in seenConfettiAts) {
                seenConfettiAts = seenConfettiAts + b.at
                confettiBursts = confettiBursts + Triple(b.x, b.y, b.at)
            }
        }
    }

    if (rejectName != null && ceremony?.pastorPhase != "closing_no") {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (rejectName == "Die Trauung") {
                    "Die Trauung wurde abgebrochen."
                } else {
                    "${rejectName} hat Nein gesagt"
                },
                color = Color.White,
                fontFamily = DisplayFont,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
        LaunchedEffect(rejectName) {
            delay(5200)
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
        val layoutId = layout?.id?.ifBlank { null }
            ?: ceremony?.ceremonyLayoutId?.ifBlank { null }
            ?: "wedding"
        val roomPainter = painterResource(
            when (layoutId) {
                "wedding_small" -> R.drawable.wedding_small_room
                "wedding_grand" -> R.drawable.wedding_grand_room
                else -> R.drawable.wedding_chapel_room
            }
        )
        val aspect = run {
            val s = roomPainter.intrinsicSize
            if (s.height > 0f) (s.width / s.height) else 0.72f
        }
        val roomH = if (maxWidth / maxHeight > aspect) maxHeight else maxWidth / aspect
        val roomW = roomH * aspect

        // Raumbild + Spiel-Layer in exakter Bildgröße (Sitze liegen auf den Bänken)
        Box(
            modifier = Modifier
                .size(roomW, roomH)
                .align(Alignment.Center)
        ) {
            Image(
                painter = roomPainter,
                contentDescription = "Trausaal",
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
                                        .onFailure { e ->
                                            Toast.makeText(
                                                context,
                                                e.message ?: "Eintreten fehlgeschlagen",
                                                Toast.LENGTH_SHORT
                                            ).show()
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
                        s < 40.dp -> 40.dp
                        s > 120.dp -> 120.dp
                        else -> s
                    }
                }
                // Milchige Sitzbereiche: Gäste sehen Blau, Brautpaar Gelb
                val guideZones = if (meIsCouple) {
                    zones.filter { it.isCoupleSeat }
                } else {
                    zones.filter { it.isGuestSeat }
                }
                if (guideZones.isNotEmpty()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val milk = Color(0x66FFFFFF)
                        val edge = Color(0xAAFFFFFF)
                        for (z in guideZones) {
                            if (z.shape == "circle") {
                                val cx = z.cx * size.width
                                val cy = z.cy * size.height
                                val rad = (z.r * size.minDimension).coerceAtLeast(8f)
                                drawCircle(color = milk, radius = rad, center = Offset(cx, cy))
                                drawCircle(
                                    color = edge,
                                    radius = rad,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = 2.5.dp.toPx()),
                                )
                            } else {
                                val left = z.x * size.width
                                val top = z.y * size.height
                                val w = z.w * size.width
                                val h = z.h * size.height
                                drawRect(
                                    color = milk,
                                    topLeft = Offset(left, top),
                                    size = Size(w, h),
                                )
                                drawRect(
                                    color = edge,
                                    topLeft = Offset(left, top),
                                    size = Size(w, h),
                                    style = Stroke(width = 2.5.dp.toPx()),
                                )
                            }
                        }
                    }
                }
                // Admin-Deko / Flammen
                zones.filter { it.isDecor || it.isFlame }.forEach { z ->
                    val sz = (roomW * (z.r * 2f).coerceIn(0.03f, 0.12f)).coerceIn(18.dp, 56.dp)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = roomW * z.sitX - sz / 2,
                                top = roomH * z.sitY - sz / 2
                            )
                    ) {
                        if (z.isFlame) FlameDecor(size = sz) else DecorMarker(size = sz)
                    }
                }
                // Geldbäume nur wenn gebucht (kein Hindernis — Tippen für 1 Coin, nur Gäste)
                val treesOn = c?.moneyTreesEnabled == true
                val claimedTrees = claimedMoneyTrees + c?.claimedMoneyTreeIds.orEmpty()
                if (treesOn) {
                    zones.filter { it.isMoneyTree }.forEach { z ->
                        val claimed = claimedTrees.contains(z.id)
                        val sz = MoneyTreeSize
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(
                                    start = roomW * z.sitX - sz / 2,
                                    top = roomH * z.sitY - sz / 2,
                                )
                                .size(sz)
                                .clip(CircleShape)
                                .background(
                                    if (claimed) Color.White.copy(0.25f)
                                    else Color.White.copy(0.55f)
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.wedding_money_tree),
                                contentDescription = "Geldbaum",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                                contentScale = ContentScale.Fit,
                                alpha = if (claimed) 0.45f else 1f,
                            )
                        }
                    }
                }

                // Nur Anwesende zeichnen — Offline-Partner nicht als Geist im Raum
                c?.gathering
                    ?.filter { it.userId == myId || it.present }
                    ?.forEach { g ->
                    val ax = if (g.userId == myId) myX else remoteX[g.userId] ?: g.x
                    val ay = if (g.userId == myId) myY else remoteY[g.userId] ?: g.y
                    // Ring nur in passender Zone (nicht bei Server-Sitz allein)
                    val isSeatedHere = inRoleSeatZone(g.isCouple, ax, ay)
                    val vowsActive = c?.vowsReady == true
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = (roomW * ax) - avatarDp / 2,
                                top = (roomH * ay) - avatarDp / 2
                            )
                            .size(avatarDp)
                            .clip(CircleShape)
                            .background(AvatarFill)
                            .border(
                                2.5.dp,
                                if (isSeatedHere) SitRingBlue else IdleRing,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val showReact = when {
                            g.userId == myId && myReaction != null -> myReaction
                            g.reaction != null && g.reactionUntil > System.currentTimeMillis() -> g.reaction
                            else -> null
                        }
                        val glyphSp = (avatarDp.value * 0.52f).sp
                        // Ja/Nein-Füllung über dem blauen Ring
                        if (vowsActive && g.isCouple && g.vowProgress > 0f) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(
                                    color = if (g.vow == "no") {
                                        Color(0xCCE53935)
                                    } else {
                                        Color(0xCC43A047)
                                    },
                                    startAngle = -90f,
                                    sweepAngle = 360f * g.vowProgress.coerceIn(0f, 1f),
                                    useCenter = true,
                                )
                            }
                        }
                        when {
                            showReact != null -> ItemGlyph(id = clipItemId(showReact), fontSize = glyphSp)
                            else -> ItemGlyph(
                                id = clipItemId(g.petEmoji).ifBlank { "🐣" },
                                fontSize = glyphSp,
                            )
                        }
                    }
                }

                // Priester am Altar
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            start = roomW * PRIEST_X - PriestSize / 2,
                            top = roomH * PRIEST_Y - PriestSize / 2
                        )
                ) {
                    Image(
                        painter = painterResource(R.drawable.wedding_priest),
                        contentDescription = "Priester",
                        modifier = Modifier
                            .size(PriestSize)
                            .clip(CircleShape)
                            .border(1.5.dp, Color.White.copy(0.7f), CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    if (c?.pastorPhase == "dots") {
                        PastorDotsBubble(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 18.dp, y = (-22).dp)
                        )
                    }
                }

                // Tippen: Sitze / Laufen (auch vom Sitz aufstehen)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(
                            zones,
                            avatarR,
                            meIsCouple,
                            ceremony?.canStand,
                            ceremony?.seatingLocked,
                            ceremony?.moneyTreesEnabled,
                        ) {
                            detectTapGestures { offset ->
                                if (walking) return@detectTapGestures
                                val rawX = (offset.x / size.width).coerceIn(0.01f, 0.99f)
                                val rawY = (offset.y / size.height).coerceIn(0.01f, 0.99f)
                                val cNow = ceremonyLatest
                                val claimedNow =
                                    claimedTreesLatest + cNow?.claimedMoneyTreeIds.orEmpty()

                                // Sitz hat Vorrang vor Geldbaum (vordere Bänke bleiben tippbar)
                                val nearSeat = zones
                                    .asSequence()
                                    .filter { z ->
                                        if (meIsCouple) z.isCoupleSeat else z.isGuestSeat
                                    }
                                    .minByOrNull { z -> hypot(rawX - z.sitX, rawY - z.sitY) }
                                    ?.takeIf { z ->
                                        zoneContains(z, rawX, rawY, 0.04f) ||
                                            hypot(rawX - z.sitX, rawY - z.sitY) <
                                            (avatarR * 2.2f).coerceAtLeast(0.05f)
                                    }

                                // Nur Gäste, nur wenn Geldbäume gebucht, eng am ungeernteten Baum
                                if (
                                    nearSeat == null &&
                                    !meIsCouple &&
                                    cNow?.moneyTreesEnabled == true
                                ) {
                                    val hitTree = zones
                                        .asSequence()
                                        .filter { z ->
                                            z.isMoneyTree && z.id !in claimedNow
                                        }
                                        .minByOrNull { z ->
                                            hypot(rawX - z.sitX, rawY - z.sitY)
                                        }
                                        ?.takeIf { z ->
                                            val hitR = (z.r + 0.015f).coerceAtLeast(TREE_SNAP_R)
                                            zoneContains(z, rawX, rawY, 0.01f) ||
                                                hypot(rawX - z.sitX, rawY - z.sitY) < hitR
                                        }
                                    if (hitTree != null) {
                                        scope.launch {
                                            runCatching {
                                                LuvApiClient.ceremonyClaimMoneyTree(hitTree.id)
                                            }
                                                .onSuccess { r ->
                                                    claimedMoneyTrees =
                                                        claimedMoneyTrees + hitTree.id
                                                    if (r.ceremony != null) {
                                                        ceremony = r.ceremony
                                                        claimedMoneyTrees =
                                                            claimedMoneyTrees +
                                                                r.ceremony.claimedMoneyTreeIds
                                                    }
                                                    r.account?.let { AccountSession.setAccount(it) }
                                                    Toast.makeText(
                                                        context,
                                                        r.message
                                                            ?: if (r.already) "Schon geerntet"
                                                            else "+1 Coin!",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                                .onFailure { e ->
                                                    Toast.makeText(
                                                        context,
                                                        e.message ?: "Baum leer",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                        }
                                        return@detectTapGestures
                                    }
                                }

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
                                    var lastSend = 0L
                                    walkAlongPath(
                                        path,
                                        { x, y -> myX = x; myY = y },
                                        { myX },
                                        { myY },
                                        onStep = {
                                            val now = SystemClock.elapsedRealtime()
                                            // ~13 Updates/s — flüssig, aber leicht für den Server
                                            if (now - lastSend >= 75L) {
                                                lastSend = now
                                                scope.launch {
                                                    runCatching {
                                                        LuvApiClient.ceremonyMove(myX, myY)
                                                    }.onSuccess { info ->
                                                        if (info != null) {
                                                            ceremony = info
                                                            seated = info.gathering
                                                                .find { it.userId == myId }
                                                                ?.seatedSeatId != null
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    )
                                    runCatching { LuvApiClient.ceremonyMove(myX, myY) }
                                        .onSuccess { info ->
                                            ceremony = info ?: ceremony
                                            seated = info?.gathering
                                                ?.find { it.userId == myId }
                                                ?.seatedSeatId != null
                                                || inRoleSeatZone(meIsCouple, myX, myY)
                                        }
                                        .onFailure {
                                            Toast.makeText(
                                                context,
                                                it.message ?: "Bewegung nicht möglich",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    return true
                                }

                                // Während Zeremonie-Lock: nur stehen bleiben
                                if (ceremony?.canStand == false && seated) {
                                    scope.launch {
                                        Toast.makeText(
                                            context,
                                            "Während der Zeremonie bleibt ihr sitzen.",
                                            Toast.LENGTH_SHORT
                                        ).show()
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
                                    // Sitzstatus kommt vom Server (in blau/gelb = sitzen)
                                    seated = ceremony?.gathering
                                        ?.find { it.userId == myId }
                                        ?.seatedSeatId != null ||
                                        inRoleSeatZone(meIsCouple, myX, myY)
                                    walking = false
                                }
                            }
                        }
                )

                // Vow-Popup für Brautpaar — erst nach Pastor-Rede
                val meGuest = c?.gathering?.find { it.userId == myId }
                if (c?.vowsReady == true && meGuest?.isCouple == true && seated) {
                    val lockedFromServer =
                        when {
                            (meGuest.vowProgress >= 1f && meGuest.vow == "yes") -> "yes"
                            (meGuest.vowProgress >= 1f && meGuest.vow == "no") -> "no"
                            else -> null
                        }
                    VowDecisionPopup(
                        lockedChoice = lockedFromServer,
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
                                    ceremony = r.ceremony ?: ceremony
                                } else {
                                    ceremony = r?.ceremony ?: ceremony
                                }
                            }
                        }
                    )
                }

                // Hinweis / Timer / Pastor oben
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = 4.dp, start = 10.dp, end = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        c?.altarHoldActive == true ->
                            AltarHoldTimerBanner(
                                remainingMs = c.altarHoldRemainingMs,
                                totalMs = c.altarHoldTotalMs
                            )
                        c?.pastorPhase == "speech" ||
                            c?.pastorPhase == "closing_no" ||
                            c?.pastorPhase == "married" ->
                            PastorSpeechTile(visibleText = c.pastorLineVisible)
                        (c?.pastorPhase == "reception" || c?.phase == "reception") &&
                            c.receptionRemainingMs > 0L ->
                            ReceptionTimerBanner(remainingMs = c.receptionRemainingMs)
                        c?.vowsReady != true &&
                            c?.pastorPhase != "dots" &&
                            c?.seatingLocked != true ->
                            SeatGuideBanner(forCouple = meIsCouple)
                    }
                }
            }
        } // Kapellen-Box

        // Reaktions-Emoji: rechts oben über dem Kapellenbild (nicht im Raum)
        if (entered) {
            val roomTop = (maxHeight - roomH) / 2
            val roomEndPad = (maxWidth - roomW) / 2
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = (roomTop - 52.dp).coerceAtLeast(4.dp),
                        end = (roomEndPad + 4.dp).coerceAtLeast(8.dp),
                    )
                    .heightIn(max = roomH * 0.55f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xCC1E2430))
                    .padding(6.dp)
                    .verticalScroll(rememberScrollState()),
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

        if (confetti) {
            ConfettiOverlay()
            LaunchedEffect(Unit) {
                delay(5200)
                confetti = false
            }
        }
        // Glückwunsch-Karte nur einmal
        if (meIsCouple && showReceptionCongrats) {
            LaunchedEffect(Unit) {
                delay(5500)
                showReceptionCongrats = false
            }
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xF2FFF8F0))
                    .border(2.dp, Color(0xFFD4A017), RoundedCornerShape(24.dp))
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("💍", fontSize = 28.sp)
                SpacerH()
                Text(
                    "Glückwunsch",
                    color = Color(0xFF5C2A3A),
                    fontFamily = DisplayFont,
                    fontSize = 26.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Ihr seid verheiratet!",
                    color = Color(0xFF3E2723),
                    fontFamily = BodyFont,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Burst-Koordinaten relativ zur Kapelle (nicht Fullscreen)
        val chapelLeft = (maxWidth - roomW) / 2
        val chapelTop = (maxHeight - roomH) / 2
        confettiBursts.forEach { (px, py, at) ->
            key(at) {
                val mappedX =
                    ((chapelLeft + roomW * px.coerceIn(0f, 1f)) / maxWidth).coerceIn(0.05f, 0.95f)
                val mappedY =
                    ((chapelTop + roomH * py.coerceIn(0f, 1f)) / maxHeight).coerceIn(0.05f, 0.95f)
                PositionedConfettiBurst(
                    originX = mappedX,
                    originY = mappedY,
                    onDone = {
                        confettiBursts = confettiBursts.filterNot { it.third == at }
                    }
                )
            }
        }

        applauseBursts.forEach { (px, py, at) ->
            key(at) {
                Text(
                    "👏",
                    fontSize = 32.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = chapelLeft + roomW * px.coerceIn(0f, 1f) - 16.dp,
                            y = chapelTop + roomH * py.coerceIn(0f, 1f) - 16.dp
                        )
                )
                LaunchedEffect(at) {
                    delay(1600)
                    applauseBursts = applauseBursts.filterNot { it.third == at }
                }
            }
        }

        // Applaus-Prompt einmalig (zusätzlich Dauer-Button unten)
        if (!meIsCouple && showGuestApplausePrompt && canApplause) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xCCE91E63))
                    .clickable {
                        guestApplausePromptDone = true
                        showGuestApplausePrompt = false
                        scope.launch {
                            playApplauseSound(context)
                            val now = System.currentTimeMillis()
                            val extras = List(10) {
                                Triple(
                                    Random.nextFloat() * 0.7f + 0.15f,
                                    Random.nextFloat() * 0.55f + 0.15f,
                                    now + it
                                )
                            }
                            applauseBursts = applauseBursts + Triple(myX, myY, now) + extras
                            seenApplauseAts = seenApplauseAts + now
                            runCatching { LuvApiClient.ceremonyApplause(myX, myY) }
                                .onSuccess { if (it != null) ceremony = it }
                        }
                    }
                    .padding(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Text("Applaus 👏", color = Color.White, fontFamily = DisplayFont, fontSize = 16.sp)
            }
        }

        // Untere Leiste: zurück + Konfetti + Applaus + Geschenk + Gästebuch + Lautsprecher
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xE61E2430))
                .padding(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (!meIsCouple) "←" else "←",
                    color = Color.White,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(0.7f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(0.10f))
                        .clickable(onClick = { showLeaveConfirm = true })
                        .padding(vertical = 6.dp),
                )
                Text(
                    "🎉",
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(0.7f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x88FFD54F))
                        .clickable {
                            val now = System.currentTimeMillis()
                            confettiBursts = confettiBursts + Triple(myX, myY, now)
                            seenConfettiAts = seenConfettiAts + now
                            scope.launch {
                                runCatching { LuvApiClient.ceremonyConfetti(myX, myY) }
                                    .onSuccess { if (it != null) ceremony = it }
                            }
                        }
                        .padding(vertical = 8.dp),
                )
                if (canApplause) {
                    Text(
                        "👏",
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(0.7f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x88E91E63))
                            .clickable {
                                guestApplausePromptDone = true
                                showGuestApplausePrompt = false
                                scope.launch {
                                    playApplauseSound(context)
                                    val now = System.currentTimeMillis()
                                    applauseBursts = applauseBursts + Triple(myX, myY, now)
                                    seenApplauseAts = seenApplauseAts + now
                                    runCatching { LuvApiClient.ceremonyApplause(myX, myY) }
                                        .onSuccess { if (it != null) ceremony = it }
                                }
                            }
                            .padding(vertical = 8.dp),
                    )
                }
                if (canGift) {
                    Text(
                        "🎁",
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(0.75f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x88E91E63))
                            .clickable { showGiftPicker = true }
                            .padding(vertical = 8.dp),
                    )
                }
                if (canGuestbook) {
                    Text(
                        "📖",
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(0.75f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x887E57C2))
                            .clickable { showGuestbook = true }
                            .padding(vertical = 8.dp),
                    )
                }
                Text(
                    if (musicMuted) "🔇" else "🔊",
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(0.7f)
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
        if (showGuestbook && !giftTargetUserId.isNullOrBlank()) {
            WeddingGuestbookDialog(
                coupleUserId = giftTargetUserId,
                onDismiss = { showGuestbook = false },
                onWritten = {
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

        if (showLeaveConfirm) {
            val ceremonyStarted = ceremony?.seatingLocked == true &&
                ceremony?.pastorPhase != "reception" &&
                ceremony?.phase != "reception"
            val leaveBody = when {
                meIsCouple ->
                    "Raum wirklich verlassen?\nIhr als Brautpaar könnt jederzeit wieder eintreten."
                ceremonyStarted ->
                    "Raum wirklich verlassen?\nDie Zeremonie hat begonnen — als Gast kommt ihr danach nicht mehr rein."
                else ->
                    "Raum wirklich verlassen?"
            }
            AlertDialog(
                onDismissRequest = { showLeaveConfirm = false },
                containerColor = Color(0xF2FFF8F0),
                title = {
                    Text(
                        "Zeremonie verlassen?",
                        fontFamily = DisplayFont,
                        color = Color(0xFF5C2A3A),
                        fontSize = 20.sp,
                    )
                },
                text = {
                    Text(
                        leaveBody,
                        fontFamily = BodyFont,
                        color = Color(0xFF3E2723),
                        fontSize = 15.sp,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLeaveConfirm = false
                            leaveRoom()
                        }
                    ) {
                        Text("Verlassen", color = Color(0xFFE53935), fontFamily = DisplayFont)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLeaveConfirm = false }) {
                        Text("Bleiben", color = AccentRose, fontFamily = DisplayFont)
                    }
                },
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
private fun SeatGuideBanner(forCouple: Boolean) {
    val text = if (forCouple) {
        "Bitte stellt euch vor den Altar (helle Fläche)."
    } else {
        "Gäste: bitte auf die Bänke setzen (helle Flächen)."
    }
    Text(
        text,
        color = Color(0xFF3E2723),
        fontFamily = BodyFont,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xE6FFF8F0))
            .border(1.dp, Color(0x66D4A017), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private const val VOW_HOLD_MS = 15_000

@Composable
private fun VowDecisionPopup(
    lockedChoice: String? = null,
    onYesProgress: (Float) -> Unit,
    onNoProgress: (Float) -> Unit,
) {
    var locked by remember { mutableStateOf(lockedChoice) }
    LaunchedEffect(lockedChoice) {
        if (lockedChoice != null) locked = lockedChoice
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xF7FFF8F0))
                .border(2.dp, Color(0xFFD4A017), RoundedCornerShape(26.dp))
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("💍", fontSize = 28.sp)
            Text(
                "Wollt ihr euch heiraten?",
                color = Color(0xFF5C2A3A),
                fontFamily = DisplayFont,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                when (locked) {
                    "yes" -> "Ja eingerastet — warte auf Partner…"
                    "no" -> "Nein eingerastet"
                    else -> "Taste 15 Sekunden gedrückt halten"
                },
                color = Color(0xFF6D4C41),
                fontFamily = BodyFont,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            if (locked == null || locked == "yes") {
                VowHoldSlider(
                    label = "Ja",
                    color = Color(0xFF43A047),
                    locked = locked == "yes",
                    onProgress = { p ->
                        onYesProgress(p)
                        if (p >= 1f) locked = "yes"
                    },
                )
            }
            if (locked == null || locked == "no") {
                VowHoldSlider(
                    label = "Nein",
                    color = Color(0xFFE53935),
                    locked = locked == "no",
                    onProgress = { p ->
                        onNoProgress(p)
                        if (p >= 1f) locked = "no"
                    },
                )
            }
        }
    }
}

@Composable
private fun VowHoldSlider(
    label: String,
    color: Color,
    locked: Boolean = false,
    onProgress: (Float) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val progress = remember { Animatable(if (locked) 1f else 0f) }
    LaunchedEffect(locked) {
        if (locked) {
            progress.snapTo(1f)
            onProgress(1f)
        }
    }
    LaunchedEffect(pressed, locked) {
        if (locked) return@LaunchedEffect
        if (pressed) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(VOW_HOLD_MS, easing = LinearEasing))
            onProgress(1f)
        } else {
            // Nicht auf 0 zurücksetzen wenn schon voll (Finger los nach Einrasten)
            if (progress.value < 0.999f) {
                progress.snapTo(0f)
                onProgress(0f)
            }
        }
    }
    LaunchedEffect(progress.value, locked) {
        if (locked) return@LaunchedEffect
        if (pressed && progress.value > 0f && progress.value < 1f) {
            onProgress(progress.value)
        }
    }
    val remainSec = ceil((1f - progress.value) * (VOW_HOLD_MS / 1000f)).toInt().coerceAtLeast(0)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(if (locked) 0.22f else 0.12f))
            .border(
                if (locked) 2.5.dp else 1.5.dp,
                color.copy(if (locked) 1f else 0.75f),
                RoundedCornerShape(18.dp),
            )
            .then(
                if (locked) Modifier
                else Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = {},
                )
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                color = color,
                fontFamily = DisplayFont,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                when {
                    locked -> "✓ eingerastet"
                    pressed -> "${remainSec}s"
                    else -> "15s halten"
                },
                color = Color(0xFF5D4037),
                fontFamily = BodyFont,
                fontSize = 13.sp,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(0.55f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                    .height(14.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(color.copy(0.75f), color),
                        )
                    ),
            )
        }
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

@Composable
private fun PositionedConfettiBurst(
    originX: Float,
    originY: Float,
    onDone: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val bits = remember {
        List(28) {
            Triple(
                (Random.nextFloat() - 0.5f) * 0.35f,
                -(0.12f + Random.nextFloat() * 0.45f),
                Color(
                    listOf(0xFFFFD54F, 0xFFFF5A6A, 0xFF7CFF6B, 0xFF3DD6FF, 0xFFFF4FC3, 0xFFFFFFFF)
                        .random()
                )
            )
        }
    }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(1400, easing = LinearEasing))
        onDone()
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val ox = size.width * originX.coerceIn(0.05f, 0.95f)
        val oy = size.height * originY.coerceIn(0.05f, 0.95f)
        val t = progress.value
        bits.forEach { (dx, dy, col) ->
            val x = ox + dx * size.width * t
            val y = oy + dy * size.height * t + (t * t * size.height * 0.12f)
            val alpha = (1f - t).coerceIn(0f, 1f)
            drawCircle(
                color = col.copy(alpha = alpha),
                radius = 5f + (1f - t) * 3f,
                center = Offset(x, y)
            )
        }
    }
}

private fun playApplauseSound(context: android.content.Context) {
    runCatching {
        val p = android.media.MediaPlayer.create(context, com.luv.couple.R.raw.wedding_applause)
            ?: return
        p.setOnCompletionListener { mp ->
            runCatching { mp.release() }
        }
        p.setVolume(0.45f, 0.45f)
        p.start()
    }
}
