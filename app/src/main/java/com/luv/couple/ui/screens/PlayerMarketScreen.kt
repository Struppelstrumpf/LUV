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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.LuvApp
import com.luv.couple.net.AccountSession
import com.luv.couple.net.LuvApiClient
import com.luv.couple.profile.ProfileCatalog
import com.luv.couple.profile.ProfileThemeBackdrop
import com.luv.couple.shop.ShopCatalog
import com.luv.couple.ui.UiScale
import com.luv.couple.ui.rememberUiScale
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
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

@Composable
fun PlayerMarketScreen(
    @Suppress("UNUSED_PARAMETER") onClose: () -> Unit,
    modifier: Modifier = Modifier
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
    var offersLoading by remember { mutableStateOf(false) }
    var previewListing by remember { mutableStateOf<LuvApiClient.MarketListing?>(null) }
    var tradeListing by remember { mutableStateOf<LuvApiClient.MarketListing?>(null) }
    var friends by remember { mutableStateOf<List<LuvApiClient.FriendCard>>(emptyList()) }

    fun openOffers(item: LuvApiClient.MarketItem) {
        offersItem = item
        offersList = emptyList()
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
            }.onFailure {
                Toast.makeText(
                    context,
                    it.message ?: "Angebote laden fehlgeschlagen",
                    Toast.LENGTH_SHORT
                ).show()
                offersItem = null
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
        initialValue = listOf(ShopCatalog.DEFAULT_PET)
    )

    fun inventoryPicks(): List<InventoryPick> = buildList {
        ownedPets.filter { it != ShopCatalog.DEFAULT_PET }.forEach { pet ->
            val label = ShopCatalog.PETS.firstOrNull { it.emoji == pet }?.label ?: pet
            add(InventoryPick("pets", pet, label, pet, 1))
        }
        ownedThemes.filter { it != ProfileCatalog.DEFAULT_THEME_ID }.forEach { themeId ->
            val theme = ShopCatalog.THEMES.firstOrNull { it.id == themeId }
            add(
                InventoryPick(
                    kind = "themes",
                    itemId = themeId,
                    label = theme?.label ?: themeId,
                    emoji = theme?.emoji ?: "🖼️",
                    count = 1
                )
            )
        }
        ownedStickers.forEach { (emoji, count) ->
            if (count > 0) add(InventoryPick("stickers", emoji, emoji, emoji, count))
        }
        ownedEmojis.forEach { (emoji, count) ->
            if (count > 0 && emoji !in ShopCatalog.DEFAULT_BAR) {
                add(InventoryPick("emojis", emoji, emoji, emoji, count))
            }
        }
    }

    suspend fun syncInventory() {
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
                if (it.offers.isEmpty()) {
                    offersItem = null
                    reloadMarket()
                }
            }
        }
    }

    LaunchedEffect(mode, category, query) {
        offersItem = null
        offersList = emptyList()
        previewListing = null
        tradeListing = null
        reloadMarket()
    }

    LaunchedEffect(Unit) {
        syncInventory()
        reloadMine()
        runCatching { friends = LuvApiClient.fetchFriends().friends }
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
                "LUV TAUSCHPLATZ · COMMUNITY-HANDEL",
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
                    label = "Meine Angebote",
                    badge = myListings.size.takeIf { it > 0 },
                    filled = showMine,
                    ui = ui,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        showMine = !showMine
                        if (showMine) reloadMine()
                    }
                )
                MarketPillButton(
                    label = "Angebot erstellen",
                    filled = true,
                    accent = true,
                    ui = ui,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            syncInventory()
                            showCreate = true
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
                                        fontSize = ui.ts(11.sp),
                                        softWrap = true
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

    offersItem?.let { product ->
        MarketOffersDialog(
            product = product,
            offers = offersList,
            loading = offersLoading,
            onDismiss = {
                offersItem = null
                offersList = emptyList()
                previewListing = null
            },
            onSelectOffer = { previewListing = it }
        )
    }

    tradeListing?.let { target ->
        TradeOfferDialog(
            target = target,
            inventory = inventoryPicks(),
            onDismiss = { tradeListing = null },
            onTraded = {
                tradeListing = null
                previewListing = null
                refreshOffersIfOpen()
                reloadMarket()
                reloadMine()
                scope.launch { syncInventory() }
                Toast.makeText(context, "Tausch abgeschlossen", Toast.LENGTH_SHORT).show()
            }
        )
    }

    previewListing?.let { listing ->
        MarketListingPreviewDialog(
            listing = listing,
            busy = busyId == listing.id,
            onDismiss = { previewListing = null },
            onBuy = {
                busyId = listing.id
                scope.launch {
                    runCatching {
                        LuvApiClient.buyMarketListing(listing.id)
                        syncInventory()
                    }.onSuccess {
                        Toast.makeText(context, "${listing.label} gekauft", Toast.LENGTH_SHORT).show()
                        previewListing = null
                        refreshOffersIfOpen()
                        reloadMarket()
                        reloadMine()
                    }.onFailure {
                        Toast.makeText(
                            context,
                            it.message ?: "Kauf fehlgeschlagen",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    busyId = null
                }
            },
            onTrade = {
                previewListing = null
                tradeListing = listing
            }
        )
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
private fun MarketOffersDialog(
    product: LuvApiClient.MarketItem,
    offers: List<LuvApiClient.MarketListing>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSelectOffer: (LuvApiClient.MarketListing) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MarketCream,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(product.emoji, fontSize = 28.sp)
                Column {
                    Text(product.label, fontFamily = DisplayFont, color = MarketBrown, fontSize = 20.sp)
                    Text(
                        "${offers.size} Angebot${if (offers.size == 1) "" else "e"}",
                        color = MarketBrownMuted,
                        fontFamily = BodyFont,
                        fontSize = 12.sp
                    )
                }
            }
        },
        text = {
            when {
                loading -> Text("Lade Angebote…", color = MarketBrownMuted, fontFamily = BodyFont)
                offers.isEmpty() -> Text(
                    "Keine Angebote mehr.",
                    color = MarketBrownMuted,
                    fontFamily = BodyFont
                )
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Zurück", color = MarketBrownMuted, fontFamily = BodyFont)
            }
        }
    )
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
                    when {
                        offer.allowTrade && offer.priceCoins <= 0 -> append("Nur Tausch")
                        offer.allowTrade -> append("${offer.priceCoins} Coins · Tausch möglich")
                        else -> append("${offer.priceCoins} Coins")
                    }
                    if (offer.allowTrade && !offer.tradeWantLabel.isNullOrBlank()) {
                        append(" · sucht ${offer.tradeWantLabel}")
                    }
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
    onDismiss: () -> Unit,
    onBuy: () -> Unit,
    onTrade: () -> Unit
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
                        Text(
                            listing.emoji,
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
                        Text(listing.emoji, fontSize = 72.sp)
                    }
                }
                Text(
                    listing.label,
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
                    when {
                        listing.allowTrade && listing.priceCoins <= 0 -> "Tausch möglich"
                        listing.allowTrade -> "${listing.priceCoins} Coins · Tausch möglich"
                        else -> "${listing.priceCoins} Coins"
                    },
                    color = MarketGold,
                    fontFamily = DisplayFont,
                    fontSize = 14.sp
                )
                if (listing.allowTrade && !listing.tradeWantLabel.isNullOrBlank()) {
                    Text(
                        "Gesucht: ${listing.tradeWantLabel}",
                        color = MarketBrownMuted,
                        fontFamily = BodyFont,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!listing.isMine && listing.priceCoins > 0) {
                    TextButton(enabled = !busy, onClick = onBuy) {
                        Text(if (busy) "…" else "Kaufen", color = MarketGold, fontFamily = DisplayFont)
                    }
                }
                if (!listing.isMine && listing.allowTrade) {
                    TextButton(enabled = !busy, onClick = onTrade) {
                        Text("Tausch", color = MarketBrown, fontFamily = BodyFont)
                    }
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
            Text(item.emoji, fontSize = ui.ts(22.sp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.label,
                    color = MarketBrown,
                    fontFamily = DisplayFont,
                    fontSize = ui.ts(14.sp),
                    softWrap = true,
                    modifier = Modifier.weight(1f)
                )
                if (item.ownedByViewer) {
                    Spacer(modifier = Modifier.width(ui.s(6.dp)))
                    Text(
                        "DEINS",
                        color = MarketGold,
                        fontFamily = DisplayFont,
                        fontSize = ui.ts(9.sp),
                        softWrap = false,
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
                softWrap = true
            )
            Text(
                buildString {
                    when {
                        item.priceCoins > 0 -> append("ab ${item.priceCoins} 🪙")
                        item.allowTrade -> append("Tausch")
                        else -> append("Angebote")
                    }
                    if (item.allowTrade && item.priceCoins > 0) append(" · Tausch")
                    append("  ·  ")
                    append(item.offerCount)
                    append(if (item.offerCount == 1) " Angebot" else " Angebote")
                    append("  ·  ")
                    append(trendLabel(item.trend))
                },
                color = MarketGold,
                fontFamily = DisplayFont,
                fontSize = ui.ts(12.sp),
                softWrap = true
            )
        }
        Text("›", color = MarketBrownMuted, fontFamily = DisplayFont, fontSize = ui.ts(22.sp))
    }
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
        Text(listing.emoji, fontSize = 28.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(listing.label, color = MarketBrown, fontFamily = DisplayFont, fontSize = 14.sp)
            Text(
                if (listing.isPrivate) "Privat" else "Öffentlich",
                color = MarketBrownMuted,
                fontFamily = BodyFont,
                fontSize = 11.sp
            )
            Text(
                if (listing.allowTrade && listing.priceCoins <= 0) "Tausch"
                else "${listing.priceCoins} 🪙",
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
    var priceText by remember { mutableStateOf("5") }
    var allowTrade by remember { mutableStateOf(false) }
    var isPrivate by remember { mutableStateOf(false) }
    var targetFriend by remember { mutableStateOf<LuvApiClient.FriendCard?>(null) }
    var tradeWant by remember { mutableStateOf<InventoryPick?>(null) }
    var busy by remember { mutableStateOf(false) }

    val activeKind = if (showInventarTabs) inventarTab else categoryFilter
    val sellInventory = remember(inventory, activeKind) {
        inventory.filter { it.kind == activeKind }
    }
    LaunchedEffect(activeKind) {
        if (pick != null && pick?.kind != activeKind) pick = null
        if (tradeWant != null && tradeWant?.kind == pick?.kind && tradeWant?.itemId == pick?.itemId) {
            tradeWant = null
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = MarketCream,
        title = {
            Text("Angebot erstellen", fontFamily = DisplayFont, color = MarketBrown)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (showInventarTabs) {
                        "Artikel aus Inventar"
                    } else {
                        "Nur ${categoryLabel(categoryFilter)} aus deinem Inventar"
                    },
                    color = MarketBrownMuted,
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
                        val selected = pick?.kind == item.kind && pick?.itemId == item.itemId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) MarketGold.copy(0.15f) else MarketCard)
                                .clickable { pick = item }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(item.emoji, fontSize = 22.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.label, color = MarketBrown, fontFamily = BodyFont, fontSize = 13.sp)
                                Text("×${item.count}", color = MarketBrownMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Text("Preis (Coins)", color = MarketBrownMuted, fontFamily = BodyFont, fontSize = 12.sp)
                BasicTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { ch -> ch.isDigit() }.take(3) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(color = MarketBrown, fontFamily = BodyFont, fontSize = 16.sp),
                    cursorBrush = SolidColor(MarketGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MarketCard)
                        .padding(10.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { allowTrade = !allowTrade }
                ) {
                    Text(if (allowTrade) "☑" else "☐", color = MarketGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tausch erlauben", color = MarketBrown, fontFamily = BodyFont)
                }
                if (allowTrade) {
                    Text("Gewünschter Tausch (optional)", color = MarketBrownMuted, fontFamily = BodyFont, fontSize = 12.sp)
                    // Tausch-Wunsch: gesamtes Inventar (nicht nur Verkaufs-Tab)
                    inventory.filter {
                        it.kind != pick?.kind || it.itemId != pick?.itemId
                    }.forEach { item ->
                        val selected =
                            tradeWant?.kind == item.kind && tradeWant?.itemId == item.itemId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) MarketGold.copy(0.12f) else Color.Transparent)
                                .clickable { tradeWant = item }
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.emoji, fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(item.label, color = MarketBrown, fontFamily = BodyFont, fontSize = 12.sp)
                        }
                    }
                }
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
                        val selected = targetFriend?.userId == friend.userId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) MarketGold.copy(0.12f) else Color.Transparent)
                                .clickable { targetFriend = friend }
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(friend.petEmoji, fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(friend.nickname, color = MarketBrown, fontFamily = BodyFont)
                        }
                    }
                    if (friends.isEmpty()) {
                        Text("Keine Freunde — erst Freund hinzufügen.", color = MarketBrownMuted, fontFamily = BodyFont, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && pick != null && (!isPrivate || targetFriend != null),
                onClick = {
                    val selected = pick ?: return@TextButton
                    val price = priceText.toIntOrNull() ?: 0
                    if (!allowTrade && price < 1) {
                        Toast.makeText(context, "Preis mind. 1 Coin oder Tausch", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    if (allowTrade && tradeWant == null) {
                        Toast.makeText(context, "Beim Tausch Gesuch auswählen", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    busy = true
                    scope.launch {
                        runCatching {
                            LuvApiClient.listMarketItem(
                                kind = selected.kind,
                                itemId = selected.itemId,
                                priceCoins = price,
                                allowTrade = allowTrade,
                                isPrivate = isPrivate,
                                targetUserId = targetFriend?.userId,
                                tradeWantKind = tradeWant?.kind,
                                tradeWantItemId = tradeWant?.itemId,
                                tradeWantLabel = tradeWant?.label
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
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text("Abbrechen", color = MarketBrownMuted, fontFamily = BodyFont)
            }
        }
    )
}

@Composable
private fun TradeOfferDialog(
    target: LuvApiClient.MarketListing,
    inventory: List<InventoryPick>,
    onDismiss: () -> Unit,
    onTraded: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pick by remember { mutableStateOf<InventoryPick?>(null) }
    var busy by remember { mutableStateOf(false) }
    val wanted = remember(target, inventory) {
        if (target.tradeWantKind.isNullOrBlank() || target.tradeWantItemId.isNullOrBlank()) {
            inventory
        } else {
            inventory.filter {
                it.kind == target.tradeWantKind && it.itemId == target.tradeWantItemId
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = MarketCream,
        title = {
            Text("Tausch anbieten", fontFamily = DisplayFont, color = MarketBrown)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Du bekommst: ${target.emoji} ${target.label}",
                    color = MarketBrown,
                    fontFamily = BodyFont
                )
                if (!target.tradeWantLabel.isNullOrBlank()) {
                    Text(
                        "Gesucht: ${target.tradeWantLabel}",
                        color = MarketGold,
                        fontFamily = BodyFont,
                        fontSize = 12.sp
                    )
                }
                Text("Dein Angebot:", color = MarketBrownMuted, fontFamily = BodyFont, fontSize = 12.sp)
                wanted.forEach { item ->
                    val selected = pick == item
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) MarketGold.copy(0.12f) else MarketCard)
                            .clickable { pick = item }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.emoji, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(item.label, color = MarketBrown, fontFamily = BodyFont)
                    }
                }
                if (wanted.isEmpty()) {
                    Text(
                        if (target.tradeWantLabel != null) {
                            "Du hast „${target.tradeWantLabel}“ nicht im Inventar."
                        } else {
                            "Nichts zum Tauschen im Inventar."
                        },
                        color = MarketBrownMuted,
                        fontFamily = BodyFont
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && pick != null,
                onClick = {
                    val offer = pick ?: return@TextButton
                    busy = true
                    scope.launch {
                        runCatching {
                            LuvApiClient.tradeMarketListing(
                                listingId = target.id,
                                kind = offer.kind,
                                itemId = offer.itemId
                            )
                        }.onSuccess { onTraded() }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "Tausch fehlgeschlagen",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        busy = false
                    }
                }
            ) {
                Text("Tauschen", color = MarketGold, fontFamily = DisplayFont)
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text("Abbrechen", color = MarketBrownMuted, fontFamily = BodyFont)
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
    else -> "= stabil"
}
