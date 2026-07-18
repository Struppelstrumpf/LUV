package com.luv.couple.profile

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ProfileElType {
    Avatar, Name, Status, Sticker, Text;

    val wire: String
        get() = when (this) {
            Avatar -> "avatar"
            Name -> "name"
            Status -> "status"
            Sticker -> "sticker"
            Text -> "text"
        }

    companion object {
        fun fromWire(raw: String?): ProfileElType = when (raw?.lowercase()) {
            "avatar" -> Avatar
            "name" -> Name
            "status" -> Status
            "text" -> Text
            else -> Sticker
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
    val color: String? = null
)

data class ProfileTheme(
    val id: String,
    val label: String,
    val emoji: String,
    val skyTop: Long,
    val skyBottom: Long,
    val groundTop: Long,
    val groundBottom: Long
)

data class ProfileState(
    val themeId: String = ProfileCatalog.DEFAULT_THEME_ID,
    val statusEmoji: String = "😊",
    val bio: String = "",
    val layout: List<ProfileLayoutEl> = emptyList()
) {
    fun normalized(nickname: String): ProfileState {
        val core = ProfileCatalog.defaultLayout(nickname, statusEmoji)
        val byType = layout.associateBy { it.type }
        val mergedCore = core.map { def ->
            val saved = byType[def.type]
            if (saved == null) def
            else saved.copy(
                id = def.id,
                type = def.type,
                text = when (def.type) {
                    ProfileElType.Name -> nickname
                    ProfileElType.Status -> statusEmoji
                    else -> saved.text ?: def.text
                }
            )
        }
        val decor = layout.filter {
            it.type == ProfileElType.Sticker || it.type == ProfileElType.Text
        }.take(36)
        return copy(layout = mergedCore + decor)
    }
}

object ProfileCatalog {
    const val DEFAULT_THEME_ID = "meadow"
    const val MAX_DECOR = 36

    val THEMES: List<ProfileTheme> = listOf(
        ProfileTheme(
            "meadow", "Wiese", "🌿",
            0xFF64B5F6, 0xFFBBDEFB, 0xFF7CB342, 0xFF33691E
        ),
        ProfileTheme(
            "sunset", "Abendrot", "🌅",
            0xFFFF8F00, 0xFFFFE0B2, 0xFFBF360C, 0xFF6D4C41
        ),
        ProfileTheme(
            "night", "Nacht", "🌙",
            0xFF1A237E, 0xFF3949AB, 0xFF263238, 0xFF102027
        ),
        ProfileTheme(
            "snow", "Schnee", "❄️",
            0xFFB3E5FC, 0xFFE1F5FE, 0xFFECEFF1, 0xFFCFD8DC
        ),
        ProfileTheme(
            "blossom", "Blüte", "🌸",
            0xFFF8BBD0, 0xFFFCE4EC, 0xFF81C784, 0xFF558B2F
        ),
        ProfileTheme(
            "ocean", "Meer", "🌊",
            0xFF4FC3F7, 0xFFB3E5FC, 0xFF00838F, 0xFF004D40
        )
    )

    /** Kostenlose Starter-Sticker für die Profil-Leinwand. */
    val FREE_STICKERS: List<String> = listOf(
        "☀️", "😎", "💌", "🏠", "🐦", "🌳", "🌻", "🦔",
        "🐶", "🐱", "⭐", "✨", "❤️", "🌹", "🌈", "🍀",
        "🎈", "🎁", "☕", "🎵", "🦋", "🐝", "🌙", "🔥"
    )

    fun theme(id: String): ProfileTheme =
        THEMES.firstOrNull { it.id == id } ?: THEMES.first()

    fun defaultLayout(nickname: String, status: String = "😊"): List<ProfileLayoutEl> = listOf(
        ProfileLayoutEl("el-avatar", ProfileElType.Avatar, 18f, 22f, 1f, -6f, z = 20),
        ProfileLayoutEl(
            "el-name", ProfileElType.Name, 42f, 18f, 1f, -8f,
            z = 21, text = nickname, color = "#FFFFFF"
        ),
        ProfileLayoutEl(
            "el-status", ProfileElType.Status, 58f, 28f, 1f, 0f,
            z = 22, text = status, emoji = status
        )
    )

    fun newSticker(emoji: String, layout: List<ProfileLayoutEl>): ProfileLayoutEl {
        val n = layout.count { it.type == ProfileElType.Sticker }
        val col = n % 4
        val row = n / 4
        return ProfileLayoutEl(
            id = "stk-${UUID.randomUUID()}",
            type = ProfileElType.Sticker,
            x = (22f + col * 18f).coerceIn(12f, 88f),
            y = (48f + row * 14f).coerceIn(36f, 88f),
            scale = 1f,
            rotation = 0f,
            z = (25 + n).coerceAtMost(85),
            emoji = emoji.take(8)
        )
    }

    fun encode(state: ProfileState): String {
        val o = JSONObject()
            .put("themeId", state.themeId)
            .put("statusEmoji", state.statusEmoji)
            .put("bio", state.bio.take(280))
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
                    add(
                        ProfileLayoutEl(
                            id = e.optString("id").ifBlank { "el-$i" },
                            type = ProfileElType.fromWire(e.optString("type")),
                            x = e.optDouble("x", 50.0).toFloat().coerceIn(0f, 100f),
                            y = e.optDouble("y", 50.0).toFloat().coerceIn(0f, 100f),
                            scale = e.optDouble("scale", 1.0).toFloat().coerceIn(0.35f, 2.5f),
                            rotation = e.optDouble("rotation", 0.0).toFloat(),
                            flipX = e.optBoolean("flipX", false),
                            visible = e.optBoolean("visible", true),
                            z = e.optInt("z", 10),
                            emoji = e.optString("emoji").takeIf { it.isNotBlank() && it != "null" },
                            text = e.optString("text").takeIf { it.isNotBlank() && it != "null" },
                            color = e.optString("color").takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
                }
            }
            ProfileState(
                themeId = o.optString("themeId", DEFAULT_THEME_ID),
                statusEmoji = o.optString("statusEmoji", "😊").ifBlank { "😊" },
                bio = o.optString("bio", "").take(280),
                layout = layout
            ).normalized(nickname)
        }.getOrElse {
            ProfileState(layout = defaultLayout(nickname)).normalized(nickname)
        }
    }
}
