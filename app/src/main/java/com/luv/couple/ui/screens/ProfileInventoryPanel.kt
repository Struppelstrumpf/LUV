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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luv.couple.profile.ProfileCatalog
import com.luv.couple.profile.ProfileTheme
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

private enum class InvTab(val label: String) {
    Emojis("Emojis"),
    Themes("Hintergrund"),
    Stickers("Sticker"),
    Companions("Begleiter"),
    Extras("Extras")
}

private fun tabsFor(mode: InventoryPanelMode): List<InvTab> = when (mode) {
    InventoryPanelMode.Menu -> listOf(
        InvTab.Emojis, InvTab.Themes, InvTab.Stickers, InvTab.Companions
    )
    InventoryPanelMode.ProfileChest -> listOf(
        InvTab.Themes, InvTab.Stickers, InvTab.Companions, InvTab.Extras
    )
}

/**
 * Gemeinsames Inventar (Profil-Truhe + Hauptmenü).
 * Menü: Emojis zuerst, ohne Extras. Profil: Extras, ohne Emojis.
 */
@Composable
fun ProfileInventoryPanel(
    mode: InventoryPanelMode,
    ownedStickers: List<String>,
    ownedEmojis: Map<String, Int> = emptyMap(),
    ownedThemes: List<String> = listOf(ProfileCatalog.DEFAULT_THEME_ID),
    ownedPets: List<String> = listOf("🐣"),
    currentThemeId: String,
    currentCompanion: String,
    hasGlass: Boolean,
    hasBio: Boolean,
    onTheme: (ProfileTheme) -> Unit,
    onSticker: (String) -> Unit,
    onCompanion: (String) -> Unit,
    onEmoji: (String) -> Unit = {},
    onGlass: () -> Unit = {},
    onBio: () -> Unit = {},
    onOpenMarketplace: () -> Unit,
    onOpenItemShop: () -> Unit,
    onOpenGallery: (() -> Unit)? = null,
    selectedTab: Int = 0,
    onTabChange: (Int) -> Unit = {},
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showCardChrome: Boolean = true
) {
    val tabs = remember(mode) { tabsFor(mode) }
    var tab by remember(mode) { mutableIntStateOf(selectedTab.coerceIn(0, tabs.lastIndex)) }
    LaunchedEffect(selectedTab, tabs.size) {
        tab = selectedTab.coerceIn(0, tabs.lastIndex)
    }
    val themeItems = remember(ownedThemes) {
        val owned = ownedThemes.toSet()
        ProfileCatalog.THEMES.filter { it.id in owned }
    }
    val petItems = remember(ownedPets) {
        val owned = ownedPets.toSet()
        ProfileCatalog.COMPANIONS.filter { it in owned }.ifEmpty { listOf("🐣") }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val scale = (maxWidth / RefInventoryWidth).coerceIn(0.72f, 1f)
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
                        .padding(s(16.dp))
                } else {
                    Modifier.padding(horizontal = s(4.dp))
                }
            )

        Column(modifier = body) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧰", fontSize = ts(22.sp))
                Spacer(modifier = Modifier.width(s(8.dp)))
                Column(modifier = Modifier.weight(1f)) {
                    Text("TRUHE", color = TextMuted, fontFamily = BodyFont, fontSize = ts(11.sp))
                    Text(
                        "Dein Inventar",
                        color = TextPrimary,
                        fontFamily = DisplayFont,
                        fontSize = ts(22.sp)
                    )
                }
                if (onDismiss != null) {
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
            Spacer(modifier = Modifier.height(s(12.dp)))
            Row(horizontalArrangement = Arrangement.spacedBy(s(8.dp))) {
                ShopLinkChip("🛒 Marktplatz", onOpenMarketplace, Modifier.weight(1f), scale)
                ShopLinkChip("✨ Mehr Sticker", onOpenItemShop, Modifier.weight(1f), scale)
                if (onOpenGallery != null) {
                    ShopLinkChip("🖼️ Galerie", onOpenGallery, Modifier.weight(1f), scale)
                }
            }
            Spacer(modifier = Modifier.height(s(12.dp)))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(s(4.dp))
            ) {
                tabs.forEachIndexed { i, invTab ->
                    val on = tab == i
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(s(12.dp)))
                            .background(if (on) AccentRose.copy(0.28f) else Color.White.copy(0.06f))
                            .clickable {
                                tab = i
                                onTabChange(i)
                            }
                            .padding(vertical = s(9.dp), horizontal = s(2.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            invTab.label,
                            color = TextPrimary,
                            fontFamily = if (on) DisplayFont else BodyFont,
                            fontSize = ts(11.sp),
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(s(12.dp)))

            val gridMod = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .fillMaxHeight()

            when (tabs.getOrNull(tab)) {
                InvTab.Emojis -> {
                    val entries = ownedEmojis.entries.sortedBy { it.key }
                    if (entries.isEmpty()) {
                        Box(modifier = gridMod, contentAlignment = Alignment.Center) {
                            Text(
                                "Noch keine Emojis — im Itemshop gibt’s welche.",
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = ts(13.sp),
                                modifier = Modifier.padding(s(12.dp))
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(s(58.dp)),
                            modifier = gridMod,
                            contentPadding = PaddingValues(s(4.dp)),
                            horizontalArrangement = Arrangement.spacedBy(s(8.dp)),
                            verticalArrangement = Arrangement.spacedBy(s(8.dp))
                        ) {
                            items(entries, key = { it.key }) { (emoji, count) ->
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(s(14.dp)))
                                        .background(BgSoft)
                                        .clickable { onEmoji(emoji) }
                                        .padding(s(6.dp))
                                ) {
                                    Text(
                                        emoji,
                                        fontSize = ts(26.sp),
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                    if (count > 1) {
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
                    }
                }
                InvTab.Themes -> if (themeItems.isEmpty()) {
                    InvEmptyHint(
                        title = "Noch keine Hintergründe.",
                        body = "Im Itemshop unter Ausstattung kaufen.",
                        onOpenItemShop = onOpenItemShop,
                        scale = scale,
                        modifier = gridMod
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(s(80.dp)),
                        modifier = gridMod,
                        horizontalArrangement = Arrangement.spacedBy(s(8.dp)),
                        verticalArrangement = Arrangement.spacedBy(s(8.dp)),
                        contentPadding = PaddingValues(bottom = s(4.dp))
                    ) {
                        items(themeItems, key = { it.id }) { theme ->
                            val on = theme.id == currentThemeId
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(s(14.dp)))
                                    .background(if (on) AccentRose.copy(0.2f) else BgSoft)
                                    .border(
                                        1.dp,
                                        if (on) AccentRose.copy(0.65f) else Color.White.copy(0.08f),
                                        RoundedCornerShape(s(14.dp))
                                    )
                                    .clickable { onTheme(theme) }
                                    .padding(s(10.dp)),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(theme.emoji, fontSize = ts(26.sp))
                                Spacer(modifier = Modifier.height(s(4.dp)))
                                Text(
                                    theme.label,
                                    color = TextPrimary,
                                    fontFamily = BodyFont,
                                    fontSize = ts(11.sp),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                InvTab.Stickers -> if (ownedStickers.isEmpty()) {
                    InvEmptyHint(
                        title = "Noch keine Sticker.",
                        body = "Im Itemshop unter Ausstattung kaufen.",
                        onOpenItemShop = onOpenItemShop,
                        scale = scale,
                        modifier = gridMod
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(s(58.dp)),
                        modifier = gridMod,
                        contentPadding = PaddingValues(s(4.dp)),
                        horizontalArrangement = Arrangement.spacedBy(s(8.dp)),
                        verticalArrangement = Arrangement.spacedBy(s(8.dp))
                    ) {
                        items(ownedStickers, key = { it }) { emoji ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(s(14.dp)))
                                    .background(BgSoft)
                                    .clickable { onSticker(emoji) },
                                contentAlignment = Alignment.Center
                            ) { Text(emoji, fontSize = ts(26.sp)) }
                        }
                    }
                }
                InvTab.Companions -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(s(64.dp)),
                    modifier = gridMod,
                    horizontalArrangement = Arrangement.spacedBy(s(8.dp)),
                    verticalArrangement = Arrangement.spacedBy(s(8.dp))
                ) {
                    items(petItems, key = { it }) { emoji ->
                        val on = emoji == currentCompanion
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(s(14.dp)))
                                .background(if (on) AccentRose.copy(0.22f) else BgSoft)
                                .border(
                                    1.dp,
                                    if (on) AccentRose.copy(0.65f) else Color.White.copy(0.08f),
                                    RoundedCornerShape(s(14.dp))
                                )
                                .clickable { onCompanion(emoji) },
                            contentAlignment = Alignment.Center
                        ) { Text(emoji, fontSize = ts(28.sp)) }
                    }
                }
                InvTab.Extras -> Column(
                    modifier = gridMod,
                    verticalArrangement = Arrangement.spacedBy(s(10.dp))
                ) {
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
                null -> Unit
            }
        }
    }
}

@Composable
fun ProfileChestDialog(
    ownedStickers: List<String>,
    ownedThemes: List<String> = listOf(ProfileCatalog.DEFAULT_THEME_ID),
    ownedPets: List<String> = listOf("🐣"),
    currentThemeId: String,
    currentCompanion: String,
    hasGlass: Boolean,
    hasBio: Boolean,
    onTheme: (ProfileTheme) -> Unit,
    onSticker: (String) -> Unit,
    onCompanion: (String) -> Unit,
    onGlass: () -> Unit,
    onBio: () -> Unit,
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
            currentThemeId = currentThemeId,
            currentCompanion = currentCompanion,
            hasGlass = hasGlass,
            hasBio = hasBio,
            onTheme = onTheme,
            onSticker = onSticker,
            onCompanion = onCompanion,
            onGlass = onGlass,
            onBio = onBio,
            onOpenMarketplace = onOpenMarketplace,
            onOpenItemShop = onOpenItemShop,
            selectedTab = selectedTab,
            onTabChange = onTabChange,
            onDismiss = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .padding(horizontal = 14.dp)
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
        modifier = modifier.padding(s(12.dp)),
        verticalArrangement = Arrangement.spacedBy(s(10.dp)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = TextPrimary, fontFamily = DisplayFont, fontSize = ts(16.sp))
        Text(body, color = TextMuted, fontFamily = BodyFont, fontSize = ts(13.sp))
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
    fun ts(sp: TextUnit): TextUnit = (sp.value * scale).sp
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(s(14.dp)))
            .background(AccentRose.copy(0.16f))
            .border(1.dp, AccentRose.copy(0.35f), RoundedCornerShape(s(14.dp)))
            .clickable(onClick = onClick)
            .padding(vertical = s(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = TextPrimary, fontFamily = BodyFont, fontSize = ts(13.sp), maxLines = 1)
    }
}
