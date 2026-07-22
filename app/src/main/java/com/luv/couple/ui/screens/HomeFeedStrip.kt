package com.luv.couple.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.LuvApp
import com.luv.couple.net.LuvApiClient
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val EMPTY_SHORT = "Nichts Neues hier"
private const val EMPTY_BODY =
    "Hier erscheinen kurz Nachrichten aus der Community — " +
        "Hochzeiten, Event-Siege, seltene Funde und mehr."
private const val HIDE_STRIP_HINT =
    "Die schmale Nachrichten-Kachel auf dem Home kannst du unter " +
        "Zahnrad → Einstellungen → „Home-Nachrichten“ ausblenden."

@Composable
fun HomeFeedStrip(
    accent: Color,
    enabled: Boolean = true,
    onAction: (LuvApiClient.HomeFeedItem) -> Unit = {}
) {
    val prefs = LuvApp.instance.prefs
    val stripEnabled by prefs.homeFeedStripEnabledFlow.collectAsStateWithLifecycle(initialValue = true)
    if (!enabled || !stripEnabled) return

    var items by remember { mutableStateOf<List<LuvApiClient.HomeFeedItem>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<LuvApiClient.HomeFeedItem?>(null) }
    var showEmptyInfo by remember { mutableStateOf(false) }
    var showHideHint by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            val next = runCatching { LuvApiClient.fetchHomeFeed() }
                .getOrDefault(emptyList())
                .filter { it.expiresAt <= 0L || it.expiresAt > now }
            items = next
            if (index >= next.size) index = 0
            delay(45_000)
        }
    }

    LaunchedEffect(items) {
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(10_000)
            if (items.size <= 1) break
            index = (index + 1) % items.size
        }
    }

    val current = items.getOrNull(index.coerceAtMost((items.size - 1).coerceAtLeast(0)))
    val displayText = current?.shortText ?: EMPTY_SHORT
    val displayKey = current?.id ?: "empty"

    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(shape)
            .background(BgSoft.copy(alpha = 0.92f))
            .border(1.dp, accent.copy(alpha = 0.35f), shape)
            .clickable {
                if (current != null) detail = current
                else showEmptyInfo = true
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        AnimatedContent(
            targetState = displayKey to displayText,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "homeFeedRotate"
        ) { (_, text) ->
            Text(
                text = text,
                color = if (current == null) TextMuted else TextPrimary,
                fontFamily = BodyFont,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showEmptyInfo) {
        AlertDialog(
            onDismissRequest = { showEmptyInfo = false },
            containerColor = BgSoft,
            title = {
                HomeFeedDialogTitle(
                    title = EMPTY_SHORT,
                    accent = accent,
                    onInfoClick = { showHideHint = true }
                )
            },
            text = {
                Text(
                    EMPTY_BODY,
                    color = TextPrimary,
                    fontFamily = BodyFont,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showEmptyInfo = false }) {
                    Text("Schließen", color = TextMuted, fontFamily = BodyFont)
                }
            }
        )
    }

    detail?.let { item ->
        val whenLabel = remember(item.createdAt) {
            if (item.createdAt <= 0L) ""
            else SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.GERMANY)
                .format(Date(item.createdAt))
        }
        val actionLabel = when (item.actionType) {
            "wedding_image" -> "Hochzeitsbild öffnen"
            "contest_image" -> "Bild ansehen"
            "market" -> "Zum Marktplatz"
            "profile" -> "Profil ansehen"
            else -> null
        }
        AlertDialog(
            onDismissRequest = { detail = null },
            containerColor = BgSoft,
            title = {
                HomeFeedDialogTitle(
                    title = item.title,
                    accent = accent,
                    onInfoClick = { showHideHint = true }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (whenLabel.isNotBlank()) {
                        Text(
                            whenLabel,
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        item.body.ifBlank { item.shortText },
                        color = TextPrimary,
                        fontFamily = BodyFont,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (actionLabel != null) {
                        TextButton(
                            onClick = {
                                detail = null
                                onAction(item)
                            }
                        ) {
                            Text(actionLabel, color = accent, fontFamily = BodyFont)
                        }
                    }
                    TextButton(onClick = { detail = null }) {
                        Text("Schließen", color = TextMuted, fontFamily = BodyFont)
                    }
                }
            }
        )
    }

    if (showHideHint) {
        AlertDialog(
            onDismissRequest = { showHideHint = false },
            containerColor = BgSoft,
            title = {
                Text(
                    "Home-Nachrichten",
                    fontFamily = DisplayFont,
                    fontSize = 20.sp,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    HIDE_STRIP_HINT,
                    color = TextPrimary,
                    fontFamily = BodyFont,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showHideHint = false }) {
                    Text("Verstanden", color = accent, fontFamily = BodyFont)
                }
            }
        )
    }
}

@Composable
private fun HomeFeedDialogTitle(
    title: String,
    accent: Color,
    onInfoClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            fontFamily = DisplayFont,
            fontSize = 20.sp,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(1.dp, accent.copy(alpha = 0.55f), CircleShape)
                .clickable(onClick = onInfoClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "i",
                color = accent,
                fontFamily = BodyFont,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
