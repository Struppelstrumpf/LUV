package com.luv.couple.net

import com.luv.couple.BuildConfig
import com.luv.couple.data.AccountInfo
import com.luv.couple.data.PeerPalette
import com.luv.couple.data.RoomPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.luv.couple.data.RosterMember
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RoomSession(
    val code: String,
    val token: String,
    val invite: String,
    val joinUrl: String,
    val maxPeers: Int = PeerPalette.MAX_PEERS,
    val capacity: Int = PeerPalette.FREE_LOBBY_START_CAPACITY,
    val isFree: Boolean = false,
    val name: String = "Lobby",
    val hostNickname: String = "Host",
    val hostColorSide: String = "blue",
    val suggestedColorIndex: Int? = null,
    val peers: Int = 0,
    val memberList: List<RosterMember> = emptyList()
)

data class AuthResult(
    val sessionToken: String,
    val user: AccountInfo,
    val created: Boolean,
    val linked: Boolean = false,
    val remoteLobbies: List<RemoteLobby> = emptyList()
)

data class AuthConfig(
    val googleEnabled: Boolean,
    val googleWebClientId: String?
)

data class CanvasMemory(
    val code: String,
    val lobbyName: String,
    val releasedAt: Long,
    val expiresAt: Long,
    val imageUrl: String
)

data class PublicCanvasPreview(
    val id: String,
    val lobbyName: String,
    val hostNickname: String,
    val memberNicknames: List<String>,
    val nameLine: String,
    val imageUrl: String
)

data class RemoteLobby(
    val code: String,
    val name: String,
    val token: String?,
    val capacity: Int,
    val isFree: Boolean,
    val hostColorSide: String,
    val invite: String,
    val hostNickname: String
)

data class RedeemResult(
    val type: String,
    val coins: Int = 0,
    val message: String = "",
    val user: AccountInfo
)

data class PublicReportInfo(
    val id: String,
    val publicId: String,
    val nameLine: String,
    val hostNickname: String,
    val reporterNickname: String,
    val imageUrl: String,
    val reportedAt: Long
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
    val amountEur: String,
    val compareAtEur: String? = null
)

class LuvApiException(
    message: String,
    val error: String? = null
) : Exception(message) {
    val isNoCoins: Boolean
        get() = error == "no_coins" ||
            message?.contains("coin", ignoreCase = true) == true
}

object LuvApiClient {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val emptyBody = "{}".toRequestBody(jsonMedia)
    private val inviteRegex = Regex("""LUV-?([A-Z0-9]{4,12})""", RegexOption.IGNORE_CASE)
    private val joinUrlRegex = Regex(
        """(?:https?://)?(?:www\.)?reineke\.pro/(?:luv|love)/j/([A-Z0-9]{4,12})""",
        RegexOption.IGNORE_CASE
    )
    private val luvSchemeRegex = Regex("""luv://join/([A-Z0-9]{4,12})""", RegexOption.IGNORE_CASE)
    private val bareCodeRegex = Regex("""\b([A-Z0-9]{6})\b""", RegexOption.IGNORE_CASE)

    private val http = OkHttpClient.Builder()
        .dispatcher(
            Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 32
            }
        )
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    @Volatile
    var sessionToken: String? = null

    fun baseUrl(): String = BuildConfig.LUV_API_BASE_URL.trimEnd('/')

    fun publicJoinUrl(code: String): String =
        "https://reineke.pro/luv/j/${code.uppercase()}"

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

    suspend fun authConfig(): AuthConfig = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl()}/v1/auth/config")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            AuthConfig(
                googleEnabled = json?.optBoolean("googleEnabled", false) == true,
                googleWebClientId = json?.optString("googleWebClientId")?.takeIf { it.isNotBlank() }
            )
        }
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

    suspend fun authGoogle(idToken: String): AuthResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("idToken", idToken).toString().toRequestBody(jsonMedia)
        val request = authedRequestBuilder("/v1/auth/google").post(body).build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful) {
                throw LuvApiException(
                    message = json?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: when (json?.optString("error")) {
                            "google_disabled" -> "Google-Login ist noch nicht eingerichtet."
                            "google_in_use" -> "Dieses Google-Konto gehört schon zu einem anderen LUV-Konto."
                            "already_linked_other" -> "Schon mit einem anderen Google-Konto verbunden."
                            else -> "Google-Anmeldung fehlgeschlagen."
                        },
                    error = json?.optString("error")
                )
            }
            val parsed = json ?: throw LuvApiException("Ungültige Server-Antwort")
            val token = parsed.getString("sessionToken")
            sessionToken = token
            AuthResult(
                sessionToken = token,
                user = AccountInfo.fromApi(parsed.getJSONObject("user")),
                created = parsed.optBoolean("created", false),
                linked = parsed.optBoolean("linked", false),
                remoteLobbies = parseRemoteLobbies(parsed.optJSONArray("lobbies"))
            )
        }
    }

    suspend fun logout(): Unit = withContext(Dispatchers.IO) {
        runCatching {
            val request = authedRequestBuilder("/v1/auth/logout").post(emptyBody).build()
            http.newCall(request).execute().use { }
        }
        sessionToken = null
    }

    /** Löscht Konto inkl. Google-Verknüpfung auf dem Server. */
    suspend fun deleteAccount(): Unit = withContext(Dispatchers.IO) {
        val request = authedRequestBuilder("/v1/me").delete(emptyBody).build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val json = runCatching { JSONObject(raw) }.getOrNull()
                throw LuvApiException(
                    json?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: "Konto konnte nicht gelöscht werden."
                )
            }
        }
        sessionToken = null
    }

    suspend fun myLobbies(): List<RemoteLobby> = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/me/lobbies")
        parseRemoteLobbies(json.optJSONArray("lobbies"))
    }

    private fun parseRemoteLobbies(arr: org.json.JSONArray?): List<RemoteLobby> {
        if (arr == null) return emptyList()
        val out = ArrayList<RemoteLobby>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val code = o.optString("code").uppercase()
            if (code.isBlank()) continue
            out += RemoteLobby(
                code = code,
                name = o.optString("name", "Lobby"),
                token = o.optString("token").takeIf { it.isNotBlank() },
                capacity = o.optInt("capacity", PeerPalette.FREE_LOBBY_START_CAPACITY),
                isFree = o.optBoolean("isFree", false),
                hostColorSide = o.optString("hostColorSide", "blue"),
                invite = o.optString("invite"),
                hostNickname = o.optString("hostNickname", "Host")
            )
        }
        return out
    }

    suspend fun me(): AccountInfo = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/me")
        AccountInfo.fromApi(json.getJSONObject("user"))
    }

    suspend fun updateNickname(nickname: String): AccountInfo = withContext(Dispatchers.IO) {
        val body = JSONObject().put("nickname", nickname.trim().take(18)).toString()
        val json = authedPatch("/v1/me", body)
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

    suspend fun reportPublicCanvas(publicId: String): Boolean = withContext(Dispatchers.IO) {
        val clean = publicId.trim()
        if (clean.isBlank()) throw LuvApiException("Kein Bild zum Melden.")
        val json = authedPost("/v1/public-canvases/${clean.encodeURL()}/report", "{}")
        json.optBoolean("ok", true)
    }

    suspend fun listPublicReports(): List<PublicReportInfo> = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/admin/public-reports")
        val arr = json.optJSONArray("reports") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    PublicReportInfo(
                        id = o.getString("id"),
                        publicId = o.optString("publicId"),
                        nameLine = o.optString("nameLine", "Öffentliche Leinwand"),
                        hostNickname = o.optString("hostNickname", "Jemand"),
                        reporterNickname = o.optString("reporterNickname", "Jemand"),
                        imageUrl = o.optString("imageUrl"),
                        reportedAt = o.optLong("reportedAt")
                    )
                )
            }
        }
    }

    suspend fun keepPublicReport(reportId: String) = withContext(Dispatchers.IO) {
        authedPost("/v1/admin/public-reports/${reportId.encodeURL()}/keep", "{}")
    }

    suspend fun deletePublicReport(reportId: String): Boolean = withContext(Dispatchers.IO) {
        val json = authedPost("/v1/admin/public-reports/${reportId.encodeURL()}/delete", "{}")
        json.optBoolean("banned", false)
    }

    suspend fun shopPacks(): Pair<Boolean, List<ShopPack>> = withContext(Dispatchers.IO) {
        val request = authedRequestBuilder("/v1/shop/packs").get().build()
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
                            amountEur = o.optString("amountEur"),
                            compareAtEur = o.optString("compareAtEur").takeIf { it.isNotBlank() }
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

    suspend fun createRoom(name: String = "Lobby", hostColorSide: String = "blue"): RoomSession =
        withContext(Dispatchers.IO) {
            val side = if (hostColorSide.equals("purple", ignoreCase = true)) "purple" else "blue"
            val body = JSONObject()
                .put("name", name.trim().take(PeerPalette.MAX_LOBBY_NAME_LENGTH).ifBlank { "Lobby" })
                .put("hostColorSide", side)
                .toString()
                .toRequestBody(jsonMedia)
            val request = authedRequestBuilder("/v1/rooms").post(body).build()
            executeRoom(request)
        }

    suspend fun joinRoom(rawCode: String): RoomSession = withContext(Dispatchers.IO) {
        val code = normalizeCode(rawCode)
            ?: throw LuvApiException("Ungültiger Link oder Code.")
        val request = authedRequestBuilder("/v1/rooms/$code/join").post(emptyBody).build()
        executeRoom(request)
    }

    suspend fun roomPreview(rawCode: String): RoomPreview = withContext(Dispatchers.IO) {
        val code = normalizeCode(rawCode)
            ?: throw LuvApiException("Ungültiger Link oder Code.")
        val request = Request.Builder()
            .url("${baseUrl()}/v1/rooms/$code/preview")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful) {
                val err = json?.optString("error")
                throw LuvApiException(
                    message = json?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: when (err) {
                            "room_not_found" -> "Lobby nicht gefunden. Host muss online sein."
                            else -> "Lobby nicht erreichbar."
                        },
                    error = err
                )
            }
            parsePreview(json ?: throw LuvApiException("Ungültige Server-Antwort"))
        }
    }

    /** Host: Lobby nach Restart wiederherstellen */
    suspend fun ensureRoom(code: String, token: String): RoomSession = withContext(Dispatchers.IO) {
        val body = JSONObject().put("token", token).toString().toRequestBody(jsonMedia)
        val request = authedRequestBuilder("/v1/rooms/${code.encodeURL()}/ensure").post(body).build()
        executeRoom(request)
    }

    /** Host: Lobby-Name für alle syncen */
    suspend fun renameRoom(code: String, name: String): String = withContext(Dispatchers.IO) {
        val clean = normalizeCode(code) ?: throw LuvApiException("Ungültiger Code.")
        val cleanName = name.trim().take(PeerPalette.MAX_LOBBY_NAME_LENGTH)
        val body = JSONObject().put("name", cleanName).toString()
        val json = authedPatch("/v1/rooms/$clean", body)
        json.optString("name", cleanName)
    }

    suspend fun buySlot(code: String): RoomSession = withContext(Dispatchers.IO) {
        val clean = normalizeCode(code) ?: throw LuvApiException("Ungültiger Code.")
        val request = authedRequestBuilder("/v1/rooms/$clean/slots").post(emptyBody).build()
        executeRoom(request)
    }

    suspend fun leaveRoom(code: String) = withContext(Dispatchers.IO) {
        val clean = normalizeCode(code) ?: return@withContext
        val request = authedRequestBuilder("/v1/rooms/$clean/leave").post(emptyBody).build()
        http.newCall(request).execute().use { /* best effort — Lobby bleibt für andere */ }
    }

    @Deprecated("Use leaveRoom — abandon kicked everyone")
    suspend fun abandonRoom(code: String) = leaveRoom(code)

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

    private fun parsePreview(json: JSONObject): RoomPreview {
        val code = json.getString("code")
        return RoomPreview(
            code = code,
            name = json.optString("name", "Lobby"),
            hostNickname = json.optString("hostNickname", "Host"),
            peers = json.optInt("peers", 0),
            capacity = json.optInt("capacity", PeerPalette.FREE_LOBBY_START_CAPACITY),
            maxPeers = json.optInt("maxPeers", PeerPalette.MAX_PEERS),
            isFree = json.optBoolean("isFree", false),
            invite = json.optString("invite", "LUV-$code"),
            joinUrl = json.optString("joinUrl", publicJoinUrl(code)),
            hostColorSide = json.optString("hostColorSide", "blue").ifBlank { "blue" }
        )
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

    private fun authedPatch(path: String, jsonBody: String): JSONObject {
        val request = authedRequestBuilder(path)
            .patch(jsonBody.toRequestBody(jsonMedia))
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
            val json = runCatching { JSONObject(body) }.getOrNull()
            if (!response.isSuccessful) {
                val err = json?.optString("error")
                val msg = json?.optString("message")?.takeIf { it.isNotBlank() }
                throw LuvApiException(
                    message = msg ?: when (err) {
                        "room_not_found" -> "Lobby nicht gefunden. Host muss online sein — Link neu teilen."
                        "room_full" -> "Lobby ist voll (max. ${PeerPalette.MAX_PEERS} Personen)."
                        "max_lobbies" -> "Maximal ${PeerPalette.MAX_LOBBIES} Lobbys."
                        "no_coins" -> "Nicht genug Coins."
                        "capacity_full" -> "Maximal ${PeerPalette.MAX_PEERS} Personen."
                        "not_host" -> "Nur der Host kann Plätze freischalten."
                        "unauthorized" -> "Bitte neu anmelden."
                        else -> "API-Fehler (${response.code})"
                    },
                    error = err
                )
            }
            val parsed = json ?: throw LuvApiException("Ungültige Server-Antwort")
            parsed.optJSONObject("user")?.let { userJson ->
                AccountSession.setAccount(AccountInfo.fromApi(userJson))
            }
            val code = parsed.getString("code")
            val suggested = if (parsed.has("suggestedColorIndex")) {
                parsed.optInt("suggestedColorIndex", -1).takeIf { it >= 0 }
            } else {
                null
            }
            return RoomSession(
                code = code,
                token = parsed.optString("token").ifBlank {
                    // Slot-Kauf liefert ggf. kein Token — Caller behält bestehendes
                    ""
                },
                invite = parsed.optString("invite", "LUV-$code"),
                joinUrl = parsed.optString("joinUrl", publicJoinUrl(code)),
                maxPeers = parsed.optInt("maxPeers", PeerPalette.MAX_PEERS),
                capacity = parsed.optInt("capacity", PeerPalette.FREE_LOBBY_START_CAPACITY),
                isFree = parsed.optBoolean("isFree", false),
                name = parsed.optString("name", "Lobby"),
                hostNickname = parsed.optString("hostNickname", "Host"),
                hostColorSide = parsed.optString("hostColorSide", "blue").ifBlank { "blue" },
                suggestedColorIndex = suggested,
                peers = parsed.optInt("peers", parsed.optInt("count", 0)),
                memberList = parseMemberList(parsed)
            )
        }
    }

    private fun parseMemberList(json: JSONObject): List<RosterMember> {
        val rich = json.optJSONArray("memberList") ?: return emptyList()
        return buildList {
            for (i in 0 until rich.length()) {
                val o = rich.optJSONObject(i) ?: continue
                val nick = o.optString("nickname").trim().ifBlank { "Jemand" }
                add(
                    RosterMember(
                        userId = o.optString("userId").takeIf { it.isNotBlank() && it != "null" },
                        nickname = nick,
                        colorIndex = o.optInt("colorIndex", -1),
                        active = o.optBoolean("active", false) || o.optBoolean("online", false)
                    )
                )
            }
        }
    }

    suspend fun fetchRandomPublicCanvas(): PublicCanvasPreview? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${baseUrl()}/v1/public-canvases/random")
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@runCatching null
                val json = JSONObject(raw)
                if (!json.optBoolean("available", false)) return@runCatching null
                val names = buildList {
                    val arr = json.optJSONArray("memberNicknames")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    }
                }
                PublicCanvasPreview(
                    id = json.optString("id"),
                    lobbyName = json.optString("lobbyName", "Lobby"),
                    hostNickname = json.optString("hostNickname", "Jemand"),
                    memberNicknames = names,
                    nameLine = json.optString("nameLine").ifBlank {
                        json.optString("hostNickname", "Jemand")
                    },
                    imageUrl = json.optString("imageUrl")
                )
            }
        }.getOrNull()
    }

    suspend fun uploadCanvasSnapshot(code: String, token: String, imageBase64: String) =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("token", token)
                .put("imageBase64", imageBase64)
                .toString()
            authedPost("/v1/rooms/${code.uppercase()}/canvas-snapshot", body)
        }

    suspend fun touchCanvas(code: String, token: String): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().put("token", token).toString()
        authedPost("/v1/rooms/${code.uppercase()}/canvas-touch", body)
    }

    suspend fun fetchMemories(): List<CanvasMemory> = withContext(Dispatchers.IO) {
        runCatching {
            val json = authedGet("/v1/me/memories")
            val arr = json.optJSONArray("memories") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    parseMemory(o)?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun fetchRoomMemory(code: String, token: String): CanvasMemory? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = authedRequestBuilder("/v1/rooms/${code.uppercase()}/memory")
                    .header("x-room-token", token)
                    .get()
                    .build()
                http.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) return@runCatching null
                    val json = JSONObject(raw)
                    if (!json.optBoolean("available", false)) return@runCatching null
                    parseMemory(json)
                }
            }.getOrNull()
        }

    private fun parseMemory(o: JSONObject): CanvasMemory? {
        val code = o.optString("code").ifBlank { return null }
        val releasedAt = o.optLong("releasedAt", 0L)
        if (releasedAt <= 0L) return null
        return CanvasMemory(
            code = code,
            lobbyName = o.optString("lobbyName", "Lobby"),
            releasedAt = releasedAt,
            expiresAt = o.optLong("expiresAt", releasedAt + 24L * 60L * 60L * 1000L),
            imageUrl = o.optString("imageUrl", "/v1/rooms/$code/memory/image")
        )
    }

    private fun String.encodeURL(): String =
        java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
}
