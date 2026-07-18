package com.luv.couple.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.LuvApp
import com.luv.couple.net.AccountSession
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.PendingProfilePlace
import com.luv.couple.net.ProfilePlaceAction
import com.luv.couple.net.ShopPack
import com.luv.couple.profile.ProfileCatalog
import com.luv.couple.profile.ProfileElType
import com.luv.couple.profile.ProfileState
import com.luv.couple.shop.ShopCatalog
import com.luv.couple.shop.ShopEmoji
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class MarketPanel { Hub, Marketplace, ItemShop, CoinShop }

@Composable
fun MarketScreen(
    shopEnabled: Boolean,
    packs: List<ShopPack>,
    onBuyPack: (ShopPack) -> Unit,
    onRefreshInventory: suspend () -> Unit = {},
    startInCoinShop: Boolean = false,
    onStartInCoinShopConsumed: () -> Unit = {},
    startPanel: MarketPanel? = null,
    onStartPanelConsumed: () -> Unit = {}
) {
    var panel by remember { mutableStateOf(MarketPanel.Hub) }
    LaunchedEffect(startInCoinShop) {
        if (startInCoinShop) {
            panel = MarketPanel.CoinShop
            onStartInCoinShopConsumed()
        }
    }
    LaunchedEffect(startPanel) {
        val target = startPanel ?: return@LaunchedEffect
        panel = target
        onStartPanelConsumed()
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
                    subtitle = "Emojis, Begleiter & mehr",
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
    var pendingBuy by remember { mutableStateOf<ShopEmoji?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            val remote = LuvApiClient.fetchInventory()
            if (remote.isNotEmpty()) prefs.setOwnedEmojis(remote)
        }
        onRefreshInventory()
    }

    pendingBuy?.let { item ->
        AlertDialog(
            onDismissRequest = { if (busyEmoji == null) pendingBuy = null },
            containerColor = BgSoft,
            title = {
                Text(
                    "Kaufen?",
                    fontFamily = DisplayFont,
                    fontSize = 22.sp,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        item.emoji,
                        fontSize = 40.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Für ${item.priceCoins} Coins in dein Inventar legen?",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Text(
                        "Du hast ${account?.coins ?: 0} Coins.",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = busyEmoji == null,
                    onClick = {
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
                                pendingBuy = null
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
                ) {
                    Text("Kaufen", color = AccentRose, fontFamily = DisplayFont)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = busyEmoji == null,
                    onClick = { pendingBuy = null }
                ) {
                    Text("Abbrechen", color = TextMuted, fontFamily = BodyFont)
                }
            }
        )
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
            listOf("Ausstattung", "Begleiter", "Emojis").forEachIndexed { index, label ->
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
            1 -> EmptyMarketCard("Begleiter", "Bald: kleine Begleiter auf eurer Leinwand.")
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
                                    pendingBuy = item
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
fun InventoryScreen(
    nickname: String,
    onOpenMarketplace: () -> Unit,
    onOpenItemShop: () -> Unit,
    onOpenProfileDesigner: () -> Unit
) {
    val prefs = LuvApp.instance.prefs
    val owned by prefs.ownedEmojisFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    var profile by remember {
        mutableStateOf(ProfileState(layout = ProfileCatalog.defaultLayout(nickname)))
    }
    var pendingAction by remember { mutableStateOf<ProfilePlaceAction?>(null) }

    LaunchedEffect(nickname) {
        runCatching {
            val remote = LuvApiClient.fetchInventory()
            if (remote.isNotEmpty()) prefs.setOwnedEmojis(remote)
        }
        val json = runCatching { prefs.profileCanvasJson() }.getOrNull()
        profile = ProfileCatalog.decode(json, nickname)
    }

    val stickers = remember(owned) {
        (ProfileCatalog.FREE_STICKERS + owned.keys).distinct().sorted()
    }

    fun confirmPlace(action: ProfilePlaceAction) {
        pendingAction = action
    }

    MenuBackdrop(includeNavigationBars = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 8.dp)
        ) {
            ProfileInventoryPanel(
                ownedStickers = stickers,
                currentThemeId = profile.themeId,
                currentCompanion = profile.companionEmoji,
                hasGlass = profile.layout.any { it.type == ProfileElType.Glass },
                hasBio = profile.layout.any { it.type == ProfileElType.Bio },
                onTheme = { confirmPlace(ProfilePlaceAction.Theme(it.id)) },
                onSticker = { confirmPlace(ProfilePlaceAction.Sticker(it)) },
                onCompanion = { confirmPlace(ProfilePlaceAction.Buddy(it)) },
                onGlass = { confirmPlace(ProfilePlaceAction.Glass) },
                onBio = { confirmPlace(ProfilePlaceAction.Bio) },
                onOpenMarketplace = onOpenMarketplace,
                onOpenItemShop = onOpenItemShop,
                modifier = Modifier.fillMaxSize(),
                showCardChrome = true
            )
        }
    }

    val action = pendingAction
    if (action != null) {
        val label = when (action) {
            is ProfilePlaceAction.Theme -> "diesen Hintergrund"
            is ProfilePlaceAction.Sticker -> "diesen Sticker"
            is ProfilePlaceAction.Buddy -> "diesen Begleiter"
            ProfilePlaceAction.Glass -> "das Münzglas"
            ProfilePlaceAction.Bio -> "die Bio"
        }
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("Profil gestalten?", fontFamily = DisplayFont, color = TextPrimary) },
            text = {
                Text(
                    "Du wirst zum Profil gestalten geleitet und $label dort platziert.",
                    fontFamily = BodyFont,
                    color = TextMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        PendingProfilePlace.offer(action)
                        pendingAction = null
                        onOpenProfileDesigner()
                    }
                ) {
                    Text("Ja, platzieren", color = AccentRose, fontFamily = DisplayFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text("Abbrechen", color = TextMuted, fontFamily = BodyFont)
                }
            }
        )
    }
}

@Composable
fun EmojiBarEditorDialog(
    onDismiss: () -> Unit
) {
    val prefs = LuvApp.instance.prefs
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var bar by remember { mutableStateOf(ShopCatalog.DEFAULT_BAR.take(ShopCatalog.MAX_BAR)) }
    var owned by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var showAdd by remember { mutableStateOf(false) }

    // Drag: Liste bleibt während des Ziehens stabil — erst am Ende umsortieren
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var overTrash by remember { mutableStateOf(false) }

    val slotDp = 52.dp
    val gapDp = 8.dp
    val trashDp = 64.dp
    val listTrashGapDp = 14.dp
    val slotPx = with(density) { slotDp.toPx() }
    val gapPx = with(density) { gapDp.toPx() }
    val stride = slotPx + gapPx

    LaunchedEffect(Unit) {
        bar = prefs.emojiBar().take(ShopCatalog.MAX_BAR)
        owned = prefs.ownedEmojis()
    }

    fun persist(next: List<String>) {
        val clean = next.filter { it.isNotBlank() }.distinct().take(ShopCatalog.MAX_BAR)
        if (clean.isEmpty()) return
        bar = clean
        scope.launch { prefs.setEmojiBar(clean) }
    }

    fun targetIndex(): Int {
        if (dragFrom < 0 || bar.isEmpty()) return 0
        val center = dragFrom * stride + dragOffsetY + slotPx / 2f
        return (center / stride).roundToInt().coerceIn(0, bar.lastIndex)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.55f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1A2030), BgDeep)
                        )
                    )
                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(28.dp))
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Reaktionsleiste",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 26.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Ziehen zum Sortieren · in den Mülleimer zum Entfernen",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${bar.size} / ${ShopCatalog.MAX_BAR}",
                    color = AccentRose.copy(0.9f),
                    fontFamily = DisplayFont,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                val emojiStackH = slotDp * bar.size.coerceAtLeast(1) +
                    gapDp * (bar.size - 1).coerceAtLeast(0)
                val to = if (dragFrom >= 0) targetIndex() else -1
                val trashHot = overTrash && dragFrom >= 0
                val trashScale by animateFloatAsState(
                    targetValue = if (trashHot) 1.05f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "trashScale"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(0.04f), RoundedCornerShape(22.dp))
                        .border(1.dp, Color.White.copy(0.06f), RoundedCornerShape(22.dp))
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(emojiStackH)
                    ) {
                        bar.forEachIndexed { index, emoji ->
                            val dragging = dragFrom == index
                            val shiftSlots = when {
                                dragFrom < 0 || dragging || overTrash -> 0
                                dragFrom < index && to >= index -> -1
                                dragFrom > index && to <= index -> 1
                                else -> 0
                            }
                            val shiftY by animateFloatAsState(
                                targetValue = shiftSlots * stride,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "slotShift$index"
                            )
                            Box(
                                modifier = Modifier
                                    .zIndex(if (dragging) 12f else 1f)
                                    .graphicsLayer {
                                        translationY = index * stride +
                                            if (dragging) dragOffsetY else shiftY
                                        scaleX = if (dragging) 1.08f else 1f
                                        scaleY = if (dragging) 1.08f else 1f
                                        alpha = when {
                                            dragging && overTrash -> 0.35f
                                            dragging -> 1f
                                            else -> 1f
                                        }
                                    }
                                    .fillMaxWidth()
                                    .height(slotDp)
                                    .shadow(
                                        if (dragging) 14.dp else 0.dp,
                                        RoundedCornerShape(16.dp),
                                        clip = false
                                    )
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (dragging) Color(0xFF2A3348)
                                        else Color.White.copy(0.07f)
                                    )
                                    .border(
                                        1.dp,
                                        if (dragging) AccentRose.copy(0.5f)
                                        else Color.White.copy(0.08f),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .pointerInput(emoji) {
                                        detectDragGestures(
                                            onDragStart = {
                                                val from = bar.indexOf(emoji)
                                                if (from < 0) return@detectDragGestures
                                                dragFrom = from
                                                dragOffsetY = 0f
                                                overTrash = false
                                                haptics.performHapticFeedback(
                                                    HapticFeedbackType.LongPress
                                                )
                                            },
                                            onDragEnd = {
                                                val from = dragFrom
                                                val trash = overTrash
                                                val dest = targetIndex()
                                                dragFrom = -1
                                                dragOffsetY = 0f
                                                overTrash = false
                                                if (from < 0) return@detectDragGestures
                                                when {
                                                    trash && bar.size > 1 -> {
                                                        haptics.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                        persist(
                                                            bar.filterIndexed { i, _ -> i != from }
                                                        )
                                                    }
                                                    dest != from -> {
                                                        val next = bar.toMutableList()
                                                        val item = next.removeAt(from)
                                                        next.add(dest, item)
                                                        persist(next)
                                                        haptics.performHapticFeedback(
                                                            HapticFeedbackType.TextHandleMove
                                                        )
                                                    }
                                                    else -> Unit
                                                }
                                            },
                                            onDragCancel = {
                                                dragFrom = -1
                                                dragOffsetY = 0f
                                                overTrash = false
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                if (dragFrom < 0) return@detectDragGestures
                                                dragOffsetY += dragAmount.y
                                                // Unterhalb der letzten Slot-Mitte → Mülleimer
                                                val listBottom = bar.size * stride - gapPx
                                                val finger =
                                                    dragFrom * stride + dragOffsetY + slotPx / 2f
                                                val nowOver = finger > listBottom + gapPx * 0.5f
                                                if (nowOver != overTrash) {
                                                    overTrash = nowOver
                                                    if (nowOver) {
                                                        haptics.performHapticFeedback(
                                                            HapticFeedbackType.TextHandleMove
                                                        )
                                                    }
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 30.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(listTrashGapDp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(trashDp)
                            .graphicsLayer {
                                scaleX = trashScale
                                scaleY = trashScale
                            }
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (trashHot) AccentRose.copy(0.28f)
                                else Color.White.copy(0.05f)
                            )
                            .border(
                                1.dp,
                                if (trashHot) AccentRose.copy(0.7f)
                                else Color.White.copy(0.08f),
                                RoundedCornerShape(18.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🗑", fontSize = if (trashHot) 26.sp else 22.sp)
                            Text(
                                if (trashHot) "Loslassen zum Entfernen" else "Mülleimer",
                                color = if (trashHot) AccentRose else TextMuted,
                                fontFamily = BodyFont,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                val canAdd = bar.size < ShopCatalog.MAX_BAR
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .shadow(8.dp, CircleShape, clip = false)
                        .clip(CircleShape)
                        .background(
                            if (canAdd) AccentRose.copy(0.9f)
                            else Color.White.copy(0.08f)
                        )
                        .clickable(enabled = canAdd) { showAdd = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "+",
                        color = if (canAdd) Color.White else TextMuted,
                        fontFamily = DisplayFont,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (canAdd) "Emoji hinzufügen" else "Leiste voll (max. ${ShopCatalog.MAX_BAR})",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AccentRose.copy(0.2f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Fertig", color = AccentRose, fontFamily = DisplayFont, fontSize = 17.sp)
                }
            }
        }
    }

    if (showAdd) {
        val available = owned.keys.filter { it !in bar }.sorted()
        AlertDialogSimple(
            title = "Emoji hinzufügen",
            onDismiss = { showAdd = false }
        ) {
            if (available.isEmpty()) {
                Text(
                    "Keine weiteren Emojis im Inventar.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp
                )
            } else {
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
                                    if (bar.size < ShopCatalog.MAX_BAR) {
                                        persist(bar + emoji)
                                    }
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
