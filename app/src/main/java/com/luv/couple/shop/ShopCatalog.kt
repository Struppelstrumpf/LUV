package com.luv.couple.shop

import com.luv.couple.R
import com.luv.couple.net.ShopPack

object ShopCatalog {
    const val MAX_BAR = 8
    val DEFAULT_BAR: List<String> = listOf("👍", "❌", "❤️", "😂", "😱", "😡", "😭")

    /** Standard-Begleiter — jeder startet damit ausgerüstet. */
    const val DEFAULT_PET = "🐣"

    /** Alle kaufbaren Reaktions-Emojis mit fairem Coin-Preis. */
    val EMOJIS: List<ShopEmoji> = listOf(
        ShopEmoji("👍", 5), ShopEmoji("👎", 5), ShopEmoji("❌", 5), ShopEmoji("❤️", 8),
        ShopEmoji("🧡", 8), ShopEmoji("💛", 8), ShopEmoji("💚", 8), ShopEmoji("💙", 8),
        ShopEmoji("💜", 8), ShopEmoji("🖤", 8), ShopEmoji("🤍", 8), ShopEmoji("💔", 10),
        ShopEmoji("💕", 12), ShopEmoji("💖", 12), ShopEmoji("💗", 12), ShopEmoji("💘", 14),
        ShopEmoji("😂", 8), ShopEmoji("🤣", 10), ShopEmoji("😊", 6), ShopEmoji("🙂", 5),
        ShopEmoji("😉", 6), ShopEmoji("😍", 10), ShopEmoji("🥰", 12), ShopEmoji("😘", 10),
        ShopEmoji("😜", 8), ShopEmoji("🤪", 10), ShopEmoji("😎", 12), ShopEmoji("🤩", 12),
        ShopEmoji("😱", 8), ShopEmoji("😨", 8), ShopEmoji("😰", 8), ShopEmoji("😥", 8),
        ShopEmoji("😢", 8), ShopEmoji("😭", 10), ShopEmoji("😡", 8), ShopEmoji("🤬", 12),
        ShopEmoji("😤", 8), ShopEmoji("😳", 8), ShopEmoji("🥺", 12), ShopEmoji("😴", 6),
        ShopEmoji("🤔", 8), ShopEmoji("🙄", 8), ShopEmoji("😬", 8), ShopEmoji("🤐", 8),
        ShopEmoji("🫡", 10), ShopEmoji("🤝", 10), ShopEmoji("🙏", 8), ShopEmoji("💪", 10),
        ShopEmoji("🔥", 10), ShopEmoji("✨", 8), ShopEmoji("⭐", 8), ShopEmoji("🌟", 10),
        ShopEmoji("💯", 12), ShopEmoji("🎉", 10), ShopEmoji("🎊", 10), ShopEmoji("🎈", 8),
        ShopEmoji("🎁", 12), ShopEmoji("🌹", 12), ShopEmoji("🌸", 8), ShopEmoji("🍀", 8),
        ShopEmoji("🌈", 10), ShopEmoji("☀️", 6), ShopEmoji("🌙", 6), ShopEmoji("⚡", 8),
        ShopEmoji("💥", 10), ShopEmoji("🎯", 10), ShopEmoji("🏆", 14), ShopEmoji("👑", 16),
        ShopEmoji("💎", 18), ShopEmoji("🐱", 12), ShopEmoji("🐶", 12), ShopEmoji("🐻", 12),
        ShopEmoji("🐼", 14), ShopEmoji("🦊", 14), ShopEmoji("🐰", 12), ShopEmoji("🦄", 18),
        ShopEmoji("🐸", 10), ShopEmoji("🐧", 12), ShopEmoji("🦋", 10), ShopEmoji("🐝", 8),
        ShopEmoji("🍕", 8), ShopEmoji("🍩", 8), ShopEmoji("☕", 6), ShopEmoji("🍷", 10),
        ShopEmoji("🍺", 8), ShopEmoji("🧁", 8), ShopEmoji("🍓", 6), ShopEmoji("🍑", 10),
        ShopEmoji("🎵", 8), ShopEmoji("🎶", 8), ShopEmoji("📱", 6), ShopEmoji("💡", 6),
        ShopEmoji("📎", 5), ShopEmoji("✏️", 5), ShopEmoji("📌", 5), ShopEmoji("🔔", 8),
        ShopEmoji("👀", 8), ShopEmoji("💀", 12), ShopEmoji("👻", 12), ShopEmoji("🤖", 14),
        ShopEmoji("👽", 14), ShopEmoji("💩", 10), ShopEmoji("🤡", 12), ShopEmoji("😈", 14),
        ShopEmoji("😇", 12), ShopEmoji("🫠", 12), ShopEmoji("🫢", 10), ShopEmoji("🫣", 10),
        ShopEmoji("🫶", 14), ShopEmoji("🫰", 12), ShopEmoji("✌️", 8),
        ShopEmoji("🤞", 8), ShopEmoji("🤟", 8), ShopEmoji("🤘", 8), ShopEmoji("👏", 8),
        ShopEmoji("🙌", 10), ShopEmoji("👋", 6)
    ).distinctBy { it.emoji }

    /** Profil-Hintergründe (meadow ist Starter, gratis). */
    val THEMES: List<ShopTheme> = listOf(
        ShopTheme("meadow", "Wiese", "🌿", 0),
        ShopTheme("forest", "Wald", "🌲", 18),
        ShopTheme("sunset", "Abendrot", "🌅", 20),
        ShopTheme("night", "Nacht", "🌙", 20),
        ShopTheme("snow", "Schnee", "❄️", 18),
        ShopTheme("blossom", "Blüte", "🌸", 22),
        ShopTheme("ocean", "Meer", "🌊", 22),
        ShopTheme("rain", "Regen", "🌧️", 18),
        ShopTheme("autumn", "Herbst", "🍂", 20),
        ShopTheme("stars", "Sterne", "✨", 24),
        ShopTheme("cabin", "Hütte", "🏠", 22),
        ShopTheme("lake", "See", "🏞️", 22),
        ShopTheme("lavender", "Lavendel", "💜", 24),
        ShopTheme("hearth", "Kamin", "🔥", 24)
    )

    /**
     * Profil-Sticker zum Kaufen (auf die Leinwand kleben).
     * Getrennt von Begleitern und Reaktions-Emojis.
     */
    val STICKERS: List<ShopEmoji> = listOf(
        ShopEmoji("🦋", 8), ShopEmoji("🐝", 6), ShopEmoji("🐞", 7), ShopEmoji("🌸", 7),
        ShopEmoji("🌺", 8), ShopEmoji("🌷", 7), ShopEmoji("🌻", 8), ShopEmoji("🌹", 10),
        ShopEmoji("🍀", 6), ShopEmoji("🌿", 5), ShopEmoji("🍃", 5), ShopEmoji("🍄", 8),
        ShopEmoji("🌳", 6), ShopEmoji("🌈", 10), ShopEmoji("☀️", 6), ShopEmoji("🌙", 6),
        ShopEmoji("⭐", 6), ShopEmoji("✨", 6), ShopEmoji("💫", 8), ShopEmoji("🌟", 8),
        ShopEmoji("☁️", 5), ShopEmoji("❄️", 7), ShopEmoji("🌊", 8), ShopEmoji("🐚", 7),
        ShopEmoji("❤️", 8), ShopEmoji("💕", 10), ShopEmoji("💝", 12), ShopEmoji("🫶", 12),
        ShopEmoji("💌", 10), ShopEmoji("🎀", 8), ShopEmoji("🎈", 8), ShopEmoji("🎁", 12),
        ShopEmoji("🏠", 8), ShopEmoji("☕", 6), ShopEmoji("🎵", 6), ShopEmoji("🎨", 10),
        ShopEmoji("✏️", 5), ShopEmoji("🔥", 8), ShopEmoji("😎", 8), ShopEmoji("🦔", 12),
        ShopEmoji("🐶", 10), ShopEmoji("🐱", 10), ShopEmoji("🐦", 8), ShopEmoji("🧸", 12),
        ShopEmoji("🪄", 10), ShopEmoji("🪶", 7), ShopEmoji("🪴", 8), ShopEmoji("🪸", 9)
    ).distinctBy { it.emoji }

    /** Tab-Labels — Inventar (Menü) und Itemshop müssen übereinstimmen. */
    val SHOP_TAB_LABELS: List<String> = listOf(
        "Sticker", "Hintergründe", "Begleiter", "Emojis"
    )

    /** Nur Tiere — Herzen entfernt. Küken ist Starter. */
    val PETS: List<ShopPet> = listOf(
        ShopPet("🐣", "Küken", 0),
        ShopPet("🐦", "Vogel", 14),
        ShopPet("🐔", "Huhn", 16),
        ShopPet("🐸", "Frosch", 16),
        ShopPet("🐶", "Hund", 20),
        ShopPet("🐱", "Katze", 20),
        ShopPet("🐰", "Hase", 22),
        ShopPet("🐹", "Hamster", 18),
        ShopPet("🐻", "Bär", 24),
        ShopPet("🦊", "Fuchs", 26),
        ShopPet("🐼", "Panda", 28),
        ShopPet("🐨", "Koala", 26),
        ShopPet("🦉", "Eule", 24),
        ShopPet("🐯", "Tiger", 30),
        ShopPet("🦁", "Löwe", 32),
        ShopPet("🐮", "Kuh", 20),
        ShopPet("🐷", "Schwein", 20),
        ShopPet("🐧", "Pinguin", 22),
        ShopPet("🐢", "Schildkröte", 22),
        ShopPet("🦋", "Schmetterling", 18),
        ShopPet("🦄", "Einhorn", 40)
    )

    fun priceOf(emoji: String): Int =
        EMOJIS.firstOrNull { it.emoji == emoji }?.priceCoins ?: 10

    fun playfulPackTitle(pack: ShopPack): String = when {
        pack.id.contains("intro", ignoreCase = true) || pack.coins in 90..110 -> "Säckchen Glück"
        pack.coins <= 60 -> "Handvoll Coins"
        pack.coins <= 160 -> "Beutel voll Coins"
        pack.coins <= 450 -> "Schatztruhe"
        else -> pack.label.ifBlank { "${pack.coins} Coins" }
    }

    fun packImageRes(pack: ShopPack): Int = when {
        pack.coins <= 60 -> R.drawable.shop_coins_handful
        pack.coins <= 110 -> R.drawable.shop_coins_pouch
        pack.coins <= 200 -> R.drawable.shop_coins_chest
        else -> R.drawable.shop_coins_treasure
    }
}

data class ShopEmoji(
    val emoji: String,
    val priceCoins: Int
)

data class ShopTheme(
    val id: String,
    val label: String,
    val emoji: String,
    val priceCoins: Int
)

data class ShopPet(
    val emoji: String,
    val label: String,
    val priceCoins: Int
)
