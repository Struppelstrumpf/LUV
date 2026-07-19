package com.luv.couple.net

import com.luv.couple.data.Stroke
import com.luv.couple.data.StrokePoint
import com.luv.couple.data.TemplateStrokePart
import org.json.JSONArray
import org.json.JSONObject

/** Emoji kurz; Bild-Begleiter (img_*) länger behalten — Kompatibilität alt/neu. */
private fun clipCanvasEmojiId(raw: String?, maxEmoji: Int = 16): String {
    val e = raw?.trim().orEmpty()
    if (e.isEmpty()) return ""
    return if (e.startsWith("img_", ignoreCase = true)) e.take(32) else e.take(maxEmoji)
}

sealed class PairMessage {
    data class Hello(val token: String) : PairMessage()
    data class HelloOk(val ok: Boolean) : PairMessage()
    data class StrokeMsg(val stroke: Stroke) : PairMessage()
    data class UndoMsg(val strokeId: String) : PairMessage()
    data class Presence(
        val active: Boolean,
        val nickname: String?,
        val colorIndex: Int,
        val peerKey: String? = null,
        val userId: String? = null,
        @Deprecated("legacy") val gender: String? = null
    ) : PairMessage()
    data class Note(val text: String) : PairMessage()
    data class Recolor(val nickname: String?, val colorIndex: Int) : PairMessage()
    data class Reaction(val emoji: String, val nickname: String?) : PairMessage()
    data class StickerPlace(
        val id: String,
        val emoji: String,
        val x: Float,
        val y: Float,
        val nickname: String?
    ) : PairMessage()
    data class StickerRemove(val id: String) : PairMessage()
    data class GameBoard(val game: String, val visible: Boolean) : PairMessage()
    data object Clear : PairMessage()
    data class ClearPropose(val nickname: String?) : PairMessage()
    data class ClearVote(val proposalId: String, val yes: Boolean) : PairMessage()
    data object Ping : PairMessage()
    data object Pong : PairMessage()
}

object PairProtocol {
    fun encode(message: PairMessage): String {
        val json = when (message) {
            is PairMessage.Hello -> JSONObject()
                .put("type", "hello")
                .put("token", message.token)
            is PairMessage.HelloOk -> JSONObject()
                .put("type", "hello_ok")
                .put("ok", message.ok)
            is PairMessage.StrokeMsg -> {
                val points = JSONArray()
                message.stroke.points.forEach { point ->
                    points.put(
                        JSONObject()
                            .put("x", point.x.toDouble())
                            .put("y", point.y.toDouble())
                    )
                }
                val json = JSONObject()
                    .put("type", "stroke")
                    .put("id", message.stroke.id)
                    .put("width", message.stroke.width.toDouble())
                    .put("nickname", message.stroke.nickname ?: JSONObject.NULL)
                    .put("colorIndex", message.stroke.colorIndex)
                    .put("authorId", message.stroke.authorId ?: JSONObject.NULL)
                    .put("gender", message.stroke.gender ?: JSONObject.NULL)
                    .put("emoji", message.stroke.emoji ?: JSONObject.NULL)
                    .put("colorLocked", message.stroke.colorLocked)
                    .put("points", points)
                val parts = message.stroke.templateParts
                if (!parts.isNullOrEmpty()) {
                    json.put("templateParts", encodeTemplateParts(parts))
                    json.put("templateScale", message.stroke.templateScale.toDouble())
                    json.put("templateRotation", message.stroke.templateRotation.toDouble())
                    message.stroke.templateCoordSpace?.takeIf { it.isNotBlank() }?.let {
                        json.put("templateCoordSpace", it)
                    }
                }
                json
            }
            is PairMessage.UndoMsg -> JSONObject()
                .put("type", "undo")
                .put("id", message.strokeId)
            is PairMessage.Presence -> JSONObject()
                .put("type", "presence")
                .put("active", message.active)
                .put("nickname", message.nickname ?: JSONObject.NULL)
                .put("colorIndex", message.colorIndex)
                .put("peerKey", message.peerKey ?: JSONObject.NULL)
                .put("userId", message.userId ?: JSONObject.NULL)
                .put("gender", message.gender ?: JSONObject.NULL)
            is PairMessage.Note -> JSONObject()
                .put("type", "note")
                .put("text", message.text.take(80))
            is PairMessage.Recolor -> JSONObject()
                .put("type", "recolor")
                .put("nickname", message.nickname ?: JSONObject.NULL)
                .put("colorIndex", message.colorIndex)
            is PairMessage.Reaction -> JSONObject()
                .put("type", "reaction")
                .put("emoji", clipCanvasEmojiId(message.emoji))
                .put("nickname", message.nickname ?: JSONObject.NULL)
            is PairMessage.StickerPlace -> JSONObject()
                .put("type", "sticker_place")
                .put("id", message.id)
                .put("emoji", clipCanvasEmojiId(message.emoji))
                .put("x", message.x.toDouble())
                .put("y", message.y.toDouble())
                .put("nickname", message.nickname ?: JSONObject.NULL)
            is PairMessage.StickerRemove -> JSONObject()
                .put("type", "sticker_remove")
                .put("id", message.id)
            is PairMessage.GameBoard -> JSONObject()
                .put("type", "game_board")
                .put("game", message.game)
                .put("visible", message.visible)
            PairMessage.Clear -> JSONObject().put("type", "clear_propose")
            is PairMessage.ClearPropose -> JSONObject()
                .put("type", "clear_propose")
                .put("nickname", message.nickname ?: JSONObject.NULL)
            is PairMessage.ClearVote -> JSONObject()
                .put("type", "clear_vote")
                .put("proposalId", message.proposalId)
                .put("yes", message.yes)
            PairMessage.Ping -> JSONObject().put("type", "ping")
            PairMessage.Pong -> JSONObject().put("type", "pong")
        }
        return json.toString()
    }

    fun decode(line: String): PairMessage? {
        return runCatching {
            val json = JSONObject(line)
            when (json.getString("type")) {
                "hello" -> PairMessage.Hello(json.getString("token"))
                "hello_ok" -> PairMessage.HelloOk(json.optBoolean("ok", true))
                "stroke" -> {
                    val pointsJson = json.getJSONArray("points")
                    val points = buildList {
                        for (i in 0 until pointsJson.length()) {
                            val p = pointsJson.getJSONObject(i)
                            add(
                                StrokePoint(
                                    x = p.getDouble("x").toFloat(),
                                    y = p.getDouble("y").toFloat()
                                )
                            )
                        }
                    }
                    val nickname = json.optString("nickname").takeIf { it.isNotBlank() && it != "null" }
                    val colorIndex = if (json.has("colorIndex")) {
                        json.optInt("colorIndex", 0)
                    } else {
                        nickname?.let { com.luv.couple.data.PeerPalette.indexFor(it.lowercase()) } ?: 0
                    }
                    PairMessage.StrokeMsg(
                        Stroke(
                            id = json.getString("id"),
                            points = points,
                            width = json.optDouble("width", 18.0).toFloat(),
                            isLocal = false,
                            nickname = nickname,
                            colorIndex = colorIndex,
                            authorId = json.optString("authorId").takeIf { it.isNotBlank() && it != "null" },
                            gender = json.optString("gender").takeIf { it.isNotBlank() && it != "null" },
                            emoji = json.optString("emoji").takeIf { it.isNotBlank() && it != "null" }
                                ?.let { clipCanvasEmojiId(it) },
                            colorLocked = json.optBoolean("colorLocked", false),
                            templateParts = parseTemplateParts(json.optJSONArray("templateParts")),
                            templateScale = json.optDouble("templateScale", 1.0).toFloat()
                                .coerceIn(0.2f, 4f),
                            templateRotation = json.optDouble("templateRotation", 0.0).toFloat(),
                            templateCoordSpace = json.optString("templateCoordSpace")
                                .takeIf { it.equals("canvas", true) || it.equals("square", true) }
                        )
                    )
                }
                "undo" -> PairMessage.UndoMsg(json.getString("id"))
                "presence" -> {
                    val nickname = json.optString("nickname").takeIf { it.isNotBlank() && it != "null" }
                    val colorIndex = if (json.has("colorIndex")) {
                        json.optInt("colorIndex", 0)
                    } else {
                        nickname?.let { com.luv.couple.data.PeerPalette.indexFor(it.lowercase()) } ?: 0
                    }
                    PairMessage.Presence(
                        active = json.optBoolean("active", false),
                        nickname = nickname,
                        colorIndex = colorIndex,
                        peerKey = json.optString("peerKey").takeIf { it.isNotBlank() && it != "null" }
                            ?: json.optString("userId").takeIf { it.isNotBlank() && it != "null" }
                            ?: nickname,
                        userId = json.optString("userId").takeIf { it.isNotBlank() && it != "null" },
                        gender = json.optString("gender").takeIf { it.isNotBlank() && it != "null" }
                    )
                }
                "note" -> PairMessage.Note(json.optString("text").take(80))
                "recolor" -> PairMessage.Recolor(
                    nickname = json.optString("nickname").takeIf { it.isNotBlank() && it != "null" },
                    colorIndex = json.optInt("colorIndex", 0)
                )
                "reaction" -> PairMessage.Reaction(
                    emoji = clipCanvasEmojiId(json.optString("emoji")),
                    nickname = json.optString("nickname").takeIf { it.isNotBlank() && it != "null" }
                )
                "sticker_place" -> PairMessage.StickerPlace(
                    id = json.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                    emoji = clipCanvasEmojiId(json.optString("emoji")),
                    x = json.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
                    y = json.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f),
                    nickname = json.optString("nickname").takeIf { it.isNotBlank() && it != "null" }
                )
                "sticker_remove" -> PairMessage.StickerRemove(json.getString("id"))
                "game_board" -> PairMessage.GameBoard(
                    game = json.optString("game", "ttt"),
                    visible = json.optBoolean("visible", true)
                )
                "clear" -> PairMessage.Clear
                "clear_propose" -> PairMessage.ClearPropose(
                    json.optString("nickname").takeIf { it.isNotBlank() && it != "null" }
                )
                "clear_vote" -> PairMessage.ClearVote(
                    proposalId = json.getString("proposalId"),
                    yes = json.optBoolean("yes", json.optString("vote") == "yes")
                )
                "ping" -> PairMessage.Ping
                "pong" -> PairMessage.Pong
                else -> null
            }
        }.getOrNull()
    }

    const val TEMPLATE_MAX_PARTS = 200
    const val TEMPLATE_MAX_POINTS = 800
    private const val TEMPLATE_MIN_POINT_DIST = 0.004f

    /** Abstand-Downsampling — behält Form bei langen Dauerstrichen. */
    fun downsampleTemplatePoints(
        points: List<StrokePoint>,
        maxPoints: Int = TEMPLATE_MAX_POINTS,
        minDist: Float = TEMPLATE_MIN_POINT_DIST
    ): List<StrokePoint> {
        if (points.size <= 2) return points
        val minD2 = minDist * minDist
        val kept = ArrayList<StrokePoint>(points.size.coerceAtMost(maxPoints))
        kept.add(points.first())
        for (i in 1 until points.lastIndex) {
            val prev = kept.last()
            val p = points[i]
            val dx = p.x - prev.x
            val dy = p.y - prev.y
            if (dx * dx + dy * dy >= minD2) kept.add(p)
        }
        val last = points.last()
        if (kept.last().x != last.x || kept.last().y != last.y) kept.add(last)
        if (kept.size <= maxPoints) return kept
        val out = ArrayList<StrokePoint>(maxPoints)
        out.add(kept.first())
        val step = (kept.size - 1).toFloat() / (maxPoints - 1)
        for (i in 1 until maxPoints - 1) {
            out.add(kept[kotlin.math.round(i * step).toInt().coerceIn(0, kept.lastIndex)])
        }
        out.add(kept.last())
        return out
    }

    fun encodeTemplateParts(parts: List<TemplateStrokePart>): JSONArray {
        val arr = JSONArray()
        parts.take(TEMPLATE_MAX_PARTS).forEach { part ->
            val pts = JSONArray()
            downsampleTemplatePoints(part.points).forEach { p ->
                pts.put(JSONObject().put("x", p.x.toDouble()).put("y", p.y.toDouble()))
            }
            if (pts.length() >= 2) {
                arr.put(
                    JSONObject()
                        .put("points", pts)
                        .put("width", part.width.toDouble())
                        .put("colorIndex", part.colorIndex)
                )
            }
        }
        return arr
    }

    fun parseTemplateParts(arr: JSONArray?): List<TemplateStrokePart>? {
        if (arr == null || arr.length() == 0) return null
        val out = buildList {
            for (i in 0 until minOf(arr.length(), TEMPLATE_MAX_PARTS)) {
                val o = arr.optJSONObject(i) ?: continue
                val ptsJson = o.optJSONArray("points") ?: continue
                val points = buildList {
                    for (pi in 0 until minOf(ptsJson.length(), TEMPLATE_MAX_POINTS)) {
                        val p = ptsJson.optJSONObject(pi) ?: continue
                        add(
                            StrokePoint(
                                x = p.optDouble("x", 0.0).toFloat().coerceIn(0f, 1f),
                                y = p.optDouble("y", 0.0).toFloat().coerceIn(0f, 1f)
                            )
                        )
                    }
                }
                if (points.size < 2) continue
                add(
                    TemplateStrokePart(
                        points = points,
                        width = o.optDouble("width", 18.0).toFloat().coerceIn(3f, 48f),
                        colorIndex = o.optInt("colorIndex", 0).coerceIn(0, 31)
                    )
                )
            }
        }
        return out.ifEmpty { null }
    }
}
