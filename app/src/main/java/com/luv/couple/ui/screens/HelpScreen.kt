package com.luv.couple.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.net.LuvApiClient
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@Composable
fun HelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val trimmed = text.trim()
    val canSend = trimmed.length >= 5 && !busy

    ScreenBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Zurück",
                color = TextMuted,
                fontFamily = BodyFont,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(bottom = 4.dp)
            )
            Text("Hilfe", fontFamily = DisplayFont, fontSize = 34.sp, color = TextPrimary)
            Text(
                "Schreib uns kurz, worum es geht — Bugs, Fragen oder Feedback. Deine Nachricht landet beim Team unter Meldungen.",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(BgSoft)
                    .border(1.dp, AccentRose.copy(0.25f), RoundedCornerShape(18.dp))
                    .padding(14.dp)
            ) {
                if (text.isBlank()) {
                    Text(
                        "Deine Nachricht …",
                        color = TextMuted.copy(0.7f),
                        fontFamily = BodyFont,
                        fontSize = 15.sp
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(800) },
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontFamily = BodyFont,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    ),
                    cursorBrush = SolidColor(AccentRose),
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                "${trimmed.length}/800",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 12.sp
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(if (canSend) AccentRose.copy(0.9f) else TextMuted.copy(0.2f))
                    .clickable(enabled = canSend) {
                        busy = true
                        scope.launch {
                            runCatching { LuvApiClient.submitHelpMessage(trimmed) }
                                .onSuccess {
                                    Toast.makeText(
                                        context,
                                        "Gesendet — danke!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    text = ""
                                    onBack()
                                }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        it.message ?: "Senden fehlgeschlagen",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            busy = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (busy) "Sendet …" else "Nachricht senden",
                    color = if (canSend) TextPrimary else TextMuted,
                    fontFamily = DisplayFont,
                    fontSize = 16.sp
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
