package com.luv.couple.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.net.LuvApiClient
import com.luv.couple.shop.LiveShopCatalog

fun isImagePetId(id: String): Boolean {
    // Nur echte Bild-Items — Emoji-Pets/Sticker niemals als Bild behandeln
    return id.trim().startsWith("img_", ignoreCase = true)
}

/** Shop-/Inventar-/Leinwand-ID: img_/theme_ bis 32, sonst kurzes Emoji — nie stutzen. */
fun clipItemId(raw: String?, maxEmoji: Int = 16): String {
    val e = raw?.trim().orEmpty()
    if (e.isEmpty()) return ""
    return if (
        e.startsWith("img_", ignoreCase = true) ||
        e.startsWith("theme_", ignoreCase = true)
    ) {
        e.take(32)
    } else {
        e.take(maxEmoji)
    }
}

fun petImageUrl(id: String): String? {
    val t = id.trim()
    if (t.isBlank()) return null
    val fromCatalog =
        LiveShopCatalog.remotePets?.firstOrNull { it.emoji == t }?.imageUrl
            ?: LiveShopCatalog.remoteStickers?.firstOrNull { it.emoji == t }?.imageUrl
            ?: LiveShopCatalog.remoteEmojis?.firstOrNull { it.emoji == t }?.imageUrl
    val rawUrl = fromCatalog?.trim()?.takeIf { it.isNotBlank() }
    if (rawUrl != null) {
        return when {
            rawUrl.startsWith("http") -> rawUrl
            rawUrl.startsWith("/luv/v1") ->
                LuvApiClient.baseUrl() + rawUrl.removePrefix("/luv")
            rawUrl.startsWith("/v1") -> LuvApiClient.baseUrl() + rawUrl
            else -> "${LuvApiClient.baseUrl()}/v1/shop/pet-image/$t"
        }
    }
    if (t.startsWith("img_", ignoreCase = true)) {
        return "${LuvApiClient.baseUrl()}/v1/shop/pet-image/$t"
    }
    return null
}

/** Emoji oder Custom-Bild (img_*) einheitlich anzeigen. */
@Composable
fun ItemGlyph(
    id: String,
    fontSize: TextUnit = 28.sp,
    modifier: Modifier = Modifier
) {
    if (isImagePetId(id)) {
        CompanionGlyph(petId = id, fontSize = fontSize, modifier = modifier)
    } else {
        Text(id.ifBlank { "✨" }, fontSize = fontSize, modifier = modifier)
    }
}

@Composable
fun CompanionGlyph(
    petId: String,
    fontSize: TextUnit = 28.sp,
    modifier: Modifier = Modifier
) {
    val id = petId.trim().ifBlank { "🐣" }
    // Katalog-Keys: imageUrl kann nach Sync kommen → Reload anstoßen
    val catalogEpoch = remember(
        LiveShopCatalog.remotePets,
        LiveShopCatalog.remoteStickers,
        LiveShopCatalog.remoteEmojis
    ) {
        listOf(
            LiveShopCatalog.remotePets?.size,
            LiveShopCatalog.remoteStickers?.size,
            LiveShopCatalog.remoteEmojis?.size
        )
    }
    var bitmap by remember(id) { mutableStateOf(ItemImageCache.get(id)) }

    LaunchedEffect(id, catalogEpoch) {
        if (!isImagePetId(id)) {
            bitmap = null
            return@LaunchedEffect
        }
        bitmap = ItemImageCache.get(id)
        if (bitmap == null) {
            ItemImageCache.preload(id) {
                bitmap = ItemImageCache.get(id)
            }
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = id,
            contentScale = ContentScale.Fit,
            modifier = modifier.size((fontSize.value * 1.15f).dp)
        )
    } else if (id.startsWith("img_", ignoreCase = true)) {
        // Bild fehlt/lädt: stiller Platzhalter — Emoji-Pets nie als Pfote
        Text("🐾", fontSize = fontSize, modifier = modifier)
    } else {
        Text(id, fontSize = fontSize, modifier = modifier)
    }
}
