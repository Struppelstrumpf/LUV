package com.luv.couple.shop

import com.luv.couple.profile.ProfileCatalog
import com.luv.couple.ui.isImagePetId

/**
 * Nutzer-sichtbare Labels — nie raw img_/theme_-IDs in Toasts/Listen.
 */
object ItemLabels {

    fun forKind(kind: String, itemId: String, fallbackEmoji: String? = null): String {
        val id = itemId.trim()
        if (id.isEmpty()) return "Item"
        return when (kind.trim().lowercase()) {
            "pets", "pet", "buddy" -> petLabel(id)
            "stickers", "sticker" -> stickerLabel(id)
            "emojis", "emoji" -> emojiLabel(id)
            "themes", "theme" -> themeLabel(id)
            else -> genericLabel(id, fallbackEmoji)
        }
    }

    fun petLabel(id: String): String {
        // 1) Admin-Override (höchste Priorität)
        LiveShopCatalog.displayLabel("pets", id)?.let { return it }
        // 2) Live-Katalog (bereits mit Override gemerged)
        LiveShopCatalog.pets().firstOrNull { it.emoji == id }?.label
            ?.takeIf { it.isNotBlank() && !looksLikeRawId(it) && it != id }
            ?.let { return it }
        LiveShopCatalog.remotePets?.firstOrNull { it.emoji == id }?.label
            ?.takeIf { it.isNotBlank() && !looksLikeRawId(it) && it != id }
            ?.let { return it }
        ShopCatalog.PETS.firstOrNull { it.emoji == id }?.label
            ?.takeIf { it.isNotBlank() && it != id }
            ?.let { return it }
        if (id.startsWith("img_event_")) {
            val titlePart = id.removePrefix("img_event_").substringBeforeLast("_")
                .replace('_', ' ')
                .trim()
            return if (titlePart.isNotBlank()) "${titlePart.replaceFirstChar { it.uppercase() }}-Begleiter"
            else "Event-Begleiter"
        }
        if (isImagePetId(id)) return "Bild-Begleiter"
        return EmojiNamesDe.name(id)
    }

    fun stickerLabel(id: String): String {
        LiveShopCatalog.displayLabel("stickers", id)?.let { return it }
        LiveShopCatalog.remoteStickers?.firstOrNull { it.emoji == id }?.label
            ?.takeIf { it.isNotBlank() && !looksLikeRawId(it) && it != id }
            ?.let { return it }
        LiveShopCatalog.stickers().firstOrNull { it.emoji == id }?.label
            ?.takeIf { it.isNotBlank() && !looksLikeRawId(it) && it != id }
            ?.let { return it }
        if (isImagePetId(id)) return "Eigener Sticker"
        return EmojiNamesDe.name(id)
    }

    fun emojiLabel(id: String): String {
        LiveShopCatalog.displayLabel("emojis", id)?.let { return it }
        LiveShopCatalog.remoteEmojis?.firstOrNull { it.emoji == id }?.label
            ?.takeIf { it.isNotBlank() && !looksLikeRawId(it) && it != id }
            ?.let { return it }
        LiveShopCatalog.emojis().firstOrNull { it.emoji == id }?.label
            ?.takeIf { it.isNotBlank() && !looksLikeRawId(it) && it != id }
            ?.let { return it }
        if (isImagePetId(id)) return "Eigenes Emoji"
        return EmojiNamesDe.name(id)
    }

    fun themeLabel(id: String): String {
        LiveShopCatalog.displayLabel("themes", id)?.let { return it }
        LiveShopCatalog.remoteThemes?.firstOrNull { it.id == id }?.label
            ?.takeIf { it.isNotBlank() && !looksLikeRawId(it) }
            ?.let { return it }
        ProfileCatalog.THEMES.firstOrNull { it.id == id }?.label
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        if (id.startsWith("theme_", ignoreCase = true)) return "Eigener Hintergrund"
        return id
    }

    fun genericLabel(id: String, fallbackEmoji: String? = null): String {
        if (isImagePetId(id)) return "Eigenes Bild"
        if (id.startsWith("theme_", ignoreCase = true)) return "Eigener Hintergrund"
        val fb = fallbackEmoji?.trim().orEmpty()
        if (fb.isNotEmpty() && !looksLikeRawId(fb)) return fb
        return if (looksLikeRawId(id)) "Item" else id
    }

    /** Für Toasts: nie die technische ID ausgeben. */
    fun toastSafe(id: String, kindHint: String = ""): String {
        val kind = kindHint.ifBlank {
            when {
                id.startsWith("theme_", ignoreCase = true) -> "themes"
                isImagePetId(id) -> "stickers"
                else -> ""
            }
        }
        val label = if (kind.isNotBlank()) forKind(kind, id) else genericLabel(id)
        return if (looksLikeRawId(label)) "Item" else label
    }

    fun looksLikeRawId(raw: String): Boolean {
        val t = raw.trim()
        return t.startsWith("img_", ignoreCase = true) ||
            t.startsWith("theme_", ignoreCase = true)
    }

    /** Beste Rate für Suche: Theme-ID, Pet-Label oder Emoji-Name. */
    fun forKindGuess(itemId: String): String {
        val id = itemId.trim()
        if (id.isEmpty()) return ""
        LiveShopCatalog.displayLabel("themes", id)?.let { return it }
        LiveShopCatalog.displayLabel("pets", id)?.let { return it }
        LiveShopCatalog.displayLabel("stickers", id)?.let { return it }
        LiveShopCatalog.displayLabel("emojis", id)?.let { return it }
        ProfileCatalog.THEMES.firstOrNull { it.id == id }?.label?.let { return it }
        LiveShopCatalog.remoteThemes?.firstOrNull { it.id == id }?.label
            ?.takeIf { it.isNotBlank() && !looksLikeRawId(it) }
            ?.let { return it }
        ShopCatalog.PETS.firstOrNull { it.emoji == id }?.label?.let { return it }
        LiveShopCatalog.remotePets?.firstOrNull { it.emoji == id }?.label
            ?.takeIf { it.isNotBlank() && !looksLikeRawId(it) }
            ?.let { return it }
        return EmojiNamesDe.name(id)
    }
}
