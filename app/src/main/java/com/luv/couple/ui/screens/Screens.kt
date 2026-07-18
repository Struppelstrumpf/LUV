package com.luv.couple.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.LuvApp
import com.luv.couple.data.ConnectionState
import com.luv.couple.data.Lobby
import com.luv.couple.data.PeerPalette
import com.luv.couple.data.Role
import com.luv.couple.data.RoomPreview
import com.luv.couple.lock.CanvasStore
import com.luv.couple.net.AccountSession
import com.luv.couple.net.LobbyReconnectUi
import com.luv.couple.net.PairSessionState
import com.luv.couple.update.UpdateUiState
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.LuvWordmark
import com.luv.couple.ui.theme.FemalePurple
import com.luv.couple.ui.theme.MaleBlue
import com.luv.couple.ui.theme.TextMuted
import kotlinx.coroutines.launch
import com.luv.couple.ui.theme.TextPrimary

@Composable
internal fun ScreenBackdrop(
    includeNavigationBars: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF121821), BgDeep, Color(0xFF1A1220))
                )
            )
            .statusBarsPadding()
            .then(if (includeNavigationBars) Modifier.navigationBarsPadding() else Modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentRose.copy(alpha = 0.18f), Color.Transparent),
                        radius = 900f
                    )
                )
        )
        content()
    }
}

@Composable
fun NicknameScreen(
    initial: String = "",
    onContinue: (String) -> Unit,
    onBack: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initial) }
    val appear = remember { Animatable(0f) }
    val previewColor = PeerPalette.composeColor(PeerPalette.indexFor(name.trim().lowercase().ifBlank { "a" }))
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }

    ScreenBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp)
                .alpha(appear.value),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                if (onBack != null) {
                    Text(
                        "Zurück",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onBack)
                            .padding(vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                LuvWordmark(fontSize = 56.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Dein Spitzname",
                    style = TextStyle(
                        fontFamily = DisplayFont,
                        fontSize = 28.sp,
                        color = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "So sehen dich die anderen — und so lautet deine Farbe.",
                    color = TextMuted,
                    fontFamily = BodyFont
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(previewColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            (name.trim().take(1).ifBlank { "?" }).uppercase(),
                            color = Color(0xFF1A1F2E),
                            fontFamily = DisplayFont,
                            fontSize = 18.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(BgSoft)
                            .padding(20.dp)
                    ) {
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it.take(18) },
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontFamily = BodyFont,
                                fontSize = 18.sp
                            ),
                            cursorBrush = SolidColor(AccentRose),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (name.trim().length >= 2) onContinue(name.trim())
                                }
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (name.isBlank()) {
                                    Text("z.B. Jane", color = TextMuted, fontFamily = BodyFont, fontSize = 18.sp)
                                }
                                inner()
                            }
                        )
                    }
                }
                PrimaryButton(
                    "Weiter",
                    AccentRose,
                    onClick = { if (name.trim().length >= 2) onContinue(name.trim()) }
                )
            }
        }
    }
}

@Composable
fun LobbiesScreen(
    nickname: String,
    colorIndex: Int,
    coins: Int,
    freeLeft: Int,
    freeSessionsPerDay: Int = 5,
    canCreateFreeLobby: Boolean = true,
    lobbies: List<Lobby>,
    activeLobbyId: String?,
    lobbyStates: Map<String, ConnectionState>,
    reconnectUi: Map<String, LobbyReconnectUi>,
    error: String?,
    onOpenLobby: (Lobby) -> Unit,
    onCreateLobby: () -> Unit,
    onJoinLobby: () -> Unit,
    onInviteSeat: (Lobby) -> Unit,
    onBuySeat: (Lobby) -> Unit,
    onRenameLobby: (Lobby) -> Unit,
    onLeaveLobby: (Lobby) -> Unit,
    onReconnect: (Lobby) -> Unit,
    onOpenProfile: () -> Unit,
    updateState: UpdateUiState = UpdateUiState.Idle,
    onUpdateApp: () -> Unit = {}
) {
    val accent = PeerPalette.menuAccent()
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val lastPublic by LuvApp.instance.prefs.lastPublicCanvasFlow
        .collectAsStateWithLifecycle(initialValue = null)
    var showReportDialog by remember { mutableStateOf(false) }
    var reportBusy by remember { mutableStateOf(false) }
    var orderedLobbies by remember { mutableStateOf(lobbies) }
    var dragLobbyId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragHoverIndex by remember { mutableIntStateOf(-1) }
    val cardHeights = remember { mutableStateMapOf<String, Int>() }
    val listGapPx = with(density) { 16.dp.toPx() }

    LaunchedEffect(lobbies) {
        // Während Drag lokale Reihenfolge behalten; sonst mit Prefs-Liste mergen
        if (dragLobbyId != null) return@LaunchedEffect
        orderedLobbies = mergeLobbyOrder(orderedLobbies, lobbies)
    }

    fun persistOrder(next: List<Lobby>) {
        orderedLobbies = next
        scope.launch {
            LuvApp.instance.prefs.reorderLobbies(next.map { it.id })
        }
    }

    fun cardH(id: String): Float = (cardHeights[id] ?: 180).toFloat()

    /** Zielindex nur aus Offset berechnen — Liste während Drag nicht umbauen (sonst bricht die Geste). */
    fun previewDropIndex(): Int {
        val id = dragLobbyId ?: return -1
        val from = orderedLobbies.indexOfFirst { it.id == id }
        if (from < 0) return -1
        var baseY = 0f
        for (i in 0 until from) {
            baseY += cardH(orderedLobbies[i].id) + listGapPx
        }
        val centerY = baseY + cardH(id) / 2f + dragOffsetY
        var acc = 0f
        orderedLobbies.forEachIndexed { i, lobby ->
            val h = cardH(lobby.id)
            if (centerY < acc + h / 2f) return i
            acc += h + listGapPx
        }
        return orderedLobbies.lastIndex
    }

    fun visualShiftFor(lobbyId: String): Float {
        val dragId = dragLobbyId ?: return 0f
        if (lobbyId == dragId) return dragOffsetY
        val from = orderedLobbies.indexOfFirst { it.id == dragId }
        val to = dragHoverIndex
        val i = orderedLobbies.indexOfFirst { it.id == lobbyId }
        if (from < 0 || to < 0 || i < 0 || from == to) return 0f
        val step = cardH(dragId) + listGapPx
        return when {
            from < to && i in (from + 1)..to -> -step
            from > to && i in to until from -> step
            else -> 0f
        }
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { if (!reportBusy) showReportDialog = false },
            containerColor = BgSoft,
            title = {
                Text(
                    "Melden",
                    fontFamily = DisplayFont,
                    fontSize = 22.sp,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (lastPublic == null) {
                        Text(
                            "Beim App-Start wurde noch kein öffentliches Bild gezeigt — gerade gibt es nichts zu melden.",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    } else {
                        Text(
                            "Du meldest das letzte öffentliche Bild, das du beim Start gesehen hast:",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        Text(
                            lastPublic!!.nameLine,
                            color = TextPrimary,
                            fontFamily = DisplayFont,
                            fontSize = 18.sp
                        )
                        Text(
                            "Von anderen veröffentlicht · wird im Admin-Bereich geprüft.",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = lastPublic ?: return@TextButton
                        if (reportBusy) return@TextButton
                        reportBusy = true
                        scope.launch {
                            runCatching {
                                com.luv.couple.net.LuvApiClient.reportPublicCanvas(target.id)
                            }.onSuccess {
                                Toast.makeText(context, "Meldung gesendet", Toast.LENGTH_SHORT).show()
                                showReportDialog = false
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "Melden fehlgeschlagen",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            reportBusy = false
                        }
                    },
                    enabled = lastPublic != null && !reportBusy
                ) {
                    Text(
                        if (reportBusy) "…" else "Melden",
                        color = if (lastPublic != null) AccentRose else TextMuted,
                        fontFamily = DisplayFont,
                        fontSize = 15.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showReportDialog = false },
                    enabled = !reportBusy
                ) {
                    Text("Abbrechen", color = TextMuted, fontFamily = BodyFont, fontSize = 15.sp)
                }
            }
        )
    }

    ScreenBackdrop(includeNavigationBars = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(top = 20.dp, bottom = 8.dp)
                .verticalScroll(
                    rememberScrollState(),
                    enabled = dragLobbyId == null
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UpdateBanner(state = updateState, onUpdate = onUpdateApp)
            val equippedPet by LuvApp.instance.prefs.equippedPetFlow
                .collectAsStateWithLifecycle(initialValue = com.luv.couple.shop.ShopCatalog.DEFAULT_PET)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .clickable(onClick = onOpenProfile),
                    contentAlignment = Alignment.Center
                ) {
                    Text(equippedPet, fontSize = 22.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LuvWordmark(fontSize = 40.sp)
                        Text(
                            text = "⚑",
                            fontSize = 18.sp,
                            color = TextMuted,
                            modifier = Modifier
                                .clickable { showReportDialog = true }
                                .padding(4.dp)
                        )
                    }
                    Text("Hallo, $nickname", color = accent, fontFamily = DisplayFont, fontSize = 18.sp)
                    Text(
                        "$coins Coins",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 13.sp
                    )
                }
            }

            if (!error.isNullOrBlank()) {
                Text(error, color = AccentRose, fontFamily = BodyFont, fontSize = 13.sp)
            }

            orderedLobbies.forEach { lobby ->
                key(lobby.id) {
                    val dragging = dragLobbyId == lobby.id
                    LobbyCard(
                        lobby = lobby,
                        active = lobby.id == activeLobbyId,
                        state = lobbyStates[lobby.id] ?: ConnectionState.IDLE,
                        reconnect = reconnectUi[lobby.id],
                        accent = accent,
                        dragging = dragging,
                        dragOffsetY = visualShiftFor(lobby.id),
                        reorderEnabled = orderedLobbies.size > 1,
                        onHeight = { cardHeights[lobby.id] = it },
                        onNameDragStart = {
                            dragLobbyId = lobby.id
                            dragOffsetY = 0f
                            dragHoverIndex = orderedLobbies.indexOfFirst { it.id == lobby.id }
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onNameDrag = { dy ->
                            if (dragLobbyId != lobby.id) return@LobbyCard
                            dragOffsetY += dy
                            val nextHover = previewDropIndex()
                            if (nextHover >= 0 && nextHover != dragHoverIndex) {
                                dragHoverIndex = nextHover
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onNameDragEnd = {
                            if (dragLobbyId == lobby.id) {
                                val from = orderedLobbies.indexOfFirst { it.id == lobby.id }
                                val to = previewDropIndex()
                                if (from >= 0 && to >= 0 && from != to) {
                                    val m = orderedLobbies.toMutableList()
                                    val item = m.removeAt(from)
                                    m.add(to.coerceIn(0, m.size), item)
                                    persistOrder(m)
                                } else {
                                    persistOrder(orderedLobbies)
                                }
                            }
                            dragLobbyId = null
                            dragOffsetY = 0f
                            dragHoverIndex = -1
                        },
                        onNameDragCancel = {
                            dragLobbyId = null
                            dragOffsetY = 0f
                            dragHoverIndex = -1
                            orderedLobbies = mergeLobbyOrder(emptyList(), lobbies)
                        },
                        onOpen = { onOpenLobby(lobby) },
                        onInviteSeat = { onInviteSeat(lobby) },
                        onBuySeat = { onBuySeat(lobby) },
                        onRename = { onRenameLobby(lobby) },
                        onLeave = { onLeaveLobby(lobby) },
                        onReconnect = { onReconnect(lobby) }
                    )
                }
            }

            if (orderedLobbies.size < PeerPalette.MAX_LOBBIES) {
                PrimaryButton(
                    label = if (canCreateFreeLobby) {
                        "Neue Lobby"
                    } else {
                        "Neue Lobby · ${PeerPalette.LOBBY_CREATE_COST} Coins"
                    },
                    color = accent,
                    onClick = onCreateLobby
                )
                PrimaryButton("Beitreten", BgSoft, onJoinLobby, bordered = true)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private fun mergeLobbyOrder(current: List<Lobby>, incoming: List<Lobby>): List<Lobby> {
    if (incoming.isEmpty()) return emptyList()
    if (current.isEmpty()) return incoming
    val byId = incoming.associateBy { it.id }
    val kept = current.mapNotNull { byId[it.id] }
    val known = kept.map { it.id }.toSet()
    return kept + incoming.filter { it.id !in known }
}

@Composable
private fun LobbyCard(
    lobby: Lobby,
    active: Boolean,
    state: ConnectionState,
    reconnect: LobbyReconnectUi?,
    accent: Color,
    dragging: Boolean = false,
    dragOffsetY: Float = 0f,
    reorderEnabled: Boolean = false,
    onHeight: (Int) -> Unit = {},
    onNameDragStart: () -> Unit = {},
    onNameDrag: (Float) -> Unit = {},
    onNameDragEnd: () -> Unit = {},
    onNameDragCancel: () -> Unit = {},
    onOpen: () -> Unit,
    onInviteSeat: () -> Unit,
    onBuySeat: () -> Unit,
    onRename: () -> Unit,
    onLeave: () -> Unit,
    onReconnect: () -> Unit
) {
    val liveCapacity by PairSessionState.capacity(lobby.id).collectAsStateWithLifecycle()
    val peers by PairSessionState.peers(lobby.id).collectAsStateWithLifecycle()
    val proximityMap by LuvApp.instance.prefs.lobbyProximityFlow
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    val proximityOn = proximityMap[lobby.id] == true
    val scope = rememberCoroutineScope()
    val capacity = when {
        liveCapacity > 0 -> liveCapacity
        lobby.capacity > 0 -> lobby.capacity
        else -> PeerPalette.FREE_LOBBY_START_CAPACITY
    }
    var confirmLeave by remember(lobby.id) { mutableStateOf(false) }
    var showProximityDialog by remember(lobby.id) { mutableStateOf(false) }
    val myNickname = AccountSession.account.value?.nickname
        ?: CanvasStore.cachedNickname
        ?: ""
    val myUserId = AccountSession.account.value?.id
    // Immer kompletter Server-Roster — keine Filterung über capacity
    val nicknames = remember(peers, lobby.hostNickname, myNickname, myUserId) {
        PairSessionState.seatNicknames(
            lobbyId = lobby.id,
            myNickname = myNickname,
            myUserId = myUserId,
            hostNickname = lobby.hostNickname
        )
    }
    val occupied = nicknames.size.coerceAtLeast(1)

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = BgSoft,
            title = {
                Text(
                    "Lobby verlassen?",
                    fontFamily = DisplayFont,
                    fontSize = 22.sp,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    "Die anderen bleiben in der Lobby. Ausgegebene Coins (Plätze, Spiele, Leeren …) werden nicht erstattet.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    onLeave()
                }) {
                    Text(
                        "Ja, verlassen",
                        color = Color(0xFFFF5A6A),
                        fontFamily = DisplayFont,
                        fontSize = 15.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) {
                    Text(
                        "Lieber bleiben",
                        color = TextPrimary,
                        fontFamily = BodyFont,
                        fontSize = 15.sp
                    )
                }
            }
        )
    }

    if (showProximityDialog) {
        var draftOn by remember(lobby.id, showProximityDialog) { mutableStateOf(proximityOn) }
        AlertDialog(
            onDismissRequest = { showProximityDialog = false },
            containerColor = BgSoft,
            title = {
                Text(
                    "Lebendige Nähe",
                    fontFamily = DisplayFont,
                    fontSize = 22.sp,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Wenn jemand in dieser Lobby malt, kannst du einen kurzen Impuls bekommen — " +
                            "z. B. Hinweis, Vibration oder Widget. Standardmäßig aus, damit es nicht nervt.",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (draftOn) "Impulse an" else "Impulse aus",
                            color = TextPrimary,
                            fontFamily = BodyFont,
                            fontSize = 15.sp
                        )
                        Switch(
                            checked = draftOn,
                            onCheckedChange = { draftOn = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accent,
                                uncheckedThumbColor = Color.White.copy(alpha = 0.85f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.18f)
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        LuvApp.instance.prefs.setLobbyProximityEnabled(lobby.id, draftOn)
                    }
                    showProximityDialog = false
                }) {
                    Text("Fertig", color = accent, fontFamily = DisplayFont, fontSize = 15.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProximityDialog = false }) {
                    Text("Abbrechen", color = TextMuted, fontFamily = BodyFont, fontSize = 15.sp)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .zIndex(if (dragging) 8f else 0f)
            .graphicsLayer {
                translationY = dragOffsetY
                scaleX = if (dragging) 1.02f else 1f
                scaleY = if (dragging) 1.02f else 1f
                alpha = if (dragging) 0.96f else 1f
            }
            .shadow(
                if (dragging) 16.dp else 0.dp,
                RoundedCornerShape(22.dp),
                clip = false
            )
            .fillMaxWidth()
            .onSizeChanged { onHeight(it.height) }
            .clip(RoundedCornerShape(22.dp))
            .background(BgSoft)
            .border(
                width = if (dragging || active) 1.5.dp else 1.dp,
                color = when {
                    dragging -> accent.copy(alpha = 0.7f)
                    active -> accent.copy(alpha = 0.55f)
                    else -> Color.White.copy(alpha = 0.06f)
                },
                shape = RoundedCornerShape(22.dp)
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AutoShrinkLobbyName(
                    name = lobby.name,
                    modifier = if (reorderEnabled) {
                        Modifier.pointerInput(lobby.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onNameDragStart() },
                                onDragEnd = onNameDragEnd,
                                onDragCancel = onNameDragCancel,
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onNameDrag(dragAmount.y)
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                Text(
                    text = when (lobby.role) {
                        Role.HOST -> "Von dir erstellt"
                        Role.JOIN -> "Beigetreten"
                    },
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (proximityOn) "🔔" else "🔕",
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clickable { showProximityDialog = true }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    color = if (proximityOn) accent else TextMuted
                )
                if (state == ConnectionState.CONNECTED || state == ConnectionState.HOSTING) {
                    StatusChip(state)
                }
            }
        }
        if (
            (state == ConnectionState.RECONNECTING && (reconnect?.attempt ?: 0) >= 2) ||
            (state == ConnectionState.CONNECTING && (reconnect?.attempt ?: 0) >= 2) ||
            (reconnect?.waiting == true && (reconnect.attempt >= 2))
        ) {
            ReconnectBanner(reconnect = reconnect, accent = accent, onReconnect = onReconnect)
        }
        PrimaryButton("Leinwand öffnen", accent, onOpen)
        SeatGrid(
            capacity = capacity,
            maxPeers = PeerPalette.MAX_PEERS,
            occupied = occupied,
            nicknames = nicknames,
            accent = accent,
            canManage = true,
            onInvite = onInviteSeat,
            onBuy = onBuySeat
        )
        if (lobby.role == Role.HOST) {
            PrimaryButton("Umbenennen", BgDeep, onRename, bordered = true)
        }
        Text(
            "Verlassen",
            color = TextMuted,
            fontFamily = BodyFont,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.End)
                .clickable(onClick = { confirmLeave = true })
                .padding(4.dp)
        )
    }
}

@Composable
private fun SeatGrid(
    capacity: Int,
    maxPeers: Int,
    occupied: Int,
    nicknames: List<String>,
    accent: Color,
    canManage: Boolean,
    onInvite: () -> Unit,
    onBuy: () -> Unit
) {
    var confirmBuy by remember { mutableStateOf(false) }
    if (confirmBuy) {
        AlertDialog(
            onDismissRequest = { confirmBuy = false },
            containerColor = BgSoft,
            title = {
                Text(
                    "Platz freischalten?",
                    fontFamily = DisplayFont,
                    fontSize = 22.sp,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    "Kostet ${PeerPalette.SLOT_COST} Coins. Danach kannst du eine weitere Person einladen.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmBuy = false
                    onBuy()
                }) {
                    Text(
                        "Für ${PeerPalette.SLOT_COST} Coins kaufen",
                        color = AccentRose,
                        fontFamily = DisplayFont,
                        fontSize = 15.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBuy = false }) {
                    Text("Abbrechen", color = TextPrimary, fontFamily = BodyFont, fontSize = 15.sp)
                }
            }
        )
    }

    val seats = maxPeers.coerceIn(2, PeerPalette.MAX_PEERS)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (rowStart in 0 until seats step 5) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (i in rowStart until minOf(rowStart + 5, seats)) {
                    val filled = i < occupied
                    val unlocked = i < capacity
                    val label = when {
                        filled -> nicknames.getOrNull(i)?.takeIf { it.isNotBlank() } ?: "…"
                        unlocked -> "+"
                        else -> "${PeerPalette.SLOT_COST}"
                    }
                    SeatTile(
                        label = label,
                        filled = filled,
                        unlocked = unlocked,
                        accent = accent,
                        enabled = canManage && !filled,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when {
                                !canManage || filled -> Unit
                                unlocked -> onInvite()
                                else -> confirmBuy = true
                            }
                        }
                    )
                }
                // Platzhalter, damit letzte Reihe nicht auseinanderzieht
                repeat((5 - (minOf(rowStart + 5, seats) - rowStart)).coerceAtLeast(0)) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SeatTile(
    label: String,
    filled: Boolean,
    unlocked: Boolean,
    accent: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = when {
        filled -> accent.copy(alpha = 0.28f)
        unlocked -> Color(0xFF151A24)
        else -> Color(0xFF10141C)
    }
    val border = when {
        filled -> accent.copy(alpha = 0.55f)
        unlocked -> Color.White.copy(alpha = 0.14f)
        else -> Color.White.copy(alpha = 0.06f)
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = when {
                filled -> TextPrimary
                unlocked -> Color(0xFF25D366)
                else -> TextMuted
            },
            fontFamily = if (unlocked && !filled) DisplayFont else BodyFont,
            fontSize = if (unlocked && !filled) 26.sp else 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReconnectBanner(
    reconnect: LobbyReconnectUi?,
    accent: Color,
    onReconnect: () -> Unit
) {
    val pulse = remember { Animatable(0.55f) }
    LaunchedEffect(reconnect?.waiting, reconnect?.attempt) {
        while (true) {
            pulse.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
            pulse.animateTo(0.55f, tween(700, easing = FastOutSlowInEasing))
        }
    }
    val statusText = when {
        reconnect == null -> "Verbindung zum Server wird aufgebaut…"
        reconnect.waiting && reconnect.nextRetryInSec > 0 ->
            "Kurz offline · neuer Versuch in ${reconnect.nextRetryInSec}s"
        else -> "Verbinde mit dem Server…"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF121722))
            .border(1.dp, accent.copy(alpha = 0.35f * pulse.value), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(statusText, color = TextPrimary, fontFamily = BodyFont, fontSize = 13.sp, lineHeight = 18.sp)
        PrimaryButton("Jetzt verbinden", accent, onReconnect)
    }
}

@Composable
fun CreateLobbyScreen(
    error: String?,
    canCreateFree: Boolean = true,
    lobbyCreateCost: Int = PeerPalette.LOBBY_CREATE_COST,
    onCreate: (name: String, hostColorSide: String) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var hostColorSide by remember { mutableStateOf("blue") }
    ScreenBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Zurück",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .padding(vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Neue Lobby", fontFamily = DisplayFont, fontSize = 34.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (canCreateFree) {
                        "Jetzt gratis · 1 Person einladen inklusive · weitere Plätze je ${PeerPalette.SLOT_COST} Coins"
                    } else {
                        "Kostet $lobbyCreateCost Coins · 3 Einladungen inklusive · weitere Plätze je ${PeerPalette.SLOT_COST} Coins"
                    },
                    color = if (canCreateFree) Color(0xFF3DDC97) else TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                SoftField(
                    value = name,
                    onValueChange = { name = it.take(PeerPalette.MAX_LOBBY_NAME_LENGTH) },
                    hint = "Zusammen",
                    onConfirm = {
                        onCreate(name.trim().ifBlank { "Zusammen" }, hostColorSide)
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text("Deine Farbe", color = TextPrimary, fontFamily = DisplayFont, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Der Nächste bekommt automatisch die andere. Ab der dritten Person gibt’s weitere Farben.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HostColorChoice(
                        label = "Blau",
                        color = MaleBlue,
                        selected = hostColorSide == "blue",
                        onClick = { hostColorSide = "blue" },
                        modifier = Modifier.weight(1f)
                    )
                    HostColorChoice(
                        label = "Lila",
                        color = FemalePurple,
                        selected = hostColorSide == "purple",
                        onClick = { hostColorSide = "purple" },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!error.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(error, color = AccentRose, fontFamily = BodyFont, fontSize = 13.sp)
                }
            }
            PrimaryButton(
                if (canCreateFree) "Lobby erstellen" else "Für $lobbyCreateCost Coins erstellen",
                AccentRose,
                {
                    onCreate(name.trim().ifBlank { "Zusammen" }, hostColorSide)
                }
            )
        }
    }
}

@Composable
private fun HostColorChoice(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) color.copy(alpha = 0.22f) else BgSoft)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) color else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, color = TextPrimary, fontFamily = DisplayFont, fontSize = 16.sp)
    }
}

@Composable
fun InviteLobbyDialog(
    lobby: Lobby,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onShareToFriend: (com.luv.couple.net.LuvApiClient.FriendCard) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showFriends by remember { mutableStateOf(false) }
    var friends by remember {
        mutableStateOf<List<com.luv.couple.net.LuvApiClient.FriendCard>>(emptyList())
    }
    var friendsLoading by remember { mutableStateOf(false) }

    if (showFriends) {
        LaunchedEffect(Unit) {
            friendsLoading = true
            friends = runCatching {
                com.luv.couple.net.LuvApiClient.fetchFriends().friends
            }.getOrDefault(emptyList())
            friendsLoading = false
        }
        AlertDialog(
            onDismissRequest = { showFriends = false },
            containerColor = BgSoft,
            title = {
                Text(
                    "Freunde einladen",
                    fontFamily = DisplayFont,
                    fontSize = 22.sp,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Reihenfolge wie unter Sozial — tippe, um den Link zu teilen.",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    when {
                        friendsLoading -> Text(
                            "Lädt…",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 14.sp
                        )
                        friends.isEmpty() -> Text(
                            "Noch keine Freunde — unter Sozial kannst du welche hinzufügen.",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 14.sp
                        )
                        else -> Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            friends.forEach { card ->
                                val color = Color(
                                    PeerPalette.strokeColor(PeerPalette.indexFor(card.nickname))
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(0.06f))
                                        .clickable {
                                            onShareToFriend(card)
                                            showFriends = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(color),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(card.petEmoji, fontSize = 20.sp)
                                    }
                                    Text(
                                        card.nickname,
                                        color = TextPrimary,
                                        fontFamily = DisplayFont,
                                        fontSize = 16.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "Einladen",
                                        color = AccentRose,
                                        fontFamily = BodyFont,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFriends = false }) {
                    Text("Zurück", color = TextPrimary, fontFamily = BodyFont, fontSize = 15.sp)
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgSoft,
        title = {
            Text(
                "Jemanden einladen",
                fontFamily = DisplayFont,
                fontSize = 22.sp,
                color = TextPrimary
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "QR scannen, Link teilen oder Freunde einladen — dann malt ihr zusammen.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    lobby.name,
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 18.sp
                )
                com.luv.couple.ui.LobbyQrImage(content = lobby.joinUrl, size = 168.dp)
                Text(
                    lobby.joinUrl,
                    color = AccentRose,
                    fontFamily = BodyFont,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Code: ${lobby.invite.ifBlank { "LUV-${lobby.code}" }}",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp
                )
                TextButton(
                    onClick = { showFriends = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Freunde einladen",
                        color = AccentRose,
                        fontFamily = DisplayFont,
                        fontSize = 15.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onShare) {
                Text("Teilen", color = AccentRose, fontFamily = DisplayFont, fontSize = 15.sp)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onOpen) {
                    Text("Öffnen", color = AccentRose, fontFamily = DisplayFont, fontSize = 15.sp)
                }
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(lobby.joinUrl))
                        Toast.makeText(context, "Link kopiert", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Kopieren", color = TextPrimary, fontFamily = BodyFont, fontSize = 15.sp)
                }
            }
        }
    )
}

@Composable
fun HostShareScreen(
    lobby: Lobby,
    onInviteSeat: () -> Unit,
    onBuySeat: () -> Unit,
    onOpen: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    val peerCount by PairSessionState.peerCount(lobby.id).collectAsStateWithLifecycle()
    val liveCapacity by PairSessionState.capacity(lobby.id).collectAsStateWithLifecycle()
    val capacity = when {
        liveCapacity > 0 -> liveCapacity
        lobby.capacity > 0 -> lobby.capacity
        else -> PeerPalette.FREE_LOBBY_START_CAPACITY
    }
    val occupied = peerCount.coerceAtLeast(1)
    ScreenBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Zurück",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .padding(vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(lobby.name, fontFamily = DisplayFont, fontSize = 34.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(20.dp))
                SeatGrid(
                    capacity = capacity,
                    maxPeers = PeerPalette.MAX_PEERS,
                    occupied = occupied,
                    nicknames = listOf("Du"),
                    accent = AccentRose,
                    canManage = true,
                    onInvite = onInviteSeat,
                    onBuy = onBuySeat
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Öffnen", AccentRose, onOpen)
                PrimaryButton("Zu meinen Lobbys", BgSoft, onContinue, bordered = true)
            }
        }
    }
}

@Composable
fun JoinPreviewScreen(
    preview: RoomPreview?,
    loading: Boolean,
    error: String?,
    onJoin: () -> Unit,
    onDecline: () -> Unit
) {
    ScreenBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Ablehnen",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    modifier = Modifier
                        .clickable(onClick = onDecline)
                        .padding(vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text("Einladung", fontFamily = DisplayFont, fontSize = 34.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    loading -> Text("Lobby wird geladen…", color = TextMuted, fontFamily = BodyFont)
                    preview != null -> {
                        Text(
                            preview.name,
                            fontFamily = DisplayFont,
                            fontSize = 28.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Host: ${preview.hostNickname}",
                            color = AccentRose,
                            fontFamily = BodyFont,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "${preview.peers}/${preview.capacity} online · bis ${preview.maxPeers}",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 14.sp
                        )
                    }
                    else -> Text(
                        error ?: "Lobby nicht gefunden.",
                        color = AccentRose,
                        fontFamily = BodyFont
                    )
                }
                if (!error.isNullOrBlank() && preview != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(error, color = AccentRose, fontFamily = BodyFont, fontSize = 13.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(
                    label = "Beitreten",
                    color = AccentRose,
                    onClick = onJoin,
                    enabled = preview != null && !loading
                )
                PrimaryButton("Ablehnen", BgSoft, onDecline, bordered = true)
            }
        }
    }
}

@Composable
fun JoinScreen(
    error: String?,
    initialCode: String = "",
    onPreview: (String) -> Unit,
    onBack: () -> Unit
) {
    var code by remember { mutableStateOf(initialCode) }
    val scanLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = com.journeyapps.barcodescanner.ScanContract()
    ) { result ->
        val raw = result.contents?.trim().orEmpty()
        if (raw.isNotBlank()) onPreview(raw)
    }
    ScreenBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Zurück",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .padding(vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Beitreten", fontFamily = DisplayFont, fontSize = 34.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Link, Code — oder einfach den QR-Code scannen",
                    color = TextMuted,
                    fontFamily = BodyFont
                )
                Spacer(modifier = Modifier.height(24.dp))
                SoftField(
                    value = code,
                    onValueChange = { code = it },
                    hint = "https://reineke.pro/luv/j/…",
                    onConfirm = { onPreview(code) }
                )
                if (!error.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(error, color = AccentRose, fontFamily = BodyFont, fontSize = 13.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("Weiter", AccentRose, { onPreview(code) })
                PrimaryButton(
                    "QR Code scannen",
                    BgSoft,
                    {
                        scanLauncher.launch(
                            com.journeyapps.barcodescanner.ScanOptions()
                                .setDesiredBarcodeFormats(
                                    com.journeyapps.barcodescanner.ScanOptions.QR_CODE
                                )
                                .setPrompt("Lobby-QR scannen")
                                .setBeepEnabled(false)
                                .setOrientationLocked(true)
                        )
                    },
                    bordered = true
                )
                PrimaryButton("Abbrechen", BgSoft, onBack, bordered = true)
            }
        }
    }
}

@Composable
fun RenameLobbyScreen(
    currentName: String,
    onSave: (String) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    ScreenBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Zurück",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .padding(vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Lobby umbenennen", fontFamily = DisplayFont, fontSize = 30.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(24.dp))
                SoftField(
                    value = name,
                    onValueChange = { name = it.take(PeerPalette.MAX_LOBBY_NAME_LENGTH) },
                    hint = "Familie",
                    onConfirm = {
                        if (name.trim().isNotEmpty()) onSave(name.trim())
                    }
                )
            }
            PrimaryButton("Speichern", AccentRose, {
                if (name.trim().isNotEmpty()) onSave(name.trim())
            })
        }
    }
}

@Composable
private fun AutoShrinkLobbyName(
    name: String,
    modifier: Modifier = Modifier
) {
    var fontSize by remember(name) { mutableStateOf(22f) }
    Text(
        text = name,
        color = TextPrimary,
        fontFamily = DisplayFont,
        fontSize = fontSize.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier.fillMaxWidth(),
        onTextLayout = { layout ->
            if (layout.hasVisualOverflow && fontSize > 13f) {
                fontSize = (fontSize - 1.5f).coerceAtLeast(13f)
            }
        }
    )
}

@Composable
private fun SoftField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    onConfirm: (() -> Unit)? = null
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BgSoft)
            .padding(20.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = TextPrimary,
                fontFamily = BodyFont,
                fontSize = 15.sp
            ),
            cursorBrush = SolidColor(AccentRose),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboard?.hide()
                    onConfirm?.invoke()
                }
            ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isBlank()) {
                    Text(hint, color = TextMuted, fontFamily = BodyFont)
                }
                inner()
            }
        )
    }
}

@Composable
internal fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit,
    leading: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BgSoft)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (!leading.isNullOrBlank()) {
            Text(
                leading,
                fontSize = 22.sp,
                modifier = Modifier.padding(end = 10.dp)
            )
        }
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = TextPrimary, fontFamily = BodyFont, fontSize = 14.sp)
            Text(subtitle, color = TextMuted, fontFamily = BodyFont, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = Color(0xFF2A3140)
            )
        )
    }
}

@Composable
private fun StatusChip(state: ConnectionState) {
    val (label, color) = when (state) {
        // Allein in der Lobby = trotzdem verbunden (Server-WS offen)
        ConnectionState.CONNECTED -> "Verbunden" to Color(0xFF3DDC97)
        ConnectionState.HOSTING -> "Verbunden" to Color(0xFF3DDC97)
        ConnectionState.CONNECTING -> "Verbinde…" to AccentRose
        ConnectionState.RECONNECTING -> "Verbinde…" to AccentRose
        ConnectionState.IDLE -> "Offline" to TextMuted
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(BgDeep)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, color = TextPrimary, fontFamily = BodyFont, fontSize = 12.sp)
    }
}

@Composable
internal fun PrimaryButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    bordered: Boolean = false,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) color else color.copy(alpha = 0.35f))
            .then(
                if (bordered) Modifier.border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(18.dp))
                else Modifier
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier.alpha(0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontFamily = DisplayFont,
            fontSize = 17.sp,
            textAlign = TextAlign.Center
        )
    }
}
