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
        if (items.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(10_000)
            if (items.isEmpty()) break
            index = (index + 1) % items.size
        }
    }

    val current = items.getOrNull(index.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    if (current == null) return

    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(shape)
            .background(BgSoft.copy(alpha = 0.92f))
            .border(1.dp, accent.copy(alpha = 0.35f), shape)
            .clickable { detail = current }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        AnimatedContent(
            targetState = current.id to current.shortText,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "homeFeedRotate"
        ) { (_, text) ->
            Text(
                text = text,
                color = TextPrimary,
                fontFamily = BodyFont,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
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
                Text(
                    item.title,
                    fontFamily = DisplayFont,
                    fontSize = 20.sp,
                    color = TextPrimary
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
}
