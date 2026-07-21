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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import com.luv.couple.LuvApp
import com.luv.couple.lock.CanvasMemoryKeeper
import com.luv.couple.net.AccountSession
import com.luv.couple.net.LuvApiClient
import com.luv.couple.shop.ShopCatalog
import com.luv.couple.ui.ItemGlyph
import com.luv.couple.ui.clipItemId
import com.luv.couple.ui.isImagePetId
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

private fun cropToViewRect(src: Bitmap, vr: LuvApiClient.RoomViewRect): Bitmap {
    val left = (src.width * vr.x).toInt().coerceIn(0, src.width - 1)
    val top = (src.height * vr.y).toInt().coerceIn(0, src.height - 1)
    val width = (src.width * vr.w).toInt().coerceIn(1, src.width - left)
    val height = (src.height * vr.h).toInt().coerceIn(1, src.height - top)
    return Bitmap.createBitmap(src, left, top, width, height)
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

    val zones = layout?.zones.orEmpty()
    val avatarR = layout?.avatarR?.takeIf { it > 0f } ?: DEFAULT_AVATAR_R
    val viewRect = layout?.viewRect ?: LuvApiClient.RoomViewRect()
    val sitZones = remember(zones) { zones.filter { it.isGuestSeat } }

    fun imageToViewX(ix: Float): Float =
        ((ix - viewRect.x) / viewRect.w.coerceAtLeast(0.01f)).coerceIn(0f, 1f)

    fun imageToViewY(iy: Float): Float =
        ((iy - viewRect.y) / viewRect.h.coerceAtLeast(0.01f)).coerceIn(0f, 1f)

    fun viewToImageX(vx: Float): Float = viewRect.x + vx * viewRect.w
    fun viewToImageY(vy: Float): Float = viewRect.y + vy * viewRect.h

    LaunchedEffect(Unit) {
        emojiBar = withContext(Dispatchers.IO) {
            runCatching { LuvApp.instance.prefs.emojiBar() }
                .getOrDefault(ShopCatalog.DEFAULT_BAR)
        }
    }

    LaunchedEffect(code) {
        if (code.isBlank()) return@LaunchedEffect
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
        while (true) {
            delay(1500)
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

    LaunchedEffect(layout?.imageUrl, layout?.updatedAt, layout?.viewRect) {
        val url = layout?.imageUrl?.trim().orEmpty()
        if (url.isBlank()) {
            roomBitmap = null
            return@LaunchedEffect
        }
        val vr = layout?.viewRect ?: LuvApiClient.RoomViewRect()
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val abs = CanvasMemoryKeeper.absoluteImageUrl(url)
                val req = Request.Builder().url(abs).get().build()
                LuvApiClient.httpClient().newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val full = resp.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
                        ?: return@runCatching null
                    cropToViewRect(full, vr)
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
        val bmp = roomBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = layout?.name ?: "Raum",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        }

        if (showZones && zones.isNotEmpty()) {
            ZoneOverlay(
                zones = zones,
                viewRect = viewRect,
                modifier = Modifier.fillMaxSize(),
            )
        }

        val avatarDp = (maxWidth * (avatarR * 2f / viewRect.w.coerceAtLeast(0.01f))).let { s ->
            when {
                s < 22.dp -> 22.dp
                s > 64.dp -> 64.dp
                else -> s
            }
        }

        // Andere Spieler + ich (meine Position nur lokal)
        val others = space?.people.orEmpty().filter { it.userId != myId && !it.isMe }
        others.forEach { p ->
            val vx = imageToViewX(p.x)
            val vy = imageToViewY(p.y)
            val petId = clipItemId(p.petEmoji).ifBlank { "🐣" }
            AvatarBubble(
                petId = petId,
                reaction = p.reaction?.takeIf { p.reactionUntil > System.currentTimeMillis() },
                avatarDp = avatarDp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = maxWidth * vx - avatarDp / 2,
                        top = maxHeight * vy - avatarDp / 2,
                    ),
            )
        }
        run {
            val vx = imageToViewX(myX)
            val vy = imageToViewY(myY)
            val petId = clipItemId(
                space?.people?.find { it.userId == myId || it.isMe }?.petEmoji
            ).ifBlank { "🐣" }
            AvatarBubble(
                petId = petId,
                reaction = myReaction,
                avatarDp = avatarDp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = maxWidth * vx - avatarDp / 2,
                        top = maxHeight * vy - avatarDp / 2,
                    ),
            )
        }

        val viewRef = rememberUpdatedState(viewRect)
        val zonesRef = rememberUpdatedState(zones)
        val avatarRRef = rememberUpdatedState(avatarR)
        val sitRef = rememberUpdatedState(sitZones)
        val seatedRef = rememberUpdatedState(seated)
        val spaceRef = rememberUpdatedState(space)

        suspend fun walkTo(tx: Float, ty: Float, zns: List<LuvApiClient.RoomZone>, aR: Float): Boolean {
            val path = findPath(zns, myX, myY, tx, ty, aR)
            if (path.isEmpty()) return false
            walkAlongPath(
                path,
                { x, y -> myX = x; myY = y },
                { myX },
                { myY },
            )
            // Server nur informieren — Position lokal behalten
            runCatching { LuvApiClient.spaceMove(code, myX, myY) }
                .onSuccess { s ->
                    space = s.copy(
                        people = s.people.map { p ->
                            if (p.userId == myId || p.isMe) p.copy(x = myX, y = myY) else p
                        },
                    )
                }
            return true
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(code) {
                    detectTapGestures { offset ->
                        if (walking) return@detectTapGestures
                        val w = size.width.coerceAtLeast(1)
                        val h = size.height.coerceAtLeast(1)
                        val vr = viewRef.value
                        val viewX = (offset.x / w).coerceIn(0.005f, 0.995f)
                        val viewY = (offset.y / h).coerceIn(0.005f, 0.995f)
                        val rawX = vr.x + viewX * vr.w
                        val rawY = vr.y + viewY * vr.h
                        val zns = zonesRef.value
                        val aR = avatarRRef.value
                        val hitR = (aR * 3.5f).coerceAtLeast(0.06f)

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
                                        reactionExpanded = false
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
                repeat(2) {
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
        }
    }
}

@Composable
private fun ZoneOverlay(
    zones: List<LuvApiClient.RoomZone>,
    viewRect: LuvApiClient.RoomViewRect,
    modifier: Modifier = Modifier,
) {
    val vrW = viewRect.w.coerceAtLeast(0.01f)
    val vrH = viewRect.h.coerceAtLeast(0.01f)
    Canvas(modifier = modifier) {
        fun toScreenX(ix: Float) = ((ix - viewRect.x) / vrW) * size.width
        fun toScreenY(iy: Float) = ((iy - viewRect.y) / vrH) * size.height
        fun toScreenR(r: Float) = r * min(size.width / vrW, size.height / vrH)

        // Grün zuerst (unten), dann Rot/Blau usw.
        val ordered = zones.sortedBy { z ->
            when (z.color) {
                "green" -> 0
                "red" -> 1
                "blue" -> 2
                "brown" -> 3
                "orange" -> 4
                "yellow" -> 5
                else -> 6
            }
        }
        for (z in ordered) {
            val fill = when (z.color) {
                "green" -> Color(0x6643A047)
                "red" -> Color(0x66E53935)
                "blue" -> Color(0x8842A5F5)
                "brown" -> Color(0x998D6E63)
                "orange" -> Color(0x99FF9800)
                "yellow" -> Color(0x99FFD54F)
                else -> Color(0x55FFFFFF)
            }
            val stroke = when (z.color) {
                "green" -> Color(0xFF43A047)
                "red" -> Color(0xFFE53935)
                "blue" -> Color(0xFF42A5F5)
                "brown" -> Color(0xFF8D6E63)
                "orange" -> Color(0xFFFF9800)
                "yellow" -> Color(0xFFFFD54F)
                else -> Color.White
            }
            if (z.shape == "circle") {
                val c = Offset(toScreenX(z.cx), toScreenY(z.cy))
                val rad = toScreenR(z.r).coerceAtLeast(4f)
                drawCircle(color = fill, radius = rad, center = c)
                drawCircle(color = stroke, radius = rad, center = c, style = Stroke(width = 3f))
            } else {
                val left = toScreenX(z.x)
                val top = toScreenY(z.y)
                val w = (z.w / vrW) * size.width
                val h = (z.h / vrH) * size.height
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
}

@Composable
private fun AvatarBubble(
    petId: String,
    reaction: String?,
    avatarDp: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(avatarDp)
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
