package com.luv.couple.net

import com.luv.couple.BuildConfig
import com.luv.couple.data.AccountInfo
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

data class AuthResult(
    val sessionToken: String,
    val user: AccountInfo,
    val created: Boolean
)

data class RedeemResult(
    val type: String,
    val coins: Int = 0,
    val message: String = "",
    val user: AccountInfo
)

data class VoucherInfo(
    val code: String,
    val coins: Int,
    val maxRedeems: Int,
    val redeemCount: Int,
    val expiresAt: Long?
)

data class ShopPack(
    val id: String,
    val label: String,
    val coins: Int,
    val amountEur: String
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

    @Volatile
    var sessionToken: String? = null

    fun baseUrl(): String = BuildConfig.LUV_API_BASE_URL.trimEnd('/')

    fun publicJoinUrl(code: String): String =
        "https://reineke.pro/love/j/${code.uppercase()}"

    fun wsUrl(code: String, token: String, role: String, session: String? = sessionToken): String {
        val httpBase = baseUrl()
        val wsBase = when {
            httpBase.startsWith("https://") -> "wss://" + httpBase.removePrefix("https://")
            httpBase.startsWith("http://") -> "ws://" + httpBase.removePrefix("http://")
            else -> "ws://$httpBase"
        }
        val sessionQ = session?.takeIf { it.isNotBlank() }?.let { "&session=${it.encodeURL()}" }.orEmpty()
        return "$wsBase/v1/ws?code=${code.encodeURL()}&token=${token.encodeURL()}&role=${role.encodeURL()}$sessionQ"
    }

    suspend fun authDevice(
        installId: String,
        installSecret: String,
        nickname: String
    ): AuthResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("installId", installId)
            .put("installSecret", installSecret)
            .put("nickname", nickname)
            .toString()
            .toRequestBody(jsonMedia)
        val request = Request.Builder()
            .url("${baseUrl()}/v1/auth/device")
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw LuvApiException("Konto konnte nicht erstellt werden.")
            val json = JSONObject(raw)
            val token = json.getString("sessionToken")
            sessionToken = token
            AuthResult(
                sessionToken = token,
                user = AccountInfo.fromApi(json.getJSONObject("user")),
                created = json.optBoolean("created", false)
            )
        }
    }

    suspend fun me(): AccountInfo = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/me")
        AccountInfo.fromApi(json.getJSONObject("user"))
    }

    suspend fun claimDaily(): Pair<AccountInfo, Boolean> = withContext(Dispatchers.IO) {
        val json = authedPost("/v1/me/daily-claim", "{}")
        AccountInfo.fromApi(json.getJSONObject("user")) to json.optBoolean("claimed", false)
    }

    suspend fun redeem(code: String): RedeemResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("code", code).toString().toRequestBody(jsonMedia)
        val request = authedRequestBuilder("/v1/redeem").post(body).build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful) {
                throw LuvApiException(
                    when (json?.optString("error")) {
                        "code_not_found" -> "Code ungültig"
                        "code_expired" -> "Code abgelaufen"
                        "code_exhausted" -> "Code bereits aufgebraucht"
                        "already_redeemed" -> "Schon eingelöst"
                        else -> "Einlösen fehlgeschlagen"
                    }
                )
            }
            RedeemResult(
                type = json!!.optString("type", "voucher"),
                coins = json.optInt("coins", 0),
                message = json.optString("message", ""),
                user = AccountInfo.fromApi(json.getJSONObject("user"))
            )
        }
    }

    suspend fun listVouchers(): List<VoucherInfo> = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/admin/vouchers")
        val arr = json.getJSONArray("vouchers")
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    VoucherInfo(
                        code = o.getString("code"),
                        coins = o.optInt("coins"),
                        maxRedeems = o.optInt("maxRedeems"),
                        redeemCount = o.optInt("redeemCount"),
                        expiresAt = if (o.isNull("expiresAt")) null
                        else o.optLong("expiresAt").takeIf { it > 0 }
                    )
                )
            }
        }
    }

    suspend fun createVoucher(
        coins: Int,
        maxRedeems: Int = 1,
        validDays: Int = 30,
        forever: Boolean = false,
        code: String? = null
    ): VoucherInfo = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("coins", coins)
            .put("maxRedeems", maxRedeems)
            .put("validDays", validDays)
            .put("forever", forever)
        if (!code.isNullOrBlank()) payload.put("code", code)
        val json = authedPost("/v1/admin/vouchers", payload.toString())
        val o = json.getJSONObject("voucher")
        VoucherInfo(
            code = o.getString("code"),
            coins = o.optInt("coins"),
            maxRedeems = o.optInt("maxRedeems"),
            redeemCount = o.optInt("redeemCount"),
            expiresAt = if (o.isNull("expiresAt")) null else o.optLong("expiresAt").takeIf { it > 0 }
        )
    }

    suspend fun shopPacks(): Pair<Boolean, List<ShopPack>> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${baseUrl()}/v1/shop/packs").get().build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = JSONObject(raw)
            val arr = json.optJSONArray("packs") ?: return@use false to emptyList()
            val packs = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ShopPack(
                            id = o.getString("id"),
                            label = o.optString("label"),
                            coins = o.optInt("coins"),
                            amountEur = o.optString("amountEur")
                        )
                    )
                }
            }
            json.optBoolean("enabled", false) to packs
        }
    }

    suspend fun checkout(packId: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().put("packId", packId).toString().toRequestBody(jsonMedia)
        val request = authedRequestBuilder("/v1/shop/checkout").post(body).build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful) {
                throw LuvApiException(json?.optString("message") ?: "Shop gerade nicht verfügbar")
            }
            json?.optString("checkoutUrl")?.takeIf { it.isNotBlank() }
                ?: throw LuvApiException("Keine Checkout-URL")
        }
    }

    suspend fun createRoom(): RoomSession = withContext(Dispatchers.IO) {
        val request = authedRequestBuilder("/v1/rooms").post(emptyBody).build()
        executeRoom(request)
    }

    suspend fun joinRoom(rawCode: String): RoomSession = withContext(Dispatchers.IO) {
        val code = normalizeCode(rawCode)
            ?: throw LuvApiException("Ungültiger Link oder Code.")
        val request = authedRequestBuilder("/v1/rooms/$code/join").post(emptyBody).build()
        executeRoom(request)
    }

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
        if (stripped.length in 4..12 && stripped.all { it.isLetterOrDigit() }) return stripped
        bareCodeRegex.find(text.uppercase())?.groupValues?.getOrNull(1)?.let { bare ->
            if (bare.all { it.isLetterOrDigit() }) return bare
        }
        return null
    }

    private fun authedRequestBuilder(path: String): Request.Builder {
        val b = Request.Builder().url("${baseUrl()}$path")
        sessionToken?.let { b.header("Authorization", "Bearer $it") }
        return b
    }

    private fun authedGet(path: String): JSONObject {
        val request = authedRequestBuilder(path).get().build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw LuvApiException("API-Fehler (${response.code})")
            return JSONObject(raw)
        }
    }

    private fun authedPost(path: String, jsonBody: String): JSONObject {
        val request = authedRequestBuilder(path)
            .post(jsonBody.toRequestBody(jsonMedia))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw LuvApiException("API-Fehler (${response.code})")
            return JSONObject(raw)
        }
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
