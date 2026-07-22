package com.luv.couple.ui.wedding

import android.os.Bundle
import android.os.SystemClock
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.activity.addCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
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
        onBackPressedDispatcher.addCallback(this) {
            leaveRoomThenFinish()
        }
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
/** Großzügiger Sitz-Treffer (Norm-Koordinaten 0–1) */
private const val TREE_SNAP_R = 0.22f

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
    val remoteX = remember { mutableStateMapOf<String, Float>() }
    val remoteY = remember { mutableStateMapOf<String, Float>() }
    var marriage by remember { mutableStateOf<LuvApiClient.MarriageInfo?>(null) }
    var showGiftPicker by remember { mutableStateOf(false) }
    var showGuestbook by remember { mutableStateOf(false) }
    var applauseBursts by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
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
    var myReaction by remember { mutableStateOf<String?>(null) }
    var layout by remember { mutableStateOf(cachedLayout) }
    var spawned by remember { mutableStateOf(cachedLayout != null) }
    var reactionExpanded by remember { mutableStateOf(false) }
    var emojiBar by remember { mutableStateOf(ShopCatalog.DEFAULT_BAR) }
    var leaving by remember { mutableStateOf(false) }

    fun leaveRoom() {
        if (leaving) return
        leaving = true
        onClose()
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
        if (
            it.ceremony?.phase == "altar" ||
            it.ceremony?.phase == "vows" ||
            it.ceremony?.phase == "gifts" ||
            it.ceremony?.phase == "reception" ||
            it.ceremony?.seatingLocked == true
        ) {
            entered = true
        }
        if (
            it.ceremony?.pastorPhase == "ended" ||
            it.ceremony?.phase == "ended"
        ) {
            if (rejectName == null) rejectName = "Die Trauung"
        }
    }

    /** Zu spät / Zeremonie läuft und man war nicht dabei → zurück. */
    fun shouldKickLatecomer(c: LuvApiClient.CeremonyInfo?): Boolean {
        if (c == null) return false
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
        ceremony?.pastorPhase,
        ceremony?.phase,
        myX,
        myY,
        seated,
        ceremony?.gathering,
    ) {
        val couple = ceremony?.gathering?.find { it.userId == myId }?.isCouple == true
        chapelMusic.sync(
            ceremony,
            coupleOnAltar = couple && inRoleSeatZone(true, myX, myY),
        )
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
        // Ceremony zuerst (enthält oft schon roomLayout) + Layout parallel als Fallback
        coroutineScope {
            val layoutJob = async {
                WeddingChapelCache.layout
                    ?: runCatching { LuvApiClient.fetchRoomLayout("wedding") }.getOrNull()
            }
            val first = runCatching { LuvApiClient.fetchCeremony() }.getOrNull()
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
                applyLayout(layoutJob.await())
            } else {
                layoutJob.await()?.let { WeddingChapelCache.put(it) }
            }
        }
        while (!married && rejectName == null) {
            runCatching { LuvApiClient.fetchCeremony() }
                .onSuccess { applyCeremonyBundle(it) }
                .onFailure { e ->
                    val err = (e as? com.luv.couple.net.LuvApiException)?.error
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
                // Geldbäume
                val claimedTrees = c?.claimedMoneyTreeIds.orEmpty().toSet()
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
                        // Daumen nur während aktivem Ja-Sagen, nicht danach
                        val vowThumb = when {
                            vowsActive && g.isCouple && g.vowProgress > 0f && g.vowProgress < 1f &&
                                g.vow == "no" -> "👎"
                            vowsActive && g.isCouple && g.vowProgress > 0f && g.vowProgress < 1f &&
                                g.vow == "yes" -> "👍"
                            else -> null
                        }
                        val glyphSp = (avatarDp.value * 0.52f).sp
                        when {
                            vowThumb != null -> Text(vowThumb, fontSize = glyphSp)
                            showReact != null -> ItemGlyph(id = clipItemId(showReact), fontSize = glyphSp)
                            else -> ItemGlyph(
                                id = clipItemId(g.petEmoji).ifBlank { "🐣" },
                                fontSize = glyphSp,
                            )
                        }
                        if (vowsActive && g.isCouple && g.vowProgress > 0f && g.vowProgress < 1f) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(
                                    color = if (g.vow == "no") Color(0xFFE53935) else Color(0xFF43A047),
                                    startAngle = -90f,
                                    sweepAngle = 360f * g.vowProgress,
                                    useCenter = false,
                                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
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
                        .pointerInput(zones, avatarR, meIsCouple, ceremony?.canStand, ceremony?.seatingLocked) {
                            detectTapGestures { offset ->
                                if (walking) return@detectTapGestures
                                val rawX = (offset.x / size.width).coerceIn(0.01f, 0.99f)
                                val rawY = (offset.y / size.height).coerceIn(0.01f, 0.99f)
                                val sitPad = 0.12f

                                // Geldbaum tippen (auch leicht daneben)
                                val hitTree = zones.firstOrNull { z ->
                                    z.isMoneyTree &&
                                        (
                                            zoneContains(z, rawX, rawY, sitPad) ||
                                                hypot(rawX - z.sitX, rawY - z.sitY) < TREE_SNAP_R
                                            )
                                }
                                if (hitTree != null) {
                                    val claimed =
                                        ceremony?.claimedMoneyTreeIds?.contains(hitTree.id) == true
                                    if (!claimed) {
                                        scope.launch {
                                            runCatching {
                                                LuvApiClient.ceremonyClaimMoneyTree(hitTree.id)
                                            }
                                                .onSuccess { r ->
                                                    if (r.ceremony != null) ceremony = r.ceremony
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
                                    }
                                    return@detectTapGestures
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

                // Vow buttons for couple — erst nach Pastor-Rede
                val meGuest = c?.gathering?.find { it.userId == myId }
                if (c?.vowsReady == true && meGuest?.isCouple == true && seated) {
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
                                    // Pastor spricht Closing — Raum bleibt offen
                                    ceremony = r.ceremony ?: ceremony
                                } else {
                                    ceremony = r?.ceremony ?: ceremony
                                }
                            }
                        }
                    )
                }

                // Timer / Pastor-Rede oben im Raum
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = 4.dp),
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
            if (meIsCouple) {
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
        }

        applauseBursts.forEachIndexed { idx, (px, py) ->
            Text(
                "👏",
                fontSize = 28.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (maxWidth * px), y = (maxHeight * py))
            )
            LaunchedEffect(idx) {
                delay(1400)
                if (applauseBursts.size > idx) {
                    applauseBursts = applauseBursts.filterIndexed { i, _ -> i != idx }
                }
            }
        }

        if (canApplause) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xCCE91E63))
                    .clickable {
                        scope.launch {
                            runCatching { LuvApiClient.ceremonyApplause() }
                                .onSuccess { ceremony = it ?: ceremony }
                            applauseBursts = applauseBursts + (
                                Random.nextFloat() * 0.7f + 0.1f to
                                    Random.nextFloat() * 0.6f + 0.15f
                                )
                        }
                    }
                    .padding(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Text("Applaus 👏", color = Color.White, fontFamily = DisplayFont, fontSize = 16.sp)
            }
        }

        // Untere Leiste: zurück + Geschenk/Gästebuch + Lautsprecher
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
                    if (!meIsCouple) "Verlassen" else "←",
                    color = Color.White,
                    fontSize = if (!meIsCouple) 14.sp else 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(0.10f))
                        .clickable(onClick = { leaveRoom() })
                        .padding(vertical = 6.dp),
                )
                if (canGift) {
                    Text(
                        "🎁 Geschenk",
                        fontSize = 14.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1.2f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x88E91E63))
                            .clickable { showGiftPicker = true }
                            .padding(vertical = 10.dp),
                    )
                } else if (canGuestbook) {
                    Text(
                        "📖 Gästebuch",
                        fontSize = 14.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1.2f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x887E57C2))
                            .clickable { showGuestbook = true }
                            .padding(vertical = 10.dp),
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
