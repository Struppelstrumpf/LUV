package com.luv.couple.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.update.UpdateUiState
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary

@Composable
fun UpdateBanner(
    state: UpdateUiState,
    onUpdate: () -> Unit
) {
    val release = when (state) {
        is UpdateUiState.Available -> state.release
        is UpdateUiState.Downloading -> state.release
        is UpdateUiState.Ready -> state.release
        is UpdateUiState.Error -> state.release
        else -> null
    }
    if (release == null) return

    val title = when (state) {
        is UpdateUiState.Available -> "Neue Version ${release.versionName}"
        is UpdateUiState.Downloading -> "Lädt LUV ${release.versionName}…"
        is UpdateUiState.Ready -> "Bereit: ${release.versionName}"
        is UpdateUiState.Error -> "Update unterbrochen"
        else -> return
    }
    val body = when (state) {
        is UpdateUiState.Available -> release.notes.ifBlank { "Update bereit — Daten bleiben erhalten." }
        is UpdateUiState.Downloading -> "${(state.progress * 100).toInt()}% — bitte kurz warten"
        is UpdateUiState.Ready -> "Tippe, falls der Installer nicht geöffnet hat."
        is UpdateUiState.Error -> state.message
        else -> return
    }
    val action = when (state) {
        is UpdateUiState.Available -> "Jetzt aktualisieren"
        is UpdateUiState.Downloading -> null
        is UpdateUiState.Ready -> "Installation öffnen"
        is UpdateUiState.Error -> "Erneut versuchen"
        else -> null
    }
    val progress = when (state) {
        is UpdateUiState.Downloading -> state.progress
        is UpdateUiState.Ready -> 1f
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF241820))
            .border(1.dp, AccentRose.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = TextPrimary, fontFamily = DisplayFont, fontSize = 18.sp)
        Text(body, color = TextMuted, fontFamily = BodyFont, fontSize = 13.sp, lineHeight = 18.sp)
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = AccentRose,
                trackColor = BgSoft
            )
        }
        if (!action.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AccentRose)
                    .clickable(onClick = onUpdate)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    action,
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
