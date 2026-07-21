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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.lock.CanvasMemoryKeeper
import com.luv.couple.net.AccountSession
import com.luv.couple.net.LuvApiClient
import com.luv.couple.ui.theme.LuvTheme
import com.luv.couple.ui.theme.TextMuted
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Custom-Lobby-Raum (Vogelperspektive) — Layout + Space-API, kein Paint.
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
    // token reserved for callers / future room-token auth
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

    val zones = layout?.zones.orEmpty()
    val avatarR = layout?.avatarR?.takeIf { it > 0f } ?: DEFAULT_AVATAR_R
    val viewRect = layout?.viewRect ?: LuvApiClient.RoomViewRect()
    val sitZones = remember(zones) { zones.filter { it.isGuestSeat } }

    fun imageToViewX(ix: Float): Float = ((ix - viewRect.x) / viewRect.w).coerceIn(0f, 1f)
    fun imageToViewY(iy: Float): Float = ((iy - viewRect.y) / viewRect.h).coerceIn(0f, 1f)
    fun viewToImageX(vx: Float): Float = viewRect.x + vx * viewRect.w
    fun viewToImageY(vy: Float): Float = viewRect.y + vy * viewRect.h

    LaunchedEffect(code) {
        if (code.isBlank()) return@LaunchedEffect
        // Layout bevorzugt aus Space; Fallback fetchRoomLayout(customRoomId)
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

        val avatarDp = (maxWidth * ((avatarR * 2f) / viewRect.w)).let { s ->
            when {
                s < 22.dp -> 22.dp
                s > 72.dp -> 72.dp
                else -> s
            }
        }

        space?.people?.forEach { p ->
            val ax = if (p.userId == myId || p.isMe) myX else p.x
            val ay = if (p.userId == myId || p.isMe) myY else p.y
            val vx = imageToViewX(ax)
            val vy = imageToViewY(ay)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = maxWidth * vx - avatarDp / 2,
                        top = maxHeight * vy - avatarDp / 2,
                    )
                    .size(avatarDp)
                    .clip(CircleShape)
                    .background(Color(0xFF4A3728))
                    .border(2.dp, Color.White.copy(0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val showReact = when {
                    (p.userId == myId || p.isMe) && myReaction != null -> myReaction
                    p.reaction != null && p.reactionUntil > System.currentTimeMillis() -> p.reaction
                    else -> null
                }
                Text(showReact ?: p.petEmoji, fontSize = (avatarDp.value * 0.5f).sp)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(zones, avatarR, sitZones, seated, code) {
                    detectTapGestures { offset ->
                        val viewX = (offset.x / size.width).coerceIn(0.01f, 0.99f)
                        val viewY = (offset.y / size.height).coerceIn(0.01f, 0.99f)
                        val rawX = viewToImageX(viewX)
                        val rawY = viewToImageY(viewY)
                        val hitR = (avatarR * 1.8f).coerceAtLeast(0.035f)

                        if (seated) {
                            scope.launch {
                                walking = true
                                runCatching { LuvApiClient.spaceStand(code) }
                                    .onSuccess {
                                        space = it
                                        seated = false
                                    }
                                val path = findPath(zones, myX, myY, rawX, rawY, avatarR)
                                if (path.isNotEmpty()) {
                                    walkAlongPath(path, { x, y -> myX = x; myY = y }, { myX }, { myY })
                                    runCatching { LuvApiClient.spaceMove(code, myX, myY) }
                                        .onSuccess { space = it }
                                }
                                walking = false
                            }
                            return@detectTapGestures
                        }

                        val hitSit = sitZones.firstOrNull { z ->
                            val taken = space?.people?.any { it.seatedSeatId == z.id } == true
                            !taken && (
                                zoneContains(z, rawX, rawY, 0.02f) ||
                                    hypot(rawX - z.sitX, rawY - z.sitY) < hitR
                                )
                        }
                        if (hitSit != null) {
                            scope.launch {
                                walking = true
                                val path = findPath(
                                    zones, myX, myY, hitSit.sitX, hitSit.sitY, avatarR
                                )
                                walkAlongPath(path, { x, y -> myX = x; myY = y }, { myX }, { myY })
                                runCatching { LuvApiClient.spaceMove(code, myX, myY) }
                                runCatching { LuvApiClient.spaceSit(code, hitSit.id) }
                                    .onSuccess {
                                        space = it
                                        seated = true
                                        myX = hitSit.sitX
                                        myY = hitSit.sitY
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

                        if (!walkableAt(zones, rawX, rawY, avatarR) && zones.none { it.isWalk }) {
                            return@detectTapGestures
                        }
                        scope.launch {
                            walking = true
                            val path = findPath(zones, myX, myY, rawX, rawY, avatarR)
                            if (path.isNotEmpty()) {
                                walkAlongPath(path, { x, y -> myX = x; myY = y }, { myX }, { myY })
                                runCatching { LuvApiClient.spaceMove(code, myX, myY) }
                                    .onSuccess { space = it }
                            }
                            walking = false
                        }
                    }
                },
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(0.75f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf("❤️", "😍", "🎉", "👏").forEach { emo ->
                Text(
                    emo,
                    fontSize = 28.sp,
                    modifier = Modifier.clickable {
                        scope.launch {
                            runCatching { LuvApiClient.spaceReact(code, emo) }
                                .onSuccess { space = it }
                            myReaction = emo
                            delay(2000)
                            if (myReaction == emo) myReaction = null
                        }
                    },
                )
            }
        }

        Text(
            "✕",
            color = TextMuted,
            fontSize = 18.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clickable(onClick = onClose),
        )
    }
}
