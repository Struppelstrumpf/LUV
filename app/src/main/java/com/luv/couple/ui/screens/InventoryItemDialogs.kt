package com.luv.couple.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luv.couple.net.LuvApiClient
import com.luv.couple.profile.ProfileCatalog
import com.luv.couple.shop.LiveShopCatalog
import com.luv.couple.shop.ShopCatalog
import com.luv.couple.ui.CompanionGlyph
import com.luv.couple.ui.ItemGlyph
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.launch

data class InvDetailTarget(
    val kind: String,
    val itemId: String,
    val emoji: String,
    val label: String,
    val count: Int = 1
)

private val InvGold = Color(0xFFE8C547)
private val InvPriceRed = Color(0xFFE53935)
private val InvPriceGreen = Color(0xFF43A047)

private fun invKindLabel(kind: String): String = when (kind) {
    "stickers" -> "Sticker"
    "themes" -> "Hintergrund"
    "pets" -> "Begleiter"
    "emojis" -> "Emoji"
    else -> "Item"
}

private fun invPrimaryActionLabel(kind: String): String = when (kind) {
    "stickers" -> "Platzieren"
    else -> "Ausrüsten"
}

private data class InvShopPresence(
    val inShop: Boolean,
    val priceCoins: Int?,
    val remainingMs: Long?
)

private fun lookupShopPresence(kind: String, itemId: String): InvShopPresence {
    val id = itemId.trim()
    return when (kind) {
        "stickers" -> {
            val hit = LiveShopCatalog.stickers().firstOrNull { it.emoji == id }
            InvShopPresence(hit != null, hit?.priceCoins, hit?.remainingMs)
        }
        "emojis" -> {
            val hit = LiveShopCatalog.emojis().firstOrNull { it.emoji == id }
            InvShopPresence(hit != null, hit?.priceCoins, hit?.remainingMs)
        }
        "pets" -> {
            val hit = LiveShopCatalog.pets().firstOrNull { it.emoji == id }
            InvShopPresence(hit != null, hit?.priceCoins, hit?.remainingMs)
        }
        "themes" -> {
            val hit = LiveShopCatalog.themes().firstOrNull { it.id == id }
            InvShopPresence(hit != null, hit?.priceCoins, hit?.remainingMs)
        }
        else -> InvShopPresence(false, null, null)
    }
}

fun canOfferInventoryItem(kind: String, itemId: String): Boolean {
    val id = itemId.trim()
    if (id.isEmpty()) return false
    return when (kind) {
        "themes" -> id != ProfileCatalog.DEFAULT_THEME_ID
        "emojis" -> id !in ShopCatalog.DEFAULT_BAR
        "pets", "stickers" -> true
        else -> false
    }
}

private fun formatMarketSpan(insight: LuvApiClient.MarketPriceInsight?): String {
    val listings = insight?.listings
    if (listings != null && listings.count > 0) {
        return if (listings.min == listings.max) {
            "${listings.min} 🪙"
        } else {
            "${listings.min}–${listings.max} 🪙"
        }
    }
    val sales = insight?.sales
    if (sales != null && sales.count > 0) {
        return if (sales.min == sales.max) {
            "${sales.min} 🪙 · verkauft"
        } else {
            "${sales.min}–${sales.max} 🪙 · verkauft"
        }
    }
    return "Keine aktuelle Spanne"
}

@Composable
fun InventoryPriceLegendDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF1C2433), BgDeep))
                )
                .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Preis-Farben",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 22.sp
                )
                Text(
                    "Bei der Coin-Sortierung zeigen die Zahlen am Item den relevanten Preis:",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp
                )
                LegendRow(
                    color = InvPriceRed,
                    title = "Rot",
                    body = "Letzter Verkaufspreis auf dem Marktplatz"
                )
                LegendRow(
                    color = InvPriceGreen,
                    title = "Grün",
                    body = "Aktueller Itemshop-Preis (kein Marktverkauf bekannt)"
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Verstanden", color = AccentRose, fontFamily = DisplayFont)
                }
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, title: String, body: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgSoft)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, Color.White.copy(0.25f), CircleShape)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = color, fontFamily = DisplayFont, fontSize = 15.sp)
            Text(body, color = TextMuted, fontFamily = BodyFont, fontSize = 13.sp)
        }
    }
}

@Composable
fun InventoryItemDetailDialog(
    target: InvDetailTarget,
    onDismiss: () -> Unit,
    onPrimaryAction: () -> Unit,
    onOfferMarket: () -> Unit
) {
    var insight by remember(target.kind, target.itemId) {
        mutableStateOf<LuvApiClient.MarketPriceInsight?>(null)
    }
    var loading by remember(target.kind, target.itemId) { mutableStateOf(true) }
    val shop = remember(target.kind, target.itemId, LiveShopCatalog.catalogEpoch) {
        lookupShopPresence(target.kind, target.itemId)
    }
    val offerAllowed = canOfferInventoryItem(target.kind, target.itemId)
    val remaining = formatShopRemainingInfo(shop.remainingMs)

    LaunchedEffect(target.kind, target.itemId) {
        loading = true
        insight = runCatching {
            LuvApiClient.fetchMarketItemPrice(target.kind, target.itemId)
        }.getOrNull()
        loading = false
    }

    val shopPrice = shop.priceCoins ?: insight?.shopPrice

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF222A3A), BgDeep))
                )
                .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(28.dp))
                .padding(horizontal = 18.dp, vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.radialGradient(
                                listOf(AccentRose.copy(0.28f), BgSoft)
                            )
                        )
                        .border(1.dp, AccentRose.copy(0.35f), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (target.kind == "pets") {
                        CompanionGlyph(petId = target.emoji, fontSize = 44.sp)
                    } else {
                        ItemGlyph(id = target.emoji, fontSize = 44.sp)
                    }
                }
                Text(
                    target.label,
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    invKindLabel(target.kind) + if (target.count > 1) " · ×${target.count}" else "",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp
                )

                DetailInfoCard {
                    DetailLine(
                        label = "Itemshop-Preis",
                        value = if (shopPrice != null && shopPrice > 0) {
                            "$shopPrice 🪙"
                        } else {
                            "nicht gelistet"
                        },
                        valueColor = if (shopPrice != null && shopPrice > 0) InvGold else TextMuted
                    )
                    DetailLine(
                        label = "Im Itemshop",
                        value = when {
                            shop.inShop && remaining != null -> "Ja · ${remaining.text}"
                            shop.inShop -> "Ja · gerade verfügbar"
                            else -> "Nein"
                        },
                        valueColor = when {
                            shop.inShop && remaining != null -> remaining.color
                            shop.inShop -> InvPriceGreen
                            else -> TextMuted
                        }
                    )
                    DetailLine(
                        label = "Marktplatz-Spanne",
                        value = if (loading) "lädt…" else formatMarketSpan(insight),
                        valueColor = InvGold
                    )
                }

                if (offerAllowed) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(InvGold.copy(0.14f))
                            .border(1.dp, InvGold.copy(0.4f), RoundedCornerShape(16.dp))
                            .clickable(onClick = onOfferMarket)
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Im Marktplatz anbieten",
                            color = InvGold,
                            fontFamily = DisplayFont,
                            fontSize = 15.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(0.08f))
                            .clickable(onClick = onDismiss)
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Schließen", color = TextMuted, fontFamily = BodyFont)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AccentRose.copy(0.85f))
                            .clickable(onClick = onPrimaryAction)
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            invPrimaryActionLabel(target.kind),
                            color = Color.White,
                            fontFamily = DisplayFont,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailInfoCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BgSoft.copy(0.95f))
            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = { content() }
    )
}

@Composable
private fun DetailLine(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextMuted, fontFamily = BodyFont, fontSize = 13.sp)
        Text(
            value,
            color = valueColor,
            fontFamily = DisplayFont,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
fun InventoryListOnMarketDialog(
    target: InvDetailTarget,
    onDismiss: () -> Unit,
    onListed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var priceText by remember { mutableStateOf("5") }
    var busy by remember { mutableStateOf(false) }
    var insight by remember { mutableStateOf<LuvApiClient.MarketPriceInsight?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(target.kind, target.itemId) {
        loading = true
        val hint = runCatching {
            LuvApiClient.fetchMarketItemPrice(target.kind, target.itemId)
        }.getOrNull()
        insight = hint
        loading = false
        val suggest = when {
            hint?.sales != null -> (hint.sales.min + hint.sales.max) / 2
            hint?.listings != null -> (hint.listings.min + hint.listings.max) / 2
            hint?.shopPrice != null -> hint.shopPrice
            else -> null
        }
        if (suggest != null && suggest > 0) {
            priceText = suggest.coerceIn(1, 10_000).toString()
        }
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF2A2430), Color(0xFF151A24), BgDeep)
                    )
                )
                .border(1.dp, InvGold.copy(0.35f), RoundedCornerShape(28.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 580.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Marktplatz-Angebot",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 24.sp
                )
                Text(
                    "Dein Item wandert aus dem Inventar zu „Meine Angebote“.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(InvGold.copy(0.18f), BgSoft, AccentRose.copy(0.12f))
                            )
                        )
                        .border(1.dp, InvGold.copy(0.4f), RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(BgDeep.copy(0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (target.kind == "pets") {
                            CompanionGlyph(petId = target.emoji, fontSize = 36.sp)
                        } else {
                            ItemGlyph(id = target.emoji, fontSize = 36.sp)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            target.label,
                            color = TextPrimary,
                            fontFamily = DisplayFont,
                            fontSize = 18.sp
                        )
                        Text(
                            "${invKindLabel(target.kind)} · ×${target.count}",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 12.sp
                        )
                    }
                }

                DetailInfoCard {
                    if (loading) {
                        Text("Preise werden geladen…", color = TextMuted, fontFamily = BodyFont)
                    } else {
                        insight?.shopPrice?.let {
                            DetailLine("Itemshop", "$it 🪙", InvGold)
                        }
                        insight?.listings?.let {
                            DetailLine(
                                "Aktuelle Angebote",
                                if (it.min == it.max) "${it.min} 🪙" else "${it.min}–${it.max} 🪙",
                                InvGold
                            )
                        }
                        insight?.sales?.let {
                            DetailLine(
                                "Verkauft · ${insight?.windowDays ?: 90} Tage",
                                if (it.min == it.max) "${it.min} 🪙" else "${it.min}–${it.max} 🪙",
                                TextPrimary
                            )
                        }
                        if (insight == null || insight?.hasAny != true) {
                            Text(
                                "Noch keine Preisdaten — wähle einen fairen Startpreis.",
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Dein Preis", color = TextMuted, fontFamily = BodyFont, fontSize = 12.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(BgSoft)
                            .border(1.dp, InvGold.copy(0.35f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🪙", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        BasicTextField(
                            value = priceText,
                            onValueChange = {
                                priceText = it.filter { ch -> ch.isDigit() }.take(5)
                            },
                            singleLine = true,
                            enabled = !busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontFamily = DisplayFont,
                                fontSize = 22.sp
                            ),
                            cursorBrush = SolidColor(InvGold),
                            modifier = Modifier.weight(1f)
                        )
                        Text("Coins", color = TextMuted, fontFamily = BodyFont, fontSize = 13.sp)
                    }
                }

                Text(
                    "Nur Coins in LUV — Echtgeldhandel ist verboten.",
                    color = TextMuted.copy(0.85f),
                    fontFamily = BodyFont,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(InvGold.copy(0.95f), Color(0xFFF0D78A))
                            )
                        )
                        .clickable(enabled = !busy) {
                            val price = priceText.toIntOrNull() ?: 0
                            if (price < 1) {
                                Toast.makeText(context, "Preis mind. 1 Coin", Toast.LENGTH_SHORT)
                                    .show()
                                return@clickable
                            }
                            busy = true
                            scope.launch {
                                runCatching {
                                    LuvApiClient.listMarketItem(
                                        kind = target.kind,
                                        itemId = target.itemId,
                                        priceCoins = price,
                                        allowTrade = false,
                                        isPrivate = false,
                                        targetUserId = null,
                                        tradeWantKind = null,
                                        tradeWantItemId = null,
                                        tradeWantLabel = null
                                    )
                                }.onSuccess {
                                    Toast.makeText(
                                        context,
                                        "Im Marktplatz unter Meine Angebote",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onListed()
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        it.message ?: "Anbieten fehlgeschlagen",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                busy = false
                            }
                        }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (busy) "Wird eingestellt…" else "In den Marktplatz stellen",
                        color = Color(0xFF2A1F0A),
                        fontFamily = DisplayFont,
                        fontSize = 16.sp
                    )
                }

                TextButton(
                    enabled = !busy,
                    onClick = onDismiss
                ) {
                    Text("Abbrechen", color = TextMuted, fontFamily = BodyFont)
                }
            }
        }
    }
}
