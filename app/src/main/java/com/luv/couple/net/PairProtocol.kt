package com.luv.couple.net

import com.luv.couple.data.Stroke
import com.luv.couple.data.StrokePoint
import org.json.JSONArray
import org.json.JSONObject

sealed class PairMessage {
    data class Hello(val token: String) : PairMessage()
    data class HelloOk(val ok: Boolean) : PairMessage()
    data class StrokeMsg(val stroke: Stroke) : PairMessage()
    data object Clear : PairMessage()
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
                    .put("points", points)
            }
            PairMessage.Clear -> JSONObject().put("type", "clear")
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
                    PairMessage.StrokeMsg(
                        Stroke(
                            id = json.getString("id"),
                            points = points,
                            width = json.optDouble("width", 18.0).toFloat()
                        )
                    )
                }
                "clear" -> PairMessage.Clear
                "ping" -> PairMessage.Ping
                "pong" -> PairMessage.Pong
                else -> null
            }
        }.getOrNull()
    }
}
