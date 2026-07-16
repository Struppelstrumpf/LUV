package com.luv.couple.net

import com.luv.couple.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RoomSession(
    val code: String,
    val token: String,
    val invite: String
)

class LuvApiException(message: String) : Exception(message)

object LuvApiClient {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val emptyBody = "{}".toRequestBody(jsonMedia)

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    fun baseUrl(): String = BuildConfig.LUV_API_BASE_URL.trimEnd('/')

    fun wsUrl(code: String, token: String, role: String): String {
        val httpBase = baseUrl()
        val wsBase = when {
            httpBase.startsWith("https://") -> "wss://" + httpBase.removePrefix("https://")
            httpBase.startsWith("http://") -> "ws://" + httpBase.removePrefix("http://")
            else -> "ws://$httpBase"
        }
        return "$wsBase/v1/ws?code=${code.encodeURL()}&token=${token.encodeURL()}&role=${role.encodeURL()}"
    }

    suspend fun createRoom(): RoomSession = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl()}/v1/rooms")
            .post(emptyBody)
            .build()
        executeRoom(request)
    }

    suspend fun joinRoom(rawCode: String): RoomSession = withContext(Dispatchers.IO) {
        val code = normalizeCode(rawCode) ?: throw LuvApiException("Ungültiger Code")
        val request = Request.Builder()
            .url("${baseUrl()}/v1/rooms/$code/join")
            .post(emptyBody)
            .build()
        executeRoom(request)
    }

    fun normalizeCode(raw: String): String? {
        val cleaned = raw.trim().uppercase().replace("\\s".toRegex(), "")
        val code = cleaned.removePrefix("LUV-").removePrefix("LUV")
        if (code.length !in 4..12) return null
        if (!code.all { it.isLetterOrDigit() }) return null
        return code
    }

    private fun executeRoom(request: Request): RoomSession {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val err = runCatching { JSONObject(body).optString("error") }.getOrNull()
                throw LuvApiException(
                    when (err) {
                        "room_not_found" -> "Raum nicht gefunden"
                        "room_full" -> "Raum ist schon voll"
                        else -> "API-Fehler (${response.code})"
                    }
                )
            }
            val json = JSONObject(body)
            return RoomSession(
                code = json.getString("code"),
                token = json.getString("token"),
                invite = json.optString("invite", "LUV-${json.getString("code")}")
            )
        }
    }

    private fun String.encodeURL(): String =
        java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
}
