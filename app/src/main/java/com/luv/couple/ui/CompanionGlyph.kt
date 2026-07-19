package com.luv.couple.ui

import android.graphics.BitmapFactory
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

fun isImagePetId(id: String): Boolean {
    // Nur echte Bild-Begleiter — Emoji-Pets niemals als Bild behandeln
    return id.trim().startsWith("img_", ignoreCase = true)
}

fun petImageUrl(id: String): String? {
    val t = id.trim()
    if (t.isBlank()) return null
    val fromCatalog = LiveShopCatalog.remotePets
        ?.firstOrNull { it.emoji == t }
        ?.imageUrl
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    if (fromCatalog != null) {
        return when {
            fromCatalog.startsWith("http") -> fromCatalog
            fromCatalog.startsWith("/luv/v1") ->
                LuvApiClient.baseUrl() + fromCatalog.removePrefix("/luv")
            fromCatalog.startsWith("/v1") -> LuvApiClient.baseUrl() + fromCatalog
            else -> "${LuvApiClient.baseUrl()}/v1/shop/pet-image/$t"
        }
    }
    if (t.startsWith("img_", ignoreCase = true)) {
        return "${LuvApiClient.baseUrl()}/v1/shop/pet-image/$t"
    }
    return null
}

@Composable
fun CompanionGlyph(
    petId: String,
    fontSize: TextUnit = 28.sp,
    modifier: Modifier = Modifier
) {
    val id = petId.trim().ifBlank { "🐣" }
    val url = remember(id, LiveShopCatalog.remotePets) { petImageUrl(id) }
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(url).get().build()
                LuvApiClient.httpClient().newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val bytes = resp.body?.bytes() ?: return@runCatching null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }.getOrNull()
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
