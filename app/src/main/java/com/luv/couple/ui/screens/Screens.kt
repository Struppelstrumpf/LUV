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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.data.ConnectionState
import com.luv.couple.data.Lobby
import com.luv.couple.data.PeerPalette
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
    lobbies: List<Lobby>,
    activeLobbyId: String?,
    lobbyStates: Map<String, ConnectionState>,
    reconnectUi: Map<String, LobbyReconnectUi>,
    versionLabel: String,
    partnerNotifyEnabled: Boolean,
    onPartnerNotifyChange: (Boolean) -> Unit,
    partnerHapticEnabled: Boolean,
    onPartnerHapticChange: (Boolean) -> Unit,
    error: String?,
    onOpenLobby: (Lobby) -> Unit,
    onCreateLobby: () -> Unit,
    onJoinLobby: () -> Unit,
    onShareLobby: (Lobby) -> Unit,
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
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .clickable(onClick = onEditNickname),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        nickname.take(1).uppercase(),
                        color = Color(0xFF1A1F2E),
                        fontFamily = DisplayFont,
                        fontSize = 18.sp
                    )
                }
                Column {
                    Text("LUV", fontFamily = DisplayFont, fontSize = 36.sp, color = TextPrimary, letterSpacing = 2.sp)
                    Text("Hallo, $nickname", color = TextMuted, fontFamily = BodyFont, fontSize = 14.sp)
                }
            }

            Text(
                "Bis zu ${PeerPalette.MAX_LOBBIES} Lobbys · max. ${PeerPalette.MAX_PEERS} Personen",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 13.sp
            )
            if (versionLabel.isNotBlank()) {
                Text(versionLabel, color = TextMuted, fontFamily = BodyFont, fontSize = 12.sp)
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
                    onShare = { onShareLobby(lobby) },
                    onRename = { onRenameLobby(lobby) },
                    onLeave = { onLeaveLobby(lobby) },
                    onReconnect = { onReconnect(lobby) }
                )
            }

            if (lobbies.size < PeerPalette.MAX_LOBBIES) {
                PrimaryButton("Neue Lobby hosten", accent, onCreateLobby)
                PrimaryButton("Per Link beitreten", BgSoft, onJoinLobby, bordered = true)
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
    onShare: () -> Unit,
    onRename: () -> Unit,
    onLeave: () -> Unit,
    onReconnect: () -> Unit
) {
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
                    if (lobby.role.name == "HOST") "Du hostest" else "Beigetreten",
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                PrimaryButton("Link teilen", Color(0xFF25D366), onShare)
            }
            Box(modifier = Modifier.weight(1f)) {
                PrimaryButton("Umbenennen", BgDeep, onRename, bordered = true)
            }
        }
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
                    "Name für Widget & Benachrichtigungen — z. B. Familie",
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
            PrimaryButton("Lobby erstellen & Link teilen", AccentRose, {
                onCreate(name.trim().ifBlank { "Lobby" })
            })
        }
    }
}

@Composable
fun HostShareScreen(
    lobbyName: String,
    joinUrl: String,
    connectionState: ConnectionState,
    onShare: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
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
                Text(lobbyName, fontFamily = DisplayFont, fontSize = 34.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Teile den Link — die App öffnet sich und tritt direkt bei (bis ${PeerPalette.MAX_PEERS} Personen).",
                    color = TextMuted,
                    fontFamily = BodyFont
                )
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(BgSoft)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                        .padding(20.dp)
                ) {
                    Text(
                        text = joinUrl,
                        color = TextPrimary,
                        fontFamily = BodyFont,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                StatusChip(connectionState)
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("Per WhatsApp teilen", Color(0xFF25D366), onShare)
                PrimaryButton("Zu meinen Lobbys", AccentRose, onContinue)
            }
        }
    }
}

@Composable
fun JoinScreen(
    error: String?,
    initialCode: String = "",
    onJoin: (String) -> Unit,
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
                    "Link oder Code einfügen",
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
                PrimaryButton("Verbinden", AccentRose, { onJoin(code) })
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
    bordered: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(color)
            .then(
                if (bordered) Modifier.border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(18.dp))
                else Modifier
            )
            .clickable(onClick = onClick),
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
