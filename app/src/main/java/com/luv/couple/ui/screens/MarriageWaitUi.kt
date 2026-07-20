package com.luv.couple.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luv.couple.net.AccountSession
import com.luv.couple.net.LuvApiClient
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.launch

private val RefSkipWidth = 360.dp

/**
 * Schönes, skalierendes Popup: Verlobungs-/Hochzeitswartezeit mit Coins überspringen.
 */
@Composable
fun MarriageSkipWaitDialog(
    marriage: LuvApiClient.MarriageInfo,
    onDismiss: () -> Unit,
    onSkipped: (LuvApiClient.MarriageInfo?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val coins = AccountSession.account.value?.coins ?: 0
    val isEngage = marriage.status == "engaged"
    val isWedding = marriage.status == "wedding"
    if (!isEngage && !isWedding) {
        onDismiss()
        return
    }
    val cost = if (isEngage) marriage.engageSkipCost else marriage.weddingSkipCost
    val label = if (isEngage) {
        marriage.engageRemainingLabel ?: "…"
    } else {
        marriage.weddingRemainingLabel ?: "…"
    }
    val need = marriage.weddingStrokesRequired.coerceAtLeast(1)
    val strokesReady = !isWedding || marriage.weddingStrokesReady
    val title = if (isEngage) "7 Tage Verlobung" else "Hochzeitsleinwand"
    val subtitle = if (isEngage) {
        "Noch $label — dann öffnet sich eure gemeinsame Hochzeitsleinwand."
    } else if (!strokesReady) {
        "Zuerst malt jeder mindestens $need Striche. Danach könnt ihr die restliche Zeit " +
            "abwarten oder mit Coins sofort heiraten."
    } else if (cost <= 0) {
        "Striche geschafft und Wartezeit vorbei — ihr könnt jetzt heiraten."
    } else {
        "Noch $label Wartezeit — oder mit Coins sofort heiraten."
    }
    val nextHint = if (isEngage) {
        "Ablauf: 7 Tage warten → gemeinsam malen (je $need Striche) → Ehe. " +
            "Überspringen kostet Coins."
    } else {
        "Ehe geht erst, wenn beide je $need Striche gemalt haben — egal ob ihr wartet oder Coins zahlt."
    }
    val canPay = strokesReady && coins >= cost

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            val scale = (maxWidth / RefSkipWidth).coerceIn(0.72f, 1.15f)
            fun s(dp: Dp): Dp = dp * scale
            fun ts(sp: TextUnit): TextUnit = (sp.value * scale).sp

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(s(28.dp)))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF2A1F28), BgDeep, Color(0xFF1A1420))
                        )
                    )
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                Color(0xFFFFD54F).copy(0.55f),
                                AccentRose.copy(0.4f),
                                Color.White.copy(0.08f)
                            )
                        ),
                        RoundedCornerShape(s(28.dp))
                    )
                    .padding(s(22.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(s(12.dp))
            ) {
                Box(
                    modifier = Modifier
                        .size(s(64.dp))
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0x55FFD54F), Color.Transparent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isEngage) "💝" else "💒", fontSize = ts(34.sp))
                }
                Text(
                    title,
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = ts(22.sp),
                    textAlign = TextAlign.Center
                )
                Text(
                    subtitle,
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = ts(14.sp),
                    textAlign = TextAlign.Center
                )
                if (isWedding) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(s(16.dp)))
                            .background(BgSoft)
                            .padding(s(14.dp))
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(s(6.dp)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Striche (je $need nötig)",
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = ts(12.sp)
                            )
                            Text(
                                "Du ${marriage.weddingMyStrokes.coerceAtMost(need)}/$need  ·  " +
                                    "Partner ${marriage.weddingPartnerStrokes.coerceAtMost(need)}/$need",
                                color = if (strokesReady) Color(0xFFFFD54F) else AccentRose,
                                fontFamily = DisplayFont,
                                fontSize = ts(18.sp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(s(16.dp)))
                        .background(BgSoft)
                        .padding(s(14.dp))
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(s(6.dp)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isEngage) "Restzeit" else "Wartezeit bis zur Ehe",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = ts(12.sp)
                        )
                        Text(
                            if (isWedding && cost <= 0 && strokesReady) "bereit" else label,
                            color = Color(0xFFFFD54F),
                            fontFamily = DisplayFont,
                            fontSize = ts(28.sp)
                        )
                        Text(
                            nextHint,
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = ts(12.sp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("Dein Stand", color = TextMuted, fontFamily = BodyFont, fontSize = ts(13.sp))
                    Spacer(modifier = Modifier.width(s(8.dp)))
                    Text("🪙 $coins", color = TextPrimary, fontFamily = DisplayFont, fontSize = ts(15.sp))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(s(52.dp))
                        .clip(RoundedCornerShape(s(26.dp)))
                        .background(
                            if (canPay && !busy) {
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFFFD54F), AccentRose.copy(0.9f))
                                )
                            } else {
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF3A3A3A), Color(0xFF2A2A2A))
                                )
                            }
                        )
                        .clickable(enabled = canPay && !busy) {
                            busy = true
                            scope.launch {
                                runCatching { LuvApiClient.skipMarriageWait() }
                                    .onSuccess {
                                        Toast.makeText(
                                            context,
                                            if (it.cost > 0) {
                                                "Wartezeit übersprungen (−${it.cost} Coins)"
                                            } else {
                                                "Weiter zum nächsten Schritt"
                                            },
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onSkipped(it.marriage)
                                        onDismiss()
                                    }
                                    .onFailure {
                                        Toast.makeText(
                                            context,
                                            it.message ?: "Überspringen fehlgeschlagen",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        busy = false
                                    }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when {
                            busy -> "…"
                            !strokesReady -> "Erst je $need Striche malen"
                            cost <= 0 -> "Jetzt heiraten"
                            canPay -> "Sofort heiraten · $cost Coins"
                            else -> "Nicht genug Coins ($cost)"
                        },
                        color = if (canPay) Color(0xFF2A1A14) else TextMuted,
                        fontFamily = DisplayFont,
                        fontSize = ts(16.sp)
                    )
                }
                Text(
                    "Abbrechen",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = ts(14.sp),
                    modifier = Modifier
                        .clickable(enabled = !busy, onClick = onDismiss)
                        .padding(s(8.dp))
                )
            }
        }
    }
}
