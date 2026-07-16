package com.luv.couple.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.data.ConnectionState
import com.luv.couple.data.Gender
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.FemalePurple
import com.luv.couple.ui.theme.MaleBlue
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.delay

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
fun GenderScreen(onSelect: (Gender) -> Unit) {
    val appear = remember { Animatable(0f) }
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
                    text = "Wer bist du?",
                    style = TextStyle(
                        fontFamily = DisplayFont,
                        fontSize = 28.sp,
                        color = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Deine Farbe wird zum Sperrbildschirm.",
                    color = TextMuted,
                    fontFamily = BodyFont
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                GenderChoice(
                    title = "Mann",
                    subtitle = "Himmelblau",
                    color = MaleBlue,
                    onClick = { onSelect(Gender.MALE) }
                )
                GenderChoice(
                    title = "Frau",
                    subtitle = "Violett",
                    color = FemalePurple,
                    onClick = { onSelect(Gender.FEMALE) }
                )
            }
        }
    }
}

@Composable
private fun GenderChoice(
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "scale"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(28.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(title, color = Color.White, fontFamily = DisplayFont, fontSize = 28.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontFamily = BodyFont)
        }
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.9f))
        )
    }
}

@Composable
fun RoleScreen(
    gender: Gender,
    onHost: () -> Unit,
    onJoin: () -> Unit,
    onInstallUpdate: () -> Unit = {},
    versionLabel: String = "",
    hostError: String? = null
) {
    val accent = if (gender == Gender.MALE) MaleBlue else FemalePurple
    ScreenBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("LUV", fontFamily = DisplayFont, fontSize = 48.sp, color = accent)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Hosten oder beitreten", fontFamily = DisplayFont, fontSize = 30.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Einer hostet, teilt den kurzen Code per WhatsApp — die LUV-API verbindet euch (auch über Distanz).",
                    color = TextMuted,
                    fontFamily = BodyFont
                )
                if (versionLabel.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(versionLabel, color = TextMuted, fontFamily = BodyFont, fontSize = 12.sp)
                }
                if (!hostError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(hostError, color = AccentRose, fontFamily = BodyFont, fontSize = 13.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PrimaryButton("Hosten", accent, onHost)
                PrimaryButton("Beitreten", BgSoft, onJoin, bordered = true)
                PrimaryButton("Update installieren", BgSoft, onInstallUpdate, bordered = true)
            }
        }
    }
}

@Composable
fun HostScreen(
    code: String,
    connectionState: ConnectionState,
    onShareWhatsApp: () -> Unit,
    onContinue: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(120)
        visible = true
    }
    ScreenBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Dein Code", fontFamily = DisplayFont, fontSize = 34.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Per WhatsApp teilen. Partner tippt den Code bei Beitreten ein.",
                    color = TextMuted,
                    fontFamily = BodyFont
                )
                Spacer(modifier = Modifier.height(24.dp))
                AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { it / 3 }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(BgSoft)
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                            .padding(20.dp)
                    ) {
                        Text(
                            text = code,
                            color = TextPrimary,
                            fontFamily = BodyFont,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                StatusChip(connectionState)
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("Per WhatsApp teilen", Color(0xFF25D366), onShareWhatsApp)
                PrimaryButton("Weiter", AccentRose, onContinue)
            }
        }
    }
}

@Composable
fun JoinScreen(
    error: String?,
    onJoin: (String) -> Unit,
    onBack: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    ScreenBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Beitreten", fontFamily = DisplayFont, fontSize = 34.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Code einfügen, z. B. LUV-AB12CD",
                    color = TextMuted,
                    fontFamily = BodyFont
                )
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(BgSoft)
                        .padding(20.dp)
                ) {
                    BasicTextField(
                        value = code,
                        onValueChange = { code = it },
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontFamily = BodyFont,
                            fontSize = 15.sp
                        ),
                        cursorBrush = SolidColor(AccentRose),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (code.isBlank()) {
                                Text("LUV-AB12CD", color = TextMuted, fontFamily = BodyFont)
                            }
                            inner()
                        }
                    )
                }
                if (!error.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(error, color = AccentRose, fontFamily = BodyFont, fontSize = 13.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("Verbinden", FemalePurple, { onJoin(code) })
                PrimaryButton("Zurück", BgSoft, onBack, bordered = true)
            }
        }
    }
}

@Composable
fun HomeScreen(
    gender: Gender,
    paired: Boolean,
    connectionState: ConnectionState,
    inviteCode: String?,
    versionLabel: String,
    partnerNotifyEnabled: Boolean,
    onPartnerNotifyChange: (Boolean) -> Unit,
    partnerHapticEnabled: Boolean = true,
    onPartnerHapticChange: (Boolean) -> Unit = {},
    onOpenCanvas: () -> Unit,
    onShareCode: () -> Unit,
    onAddWidgetHelp: () -> Unit,
    onInstallUpdate: () -> Unit,
    onDisconnect: () -> Unit
) {
    val accent = if (gender == Gender.MALE) MaleBlue else FemalePurple
    ScreenBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("LUV", fontFamily = DisplayFont, fontSize = 56.sp, color = accent, letterSpacing = 3.sp)
            Text(
                "Sperrbildschirm zu zweit",
                fontFamily = DisplayFont,
                fontSize = 26.sp,
                color = TextPrimary
            )
            Text(
                "Zeichnet weiß auf eurer Farbe. Beim Entsperren wird die Leinwand gelöscht.",
                color = TextMuted,
                fontFamily = BodyFont
            )
            Text(versionLabel, color = TextMuted, fontFamily = BodyFont, fontSize = 12.sp)

            StatusChip(connectionState)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(accent)
                    .clickable(onClick = onOpenCanvas),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Leinwand öffnen",
                    color = Color.White,
                    fontFamily = DisplayFont,
                    fontSize = 22.sp
                )
            }

            Text(
                "Tipp: Füge das Widget „LUV Leinwand“ zum Sperrbildschirm hinzu. Tippen öffnet die Zeichenfläche über dem Lock.",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 13.sp
            )

            SettingsToggleRow(
                title = "Benachrichtigung wenn Partner malt",
                subtitle = "Still, höchstens einmal pro Minute",
                checked = partnerNotifyEnabled,
                accent = accent,
                onCheckedChange = onPartnerNotifyChange
            )
            SettingsToggleRow(
                title = "Vibration wenn Partner malt",
                subtitle = "Leichter Impuls auf der Leinwand",
                checked = partnerHapticEnabled,
                accent = accent,
                onCheckedChange = onPartnerHapticChange
            )

            PrimaryButton("Leinwand jetzt öffnen", accent, onOpenCanvas)
            if (!inviteCode.isNullOrBlank()) {
                PrimaryButton("Code erneut teilen", Color(0xFF25D366), onShareCode)
            }
            PrimaryButton("Update installieren", BgSoft, onInstallUpdate, bordered = true)
            PrimaryButton("Widget-Hilfe", BgSoft, onAddWidgetHelp, bordered = true)
            if (paired) {
                PrimaryButton("Verbindung trennen", Color(0xFF3A2430), onDisconnect)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
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
        ConnectionState.CONNECTED -> "Verbunden" to Color(0xFF3DDC97)
        ConnectionState.HOSTING -> "Hostet · wartet" to MaleBlue
        ConnectionState.CONNECTING -> "Verbindet…" to AccentRose
        ConnectionState.RECONNECTING -> "Stellt wieder her…" to AccentRose
        ConnectionState.IDLE -> "Offline" to TextMuted
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(BgSoft)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, color = TextPrimary, fontFamily = BodyFont, fontSize = 13.sp)
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
