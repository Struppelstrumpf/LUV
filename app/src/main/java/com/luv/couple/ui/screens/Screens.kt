package com.luv.couple.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.data.ConnectionState
import com.luv.couple.data.Lobby
import com.luv.couple.data.PeerPalette
import com.luv.couple.data.Role
import com.luv.couple.data.RoomPreview
import com.luv.couple.net.PairSessionState
import com.luv.couple.net.LobbyReconnectUi
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary

@Composable
private fun ScreenBackdrop(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF121821), BgDeep, Color(0xFF1A1220))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
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
                Text(
                    text = "LUV",
                    style = TextStyle(
                        fontFamily = DisplayFont,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 4.sp
                    )
                )
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
    lobbies: List<Lobby>,
    activeLobbyId: String?,
    lobbyStates: Map<String, ConnectionState>,
    reconnectUi: Map<String, LobbyReconnectUi>,
    partnerNotifyEnabled: Boolean,
    onPartnerNotifyChange: (Boolean) -> Unit,
    partnerHapticEnabled: Boolean,
    onPartnerHapticChange: (Boolean) -> Unit,
    error: String?,
    onOpenLobby: (Lobby) -> Unit,
    onCreateLobby: () -> Unit,
    onJoinLobby: () -> Unit,
    onInviteSeat: (Lobby) -> Unit,
    onBuySeat: (Lobby) -> Unit,
    onRenameLobby: (Lobby) -> Unit,
    onLeaveLobby: (Lobby) -> Unit,
    onReconnect: (Lobby) -> Unit,
    onEditNickname: () -> Unit
) {
    val accent = PeerPalette.composeColor(colorIndex)
    ScreenBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .clickable(onClick = onEditNickname),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        nickname.take(1).uppercase(),
                        color = Color(0xFF1A1F2E),
                        fontFamily = DisplayFont,
                        fontSize = 20.sp
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("LUV", fontFamily = DisplayFont, fontSize = 40.sp, color = TextPrimary, letterSpacing = 2.sp)
                    Text("Hallo, $nickname", color = accent, fontFamily = DisplayFont, fontSize = 18.sp)
                    Text(
                        "$coins Coins · $freeLeft frei heute",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 13.sp
                    )
                }
            }

            if (!error.isNullOrBlank()) {
                Text(error, color = AccentRose, fontFamily = BodyFont, fontSize = 13.sp)
            }

            lobbies.forEach { lobby ->
                LobbyCard(
                    lobby = lobby,
                    active = lobby.id == activeLobbyId,
                    state = lobbyStates[lobby.id] ?: ConnectionState.IDLE,
                    reconnect = reconnectUi[lobby.id],
                    accent = accent,
                    onOpen = { onOpenLobby(lobby) },
                    onInviteSeat = { onInviteSeat(lobby) },
                    onBuySeat = { onBuySeat(lobby) },
                    onRename = { onRenameLobby(lobby) },
                    onLeave = { onLeaveLobby(lobby) },
                    onReconnect = { onReconnect(lobby) }
                )
            }

            if (lobbies.size < PeerPalette.MAX_LOBBIES) {
                PrimaryButton(
                    label = "Neue Lobby hosten",
                    color = accent,
                    onClick = onCreateLobby
                )
                PrimaryButton("Per Code beitreten", BgSoft, onJoinLobby, bordered = true)
            }

            SettingsToggleRow(
                title = "Benachrichtigung beim Malen",
                subtitle = "z. B. Familie: Jane hat gezeichnet!",
                checked = partnerNotifyEnabled,
                accent = accent,
                onCheckedChange = onPartnerNotifyChange
            )
            SettingsToggleRow(
                title = "Vibration beim Malen",
                subtitle = "Leichter Impuls auf der Leinwand",
                checked = partnerHapticEnabled,
                accent = accent,
                onCheckedChange = onPartnerHapticChange
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun LobbyCard(
    lobby: Lobby,
    active: Boolean,
    state: ConnectionState,
    reconnect: LobbyReconnectUi?,
    accent: Color,
    onOpen: () -> Unit,
    onInviteSeat: () -> Unit,
    onBuySeat: () -> Unit,
    onRename: () -> Unit,
    onLeave: () -> Unit,
    onReconnect: () -> Unit
) {
    val peerCount by PairSessionState.peerCount(lobby.id).collectAsStateWithLifecycle()
    val liveCapacity by PairSessionState.capacity(lobby.id).collectAsStateWithLifecycle()
    val peers by PairSessionState.peers(lobby.id).collectAsStateWithLifecycle()
    val capacity = when {
        liveCapacity > 0 -> liveCapacity
        lobby.capacity > 0 -> lobby.capacity
        else -> PeerPalette.FREE_LOBBY_START_CAPACITY
    }
    val occupied = peerCount.coerceAtLeast(1)
    val nicknames = remember(peers, lobby.hostNickname, lobby.role) {
        buildList {
            if (lobby.role == Role.HOST) add("Du")
            else if (lobby.hostNickname.isNotBlank()) add(lobby.hostNickname)
            peers.values
                .map { it.nickname }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
                .forEach { nick ->
                    if (none { it.equals(nick, ignoreCase = true) || (it == "Du" && nick.equals("Du", true)) }) {
                        add(nick)
                    }
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(BgSoft)
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(lobby.name, color = TextPrimary, fontFamily = DisplayFont, fontSize = 22.sp)
                Text(
                    buildString {
                        append(if (lobby.role == Role.HOST) "Du hostest" else "Beigetreten")
                        append(" · $occupied/$capacity")
                        if (lobby.isFree) append(" · gratis")
                    },
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 12.sp
                )
            }
            StatusChip(state)
        }
        if (state == ConnectionState.RECONNECTING || state == ConnectionState.CONNECTING || reconnect != null) {
            ReconnectBanner(reconnect = reconnect, accent = accent, onReconnect = onReconnect)
        }
        PrimaryButton("Leinwand öffnen", accent, onOpen)
        if (lobby.role == Role.HOST) {
            Text(
                "Platz antippen: + lädt ein · gesperrt kostet ${PeerPalette.SLOT_COST} Coins",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 12.sp
            )
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
        }
        PrimaryButton("Umbenennen", BgDeep, onRename, bordered = true)
        Text(
            "Verlassen",
            color = TextMuted,
            fontFamily = BodyFont,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.End)
                .clickable(onClick = onLeave)
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
                        filled -> nicknames.getOrNull(i) ?: "Online"
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
                                else -> onBuy()
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
        reconnect == null -> "Verbindung wird aufgebaut…"
        reconnect.waiting && reconnect.nextRetryInSec > 0 ->
            "Getrennt · Versuch ${reconnect.attempt} · nächster Versuch in ${reconnect.nextRetryInSec}s"
        else -> "Verbinde jetzt erneut…"
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
        if (reconnect != null && reconnect.waiting) {
            Text(
                "Automatik: ${reconnect.backoffSec}s Pause · steigert bis 120s",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 11.sp
            )
        }
        PrimaryButton("Jetzt verbinden", accent, onReconnect)
    }
}

@Composable
fun CreateLobbyScreen(
    error: String?,
    onCreate: (String) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
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
                    "1 kostenlose Lobby pro Tag (max. 1 aktiv). Weitere kosten ${PeerPalette.LOBBY_CREATE_COST} Coins. Max. ${PeerPalette.MAX_PEERS} Personen.",
                    color = TextMuted,
                    fontFamily = BodyFont
                )
                Spacer(modifier = Modifier.height(24.dp))
                SoftField(value = name, onValueChange = { name = it.take(24) }, hint = "Familie")
                if (!error.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(error, color = AccentRose, fontFamily = BodyFont, fontSize = 13.sp)
                }
            }
            PrimaryButton("Lobby erstellen", AccentRose, {
                onCreate(name.trim().ifBlank { "Lobby" })
            })
        }
    }
}

@Composable
fun HostShareScreen(
    lobby: Lobby,
    connectionState: ConnectionState,
    onInviteSeat: () -> Unit,
    onBuySeat: () -> Unit,
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (lobby.isFree) {
                        "Gratis-Lobby: 1 Einladung frei. Weitere Plätze: ${PeerPalette.SLOT_COST} Coins. Tippe auf +"
                    } else {
                        "3 Einladungen inklusive. Weitere Plätze: ${PeerPalette.SLOT_COST} Coins. Tippe auf +"
                    },
                    color = TextMuted,
                    fontFamily = BodyFont
                )
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
                Spacer(modifier = Modifier.height(16.dp))
                StatusChip(connectionState)
            }
            PrimaryButton("Zu meinen Lobbys", AccentRose, onContinue)
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
                    "Link oder Code einfügen — danach siehst du Lobby & Host",
                    color = TextMuted,
                    fontFamily = BodyFont
                )
                Spacer(modifier = Modifier.height(24.dp))
                SoftField(
                    value = code,
                    onValueChange = { code = it },
                    hint = "https://reineke.pro/luv/j/…"
                )
                if (!error.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(error, color = AccentRose, fontFamily = BodyFont, fontSize = 13.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("Weiter", AccentRose, { onPreview(code) })
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
                SoftField(value = name, onValueChange = { name = it.take(24) }, hint = "Familie")
            }
            PrimaryButton("Speichern", AccentRose, {
                if (name.trim().isNotEmpty()) onSave(name.trim())
            })
        }
    }
}

@Composable
private fun SoftField(value: String, onValueChange: (String) -> Unit, hint: String) {
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
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit
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
        ConnectionState.CONNECTED -> "Online" to Color(0xFF3DDC97)
        ConnectionState.HOSTING -> "Wartet" to AccentRose
        ConnectionState.CONNECTING -> "…" to AccentRose
        ConnectionState.RECONNECTING -> "Reconnect…" to AccentRose
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
private fun PrimaryButton(
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
