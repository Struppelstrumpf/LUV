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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
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
import com.luv.couple.shop.ShopPet
import com.luv.couple.shop.ShopTheme
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.MaleBlue
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class MarketPanel { Hub, Marketplace, ItemShop, CoinShop }

/** Wohin „Zurück“ aus einem Deep-Link (Inventar/Profil → Markt) führt. */
sealed class MarketReturnTo {
    data class Inventory(val subTab: Int) : MarketReturnTo()
    data class Profile(val chestTab: Int) : MarketReturnTo()
    data object None : MarketReturnTo()
}

@Composable
fun MarketScreen(
    shopEnabled: Boolean,
    packs: List<ShopPack>,
    onBuyPack: (ShopPack, Int) -> Unit,
    onRefreshInventory: suspend () -> Unit = {},
    startInCoinShop: Boolean = false,
    onStartInCoinShopConsumed: () -> Unit = {},
    startPanel: MarketPanel? = null,
    onStartPanelConsumed: () -> Unit = {},
    startShopTab: Int = 0,
    onLeaveDeepLink: (() -> Unit)? = null,
    /** false = Käufe/Lootbox gesperrt (Google-Anmeldung fehlt) */
    economyUnlocked: Boolean = true,
    onRequireGoogle: () -> Unit = {}
) {
    var panel by remember { mutableStateOf(MarketPanel.Hub) }
    var deepLinked by remember { mutableStateOf(false) }
    var shopTabSeed by remember { mutableIntStateOf(0) }
    fun backFromPanel() {
        if (deepLinked && onLeaveDeepLink != null) {
            deepLinked = false
            onLeaveDeepLink()
        } else {
            panel = MarketPanel.Hub
        }
    }
    LaunchedEffect(startInCoinShop) {
        if (startInCoinShop) {
            panel = MarketPanel.CoinShop
            deepLinked = onLeaveDeepLink != null
            onStartInCoinShopConsumed()
        }
    }
    LaunchedEffect(startPanel) {
        val target = startPanel ?: return@LaunchedEffect
        if (target == MarketPanel.ItemShop) {
            shopTabSeed = startShopTab.coerceIn(0, ShopCatalog.SHOP_TAB_LABELS.lastIndex)
        }
        panel = target
        deepLinked = onLeaveDeepLink != null
        onStartPanelConsumed()
    }
    when (panel) {
        MarketPanel.Hub -> MarketHub(
            packs = packs,
            onOpenMarketplace = {
                deepLinked = false
                panel = MarketPanel.Marketplace
            },
            onOpenItemShop = {
                deepLinked = false
                shopTabSeed = 0
                panel = MarketPanel.ItemShop
            },
            onOpenCoinShop = {
                deepLinked = false
                panel = MarketPanel.CoinShop
            }
        )
        MarketPanel.Marketplace -> MarketExpandShell(
            title = "Marktplatz",
            onBack = { backFromPanel() }
        ) {
            PlayerMarketScreen(
                onClose = { backFromPanel() },
                economyUnlocked = economyUnlocked,
                onRequireGoogle = onRequireGoogle
            )
        }
        MarketPanel.ItemShop -> MarketExpandShell(
            title = "Itemshop",
            onBack = { backFromPanel() }
        ) {
            key(shopTabSeed) {
                ItemShopContent(
                    onRefreshInventory = onRefreshInventory,
                    initialTab = shopTabSeed,
                    economyUnlocked = economyUnlocked,
                    onRequireGoogle = onRequireGoogle
                )
            }
        }
        MarketPanel.CoinShop -> MarketExpandShell(
            title = "Coinshop",
            onBack = { backFromPanel() }
        ) {
            CoinShopContent(
                shopEnabled = shopEnabled,
                packs = packs,
                onBuyPack = onBuyPack,
                economyUnlocked = economyUnlocked,
                onRequireGoogle = onRequireGoogle
            )
        }
    }
}

@Composable
private fun MarketHub(
    packs: List<ShopPack>,
    onOpenMarketplace: () -> Unit,
    onOpenItemShop: () -> Unit,
    onOpenCoinShop: () -> Unit
) {
    var hub by remember { mutableStateOf<LuvApiClient.MarketHubData?>(null) }
    val marketAlert by com.luv.couple.net.NotificationBadges.hasMarketDot.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        hub = runCatching { LuvApiClient.fetchMarketHub() }.getOrNull()
        com.luv.couple.net.NotificationBadges.refreshPendingSales()
    }
    val marketPreviews = hub?.marketNewest.orEmpty()
    val shopPreviews = hub?.shopTop.orEmpty()
    val coinPreviews = remember(hub, packs) {
        val fromApi = hub?.coinNewest.orEmpty()
        if (fromApi.isNotEmpty()) fromApi
        else packs.take(2).map { pack ->
            LuvApiClient.MarketHubPreview(
                emoji = "🪙",
                label = ShopCatalog.playfulPackTitle(pack),
                detail = "${pack.amountEur} € · ${pack.coins}",
                packId = pack.id,
                packCoins = pack.coins
            )
        }
    }
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
                MarketTile(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    title = "Marktplatz",
                    badge = if (marketAlert) "Verkauf!" else "Neueste Angebote",
                    brush = Brush.linearGradient(listOf(Color(0xFF2A3148), Color(0xFF1A2030))),
                    previews = marketPreviews,
                    emptyHint = "Noch keine Angebote",
                    alertDot = marketAlert,
                    onClick = onOpenMarketplace
                )
                MarketTile(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    title = "Itemshop",
                    badge = "Meistgekauft",
                    brush = Brush.linearGradient(listOf(Color(0xFF3A2438), Color(0xFF241828))),
                    previews = shopPreviews,
                    emptyHint = "Beliebte Items laden…",
                    onClick = onOpenItemShop
                )
                MarketTile(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    title = "Coinshop",
                    badge = "Angebote",
                    brush = Brush.linearGradient(listOf(Color(0xFF3A3020), Color(0xFF241C12))),
                    previews = coinPreviews,
                    emptyHint = "Bald verfügbar",
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
    badge: String,
    brush: Brush,
    previews: List<LuvApiClient.MarketHubPreview>,
    emptyHint: String,
    onClick: () -> Unit,
    alertDot: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(brush)
            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (alertDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(AccentRose)
            )
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 22.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    badge,
                    color = if (alertDot) AccentRose else TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
            if (previews.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emptyHint, color = TextMuted, fontFamily = BodyFont, fontSize = 13.sp)
                }
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    previews.take(2).forEach { preview ->
                        MarketTilePreviewCard(
                            preview = preview,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        )
                    }
                    if (previews.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketTilePreviewCard(
    preview: LuvApiClient.MarketHubPreview,
    modifier: Modifier = Modifier
) {
    val packImage = when {
        preview.packCoins > 0 -> ShopCatalog.packImageRes(
            ShopPack(
                id = preview.packId.orEmpty(),
                label = preview.label,
                coins = preview.packCoins,
                amountEur = ""
            )
        )
        !preview.packId.isNullOrBlank() -> ShopCatalog.packImageRes(
            ShopPack(
                id = preview.packId,
                label = preview.label,
                coins = preview.packCoins.coerceAtLeast(1),
                amountEur = ""
            )
        )
        else -> null
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(0.07f))
            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (packImage != null) {
            Image(
                painter = painterResource(packImage),
                contentDescription = preview.label,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(preview.emoji, fontSize = 28.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            preview.label,
            color = TextPrimary,
            fontFamily = DisplayFont,
            fontSize = 13.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        Text(
            preview.detail,
            color = TextMuted,
            fontFamily = BodyFont,
            fontSize = 11.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
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
    onBuyPack: (ShopPack, Int) -> Unit,
    economyUnlocked: Boolean = true,
    onRequireGoogle: () -> Unit = {}
) {
    val account by AccountSession.account.collectAsStateWithLifecycle()
    var pendingPack by remember { mutableStateOf<ShopPack?>(null) }
    var quantity by remember { mutableIntStateOf(1) }
    val offers = remember(packs) { packs.filter { it.isOffer || it.onceOnly } }
    val normals = remember(packs) { packs.filterNot { it.isOffer || it.onceOnly } }

    fun requestBuy(pack: ShopPack) {
        if (!economyUnlocked) {
            onRequireGoogle()
            return
        }
        quantity = 1
        pendingPack = pack
    }

    pendingPack?.let { pack ->
        val unit = pack.amountEur.replace(',', '.').toDoubleOrNull() ?: 0.0
        val qty = if (pack.onceOnly) 1 else quantity.coerceIn(1, 20)
        val total = String.format(java.util.Locale.GERMANY, "%.2f", unit * qty)
        AlertDialog(
            onDismissRequest = { pendingPack = null },
            containerColor = BgSoft,
            title = {
                Text(
                    ShopCatalog.playfulPackTitle(pack),
                    fontFamily = DisplayFont,
                    color = TextPrimary,
                    fontSize = 22.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${pack.coins * qty} Coins · $total €",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 14.sp
                    )
                    if (pack.onceOnly) {
                        Text(
                            "Aktionsangebot — nur einmal kaufbar.",
                            color = AccentRose,
                            fontFamily = BodyFont,
                            fontSize = 13.sp
                        )
                    } else {
                        Text(
                            "Menge",
                            color = TextPrimary,
                            fontFamily = DisplayFont,
                            fontSize = 15.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            TextButton(
                                onClick = { quantity = (quantity - 1).coerceAtLeast(1) },
                                enabled = quantity > 1
                            ) {
                                Text("−", color = TextPrimary, fontSize = 22.sp)
                            }
                            Text(
                                "$qty",
                                color = TextPrimary,
                                fontFamily = DisplayFont,
                                fontSize = 22.sp
                            )
                            TextButton(
                                onClick = { quantity = (quantity + 1).coerceAtMost(20) }
                            ) {
                                Text("+", color = TextPrimary, fontSize = 22.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val buyQty = if (pack.onceOnly) 1 else quantity.coerceIn(1, 20)
                    pendingPack = null
                    onBuyPack(pack, buyQty)
                }) {
                    Text("Kaufen", color = AccentRose, fontFamily = DisplayFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPack = null }) {
                    Text("Abbrechen", color = TextMuted, fontFamily = BodyFont)
                }
            }
        )
    }

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
            if (offers.isNotEmpty()) {
                Text(
                    "Angebote",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 16.sp
                )
                offers.forEach { pack ->
                    CoinPackCard(
                        pack = pack,
                        onBuy = { requestBuy(pack) }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Coin-Pakete",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 16.sp
                )
            }
            normals.forEach { pack ->
                CoinPackCard(
                    pack = pack,
                    onBuy = { requestBuy(pack) }
                )
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
                when {
                    pack.onceOnly || pack.compareAtEur != null -> {
                        Text(
                            "1× Angebot",
                            color = Color.White,
                            fontFamily = DisplayFont,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AccentRose.copy(0.92f))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    pack.mostPurchased -> {
                        Text(
                            "Am meisten gekauft",
                            color = Color.White,
                            fontFamily = DisplayFont,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaleBlue.copy(0.92f))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
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

private sealed class ShopPendingBuy {
    data class Emoji(val item: ShopEmoji) : ShopPendingBuy()
    data class Theme(val item: ShopTheme) : ShopPendingBuy()
    data class Sticker(val item: ShopEmoji) : ShopPendingBuy()
    data class Pet(val item: ShopPet) : ShopPendingBuy()
}

@Composable
private fun ItemShopContent(
    onRefreshInventory: suspend () -> Unit,
    initialTab: Int = 0,
    economyUnlocked: Boolean = true,
    onRequireGoogle: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = LuvApp.instance.prefs
    val account by AccountSession.account.collectAsStateWithLifecycle()
    var tab by remember {
        mutableIntStateOf(initialTab.coerceIn(0, ShopCatalog.SHOP_TAB_LABELS.lastIndex))
    }
    val ownedEmojis by prefs.ownedEmojisFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val ownedThemes by prefs.ownedThemesFlow.collectAsStateWithLifecycle(
        initialValue = listOf(ProfileCatalog.DEFAULT_THEME_ID)
    )
    val ownedStickers by prefs.ownedStickersFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val ownedPets by prefs.ownedPetsFlow.collectAsStateWithLifecycle(
        initialValue = listOf(ShopCatalog.DEFAULT_PET)
    )
    var busyKey by remember { mutableStateOf<String?>(null) }
    var pendingBuy by remember { mutableStateOf<ShopPendingBuy?>(null) }

    fun openBuy(pending: ShopPendingBuy) {
        if (!economyUnlocked) {
            onRequireGoogle()
            return
        }
        pendingBuy = pending
    }

    suspend fun reloadBag() {
        runCatching {
            val remote = LuvApiClient.fetchInventory()
            prefs.applyInventoryBag(
                emojis = remote.emojis,
                themes = remote.themes,
                stickers = remote.stickers,
                pets = remote.pets,
                equippedPet = remote.equippedPet
            )
        }
        onRefreshInventory()
    }

    LaunchedEffect(Unit) { reloadBag() }

    pendingBuy?.let { pending ->
        val preview = when (pending) {
            is ShopPendingBuy.Emoji -> pending.item.emoji
            is ShopPendingBuy.Theme -> pending.item.emoji
            is ShopPendingBuy.Sticker -> pending.item.emoji
            is ShopPendingBuy.Pet -> pending.item.emoji
        }
        val price = when (pending) {
            is ShopPendingBuy.Emoji -> pending.item.priceCoins
            is ShopPendingBuy.Theme -> pending.item.priceCoins
            is ShopPendingBuy.Sticker -> pending.item.priceCoins
            is ShopPendingBuy.Pet -> pending.item.priceCoins
        }
        val titleLabel = when (pending) {
            is ShopPendingBuy.Emoji -> "Emoji $preview"
            is ShopPendingBuy.Theme -> pending.item.label
            is ShopPendingBuy.Sticker -> "Sticker $preview"
            is ShopPendingBuy.Pet -> pending.item.label
        }
        val kindLabel = when (pending) {
            is ShopPendingBuy.Emoji -> "dieses Emoji"
            is ShopPendingBuy.Theme -> "den Hintergrund „${pending.item.label}“"
            is ShopPendingBuy.Sticker -> "diesen Sticker"
            is ShopPendingBuy.Pet -> "den Begleiter „${pending.item.label}“"
        }
        val alreadyOwned = when (pending) {
            is ShopPendingBuy.Theme -> pending.item.id in ownedThemes
            is ShopPendingBuy.Pet -> pending.item.emoji in ownedPets
            else -> false
        }
        AlertDialog(
            onDismissRequest = { if (busyKey == null) pendingBuy = null },
            containerColor = BgSoft,
            title = {
                Text(
                    if (alreadyOwned) "Vorschau" else "Vorschau · Kaufen?",
                    fontFamily = DisplayFont,
                    fontSize = 22.sp,
                    color = TextPrimary
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (pending) {
                        is ShopPendingBuy.Theme -> ThemePreviewCard(
                            themeId = pending.item.id,
                            emoji = pending.item.emoji,
                            label = pending.item.label
                        )
                        else -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(BgDeep)
                                    .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(preview, fontSize = 72.sp)
                            }
                        }
                    }
                    Text(
                        titleLabel,
                        color = TextPrimary,
                        fontFamily = DisplayFont,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    if (!alreadyOwned) {
                        Text(
                            if (price <= 0) "Kostenlos in dein Inventar legen?"
                            else "Für $price Coins $kindLabel in dein Inventar legen?",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Du hast ${account?.coins ?: 0} Coins.",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            "Schon in deinem Inventar.",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                if (alreadyOwned) {
                    TextButton(onClick = { pendingBuy = null }) {
                        Text("Schließen", color = AccentRose, fontFamily = DisplayFont)
                    }
                } else TextButton(
                    enabled = busyKey == null,
                    onClick = {
                        busyKey = preview
                        scope.launch {
                            runCatching {
                                when (pending) {
                                    is ShopPendingBuy.Emoji -> {
                                        val (_, ownedCount) = LuvApiClient.buyEmoji(pending.item.emoji)
                                        val next = ownedEmojis.toMutableMap()
                                        next[pending.item.emoji] = ownedCount
                                        prefs.setOwnedEmojis(next)
                                    }
                                    is ShopPendingBuy.Theme -> {
                                        LuvApiClient.buyTheme(pending.item.id)
                                        reloadBag()
                                    }
                                    is ShopPendingBuy.Sticker -> {
                                        LuvApiClient.buySticker(pending.item.emoji)
                                        reloadBag()
                                    }
                                    is ShopPendingBuy.Pet -> {
                                        LuvApiClient.buyPet(pending.item.emoji)
                                        reloadBag()
                                    }
                                }
                                Toast.makeText(
                                    context,
                                    if (price <= 0) "$preview erhalten" else "$preview gekauft (−$price)",
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
                            busyKey = null
                        }
                    }
                ) {
                    Text(
                        if (price <= 0) "Nehmen" else "Kaufen",
                        color = AccentRose,
                        fontFamily = DisplayFont
                    )
                }
            },
            dismissButton = {
                TextButton(enabled = busyKey == null, onClick = { pendingBuy = null }) {
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
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShopCatalog.SHOP_TAB_LABELS.chunked(2).forEachIndexed { rowIndex, rowLabels ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowLabels.forEachIndexed { colIndex, label ->
                        val index = rowIndex * 2 + colIndex
                        val active = tab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (active) AccentRose.copy(0.28f) else BgSoft)
                                .clickable { tab = index }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = TextPrimary,
                                fontFamily = if (active) DisplayFont else BodyFont,
                                fontSize = 13.sp,
                                softWrap = true,
                                maxLines = 2,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        when (tab) {
            0 -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(ShopCatalog.STICKERS, key = { "s-${it.emoji}" }) { item ->
                    val count = ownedStickers[item.emoji] ?: 0
                    ShopGridCell(
                        emoji = item.emoji,
                        price = item.priceCoins,
                        ownedLabel = if (count > 0) "×$count" else null,
                        themeId = null,
                        enabled = busyKey == null,
                        onClick = { openBuy(ShopPendingBuy.Sticker(item)) }
                    )
                }
            }
            1 -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(ShopCatalog.THEMES, key = { "t-${it.id}" }) { item ->
                    val have = item.id in ownedThemes
                    ShopGridCell(
                        emoji = item.emoji,
                        price = item.priceCoins,
                        ownedLabel = if (have) "✓" else null,
                        themeId = item.id,
                        enabled = busyKey == null,
                        onClick = { openBuy(ShopPendingBuy.Theme(item)) }
                    )
                }
            }
            2 -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(ShopCatalog.PETS, key = { it.emoji }) { item ->
                    val have = item.emoji in ownedPets
                    ShopGridCell(
                        emoji = item.emoji,
                        price = item.priceCoins,
                        ownedLabel = if (have) "✓" else null,
                        themeId = null,
                        enabled = busyKey == null,
                        onClick = { openBuy(ShopPendingBuy.Pet(item)) }
                    )
                }
            }
            3 -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(ShopCatalog.EMOJIS, key = { it.emoji }) { item ->
                    val count = ownedEmojis[item.emoji] ?: 0
                    ShopGridCell(
                        emoji = item.emoji,
                        price = item.priceCoins,
                        ownedLabel = if (count > 0) "×$count" else null,
                        themeId = null,
                        enabled = busyKey == null,
                        onClick = { openBuy(ShopPendingBuy.Emoji(item)) }
                    )
                }
            }
            else -> LootboxTab(
                coins = account?.coins ?: 0,
                busy = busyKey != null,
                onBusy = { busyKey = it },
                onRefresh = { reloadBag() },
                economyUnlocked = economyUnlocked,
                onRequireGoogle = onRequireGoogle
            )
        }
    }
}

@Composable
private fun LootboxTab(
    coins: Int,
    busy: Boolean,
    onBusy: (String?) -> Unit,
    onRefresh: suspend () -> Unit,
    economyUnlocked: Boolean = true,
    onRequireGoogle: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val prefs = LuvApp.instance.prefs
    val confirmBuy by prefs.lootboxConfirmBuyFlow.collectAsStateWithLifecycle(initialValue = true)
    var queue by remember { mutableStateOf<List<com.luv.couple.net.LootboxResult>>(emptyList()) }
    var activePendingId by remember { mutableStateOf<String?>(null) }
    var tapsLeft by remember { mutableIntStateOf(0) }
    var revealedReward by remember { mutableStateOf<com.luv.couple.net.LootboxResult?>(null) }
    var phase by remember { mutableStateOf("idle") } // idle | tapping | reveal
    var shake by remember { mutableFloatStateOf(0f) }
    var flash by remember { mutableFloatStateOf(0f) }
    var showConfirmBuy by remember { mutableStateOf(false) }
    var buyQty by remember { mutableIntStateOf(1) }
    val shakeAnim by animateFloatAsState(shake, label = "lootShake")
    val flashAnim by animateFloatAsState(flash, label = "lootFlash")
    val maxAffordable = (coins / ShopCatalog.LOOTBOX_PRICE_COINS).coerceAtLeast(0)
    val price = ShopCatalog.LOOTBOX_PRICE_COINS

    fun kindLabel(kind: String): String = when (kind) {
        "themes" -> "Hintergrund"
        "pets" -> "Begleiter"
        "stickers" -> "Sticker"
        "emojis" -> "Emoji"
        else -> "Item"
    }

    fun resolveLabel(result: com.luv.couple.net.LootboxResult): String = when (result.kind) {
        "themes" -> ShopCatalog.THEMES.firstOrNull { it.id == result.itemId }?.label
            ?: result.label
        "pets" -> ShopCatalog.PETS.firstOrNull { it.emoji == result.itemId }?.label
            ?: result.label
        else -> result.emoji.ifBlank { result.label }
    }

    fun beginOpening(next: com.luv.couple.net.LootboxResult, rest: List<com.luv.couple.net.LootboxResult>) {
        activePendingId = next.pendingId
        queue = rest
        tapsLeft = ShopCatalog.LOOTBOX_TAP_COUNT
        phase = "tapping"
        revealedReward = null
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun startLootboxPurchase(quantity: Int) {
        if (!economyUnlocked) {
            onRequireGoogle()
            return
        }
        val qty = quantity.coerceAtLeast(1)
        if (coins < price * qty) {
            Toast.makeText(context, "Nicht genug Coins", Toast.LENGTH_SHORT).show()
            return
        }
        onBusy("lootbox")
        scope.launch {
            runCatching { LuvApiClient.buyLootbox(qty) }
                .onSuccess { result ->
                    onRefresh()
                    val all = result.pending.ifEmpty { result.purchased }
                    val first = all.firstOrNull()
                    if (first == null) {
                        Toast.makeText(context, "Kauf fehlgeschlagen", Toast.LENGTH_SHORT).show()
                    } else {
                        beginOpening(first, all.drop(1))
                    }
                }
                .onFailure {
                    Toast.makeText(
                        context,
                        it.message ?: "Kauf fehlgeschlagen",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            onBusy(null)
        }
    }

    fun finishTapsAndOpen() {
        val id = activePendingId
        if (id.isNullOrBlank()) {
            phase = "idle"
            return
        }
        onBusy("lootbox-open")
        scope.launch {
            shake = 1f
            kotlinx.coroutines.delay(80)
            shake = -1f
            kotlinx.coroutines.delay(80)
            shake = 1f
            kotlinx.coroutines.delay(80)
            shake = 0f
            flash = 1f
            runCatching { LuvApiClient.openLootbox(id) }
                .onSuccess { reward ->
                    revealedReward = reward
                    phase = "reveal"
                    activePendingId = null
                    onRefresh()
                }
                .onFailure {
                    Toast.makeText(
                        context,
                        it.message ?: "Öffnen fehlgeschlagen",
                        Toast.LENGTH_SHORT
                    ).show()
                    // Pending bleibt serverseitig — Queue neu laden
                    runCatching { LuvApiClient.pendingLootboxes() }
                        .onSuccess { list ->
                            queue = list
                            val first = list.firstOrNull()
                            if (first != null) beginOpening(first, list.drop(1))
                            else phase = "idle"
                        }
                        .onFailure { phase = "idle" }
                }
            kotlinx.coroutines.delay(220)
            flash = 0f
            onBusy(null)
        }
    }

    fun afterRevealDismiss() {
        revealedReward = null
        val next = queue.firstOrNull()
        if (next != null) {
            beginOpening(next, queue.drop(1))
        } else {
            phase = "idle"
            tapsLeft = 0
            activePendingId = null
            scope.launch {
                runCatching { LuvApiClient.pendingLootboxes() }
                    .onSuccess { list ->
                        if (list.isNotEmpty()) {
                            beginOpening(list.first(), list.drop(1))
                        }
                    }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!economyUnlocked) return@LaunchedEffect
        runCatching { LuvApiClient.pendingLootboxes() }
            .onSuccess { list ->
                if (list.isNotEmpty() && phase == "idle") {
                    beginOpening(list.first(), list.drop(1))
                }
            }
    }

    if (showConfirmBuy) {
        val cappedMax = maxAffordable.coerceAtLeast(1)
        val qty = buyQty.coerceIn(1, cappedMax)
        val total = qty * price
        AlertDialog(
            onDismissRequest = { if (!busy) showConfirmBuy = false },
            containerColor = BgSoft,
            title = {
                Text("Lootbox kaufen?", fontFamily = DisplayFont, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Zufälliger Inhalt, nicht erstattungsfähig. " +
                            "Gekaufte Boxen bleiben gespeichert, bis du sie öffnest.",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 14.sp
                    )
                    Text(
                        "Menge",
                        color = TextPrimary,
                        fontFamily = DisplayFont,
                        fontSize = 15.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        TextButton(
                            onClick = { buyQty = (buyQty - 1).coerceAtLeast(1) },
                            enabled = qty > 1 && !busy
                        ) {
                            Text("−", color = TextPrimary, fontSize = 22.sp)
                        }
                        Text(
                            "$qty",
                            color = TextPrimary,
                            fontFamily = DisplayFont,
                            fontSize = 22.sp
                        )
                        TextButton(
                            onClick = { buyQty = (buyQty + 1).coerceAtMost(cappedMax) },
                            enabled = qty < cappedMax && !busy
                        ) {
                            Text("+", color = TextPrimary, fontSize = 22.sp)
                        }
                    }
                    Text(
                        "$total Coins · max. $cappedMax (dein Guthaben)",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && maxAffordable >= 1,
                    onClick = {
                        showConfirmBuy = false
                        startLootboxPurchase(qty)
                    }
                ) {
                    Text(
                        "Kaufen · $total Coins",
                        color = AccentRose,
                        fontFamily = DisplayFont
                    )
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { showConfirmBuy = false }) {
                    Text("Abbrechen", color = TextMuted, fontFamily = BodyFont)
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .graphicsLayer {
                        translationX = shakeAnim * 10f
                        rotationZ = shakeAnim * 4f
                    }
            ) {
                Text(
                    "Lootbox",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 28.sp
                )
                Text(
                    "$price Coins",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 15.sp
                )
                val waiting = queue.size + if (phase == "tapping" || phase == "reveal") 1 else 0
                if (waiting > 1 || (waiting == 1 && phase != "idle")) {
                    Text(
                        if (phase == "idle") "$waiting ungeöffnet" else "Noch $waiting Boxen",
                        color = MaleBlue,
                        fontFamily = BodyFont,
                        fontSize = 13.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(168.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF2A3344), Color(0xFF1A2230))
                            )
                        )
                        .border(1.5.dp, MaleBlue.copy(0.55f), RoundedCornerShape(28.dp))
                        .clickable(enabled = !busy && phase != "reveal") {
                            when (phase) {
                                "idle" -> {
                                    if (!economyUnlocked) {
                                        onRequireGoogle()
                                    } else if (confirmBuy) {
                                        if (coins < price) {
                                            Toast.makeText(
                                                context,
                                                "Nicht genug Coins",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            buyQty = 1
                                            showConfirmBuy = true
                                        }
                                    } else {
                                        startLootboxPurchase(1)
                                    }
                                }
                                "tapping" -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    tapsLeft = (tapsLeft - 1).coerceAtLeast(0)
                                    if (tapsLeft == 0) {
                                        finishTapsAndOpen()
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎁", fontSize = 72.sp)
                }
                Text(
                    when (phase) {
                        "tapping" -> "Noch $tapsLeft× tippen"
                        "reveal" -> "Geöffnet!"
                        else -> "Tippen zum Öffnen · $price Coins"
                    },
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Kauf bestätigen",
                            color = TextPrimary,
                            fontFamily = DisplayFont,
                            fontSize = 14.sp
                        )
                        Text(
                            if (confirmBuy) {
                                "Vor dem Kauf nachfragen · Menge wählbar"
                            } else {
                                "Direkt mit Tippen kaufen"
                            },
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = confirmBuy,
                        onCheckedChange = { on ->
                            scope.launch { prefs.setLootboxConfirmBuy(on) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaleBlue,
                            uncheckedThumbColor = Color.White.copy(alpha = 0.85f),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.18f)
                        )
                    )
                }
                Text(
                    "Meist etwas um $price Coins Wert; teure und sehr günstige Items sind seltener. " +
                        "Gleiche Items können mehrfach kommen (Emojis/Sticker stapeln; " +
                        "bereits besessene Hintergründe/Begleiter werden in Coins umgewandelt). " +
                        "Gekaufte Lootboxen bleiben gespeichert, bis du sie öffnest. " +
                        "Nicht erstattungsfähig — Details in den AGB.",
                    color = TextMuted.copy(alpha = 0.85f),
                    fontFamily = BodyFont,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        if (flashAnim > 0.05f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAnim * 0.85f))
            )
        }
    }

    revealedReward?.takeIf { phase == "reveal" }?.let { reward ->
        AlertDialog(
            onDismissRequest = { afterRevealDismiss() },
            containerColor = BgSoft,
            title = {
                Text("Lootbox geöffnet", fontFamily = DisplayFont, color = TextPrimary)
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(reward.emoji.ifBlank { "✨" }, fontSize = 56.sp)
                    Text(
                        "${kindLabel(reward.kind)} · ${resolveLabel(reward)}",
                        color = TextPrimary,
                        fontFamily = DisplayFont,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Shop-Preis: ${reward.shopPrice} Coins",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 14.sp
                    )
                    if (reward.duplicate && reward.coinsRefund > 0) {
                        Text(
                            "Schon vorhanden · +${reward.coinsRefund} Coins",
                            color = AccentRose,
                            fontFamily = DisplayFont,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    Text(
                        "Chance: ${"%.2f".format(reward.chancePercent)} %",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 13.sp
                    )
                    if (queue.isNotEmpty()) {
                        Text(
                            "Noch ${queue.size} ungeöffnet",
                            color = MaleBlue,
                            fontFamily = BodyFont,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { afterRevealDismiss() }) {
                    Text(
                        if (queue.isNotEmpty()) "Weiter" else "Ins Inventar",
                        color = AccentRose,
                        fontFamily = DisplayFont
                    )
                }
            }
        )
    }
}

@Composable
private fun ThemePreviewCard(themeId: String, emoji: String, label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.12f)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(18.dp))
    ) {
        // Wie auf der Profil-Leinwand (Himmel, Boden, Effekt)
        com.luv.couple.profile.ProfileThemeBackdrop(
            themeId = themeId,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(0.35f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 22.sp)
            Text(
                label,
                color = Color.White,
                fontFamily = DisplayFont,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun ShopGridCell(
    emoji: String,
    price: Int,
    ownedLabel: String?,
    themeId: String?,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (themeId == null) Modifier.background(BgSoft) else Modifier
            )
            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        if (themeId != null) {
            com.luv.couple.profile.ProfileThemeBackdrop(
                themeId = themeId,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            emoji,
            fontSize = 32.sp,
            modifier = Modifier.align(Alignment.Center)
        )
        Text(
            if (price <= 0) "frei" else "$price",
            color = if (themeId != null) Color.White else AccentRose,
            fontFamily = DisplayFont,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        )
        if (ownedLabel != null) {
            Text(
                ownedLabel,
                color = if (themeId != null) Color.White.copy(0.9f) else TextMuted,
                fontFamily = BodyFont,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun InventoryScreen(
    nickname: String,
    selectedTab: Int = 0,
    onTabChange: (Int) -> Unit = {},
    onOpenMarketplace: () -> Unit,
    onOpenItemShop: () -> Unit,
    onOpenProfileDesigner: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = LuvApp.instance.prefs
    val owned by prefs.ownedEmojisFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val ownedThemes by prefs.ownedThemesFlow.collectAsStateWithLifecycle(
        initialValue = listOf(ProfileCatalog.DEFAULT_THEME_ID)
    )
    val ownedStickersMap by prefs.ownedStickersFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val ownedPets by prefs.ownedPetsFlow.collectAsStateWithLifecycle(
        initialValue = listOf(ShopCatalog.DEFAULT_PET)
    )
    val equippedPet by prefs.equippedPetFlow.collectAsStateWithLifecycle(
        initialValue = ShopCatalog.DEFAULT_PET
    )
    val emojiBar by prefs.emojiBarFlow.collectAsStateWithLifecycle(
        initialValue = ShopCatalog.DEFAULT_BAR
    )
    val unseenKeys by prefs.inventoryUnseenFlow.collectAsStateWithLifecycle(
        initialValue = emptySet()
    )
    var sessionGlowKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var profile by remember {
        mutableStateOf(ProfileState(layout = ProfileCatalog.defaultLayout(nickname)))
    }
    var pendingAction by remember { mutableStateOf<ProfilePlaceAction?>(null) }
    var pendingBarEmoji by remember { mutableStateOf<String?>(null) }
    var barFullEmoji by remember { mutableStateOf<String?>(null) }
    var replacePickFor by remember { mutableStateOf<String?>(null) }
    var showBarEditor by remember { mutableStateOf(false) }
    var showGallery by remember { mutableStateOf(false) }

    LaunchedEffect(unseenKeys) {
        if (unseenKeys.isNotEmpty()) {
            sessionGlowKeys = sessionGlowKeys + unseenKeys
        }
    }

    val ownedStickersLatest = rememberUpdatedState(ownedStickersMap)
    val ownedThemesLatest = rememberUpdatedState(ownedThemes)
    val ownedPetsLatest = rememberUpdatedState(ownedPets)
    val ownedEmojisLatest = rememberUpdatedState(owned)

    DisposableEffect(Unit) {
        onDispose {
            val keys = buildSet {
                ownedStickersLatest.value.forEach { (k, v) ->
                    if (k.isNotBlank() && v > 0) add("stickers:$k")
                }
                ownedThemesLatest.value.forEach { id ->
                    id.trim().takeIf { it.isNotBlank() }?.let { add("themes:$it") }
                }
                ownedPetsLatest.value.forEach { pet ->
                    pet.trim().takeIf { it.isNotBlank() }?.let { add("pets:$it") }
                }
                ownedEmojisLatest.value.forEach { (k, v) ->
                    if (k.isNotBlank() && v > 0) add("emojis:$k")
                }
            }
            kotlinx.coroutines.MainScope().launch {
                prefs.markAllInventorySeen(keys)
                com.luv.couple.net.NotificationBadges.syncInventoryUnseenFromPrefs()
                com.luv.couple.net.NotificationBadges.syncAppBadge(context)
            }
        }
    }

    LaunchedEffect(nickname) {
        runCatching {
            val remote = LuvApiClient.fetchInventory()
            prefs.applyInventoryBag(
                emojis = remote.emojis,
                themes = remote.themes,
                stickers = remote.stickers,
                pets = remote.pets,
                equippedPet = remote.equippedPet
            )
            com.luv.couple.net.NotificationBadges.syncInventoryUnseenFromPrefs()
            com.luv.couple.net.NotificationBadges.syncAppBadge(context)
        }
        val json = runCatching { prefs.profileCanvasJson() }.getOrNull()
        profile = ProfileCatalog.decode(json, nickname)
        // Begleiter vom Profil → Prefs, nicht umgekehrt (sonst Flackern zum Default)
        val companion = profile.companionEmoji.trim()
        if (companion.isNotBlank()) {
            runCatching { prefs.setEquippedPet(companion) }
        }
    }

    fun confirmPlace(action: ProfilePlaceAction) {
        pendingAction = action
    }

    fun addEmojiToBar(emoji: String, replaceIndex: Int? = null) {
        scope.launch {
            val current = prefs.emojiBar().toMutableList()
            if (emoji in current) {
                Toast.makeText(context, "Schon in der Reaktionsleiste", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (replaceIndex != null && replaceIndex in current.indices) {
                current[replaceIndex] = emoji
            } else if (current.size < ShopCatalog.MAX_BAR) {
                current.add(emoji)
            } else {
                barFullEmoji = emoji
                return@launch
            }
            prefs.setEmojiBar(current)
            Toast.makeText(context, "$emoji in die Reaktionsleiste", Toast.LENGTH_SHORT).show()
        }
    }

    MenuBackdrop(includeNavigationBars = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 8.dp)
        ) {
            ProfileInventoryPanel(
                mode = InventoryPanelMode.Menu,
                ownedStickers = ownedStickersMap,
                ownedEmojis = owned,
                ownedThemes = ownedThemes,
                ownedPets = ownedPets,
                currentThemeId = profile.themeId,
                currentCompanion = equippedPet.ifBlank { profile.companionEmoji },
                hasGlass = false,
                hasBio = false,
                onTheme = { confirmPlace(ProfilePlaceAction.Theme(it.id)) },
                onSticker = { confirmPlace(ProfilePlaceAction.Sticker(it)) },
                onCompanion = { emoji ->
                    scope.launch {
                        runCatching {
                            val eq = LuvApiClient.equipPet(emoji)
                            prefs.setEquippedPet(eq)
                            profile = profile.copy(companionEmoji = eq)
                            Toast.makeText(
                                context,
                                "$eq ausgerüstet",
                                Toast.LENGTH_SHORT
                            ).show()
                        }.onFailure {
                            Toast.makeText(
                                context,
                                it.message ?: "Ausrüsten fehlgeschlagen",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onEmoji = { emoji ->
                    val bar = emojiBar
                    when {
                        emoji in bar -> Toast.makeText(
                            context,
                            "Schon in der Reaktionsleiste",
                            Toast.LENGTH_SHORT
                        ).show()
                        bar.size >= ShopCatalog.MAX_BAR -> barFullEmoji = emoji
                        else -> pendingBarEmoji = emoji
                    }
                },
                onOpenMarketplace = onOpenMarketplace,
                onOpenItemShop = onOpenItemShop,
                onOpenGallery = { showGallery = true },
                selectedTab = selectedTab,
                onTabChange = onTabChange,
                highlightKeys = sessionGlowKeys,
                unseenKeys = unseenKeys,
                onKindVisited = { kind ->
                    scope.launch {
                        prefs.markInventoryKindSeen(kind)
                        com.luv.couple.net.NotificationBadges.syncInventoryUnseenFromPrefs()
                        com.luv.couple.net.NotificationBadges.syncAppBadge(context)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                showCardChrome = true
            )
        }
    }

    if (showGallery) {
        Dialog(
            onDismissRequest = { showGallery = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            GalleryScreen(onClose = { showGallery = false })
        }
    }

    pendingBarEmoji?.let { emoji ->
        AlertDialog(
            onDismissRequest = { pendingBarEmoji = null },
            containerColor = BgSoft,
            title = {
                Text("Reaktionsleiste?", fontFamily = DisplayFont, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(emoji, fontSize = 36.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text(
                        "Dieses Emoji in deine Reaktionsleiste (Leinwand oben rechts) aufnehmen?",
                        fontFamily = BodyFont,
                        color = TextMuted
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        addEmojiToBar(emoji)
                        pendingBarEmoji = null
                    }
                ) {
                    Text("Ja, aufnehmen", color = AccentRose, fontFamily = DisplayFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBarEmoji = null }) {
                    Text("Abbrechen", color = TextMuted, fontFamily = BodyFont)
                }
            }
        )
    }

    barFullEmoji?.let { emoji ->
        AlertDialog(
            onDismissRequest = { barFullEmoji = null },
            containerColor = BgSoft,
            title = {
                Text("Leiste voll", fontFamily = DisplayFont, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(emoji, fontSize = 36.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text(
                        "Die Reaktionsleiste hat schon 8 Emojis. Entweder eines auf den Papierkorb ziehen oder ein bestehendes ersetzen.",
                        fontFamily = BodyFont,
                        color = TextMuted
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        replacePickFor = emoji
                        barFullEmoji = null
                    }
                ) {
                    Text("Ersetzen", color = AccentRose, fontFamily = DisplayFont)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            barFullEmoji = null
                            showBarEditor = true
                        }
                    ) {
                        Text("Papierkorb", color = TextMuted, fontFamily = BodyFont)
                    }
                    TextButton(onClick = { barFullEmoji = null }) {
                        Text("Abbrechen", color = TextMuted, fontFamily = BodyFont)
                    }
                }
            }
        )
    }

    replacePickFor?.let { newEmoji ->
        AlertDialog(
            onDismissRequest = { replacePickFor = null },
            containerColor = BgSoft,
            title = {
                Text("Welches ersetzen?", fontFamily = DisplayFont, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Tippe das Emoji an, das durch $newEmoji ersetzt werden soll.",
                        fontFamily = BodyFont,
                        color = TextMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        emojiBar.take(ShopCatalog.MAX_BAR).forEachIndexed { index, e ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BgDeep)
                                    .clickable {
                                        addEmojiToBar(newEmoji, replaceIndex = index)
                                        replacePickFor = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(e, fontSize = 22.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { replacePickFor = null }) {
                    Text("Abbrechen", color = TextMuted, fontFamily = BodyFont)
                }
            }
        )
    }

    if (showBarEditor) {
        EmojiBarEditorDialog(onDismiss = { showBarEditor = false })
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
