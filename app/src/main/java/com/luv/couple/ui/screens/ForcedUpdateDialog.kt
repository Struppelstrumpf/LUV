package com.luv.couple.ui.screens

import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.luv.couple.update.AppChangelog
import com.luv.couple.update.UpdateUiState
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary

fun UpdateUiState.requiresForcedUpdate(): Boolean = when (this) {
    is UpdateUiState.Available,
    is UpdateUiState.Downloading,
    is UpdateUiState.Ready -> true
    is UpdateUiState.Error -> release != null
    else -> false
}

@Composable
private fun ApplyDialogBackdropBlur(radiusPx: Int = 72) {
    val view = LocalView.current
    DisposableEffect(radiusPx) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            // Kein schwarzes Dim — weißer Frost liegt im Compose-Scrim
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.setBackgroundBlurRadius(radiusPx)
                runCatching {
                    window.attributes = window.attributes.apply {
                        blurBehindRadius = radiusPx
                    }
                }
            }
        }
        onDispose {
            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { window.setBackgroundBlurRadius(0) }
                runCatching {
                    window.attributes = window.attributes.apply { blurBehindRadius = 0 }
                }
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }
    }
}

@Composable
fun ForcedUpdateDialog(
    state: UpdateUiState,
    onUpdate: () -> Unit
) {
    if (!state.requiresForcedUpdate()) return

    val release = when (state) {
        is UpdateUiState.Available -> state.release
        is UpdateUiState.Downloading -> state.release
        is UpdateUiState.Ready -> state.release
        is UpdateUiState.Error -> state.release
        else -> null
    } ?: return

    val teasers = AppChangelog.teaserLines(release.versionName, maxItems = 3)

    BackHandler(enabled = true) { /* Pflicht-Update — Zurück blockieren */ }

    Dialog(
        onDismissRequest = { /* nicht schließbar */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ApplyDialogBackdropBlur()
        // Weißer Frost: Hintergrund verschwimmt / ist kaum noch lesbar
        val frostAlpha =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.78f else 0.90f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = frostAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 28.dp)
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BgDeep.copy(alpha = 0.96f))
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Zeit für ein Update ♥",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    "LUV ${release.versionName} ist da. Damit Zeichnen, Lobbys und Sync weiter zuverlässig laufen, brauchst du kurz die neue Version.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center
                )
                if (teasers.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(BgSoft.copy(alpha = 0.85f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            "Neu in dieser Version",
                            color = TextPrimary,
                            fontFamily = DisplayFont,
                            fontSize = 13.sp
                        )
                        teasers.forEach { line ->
                            Text(
                                "· $line",
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = 13.sp,
                                lineHeight = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Text(
                    "Daten bleiben erhalten — ohne Update geht’s nicht weiter.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center
                )

                when (state) {
                    is UpdateUiState.Downloading -> {
                        LinearProgressIndicator(
                            progress = { state.progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = AccentRose,
                            trackColor = BgSoft
                        )
                        Text(
                            "Lädt… ${(state.progress * 100).toInt()}%",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 13.sp
                        )
                    }
                    is UpdateUiState.Error -> {
                        Text(
                            state.message,
                            color = AccentRose,
                            fontFamily = BodyFont,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                    else -> Unit
                }

                val actionLabel = when (state) {
                    is UpdateUiState.Downloading -> null
                    is UpdateUiState.Ready -> "Installation öffnen"
                    is UpdateUiState.Error -> "Erneut versuchen"
                    else -> "Jetzt aktualisieren"
                }
                if (actionLabel != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AccentRose)
                            .clickable(onClick = onUpdate)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            actionLabel,
                            color = TextPrimary,
                            fontFamily = DisplayFont,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
