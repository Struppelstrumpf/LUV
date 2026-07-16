package com.luv.couple.net

import com.luv.couple.BuildConfig
import com.luv.couple.data.PeerPalette
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
    val invite: String,
    val joinUrl: String,
    val maxPeers: Int = PeerPalette.MAX_PEERS
)

class LuvApiException(message: String) : Exception(message)

object LuvApiClient {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val emptyBody = "{}".toRequestBody(jsonMedia)
    private val inviteRegex = Regex("""LUV-?([A-Z0-9]{4,12})""", RegexOption.IGNORE_CASE)
    private val joinUrlRegex = Regex(
        """(?:https?://)?(?:www\.)?reineke\.pro/love/j/([A-Z0-9]{4,12})""",
        RegexOption.IGNORE_CASE
    )
    private val luvSchemeRegex = Regex("""luv://join/([A-Z0-9]{4,12})""", RegexOption.IGNORE_CASE)
    private val bareCodeRegex = Regex("""\b([A-Z0-9]{6})\b""", RegexOption.IGNORE_CASE)

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    fun baseUrl(): String = BuildConfig.LUV_API_BASE_URL.trimEnd('/')

    fun publicJoinUrl(code: String): String =
        "https://reineke.pro/love/j/${code.uppercase()}"

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
            ?: throw LuvApiException("Ungültiger Link oder Code.")
        val request = Request.Builder()
            .url("${baseUrl()}/v1/rooms/$code/join")
            .post(emptyBody)
            .build()
        executeRoom(request)
    }

    /**
     * Akzeptiert Code, LUV-Code, Join-URL oder ganzen WhatsApp-Text.
     */
    fun normalizeCode(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        joinUrlRegex.find(text)?.groupValues?.getOrNull(1)?.uppercase()?.let { return it }
        luvSchemeRegex.find(text)?.groupValues?.getOrNull(1)?.uppercase()?.let { return it }

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
                        "room_not_found" -> "Lobby nicht gefunden. Host muss online sein — Link neu teilen."
                        "room_full" -> "Lobby ist voll (max. ${PeerPalette.MAX_PEERS} Personen)."
                        else -> "API-Fehler (${response.code})"
                    }
                )
            }
            val json = JSONObject(body)
            val code = json.getString("code")
            return RoomSession(
                code = code,
                token = json.getString("token"),
                invite = json.optString("invite", "LUV-$code"),
                joinUrl = json.optString("joinUrl", publicJoinUrl(code)),
                maxPeers = json.optInt("maxPeers", PeerPalette.MAX_PEERS)
            )
        }
    }

    private fun String.encodeURL(): String =
        java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
}
