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
    private val inviteRegex = Regex("""LUV-?([A-Z0-9]{4,12})""", RegexOption.IGNORE_CASE)
    private val bareCodeRegex = Regex("""\b([A-Z0-9]{6})\b""", RegexOption.IGNORE_CASE)

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
        val code = normalizeCode(rawCode)
            ?: throw LuvApiException("Ungültiger Code. Bitte nur z. B. LUV-AB12CD einfügen.")
        val request = Request.Builder()
            .url("${baseUrl()}/v1/rooms/$code/join")
            .post(emptyBody)
            .build()
        executeRoom(request)
    }

    /**
     * Akzeptiert kurzen Code, LUV-Code oder ganzen WhatsApp-Text.
     */
    fun normalizeCode(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        inviteRegex.find(text)?.groupValues?.getOrNull(1)?.uppercase()?.let { extracted ->
            if (extracted.all { it.isLetterOrDigit() }) return extracted
        }

        val compact = text.uppercase().replace("\\s".toRegex(), "")
        val stripped = compact.removePrefix("LUV-").removePrefix("LUV")
        if (stripped.length in 4..12 && stripped.all { it.isLetterOrDigit() }) {
            return stripped
        }

        bareCodeRegex.find(text.uppercase())?.groupValues?.getOrNull(1)?.let { bare ->
            if (bare.all { it.isLetterOrDigit() }) return bare
        }
        return null
    }

    private fun executeRoom(request: Request): RoomSession {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val err = runCatching { JSONObject(body).optString("error") }.getOrNull()
                throw LuvApiException(
                    when (err) {
                        "room_not_found" -> "Raum nicht gefunden. Host muss online hosten, dann Code neu teilen."
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
