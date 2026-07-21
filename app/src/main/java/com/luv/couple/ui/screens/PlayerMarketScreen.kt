package com.luv.couple.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.luv.couple.ui.ItemGlyph
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.LuvApp
import com.luv.couple.net.AccountSession
import com.luv.couple.net.LuvApiClient
import com.luv.couple.profile.ProfileCatalog
import com.luv.couple.profile.ProfileElType
import com.luv.couple.profile.ProfileThemeBackdrop
import com.luv.couple.shop.InventoryAvailability
import com.luv.couple.shop.ItemLabels
import com.luv.couple.shop.LiveShopCatalog
import com.luv.couple.shop.ShopCatalog
import com.luv.couple.ui.UiScale
import com.luv.couple.ui.rememberUiScale
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MarketCream = Color(0xFFF7F0E4)
private val MarketCreamDeep = Color(0xFFEDE4D4)
private val MarketBrown = Color(0xFF4A3728)
private val MarketBrownMuted = Color(0xFF8B7355)
private val MarketGold = Color(0xFFB8860B)
private val MarketCard = Color(0xFFFFFBF5)

private data class InventoryPick(
    val kind: String,
    val itemId: String,
    val label: String,
    val emoji: String,
    val count: Int
)

/** Nie raw img_/theme_-IDs in Markt-Texten zeigen. */
private fun marketDisplayLabel(kind: String, itemId: String, fallbackLabel: String = ""): String {
    val fromCatalog = ItemLabels.forKind(kind, itemId)
    if (!ItemLabels.looksLikeRawId(fromCatalog) && fromCatalog != itemId) return fromCatalog
    val fb = fallbackLabel.trim()
    if (fb.isNotEmpty() && !ItemLabels.looksLikeRawId(fb) && fb != itemId) return fb
    if (!ItemLabels.looksLikeRawId(fromCatalog)) return fromCatalog
    return ItemLabels.genericLabel(itemId)
}

@Composable
fun PlayerMarketScreen(
    @Suppress("UNUSED_PARAMETER") onClose: () -> Unit,
    modifier: Modifier = Modifier,
    economyUnlocked: Boolean = true,
    onRequireGoogle: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = LuvApp.instance.prefs
    val account by AccountSession.account.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf("market") }
    var category by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var searchInput by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<LuvApiClient.MarketItem>>(emptyList()) }
    var categories by remember { mutableStateOf<List<LuvApiClient.MarketCategory>>(emptyList()) }
    var myListings by remember { mutableStateOf<List<LuvApiClient.MarketListing>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var showMine by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    /** Item aus Übersicht → Angebots-Liste (Nasebär). */
    var offersItem by remember { mutableStateOf<LuvApiClient.MarketItem?>(null) }
    var offersList by remember { mutableStateOf<List<LuvApiClient.MarketListing>>(emptyList()) }
    var offersInsight by remember { mutableStateOf<LuvApiClient.MarketPriceInsight?>(null) }
    var offersLoading by remember { mutableStateOf(false) }
    var previewListing by remember { mutableStateOf<LuvApiClient.MarketListing?>(null) }
    var purchaseFlash by remember { mutableStateOf<Pair<String, Int>?>(null) }
    LaunchedEffect(purchaseFlash) {
        if (purchaseFlash == null) return@LaunchedEffect
        delay(1000)
        purchaseFlash = null
    }
    var friends by remember { mutableStateOf<List<LuvApiClient.FriendCard>>(emptyList()) }
    var pendingSales by remember {
        mutableStateOf<com.luv.couple.net.PendingSalesResult?>(null)
    }
    var claimBusy by remember { mutableStateOf(false) }

    fun openOffers(item: LuvApiClient.MarketItem) {
        offersItem = item
        offersList = emptyList()
        offersInsight = item.priceInsight
        offersLoading = true
        scope.launch {
            runCatching {
                LuvApiClient.fetchMarketOffers(
                    kind = item.kind,
                    itemId = item.itemId,
                    mode = mode
                )
            }.onSuccess {
                offersList = it.offers
                if (it.priceInsight != null) offersInsight = it.priceInsight
            }.onFailure {
                Toast.makeText(
                    context,
                    it.message ?: "Angebote laden fehlgeschlagen",
                    Toast.LENGTH_SHORT
                ).show()
                offersItem = null
                offersInsight = null
            }
            offersLoading = false
        }
    }

    val ownedEmojis by prefs.ownedEmojisFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val ownedThemes by prefs.ownedThemesFlow.collectAsStateWithLifecycle(
        initialValue = listOf(ProfileCatalog.DEFAULT_THEME_ID)
    )
    val ownedStickers by prefs.ownedStickersFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val ownedPets by prefs.ownedPetsFlow.collectAsStateWithLifecycle(
        initialValue = mapOf(ShopCatalog.DEFAULT_PET to 1)
    )
    val equippedPet by prefs.equippedPetFlow.collectAsStateWithLifecycle(
        initialValue = ShopCatalog.DEFAULT_PET
    )
    val emojiBar by prefs.emojiBarFlow.collectAsStateWithLifecycle(
        initialValue = ShopCatalog.DEFAULT_BAR
    )
    var profileThemeId by remember { mutableStateOf(ProfileCatalog.DEFAULT_THEME_ID) }
    var profileCompanion by remember { mutableStateOf(ShopCatalog.DEFAULT_PET) }
    var profilePlacedStickers by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    LaunchedEffect(Unit) {
        val json = prefs.profileCanvasJson().orEmpty()
        if (json.isNotBlank()) {
            runCatching {
                val state = ProfileCatalog.decode(json, "")
                profileThemeId = state.themeId.ifBlank { ProfileCatalog.DEFAULT_THEME_ID }
                profileCompanion = state.companionEmoji.ifBlank { ShopCatalog.DEFAULT_PET }
                profilePlacedStickers = InventoryAvailability.countPlacedStickers(
                    state.layout.filter { it.type == ProfileElType.Sticker }.map { it.emoji }
                )
            }
        }
    }

    fun inventoryPicks(): List<InventoryPick> = buildList {
        // Ausgerüstete / Profil-Items: 1 reserviert, Extras verkaufbar
        InventoryAvailability.freePets(ownedPets, equippedPet, profileCompanion).forEach { (pet, free) ->
            val label = ItemLabels.petLabel(pet)
            add(InventoryPick("pets", pet, label, pet, free))
        }
        ownedThemes.filter { it != ProfileCatalog.DEFAULT_THEME_ID }.forEach { themeId ->
            if (themeId == profileThemeId) return@forEach
            val theme = ShopCatalog.THEMES.firstOrNull { it.id == themeId }
                ?: LiveShopCatalog.remoteThemes?.firstOrNull { it.id == themeId }
            add(
                InventoryPick(
                    kind = "themes",
                    itemId = themeId,
                    label = ItemLabels.themeLabel(themeId),
                    emoji = theme?.emoji?.takeIf { !ItemLabels.looksLikeRawId(it) } ?: "🖼️",
                    count = 1
                )
            )
        }
        val freeStickers = InventoryAvailability.freeStickers(ownedStickers, profilePlacedStickers)
        freeStickers.forEach { (emoji, free) ->
            add(
                InventoryPick(
                    "stickers",
                    emoji,
                    ItemLabels.stickerLabel(emoji),
                    emoji,
                    free
                )
            )
        }
        InventoryAvailability.freeEmojis(ownedEmojis, emojiBar).forEach { (emoji, free) ->
            if (emoji in ShopCatalog.DEFAULT_BAR) return@forEach
            add(
                InventoryPick(
                    "emojis",
                    emoji,
                    ItemLabels.emojiLabel(emoji),
                    emoji,
                    free
                )
            )
        }
    }

    suspend fun syncInventory(): Boolean {
        return runCatching {
            val remote = LuvApiClient.fetchInventory()
            prefs.applyInventorySnap(
                emojis = remote.emojis,
                themes = remote.themes,
                stickers = remote.stickers,
                pets = remote.pets,
                equippedPet = remote.equippedPet
            )
            true
        }.getOrElse {
            Toast.makeText(
                context,
                "Inventar-Sync fehlgeschlagen — bitte neu laden",
                Toast.LENGTH_SHORT
            ).show()
            false
        }
    }

    fun reloadMarket() {
        scope.launch {
            loading = items.isEmpty()
            runCatching {
                LuvApiClient.fetchMarket(mode = mode, category = category, query = query)
            }.onSuccess {
                items = it.items
                categories = it.categories
            }.onFailure {
                Toast.makeText(
                    context,
                    it.message ?: "Markt laden fehlgeschlagen",
                    Toast.LENGTH_SHORT
                ).show()
            }
            loading = false
        }
    }

    fun reloadMine() {
        scope.launch {
            runCatching { LuvApiClient.fetchMyMarketListings() }
                .onSuccess { myListings = it }
        }
    }

    fun refreshOffersIfOpen() {
        val item = offersItem ?: return
        scope.launch {
            runCatching {
                LuvApiClient.fetchMarketOffers(
                    kind = item.kind,
                    itemId = item.itemId,
                    mode = mode
                )
            }.onSuccess {
                offersList = it.offers
                if (it.priceInsight != null) offersInsight = it.priceInsight
                if (it.offers.isEmpty()) {
                    offersItem = null
                    offersInsight = null
                    reloadMarket()
                }
            }
        }
    }

    LaunchedEffect(mode, category, query) {
        offersItem = null
        offersList = emptyList()
        offersInsight = null
        previewListing = null
        reloadMarket()
    }

    LaunchedEffect(Unit) {
        syncInventory()
        reloadMine()
        runCatching { friends = LuvApiClient.fetchFriends().friends }
        val sales = com.luv.couple.net.NotificationBadges.refreshPendingSales(context)
        if (sales != null && sales.count > 0) {
            pendingSales = sales
        }
    }

    pendingSales?.takeIf { it.count > 0 }?.let { sales ->
        AlertDialog(
            onDismissRequest = { if (!claimBusy) pendingSales = null },
            containerColor = BgSoft,
            title = {
                Text(
                    if (sales.count == 1) "Item verkauft" else "Items verkauft",
                    fontFamily = DisplayFont,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        sales.sales.take(12).forEach { sale ->
                            ItemGlyph(id = sale.emoji, fontSize = 28.sp)
                        }
                    }
                    Text(
                        if (sales.count == 1) {
                            "Dein Angebot wurde verkauft."
                        } else {
                            "${sales.count} Angebote wurden verkauft."
                        },
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 14.sp
                    )
                    Text(
                        "${sales.totalCoins} Coins abholen",
                        color = TextPrimary,
                        fontFamily = DisplayFont,
                        fontSize = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !claimBusy,
                    onClick = {
                        claimBusy = true
                        scope.launch {
                            runCatching { LuvApiClient.claimPendingMarketSales() }
                                .onSuccess { result ->
                                    com.luv.couple.net.NotificationBadges.setPendingSales(0)
                                    com.luv.couple.net.NotificationBadges.syncAppBadge(context)
                                    pendingSales = null
                                    Toast.makeText(
                                        context,
                                        "+${result.totalCoins} Coins",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        it.message ?: "Abholen fehlgeschlagen",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            claimBusy = false
                        }
                    }
                ) {
                    Text("Abholen", color = AccentRose, fontFamily = DisplayFont)
                }
            },
            dismissButton = {
                TextButton(enabled = !claimBusy, onClick = { pendingSales = null }) {
                    Text("Später", color = TextMuted, fontFamily = BodyFont)
                }
            }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(MarketCream)
            .border(1.dp, MarketBrownMuted.copy(0.35f), RoundedCornerShape(18.dp))
    ) {
        val ui = rememberUiScale()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ui.s(14.dp))
        ) {
            Text(
                "Marktplatz",
                fontFamily = DisplayFont,
                fontSize = ui.ts(26.sp),
                color = MarketBrown,
                fontWeight = FontWeight.Bold
            )
            Text(
                "LUV MARKTPLATZ · COMMUNITY-HANDEL",
                fontFamily = BodyFont,
                fontSize = ui.ts(11.sp),
                color = MarketBrownMuted,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(ui.s(10.dp)))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ui.s(8.dp))
            ) {
                MarketPillButton(
                    label = if (showMine) "Zurück" else "Meine Angebote",
                    badge = if (showMine) null else myListings.size.takeIf { it > 0 },
                    filled = showMine,
                    ui = ui,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (showMine) {
                            showMine = false
                        } else {
                            showMine = true
                            reloadMine()
                        }
                    }
                )
                MarketPillButton(
                    label = "Angebot erstellen",
                    filled = true,
                    accent = true,
                    ui = ui,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!economyUnlocked) {
                            onRequireGoogle()
                        } else {
                            scope.launch {
                                syncInventory()
                                showCreate = true
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(ui.s(10.dp)))

            if (!showMine) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ui.s(8.dp))
                ) {
                    listOf("market" to "Markt", "private" to "Privat").forEach { (id, label) ->
                        MarketPillButton(
                            label = label,
                            filled = mode == id,
                            ui = ui,
                            modifier = Modifier.weight(1f),
                            onClick = { mode = id }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(ui.s(10.dp)))

                BasicTextField(
                    value = searchInput,
                    onValueChange = { searchInput = it.take(40) },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MarketBrown,
                        fontFamily = BodyFont,
                        fontSize = ui.ts(14.sp)
                    ),
                    cursorBrush = SolidColor(MarketGold),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { query = searchInput.trim() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ui.s(12.dp)))
                        .background(MarketCard)
                        .border(1.dp, MarketBrownMuted.copy(0.25f), RoundedCornerShape(ui.s(12.dp)))
                        .padding(horizontal = ui.s(12.dp), vertical = ui.s(10.dp)),
                    decorationBox = { inner ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔍", fontSize = ui.ts(14.sp))
                            Spacer(modifier = Modifier.width(ui.s(8.dp)))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchInput.isBlank()) {
                                    Text(
                                        "Suchen…",
                                        color = MarketBrownMuted,
                                        fontFamily = BodyFont,
                                        fontSize = ui.ts(14.sp)
                                    )
                                }
                                inner()
                            }
                            if (searchInput.isNotBlank()) {
                                Text(
                                    "↵",
                                    color = MarketGold,
                                    modifier = Modifier.clickable { query = searchInput.trim() }
                                )
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(ui.s(10.dp)))

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Breite so, dass „Hintergründe“ / „Begleiter“ voll lesbar bleiben
                    Column(
                        modifier = Modifier
                            .width(ui.s(124.dp))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(ui.s(12.dp)))
                            .background(MarketCreamDeep)
                            .padding(ui.s(6.dp)),
                        verticalArrangement = Arrangement.spacedBy(ui.s(4.dp))
                    ) {
                        val cats = categories.ifEmpty {
                            listOf(
                                LuvApiClient.MarketCategory("all", "Alle", "📦"),
                                LuvApiClient.MarketCategory("stickers", "Sticker", "🎀"),
                                LuvApiClient.MarketCategory("themes", "Hintergründe", "🖼️"),
                                LuvApiClient.MarketCategory("pets", "Begleiter", "🐣"),
                                LuvApiClient.MarketCategory("emojis", "Emojis", "😊")
                            )
                        }
                        cats.forEach { cat ->
                            val active = category == cat.id ||
                                (category == "all" && cat.id == "all")
                            val displayLabel = categoryLabel(cat.id).takeIf { cat.id != "all" }
                                ?: "Alle"
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(ui.s(10.dp)))
                                    .background(
                                        if (active) MarketCard else Color.Transparent
                                    )
                                    .border(
                                        if (active) 1.dp else 0.dp,
                                        MarketGold.copy(0.4f),
                                        RoundedCornerShape(ui.s(10.dp))
                                    )
                                    .clickable { category = cat.id }
                                    .padding(horizontal = ui.s(6.dp), vertical = ui.s(8.dp))
                            ) {
                                Column {
                                    Text(cat.emoji, fontSize = ui.ts(14.sp))
                                    Text(
                                        displayLabel,
                                        color = if (active) MarketBrown else MarketBrownMuted,
                                        fontFamily = if (active) DisplayFont else BodyFont,
                                        fontSize = ui.ts(10.sp),
                                        softWrap = false,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(ui.s(10.dp)))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${account?.coins ?: 0} Coins",
                            color = MarketBrownMuted,
                            fontFamily = BodyFont,
                            fontSize = ui.ts(12.sp)
                        )
                        Spacer(modifier = Modifier.height(ui.s(6.dp)))
                        val offersProduct = offersItem
                        if (offersProduct != null) {
                            MarketOffersPanel(
                                product = offersProduct,
                                offers = offersList,
                                loading = offersLoading,
                                ui = ui,
                                priceInsight = offersInsight ?: offersProduct.priceInsight,
                                onBack = {
                                    offersItem = null
                                    offersList = emptyList()
                                    offersInsight = null
                                    previewListing = null
                                },
                                onSelectOffer = { previewListing = it }
                            )
                        } else {
                            when {
                                loading && items.isEmpty() -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Lade…", color = MarketBrownMuted, fontFamily = BodyFont)
                                    }
                                }
                                items.isEmpty() -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Noch keine Angebote hier.",
                                            color = MarketBrownMuted,
                                            fontFamily = BodyFont,
                                            fontSize = ui.ts(13.sp)
                                        )
                                    }
                                }
                                else -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(ui.s(8.dp))
                                    ) {
                                        items(items, key = { "${it.kind}|${it.itemId}" }) { item ->
                                            MarketItemRow(
                                                item = item,
                                                ui = ui,
                                                onOpenOffers = { openOffers(item) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    if (myListings.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Du hast noch nichts eingestellt.",
                                color = MarketBrownMuted,
                                fontFamily = BodyFont
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(myListings, key = { it.id }) { listing ->
                                MyListingRow(
                                    listing = listing,
                                    busy = busyId == listing.id,
                                    onCancel = {
                                        busyId = listing.id
                                        scope.launch {
                                            runCatching {
                                                LuvApiClient.cancelMarketListing(listing.id)
                                                syncInventory()
                                            }.onSuccess {
                                                Toast.makeText(
                                                    context,
                                                    "Angebot zurückgezogen",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                reloadMine()
                                                reloadMarket()
                                            }.onFailure {
                                                Toast.makeText(
                                                    context,
                                                    it.message ?: "Abbrechen fehlgeschlagen",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            busyId = null
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateListingDialog(
            inventory = inventoryPicks(),
            friends = friends,
            categoryFilter = category,
            onDismiss = { showCreate = false },
            onCreated = {
                showCreate = false
                reloadMine()
                reloadMarket()
                scope.launch { syncInventory() }
                Toast.makeText(context, "Angebot erstellt", Toast.LENGTH_SHORT).show()
            }
        )
    }

    previewListing?.let { listing ->
        MarketListingPreviewDialog(
            listing = listing,
            busy = busyId == listing.id,
            priceInsight = offersInsight,
            onDismiss = { previewListing = null },
            onBuy = {
                if (!economyUnlocked) {
                    onRequireGoogle()
                } else {
                    val bought = listing
                    val prevAccount = account
                    if (prevAccount != null && bought.priceCoins > 0) {
                        AccountSession.setAccount(
                            prevAccount.copy(
                                coins = (prevAccount.coins - bought.priceCoins).coerceAtLeast(0)
                            )
                        )
                    }
                    val prevOffers = offersList
                    offersList = offersList.filter { it.id != bought.id }
                    purchaseFlash = marketDisplayLabel(
                        bought.kind,
                        bought.itemId,
                        bought.label
                    ) to bought.priceCoins
                    previewListing = null
                    busyId = null
                    scope.launch {
                        runCatching {
                            LuvApiClient.buyMarketListing(bought.id)
                        }.onSuccess {
                            // Listen parallel im Hintergrund
                            kotlinx.coroutines.coroutineScope {
                                launch { runCatching { syncInventory() } }
                                launch { refreshOffersIfOpen() }
                                launch { reloadMarket() }
                                launch { reloadMine() }
                            }
                        }.onFailure {
                            prevAccount?.let { AccountSession.setAccount(it) }
                            offersList = prevOffers
                            Toast.makeText(
                                context,
                                it.message ?: "Kauf fehlgeschlagen",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        )
    }
    purchaseFlash?.let { (title, pricePaid) ->
        PurchaseFlashPopup(title = title, priceCoins = pricePaid)
    }
}

@Composable
private fun MarketPillButton(
    label: String,
    modifier: Modifier = Modifier,
    badge: Int? = null,
    filled: Boolean = false,
    accent: Boolean = false,
    ui: UiScale = UiScale(1f),
    onClick: () -> Unit
) {
    val bg = when {
        accent && filled -> MarketGold
        filled -> MarketCard
        else -> MarketCreamDeep
    }
    val fg = when {
        accent && filled -> Color.White
        filled -> MarketBrown
        else -> MarketBrownMuted
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(ui.s(12.dp)))
            .background(bg)
            .border(1.dp, MarketBrownMuted.copy(0.3f), RoundedCornerShape(ui.s(12.dp)))
            .clickable(onClick = onClick)
            .padding(horizontal = ui.s(10.dp), vertical = ui.s(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ui.s(6.dp))
        ) {
            Text(
                label,
                color = fg,
                fontFamily = DisplayFont,
                fontSize = ui.ts(12.sp),
                softWrap = false
            )
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .size(ui.s(18.dp))
                        .clip(CircleShape)
                        .background(MarketGold),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$badge",
                        color = Color.White,
                        fontFamily = BodyFont,
                        fontSize = ui.ts(10.sp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketOffersPanel(
    product: LuvApiClient.MarketItem,
    offers: List<LuvApiClient.MarketListing>,
    loading: Boolean,
    ui: UiScale,
    priceInsight: LuvApiClient.MarketPriceInsight? = product.priceInsight,
    onBack: () -> Unit,
    onSelectOffer: (LuvApiClient.MarketListing) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollTop = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 40
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(ui.s(10.dp)))
                    .background(MarketCreamDeep)
                    .clickable(onClick = onBack)
                    .padding(horizontal = ui.s(10.dp), vertical = ui.s(8.dp)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ui.s(8.dp))
            ) {
                Text("<", color = MarketBrown, fontFamily = DisplayFont, fontSize = ui.ts(20.sp))
                ItemGlyph(id = product.emoji, fontSize = ui.ts(22.sp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        product.label,
                        color = MarketBrown,
                        fontFamily = DisplayFont,
                        fontSize = ui.ts(14.sp),
                        softWrap = true
                    )
                    Text(
                        if (loading) "Lade..."
                        else "${offers.size} Angebot${if (offers.size == 1) "" else "e"}",
                        color = MarketBrownMuted,
                        fontFamily = BodyFont,
                        fontSize = ui.ts(11.sp)
                    )
                }
                Text("Zurück", color = MarketGold, fontFamily = DisplayFont, fontSize = ui.ts(12.sp))
            }
            if (priceInsight != null && priceInsight.hasAny) {
                Spacer(modifier = Modifier.height(ui.s(8.dp)))
                MarketPriceInsightCard(insight = priceInsight, compact = true)
            }
            Spacer(modifier = Modifier.height(ui.s(8.dp)))
            if (loading && offers.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Lade Angebote...", color = MarketBrownMuted, fontFamily = BodyFont)
                }
            } else if (offers.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Keine Angebote mehr.", color = MarketBrownMuted, fontFamily = BodyFont)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(ui.s(8.dp))
                ) {
                    items(offers, key = { it.id }) { offer ->
                        MarketOfferRow(
                            offer = offer,
                            onClick = { onSelectOffer(offer) }
                        )
                    }
                }
            }
        }
        if (showScrollTop) {
            Text(
                "Nach oben",
                color = Color.White,
                fontFamily = DisplayFont,
                fontSize = ui.ts(12.sp),
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = ui.s(10.dp))
                    .clip(RoundedCornerShape(ui.s(20.dp)))
                    .background(MarketBrown)
                    .clickable {
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                    .padding(horizontal = ui.s(14.dp), vertical = ui.s(8.dp))
            )
        }
    }
}

@Composable
private fun MarketOfferRow(
    offer: LuvApiClient.MarketListing,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MarketCard)
            .border(1.dp, MarketBrownMuted.copy(0.2f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                offer.sellerNickname,
                color = MarketBrown,
                fontFamily = DisplayFont,
                fontSize = 14.sp
            )
            Text(
                buildString {
                    append("${offer.priceCoins} Coins")
                    if (offer.isMine) append(" · dein Angebot")
                },
                color = MarketGold,
                fontFamily = BodyFont,
                fontSize = 12.sp,
                softWrap = true
            )
        }
        Text("›", color = MarketBrownMuted, fontFamily = DisplayFont, fontSize = 20.sp)
    }
}

@Composable
private fun MarketListingPreviewDialog(
    listing: LuvApiClient.MarketListing,
    busy: Boolean,
    priceInsight: LuvApiClient.MarketPriceInsight? = null,
    onDismiss: () -> Unit,
    onBuy: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = MarketCream,
        title = {
            Text("Vorschau", fontFamily = DisplayFont, color = MarketBrown)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (listing.category == "themes" || listing.kind == "themes") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.12f)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        ProfileThemeBackdrop(
                            themeId = listing.itemId,
                            modifier = Modifier.fillMaxSize()
                        )
                        ItemGlyph(
                            id = listing.emoji.takeIf { !ItemLabels.looksLikeRawId(it) } ?: "🖼️",
                            fontSize = 28.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(0.35f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MarketCard)
                            .border(1.dp, MarketBrownMuted.copy(0.25f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        ItemGlyph(id = listing.emoji, fontSize = 72.sp)
                    }
                }
                Text(
                    marketDisplayLabel(listing.kind, listing.itemId, listing.label),
                    color = MarketBrown,
                    fontFamily = DisplayFont,
                    fontSize = 18.sp
                )
                Text(
                    "${categoryLabel(listing.category)} · ${listing.sellerNickname}",
                    color = MarketBrownMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp
                )
                Text(
                    "${listing.priceCoins} Coins",
                    color = MarketGold,
                    fontFamily = DisplayFont,
                    fontSize = 14.sp
                )
                MarketPriceInsightCard(
                    insight = priceInsight,
                    compact = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (!listing.isMine && listing.priceCoins > 0) {
                TextButton(enabled = !busy, onClick = onBuy) {
                    Text(if (busy) "…" else "Kaufen", color = MarketGold, fontFamily = DisplayFont)
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text("Schließen", color = MarketBrownMuted, fontFamily = BodyFont)
            }
        }
    )
}

@Composable
private fun MarketItemRow(
    item: LuvApiClient.MarketItem,
    ui: UiScale,
    onOpenOffers: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ui.s(14.dp)))
            .background(MarketCard)
            .border(1.dp, MarketBrownMuted.copy(0.2f), RoundedCornerShape(ui.s(14.dp)))
            .clickable(onClick = onOpenOffers)
            .padding(ui.s(10.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ui.s(10.dp))
    ) {
        Box(
            modifier = Modifier
                .size(ui.s(48.dp))
                .clip(RoundedCornerShape(ui.s(10.dp)))
                .then(
                    if (item.category != "themes" && item.kind != "themes") {
                        Modifier.background(MarketCreamDeep)
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (item.category == "themes" || item.kind == "themes") {
                ProfileThemeBackdrop(
                    themeId = item.itemId,
                    modifier = Modifier.fillMaxSize()
                )
            }
            ItemGlyph(id = item.emoji, fontSize = ui.ts(22.sp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ui.s(6.dp))
            ) {
                Text(
                    marketDisplayLabel(item.kind, item.itemId, item.label),
                    color = MarketBrown,
                    fontFamily = DisplayFont,
                    fontSize = ui.ts(14.sp),
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (item.ownedByViewer) {
                    Text(
                        "DEINS",
                        color = MarketGold,
                        fontFamily = DisplayFont,
                        fontSize = ui.ts(9.sp),
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(ui.s(4.dp)))
                            .background(MarketGold.copy(0.15f))
                            .padding(horizontal = ui.s(4.dp), vertical = ui.s(2.dp))
                    )
                }
            }
            Text(
                categoryLabel(item.category),
                color = MarketBrownMuted,
                fontFamily = BodyFont,
                fontSize = ui.ts(11.sp),
                softWrap = false,
                maxLines = 1
            )
            // Meta-Zeile: feste kleine Schrift, kein Umbruch bei 2-stelligen Preisen
            val metaSp = ui.ts(10.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ui.s(4.dp))
            ) {
                Text(
                    if (item.priceCoins > 0) "ab ${item.priceCoins} 🪙" else "Angebote",
                    color = MarketGold,
                    fontFamily = DisplayFont,
                    fontSize = metaSp,
                    softWrap = false,
                    maxLines = 1
                )
                Text("·", color = MarketBrownMuted, fontSize = metaSp, softWrap = false)
                Text(
                    "${item.offerCount}${if (item.offerCount == 1) " Ang." else " Ang."}",
                    color = MarketGold,
                    fontFamily = DisplayFont,
                    fontSize = metaSp,
                    softWrap = false,
                    maxLines = 1
                )
                Text("·", color = MarketBrownMuted, fontSize = metaSp, softWrap = false)
                Text(
                    trendArrow(item.trend),
                    color = trendColor(item.trend),
                    fontFamily = DisplayFont,
                    fontSize = metaSp,
                    softWrap = false,
                    maxLines = 1
                )
            }
            MarketPriceMetaRow(insight = item.priceInsight, fontSp = metaSp)
        }
        Text("›", color = MarketBrownMuted, fontFamily = DisplayFont, fontSize = ui.ts(22.sp))
    }
}

@Composable
private fun MarketPriceMetaRow(
    insight: LuvApiClient.MarketPriceInsight?,
    fontSp: androidx.compose.ui.unit.TextUnit
) {
    if (insight == null || !insight.hasAny) return
    val parts = buildList {
        insight.shopPrice?.let { add("Shop $it 🪙") }
        when {
            insight.sales != null -> {
                val s = insight.sales
                add("${trendArrow(s.trend)} ${formatCoinRange(s.min, s.max)}")
            }
            insight.listings != null -> {
                add(formatCoinRange(insight.listings.min, insight.listings.max))
            }
        }
    }
    if (parts.isEmpty()) return
    Text(
        parts.joinToString("  ·  "),
        color = MarketBrownMuted,
        fontFamily = BodyFont,
        fontSize = fontSp,
        softWrap = false,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun MyListingRow(
    listing: LuvApiClient.MarketListing,
    busy: Boolean,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MarketCard)
            .border(1.dp, MarketGold.copy(0.25f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ItemGlyph(id = listing.emoji, fontSize = 28.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                marketDisplayLabel(listing.kind, listing.itemId, listing.label),
                color = MarketBrown,
                fontFamily = DisplayFont,
                fontSize = 14.sp
            )
            Text(
                if (listing.isPrivate) "Privat" else "Öffentlich",
                color = MarketBrownMuted,
                fontFamily = BodyFont,
                fontSize = 11.sp
            )
            Text(
                "${listing.priceCoins} 🪙",
                color = MarketGold,
                fontFamily = DisplayFont,
                fontSize = 12.sp
            )
        }
        Text(
            if (busy) "…" else "Zurückziehen",
            color = MarketBrown,
            fontFamily = BodyFont,
            fontSize = 11.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MarketCreamDeep)
                .clickable(enabled = !busy, onClick = onCancel)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

/** Gleiche Reihenfolge wie Itemshop & Inventar. */
private val CreateInventoryTabs: List<Pair<String, String>> = listOf(
    "stickers" to "Sticker",
    "themes" to "Hintergründe",
    "pets" to "Begleiter",
    "emojis" to "Emojis"
)

@Composable
private fun CreateListingDialog(
    inventory: List<InventoryPick>,
    friends: List<LuvApiClient.FriendCard>,
    categoryFilter: String,
    onDismiss: () -> Unit,
    onCreated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showInventarTabs = categoryFilter == "all" || categoryFilter.isBlank()
    var inventarTab by remember {
        mutableStateOf(
            when (categoryFilter) {
                "pets", "stickers", "themes", "emojis" -> categoryFilter
                else -> "stickers"
            }
        )
    }
    var pick by remember { mutableStateOf<InventoryPick?>(null) }
    var picking by remember { mutableStateOf(false) }
    var priceText by remember { mutableStateOf("5") }
    var isPrivate by remember { mutableStateOf(false) }
    var targetFriend by remember { mutableStateOf<LuvApiClient.FriendCard?>(null) }
    var busy by remember { mutableStateOf(false) }
    var priceInsight by remember { mutableStateOf<LuvApiClient.MarketPriceInsight?>(null) }
    var priceInsightLoading by remember { mutableStateOf(false) }

    val activeKind = if (showInventarTabs) inventarTab else categoryFilter
    val sellInventory = remember(inventory, activeKind) {
        inventory.filter { it.kind == activeKind }
    }

    LaunchedEffect(pick?.kind, pick?.itemId) {
        val selected = pick
        if (selected == null) {
            priceInsight = null
            return@LaunchedEffect
        }
        priceInsightLoading = true
        priceInsight = runCatching {
            LuvApiClient.fetchMarketItemPrice(selected.kind, selected.itemId)
        }.getOrNull()
        priceInsightLoading = false
        // Sinnvoller Startpreis: Mittel der Spanne oder Shop-Preis
        val hint = priceInsight
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

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = MarketCream,
        title = {
            Text(
                if (picking) "Item wählen" else "Angebot erstellen",
                fontFamily = DisplayFont,
                color = MarketBrown
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (picking) {
                    Text(
                        "Nur Coins in LUV — Echtgeldhandel mit Items ist verboten und kann zum Ban führen.",
                        color = MarketBrown.copy(alpha = 0.75f),
                        fontFamily = BodyFont,
                        fontSize = 12.sp
                    )
                    if (showInventarTabs) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CreateInventoryTabs.chunked(2).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    row.forEach { (id, label) ->
                                        val on = inventarTab == id
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (on) MarketGold.copy(0.2f) else MarketCard)
                                                .clickable { inventarTab = id }
                                                .padding(vertical = 8.dp, horizontal = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                label,
                                                color = MarketBrown,
                                                fontFamily = if (on) DisplayFont else BodyFont,
                                                fontSize = 12.sp,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (sellInventory.isEmpty()) {
                        Text(
                            if (inventory.isEmpty()) {
                                "Nichts Verkaufbares im Inventar."
                            } else {
                                "Keine ${categoryLabel(activeKind)} im Inventar."
                            },
                            color = MarketBrownMuted,
                            fontFamily = BodyFont
                        )
                    } else {
                        sellInventory.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MarketCard)
                                    .clickable {
                                        pick = item
                                        picking = false
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ItemGlyph(id = item.emoji, fontSize = 22.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.label,
                                        color = MarketBrown,
                                        fontFamily = BodyFont,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        "×${item.count}",
                                        color = MarketBrownMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val selected = pick
                    if (selected == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MarketGold.copy(0.18f))
                                .border(1.dp, MarketGold.copy(0.45f), RoundedCornerShape(12.dp))
                                .clickable { picking = true }
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Item wählen",
                                color = MarketBrown,
                                fontFamily = DisplayFont,
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MarketCard)
                                .border(1.dp, MarketGold.copy(0.35f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ItemGlyph(id = selected.emoji, fontSize = 36.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Ausgewählt",
                                    color = MarketBrownMuted,
                                    fontFamily = BodyFont,
                                    fontSize = 11.sp
                                )
                                Text(
                                    selected.label,
                                    color = MarketBrown,
                                    fontFamily = DisplayFont,
                                    fontSize = 16.sp
                                )
                                Text(
                                    "${categoryLabel(selected.kind)} · ×${selected.count}",
                                    color = MarketBrownMuted,
                                    fontFamily = BodyFont,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                "Ändern",
                                color = MarketGold,
                                fontFamily = DisplayFont,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { picking = true }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                        if (priceInsightLoading) {
                            Text(
                                "Preise werden geladen…",
                                color = MarketBrownMuted,
                                fontFamily = BodyFont,
                                fontSize = 12.sp
                            )
                        } else {
                            MarketPriceInsightCard(insight = priceInsight)
                        }
                    }
                    Text(
                        "Preis (Coins)",
                        color = MarketBrownMuted,
                        fontFamily = BodyFont,
                        fontSize = 12.sp
                    )
                    BasicTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter { ch -> ch.isDigit() }.take(5) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TextStyle(
                            color = MarketBrown,
                            fontFamily = BodyFont,
                            fontSize = 16.sp
                        ),
                        cursorBrush = SolidColor(MarketGold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MarketCard)
                            .padding(10.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isPrivate = !isPrivate }
                    ) {
                        Text(if (isPrivate) "☑" else "☐", color = MarketGold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Privates Angebot", color = MarketBrown, fontFamily = BodyFont)
                    }
                    if (isPrivate) {
                        friends.forEach { friend ->
                            val on = targetFriend?.userId == friend.userId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (on) MarketGold.copy(0.12f) else Color.Transparent)
                                    .clickable { targetFriend = friend }
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                com.luv.couple.ui.CompanionGlyph(
                                    petId = friend.petEmoji,
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(friend.nickname, color = MarketBrown, fontFamily = BodyFont)
                            }
                        }
                        if (friends.isEmpty()) {
                            Text(
                                "Keine Freunde — erst Freund hinzufügen.",
                                color = MarketBrownMuted,
                                fontFamily = BodyFont,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (picking) {
                TextButton(enabled = !busy, onClick = { picking = false }) {
                    Text("Zurück", color = MarketBrownMuted, fontFamily = BodyFont)
                }
            } else {
                TextButton(
                    enabled = !busy && pick != null && (!isPrivate || targetFriend != null),
                    onClick = {
                        val selected = pick ?: return@TextButton
                        val price = priceText.toIntOrNull() ?: 0
                        if (price < 1) {
                            Toast.makeText(context, "Preis mind. 1 Coin", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        busy = true
                        scope.launch {
                            runCatching {
                                LuvApiClient.listMarketItem(
                                    kind = selected.kind,
                                    itemId = selected.itemId,
                                    priceCoins = price,
                                    allowTrade = false,
                                    isPrivate = isPrivate,
                                    targetUserId = targetFriend?.userId,
                                    tradeWantKind = null,
                                    tradeWantItemId = null,
                                    tradeWantLabel = null
                                )
                            }.onSuccess { onCreated() }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        it.message ?: "Erstellen fehlgeschlagen",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            busy = false
                        }
                    }
                ) {
                    Text(if (busy) "…" else "Einstellen", color = MarketGold, fontFamily = DisplayFont)
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    if (picking) picking = false else onDismiss()
                }
            ) {
                Text(
                    if (picking) "Abbrechen" else "Schließen",
                    color = MarketBrownMuted,
                    fontFamily = BodyFont
                )
            }
        }
    )
}


private fun categoryLabel(category: String): String = when (category) {
    "stickers" -> "Sticker"
    "themes" -> "Hintergründe"
    "pets" -> "Begleiter"
    "emojis" -> "Emojis"
    "all" -> "Alle"
    else -> "Artikel"
}

private fun trendLabel(trend: String): String = when (trend) {
    "↑" -> "↑ steigend"
    "↓" -> "↓ fallend"
    else -> "→ stabil"
}

private fun trendArrow(trend: String): String = when (trend) {
    "↑" -> "↑"
    "↓" -> "↓"
    else -> "→"
}

private fun trendColor(trend: String): Color = when (trend) {
    "↑" -> Color(0xFF2E7D32)
    "↓" -> Color(0xFFC62828)
    else -> MarketBrownMuted
}

private fun formatCoinRange(min: Int, max: Int): String =
    if (min == max) "$min 🪙" else "$min–$max 🪙"

@Composable
private fun MarketPriceInsightCard(
    insight: LuvApiClient.MarketPriceInsight?,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (insight == null || !insight.hasAny) return
    val shape = RoundedCornerShape(if (compact) 10.dp else 12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MarketCreamDeep.copy(0.85f))
            .border(1.dp, MarketGold.copy(0.28f), shape)
            .padding(
                horizontal = if (compact) 10.dp else 12.dp,
                vertical = if (compact) 8.dp else 10.dp
            ),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
    ) {
        insight.shopPrice?.let { shop ->
            PriceInsightLine(
                label = "Itemshop",
                value = "$shop 🪙",
                accent = MarketBrown,
                compact = compact
            )
        }
        insight.sales?.let { sales ->
            PriceInsightLine(
                label = "Verkauft · ${insight.windowDays} Tage",
                value = formatCoinRange(sales.min, sales.max),
                trend = sales.trend,
                accent = MarketGold,
                compact = compact
            )
        }
        insight.listings?.let { listings ->
            PriceInsightLine(
                label = "Aktuelle Angebote",
                value = formatCoinRange(listings.min, listings.max),
                trend = listings.trend,
                accent = MarketGold,
                compact = compact
            )
        }
    }
}

@Composable
private fun PriceInsightLine(
    label: String,
    value: String,
    accent: Color,
    compact: Boolean,
    trend: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            color = MarketBrownMuted,
            fontFamily = BodyFont,
            fontSize = if (compact) 11.sp else 12.sp,
            modifier = Modifier.weight(1f)
        )
        if (trend != null) {
            Text(
                trendArrow(trend),
                color = trendColor(trend),
                fontFamily = DisplayFont,
                fontSize = if (compact) 13.sp else 14.sp
            )
        }
        Text(
            value,
            color = accent,
            fontFamily = DisplayFont,
            fontSize = if (compact) 12.sp else 13.sp,
            softWrap = false
        )
    }
}

/** Kurze Zeile für Marktlisten (gleiche Infos, kompakt). */
private fun priceInsightSummary(insight: LuvApiClient.MarketPriceInsight?): String? {
    if (insight == null || !insight.hasAny) return null
    val parts = mutableListOf<String>()
    insight.shopPrice?.let { parts.add("Shop $it 🪙") }
    when {
        insight.sales != null -> {
            val s = insight.sales
            parts.add("${trendArrow(s.trend)} ${formatCoinRange(s.min, s.max)}")
        }
        insight.listings != null -> {
            val l = insight.listings
            parts.add("Angebote ${formatCoinRange(l.min, l.max)}")
        }
    }
    return parts.joinToString("  ·  ").takeIf { it.isNotBlank() }
}
