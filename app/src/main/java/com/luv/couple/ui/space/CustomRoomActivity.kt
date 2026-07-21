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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Custom-Lobby-Raum (Vogelperspektive) — Layout + Space-API, kein Paint.
 * Weiß = Karte, Schwarz = Kamera-Fenster mit Edge-Scroll.
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

private const val DEFAULT_AVATAR_R = 0.028f
private const val CAMERA_EDGE = 0.30f

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
    val density = LocalDensity.current
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
    // Kamera in Karten-Lokalcoords (0–1 innerhalb viewRect)
    var camLX by remember { mutableFloatStateOf(0f) }
    var camLY by remember { mutableFloatStateOf(0f) }
    var camInited by remember { mutableStateOf(false) }

    val zones = layout?.zones.orEmpty()
    val avatarR = layout?.avatarR?.takeIf { it > 0f } ?: DEFAULT_AVATAR_R
    val viewRect = layout?.viewRect ?: LuvApiClient.RoomViewRect()
    val cameraRect = layout?.cameraRect ?: LuvApiClient.RoomCameraRect(viewRect.w, viewRect.h)
    val camFracW = (cameraRect.w / viewRect.w.coerceAtLeast(0.01f)).coerceIn(0.12f, 1f)
    val camFracH = (cameraRect.h / viewRect.h.coerceAtLeast(0.01f)).coerceIn(0.12f, 1f)
    val sitZones = remember(zones) { zones.filter { it.isGuestSeat } }

    fun toLocalX(ix: Float): Float =
        ((ix - viewRect.x) / viewRect.w.coerceAtLeast(0.01f)).coerceIn(0f, 1f)

    fun toLocalY(iy: Float): Float =
        ((iy - viewRect.y) / viewRect.h.coerceAtLeast(0.01f)).coerceIn(0f, 1f)

    fun fromLocalX(lx: Float): Float = viewRect.x + lx * viewRect.w
    fun fromLocalY(ly: Float): Float = viewRect.y + ly * viewRect.h

    fun clampCam(nx: Float, ny: Float): Pair<Float, Float> {
        val maxX = (1f - camFracW).coerceAtLeast(0f)
        val maxY = (1f - camFracH).coerceAtLeast(0f)
        return nx.coerceIn(0f, maxX) to ny.coerceIn(0f, maxY)
    }

    fun centerCameraOn(ax: Float, ay: Float) {
        val (nx, ny) = clampCam(toLocalX(ax) - camFracW * 0.5f, toLocalY(ay) - camFracH * 0.5f)
        camLX = nx
        camLY = ny
    }

    /** Kamera folgt nur, wenn Avatar nahe am Bildschirmrand ist. */
    fun applyCameraEdge(ax: Float, ay: Float, smooth: Boolean = true) {
        if (camFracW >= 0.999f && camFracH >= 0.999f) {
            camLX = 0f
            camLY = 0f
            return
        }
        val lx = toLocalX(ax)
        val ly = toLocalY(ay)
        var nx = camLX
        var ny = camLY
        val sx = (lx - camLX) / camFracW
        val sy = (ly - camLY) / camFracH
        if (sx < CAMERA_EDGE) nx -= (CAMERA_EDGE - sx) * camFracW
        if (sx > 1f - CAMERA_EDGE) nx += (sx - (1f - CAMERA_EDGE)) * camFracW
        if (sy < CAMERA_EDGE) ny -= (CAMERA_EDGE - sy) * camFracH
        if (sy > 1f - CAMERA_EDGE) ny += (sy - (1f - CAMERA_EDGE)) * camFracH
        val (tx, ty) = clampCam(nx, ny)
        if (smooth) {
            camLX += (tx - camLX) * 0.42f
            camLY += (ty - camLY) * 0.42f
        } else {
            camLX = tx
            camLY = ty
        }
    }

    fun screenFracX(ix: Float): Float = (toLocalX(ix) - camLX) / camFracW
    fun screenFracY(iy: Float): Float = (toLocalY(iy) - camLY) / camFracH

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
                val me = s.people.find { it.userId == myId || it.isMe }
                if (me != null && !walking) {
                    if (me.seatedSeatId != null) seated = true
                    if (!seated || hypot(me.x - myX, me.y - myY) > 0.08f) {
                        myX = me.x
                        myY = me.y
                    }
                    spawned = true
                }
                lastBellMoveAt = s.lastMoveAt
            }
        while (true) {
            delay(1200)
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
                    space = s
                    if (s.layout != null) layout = s.layout
                    val me = s.people.find { it.userId == myId || it.isMe }
                    if (me != null && !walking) {
                        seated = me.seatedSeatId != null
                        if (!seated && hypot(me.x - myX, me.y - myY) > 0.08f) {
                            myX = me.x
                            myY = me.y
                        } else if (seated) {
                            myX = me.x
                            myY = me.y
                        }
                    }
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

    LaunchedEffect(spawned, layout?.cameraRect, layout?.viewRect, myX, myY) {
        if (!spawned || layout == null) return@LaunchedEffect
        if (!camInited) {
            centerCameraOn(myX, myY)
            camInited = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1520))
            .statusBarsPadding()
            .navigationBarsPadding()
            .clipToBounds()
    ) {
        val worldW = maxWidth / camFracW
        val worldH = maxHeight / camFracH
        val mapOffsetX = with(density) { (-camLX * worldW.toPx()).roundToInt().toDp() }
        val mapOffsetY = with(density) { (-camLY * worldH.toPx()).roundToInt().toDp() }

        val bmp = roomBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = layout?.name ?: "Raum",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .requiredWidth(worldW)
                    .requiredHeight(worldH)
                    .offset(mapOffsetX, mapOffsetY),
                contentScale = ContentScale.FillBounds,
            )
        }

        val avatarDp = (maxWidth * ((avatarR * 2f) / cameraRect.w.coerceAtLeast(0.01f))).let { s ->
            when {
                s < 22.dp -> 22.dp
                s > 72.dp -> 72.dp
                else -> s
            }
        }

        space?.people?.forEach { p ->
            val ax = if (p.userId == myId || p.isMe) myX else p.x
            val ay = if (p.userId == myId || p.isMe) myY else p.y
            val sx = screenFracX(ax)
            val sy = screenFracY(ay)
            // Außerhalb Kamera nicht zeichnen (leicht gepuffert)
            if (sx < -0.15f || sx > 1.15f || sy < -0.15f || sy > 1.15f) return@forEach
            val petId = clipItemId(p.petEmoji).ifBlank { "🐣" }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = maxWidth * sx - avatarDp / 2,
                        top = maxHeight * sy - avatarDp / 2,
                    )
                    .size(avatarDp)
                    .clip(CircleShape)
                    .background(
                        if (isImagePetId(petId)) Color.White.copy(0.92f)
                        else Color(0xFF4A3728)
                    )
                    .border(2.dp, Color.White.copy(0.7f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val showReact = when {
                    (p.userId == myId || p.isMe) && myReaction != null -> myReaction
                    p.reaction != null && p.reactionUntil > System.currentTimeMillis() -> p.reaction
                    else -> null
                }
                val glyphSize = (avatarDp.value * 0.68f).sp
                if (showReact != null) {
                    ItemGlyph(id = clipItemId(showReact), fontSize = glyphSize)
                } else {
                    ItemGlyph(id = petId, fontSize = glyphSize)
                }
            }
        }

        val camLXRef = rememberUpdatedState(camLX)
        val camLYRef = rememberUpdatedState(camLY)
        val camFWRef = rememberUpdatedState(camFracW)
        val camFHRef = rememberUpdatedState(camFracH)
        val viewRef = rememberUpdatedState(viewRect)
        val zonesRef = rememberUpdatedState(zones)
        val avatarRRef = rememberUpdatedState(avatarR)
        val sitRef = rememberUpdatedState(sitZones)
        val seatedRef = rememberUpdatedState(seated)
        val spaceRef = rememberUpdatedState(space)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 64.dp)
                .pointerInput(code) {
                    detectTapGestures { offset ->
                        val vr = viewRef.value
                        val cW = camFWRef.value
                        val cH = camFHRef.value
                        val tapLocalX = camLXRef.value + (offset.x / size.width).coerceIn(0f, 1f) * cW
                        val tapLocalY = camLYRef.value + (offset.y / size.height).coerceIn(0f, 1f) * cH
                        val rawX = vr.x + tapLocalX * vr.w
                        val rawY = vr.y + tapLocalY * vr.h
                        val zns = zonesRef.value
                        val aR = avatarRRef.value
                        val hitR = (aR * 1.8f).coerceAtLeast(0.035f)

                        if (seatedRef.value) {
                            scope.launch {
                                walking = true
                                runCatching { LuvApiClient.spaceStand(code) }
                                    .onSuccess {
                                        space = it
                                        seated = false
                                    }
                                val path = findPath(zns, myX, myY, rawX, rawY, aR)
                                if (path.isNotEmpty()) {
                                    walkAlongPath(
                                        path,
                                        { x, y -> myX = x; myY = y },
                                        { myX },
                                        { myY },
                                        onStep = { applyCameraEdge(myX, myY, smooth = true) },
                                    )
                                    applyCameraEdge(myX, myY, smooth = false)
                                    runCatching { LuvApiClient.spaceMove(code, myX, myY) }
                                        .onSuccess { space = it }
                                }
                                walking = false
                            }
                            return@detectTapGestures
                        }

                        val hitSit = sitRef.value.firstOrNull { z ->
                            val taken = spaceRef.value?.people?.any { it.seatedSeatId == z.id } == true
                            !taken && (
                                zoneContains(z, rawX, rawY, 0.02f) ||
                                    hypot(rawX - z.sitX, rawY - z.sitY) < hitR
                                )
                        }
                        if (hitSit != null) {
                            scope.launch {
                                walking = true
                                val path = findPath(zns, myX, myY, hitSit.sitX, hitSit.sitY, aR)
                                if (path.isNotEmpty()) {
                                    walkAlongPath(
                                        path,
                                        { x, y -> myX = x; myY = y },
                                        { myX },
                                        { myY },
                                        onStep = { applyCameraEdge(myX, myY, smooth = true) },
                                    )
                                    applyCameraEdge(myX, myY, smooth = false)
                                }
                                runCatching { LuvApiClient.spaceMove(code, myX, myY) }
                                runCatching { LuvApiClient.spaceSit(code, hitSit.id) }
                                    .onSuccess {
                                        space = it
                                        seated = true
                                        myX = hitSit.sitX
                                        myY = hitSit.sitY
                                        applyCameraEdge(myX, myY, smooth = false)
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

                        if (!walkableAt(zns, rawX, rawY, aR) && zns.none { it.isWalk }) {
                            return@detectTapGestures
                        }
                        scope.launch {
                            walking = true
                            val path = findPath(zns, myX, myY, rawX, rawY, aR)
                            if (path.isNotEmpty()) {
                                walkAlongPath(
                                    path,
                                    { x, y -> myX = x; myY = y },
                                    { myX },
                                    { myY },
                                    onStep = { applyCameraEdge(myX, myY, smooth = true) },
                                )
                                applyCameraEdge(myX, myY, smooth = false)
                                runCatching { LuvApiClient.spaceMove(code, myX, myY) }
                                    .onSuccess { space = it }
                            }
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
                repeat(3) {
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
