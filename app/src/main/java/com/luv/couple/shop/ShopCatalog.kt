package com.luv.couple.shop

import com.luv.couple.R
import com.luv.couple.net.ShopPack

object ShopCatalog {
    val DEFAULT_BAR: List<String> = listOf("👍", "❌", "❤️", "😂", "😱", "😡", "😭")

    /** Alle kaufbaren Emojis mit fairem Coin-Preis. */
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
