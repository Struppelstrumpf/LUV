package com.luv.couple.ui.wedding

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.LuvApp
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.LuvApiException
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

    val inventory = remember(ownedEmojis, ownedStickers, ownedThemes, ownedPets, tab) {
        when (tab) {
            "emojis" -> ownedEmojis
                .filter { it.value > 0 }
                .map { (id, n) -> GiftPick("emojis", id, id, n) }
                .sortedBy { it.itemId }
            "stickers" -> ownedStickers
                .filter { it.value > 0 }
                .map { (id, n) -> GiftPick("stickers", id, id, n) }
                .sortedBy { it.itemId }
            "themes" -> ownedThemes
                .filter { it != "meadow" }
                .distinct()
                .map { id ->
                    val theme = ShopCatalog.THEMES.find { it.id == id }
                    GiftPick("themes", id, "${theme?.emoji ?: "🖼️"} ${theme?.label ?: id}", 1)
                }
            "pets" -> ownedPets
                .filter { it.key != ShopCatalog.DEFAULT_PET && it.value > 0 }
                .map { (id, n) -> GiftPick("pets", id, id, n) }
                .sortedBy { it.itemId }
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
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
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
                    inventory.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(TextPrimary.copy(0.05f))
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
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ItemGlyph(id = item.itemId, fontSize = 22.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.label, color = TextPrimary, fontFamily = BodyFont)
                                if (item.count > 1) {
                                    Text(
                                        "×${item.count}",
                                        color = TextMuted,
                                        fontFamily = BodyFont,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Text("Schenken", color = AccentRose, fontFamily = DisplayFont, fontSize = 13.sp)
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
