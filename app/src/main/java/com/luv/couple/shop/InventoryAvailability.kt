package com.luv.couple.shop

/**
 * Freier Bestand = Besitz minus gerade in Verwendung.
 * Gesamtbesitz (Server Mode-3) bleibt unverändert — nur die Anzeige/Platzierbarkeit.
 */
object InventoryAvailability {

    fun freeStickers(
        owned: Map<String, Int>,
        placedOnProfile: Map<String, Int>
    ): Map<String, Int> =
        owned.mapNotNull { (id, total) ->
            val free = (total - (placedOnProfile[id] ?: 0)).coerceAtLeast(0)
            if (free > 0) id to free else null
        }.toMap()

    fun freeEmojis(
        owned: Map<String, Int>,
        emojiBar: Collection<String>
    ): Map<String, Int> {
        val inBar = emojiBar.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return owned.mapNotNull { (id, total) ->
            val used = if (id in inBar) 1 else 0
            val free = (total - used).coerceAtLeast(0)
            if (free > 0) id to free else null
        }.toMap()
    }

    /** Themes: aktiver Hintergrund gilt als in Verwendung → nicht in der freien Liste. */
    fun freeThemes(
        owned: Collection<String>,
        currentThemeId: String
    ): List<String> {
        val active = currentThemeId.trim()
        return owned.map { it.trim() }.filter { it.isNotEmpty() && it != active }.distinct()
    }

    /** Pets: freier Bestand = owned − 1 wenn ausgerüstet / Profil-Begleiter. */
    fun freePets(
        owned: Map<String, Int>,
        equippedPet: String,
        profileCompanion: String = equippedPet,
    ): Map<String, Int> {
        val reserved = buildSet {
            equippedPet.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
            profileCompanion.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
        }
        return owned.mapNotNull { (id, total) ->
            val key = id.trim()
            if (key.isEmpty() || key == ShopCatalog.DEFAULT_PET) return@mapNotNull null
            val used = if (key in reserved) 1 else 0
            val free = (total - used).coerceAtLeast(0)
            if (free > 0) key to free else null
        }.toMap()
    }

    @Deprecated("Use freePets(Map, …)", ReplaceWith("freePets(owned.associateWith { 1 }, equippedPet)"))
    fun freePets(
        owned: Collection<String>,
        equippedPet: String
    ): List<String> {
        val eq = equippedPet.trim()
        return owned.map { it.trim() }.filter { it.isNotEmpty() && it != eq }.distinct()
    }

    fun countPlacedStickers(
        layoutEmojis: Iterable<String?>
    ): Map<String, Int> =
        layoutEmojis
            .mapNotNull { it?.trim()?.takeIf { e -> e.isNotEmpty() } }
            .groupingBy { it }
            .eachCount()
}
