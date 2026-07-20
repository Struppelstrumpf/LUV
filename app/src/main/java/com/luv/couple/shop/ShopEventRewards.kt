package com.luv.couple.shop

/**
 * Saisonale Items — nicht im normalen Shop-Sortiment, sondern über Events/Erfolge.
 * Besitz bleibt handelbar (Server kennt die Items weiterhin).
 *
 * Keys als "kind:id", damit Pets wie 🐰/🦌 nicht fälschlich gesperrt werden.
 */
object ShopEventRewards {
    val EXCLUDED_KEYS: Set<String> = setOf(
        "emojis:💘",
        "emojis:🐰",
        "emojis:🎃",
        "emojis:🎅",
        "emojis:🎄",
        "emojis:💝",
        "stickers:💝",
        "stickers:🕯️",
        "stickers:🎄",
        "stickers:🎅",
        "stickers:🤶",
        "stickers:🎃",
        "stickers:🐰",
        "stickers:🥚",
        "stickers:🦌",
        "stickers:⛄",
        "stickers:❄️",
        "stickers:💘",
        "stickers:💒",
        "emojis:🥇",
        "stickers:🥇",
    )

    val EXCLUDED_IDS: Set<String> = EXCLUDED_KEYS.map { it.substringAfter(':') }.toSet()

    fun isExcluded(kind: String, id: String): Boolean {
        val k = kind.trim().lowercase()
        val raw = id.trim()
        if (k.isEmpty() || raw.isEmpty()) return false
        val bare = raw.replace("\uFE0F", "")
        return "$k:$raw" in EXCLUDED_KEYS || "$k:$bare" in EXCLUDED_KEYS
    }

    /** Ohne Kind: nur IDs die in allen Shop-Arten Event-only sind (keine Pets). */
    fun isExcluded(id: String): Boolean = false

    fun <T> filterShopList(items: List<T>, kind: String, idOf: (T) -> String): List<T> =
        items.filter { !isExcluded(kind, idOf(it)) }

    fun <T> filterShopList(items: List<T>, idOf: (T) -> String): List<T> =
        items.filter { !isExcluded(idOf(it)) }
}
