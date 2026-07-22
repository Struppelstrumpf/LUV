package com.luv.couple.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.LuvApp
import com.luv.couple.data.Lobby
import com.luv.couple.data.PeerPalette
import com.luv.couple.data.Role
import com.luv.couple.lock.CanvasStore
import com.luv.couple.lock.DrawingView
import com.luv.couple.lock.LockScreenWidgetProvider
import com.luv.couple.net.AccountSession
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.LuvApiException
import com.luv.couple.net.PairConnectionService
import com.luv.couple.net.PairSessionState
import com.luv.couple.net.RoomSession
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import java.util.UUID
import kotlin.math.min
import kotlinx.coroutines.launch

/**
 * Kompakte Random-Malfläche für den Wartungs-Screen.
 * Toolbar liegt unter der Leinwand (kein Overlap mit Gesture-Leiste).
 */
@Composable
fun MaintenanceRandomDrawPanel(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = LuvApp.instance.prefs
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var lobbyId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Random-Lobby…") }
    var colorIndex by remember {
        mutableIntStateOf(
            CanvasStore.cachedColorIndex.coerceIn(0, PeerPalette.COLOR_COUNT - 1)
        )
    }
    var brushWidth by remember {
        mutableFloatStateOf(with(density) { 18.dp.toPx() }.coerceIn(6f, 40f))
    }
    var eraserOn by remember { mutableStateOf(false) }
    var studioMode by remember { mutableStateOf<BrushStudioMode?>(null) }
    var drawingView by remember { mutableStateOf<DrawingView?>(null) }
    val revision by CanvasStore.revision.collectAsStateWithLifecycle()
    val prevActive = remember { CanvasStore.activeLobbyId.value }

    LaunchedEffect(Unit) {
        status = "Verbinde…"
        val ensured = ensureMaintenanceRandomLobby()
        if (ensured == null) {
            status = "Random-Lobby gerade nicht verfügbar"
            return@LaunchedEffect
        }
        lobbyId = ensured.id
        CanvasStore.setFreeMultiColorLobby(ensured.id, true)
        CanvasStore.setActiveLobby(ensured.id)
        val nick = prefs.snapshot().nickname
            ?: AccountSession.account.value?.nickname
            ?: "Du"
        val col = (prefs.colorIndexForLobby(ensured.code)
            ?: CanvasStore.cachedColorIndex)
            .coerceIn(0, PeerPalette.COLOR_COUNT - 1)
        colorIndex = col
        CanvasStore.updateProfile(nick, col)
        PairSessionState.setCapacity(ensured.id, ensured.capacity)
        PairConnectionService.startAll(context)
        LockScreenWidgetProvider.requestUpdate(context)
        status = "Random · gemeinsam malen"
    }

    DisposableEffect(Unit) {
        onDispose {
            lobbyId?.let { CanvasStore.setFreeMultiColorLobby(it, false) }
            if (!prevActive.isNullOrBlank()) {
                CanvasStore.setActiveLobby(prevActive)
            }
        }
    }

    LaunchedEffect(revision, lobbyId) {
        val id = lobbyId ?: return@LaunchedEffect
        drawingView?.setStrokes(CanvasStore.snapshot(id), animateNew = false)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF151C28))
                    .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(16.dp))
            ) {
                if (lobbyId == null) {
                    Text(
                        status,
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    val id = lobbyId!!
                    AndroidView(
                        factory = { ctx ->
                            DrawingView(ctx).apply {
                                myColorIndex = colorIndex
                                myBrushWidth = brushWidth
                                eraserEnabled = eraserOn
                                canvasBackground = 0xFF151C28.toInt()
                                onStrokeFinished = { pts ->
                                    val short = min(width, height).toFloat().coerceAtLeast(1f)
                                    CanvasStore.addLocalStroke(
                                        points = pts,
                                        width = myBrushWidth,
                                        lobbyId = id,
                                        shortSidePx = short
                                    )
                                    setStrokes(CanvasStore.snapshot(id), animateNew = false)
                                }
                                onDoubleTapUndo = {
                                    CanvasStore.undoLastLocalStroke(id)
                                    setStrokes(CanvasStore.snapshot(id), animateNew = false)
                                }
                                onErasePath = { brush ->
                                    val radius = CanvasStore.eraseRadiusForBrush(myBrushWidth)
                                    if (
                                        CanvasStore.eraseLocalAlong(
                                            brush,
                                            radius = radius,
                                            lobbyId = id,
                                            broadcast = false
                                        )
                                    ) {
                                        setStrokes(CanvasStore.snapshot(id), animateNew = false)
                                    }
                                }
                                onEraseGestureEnd = {
                                    CanvasStore.endEraseSession(id)
                                }
                                drawingView = this
                                setStrokes(CanvasStore.snapshot(id), animateNew = false)
                            }
                        },
                        update = { view ->
                            view.myColorIndex = colorIndex
                            view.myBrushWidth = brushWidth
                            view.eraserEnabled = eraserOn
                            drawingView = view
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Feste Leiste unter der Leinwand — kein Overlap / nicht unter Systemgesten
            MaintenanceDrawToolbar(
                colorIndex = colorIndex,
                eraserOn = eraserOn,
                enabled = lobbyId != null,
                onColor = {
                    eraserOn = false
                    studioMode = BrushStudioMode.COLOR
                },
                onThickness = {
                    eraserOn = false
                    studioMode = BrushStudioMode.THICKNESS
                },
                onEraser = { eraserOn = !eraserOn },
                onTrash = {
                    val id = lobbyId ?: return@MaintenanceDrawToolbar
                    scope.launch {
                        CanvasStore.clear(notifyPeer = true, lobbyId = id)
                        drawingView?.clearCanvas()
                        drawingView?.setStrokes(CanvasStore.snapshot(id), animateNew = false)
                    }
                }
            )
        }

        studioMode?.let { mode ->
            BrushStudioSheet(
                selectedColor = colorIndex,
                takenColors = emptySet(),
                brushWidth = brushWidth,
                mode = mode,
                onColorPick = { idx ->
                    colorIndex = idx
                    CanvasStore.recolorOwnStrokes(idx, lobbyId, broadcast = true)
                    val id = lobbyId
                    scope.launch {
                        if (id != null) {
                            val code = prefs.snapshot().lobbies.firstOrNull { it.id == id }?.code
                            if (!code.isNullOrBlank()) {
                                prefs.setColorIndexForLobby(code, idx)
                            }
                        }
                    }
                    studioMode = null
                },
                onBrushWidthChange = { w -> brushWidth = w },
                onDismiss = { studioMode = null }
            )
        }
    }
}

@Composable
private fun MaintenanceDrawToolbar(
    colorIndex: Int,
    eraserOn: Boolean,
    enabled: Boolean,
    onColor: () -> Unit,
    onThickness: () -> Unit,
    onEraser: () -> Unit,
    onTrash: () -> Unit
) {
    val swatch = Color(PeerPalette.strokeColor(colorIndex))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xE61E2430))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ToolChip(
            label = "Farbe",
            selected = false,
            enabled = enabled,
            onClick = onColor,
            leading = {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(1.dp, Color.White.copy(0.35f), CircleShape)
                )
            }
        )
        ToolChip(label = "Stärke", selected = false, enabled = enabled, onClick = onThickness)
        ToolChip(label = "Radierer", selected = eraserOn, enabled = enabled, onClick = onEraser)
        Spacer(modifier = Modifier.weight(1f))
        ToolChip(label = "Papierkorb", selected = false, enabled = enabled, onClick = onTrash)
    }
}

@Composable
private fun ToolChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    !enabled -> Color.White.copy(0.04f)
                    selected -> AccentRose.copy(0.35f)
                    else -> Color.White.copy(0.08f)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        leading?.invoke()
        Text(label, fontSize = 12.sp, color = TextPrimary, fontFamily = BodyFont)
    }
}

private suspend fun ensureMaintenanceRandomLobby(): Lobby? {
    val prefs = LuvApp.instance.prefs
    val existing = prefs.snapshot().lobbies.firstOrNull { it.isRandom }
    if (existing != null) return existing
    return try {
        upsertRandomLobby(LuvApiClient.randomMatch())
    } catch (e: LuvApiException) {
        if (e.error == "already_in_random") {
            val code = e.roomCode?.uppercase()?.removePrefix("LUV-")
            if (!code.isNullOrBlank()) {
                val room = runCatching { LuvApiClient.joinRoom(code) }.getOrNull()
                if (room != null) return upsertRandomLobby(room)
            }
        }
        null
    } catch (_: Exception) {
        null
    }
}

private suspend fun upsertRandomLobby(room: RoomSession): Lobby {
    val prefs = LuvApp.instance.prefs
    val role = when (room.role?.uppercase()) {
        "JOIN" -> Role.JOIN
        else -> Role.HOST
    }
    room.suggestedColorIndex?.let { prefs.setColorIndexForLobby(room.code, it) }
    val lobby = Lobby(
        id = UUID.randomUUID().toString(),
        name = "Random",
        role = role,
        code = room.code,
        token = room.token,
        invite = room.invite,
        capacity = room.capacity,
        isFree = room.isFree,
        isRandom = true,
        hostNickname = room.hostNickname,
        hostColorSide = room.hostColorSide,
        createdByMe = role == Role.HOST
    )
    prefs.upsertLobby(lobby)
    return lobby
}
