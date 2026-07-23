package com.luv.couple.ui.space

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import kotlin.math.min
import kotlin.math.roundToInt
import com.luv.couple.LuvApp
import com.luv.couple.data.PeerPalette
import com.luv.couple.lock.CanvasMemoryKeeper
import com.luv.couple.net.AccountSession
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.PairConnectionService
import com.luv.couple.net.PairEvent
import com.luv.couple.shop.ShopCatalog
import com.luv.couple.ui.ItemGlyph
import com.luv.couple.ui.clipItemId
import com.luv.couple.ui.isImagePetId
import com.luv.couple.ui.screens.ProfileCanvasScreen
import com.luv.couple.ui.theme.LuvTheme
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Custom-Lobby-Raum — ganze Karte auf dem Screen (stabil).
 * Eigene Position ist lokal maßgeblich (kein Server-Zucken).
 */
class CustomRoomActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val code = intent.getStringExtra(EXTRA_CODE).orEmpty()
        val token = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        val bell = intent.getBooleanExtra(EXTRA_BELL, true)
        setContent {
            LuvTheme {
                CustomRoomScreen(
                    code = code,
                    token = token,
                    spaceBell = bell,
                    onClose = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_BELL = "bell"
    }
}

private const val DEFAULT_AVATAR_R = 0.022f
private const val STOVE_IMAGE_URL = "https://reineke.pro/luv/kochen-herdplatte.png"

private fun vibrateShort(context: Context, ms: Long = 80L) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(ms)
        }
    }
}

private fun rectContains(px: Float, py: Float, x: Float, y: Float, w: Float, h: Float): Boolean =
    px >= x && px <= x + w && py >= y && py <= y + h

private fun portalContains(p: LuvApiClient.RoomPortal, x: Float, y: Float): Boolean =
    rectContains(x, y, p.x, p.y, p.w, p.h)

private fun actionContains(a: LuvApiClient.RoomAction, x: Float, y: Float): Boolean =
    rectContains(x, y, a.x, a.y, a.w, a.h)

/** Ziel von anderen Avataren wegschieben; null = zu nah, Abbruch. */
private fun resolveCrowdTarget(
    tx: Float,
    ty: Float,
    others: List<LuvApiClient.RoomSpacePerson>,
    avatarR: Float,
): Pair<Float, Float>? {
    val minDist = avatarR * 2.2f
    var x = tx.coerceIn(0f, 1f)
    var y = ty.coerceIn(0f, 1f)
    repeat(3) {
        for (p in others) {
            val d = hypot(x - p.x, y - p.y)
            if (d < minDist) {
                if (d < 1e-4f) {
                    x = (p.x + minDist).coerceIn(0f, 1f)
                    y = p.y.coerceIn(0f, 1f)
                } else {
                    val scale = minDist / d
                    x = (p.x + (x - p.x) * scale).coerceIn(0f, 1f)
                    y = (p.y + (y - p.y) * scale).coerceIn(0f, 1f)
                }
            }
        }
    }
    if (others.any { hypot(x - it.x, y - it.y) < minDist * 0.97f }) return null
    return x to y
}

private fun nudgeAwayFromOthers(
    x0: Float,
    y0: Float,
    others: List<LuvApiClient.RoomSpacePerson>,
    avatarR: Float,
): Pair<Float, Float> {
    val minDist = avatarR * 2.2f
    var x = x0
    var y = y0
    for (p in others) {
        val d = hypot(x - p.x, y - p.y)
        if (d < minDist) {
            if (d < 1e-4f) {
                x = (p.x + minDist).coerceIn(0f, 1f)
            } else {
                val scale = minDist / d
                x = (p.x + (x - p.x) * scale).coerceIn(0f, 1f)
                y = (p.y + (y - p.y) * scale).coerceIn(0f, 1f)
            }
        }
    }
    return x to y
}

/**
 * Gleiche Projektion wie Admin ([ContentScale.Fit] / letterbox):
 * Bildkoordinaten 0..1 → Screen-Pixel im sichtbaren Kartenrechteck.
 */
private data class MapViewport(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    fun toScreenX(ix: Float): Float = left + ix * width
    fun toScreenY(iy: Float): Float = top + iy * height
    fun toScreenR(r: Float): Float = r * min(width, height)
    fun fromScreenX(sx: Float): Float = ((sx - left) / width.coerceAtLeast(1f)).coerceIn(0f, 1f)
    fun fromScreenY(sy: Float): Float = ((sy - top) / height.coerceAtLeast(1f)).coerceIn(0f, 1f)
    fun containsScreen(sx: Float, sy: Float): Boolean =
        sx in left..(left + width) && sy in top..(top + height)

    companion object {
        fun fit(boxW: Float, boxH: Float, imgW: Int, imgH: Int): MapViewport {
            val iw = imgW.coerceAtLeast(1).toFloat()
            val ih = imgH.coerceAtLeast(1).toFloat()
            val scale = min(boxW / iw, boxH / ih)
            val cw = iw * scale
            val ch = ih * scale
            return MapViewport(
                left = (boxW - cw) * 0.5f,
                top = (boxH - ch) * 0.5f,
                width = cw,
                height = ch,
            )
        }
    }
}

@Composable
fun CustomRoomScreen(
    code: String,
    token: String,
    spaceBell: Boolean,
    onClose: () -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val roomToken = token

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val myId = AccountSession.account.value?.id.orEmpty()

    var layout by remember { mutableStateOf<LuvApiClient.RoomLayout?>(null) }
    var space by remember { mutableStateOf<LuvApiClient.RoomSpaceInfo?>(null) }
    var roomBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var stoveBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var myX by remember { mutableFloatStateOf(0.5f) }
    var myY by remember { mutableFloatStateOf(0.85f) }
    var seated by remember { mutableStateOf(false) }
    var spawned by remember { mutableStateOf(false) }
    var walking by remember { mutableStateOf(false) }
    var myReaction by remember { mutableStateOf<String?>(null) }
    var lastBellMoveAt by remember { mutableLongStateOf(0L) }
    var reactionExpanded by remember { mutableStateOf(false) }
    var emojiBar by remember { mutableStateOf(ShopCatalog.DEFAULT_BAR) }
    var showZones by remember { mutableStateOf(true) }
    var cookOpen by remember { mutableStateOf(false) }
    var showInventory by remember { mutableStateOf(false) }
    var peerPopup by remember { mutableStateOf<LuvApiClient.RoomSpacePerson?>(null) }
    var peerProfileUserId by remember { mutableStateOf<String?>(null) }
    var peerProfileNick by remember { mutableStateOf("") }

    val zones = layout?.zones.orEmpty()
    val portals = layout?.portals.orEmpty()
    val actions = layout?.actions.orEmpty()
    val avatarR = layout?.avatarR?.takeIf { it > 0f } ?: DEFAULT_AVATAR_R
    val sitZones = remember(zones) { zones.filter { it.isGuestSeat } }

    LaunchedEffect(Unit) {
        emojiBar = withContext(Dispatchers.IO) {
            runCatching { LuvApp.instance.prefs.emojiBar() }
                .getOrDefault(ShopCatalog.DEFAULT_BAR)
        }
    }

    LaunchedEffect(cookOpen) {
        if (!cookOpen) return@LaunchedEffect
        if (stoveBitmap != null) return@LaunchedEffect
        stoveBitmap = withContext(Dispatchers.IO) {
            runCatching {
                val abs = CanvasMemoryKeeper.absoluteImageUrl(STOVE_IMAGE_URL)
                val req = Request.Builder().url(abs).get().build()
                LuvApiClient.httpClient().newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    resp.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
                }
            }.getOrNull()
        }
    }

    LaunchedEffect(code) {
        if (code.isBlank()) return@LaunchedEffect
        // WS für diese Lobby sicher starten (Live-Positions-Push)
        PairConnectionService.startAll(context)
        runCatching { LuvApiClient.fetchRoomSpace(code) }
            .onSuccess { s ->
                space = s
                val lay = s.layout
                if (lay != null) {
                    layout = lay
                    if (!spawned) {
                        myX = lay.spawnX
                        myY = lay.spawnY
                        spawned = true
                        seated = s.people.find { it.userId == myId || it.isMe }?.seatedSeatId != null
                    }
                } else if (!s.customRoomId.isNullOrBlank()) {
                    runCatching { LuvApiClient.fetchRoomLayout(s.customRoomId) }
                        .onSuccess { lay2 ->
                            layout = lay2
                            if (!spawned) {
                                myX = lay2.spawnX
                                myY = lay2.spawnY
                                spawned = true
                            }
                        }
                }
                lastBellMoveAt = s.lastMoveAt
            }
        // Fallback-Poll (selten) — Live kommt über WS space_pos
        while (true) {
            delay(4000)
            runCatching { LuvApiClient.fetchRoomSpace(code) }
                .onSuccess { s ->
                    if (spaceBell &&
                        s.lastMoveAt > 0L &&
                        s.lastMoveAt != lastBellMoveAt &&
                        !s.lastMoveBy.isNullOrBlank() &&
                        s.lastMoveBy != myId
                    ) {
                        vibrateShort(context, 80)
                    }
                    if (s.lastMoveAt > 0L) lastBellMoveAt = s.lastMoveAt
                    // Layout/Zonen aktualisieren, eigene Position NIE vom Poll überschreiben
                    if (s.layout != null) layout = s.layout
                    space = s.copy(
                        people = s.people.map { p ->
                            if (p.userId == myId || p.isMe) {
                                p.copy(x = myX, y = myY, seatedSeatId = if (seated) p.seatedSeatId else null)
                            } else {
                                p
                            }
                        },
                    )
                }
        }
    }

    LaunchedEffect(code, myId) {
        if (code.isBlank()) return@LaunchedEffect
        val want = code.uppercase().removePrefix("LUV-")
        PairConnectionService.events.collect { ev ->
            val pos = ev as? PairEvent.LivePos ?: return@collect
            if (pos.kind != "space_pos") return@collect
            val rc = pos.roomCode.uppercase().removePrefix("LUV-")
            if (rc != want) return@collect
            if (pos.userId == myId) return@collect
            val cur = space ?: return@collect
            val others = cur.people.toMutableList()
            val idx = others.indexOfFirst { it.userId == pos.userId }
            if (idx >= 0) {
                others[idx] = others[idx].copy(x = pos.x, y = pos.y)
            } else {
                others.add(
                    LuvApiClient.RoomSpacePerson(
                        userId = pos.userId,
                        nickname = "…",
                        petEmoji = "🙂",
                        x = pos.x,
                        y = pos.y,
                        layoutId = pos.layoutId,
                        isMe = false,
                    )
                )
            }
            space = cur.copy(people = others, lastMoveAt = System.currentTimeMillis(), lastMoveBy = pos.userId)
            if (spaceBell) vibrateShort(context, 40)
        }
    }

    LaunchedEffect(layout?.imageUrl, layout?.updatedAt) {
        val url = layout?.imageUrl?.trim().orEmpty()
        if (url.isBlank()) {
            roomBitmap = null
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val abs = CanvasMemoryKeeper.absoluteImageUrl(url)
                val req = Request.Builder().url(abs).get().build()
                LuvApiClient.httpClient().newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    resp.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
                }
            }.getOrNull()
        }
        roomBitmap = loaded
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1520))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val density = LocalDensity.current
        val boxWpx = with(density) { maxWidth.toPx() }
        val boxHpx = with(density) { maxHeight.toPx() }
        val bmp = roomBitmap
        val viewport = remember(bmp?.width, bmp?.height, boxWpx, boxHpx) {
            if (bmp == null) MapViewport(0f, 0f, boxWpx, boxHpx)
            else MapViewport.fit(boxWpx, boxHpx, bmp.width, bmp.height)
        }

        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = layout?.name ?: "Raum",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        if (showZones && (zones.isNotEmpty() || portals.isNotEmpty() || actions.isNotEmpty())) {
            ZoneOverlay(
                zones = zones,
                portals = portals,
                actions = actions,
                viewport = viewport,
                modifier = Modifier.fillMaxSize(),
            )
            ZoneLabels(
                portals = portals,
                actions = actions,
                viewport = viewport,
                modifier = Modifier.fillMaxSize(),
            )
        }

        val avatarDp = with(density) {
            (viewport.toScreenR(avatarR) * 2f).toDp()
        }.let { s ->
            when {
                s < 22.dp -> 22.dp
                s > 64.dp -> 64.dp
                else -> s
            }
        }

        // Andere Spieler + ich (meine Position nur lokal)
        val others = space?.people.orEmpty().filter { it.userId != myId && !it.isMe }
        others.forEach { p ->
            val sx = viewport.toScreenX(p.x)
            val sy = viewport.toScreenY(p.y)
            val petId = clipItemId(p.petEmoji).ifBlank { "🐣" }
            AvatarBubble(
                petId = petId,
                reaction = p.reaction?.takeIf { p.reactionUntil > System.currentTimeMillis() },
                avatarDp = avatarDp,
                seated = p.seatedSeatId != null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            (sx - avatarDp.toPx() / 2f).roundToInt(),
                            (sy - avatarDp.toPx() / 2f).roundToInt(),
                        )
                    },
            )
        }
        run {
            val sx = viewport.toScreenX(myX)
            val sy = viewport.toScreenY(myY)
            val petId = clipItemId(
                space?.people?.find { it.userId == myId || it.isMe }?.petEmoji
            ).ifBlank { "🐣" }
            AvatarBubble(
                petId = petId,
                reaction = myReaction,
                avatarDp = avatarDp,
                seated = seated,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            (sx - avatarDp.toPx() / 2f).roundToInt(),
                            (sy - avatarDp.toPx() / 2f).roundToInt(),
                        )
                    },
            )
        }

        val viewportRef = rememberUpdatedState(viewport)
        val zonesRef = rememberUpdatedState(zones)
        val portalsRef = rememberUpdatedState(portals)
        val actionsRef = rememberUpdatedState(actions)
        val avatarRRef = rememberUpdatedState(avatarR)
        val sitRef = rememberUpdatedState(sitZones)
        val seatedRef = rememberUpdatedState(seated)
        val spaceRef = rememberUpdatedState(space)
        val overlayBusyRef = rememberUpdatedState(
            cookOpen || showInventory || peerPopup != null || peerProfileUserId != null
        )

        suspend fun applyEnteredSpace(s: LuvApiClient.RoomSpaceInfo) {
            if (s.layout != null) layout = s.layout
            val me = s.people.find { it.userId == myId || it.isMe }
            if (me != null) {
                myX = me.x
                myY = me.y
                seated = me.seatedSeatId != null
            } else {
                val lay = s.layout
                if (lay != null) {
                    myX = lay.spawnX
                    myY = lay.spawnY
                }
                seated = false
            }
            space = s.copy(
                people = s.people.map { p ->
                    if (p.userId == myId || p.isMe) p.copy(x = myX, y = myY) else p
                },
            )
        }

        suspend fun walkTo(tx: Float, ty: Float, zns: List<LuvApiClient.RoomZone>, aR: Float): Boolean {
            val crowd = spaceRef.value?.people.orEmpty()
                .filter { it.userId != myId && !it.isMe }
            val resolved = resolveCrowdTarget(tx, ty, crowd, aR)
            if (resolved == null) {
                Toast.makeText(context, "Zu nah", Toast.LENGTH_SHORT).show()
                return false
            }
            val (goalX, goalY) = resolved
            val path = findPath(zns, myX, myY, goalX, goalY, aR)
            if (path.isEmpty()) return false
            walkAlongPath(
                path,
                { x, y -> myX = x; myY = y },
                { myX },
                { myY },
            )
            val nudged = nudgeAwayFromOthers(myX, myY, crowd, aR)
            myX = nudged.first
            myY = nudged.second
            // Server: Move (+ ggf. Portal-Wechsel in einem Request)
            runCatching { LuvApiClient.spaceMove(code, myX, myY) }
                .onSuccess { result ->
                    val s = result.space
                    if (result.enteredPortal || s.activeLayoutId != null &&
                        s.activeLayoutId != layout?.id
                    ) {
                        applyEnteredSpace(s)
                    } else {
                        space = s.copy(
                            people = s.people.map { p ->
                                if (p.userId == myId || p.isMe) p.copy(x = myX, y = myY) else p
                            },
                        )
                        if (result.actionType == "cook") {
                            cookOpen = true
                        } else {
                            val hitCook = actionsRef.value.firstOrNull {
                                it.actionType.equals("cook", ignoreCase = true) &&
                                    actionContains(it, myX, myY)
                            }
                            if (hitCook != null) cookOpen = true
                        }
                    }
                }
                .onFailure {
                    // Stille Netzwerk-Glitches; nur echte inhaltliche Fehler zeigen
                    val msg = it.message.orEmpty()
                    if (!msg.contains("no_portal", ignoreCase = true) &&
                        !msg.contains("API-Fehler", ignoreCase = true)
                    ) {
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            return true
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(code) {
                    detectTapGestures { offset ->
                        if (walking || overlayBusyRef.value) return@detectTapGestures
                        val vp = viewportRef.value
                        if (!vp.containsScreen(offset.x, offset.y)) return@detectTapGestures
                        val rawX = vp.fromScreenX(offset.x)
                        val rawY = vp.fromScreenY(offset.y)
                        val zns = zonesRef.value
                        val aR = avatarRRef.value
                        val hitR = (aR * 3.5f).coerceAtLeast(0.06f)

                        val hitPeer = spaceRef.value?.people.orEmpty().firstOrNull { p ->
                            (p.userId != myId && !p.isMe) &&
                                hypot(rawX - p.x, rawY - p.y) < hitR
                        }
                        if (hitPeer != null) {
                            peerPopup = hitPeer
                            return@detectTapGestures
                        }

                        if (zns.none { it.isWalk }) {
                            scope.launch {
                                Toast.makeText(
                                    context,
                                    "Kein Laufbereich (grün) gesetzt",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            return@detectTapGestures
                        }

                        if (seatedRef.value) {
                            scope.launch {
                                walking = true
                                runCatching { LuvApiClient.spaceStand(code) }
                                    .onSuccess {
                                        seated = false
                                        space = it
                                    }
                                walkTo(rawX, rawY, zns, aR)
                                walking = false
                            }
                            return@detectTapGestures
                        }

                        val hitSit = sitRef.value.firstOrNull { z ->
                            val taken = spaceRef.value?.people?.any {
                                it.seatedSeatId == z.id && it.userId != myId && !it.isMe
                            } == true
                            !taken && (
                                zoneContains(z, rawX, rawY, 0.05f) ||
                                    hypot(rawX - z.sitX, rawY - z.sitY) < hitR
                                )
                        }
                        if (hitSit != null) {
                            scope.launch {
                                walking = true
                                val approach = nearestWalkablePoint(
                                    zns, hitSit.sitX, hitSit.sitY, aR
                                ) ?: (hitSit.sitX to hitSit.sitY)
                                walkTo(approach.first, approach.second, zns, aR)
                                runCatching { LuvApiClient.spaceSit(code, hitSit.id) }
                                    .onSuccess {
                                        seated = true
                                        myX = hitSit.sitX
                                        myY = hitSit.sitY
                                        space = it
                                    }
                                    .onFailure {
                                        Toast.makeText(
                                            context,
                                            it.message ?: "Sitz belegt",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                walking = false
                            }
                            return@detectTapGestures
                        }

                        // Kochen / Aktionen: Tippen in/nahe der Zone → hinlaufen + öffnen
                        val hitAction = actionsRef.value.firstOrNull { a ->
                            actionContains(a, rawX, rawY) ||
                                (
                                    rawX >= a.x - 0.06f && rawX <= a.x + a.w + 0.06f &&
                                        rawY >= a.y - 0.06f && rawY <= a.y + a.h + 0.06f
                                    )
                        }
                        if (hitAction != null) {
                            scope.launch {
                                walking = true
                                val ax = hitAction.x + hitAction.w / 2f
                                val ay = hitAction.y + hitAction.h / 2f
                                val approach = nearestWalkablePoint(zns, ax, ay, aR)
                                    ?: (ax to ay)
                                walkTo(approach.first, approach.second, zns, aR)
                                if (hitAction.actionType.equals("cook", ignoreCase = true)) {
                                    cookOpen = true
                                }
                                walking = false
                            }
                            return@detectTapGestures
                        }

                        scope.launch {
                            walking = true
                            walkTo(rawX, rawY, zns, aR)
                            walking = false
                        }
                    }
                },
        )

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
                                        runCatching { LuvApiClient.spaceReact(code, emo) }
                                            .onSuccess { space = it }
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
            ) {
                Text(
                    "←",
                    color = Color.White,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(0.10f))
                        .clickable(onClick = onClose)
                        .padding(vertical = 6.dp),
                )
                Text(
                    "Zonen",
                    color = Color.White,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (showZones) Color(0x8843A047) else Color.White.copy(0.10f)
                        )
                        .clickable { showZones = !showZones }
                        .padding(vertical = 10.dp),
                )
                Text(
                    "Inventar",
                    color = Color.White,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (showInventory) Color(0x88AB47BC) else Color.White.copy(0.10f)
                        )
                        .clickable { showInventory = !showInventory }
                        .padding(vertical = 10.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(0.06f)),
                )
            }
        }

        if (showInventory) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.45f))
                    .clickable { showInventory = false },
            )
            EmptyRoomInventoryPanel(
                compact = false,
                onClose = { showInventory = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.62f)
                    .padding(horizontal = 10.dp, vertical = 56.dp)
                    .clickable { },
            )
        }

        peerPopup?.let { peer ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.40f))
                    .clickable { peerPopup = null },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xF21E2430))
                        .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(18.dp))
                        .clickable { }
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        peer.nickname.ifBlank { "Spieler" },
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Profil",
                            color = Color.White,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF42A5F5))
                                .clickable {
                                    peerProfileNick = peer.nickname.ifBlank { "Spieler" }
                                    peerProfileUserId = peer.userId
                                    peerPopup = null
                                }
                                .padding(vertical = 12.dp),
                        )
                        Text(
                            "Handel",
                            color = Color.White.copy(0.35f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(0.08f))
                                .padding(vertical = 12.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Zurück",
                        color = Color.White.copy(0.85f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(0.10f))
                            .clickable { peerPopup = null }
                            .padding(vertical = 11.dp),
                    )
                }
            }
        }

        if (cookOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.55f))
                    .clickable { cookOpen = false },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .fillMaxHeight(0.88f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xF21E2430))
                        .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(20.dp))
                        .clickable { }
                        .padding(12.dp),
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Kochen",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(0.12f))
                                .clickable { cookOpen = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("✕", color = Color.White.copy(0.8f), fontSize = 14.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    val stove = stoveBitmap
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (stove != null) {
                            Image(
                                bitmap = stove.asImageBitmap(),
                                contentDescription = "Herdplatte",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Text("Lädt…", color = Color.White.copy(0.5f), fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    EmptyRoomInventoryPanel(
                        compact = true,
                        onClose = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(168.dp),
                    )
                }
            }
        }

        peerProfileUserId?.let { uid ->
            Box(modifier = Modifier.fillMaxSize()) {
                ProfileCanvasScreen(
                    nickname = peerProfileNick.ifBlank { "Spieler" },
                    colorIndex = PeerPalette.indexFor(peerProfileNick.ifBlank { uid }),
                    editable = false,
                    userId = uid,
                    onClose = { peerProfileUserId = null },
                )
            }
        }
    }
}

@Composable
private fun ZoneOverlay(
    zones: List<LuvApiClient.RoomZone>,
    portals: List<LuvApiClient.RoomPortal>,
    actions: List<LuvApiClient.RoomAction>,
    viewport: MapViewport,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        // Grün unten, Rot darüber (Sperren sichtbar)
        val ordered = zones.sortedBy { z ->
            when (z.color) {
                "green" -> 0
                "red" -> 1
                "blue" -> 2
                "brown" -> 3
                "orange" -> 4
                "yellow" -> 5
                "gold", "pink", "lime", "violet" -> 6
                else -> 7
            }
        }
        for (z in ordered) {
            val fill = when (z.color) {
                "green" -> Color(0x5543A047)
                "red" -> Color(0xAAE53935)
                "blue" -> Color(0x8842A5F5)
                "brown" -> Color(0x998D6E63)
                "orange" -> Color(0x99FF9800)
                "yellow" -> Color(0x99FFD54F)
                "gold" -> Color(0x66D4AF37)
                "pink" -> Color(0x66FF7043)
                "lime" -> Color(0x667CB342)
                "violet" -> Color(0x667E57C2)
                else -> Color(0x55FFFFFF)
            }
            val stroke = when (z.color) {
                "green" -> Color(0xFF43A047)
                "red" -> Color(0xFFE53935)
                "blue" -> Color(0xFF42A5F5)
                "brown" -> Color(0xFF8D6E63)
                "orange" -> Color(0xFFFF9800)
                "yellow" -> Color(0xFFFFD54F)
                "gold" -> Color(0xFFD4AF37)
                "pink" -> Color(0xFFFF7043)
                "lime" -> Color(0xFF7CB342)
                "violet" -> Color(0xFF7E57C2)
                else -> Color.White
            }
            when (z.shape) {
                "circle" -> {
                    val c = Offset(viewport.toScreenX(z.cx), viewport.toScreenY(z.cy))
                    val rad = viewport.toScreenR(z.r).coerceAtLeast(4f)
                    drawCircle(color = fill, radius = rad, center = c)
                    drawCircle(color = stroke, radius = rad, center = c, style = Stroke(width = 3f))
                }
                "poly" -> {
                    if (z.points.size >= 3) {
                        val path = Path().apply {
                            val first = z.points.first()
                            moveTo(viewport.toScreenX(first.first), viewport.toScreenY(first.second))
                            for (i in 1 until z.points.size) {
                                val p = z.points[i]
                                lineTo(viewport.toScreenX(p.first), viewport.toScreenY(p.second))
                            }
                            close()
                        }
                        drawPath(path, color = fill)
                        drawPath(path, color = stroke, style = Stroke(width = 3f))
                    }
                }
                else -> {
                    val left = viewport.toScreenX(z.x)
                    val top = viewport.toScreenY(z.y)
                    val w = z.w * viewport.width
                    val h = z.h * viewport.height
                    drawRect(color = fill, topLeft = Offset(left, top), size = Size(w, h))
                    drawRect(
                        color = stroke,
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        style = Stroke(width = 3f),
                    )
                }
            }
        }
        for (p in portals) {
            val left = viewport.toScreenX(p.x)
            val top = viewport.toScreenY(p.y)
            val w = p.w * viewport.width
            val h = p.h * viewport.height
            drawRect(
                color = Color(0x66AB47BC),
                topLeft = Offset(left, top),
                size = Size(w, h),
            )
            drawRect(
                color = Color(0xFFAB47BC),
                topLeft = Offset(left, top),
                size = Size(w, h),
                style = Stroke(width = 3f),
            )
        }
        for (a in actions) {
            val left = viewport.toScreenX(a.x)
            val top = viewport.toScreenY(a.y)
            val w = a.w * viewport.width
            val h = a.h * viewport.height
            drawRect(
                color = Color(0x6626A69A),
                topLeft = Offset(left, top),
                size = Size(w, h),
            )
            drawRect(
                color = Color(0xFF26A69A),
                topLeft = Offset(left, top),
                size = Size(w, h),
                style = Stroke(width = 3f),
            )
        }
    }
}

@Composable
private fun ZoneLabels(
    portals: List<LuvApiClient.RoomPortal>,
    actions: List<LuvApiClient.RoomAction>,
    viewport: MapViewport,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        portals.forEach { p ->
            val label = p.label.trim().ifBlank { "Portal" }
            val cx = viewport.toScreenX(p.x + p.w * 0.5f)
            val cy = viewport.toScreenY(p.y + p.h * 0.5f)
            Text(
                label,
                color = Color.White,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset((cx - 36.dp.toPx()).roundToInt(), (cy - 8.dp.toPx()).roundToInt())
                    }
                    .width(72.dp)
                    .background(Color(0x99000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        actions.forEach { a ->
            val label = a.label.trim().ifBlank {
                when {
                    a.actionType.equals("cook", ignoreCase = true) -> "Kochen"
                    else -> a.actionType.ifBlank { "Aktion" }
                }
            }
            val cx = viewport.toScreenX(a.x + a.w * 0.5f)
            val cy = viewport.toScreenY(a.y + a.h * 0.5f)
            Text(
                label,
                color = Color.White,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset((cx - 36.dp.toPx()).roundToInt(), (cy - 8.dp.toPx()).roundToInt())
                    }
                    .width(72.dp)
                    .background(Color(0x99000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun EmptyRoomInventoryPanel(
    compact: Boolean,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Sticker", "Hintergründe", "Begleiter", "Emojis")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(if (compact) 14.dp else 20.dp))
            .background(Color(0xF21E2430))
            .border(1.dp, Color.White.copy(0.10f), RoundedCornerShape(if (compact) 14.dp else 20.dp))
            .padding(if (compact) 10.dp else 14.dp),
    ) {
        if (!compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎒", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Dein Inventar",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (onClose != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(0.12f))
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✕", color = Color.White.copy(0.7f), fontSize = 14.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tabs.forEachIndexed { i, label ->
                val on = tab == i
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (on) Color(0x55E91E8C) else Color.White.copy(0.06f)
                        )
                        .clickable { tab = i }
                        .padding(vertical = if (compact) 6.dp else 9.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = Color.White,
                        fontSize = if (compact) 9.sp else 10.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(if (compact) 8.dp else 10.dp))
        val rows = if (compact) 2 else 3
        val cols = 4
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (compact) Modifier else Modifier.weight(1f)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(cols) {
                        DashedEmptyCell(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                        )
                    }
                }
            }
            if (!compact) {
                Text(
                    "Noch leer",
                    color = Color.White.copy(0.45f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DashedEmptyCell(modifier: Modifier = Modifier) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(0.04f))
            .drawBehind {
                drawRoundRect(
                    color = Color.White.copy(0.22f),
                    style = Stroke(width = 2f, pathEffect = dash),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                )
            },
    )
}

@Composable
private fun AvatarBubble(
    petId: String,
    reaction: String?,
    avatarDp: Dp,
    seated: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(avatarDp)
            .then(
                if (seated) {
                    Modifier
                        .border(2.dp, Color(0xFF42A5F5), CircleShape)
                        .padding(2.dp)
                } else {
                    Modifier
                }
            )
            .clip(CircleShape)
            .background(
                if (isImagePetId(petId)) Color.White.copy(0.92f)
                else Color(0xFF4A3728)
            )
            .border(2.dp, Color.White.copy(0.7f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val glyphSize = (avatarDp.value * 0.68f).sp
        if (reaction != null) {
            ItemGlyph(id = clipItemId(reaction), fontSize = glyphSize)
        } else {
            ItemGlyph(id = petId, fontSize = glyphSize)
        }
    }
}
