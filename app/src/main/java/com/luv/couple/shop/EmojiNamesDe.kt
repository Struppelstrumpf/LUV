package com.luv.couple.shop

/**
 * Deutsche Anzeigenamen für Emoji-/Sticker-Items.
 * Kuratierte Namen (CLDR-nah) + Fallback aus Suchbegriffen.
 */
object EmojiNamesDe {

    private val OVERRIDES = mapOf(
        "🌳" to "Grüner Baum",
        "🌲" to "Nadelbaum",
        "🎄" to "Weihnachtsbaum",
        "🌴" to "Palme",
        "🎋" to "Tanabata-Baum",
        "🌵" to "Kaktus",
        "❤️" to "Rotes Herz",
        "♥️" to "Herz",
        "🧡" to "Oranges Herz",
        "💛" to "Gelbes Herz",
        "💚" to "Grünes Herz",
        "💙" to "Blaues Herz",
        "💜" to "Lila Herz",
        "🖤" to "Schwarzes Herz",
        "🤍" to "Weißes Herz",
        "🤎" to "Braunes Herz",
        "💔" to "Gebrochenes Herz",
        "❣️" to "Herz-Ausrufezeichen",
        "💕" to "Zwei Herzen",
        "💖" to "Funkelndes Herz",
        "💗" to "Wachsendes Herz",
        "💘" to "Herz mit Pfeil",
        "💝" to "Herz mit Schleife",
        "💞" to "Kreisende Herzen",
        "💟" to "Herzdekoration",
        "❤️‍🔥" to "Brennendes Herz",
        "❤️‍🩹" to "Heilendes Herz",
        "👍" to "Daumen hoch",
        "👎" to "Daumen runter",
        "❌" to "Kreuz",
        "😂" to "Freudentränen",
        "🤣" to "Lachtränen",
        "😊" to "Lächelndes Gesicht",
        "😍" to "Herzaugen",
        "🥰" to "Verliebtes Gesicht",
        "😘" to "Kuss zuwerfen",
        "🔥" to "Feuer",
        "✨" to "Funkeln",
        "⭐" to "Stern",
        "🌟" to "Glitzerstern",
        "🦋" to "Schmetterling",
        "🌸" to "Kirschblüte",
        "🌹" to "Rose",
        "🍉" to "Wassermelone",
        "🍀" to "Kleeblatt",
        "🌙" to "Mond",
        "☀️" to "Sonne",
        "🌈" to "Regenbogen",
        "🐱" to "Katze",
        "🐶" to "Hund",
        "🐣" to "Küken",
        "🐯" to "Tiger",
        "🧙" to "Hexe",
        "🧙‍♀️" to "Hexe",
        "🧙‍♂️" to "Hexer",
    )

    private val EN_SKIP = setOf(
        "heart", "hearts", "love", "emotion", "face", "smile", "smiling", "with", "the",
        "and", "for", "red", "blue", "green", "yellow", "orange", "purple", "black",
        "white", "brown", "tree", "plant", "animal", "food", "christmas", "evergreen",
        "forest", "pine", "deciduous", "celebration", "japanese", "game", "card",
        "suit", "ily", "xoxo", "adorbs", "bae", "eyes", "eye", "happy", "funny",
        "laugh", "joy", "tear", "tears", "crying", "good", "thumb", "thumbs", "yes",
        "no", "nope", "down", "up", "mark", "cross", "cancel", "multiply", "space",
        "moon", "star", "sparkle", "fire", "burn", "on", "two", "dating", "romance",
        "romantic", "valentine", "ribbon", "arrow", "cupid", "growing", "sparkling",
        "revolving", "decoration", "mending", "improving", "recovering", "well",
        "healthier", "sacred", "lust", "wicked", "evil", "cardiac", "heavy",
        "punctuation", "exclamation", "anniversary", "kisses", "kiss", "loving",
        "morning", "night", "excited", "nervous", "pulse", "heartpulse", "muah",
        "feeling", "feels", "bestest", "romance", "143"
    )

    fun name(emoji: String): String {
        val id = emoji.trim()
        if (id.isEmpty()) return "Item"
        OVERRIDES[id]?.let { return it }
        val bare = id.replace("\uFE0F", "")
        OVERRIDES[bare]?.let { return it }
        val fromKw = nameFromKeywords(EmojiSearchKeywords.forEmoji(id))
        if (!fromKw.isNullOrBlank()) return fromKw
        return id
    }

    private fun nameFromKeywords(raw: String): String? {
        val words = raw.lowercase()
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 3 && it !in EN_SKIP && it.all { c -> c.isLetter() } }
        val first = words.firstOrNull() ?: return null
        return first.replaceFirstChar { it.uppercaseChar() }
    }
}
