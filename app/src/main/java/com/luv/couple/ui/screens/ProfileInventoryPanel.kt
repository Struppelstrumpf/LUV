package com.luv.couple.ui.screens

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.luv.couple.shop.LiveShopCatalog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luv.couple.profile.ProfileCatalog
import com.luv.couple.profile.ProfileTheme
import com.luv.couple.profile.ProfileThemeBackdrop
import com.luv.couple.net.LuvApiClient
import com.luv.couple.shop.InventoryAvailability
import com.luv.couple.shop.ItemLabels
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary

/** Referenzbreite für Scale 1.0 — darunter proportional kleiner. */
private val RefInventoryWidth = 390.dp

enum class InventoryPanelMode { Menu, ProfileChest }

private enum class InvTab(val label: String, val kindPrefix: String?) {
    Stickers("Sticker", "stickers"),
    Themes("Hintergründe", "themes"),
    Companions("Begleiter", "pets"),
    Emojis("Emojis", "emojis"),
    Extras("Extras", null)
}

/**
 * Gleiche Reihenfolge wie Itemshop: Sticker → Hintergründe → Begleiter → Emojis.
 * Profil-Truhe: Extras statt Emojis (Münzglas/Bio).
 */
private fun tabsFor(mode: InventoryPanelMode, readOnly: Boolean): List<InvTab> = when {
    readOnly && mode == InventoryPanelMode.ProfileChest -> listOf(InvTab.Extras)
    mode == InventoryPanelMode.Menu -> listOf(
        InvTab.Stickers, InvTab.Themes, InvTab.Companions, InvTab.Emojis
    )
    else -> listOf(
        InvTab.Stickers, InvTab.Themes, InvTab.Companions, InvTab.Extras
    )
}

/**
 * Gemeinsames Inventar (Profil-Truhe + Hauptmenü).
 * Tabs 0–2 identisch mit Itemshop; Menü-Tab 3 = Emojis, Profil-Tab 3 = Extras.
 */
@Composable
fun ProfileInventoryPanel(
    mode: InventoryPanelMode,
    ownedStickers: Map<String, Int>,
    ownedEmojis: Map<String, Int> = emptyMap(),
    ownedThemes: List<String> = listOf(ProfileCatalog.DEFAULT_THEME_ID),
    ownedPets: List<String> = listOf("🐣"),
    /** Auf Profil platzierte Sticker (emoji → Anzahl) — reduziert freie Anzeige */
    placedStickers: Map<String, Int> = emptyMap(),
    /** Emojis in der Reaktionsleiste — je 1× als in Verwendung */
    emojiBar: Collection<String> = emptyList(),
    currentThemeId: String,
    currentCompanion: String,
    hasGlass: Boolean,
    hasBio: Boolean,
    hasStreak: Boolean = false,
    spouseName: String? = null,
    engagedName: String? = null,
    hasSpouse: Boolean = false,
    hasEngaged: Boolean = false,
    /** Day-Streak (🔥) — auch für Profilbesucher sichtbar */
    dayStreak: Int = 0,
    /** Fremdprofil: nur anschauen, nichts platzieren */
    readOnly: Boolean = false,
    onTheme: (ProfileTheme) -> Unit,
    onSticker: (String) -> Unit,
    onCompanion: (String) -> Unit,
    onEmoji: (String) -> Unit = {},
    onGlass: () -> Unit = {},
    onBio: () -> Unit = {},
    onStreak: () -> Unit = {},
    onSpouse: () -> Unit = {},
    onEngaged: () -> Unit = {},
    onOpenMarketplace: () -> Unit,
    onOpenItemShop: () -> Unit,
    onOpenGallery: (() -> Unit)? = null,
    selectedTab: Int = 0,
    onTabChange: (Int) -> Unit = {},
    /** Item-Keys mit Session-Glow, z. B. "stickers:🦋" */
    highlightKeys: Set<String> = emptySet(),
    /** Noch ungesehen (Tab-Punkte), z. B. "themes:night" */
    unseenKeys: Set<String> = emptySet(),
    /** Tab besucht → Kind als gesehen melden ("stickers", "themes", …) */
    onKindVisited: (String) -> Unit = {},
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showCardChrome: Boolean = true
) {
    val tabs = remember(mode, readOnly) { tabsFor(mode, readOnly) }
    var tab by remember(mode, readOnly) {
        mutableIntStateOf(
            if (readOnly) 0 else selectedTab.coerceIn(0, tabs.lastIndex.coerceAtLeast(0))
        )
    }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var labelsEpoch by remember { mutableIntStateOf(LiveShopCatalog.displayLabelsEpoch) }
    LaunchedEffect(Unit) {
        runCatching { LuvApiClient.refreshItemDisplayLabels() }
        labelsEpoch = LiveShopCatalog.displayLabelsEpoch
    }
    LaunchedEffect(selectedTab, tabs.size) {
        tab = selectedTab.coerceIn(0, tabs.lastIndex)
    }
    LaunchedEffect(tab, tabs) {
        tabs.getOrNull(tab)?.kindPrefix?.let(onKindVisited)
    }
    // Frei = Besitz − in Verwendung (platziert / Leiste / ausgerüstet) — für Ausgrauung
    val freeStickers = remember(ownedStickers, placedStickers) {
        InventoryAvailability.freeStickers(ownedStickers, placedStickers)
    }
    val freeEmojis = remember(ownedEmojis, emojiBar) {
        InventoryAvailability.freeEmojis(ownedEmojis, emojiBar)
    }
    val themeItems = remember(ownedThemes, searchQuery, labelsEpoch, LiveShopCatalog.remoteThemes) {
        ownedThemes
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .map { ProfileCatalog.theme(it) }
            .filter {
                LiveShopCatalog.matchesQuery(
                    searchQuery,
                    it.emoji,
                    ItemLabels.themeLabel(it.id)
                )
            }
    }
    val petItems = remember(ownedPets, searchQuery, labelsEpoch) {
        ownedPets.map { it.trim() }.filter { it.isNotEmpty() }.distinct().ifEmpty {
            if (readOnly) listOf("🐣") else emptyList()
        }.filter { id ->
            val label = ItemLabels.petLabel(id)
            val search = LiveShopCatalog.remotePets?.firstOrNull { it.emoji == id }?.searchText.orEmpty()
            LiveShopCatalog.matchesQuery(searchQuery, id, label, search)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val scale = (maxWidth / RefInventoryWidth).coerceIn(0.62f, 1f)
        fun s(dp: Dp): Dp = dp * scale
        fun ts(sp: TextUnit): TextUnit = (sp.value * scale).sp

        val body = Modifier
            .fillMaxSize()
            .then(
                if (showCardChrome) {
                    Modifier
                        .clip(RoundedCornerShape(s(28.dp)))
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFF1C2433), BgDeep))
                        )
                        .border(1.dp, Color.White.copy(0.10f), RoundedCornerShape(s(28.dp)))
                        .padding(horizontal = s(14.dp), vertical = s(14.dp))
                } else {
                    Modifier.padding(horizontal = s(8.dp))
                }
            )

        Column(modifier = body) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎒", fontSize = ts(22.sp))
                Spacer(modifier = Modifier.width(s(8.dp)))
                Text(
                    "Dein Inventar",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = ts(22.sp),
                    modifier = Modifier.weight(1f)
                )
                ShopSearchIconButton(
                    expanded = searchOpen,
                    onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) searchQuery = ""
                    }
                )
                if (onDismiss != null) {
                    Spacer(modifier = Modifier.width(s(8.dp)))
                    Box(
                        modifier = Modifier
                            .size(s(32.dp))
                            .clip(CircleShape)
                            .background(Color.White.copy(0.12f))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) { Text("✕", color = TextMuted, fontSize = ts(14.sp)) }
                }
            }
            if (searchOpen) {
                Spacer(modifier = Modifier.height(s(10.dp)))
                ShopSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Inventar filtern…"
                )
            }
            Spacer(modifier = Modifier.height(s(12.dp)))
            Row(horizontalArrangement = Arrangement.spacedBy(s(8.dp))) {
                ShopLinkChip("🛒 Marktplatz", onOpenMarketplace, Modifier.weight(1f), scale)
                ShopLinkChip("✨ Itemshop", onOpenItemShop, Modifier.weight(1f), scale)
                if (onOpenGallery != null) {
                    ShopLinkChip("🖼️ Galerie", onOpenGallery, Modifier.weight(1f), scale)
                }
            }
            Spacer(modifier = Modifier.height(s(12.dp)))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(s(6.dp))
            ) {
                tabs.forEachIndexed { i, invTab ->
                    val on = tab == i
                    val tabHasNew = invTab.kindPrefix?.let { prefix ->
                        unseenKeys.any { it.startsWith("$prefix:") }
                    } == true
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(s(12.dp)))
                            .background(if (on) AccentRose.copy(0.28f) else Color.White.copy(0.06f))
                            .clickable {
                                tab = i
                                onTabChange(i)
                            }
                            .padding(vertical = s(9.dp), horizontal = s(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            val base = 10f * scale
                            val fontSp = when {
                                invTab.label.length > 10 && maxWidth < 72.dp -> (base * 0.78f).sp
                                invTab.label.length > 8 && maxWidth < 84.dp -> (base * 0.88f).sp
                                else -> base.sp
                            }
                            Text(
                                invTab.label,
                                color = TextPrimary,
                                fontFamily = if (on) DisplayFont else BodyFont,
                                fontSize = fontSp,
                                softWrap = false,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (tabHasNew) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = s(2.dp), end = s(4.dp))
                                    .size(s(7.dp))
                                    .clip(CircleShape)
                                    .background(AccentRose)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(s(10.dp)))

            val gridMod = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .fillMaxHeight()
            val q = searchQuery

            when (tabs.getOrNull(tab)) {
                InvTab.Emojis -> {
                    val entries = ownedEmojis.entries
                        .filter { LiveShopCatalog.matchesQuery(q, it.key, ItemLabels.emojiLabel(it.key)) }
                        .sortedBy { it.key }
                    val freeEntries = if (readOnly) entries else entries.filter { (freeEmojis[it.key] ?: 0) > 0 }
                    val dimmedEntries = if (readOnly) emptyList() else entries.filter { (freeEmojis[it.key] ?: 0) <= 0 }
                    if (entries.isEmpty()) {
                        Box(modifier = gridMod, contentAlignment = Alignment.Center) {
                            Text(
                                "Noch keine Emojis — im Itemshop unter Emojis kaufen.",
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = ts(13.sp),
                                modifier = Modifier.padding(s(12.dp))
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            modifier = gridMod,
                            contentPadding = PaddingValues(s(4.dp)),
                            horizontalArrangement = Arrangement.spacedBy(s(8.dp)),
                            verticalArrangement = Arrangement.spacedBy(s(8.dp))
                        ) {
                            @Composable
                            fun EmojiCell(emoji: String, count: Int, dimmed: Boolean) {
                                val free = freeEmojis[emoji] ?: 0
                                val itemKey = "emojis:$emoji"
                                ItemNewGlowBorder(
                                    active = itemKey in highlightKeys,
                                    corner = s(14.dp),
                                    modifier = Modifier.aspectRatio(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(s(14.dp)))
                                            .background(BgSoft)
                                            .alpha(if (dimmed) 0.38f else 1f)
                                            .clickable(enabled = readOnly || free > 0) { onEmoji(emoji) }
                                            .padding(s(6.dp))
                                    ) {
                                        com.luv.couple.ui.ItemGlyph(
                                            id = emoji,
                                            fontSize = ts(26.sp),
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                        if (count > 0) {
                                            Text(
                                                "×$count",
                                                color = TextMuted,
                                                fontFamily = BodyFont,
                                                fontSize = ts(10.sp),
                                                modifier = Modifier.align(Alignment.BottomEnd)
                                            )
                                        }
                                    }
                                }
                            }
                            items(freeEntries, key = { "free-${it.key}" }) { (emoji, count) ->
                                EmojiCell(emoji, count, dimmed = false)
                            }
                            invDimmedGap(freeEntries.isNotEmpty() && dimmedEntries.isNotEmpty(), s(14.dp))
                            items(dimmedEntries, key = { "dim-${it.key}" }) { (emoji, count) ->
                                EmojiCell(emoji, count, dimmed = true)
                            }
                        }
                    }
                }
                InvTab.Themes -> if (themeItems.isEmpty()) {
                    InvEmptyHint(
                        title = "Noch keine Hintergründe.",
                        body = "Im Itemshop unter Hintergründe kaufen.",
                        onOpenItemShop = onOpenItemShop,
                        scale = scale,
                        modifier = gridMod
                    )
                } else {
                    val freeThemes = if (readOnly) themeItems
                    else themeItems.filter { it.id != currentThemeId }
                    val dimmedThemes = if (readOnly) emptyList()
                    else themeItems.filter { it.id == currentThemeId }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = gridMod,
                        horizontalArrangement = Arrangement.spacedBy(s(8.dp)),
                        verticalArrangement = Arrangement.spacedBy(s(8.dp)),
                        contentPadding = PaddingValues(bottom = s(4.dp))
                    ) {
                        @Composable
                        fun ThemeCell(theme: ProfileTheme, dimmed: Boolean) {
                            val on = theme.id == currentThemeId
                            val itemKey = "themes:${theme.id}"
                            ItemNewGlowBorder(
                                active = itemKey in highlightKeys,
                                corner = s(14.dp),
                                modifier = Modifier.aspectRatio(1.12f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(s(14.dp)))
                                        .alpha(if (dimmed) 0.45f else 1f)
                                        .border(
                                            1.dp,
                                            if (on) AccentRose.copy(0.85f) else Color.White.copy(0.08f),
                                            RoundedCornerShape(s(14.dp))
                                        )
                                        .clickable(enabled = readOnly || !on) { onTheme(theme) }
                                ) {
                                    ProfileThemeBackdrop(
                                        theme = theme,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(0.32f))
                                            .padding(vertical = s(4.dp), horizontal = s(4.dp)),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        com.luv.couple.ui.ItemGlyph(
                                            id = theme.emoji,
                                            fontSize = ts(16.sp)
                                        )
                                        Text(
                                            ItemLabels.themeLabel(theme.id),
                                            color = Color.White,
                                            fontFamily = BodyFont,
                                            fontSize = ts(10.sp),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                        items(freeThemes, key = { "free-${it.id}" }) { theme ->
                            ThemeCell(theme, dimmed = false)
                        }
                        invDimmedGap(freeThemes.isNotEmpty() && dimmedThemes.isNotEmpty(), s(14.dp))
                        items(dimmedThemes, key = { "dim-${it.id}" }) { theme ->
                            ThemeCell(theme, dimmed = true)
                        }
                    }
                }
                InvTab.Stickers -> {
                    val stickerEntries = ownedStickers.entries
                        .filter { LiveShopCatalog.matchesQuery(q, it.key, ItemLabels.stickerLabel(it.key)) }
                        .sortedBy { it.key }
                    val freeEntries = if (readOnly) stickerEntries
                    else stickerEntries.filter { (freeStickers[it.key] ?: 0) > 0 }
                    val dimmedEntries = if (readOnly) emptyList()
                    else stickerEntries.filter { (freeStickers[it.key] ?: 0) <= 0 }
                    if (stickerEntries.isEmpty()) {
                        InvEmptyHint(
                            title = "Noch keine Sticker.",
                            body = "Im Itemshop unter Sticker kaufen — z. B. Schmetterling.",
                            onOpenItemShop = onOpenItemShop,
                            scale = scale,
                            modifier = gridMod
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            modifier = gridMod,
                            contentPadding = PaddingValues(s(4.dp)),
                            horizontalArrangement = Arrangement.spacedBy(s(8.dp)),
                            verticalArrangement = Arrangement.spacedBy(s(8.dp))
                        ) {
                            @Composable
                            fun StickerCell(emoji: String, count: Int, dimmed: Boolean) {
                                val free = freeStickers[emoji] ?: 0
                                val itemKey = "stickers:$emoji"
                                ItemNewGlowBorder(
                                    active = itemKey in highlightKeys,
                                    corner = s(14.dp),
                                    modifier = Modifier.aspectRatio(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(s(14.dp)))
                                            .background(BgSoft)
                                            .alpha(if (dimmed) 0.38f else 1f)
                                            .clickable(enabled = readOnly || free > 0) { onSticker(emoji) }
                                            .padding(s(4.dp))
                                    ) {
                                        com.luv.couple.ui.ItemGlyph(
                                            id = emoji,
                                            fontSize = ts(26.sp),
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                        if (count > 0) {
                                            Text(
                                                "×$count",
                                                color = TextMuted,
                                                fontFamily = BodyFont,
                                                fontSize = ts(10.sp),
                                                modifier = Modifier.align(Alignment.BottomEnd)
                                            )
                                        }
                                    }
                                }
                            }
                            items(freeEntries, key = { "free-${it.key}" }) { (emoji, count) ->
                                StickerCell(emoji, count, dimmed = false)
                            }
                            invDimmedGap(freeEntries.isNotEmpty() && dimmedEntries.isNotEmpty(), s(14.dp))
                            items(dimmedEntries, key = { "dim-${it.key}" }) { (emoji, count) ->
                                StickerCell(emoji, count, dimmed = true)
                            }
                        }
                    }
                }
                InvTab.Companions -> if (petItems.isEmpty()) {
                    Box(modifier = gridMod, contentAlignment = Alignment.Center) {
                        Text(
                            if (readOnly) "Kein Begleiter."
                            else "Noch keine Begleiter — im Itemshop kaufen.",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = ts(13.sp),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(s(12.dp))
                        )
                    }
                } else {
                    val freePets = if (readOnly) petItems
                    else petItems.filter { it != currentCompanion }
                    val dimmedPets = if (readOnly) emptyList()
                    else petItems.filter { it == currentCompanion }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = gridMod,
                        horizontalArrangement = Arrangement.spacedBy(s(8.dp)),
                        verticalArrangement = Arrangement.spacedBy(s(8.dp))
                    ) {
                        @Composable
                        fun PetCell(emoji: String, dimmed: Boolean) {
                            val equipped = emoji == currentCompanion
                            val itemKey = "pets:$emoji"
                            ItemNewGlowBorder(
                                active = itemKey in highlightKeys,
                                corner = s(14.dp),
                                modifier = Modifier.aspectRatio(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(s(14.dp)))
                                        .background(BgSoft)
                                        .alpha(if (dimmed) 0.38f else 1f)
                                        .border(
                                            1.dp,
                                            if (equipped) AccentRose.copy(0.55f) else Color.White.copy(0.08f),
                                            RoundedCornerShape(s(14.dp))
                                        )
                                        .clickable(enabled = readOnly || !equipped) { onCompanion(emoji) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    com.luv.couple.ui.CompanionGlyph(
                                        petId = emoji,
                                        fontSize = ts(28.sp)
                                    )
                                }
                            }
                        }
                        items(freePets, key = { "free-$it" }) { emoji ->
                            PetCell(emoji, dimmed = false)
                        }
                        invDimmedGap(freePets.isNotEmpty() && dimmedPets.isNotEmpty(), s(14.dp))
                        items(dimmedPets, key = { "dim-$it" }) { emoji ->
                            PetCell(emoji, dimmed = true)
                        }
                    }
                }
                InvTab.Extras -> Column(
                    modifier = gridMod,
                    verticalArrangement = Arrangement.spacedBy(s(10.dp))
                ) {
                    // Day Streak — platzierbar auf der Profilleinwand (Extras)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(s(16.dp)))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF2A1A22), BgSoft, Color(0xFF1E2430))
                                )
                            )
                            .border(
                                1.dp,
                                if (hasStreak) AccentRose.copy(0.55f) else AccentRose.copy(0.35f),
                                RoundedCornerShape(s(16.dp))
                            )
                            .then(
                                if (!readOnly) Modifier.clickable(onClick = onStreak)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(s(14.dp)),
                            modifier = Modifier.padding(horizontal = s(16.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(s(56.dp))
                                    .clip(CircleShape)
                                    .background(AccentRose.copy(0.18f))
                                    .border(1.dp, AccentRose.copy(0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🔥", fontSize = ts(18.sp))
                                    Text(
                                        "$dayStreak",
                                        color = AccentRose,
                                        fontFamily = DisplayFont,
                                        fontSize = ts(16.sp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    "Day Streak",
                                    color = TextPrimary,
                                    fontFamily = DisplayFont,
                                    fontSize = ts(16.sp)
                                )
                                Text(
                                    when {
                                        readOnly && dayStreak <= 0 -> "Noch kein Streak"
                                        readOnly && dayStreak == 1 -> "1 Tag in Folge"
                                        readOnly -> "$dayStreak Tage in Folge"
                                        hasStreak -> "Auf der Leinwand · tippen zum Auswählen"
                                        dayStreak <= 0 -> "Platzieren (noch 0 Tage)"
                                        dayStreak == 1 -> "Feuer-Kreis platzieren · 1 Tag"
                                        else -> "Feuer-Kreis platzieren · $dayStreak Tage"
                                    },
                                    color = TextMuted,
                                    fontFamily = BodyFont,
                                    fontSize = ts(13.sp)
                                )
                            }
                        }
                    }
                    if (!readOnly) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(s(16.dp)))
                            .background(if (hasGlass) AccentRose.copy(0.18f) else BgSoft)
                            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(s(16.dp)))
                            .clickable(onClick = onGlass),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🏺", fontSize = ts(34.sp))
                            Spacer(modifier = Modifier.height(s(6.dp)))
                            Text(
                                if (hasGlass) "Münzglas auf der Leinwand" else "Münzglas platzieren",
                                color = TextPrimary,
                                fontFamily = DisplayFont,
                                fontSize = ts(14.sp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(s(16.dp)))
                            .background(if (hasBio) AccentRose.copy(0.18f) else BgSoft)
                            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(s(16.dp)))
                            .clickable(onClick = onBio),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📝", fontSize = ts(34.sp))
                            Spacer(modifier = Modifier.height(s(6.dp)))
                            Text(
                                if (hasBio) "Bio auf der Leinwand" else "Bio platzieren",
                                color = TextPrimary,
                                fontFamily = DisplayFont,
                                fontSize = ts(14.sp)
                            )
                        }
                    }
                    }
                    if (!readOnly && !spouseName.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(s(16.dp)))
                                .background(if (hasSpouse) Color(0x33FFD54F) else BgSoft)
                                .border(1.dp, Color(0xFFFFD54F).copy(0.55f), RoundedCornerShape(s(16.dp)))
                                .clickable(onClick = onSpouse),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("💍", fontSize = ts(34.sp))
                                Spacer(modifier = Modifier.height(s(6.dp)))
                                Text(
                                    if (hasSpouse) "Ehepartner auf der Leinwand"
                                    else "Ehepartner: $spouseName",
                                    color = TextPrimary,
                                    fontFamily = DisplayFont,
                                    fontSize = ts(14.sp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    if (!readOnly && !engagedName.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(s(16.dp)))
                                .background(if (hasEngaged) AccentRose.copy(0.18f) else BgSoft)
                                .border(1.dp, AccentRose.copy(0.45f), RoundedCornerShape(s(16.dp)))
                                .clickable(onClick = onEngaged),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("💝", fontSize = ts(34.sp))
                                Spacer(modifier = Modifier.height(s(6.dp)))
                                Text(
                                    if (hasEngaged) "Verlobte:r auf der Leinwand"
                                    else "Verlobte:r: $engagedName",
                                    color = TextPrimary,
                                    fontFamily = DisplayFont,
                                    fontSize = ts(14.sp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                null -> Unit
            }
        }
    }
}

/** Abstand zwischen freien und ausgegrauten (platzierten/ausgerüsteten) Items. */
private fun LazyGridScope.invDimmedGap(show: Boolean, height: Dp) {
    if (!show) return
    item(span = { GridItemSpan(maxLineSpan) }) {
        Spacer(modifier = Modifier.height(height))
    }
}

@Composable
fun ProfileChestDialog(
    ownedStickers: Map<String, Int>,
    ownedThemes: List<String> = listOf(ProfileCatalog.DEFAULT_THEME_ID),
    ownedPets: List<String> = listOf("🐣"),
    placedStickers: Map<String, Int> = emptyMap(),
    emojiBar: Collection<String> = emptyList(),
    currentThemeId: String,
    currentCompanion: String,
    hasGlass: Boolean,
    hasBio: Boolean,
    hasStreak: Boolean = false,
    spouseName: String? = null,
    engagedName: String? = null,
    hasSpouse: Boolean = false,
    hasEngaged: Boolean = false,
    dayStreak: Int = 0,
    readOnly: Boolean = false,
    onTheme: (ProfileTheme) -> Unit,
    onSticker: (String) -> Unit,
    onCompanion: (String) -> Unit,
    onGlass: () -> Unit,
    onBio: () -> Unit,
    onStreak: () -> Unit = {},
    onSpouse: () -> Unit = {},
    onEngaged: () -> Unit = {},
    onOpenMarketplace: () -> Unit,
    onOpenItemShop: () -> Unit,
    selectedTab: Int = 0,
    onTabChange: (Int) -> Unit = {},
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ProfileInventoryPanel(
            mode = InventoryPanelMode.ProfileChest,
            ownedStickers = ownedStickers,
            ownedThemes = ownedThemes,
            ownedPets = ownedPets,
            placedStickers = placedStickers,
            emojiBar = emojiBar,
            currentThemeId = currentThemeId,
            currentCompanion = currentCompanion,
            hasGlass = hasGlass,
            hasBio = hasBio,
            hasStreak = hasStreak,
            spouseName = spouseName,
            engagedName = engagedName,
            hasSpouse = hasSpouse,
            hasEngaged = hasEngaged,
            dayStreak = dayStreak,
            readOnly = readOnly,
            onTheme = onTheme,
            onSticker = onSticker,
            onCompanion = onCompanion,
            onGlass = onGlass,
            onBio = onBio,
            onStreak = onStreak,
            onSpouse = onSpouse,
            onEngaged = onEngaged,
            onOpenMarketplace = onOpenMarketplace,
            onOpenItemShop = onOpenItemShop,
            selectedTab = if (readOnly) 0 else selectedTab,
            onTabChange = onTabChange,
            onDismiss = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            showCardChrome = true
        )
    }
}

@Composable
private fun InvEmptyHint(
    title: String,
    body: String,
    onOpenItemShop: () -> Unit,
    scale: Float,
    modifier: Modifier = Modifier
) {
    fun s(dp: Dp): Dp = dp * scale
    fun ts(sp: TextUnit): TextUnit = (sp.value * scale).sp
    Column(
        modifier = modifier.padding(horizontal = s(16.dp), vertical = s(12.dp)),
        verticalArrangement = Arrangement.spacedBy(s(10.dp)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            color = TextPrimary,
            fontFamily = DisplayFont,
            fontSize = ts(16.sp),
            textAlign = TextAlign.Center
        )
        Text(
            body,
            color = TextMuted,
            fontFamily = BodyFont,
            fontSize = ts(13.sp),
            textAlign = TextAlign.Center
        )
        ShopLinkChip(label = "Zum Itemshop", onClick = onOpenItemShop, scale = scale)
    }
}

@Composable
private fun ShopLinkChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f
) {
    fun s(dp: Dp): Dp = dp * scale
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(s(14.dp)))
            .background(AccentRose.copy(0.16f))
            .border(1.dp, AccentRose.copy(0.35f), RoundedCornerShape(s(14.dp)))
            .clickable(onClick = onClick)
            .padding(horizontal = s(12.dp), vertical = s(11.dp)),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val base = 13f * scale
            val fontSp = when {
                maxWidth < 68.dp -> (base * 0.78f).sp
                maxWidth < 92.dp -> (base * 0.88f).sp
                else -> base.sp
            }
            Text(
                label,
                color = TextPrimary,
                fontFamily = BodyFont,
                fontSize = fontSp,
                softWrap = false,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}
