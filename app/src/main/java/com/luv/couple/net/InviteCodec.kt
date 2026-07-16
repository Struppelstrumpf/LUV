package com.luv.couple.net

import android.util.Base64
import com.luv.couple.data.InvitePayload
import org.json.JSONObject
import java.nio.charset.Charset

object InviteCodec {
    private const val PREFIX = "LUV1."

    fun encode(payload: InvitePayload): String {
        val json = JSONObject()
            .put("ip", payload.ip)
            .put("port", payload.port)
            .put("token", payload.token)
            .put("gender", payload.gender)
            .toString()
        val encoded = Base64.encodeToString(
            json.toByteArray(Charset.forName("UTF-8")),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return PREFIX + encoded
    }

    fun decode(raw: String): InvitePayload? {
        val cleaned = raw.trim().replace("\\s".toRegex(), "")
        if (!cleaned.startsWith(PREFIX)) return null
        return runCatching {
            val jsonBytes = Base64.decode(
                cleaned.removePrefix(PREFIX),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            val json = JSONObject(String(jsonBytes, Charset.forName("UTF-8")))
            InvitePayload(
                ip = json.getString("ip"),
                port = json.getInt("port"),
                token = json.getString("token"),
                gender = json.optString("gender", "MALE")
            )
        }.getOrNull()
    }
}
