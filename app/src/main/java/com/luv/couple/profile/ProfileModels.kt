package com.luv.couple.profile

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

enum class ProfileElType {
    Avatar, Name, Status, Bio, Glass, Pet, Sticker, Text;

    val wire: String
        get() = when (this) {
            Avatar -> "avatar"
            Name -> "name"
            Status -> "status"
            Bio -> "bio"
            Glass -> "glass"
            Pet -> "pet"
            Sticker -> "sticker"
            Text -> "text"
        }

    companion object {
        fun fromWire(raw: String?): ProfileElType = when (raw?.lowercase()) {
            "avatar" -> Avatar
            "name" -> Name
            "status" -> Status
            "bio" -> Bio
            "glass" -> Glass
            "pet" -> Pet
            "text" -> Text
            else -> Sticker
        }
    }
}

enum class ProfileFont(val wire: String, val label: String) {
    Cozy("cozy", "Gemütlich"),
    Playful("playful", "Verspielt"),
    Classic("classic", "Klassisch");

    companion object {
        fun fromWire(raw: String?): ProfileFont = when (raw?.lowercase()) {
            "playful" -> Playful
            "classic" -> Classic
            else -> Cozy
        }
    }
}

data class ProfileLayoutEl(
    val id: String,
    val type: ProfileElType,
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val flipX: Boolean = false,
    val visible: Boolean = true,
    val z: Int = 10,
    val emoji: String? = null,
    val text: String? = null,
    val color: String? = null,
    val fontSize: Float? = null,
    val fontFamily: ProfileFont? = null
)

data class ProfileTheme(
    val id: String,
    val label: String,
    val emoji: String,
    val skyTop: Long,
    val skyBottom: Long,
    val groundTop: Long,
    val groundBottom: Long,
    /** rain | snow | stars | none */
    val effect: String = "none"
)

data class ProfileMood(
    val id: String,
    val label: String,
    val emoji: String,
    val free: Boolean = true
)

data class ProfileState(
    val themeId: String = ProfileCatalog.DEFAULT_THEME_ID,
    val statusEmoji: String = "😊",
    val bio: String = "",
    val companionEmoji: String = "🐣",
    val layout: List<ProfileLayoutEl> = emptyList()
) {
    /**
     * Merge nur Avatar/Name-Defaults. Optionale Elemente (Sticker, Glas, Begleiter)
     * bleiben erhalten — Positionen werden nicht zurückgesetzt.
     */
    fun normalized(nickname: String): ProfileState {
        val defaults = ProfileCatalog.defaultLayout(nickname)
        val byType = layout.associateBy { it.type }
        val core = defaults.map { def ->
            val saved = byType[def.type]
            if (saved == null) def
            else saved.copy(
                id = def.id,
                type = def.type,
                text = if (def.type == ProfileElType.Name) nickname else (saved.text ?: def.text),
                emoji = if (def.type == ProfileElType.Avatar) saved.emoji else saved.emoji,
                // Alter Default x=18 ließ die breite Name-Box links aus der Leinwand
                x = if (def.type == ProfileElType.Name && saved.x < 40f) 50f else saved.x,
            )
        }
        val legacyPet = layout.firstOrNull { it.type == ProfileElType.Pet }
        val extras = layout.filter { el ->
            when (el.type) {
                ProfileElType.Avatar, ProfileElType.Name -> false
                // Stimmung & Freitext-Overlays entfernt; Bio optional auf der Leinwand
                ProfileElType.Status, ProfileElType.Text -> false
                // Begleiter sitzt mittig im Avatar — kein separates Pet-Element
                ProfileElType.Pet -> false
                ProfileElType.Sticker, ProfileElType.Glass, ProfileElType.Bio -> true
            }
        }.take(ProfileCatalog.MAX_DECOR + 4)
        return copy(
            companionEmoji = companionEmoji.ifBlank { legacyPet?.emoji.orEmpty() }
                .ifBlank { "🐣" },
            bio = bio.take(ProfileCatalog.MAX_BIO),
            layout = core + extras
        )
    }

    fun snapshotKey(): String = ProfileCatalog.encode(this)
}

object ProfileCatalog {
    const val DEFAULT_THEME_ID = "meadow"
    const val MAX_DECOR = 36
    const val MAX_BIO = 500
    const val DECOR_Y_MIN = 52f
    const val MAX_DECOR_Z = 85

    val EDITABLE_TYPES = setOf(
        // Avatar zeigt nur den Begleiter — kein „Avatar gestalten“ mehr
        ProfileElType.Name,
        ProfileElType.Glass,
        ProfileElType.Bio
    )

    val TEXT_COLORS: List<String> = listOf(
        "#FFFFFF", "#FFD54F", "#FF6B8A", "#00B7E4", "#C218A8",
        "#2E7D32", "#C62828", "#0277BD", "#E65100", "#546E7A"
    )

    val THEMES: List<ProfileTheme> = listOf(
        // Standard wie Referenzbild: hellblauer Himmel, dunkles Wiesengrün
        ProfileTheme("meadow", "Wiese", "🌿", 0xFF7EB8D8, 0xFFB8D4E8, 0xFF2F5D2E, 0xFF2F5D2E),
        ProfileTheme("forest", "Wald", "🌲", 0xFF81C784, 0xFFC8E6C9, 0xFF2E7D32, 0xFF1B5E20),
        ProfileTheme("sunset", "Abendrot", "🌅", 0xFFFF8F00, 0xFFFFE0B2, 0xFFBF360C, 0xFF6D4C41),
        ProfileTheme("night", "Nacht", "🌙", 0xFF1A237E, 0xFF3949AB, 0xFF263238, 0xFF102027, "stars"),
        ProfileTheme("snow", "Schnee", "❄️", 0xFFB3E5FC, 0xFFE1F5FE, 0xFFECEFF1, 0xFFCFD8DC, "snow"),
        ProfileTheme("blossom", "Blüte", "🌸", 0xFFF8BBD0, 0xFFFCE4EC, 0xFF81C784, 0xFF558B2F),
        ProfileTheme("ocean", "Meer", "🌊", 0xFF4FC3F7, 0xFFB3E5FC, 0xFF00838F, 0xFF004D40),
        ProfileTheme("rain", "Regen", "🌧️", 0xFF607D8B, 0xFFB0BEC5, 0xFF455A64, 0xFF263238, "rain"),
        ProfileTheme("autumn", "Herbst", "🍂", 0xFFFFB74D, 0xFFFFE0B2, 0xFFEF6C00, 0xFF5D4037),
        ProfileTheme("stars", "Sterne", "✨", 0xFF0D1B2A, 0xFF1B3A4B, 0xFF1B263B, 0xFF0D1B2A, "stars"),
        ProfileTheme("cabin", "Hütte", "🏠", 0xFFFFCC80, 0xFFFFE0B2, 0xFF6D4C41, 0xFF3E2723),
        ProfileTheme("lake", "See", "🏞️", 0xFF4DD0E1, 0xFFB2EBF2, 0xFF00897B, 0xFF004D40),
        ProfileTheme("lavender", "Lavendel", "💜", 0xFFCE93D8, 0xFFF3E5F5, 0xFF7E57C2, 0xFF4527A0),
        ProfileTheme("hearth", "Kamin", "🔥", 0xFFFFAB91, 0xFFFFE0B2, 0xFFBF360C, 0xFF3E2723)
    )

    /** Früher gratis — jetzt nur noch über Itemshop / Besitz. */
    val FREE_STICKERS: List<String> = emptyList()

    val MOODS: List<ProfileMood> = listOf(
        ProfileMood("m-smile", "Fröhlich", "😊", true),
        ProfileMood("m-paw", "Pfotenzeit", "🐾", true),
        ProfileMood("m-leaf", "Natur", "🌿", true),
        ProfileMood("m-cool", "Cool unterwegs", "😎", true),
        ProfileMood("m-love", "Verliebt", "🥰", true),
        ProfileMood("m-kiss", "Kuss", "😘", true),
        ProfileMood("m-star", "Strahlend", "🤩", true),
        ProfileMood("m-sleep", "Müde", "😴", true),
        ProfileMood("m-think", "Nachdenklich", "🤔", true),
        ProfileMood("m-fire", "Feuer", "🔥", true),
        ProfileMood("m-party", "Party", "🎉", true),
        ProfileMood("m-heart", "Herzchen", "💕", true),
        ProfileMood("m-spark", "Magic", "✨", true),
        ProfileMood("m-moon", "Nachtmodus", "🌙", true),
        ProfileMood("m-rainbow", "Regenbogen", "🌈", true)
    )

    /** Nur Tiere — Anzeige am Avatar; Besitz über Itemshop. */
    val COMPANIONS: List<String> = listOf(
        "🐣", "🐦", "🐔", "🐸", "🐶", "🐱", "🐰", "🐹", "🐻", "🦊",
        "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐧", "🐢", "🦋", "🦄"
    )

    val AVATAR_FACES: List<String> = listOf(
        "", "😊", "🥰", "😎", "😍", "🤗", "😇", "🤠", "😺", "🐶", "🎨", "✏️"
    )

    fun theme(id: String): ProfileTheme =
        THEMES.firstOrNull { it.id == id } ?: THEMES.first()

    /** Nur Avatar + Name darunter — wie Referenzbild. */
    fun defaultLayout(nickname: String): List<ProfileLayoutEl> = listOf(
        ProfileLayoutEl("el-avatar", ProfileElType.Avatar, 18f, 20f, 1f, 0f, z = 20),
        // Name-Box ist ~78% breit (Mittelpunkt) — x≈50, damit der linksbündige Text
        // unter dem Avatar startet und nicht links aus der Leinwand fällt
        ProfileLayoutEl(
            "el-name", ProfileElType.Name, 50f, 36f, 1f, 0f,
            z = 21, text = nickname, color = "#FFFFFF", fontSize = 18f, fontFamily = ProfileFont.Classic
        )
    )

    fun newCompanion(emoji: String): ProfileLayoutEl = ProfileLayoutEl(
        id = "el-pet",
        type = ProfileElType.Pet,
        x = 78f,
        y = 70f,
        scale = 0.9f,
        z = 8,
        emoji = emoji.take(8),
        text = emoji.take(8)
    )

    fun newGlass(): ProfileLayoutEl = ProfileLayoutEl(
        id = "el-glass",
        type = ProfileElType.Glass,
        x = 20f,
        y = 74f,
        scale = 0.95f,
        z = 9,
        color = "#FFFFFF",
        fontSize = 11f,
        fontFamily = ProfileFont.Cozy
    )

    fun newBio(text: String = ""): ProfileLayoutEl = ProfileLayoutEl(
        id = "el-bio",
        type = ProfileElType.Bio,
        x = 50f,
        y = 88f,
        scale = 1f,
        z = 13,
        text = text.take(MAX_BIO),
        color = "#FFFFFF",
        fontSize = 12f,
        fontFamily = ProfileFont.Cozy
    )

    fun newSticker(emoji: String, layout: List<ProfileLayoutEl>): ProfileLayoutEl {
        val n = layout.count { it.type == ProfileElType.Sticker }
        val col = n % 4
        val row = n / 4
        return ProfileLayoutEl(
            id = "stk-${UUID.randomUUID()}",
            type = ProfileElType.Sticker,
            x = (24f + col * 16f).coerceIn(12f, 88f),
            y = (DECOR_Y_MIN + row * 12f).coerceIn(DECOR_Y_MIN, 90f),
            scale = 1f,
            z = (25 + n).coerceAtMost(MAX_DECOR_Z),
            emoji = emoji.take(8)
        )
    }

    fun newText(color: String, layout: List<ProfileLayoutEl>): ProfileLayoutEl {
        val n = layout.count { it.type == ProfileElType.Text }
        return ProfileLayoutEl(
            id = "txt-${UUID.randomUUID()}",
            type = ProfileElType.Text,
            x = 50f,
            y = (DECOR_Y_MIN + 8f + n * 8f).coerceIn(DECOR_Y_MIN, 88f),
            scale = 1f,
            z = (30 + n).coerceAtMost(MAX_DECOR_Z),
            text = "Dein Text",
            color = color,
            fontSize = 14f,
            fontFamily = ProfileFont.Cozy
        )
    }

    fun repairRotation(rotation: Float): Float {
        var r = rotation
        while (r > 180f) r -= 360f
        while (r < -180f) r += 360f
        return if (abs(r) > 90f) 0f else r
    }

    /**
     * Begrenzt die Mittelpunkt-%-Position so, dass die visuelle Kante
     * (baseSize × scale) genau am Canvas-Rand anliegen kann.
     * Für [Name] zusätzlich etwas Luft, damit der ganze Name sichtbar bleibt.
     */
    fun clampPos(
        el: ProfileLayoutEl,
        x: Float,
        y: Float,
        boardW: Float,
        boardH: Float,
        baseSizePx: Float,
        nameText: String? = null
    ): Pair<Float, Float> {
        val w = boardW.coerceAtLeast(1f)
        val h = boardH.coerceAtLeast(1f)
        val s = el.scale.coerceIn(0.35f, 2.5f)
        val visual = (baseSizePx * s).coerceAtLeast(8f)
        var halfX = (visual / 2f) / w * 100f
        var halfY = (visual / 2f) / h * 100f

        if (el.type == ProfileElType.Name) {
            // Name-Box ist breit; Schrift schrumpft im UI — Kasten bleibt im Canvas
            val fontPx = (el.fontSize ?: 18f) * s
            halfX = (visual / 2f) / w * 100f
            halfY = ((fontPx * 1.2f).coerceAtLeast(visual * 0.35f) / 2f) / h * 100f
        }

        // Minimale Margin, damit nichts komplett abgeschnitten wird
        halfX = halfX.coerceIn(0.8f, 48f)
        halfY = halfY.coerceIn(0.8f, 48f)
        return x.coerceIn(halfX, 100f - halfX) to y.coerceIn(halfY, 100f - halfY)
    }

    fun parseColor(hex: String?, fallback: Long = 0xFFFFFFFF): androidx.compose.ui.graphics.Color {
        val raw = hex?.trim().orEmpty()
        if (raw.startsWith("#") && (raw.length == 7 || raw.length == 9)) {
            return runCatching {
                val v = android.graphics.Color.parseColor(raw)
                androidx.compose.ui.graphics.Color(v)
            }.getOrElse { androidx.compose.ui.graphics.Color(fallback) }
        }
        return androidx.compose.ui.graphics.Color(fallback)
    }

    fun encode(state: ProfileState): String {
        val o = JSONObject()
            .put("themeId", state.themeId)
            .put("statusEmoji", state.statusEmoji)
            .put("bio", state.bio.take(MAX_BIO))
            .put("companionEmoji", state.companionEmoji)
        val arr = JSONArray()
        state.layout.forEach { el ->
            arr.put(
                JSONObject()
                    .put("id", el.id)
                    .put("type", el.type.wire)
                    .put("x", el.x.toDouble())
                    .put("y", el.y.toDouble())
                    .put("scale", el.scale.toDouble())
                    .put("rotation", el.rotation.toDouble())
                    .put("flipX", el.flipX)
                    .put("visible", el.visible)
                    .put("z", el.z)
                    .put("emoji", el.emoji)
                    .put("text", el.text)
                    .put("color", el.color)
                    .put("fontSize", el.fontSize?.toDouble())
                    .put("fontFamily", el.fontFamily?.wire)
            )
        }
        o.put("layout", arr)
        return o.toString()
    }

    fun decode(raw: String?, nickname: String = "Du"): ProfileState {
        if (raw.isNullOrBlank()) {
            return ProfileState(layout = defaultLayout(nickname)).normalized(nickname)
        }
        return runCatching {
            val o = JSONObject(raw)
            val arr = o.optJSONArray("layout") ?: JSONArray()
            val layout = buildList {
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    val fontRaw = e.optString("fontFamily").takeIf { it.isNotBlank() && it != "null" }
                    val fontSize = if (e.has("fontSize") && !e.isNull("fontSize")) {
                        e.optDouble("fontSize", 14.0).toFloat()
                    } else null
                    add(
                        ProfileLayoutEl(
                            id = e.optString("id").ifBlank { "el-$i" },
                            type = ProfileElType.fromWire(e.optString("type")),
                            x = e.optDouble("x", 50.0).toFloat().coerceIn(0f, 100f),
                            y = e.optDouble("y", 50.0).toFloat().coerceIn(0f, 100f),
                            scale = e.optDouble("scale", 1.0).toFloat().coerceIn(0.35f, 2.5f),
                            rotation = repairRotation(e.optDouble("rotation", 0.0).toFloat()),
                            flipX = e.optBoolean("flipX", false),
                            visible = e.optBoolean("visible", true),
                            z = e.optInt("z", 10),
                            emoji = e.optString("emoji").takeIf { it.isNotBlank() && it != "null" },
                            text = e.optString("text").takeIf { it.isNotBlank() && it != "null" },
                            color = e.optString("color").takeIf { it.isNotBlank() && it != "null" },
                            fontSize = fontSize?.coerceIn(8f, 32f),
                            fontFamily = fontRaw?.let { ProfileFont.fromWire(it) }
                        )
                    )
                }
            }
            ProfileState(
                themeId = o.optString("themeId", DEFAULT_THEME_ID),
                statusEmoji = o.optString("statusEmoji", "😊").ifBlank { "😊" },
                bio = o.optString("bio", "").take(MAX_BIO),
                companionEmoji = o.optString("companionEmoji", "🐣").ifBlank { "🐣" },
                layout = layout
            ).normalized(nickname)
        }.getOrElse {
            ProfileState(layout = defaultLayout(nickname)).normalized(nickname)
        }
    }
}
