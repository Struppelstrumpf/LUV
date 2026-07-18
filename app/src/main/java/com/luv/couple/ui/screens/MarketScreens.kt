package com.luv.couple.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.LuvApp
import com.luv.couple.net.AccountSession
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.ShopPack
import com.luv.couple.shop.ShopCatalog
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.launch

enum class MarketPanel { Hub, Marketplace, ItemShop, CoinShop }

@Composable
fun MarketScreen(
    shopEnabled: Boolean,
    packs: List<ShopPack>,
    onBuyPack: (ShopPack) -> Unit,
    onRefreshInventory: suspend () -> Unit = {},
    startInCoinShop: Boolean = false,
    onStartInCoinShopConsumed: () -> Unit = {}
) {
    var panel by remember { mutableStateOf(MarketPanel.Hub) }
    LaunchedEffect(startInCoinShop) {
        if (startInCoinShop) {
            panel = MarketPanel.CoinShop
            onStartInCoinShopConsumed()
        }
    }
    when (panel) {
        MarketPanel.Hub -> MarketHub(
            onOpenMarketplace = { panel = MarketPanel.Marketplace },
            onOpenItemShop = { panel = MarketPanel.ItemShop },
            onOpenCoinShop = { panel = MarketPanel.CoinShop }
        )
        MarketPanel.Marketplace -> MarketExpandShell(
            title = "Marktplatz",
            onBack = { panel = MarketPanel.Hub }
        ) {
            EmptyMarketCard(
                title = "Bald hier",
                body = "Der Marktplatz öffnet sich später — tauschen, entdecken, staunen."
            )
        }
        MarketPanel.ItemShop -> MarketExpandShell(
            title = "Itemshop",
            onBack = { panel = MarketPanel.Hub }
        ) {
            ItemShopContent(onRefreshInventory = onRefreshInventory)
        }
        MarketPanel.CoinShop -> MarketExpandShell(
            title = "Coinshop",
            onBack = { panel = MarketPanel.Hub }
        ) {
            CoinShopContent(
                shopEnabled = shopEnabled,
                packs = packs,
                onBuyPack = onBuyPack
            )
        }
    }
}

@Composable
private fun MarketHub(
    onOpenMarketplace: () -> Unit,
    onOpenItemShop: () -> Unit,
    onOpenCoinShop: () -> Unit
) {
    MenuBackdrop(includeNavigationBars = false) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            val gap = 12.dp
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                Text("Markt", fontFamily = DisplayFont, fontSize = 30.sp, color = TextPrimary)
                MarketTile(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    title = "Marktplatz",
                    subtitle = "Entdecken & tauschen",
                    brush = Brush.linearGradient(listOf(Color(0xFF2A3148), Color(0xFF1A2030))),
                    onClick = onOpenMarketplace
                )
                MarketTile(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    title = "Itemshop",
                    subtitle = "Emojis, Pets & mehr",
                    brush = Brush.linearGradient(listOf(Color(0xFF3A2438), Color(0xFF241828))),
                    onClick = onOpenItemShop
                )
                MarketTile(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    title = "Coinshop",
                    subtitle = "Handvoll bis Schatztruhe",
                    brush = Brush.linearGradient(listOf(Color(0xFF3A3020), Color(0xFF241C12))),
                    onClick = onOpenCoinShop
                )
            }
        }
    }
}

@Composable
private fun MarketTile(
    modifier: Modifier,
    title: String,
    subtitle: String,
    brush: Brush,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(brush)
            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = TextPrimary, fontFamily = DisplayFont, fontSize = 26.sp)
            Text(subtitle, color = TextMuted, fontFamily = BodyFont, fontSize = 14.sp)
        }
    }
}

@Composable
private fun MarketExpandShell(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    MenuBackdrop(includeNavigationBars = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Zurück",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onBack)
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(title, color = TextPrimary, fontFamily = DisplayFont, fontSize = 22.sp)
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(56.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun EmptyMarketCard(title: String, body: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(BgSoft)
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, color = TextPrimary, fontFamily = DisplayFont, fontSize = 22.sp)
            Text(
                body,
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CoinShopContent(
    shopEnabled: Boolean,
    packs: List<ShopPack>,
    onBuyPack: (ShopPack) -> Unit
) {
    val account by AccountSession.account.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "${account?.coins ?: 0} Coins auf dem Konto",
            color = TextMuted,
            fontFamily = BodyFont,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (!shopEnabled) {
            EmptyMarketCard(
                title = "Coinshop bald",
                body = "Bis dahin reichen Tagesbonus & Gutscheine völlig."
            )
            return
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            packs.forEach { pack ->
                CoinPackCard(pack = pack, onBuy = { onBuyPack(pack) })
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CoinPackCard(pack: ShopPack, onBuy: () -> Unit) {
    val title = ShopCatalog.playfulPackTitle(pack)
    val image = ShopCatalog.packImageRes(pack)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(BgSoft)
            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(22.dp))
            .clickable(onClick = onBuy)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(BgDeep)
            ) {
                Image(
                    painter = painterResource(image),
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Text(
                    "${pack.coins}",
                    color = Color.White,
                    fontFamily = DisplayFont,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(0.55f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontFamily = DisplayFont, fontSize = 20.sp)
                Text(
                    "${pack.coins} Coins für eure Leinwand",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${pack.amountEur.replace('.', ',')} €",
                    color = AccentRose,
                    fontFamily = DisplayFont,
                    fontSize = 18.sp
                )
                pack.compareAtEur?.takeIf { it.isNotBlank() }?.let { compare ->
                    Text(
                        "statt ${compare.replace('.', ',')} €",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemShopContent(onRefreshInventory: suspend () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = LuvApp.instance.prefs
    val account by AccountSession.account.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val owned by prefs.ownedEmojisFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    var busyEmoji by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            val remote = LuvApiClient.fetchInventory()
            if (remote.isNotEmpty()) prefs.setOwnedEmojis(remote)
        }
        onRefreshInventory()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "${account?.coins ?: 0} Coins",
            color = TextMuted,
            fontFamily = BodyFont,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Ausstattung", "Pets", "Emojis").forEachIndexed { index, label ->
                val active = tab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (active) AccentRose.copy(0.28f) else BgSoft)
                        .clickable { tab = index }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = TextPrimary,
                        fontFamily = if (active) DisplayFont else BodyFont,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        when (tab) {
            0 -> EmptyMarketCard("Ausstattung", "Bald: Outfits & Leinwand-Schmuck für Coins.")
            1 -> EmptyMarketCard("Pets", "Bald: kleine Begleiter auf eurer Leinwand.")
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 88.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(ShopCatalog.EMOJIS, key = { it.emoji }) { item ->
                        val count = owned[item.emoji] ?: 0
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(BgSoft)
                                .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(16.dp))
                                .clickable(enabled = busyEmoji == null) {
                                    busyEmoji = item.emoji
                                    scope.launch {
                                        runCatching {
                                            val (_, ownedCount) = LuvApiClient.buyEmoji(item.emoji)
                                            val next = owned.toMutableMap()
                                            next[item.emoji] = ownedCount
                                            prefs.setOwnedEmojis(next)
                                            Toast.makeText(
                                                context,
                                                "${item.emoji} gekauft (−${item.priceCoins})",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }.onFailure {
                                            Toast.makeText(
                                                context,
                                                it.message ?: "Kauf fehlgeschlagen",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        busyEmoji = null
                                    }
                                }
                                .padding(8.dp)
                        ) {
                            Text(
                                item.emoji,
                                fontSize = 32.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                            Text(
                                "${item.priceCoins}",
                                color = AccentRose,
                                fontFamily = DisplayFont,
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.BottomStart)
                            )
                            if (count > 0) {
                                Text(
                                    "×$count",
                                    color = TextMuted,
                                    fontFamily = BodyFont,
                                    fontSize = 11.sp,
                                    modifier = Modifier.align(Alignment.BottomEnd)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InventoryScreen(onOpenEmojiEditor: () -> Unit) {
    val prefs = LuvApp.instance.prefs
    var tab by remember { mutableIntStateOf(0) }
    val owned by prefs.ownedEmojisFlow.collectAsStateWithLifecycle(initialValue = emptyMap())

    LaunchedEffect(Unit) {
        runCatching {
            val remote = LuvApiClient.fetchInventory()
            if (remote.isNotEmpty()) prefs.setOwnedEmojis(remote)
        }
    }

    MenuBackdrop(includeNavigationBars = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 8.dp)
        ) {
            Text("Inventar", fontFamily = DisplayFont, fontSize = 30.sp, color = TextPrimary)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Ausstattung", "Pets", "Emojis").forEachIndexed { index, label ->
                    val active = tab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (active) AccentRose.copy(0.28f) else BgSoft)
                            .clickable { tab = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = TextPrimary,
                            fontFamily = if (active) DisplayFont else BodyFont,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            when (tab) {
                0 -> EmptyMarketCard("Noch leer", "Ausstattung aus dem Itemshop landet hier.")
                1 -> EmptyMarketCard("Noch leer", "Pets aus dem Itemshop landen hier.")
                else -> {
                    if (owned.isEmpty()) {
                        EmptyMarketCard("Noch keine Emojis", "Im Itemshop findest du welche.")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 72.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(AccentRose.copy(0.22f))
                                        .clickable(onClick = onOpenEmojiEditor)
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("✎", fontSize = 28.sp, color = TextPrimary)
                                }
                            }
                            items(owned.entries.sortedBy { it.key }.toList(), key = { it.key }) { (emoji, count) ->
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(BgSoft)
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        emoji,
                                        fontSize = 30.sp,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                    if (count > 1) {
                                        Text(
                                            "×$count",
                                            color = TextMuted,
                                            fontFamily = BodyFont,
                                            fontSize = 11.sp,
                                            modifier = Modifier.align(Alignment.BottomEnd)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmojiBarEditorDialog(
    onDismiss: () -> Unit
) {
    val prefs = LuvApp.instance.prefs
    val scope = rememberCoroutineScope()
    var bar by remember { mutableStateOf(ShopCatalog.DEFAULT_BAR) }
    var owned by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        bar = prefs.emojiBar()
        owned = prefs.ownedEmojis()
    }

    fun persist(next: List<String>) {
        bar = next
        scope.launch { prefs.setEmojiBar(next) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(BgDeep)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Reaktionsleiste", color = TextPrimary, fontFamily = DisplayFont, fontSize = 22.sp)
            Text(
                "Reihenfolge ändern, entfernen oder eigene Emojis hinzufügen.",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 13.sp
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(BgSoft)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                bar.forEachIndexed { index, emoji ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(emoji, fontSize = 28.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text(
                            "↑",
                            color = if (index == 0) TextMuted else TextPrimary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(enabled = index > 0) {
                                    val next = bar.toMutableList()
                                    next[index] = next[index - 1].also { next[index - 1] = next[index] }
                                    persist(next)
                                }
                                .padding(8.dp)
                        )
                        Text(
                            "↓",
                            color = if (index >= bar.lastIndex) TextMuted else TextPrimary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(enabled = index < bar.lastIndex) {
                                    val next = bar.toMutableList()
                                    next[index] = next[index + 1].also { next[index + 1] = next[index] }
                                    persist(next)
                                }
                                .padding(8.dp)
                        )
                        Text(
                            "✕",
                            color = AccentRose,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(enabled = bar.size > 1) {
                                    persist(bar.filterIndexed { i, _ -> i != index })
                                }
                                .padding(8.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AccentRose.copy(0.25f))
                        .clickable { showAdd = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = TextPrimary, fontFamily = DisplayFont, fontSize = 24.sp)
                }
            }
            Text(
                "Fertig",
                color = AccentRose,
                fontFamily = DisplayFont,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable(onClick = onDismiss)
                    .padding(8.dp)
            )
        }
    }

    if (showAdd) {
        val available = owned.keys.filter { it !in bar }.sorted()
        AlertDialogSimple(
            title = "Emoji hinzufügen",
            onDismiss = { showAdd = false }
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(56.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(available) { emoji ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgSoft)
                            .clickable {
                                if (bar.size < 12) persist(bar + emoji)
                                showAdd = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 26.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertDialogSimple(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(BgDeep)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, color = TextPrimary, fontFamily = DisplayFont, fontSize = 18.sp)
            content()
            Text(
                "Schließen",
                color = TextMuted,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable(onClick = onDismiss)
                    .padding(8.dp)
            )
        }
    }
}
