package com.luv.couple.net

import com.luv.couple.data.Stroke
import com.luv.couple.data.StrokePoint
import org.json.JSONArray
import org.json.JSONObject

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
                JSONObject()
                    .put("type", "stroke")
                    .put("id", message.stroke.id)
                    .put("width", message.stroke.width.toDouble())
                    .put("nickname", message.stroke.nickname ?: JSONObject.NULL)
                    .put("colorIndex", message.stroke.colorIndex)
                    .put("authorId", message.stroke.authorId ?: JSONObject.NULL)
                    .put("gender", message.stroke.gender ?: JSONObject.NULL)
                    .put("points", points)
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
                .put("emoji", message.emoji.take(8))
                .put("nickname", message.nickname ?: JSONObject.NULL)
            is PairMessage.StickerPlace -> JSONObject()
                .put("type", "sticker_place")
                .put("id", message.id)
                .put("emoji", message.emoji.take(8))
                .put("x", message.x.toDouble())
                .put("y", message.y.toDouble())
                .put("nickname", message.nickname ?: JSONObject.NULL)
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
                            gender = json.optString("gender").takeIf { it.isNotBlank() && it != "null" }
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
                    emoji = json.optString("emoji").take(8),
                    nickname = json.optString("nickname").takeIf { it.isNotBlank() && it != "null" }
                )
                "sticker_place" -> PairMessage.StickerPlace(
                    id = json.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                    emoji = json.optString("emoji").take(8),
                    x = json.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
                    y = json.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f),
                    nickname = json.optString("nickname").takeIf { it.isNotBlank() && it != "null" }
                )
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
}
