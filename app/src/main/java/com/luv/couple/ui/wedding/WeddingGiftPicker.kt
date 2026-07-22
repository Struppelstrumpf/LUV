package com.luv.couple.ui.wedding

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.LuvApp
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.LuvApiException
import com.luv.couple.shop.ItemLabels
import com.luv.couple.shop.ShopCatalog
import com.luv.couple.ui.ItemGlyph
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.launch

private data class GiftPick(
    val kind: String,
    val itemId: String,
    val label: String,
    val count: Int
)

@Composable
fun WeddingGiftPickerDialog(
    targetUserId: String,
    onDismiss: () -> Unit,
    onGifted: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = LuvApp.instance.prefs
    val scope = rememberCoroutineScope()
    val ownedEmojis by prefs.ownedEmojisFlow.collectAsStateWithLifecycle(emptyMap())
    val ownedStickers by prefs.ownedStickersFlow.collectAsStateWithLifecycle(emptyMap())
    val ownedThemes by prefs.ownedThemesFlow.collectAsStateWithLifecycle(emptyList())
    val ownedPets by prefs.ownedPetsFlow.collectAsStateWithLifecycle(emptyMap())
    var tab by remember { mutableStateOf("stickers") }
    var busy by remember { mutableStateOf(false) }

    fun labelFor(kind: String, id: String): String {
        val labeled = ItemLabels.forKind(kind, id)
        if (!ItemLabels.looksLikeRawId(labeled)) return labeled
        return ItemLabels.toastSafe(id, kind)
    }

    val inventory = remember(ownedEmojis, ownedStickers, ownedThemes, ownedPets, tab) {
        when (tab) {
            "emojis" -> ownedEmojis
                .filter { it.value > 0 }
                .map { (id, n) -> GiftPick("emojis", id, labelFor("emojis", id), n) }
                .sortedBy { it.label.lowercase() }
            "stickers" -> ownedStickers
                .filter { it.value > 0 }
                .map { (id, n) -> GiftPick("stickers", id, labelFor("stickers", id), n) }
                .sortedBy { it.label.lowercase() }
            "themes" -> ownedThemes
                .filter { it != "meadow" }
                .distinct()
                .map { id ->
                    val theme = ShopCatalog.THEMES.find { it.id == id }
                    val name = theme?.label?.takeIf { it.isNotBlank() }
                        ?: labelFor("themes", id)
                    GiftPick("themes", id, name, 1)
                }
            "pets" -> ownedPets
                .filter { it.key != ShopCatalog.DEFAULT_PET && it.value > 0 }
                .map { (id, n) -> GiftPick("pets", id, labelFor("pets", id), n) }
                .sortedBy { it.label.lowercase() }
            else -> emptyList()
        }
    }

    fun syncLocalInventory() {
        scope.launch {
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
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = BgSoft,
        title = {
            Text("Geschenk wählen", fontFamily = DisplayFont, color = TextPrimary)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Dein Item wandert in den gemeinsamen Topf des Ehepaars.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "stickers" to "Sticker",
                        "emojis" to "Emojis",
                        "pets" to "Pets",
                        "themes" to "Themes"
                    ).forEach { (id, label) ->
                        val on = tab == id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (on) AccentRose.copy(0.25f) else TextPrimary.copy(0.06f))
                                .clickable(enabled = !busy) { tab = id }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                label,
                                color = TextPrimary,
                                fontFamily = if (on) DisplayFont else BodyFont,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                if (inventory.isEmpty()) {
                    Text(
                        "Nichts in dieser Kategorie.",
                        color = TextMuted,
                        fontFamily = BodyFont
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(inventory, key = { "${it.kind}:${it.itemId}" }) { item ->
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(TextPrimary.copy(0.06f))
                                    .clickable(enabled = !busy) {
                                        busy = true
                                        scope.launch {
                                            runCatching {
                                                LuvApiClient.weddingGift(
                                                    targetUserId,
                                                    item.kind,
                                                    item.itemId
                                                )
                                            }.onSuccess {
                                                syncLocalInventory()
                                                Toast.makeText(
                                                    context,
                                                    "Geschenk im Topf 🎁",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                onGifted()
                                                onDismiss()
                                            }.onFailure { e ->
                                                val msg = (e as? LuvApiException)?.message
                                                    ?: e.message
                                                    ?: "Schenken fehlgeschlagen"
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                            busy = false
                                        }
                                    }
                                    .padding(8.dp)
                                    .aspectRatio(0.85f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ItemGlyph(id = item.itemId, fontSize = 28.sp)
                                Text(
                                    item.label,
                                    color = TextPrimary,
                                    fontFamily = BodyFont,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                if (item.count > 1) {
                                    Text(
                                        "×${item.count}",
                                        color = TextMuted,
                                        fontFamily = BodyFont,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Abbrechen", fontFamily = BodyFont)
            }
        }
    )
}
