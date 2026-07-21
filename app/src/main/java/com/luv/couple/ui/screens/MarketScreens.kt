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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn
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
import com.luv.couple.data.AccountInfo
import com.luv.couple.net.AccountSession
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.PendingProfilePlace
import com.luv.couple.net.ProfilePlaceAction
import com.luv.couple.net.ShopPack
import com.luv.couple.profile.ProfileCatalog
import com.luv.couple.profile.ProfileElType
import com.luv.couple.profile.ProfileState
import com.luv.couple.shop.InventoryAvailability
import com.luv.couple.shop.ItemLabels
import com.luv.couple.shop.LiveShopCatalog
import com.luv.couple.shop.ShopCatalog
import com.luv.couple.shop.ShopEmoji
import com.luv.couple.shop.ShopEventRewards
import com.luv.couple.shop.ShopPet
import com.luv.couple.shop.ShopTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.text.style.TextOverflow
import com.luv.couple.shop.ShopRotation
import androidx.compose.ui.text.style.TextDecoration
import com.luv.couple.ui.ItemGlyph
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.MaleBlue
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.delay
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
    onBuyPack: (ShopPack) -> Unit,
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
            shopTabSeed = startShopTab.coerceIn(0, ShopCatalog.ITEM_SHOP_TAB_LABELS.lastIndex)
        }
        panel = target
        deepLinked = onLeaveDeepLink != null
        onStartPanelConsumed()
    }
    var showHubLootbox by remember { mutableStateOf(false) }
    var hubLootBusy by remember { mutableStateOf<String?>(null) }
    var hubLootRefresh by remember { mutableIntStateOf(0) }
    val hubAccount by AccountSession.account.collectAsStateWithLifecycle()
    when (panel) {
        MarketPanel.Hub -> {
            MarketHub(
                packs = packs,
                lootRefreshKey = hubLootRefresh,
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
                },
                onOpenLootbox = {
                    if (!economyUnlocked) onRequireGoogle()
                    else showHubLootbox = true
                }
            )
            if (showHubLootbox) {
                AlertDialog(
                    onDismissRequest = {
                        showHubLootbox = false
                        hubLootRefresh++
                    },
                    containerColor = BgSoft,
                    title = {
                        Text("Lootbox", fontFamily = DisplayFont, color = TextPrimary, fontSize = 22.sp)
                    },
                    text = {
                        Box(modifier = Modifier.fillMaxWidth().height(520.dp)) {
                            LootboxTab(
                                coins = hubAccount?.coins ?: 0,
                                busy = hubLootBusy != null,
                                onBusy = { hubLootBusy = it },
                                onRefresh = { onRefreshInventory() },
                                economyUnlocked = economyUnlocked,
                                onRequireGoogle = onRequireGoogle
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showHubLootbox = false
                            hubLootRefresh++
                        }) {
                            Text("Schließen", color = AccentRose, fontFamily = DisplayFont)
                        }
                    }
                )
            }
        }
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
        MarketPanel.ItemShop -> {
            var shopSearchOpen by remember { mutableStateOf(false) }
            var shopSearchQuery by remember { mutableStateOf("") }
            MarketExpandShell(
                title = "Itemshop",
                onBack = { backFromPanel() },
                trailing = {
                    ShopSearchIconButton(
                        expanded = shopSearchOpen,
                        onClick = {
                            shopSearchOpen = !shopSearchOpen
                            if (!shopSearchOpen) shopSearchQuery = ""
                        }
                    )
                },
                belowTitle = {
                    if (shopSearchOpen) {
                        ShopSearchField(
                            query = shopSearchQuery,
                            onQueryChange = { shopSearchQuery = it },
                            placeholder = "Itemshop filtern…"
                        )
                    }
                }
            ) {
                key(shopTabSeed) {
                    ItemShopContent(
                        onRefreshInventory = onRefreshInventory,
                        initialTab = shopTabSeed,
                        economyUnlocked = economyUnlocked,
                        onRequireGoogle = onRequireGoogle,
                        externalSearchQuery = shopSearchQuery,
                        searchManagedExternally = true
                    )
                }
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
/** Letzter Markt-Hub — sofort anzeigen, während neu geladen wird. */
private object MarketHubCache {
    @Volatile
    var latest: LuvApiClient.MarketHubData? = null
}

@Composable
private fun MarketHub(
    packs: List<ShopPack>,
    lootRefreshKey: Int = 0,
    onOpenMarketplace: () -> Unit,
    onOpenItemShop: () -> Unit,
    onOpenCoinShop: () -> Unit,
    onOpenLootbox: () -> Unit
) {
    var hub by remember { mutableStateOf(MarketHubCache.latest) }
    var lootPending by remember { mutableIntStateOf(0) }
    val marketAlert by com.luv.couple.net.NotificationBadges.hasMarketDot.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        val fresh = runCatching { LuvApiClient.fetchMarketHub() }.getOrNull()
        if (fresh != null) {
            MarketHubCache.latest = fresh
            hub = fresh
        }
        com.luv.couple.net.NotificationBadges.refreshPendingSales()
    }
    LaunchedEffect(lootRefreshKey) {
        lootPending = runCatching { LuvApiClient.pendingLootboxes().size }.getOrDefault(0)
    }
    val marketPreviews = hub?.marketNewest.orEmpty()
    val shopPreviews = hub?.shopTop.orEmpty()
    val offerPack = remember(packs, hub) {
        packs.firstOrNull { it.isOffer || it.onceOnly }
            ?: packs.firstOrNull {
                it.amountEur.replace(',', '.').trim().startsWith("0.99")
            }
            ?: hub?.coinNewest?.firstOrNull()?.let { preview ->
                packs.firstOrNull { it.id == preview.packId }
            }
    }
    val coinPreview = remember(offerPack, hub) {
        offerPack?.let { pack ->
            LuvApiClient.MarketHubPreview(
                emoji = "🪙",
                label = ShopCatalog.playfulPackTitle(pack),
                detail = "${pack.amountEur.replace('.', ',')} € · ${pack.coins}",
                packId = pack.id,
                packCoins = pack.coins
            )
        } ?: hub?.coinNewest?.firstOrNull()
    }
    val lootPrice = ShopCatalog.LOOTBOX_PRICE_COINS
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
                        .weight(1.15f)
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
                        .weight(1.15f)
                        .fillMaxWidth(),
                    title = "Itemshop",
                    badge = "Meistgekauft",
                    brush = Brush.linearGradient(listOf(Color(0xFF3A2438), Color(0xFF241828))),
                    previews = shopPreviews,
                    emptyHint = "Beliebte Items laden…",
                    onClick = onOpenItemShop
                )
                Row(
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    MarketTile(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        title = "Coinshop",
                        badge = "",
                        brush = Brush.linearGradient(listOf(Color(0xFF3A3020), Color(0xFF241C12))),
                        previews = listOfNotNull(coinPreview),
                        emptyHint = "Bald verfügbar",
                        onClick = onOpenCoinShop
                    )
                    MarketLootboxTile(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        pendingCount = lootPending,
                        priceCoins = lootPrice,
                        onClick = onOpenLootbox
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketLootboxTile(
    modifier: Modifier,
    pendingCount: Int,
    priceCoins: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF243848), Color(0xFF152028))))
            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Lootbox",
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 20.sp,
                maxLines = 1
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🎁", fontSize = 44.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (pendingCount > 0) {
                        if (pendingCount == 1) "1 ungeöffnet" else "$pendingCount ungeöffnet"
                    } else {
                        "Keine ungeöffnet"
                    },
                    color = if (pendingCount > 0) MaleBlue else TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    "$priceCoins Coins",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 12.sp
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
                if (badge.isNotBlank()) {
                    Text(
                        badge,
                        color = if (alertDot) AccentRose else TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
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
            ItemGlyph(id = preview.emoji, fontSize = 28.sp)
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
    trailing: (@Composable () -> Unit)? = null,
    belowTitle: (@Composable () -> Unit)? = null,
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
                if (trailing != null) {
                    trailing()
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }
            if (belowTitle != null) {
                Spacer(modifier = Modifier.height(10.dp))
                belowTitle()
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
    onBuyPack: (ShopPack) -> Unit,
    economyUnlocked: Boolean = true,
    onRequireGoogle: () -> Unit = {}
) {
    val account by AccountSession.account.collectAsStateWithLifecycle()
    var pendingPack by remember { mutableStateOf<ShopPack?>(null) }
    // Nur das 0,99-€-Angebot — keine weiteren Pakete / kein „Angebote“-Titel
    val offerPack = remember(packs) {
        packs.firstOrNull { it.isOffer || it.onceOnly }
            ?: packs.firstOrNull {
                it.amountEur.replace(',', '.').trim().startsWith("0.99")
            }
    }
    fun requestBuy(pack: ShopPack) {
        if (!economyUnlocked) {
            onRequireGoogle()
            return
        }
        pendingPack = pack
    }
    fun packPriceLabel(pack: ShopPack): String {
        pack.displayPrice?.takeIf { it.isNotBlank() }?.let { return it }
        return "${pack.amountEur.replace('.', ',')} €"
    }
    pendingPack?.let { pack ->
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
                        "${pack.coins} Coins · ${packPriceLabel(pack)}",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 14.sp
                    )
                    Text(
                        "Zahlung über Google Play.",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 13.sp
                    )
                    if (pack.onceOnly) {
                        Text(
                            "Aktionsangebot — nur einmal kaufbar.",
                            color = AccentRose,
                            fontFamily = BodyFont,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingPack = null
                    onBuyPack(pack)
                }) {
                    Text("Mit Google Play kaufen", color = AccentRose, fontFamily = DisplayFont)
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
            val pack = offerPack
            if (pack != null) {
                CoinPackCard(
                    pack = pack,
                    onBuy = { requestBuy(pack) }
                )
            } else {
                EmptyMarketCard(
                    title = "Kein Angebot",
                    body = "Aktuell ist kein Coin-Angebot verfügbar."
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
                    pack.displayPrice?.takeIf { it.isNotBlank() }
                        ?: "${pack.amountEur.replace('.', ',')} €",
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
    onRequireGoogle: () -> Unit = {},
    externalSearchQuery: String = "",
    searchManagedExternally: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = LuvApp.instance.prefs
    val account by AccountSession.account.collectAsStateWithLifecycle()
    var tab by remember {
        mutableIntStateOf(initialTab.coerceIn(0, ShopCatalog.ITEM_SHOP_TAB_LABELS.lastIndex))
    }
    var purchaseFlash by remember { mutableStateOf<Pair<String, Int>?>(null) }
    LaunchedEffect(purchaseFlash) {
        if (purchaseFlash == null) return@LaunchedEffect
        delay(1000)
        purchaseFlash = null
    }
    val ownedEmojis by prefs.ownedEmojisFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val ownedThemes by prefs.ownedThemesFlow.collectAsStateWithLifecycle(
        initialValue = listOf(ProfileCatalog.DEFAULT_THEME_ID)
    )
    val ownedStickers by prefs.ownedStickersFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val ownedPets by prefs.ownedPetsFlow.collectAsStateWithLifecycle(
        initialValue = listOf(ShopCatalog.DEFAULT_PET)
    )
    val equippedPet by prefs.equippedPetFlow.collectAsStateWithLifecycle(
        initialValue = ShopCatalog.DEFAULT_PET
    )
    var busyKey by remember { mutableStateOf<String?>(null) }
    var pendingBuy by remember { mutableStateOf<ShopPendingBuy?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var localSearchQuery by remember { mutableStateOf("") }
    val searchQuery = if (searchManagedExternally) externalSearchQuery else localSearchQuery
    var catalogTick by remember { mutableIntStateOf(0) }
    fun openBuy(pending: ShopPendingBuy) {
        if (!economyUnlocked) {
            onRequireGoogle()
            return
        }
        pendingBuy = pending
    }
    suspend fun reloadShopAndInventory(includeCatalog: Boolean = true) {
        if (includeCatalog) {
            runCatching { LuvApiClient.fetchShopCatalog() }
            catalogTick++
        }
        runCatching {
            val remote = LuvApiClient.fetchInventory()
            prefs.applyInventorySnap(
                emojis = remote.emojis,
                themes = remote.themes,
                stickers = remote.stickers,
                pets = remote.pets,
                equippedPet = remote.equippedPet
            )
        }
        // Kein zweites onRefreshInventory — Inventar ist schon gepatcht
    }

    fun optimisticSpend(price: Int): AccountInfo? {
        val prev = account ?: return null
        if (price <= 0) return prev
        AccountSession.setAccount(prev.copy(coins = (prev.coins - price).coerceAtLeast(0)))
        return prev
    }
    LaunchedEffect(Unit) { reloadShopAndInventory() }
    @Suppress("UNUSED_VARIABLE")
    val catalogRev = catalogTick
    pendingBuy?.let { pending ->
        val previewId = when (pending) {
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
            is ShopPendingBuy.Emoji -> ItemLabels.emojiLabel(pending.item.emoji)
            is ShopPendingBuy.Theme -> ItemLabels.themeLabel(pending.item.id)
            is ShopPendingBuy.Sticker -> ItemLabels.stickerLabel(pending.item.emoji)
            // Immer ItemLabels — sonst gewinnt der lokale Hardcode („Tiger“) über Admin-Namen
            is ShopPendingBuy.Pet -> ItemLabels.petLabel(pending.item.emoji)
        }
        val kindLabel = when (pending) {
            is ShopPendingBuy.Emoji -> "dieses Emoji „$titleLabel“"
            is ShopPendingBuy.Theme -> "den Hintergrund „$titleLabel“"
            is ShopPendingBuy.Sticker -> "diesen Sticker „$titleLabel“"
            is ShopPendingBuy.Pet -> "den Begleiter „$titleLabel“"
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
                            label = ItemLabels.themeLabel(pending.item.id)
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
                                ItemGlyph(id = previewId, fontSize = 72.sp)
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
                        val prevAccount = optimisticSpend(price)
                        val prevEmojis = ownedEmojis
                        val prevThemes = ownedThemes
                        val prevStickers = ownedStickers
                        val prevPets = ownedPets
                        // Sofort lokale Inventar-Anzeige
                        scope.launch {
                            when (pending) {
                                is ShopPendingBuy.Emoji -> {
                                    val next = prevEmojis.toMutableMap()
                                    next[pending.item.emoji] = (next[pending.item.emoji] ?: 0) + 1
                                    prefs.setOwnedEmojis(next)
                                }
                                is ShopPendingBuy.Theme -> {
                                    prefs.applyInventorySnap(
                                        emojis = prevEmojis,
                                        themes = (prevThemes + pending.item.id).distinct(),
                                        stickers = prevStickers,
                                        pets = prevPets,
                                        equippedPet = equippedPet
                                    )
                                }
                                is ShopPendingBuy.Sticker -> {
                                    val next = prevStickers.toMutableMap()
                                    next[pending.item.emoji] = (next[pending.item.emoji] ?: 0) + 1
                                    prefs.applyInventorySnap(
                                        emojis = prevEmojis,
                                        themes = prevThemes,
                                        stickers = next,
                                        pets = prevPets,
                                        equippedPet = equippedPet
                                    )
                                }
                                is ShopPendingBuy.Pet -> {
                                    prefs.applyInventorySnap(
                                        emojis = prevEmojis,
                                        themes = prevThemes,
                                        stickers = prevStickers,
                                        pets = (prevPets + pending.item.emoji).distinct(),
                                        equippedPet = equippedPet
                                    )
                                }
                            }
                        }
                        purchaseFlash = titleLabel to price
                        pendingBuy = null
                        busyKey = null
                        scope.launch {
                            runCatching {
                                when (pending) {
                                    is ShopPendingBuy.Emoji -> LuvApiClient.buyEmoji(pending.item.emoji)
                                    is ShopPendingBuy.Theme -> LuvApiClient.buyTheme(pending.item.id)
                                    is ShopPendingBuy.Sticker -> LuvApiClient.buySticker(pending.item.emoji)
                                    is ShopPendingBuy.Pet -> LuvApiClient.buyPet(pending.item.emoji)
                                }
                                // Nur Inventar nachziehen — kein voller Katalog
                                reloadShopAndInventory(includeCatalog = false)
                            }.onFailure {
                                prevAccount?.let { AccountSession.setAccount(it) }
                                scope.launch {
                                    prefs.applyInventorySnap(
                                        emojis = prevEmojis,
                                        themes = prevThemes,
                                        stickers = prevStickers,
                                        pets = prevPets,
                                        equippedPet = equippedPet
                                    )
                                }
                                Toast.makeText(
                                    context,
                                    it.message ?: "Kauf fehlgeschlagen",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
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
    val cycle = remember(catalogTick) { ShopRotation.cycleInfo() }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val scale = when {
            maxWidth < 360.dp -> 0.92f
            maxWidth > 600.dp -> 1.08f
            else -> 1f
        }
        fun s(v: androidx.compose.ui.unit.Dp) = v * scale
        Column(modifier = Modifier.fillMaxSize()) {
            // Header atmosphere
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(s(18.dp)))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                AccentRose.copy(0.18f),
                                BgSoft.copy(0.55f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = s(14.dp), vertical = s(12.dp))
            ) {
                Text(
                    "${account?.coins ?: 0} Coins",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = (15f * scale).sp
                )
            }
            Spacer(modifier = Modifier.height(s(10.dp)))
            // Featured preview — Demnächst immer, Pool = voller lokaler Katalog
            run {
                val stickersAll = remember(catalogTick) { LiveShopCatalog.stickers() }
                val themesAll = remember(catalogTick) { LiveShopCatalog.themes() }
                val petsAll = remember(catalogTick) { LiveShopCatalog.pets() }
                val emojisAll = remember(catalogTick) { LiveShopCatalog.emojis() }
                val stickersPool = remember(catalogTick) {
                    ShopEventRewards.filterShopList(ShopCatalog.STICKERS, "stickers") { it.emoji }
                }
                val themesPool = remember(catalogTick) {
                    ShopEventRewards.filterShopList(
                        ShopCatalog.THEMES.filter { it.priceCoins > 0 || it.id == "meadow" },
                        "themes"
                    ) { it.id }
                }
                val petsPool = remember(catalogTick) {
                    ShopEventRewards.filterShopList(ShopCatalog.PETS, "pets") { it.emoji }
                }
                val emojisPool = remember(catalogTick) {
                    ShopEventRewards.filterShopList(ShopCatalog.EMOJIS, "emojis") { it.emoji }
                }
                when (tab) {
                    0 -> {
                        val rotated = remember(catalogTick, stickersAll, stickersPool, cycle.epoch) {
                            ShopRotation.rotateCatalogDetailed(
                                stickersAll,
                                cycle.epoch,
                                idOf = { it.emoji },
                                priceOf = { it.priceCoins },
                                remainingOf = { it.remainingMs },
                                bucketSize = 12,
                                previewPool = stickersPool,
                            )
                        }
                        val preview = rotated.preview
                        val days = rotated.previewDays
                        if (preview.isNotEmpty()) {
                            ShopFeaturedRow(
                                scale = scale,
                                leftBadge = days.getOrNull(0)?.let { ShopRotation.daysUntilLabel(it) }.orEmpty(),
                                rightBadge = days.getOrNull(1)?.let { ShopRotation.daysUntilLabel(it) }.orEmpty(),
                                leftEmoji = preview.getOrNull(0)?.emoji,
                                leftName = preview.getOrNull(0)?.let {
                                    it.label.ifBlank { ItemLabels.stickerLabel(it.emoji) }
                                },
                                leftPrice = preview.getOrNull(0)?.priceCoins,
                                leftThemeId = null,
                                rightEmoji = preview.getOrNull(1)?.emoji,
                                rightName = preview.getOrNull(1)?.let {
                                    it.label.ifBlank { ItemLabels.stickerLabel(it.emoji) }
                                },
                                rightPrice = preview.getOrNull(1)?.priceCoins,
                                rightThemeId = null
                            )
                        }
                    }
                    1 -> {
                        val rotated = remember(catalogTick, themesAll, themesPool, cycle.epoch) {
                            ShopRotation.rotateCatalogDetailed(
                                themesAll,
                                cycle.epoch,
                                idOf = { it.id },
                                priceOf = { it.priceCoins },
                                remainingOf = { it.remainingMs },
                                bucketSize = 9,
                                previewPool = themesPool,
                            )
                        }
                        val preview = rotated.preview
                        val days = rotated.previewDays
                        if (preview.isNotEmpty()) {
                            ShopFeaturedRow(
                                scale = scale,
                                leftBadge = days.getOrNull(0)?.let { ShopRotation.daysUntilLabel(it) }.orEmpty(),
                                rightBadge = days.getOrNull(1)?.let { ShopRotation.daysUntilLabel(it) }.orEmpty(),
                                leftEmoji = preview.getOrNull(0)?.emoji,
                                leftName = preview.getOrNull(0)?.label,
                                leftPrice = preview.getOrNull(0)?.priceCoins,
                                leftThemeId = preview.getOrNull(0)?.id,
                                rightEmoji = preview.getOrNull(1)?.emoji,
                                rightName = preview.getOrNull(1)?.label,
                                rightPrice = preview.getOrNull(1)?.priceCoins,
                                rightThemeId = preview.getOrNull(1)?.id
                            )
                        }
                    }
                    2 -> {
                        val rotated = remember(catalogTick, petsAll, petsPool, cycle.epoch) {
                            ShopRotation.rotateCatalogDetailed(
                                petsAll,
                                cycle.epoch,
                                idOf = { it.emoji },
                                priceOf = { it.priceCoins },
                                remainingOf = { it.remainingMs },
                                bucketSize = 12,
                                previewPool = petsPool,
                            )
                        }
                        val preview = rotated.preview
                        val days = rotated.previewDays
                        if (preview.isNotEmpty()) {
                            ShopFeaturedRow(
                                scale = scale,
                                leftBadge = days.getOrNull(0)?.let { ShopRotation.daysUntilLabel(it) }.orEmpty(),
                                rightBadge = days.getOrNull(1)?.let { ShopRotation.daysUntilLabel(it) }.orEmpty(),
                                leftEmoji = preview.getOrNull(0)?.emoji,
                                leftName = preview.getOrNull(0)?.let {
                                    it.label.ifBlank { ItemLabels.petLabel(it.emoji) }
                                },
                                leftPrice = preview.getOrNull(0)?.priceCoins,
                                leftThemeId = null,
                                rightEmoji = preview.getOrNull(1)?.emoji,
                                rightName = preview.getOrNull(1)?.let {
                                    it.label.ifBlank { ItemLabels.petLabel(it.emoji) }
                                },
                                rightPrice = preview.getOrNull(1)?.priceCoins,
                                rightThemeId = null
                            )
                        }
                    }
                    else -> {
                        val rotated = remember(catalogTick, emojisAll, emojisPool, cycle.epoch) {
                            ShopRotation.rotateCatalogDetailed(
                                emojisAll,
                                cycle.epoch,
                                idOf = { it.emoji },
                                priceOf = { it.priceCoins },
                                remainingOf = { it.remainingMs },
                                bucketSize = 15,
                                previewPool = emojisPool,
                            )
                        }
                        val preview = rotated.preview
                        val days = rotated.previewDays
                        if (preview.isNotEmpty()) {
                            ShopFeaturedRow(
                                scale = scale,
                                leftBadge = days.getOrNull(0)?.let { ShopRotation.daysUntilLabel(it) }.orEmpty(),
                                rightBadge = days.getOrNull(1)?.let { ShopRotation.daysUntilLabel(it) }.orEmpty(),
                                leftEmoji = preview.getOrNull(0)?.emoji,
                                leftName = preview.getOrNull(0)?.let {
                                    it.label.ifBlank { ItemLabels.emojiLabel(it.emoji) }
                                },
                                leftPrice = preview.getOrNull(0)?.priceCoins,
                                leftThemeId = null,
                                rightEmoji = preview.getOrNull(1)?.emoji,
                                rightName = preview.getOrNull(1)?.let {
                                    it.label.ifBlank { ItemLabels.emojiLabel(it.emoji) }
                                },
                                rightPrice = preview.getOrNull(1)?.priceCoins,
                                rightThemeId = null
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(s(8.dp)))
            // Segment-Tabs: volle Labels (scrollbar auf schmalen Geräten)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(s(14.dp)))
                    .background(BgSoft.copy(0.85f))
                    .horizontalScroll(rememberScrollState())
                    .padding(s(4.dp)),
                horizontalArrangement = Arrangement.spacedBy(s(4.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShopCatalog.ITEM_SHOP_TAB_LABELS.forEachIndexed { index, label ->
                    val active = tab == index
                    val indicator by animateFloatAsState(
                        targetValue = if (active) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
                        label = "shopTab-$index"
                    )
                    Box(
                        modifier = Modifier
                            .widthIn(min = s(76.dp))
                            .clip(RoundedCornerShape(s(11.dp)))
                            .background(AccentRose.copy(0.22f * indicator))
                            .clickable { tab = index }
                            .padding(vertical = s(9.dp), horizontal = s(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                label,
                                color = TextPrimary,
                                fontFamily = if (active) DisplayFont else BodyFont,
                                fontSize = (12f * scale).coerceIn(11f, 14f).sp,
                                softWrap = false,
                                maxLines = 1,
                                overflow = TextOverflow.Visible,
                                textAlign = TextAlign.Center
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = s(3.dp))
                                    .height(s(2.dp))
                                    .fillMaxWidth(0.45f)
                                    .graphicsLayer { alpha = indicator }
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(AccentRose)
                            )
                        }
                    }
                }
            }
            if (!searchManagedExternally) {
                Spacer(modifier = Modifier.height(s(10.dp)))
                ShopSearchToggleRow(
                    expanded = searchOpen,
                    query = localSearchQuery,
                    onExpandedChange = { searchOpen = it },
                    onQueryChange = { localSearchQuery = it }
                )
                Spacer(modifier = Modifier.height(s(10.dp)))
            } else {
                Spacer(modifier = Modifier.height(s(8.dp)))
            }
            val q = searchQuery
            val stickers = remember(catalogTick, q, cycle.epoch) {
                val filtered = LiveShopCatalog.stickers().filter {
                    LiveShopCatalog.matchesQuery(
                        q,
                        it.emoji,
                        label = it.label.ifBlank { ItemLabels.stickerLabel(it.emoji) },
                        searchText = it.searchText
                    )
                }
                ShopRotation.rotateCatalog(
                    filtered,
                    cycle.epoch,
                    idOf = { it.emoji },
                    priceOf = { it.priceCoins },
                    remainingOf = { it.remainingMs },
                    withRemaining = { item, rem -> item.copy(remainingMs = rem) },
                    skipRotation = q.isNotBlank()
                ).first
            }
            val themes = remember(catalogTick, q, cycle.epoch) {
                val filtered = LiveShopCatalog.themes().filter {
                    LiveShopCatalog.matchesQuery(q, it.emoji, it.label, it.searchText)
                }
                ShopRotation.rotateCatalog(
                    filtered,
                    cycle.epoch,
                    idOf = { it.id },
                    priceOf = { it.priceCoins },
                    remainingOf = { it.remainingMs },
                    withRemaining = { item, rem -> item.copy(remainingMs = rem) },
                    skipRotation = q.isNotBlank()
                ).first
            }
            val pets = remember(catalogTick, q, cycle.epoch) {
                val filtered = LiveShopCatalog.pets().filter {
                    LiveShopCatalog.matchesQuery(q, it.emoji, it.label, it.searchText)
                }
                ShopRotation.rotateCatalog(
                    filtered,
                    cycle.epoch,
                    idOf = { it.emoji },
                    priceOf = { it.priceCoins },
                    remainingOf = { it.remainingMs },
                    withRemaining = { item, rem -> item.copy(remainingMs = rem) },
                    skipRotation = q.isNotBlank()
                ).first
            }
            val emojis = remember(catalogTick, q, cycle.epoch) {
                val filtered = LiveShopCatalog.emojis().filter {
                    LiveShopCatalog.matchesQuery(
                        q,
                        it.emoji,
                        label = it.label.ifBlank { ItemLabels.emojiLabel(it.emoji) },
                        searchText = it.searchText
                    )
                }
                ShopRotation.rotateCatalog(
                    filtered,
                    cycle.epoch,
                    idOf = { it.emoji },
                    priceOf = { it.priceCoins },
                    remainingOf = { it.remainingMs },
                    withRemaining = { item, rem -> item.copy(remainingMs = rem) },
                    skipRotation = q.isNotBlank()
                ).first
            }
            when (tab) {
                0 -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(s(10.dp)),
                    verticalArrangement = Arrangement.spacedBy(s(10.dp))
                ) {
                    items(stickers, key = { "s-${it.emoji}" }) { item ->
                        val count = ownedStickers[item.emoji] ?: 0
                        ShopGridCell(
                            emoji = item.emoji,
                            name = item.label.ifBlank { ItemLabels.stickerLabel(item.emoji) },
                            price = item.priceCoins,
                            compareAtPrice = item.compareAtPrice,
                            remainingMs = item.remainingMs,
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
                    horizontalArrangement = Arrangement.spacedBy(s(10.dp)),
                    verticalArrangement = Arrangement.spacedBy(s(10.dp))
                ) {
                    items(themes, key = { "t-${it.id}" }) { item ->
                        val have = item.id in ownedThemes
                        ShopGridCell(
                            emoji = item.emoji,
                            name = item.label,
                            price = item.priceCoins,
                            compareAtPrice = item.compareAtPrice,
                            remainingMs = item.remainingMs,
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
                    horizontalArrangement = Arrangement.spacedBy(s(10.dp)),
                    verticalArrangement = Arrangement.spacedBy(s(10.dp))
                ) {
                    items(pets, key = { it.emoji }) { item ->
                        val have = item.emoji in ownedPets
                        ShopGridCell(
                            emoji = item.emoji,
                            name = item.label,
                            price = item.priceCoins,
                            compareAtPrice = item.compareAtPrice,
                            remainingMs = item.remainingMs,
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
                    horizontalArrangement = Arrangement.spacedBy(s(10.dp)),
                    verticalArrangement = Arrangement.spacedBy(s(10.dp))
                ) {
                    items(emojis, key = { it.emoji }) { item ->
                        val count = ownedEmojis[item.emoji] ?: 0
                        ShopGridCell(
                            emoji = item.emoji,
                            name = item.label.ifBlank { ItemLabels.emojiLabel(item.emoji) },
                            price = item.priceCoins,
                            compareAtPrice = item.compareAtPrice,
                            remainingMs = item.remainingMs,
                            ownedLabel = if (count > 0) "×$count" else null,
                            themeId = null,
                            enabled = busyKey == null,
                            onClick = { openBuy(ShopPendingBuy.Emoji(item)) }
                        )
                    }
                }
            }
            purchaseFlash?.let { (title, pricePaid) ->
                PurchaseFlashPopup(
                    title = title,
                    priceCoins = pricePaid
                )
            }
        }
    }
}

@Composable
internal fun PurchaseFlashPopup(
    title: String,
    priceCoins: Int,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 36.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(BgSoft)
                .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(20.dp))
                .padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
            Text(
                if (priceCoins <= 0) "Kostenlos erhalten" else "Gekauft · −$priceCoins Coins",
                color = AccentRose,
                fontFamily = BodyFont,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
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
    /** Sofort sichtbar nach Kauf — nicht erst nach Server-Antwort (~sekunden). */
    var displayPending by remember { mutableIntStateOf(0) }
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
    fun resolveLabel(result: com.luv.couple.net.LootboxResult): String {
        val labeled = ItemLabels.forKind(result.kind, result.itemId)
        if (!ItemLabels.looksLikeRawId(labeled)) return labeled
        val fb = result.label.trim()
        if (fb.isNotEmpty() && !ItemLabels.looksLikeRawId(fb)) return fb
        val emo = result.emoji.trim()
        if (emo.isNotEmpty() && !ItemLabels.looksLikeRawId(emo)) return emo
        return ItemLabels.genericLabel(result.itemId)
    }
    fun beginOpening(next: com.luv.couple.net.LootboxResult, rest: List<com.luv.couple.net.LootboxResult>) {
        activePendingId = next.pendingId
        queue = rest
        displayPending = 1 + rest.size
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
        val total = price * qty
        if (coins < total) {
            Toast.makeText(context, "Nicht genug Coins", Toast.LENGTH_SHORT).show()
            return
        }
        val prevAccount = AccountSession.account.value
        if (prevAccount != null && total > 0) {
            AccountSession.setAccount(
                prevAccount.copy(coins = (prevAccount.coins - total).coerceAtLeast(0))
            )
        }
        // Sofort „Noch X Lootbox“ — API darf danach noch laden
        val beforeOpt = displayPending
        displayPending = (displayPending + qty).coerceAtLeast(qty)
        onBusy("lootbox")
        scope.launch {
            runCatching { LuvApiClient.buyLootbox(qty) }
                .onSuccess { result ->
                    val all = result.pending.ifEmpty { result.purchased }
                    val first = all.firstOrNull()
                    if (first == null) {
                        displayPending = beforeOpt
                        Toast.makeText(context, "Kauf fehlgeschlagen", Toast.LENGTH_SHORT).show()
                    } else {
                        beginOpening(first, all.drop(1))
                        val n = all.size
                        Toast.makeText(
                            context,
                            if (n == 1) "Lootbox gekauft" else "$n Lootboxen gekauft",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    onBusy(null)
                }
                .onFailure {
                    displayPending = beforeOpt
                    prevAccount?.let { AccountSession.setAccount(it) }
                    onBusy(null)
                    Toast.makeText(
                        context,
                        it.message ?: "Kauf fehlgeschlagen",
                        Toast.LENGTH_SHORT
                    ).show()
                }
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
                    displayPending = queue.size + 1
                    onBusy(null)
                    // Inventar im Hintergrund — Chance/Reveal sofort zeigen
                    scope.launch { runCatching { onRefresh() } }
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
            displayPending = 0
            scope.launch {
                runCatching { LuvApiClient.pendingLootboxes() }
                    .onSuccess { list ->
                        displayPending = list.size
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
                displayPending = list.size
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
                val waiting = maxOf(
                    displayPending,
                    queue.size + if (phase == "tapping" || phase == "reveal") 1 else 0
                )
                if (waiting > 0) {
                    Text(
                        when {
                            phase == "idle" && busy ->
                                if (waiting == 1) "Noch 1 Lootbox…" else "Noch $waiting Lootboxen…"
                            phase == "idle" ->
                                if (waiting == 1) "1 ungeöffnet" else "$waiting ungeöffnet"
                            waiting == 1 -> "Noch 1 Lootbox"
                            else -> "Noch $waiting Lootboxen"
                        },
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
                    ItemGlyph(id = reward.emoji.ifBlank { "✨" }, fontSize = 56.sp)
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
            ItemGlyph(id = emoji, fontSize = 22.sp)
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
private fun ShopFeaturedRow(
    scale: Float,
    leftBadge: String,
    rightBadge: String,
    leftEmoji: String?,
    leftName: String?,
    leftPrice: Int?,
    leftThemeId: String?,
    rightEmoji: String?,
    rightName: String?,
    rightPrice: Int?,
    rightThemeId: String?,
) {
    fun s(v: androidx.compose.ui.unit.Dp) = v * scale
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Demnächst im Shop",
            color = TextMuted,
            fontFamily = BodyFont,
            fontSize = (11f * scale).sp,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(s(6.dp)))
        AnimatedContent(
            targetState = "${leftEmoji ?: ""}|${rightEmoji ?: ""}|$leftBadge|$rightBadge",
            transitionSpec = {
                (fadeIn() + slideInHorizontally { it / 8 }) togetherWith
                    (fadeOut() + slideOutHorizontally { -it / 8 })
            },
            label = "shopFeatured"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(s(8.dp))
            ) {
                ShopFeaturedCard(
                    modifier = Modifier.weight(1f),
                    scale = scale,
                    emoji = leftEmoji,
                    name = leftName,
                    price = leftPrice,
                    themeId = leftThemeId,
                    badge = leftBadge
                )
                ShopFeaturedCard(
                    modifier = Modifier.weight(1f),
                    scale = scale,
                    emoji = rightEmoji,
                    name = rightName,
                    price = rightPrice,
                    themeId = rightThemeId,
                    badge = rightBadge
                )
            }
        }
    }
}

@Composable
private fun ShopFeaturedCard(
    modifier: Modifier,
    scale: Float,
    emoji: String?,
    name: String?,
    price: Int?,
    themeId: String?,
    badge: String,
) {
    fun s(v: androidx.compose.ui.unit.Dp) = v * scale
    Row(
        modifier = modifier
            .height(s(64.dp))
            .clip(RoundedCornerShape(s(12.dp)))
            .then(
                if (themeId == null) {
                    Modifier.background(BgSoft.copy(0.9f))
                } else Modifier
            )
            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(s(12.dp)))
            .padding(horizontal = s(8.dp), vertical = s(6.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s(8.dp))
    ) {
        Box(
            modifier = Modifier
                .size(s(40.dp))
                .clip(RoundedCornerShape(s(10.dp))),
            contentAlignment = Alignment.Center
        ) {
            if (themeId != null) {
                com.luv.couple.profile.ProfileThemeBackdrop(
                    themeId = themeId,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(0.25f))
                )
            }
            if (emoji != null) {
                ItemGlyph(id = emoji, fontSize = (22f * scale).sp)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                badge,
                color = if (themeId != null) Color(0xFFFFD54F) else AccentRose,
                fontFamily = BodyFont,
                fontSize = (9f * scale).sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                name.orEmpty(),
                color = if (themeId != null) Color.White else TextPrimary,
                fontFamily = DisplayFont,
                fontSize = (12f * scale).sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                when {
                    price == null -> ""
                    price <= 0 -> "frei"
                    else -> "$price 🪙"
                },
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = (10f * scale).sp,
                maxLines = 1,
                softWrap = false
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
    onClick: () -> Unit,
    compareAtPrice: Int? = null,
    remainingMs: Long? = null,
    name: String? = null
) {
    val timer = formatShopRemaining(remainingMs)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "gridPress"
    )
    Box(
        modifier = Modifier
            .aspectRatio(0.92f)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (themeId == null) Modifier.background(BgSoft) else Modifier
            )
            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(16.dp))
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    ) {
        if (themeId != null) {
            com.luv.couple.profile.ProfileThemeBackdrop(
                themeId = themeId,
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (timer != null) {
                    Text(
                        timer,
                        color = if (themeId != null) Color(0xFFFFD54F) else AccentRose,
                        fontFamily = BodyFont,
                        fontSize = 8.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            ItemGlyph(id = emoji, fontSize = 30.sp)
            Text(
                name.orEmpty(),
                color = if (themeId != null) Color.White.copy(0.92f) else TextPrimary,
                fontFamily = BodyFont,
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    if (compareAtPrice != null && compareAtPrice > price) {
                        Text(
                            "$compareAtPrice 🪙",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 9.sp,
                            style = androidx.compose.ui.text.TextStyle(
                                textDecoration = TextDecoration.LineThrough
                            ),
                            maxLines = 1
                        )
                    }
                    Text(
                        if (price <= 0) "frei" else "$price 🪙",
                        color = if (themeId != null) Color.White else AccentRose,
                        fontFamily = DisplayFont,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
                if (ownedLabel != null) {
                    Text(
                        ownedLabel,
                        color = if (themeId != null) Color.White.copy(0.9f) else TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
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
        mutableStateOf<ProfileState?>(null)
    }
    var inventoryReady by remember { mutableStateOf(false) }
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
    suspend fun refreshInventoryAndProfile() {
        runCatching {
            val remote = LuvApiClient.fetchInventory()
            prefs.applyInventorySnap(
                emojis = remote.emojis,
                themes = remote.themes,
                stickers = remote.stickers,
                pets = remote.pets,
                equippedPet = remote.equippedPet
            )
            com.luv.couple.net.NotificationBadges.syncInventoryUnseenFromPrefs()
            com.luv.couple.net.NotificationBadges.syncAppBadge(context)
        }
        // Profil vom Server — sonst zaehlen platzierte Sticker lokal nicht
        runCatching {
            val remote = LuvApiClient.fetchMyProfileCanvas()
            val next = remote.second.normalized(remote.first)
            profile = next
            prefs.setProfileCanvasJson(ProfileCatalog.encode(next))
        }.onFailure {
            val json = runCatching { prefs.profileCanvasJson() }.getOrNull()
            profile = ProfileCatalog.decode(json, nickname)
        }
        val companion = profile?.companionEmoji?.trim().orEmpty()
        if (companion.isNotBlank()) {
            runCatching { prefs.setEquippedPet(companion) }
        }
    }
    LaunchedEffect(nickname) {
        // Sofort lokales Profil (freie Items korrekt), dann Server-Refresh
        val json = runCatching { prefs.profileCanvasJson() }.getOrNull()
        profile = ProfileCatalog.decode(json, nickname)
        inventoryReady = true
        refreshInventoryAndProfile()
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, nickname) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                scope.launch { refreshInventoryAndProfile() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun addEmojiToBar(emoji: String, replaceIndex: Int? = null) {
        scope.launch {
            val current = prefs.emojiBar().toMutableList()
            if (emoji in current) {
                Toast.makeText(context, "Schon in der Reaktionsleiste", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val ownedCount = owned[emoji] ?: 0
            val free = InventoryAvailability.freeEmojis(mapOf(emoji to ownedCount), current)[emoji] ?: 0
            if (free < 1) {
                Toast.makeText(context, "Kein Emoji mehr frei", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "In die Reaktionsleiste", Toast.LENGTH_SHORT).show()
        }
    }
    val activeProfile = profile ?: ProfileState(layout = ProfileCatalog.defaultLayout(nickname))
    val placedStickers = remember(activeProfile) {
        InventoryAvailability.countPlacedStickers(
            activeProfile.layout.filter { it.type == ProfileElType.Sticker }.map { it.emoji }
        )
    }
    fun confirmPlace(action: ProfilePlaceAction) {
        when (action) {
            is ProfilePlaceAction.Sticker -> {
                val free = InventoryAvailability.freeStickers(ownedStickersMap, placedStickers)
                if ((free[action.emoji] ?: 0) < 1) {
                    Toast.makeText(context, "Kein Sticker mehr frei", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            is ProfilePlaceAction.Theme -> {
                if (action.themeId == activeProfile.themeId) {
                    Toast.makeText(context, "Hintergrund ist schon aktiv", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            else -> Unit
        }
        pendingAction = action
    }
    MenuBackdrop(includeNavigationBars = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 8.dp)
        ) {
            if (!inventoryReady) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(color = AccentRose)
                }
            } else ProfileInventoryPanel(
                mode = InventoryPanelMode.Menu,
                ownedStickers = ownedStickersMap,
                ownedEmojis = owned,
                ownedThemes = ownedThemes,
                ownedPets = ownedPets,
                placedStickers = placedStickers,
                emojiBar = emojiBar,
                currentThemeId = activeProfile.themeId,
                currentCompanion = equippedPet.ifBlank { activeProfile.companionEmoji },
                hasGlass = false,
                hasBio = false,
                onTheme = { confirmPlace(ProfilePlaceAction.Theme(it.id)) },
                onSticker = { confirmPlace(ProfilePlaceAction.Sticker(it)) },
                onCompanion = { emoji ->
                    scope.launch {
                        runCatching {
                            val eq = LuvApiClient.equipPet(emoji)
                            prefs.setEquippedPet(eq)
                            profile = activeProfile.copy(companionEmoji = eq)
                            Toast.makeText(
                                context,
                                "Begleiter ausgerüstet",
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDeep)
            ) {
                GalleryScreen(onClose = { showGallery = false })
            }
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
                    ItemGlyph(id = emoji, fontSize = 36.sp)
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
                    ItemGlyph(id = emoji, fontSize = 36.sp)
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
                        "Tippe das Emoji an, das ersetzt werden soll.",
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
                                ItemGlyph(id = e, fontSize = 22.sp)
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
    // Bei 7–8 Emojis kompakter, damit „Fertig“ sichtbar bleibt
    val compact = bar.size >= 7
    val slotDp = if (compact) 40.dp else 52.dp
    val gapDp = if (compact) 5.dp else 8.dp
    val trashDp = if (compact) 52.dp else 64.dp
    val listTrashGapDp = if (compact) 10.dp else 14.dp
    val emojiFont = if (compact) 24.sp else 30.sp
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
                    fontSize = if (compact) 22.sp else 26.sp
                )
                Spacer(modifier = Modifier.height(if (compact) 2.dp else 4.dp))
                Text(
                    "Ziehen zum Sortieren · in den Mülleimer zum Entfernen",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = if (compact) 12.sp else 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(if (compact) 4.dp else 6.dp))
                Text(
                    "${bar.size} / ${ShopCatalog.MAX_BAR}",
                    color = AccentRose.copy(0.9f),
                    fontFamily = DisplayFont,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(if (compact) 10.dp else 16.dp))
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
                                ItemGlyph(id = emoji, fontSize = emojiFont)
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
                            Text("🗑", fontSize = if (trashHot) 24.sp else if (compact) 18.sp else 22.sp)
                            Text(
                                if (trashHot) "Loslassen zum Entfernen" else "Mülleimer",
                                color = if (trashHot) AccentRose else TextMuted,
                                fontFamily = BodyFont,
                                fontSize = if (compact) 10.sp else 11.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(if (compact) 12.dp else 18.dp))
                val canAdd = bar.size < ShopCatalog.MAX_BAR
                Box(
                    modifier = Modifier
                        .size(if (compact) 48.dp else 58.dp)
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
                        fontSize = if (compact) 26.sp else 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(if (compact) 4.dp else 6.dp))
                Text(
                    if (canAdd) "Emoji hinzufügen" else "Leiste voll (max. ${ShopCatalog.MAX_BAR})",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = if (compact) 11.sp else 12.sp
                )
                Spacer(modifier = Modifier.height(if (compact) 12.dp else 18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 44.dp else 48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AccentRose.copy(0.2f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Fertig",
                        color = AccentRose,
                        fontFamily = DisplayFont,
                        fontSize = if (compact) 16.sp else 17.sp
                    )
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
                            ItemGlyph(id = emoji, fontSize = 26.sp)
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
