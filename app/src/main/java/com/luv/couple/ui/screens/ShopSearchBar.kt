package com.luv.couple.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary

@Composable
fun ShopSearchIconButton(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (expanded) AccentRose.copy(0.28f) else BgSoft)
            .border(1.dp, Color.White.copy(0.1f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("🔍", fontSize = 18.sp)
    }
}

@Composable
fun ShopSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Suchen… z. B. herz",
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        cursorBrush = SolidColor(AccentRose),
        textStyle = TextStyle(
            color = TextPrimary,
            fontFamily = BodyFont,
            fontSize = 15.sp
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgSoft)
            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { inner ->
            if (query.isEmpty()) {
                Text(placeholder, color = TextMuted, fontFamily = BodyFont, fontSize = 14.sp)
            }
            inner()
        }
    )
}

@Composable
fun ShopSearchToggleRow(
    expanded: Boolean,
    query: String,
    onExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Suchen… z. B. herz"
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShopSearchIconButton(
            expanded = expanded,
            onClick = {
                val next = !expanded
                onExpandedChange(next)
                if (!next) onQueryChange("")
            }
        )
        if (expanded) {
            ShopSearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = placeholder,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f)
            )
        }
    }
}

data class ShopRemainingInfo(
    val text: String,
    val color: Color
)

private val ShopTimerRed = Color(0xFFE53935)
private val ShopTimerOrange = Color(0xFFFF9800)
private val ShopTimerGreen = Color(0xFF43A047)

fun formatShopRemainingInfo(ms: Long?): ShopRemainingInfo? {
    if (ms == null || ms <= 0L) return null
    val minutes = ((ms + 59_999L) / 60_000L).coerceAtLeast(1L)
    val hours = ms / 3_600_000L
    val days = ms / 86_400_000L
    return when {
        ms < 5 * 60_000L -> ShopRemainingInfo("noch $minutes Min", ShopTimerRed)
        ms < 3_600_000L -> ShopRemainingInfo("noch $minutes Min", ShopTimerOrange)
        ms < 86_400_000L -> {
            val label = if (hours <= 1L) "noch 1 Stunde" else "noch $hours Stunden"
            ShopRemainingInfo(label, ShopTimerOrange)
        }
        days <= 1L -> ShopRemainingInfo("noch 1 Tag", ShopTimerGreen)
        else -> ShopRemainingInfo("noch $days Tage", ShopTimerGreen)
    }
}

fun formatShopRemaining(ms: Long?): String? = formatShopRemainingInfo(ms)?.text
