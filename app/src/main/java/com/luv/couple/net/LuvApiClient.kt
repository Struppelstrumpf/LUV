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
    val isRandom: Boolean = false,
    val isWedding: Boolean = false,
    val name: String = "Lobby",
    val hostNickname: String = "Host",
    val hostColorSide: String = "blue",
    val suggestedColorIndex: Int? = null,
    val peers: Int = 0,
    val memberList: List<RosterMember> = emptyList(),
    val role: String? = null
)

data class LootboxResult(
    val kind: String,
    val itemId: String,
    val emoji: String,
    val label: String,
    val shopPrice: Int,
    val chancePercent: Double,
    val coins: Int,
    val pendingId: String = "",
    val duplicate: Boolean = false,
    val coinsRefund: Int = 0
)

data class LootboxPurchaseResult(
    val purchased: List<LootboxResult>,
    val pending: List<LootboxResult>,
    val quantity: Int,
    val total: Int,
    val coins: Int
)

data class AuthResult(
    val sessionToken: String,
    val user: AccountInfo,
    val created: Boolean,
    val linked: Boolean = false,
    val remoteLobbies: List<RemoteLobby> = emptyList(),
    val joinedLobbies: List<RemoteLobby> = emptyList(),
    val settings: CloudSettings? = null
)

data class CloudSettings(
    val quietHours: com.luv.couple.data.QuietHoursSchedule =
        com.luv.couple.data.QuietHoursSchedule.EMPTY,
    val emojiBar: List<String> = emptyList(),
    val partnerDrawNotify: Boolean = true,
    val partnerHaptic: Boolean = true,
    val liveProximityRich: Boolean = true,
    val liveProximityWake: Boolean = false,
    val lobbyProximity: Map<String, Boolean> = emptyMap(),
    val brushWidth: Float = 18f,
    val updatedAt: Long = 0L
)

data class LobbyBundle(
    val hosted: List<RemoteLobby>,
    val joined: List<RemoteLobby>
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
    val imageUrl: String,
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    /** true = alle Exclude-IDs waren schon gesehen, Zyklus neu gestartet */
    val cycled: Boolean = false
)

data class RemoteLobby(
    val code: String,
    val name: String,
    val token: String?,
    val capacity: Int,
    val isFree: Boolean,
    val isRandom: Boolean = false,
    val isWedding: Boolean = false,
    val lastCanvasAt: Long = 0L,
    val lastCanvasActorId: String? = null,
    val hostColorSide: String,
    val invite: String,
    val hostNickname: String
)

data class PendingMarketSale(
    val id: String,
    val kind: String,
    val itemId: String,
    val emoji: String,
    val label: String,
    val priceCoins: Int,
    val soldAt: Long,
    val buyerNickname: String
)

data class PendingSalesResult(
    val sales: List<PendingMarketSale>,
    val totalCoins: Int,
    val count: Int
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

data class PeerReportInfo(
    val id: String,
    val targetNickname: String,
    val reporterNickname: String,
    val lobbyName: String,
    val imageUrl: String?,
    val reportedAt: Long
)

data class HelpMessageInfo(
    val id: String,
    val message: String,
    val nickname: String,
    val userId: String?,
    val createdAt: Long
)

data class StaffUserCard(
    val userId: String,
    val nickname: String,
    val email: String?,
    val role: String,
    val coins: Int,
    val banned: Boolean,
    val petEmoji: String,
    val permissions: Map<String, Boolean> = emptyMap(),
    val modSince: Long? = null,
    val marriage: LuvApiClient.MarriageInfo? = null,
    val marriageCooldownLabel: String? = null
)

data class StaffPermGroup(
    val id: String,
    val icon: String,
    val label: String,
    val description: String,
    val permissions: List<Pair<String, String>>
)

data class StaffOverview(
    val users: Int,
    val rooms: Int,
    val openPublicReports: Int,
    val openPeerReports: Int,
    val openHelpMessages: Int = 0,
    val moderators: Int,
    val vouchers: Int,
    val permissionGroups: List<StaffPermGroup>
)

data class StaffLobbyMember(
    val userId: String,
    val nickname: String,
    val online: Boolean,
    val colorIndex: Int = -1,
    val petEmoji: String = "🐣"
)

data class VoucherItemGrant(
    val kind: String,
    val itemId: String,
    val qty: Int = 1
)

data class StaffLobbyCard(
    val code: String,
    val name: String,
    val capacity: Int,
    val isFree: Boolean,
    val online: Int,
    val memberCount: Int,
    val members: List<StaffLobbyMember>,
    val active: Boolean,
    val live: Boolean,
    val createdAt: Long?,
    val lastActiveAt: Long?,
    val invite: String,
    val hostUserId: String?,
    val hostNickname: String
)

data class VoucherInfo(
    val code: String,
    val coins: Int,
    val maxRedeems: Int,
    val redeemCount: Int,
    val expiresAt: Long?,
    val items: List<VoucherItemGrant> = emptyList()
)

data class ShopPack(
    val id: String,
    val label: String,
    val coins: Int,
    val amountEur: String,
    val compareAtEur: String? = null,
    val onceOnly: Boolean = false,
    val isOffer: Boolean = false,
    val mostPurchased: Boolean = false,
    /** Anzeigepreis aus Google Play (z. B. „0,99 €“), sonst Server-amountEur */
    val displayPrice: String? = null
)

class LuvApiException(
    message: String,
    val error: String? = null
) : Exception(message) {
    val isNoCoins: Boolean
        get() = error == "no_coins" ||
            error == "insufficient_coins" ||
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

    /** Für Bild-Downloads (Begleiter-PNGs etc.). */
    fun httpClient(): OkHttpClient = http

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

    data class IntegrityNonceChallenge(
        val required: Boolean,
        val nonce: String?,
        val cloudProjectNumber: String?
    )

    suspend fun fetchIntegrityNonce(): IntegrityNonceChallenge = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl()}/v1/auth/integrity-nonce")
            .post(emptyBody)
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful) {
                return@use IntegrityNonceChallenge(false, null, null)
            }
            IntegrityNonceChallenge(
                required = json?.optBoolean("required", false) == true,
                nonce = json?.optString("nonce")?.takeIf { it.isNotBlank() },
                cloudProjectNumber = json?.optString("cloudProjectNumber")?.takeIf { it.isNotBlank() }
            )
        }
    }

    suspend fun authGoogle(
        idToken: String,
        integrityToken: String? = null,
        integrityNonce: String? = null
    ): AuthResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("idToken", idToken)
            .apply {
                if (!integrityToken.isNullOrBlank()) put("integrityToken", integrityToken)
                if (!integrityNonce.isNullOrBlank()) put("integrityNonce", integrityNonce)
            }
            .toString()
            .toRequestBody(jsonMedia)
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
                            "integrity_required",
                            "missing_integrity",
                            "device_untrusted",
                            "app_untrusted" ->
                                "Neue Konten nur über die Play-Store-App auf einem echten Gerät."
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
                remoteLobbies = parseRemoteLobbies(parsed.optJSONArray("lobbies")),
                joinedLobbies = parseRemoteLobbies(parsed.optJSONArray("joined")),
                settings = parseCloudSettings(parsed.optJSONObject("settings"))
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
        fun parseFail(raw: String, code: Int): Nothing {
            val json = runCatching { JSONObject(raw) }.getOrNull()
            throw LuvApiException(
                message = json?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: "Konto konnte nicht gelöscht werden. ($code)",
                error = json?.optString("error")
            )
        }
        // Ohne Body — DELETE + JSON-Body liefert bei manchen Setups 400
        val del = authedRequestBuilder("/v1/me").delete().build()
        http.newCall(del).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                sessionToken = null
                return@withContext
            }
            if (response.code != 400 && response.code != 405) {
                parseFail(raw, response.code)
            }
        }
        // Fallback
        val post = authedRequestBuilder("/v1/me/delete").post(emptyBody).build()
        http.newCall(post).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) parseFail(raw, response.code)
        }
        sessionToken = null
    }

    suspend fun myLobbies(): List<RemoteLobby> = withContext(Dispatchers.IO) {
        myLobbyBundle().hosted
    }

    suspend fun myLobbyBundle(): LobbyBundle = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/me/lobbies")
        LobbyBundle(
            hosted = parseRemoteLobbies(json.optJSONArray("lobbies")),
            joined = parseRemoteLobbies(json.optJSONArray("joined"))
        )
    }

    suspend fun fetchSettings(): CloudSettings = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/me/settings")
        parseCloudSettings(json.optJSONObject("settings"))
            ?: CloudSettings()
    }

    suspend fun putSettings(settings: CloudSettings): CloudSettings = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("settings", settingsToJson(settings))
            .toString()
        val json = authedPut("/v1/me/settings", body)
        parseCloudSettings(json.optJSONObject("settings")) ?: settings
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
                isRandom = o.optBoolean("isRandom", false),
                isWedding = o.optBoolean("isWedding", false),
                lastCanvasAt = o.optLong("lastCanvasAt", 0L),
                lastCanvasActorId = o.optString("lastCanvasActorId").takeIf { it.isNotBlank() },
                hostColorSide = o.optString("hostColorSide", "blue"),
                invite = o.optString("invite"),
                hostNickname = o.optString("hostNickname", "Host")
            )
        }
        return out
    }

    private fun parseCloudSettings(o: JSONObject?): CloudSettings? {
        if (o == null) return null
        val quietRaw = o.optJSONObject("quietHours")?.toString()
        val quiet = com.luv.couple.data.PrefsRepository.parseQuietHoursPublic(quietRaw)
        val emojiBar = buildList {
            val arr = o.optJSONArray("emojiBar")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    arr.optString(i).trim().takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }.distinct().take(com.luv.couple.shop.ShopCatalog.MAX_BAR)
        val prox = buildMap {
            val p = o.optJSONObject("lobbyProximity") ?: return@buildMap
            p.keys().forEach { key ->
                if (!p.optBoolean(key, false)) return@forEach
                val code = key.trim().uppercase().removePrefix("LUV-")
                if (code.length in 3..16 && code.all { it.isLetterOrDigit() }) {
                    put(code, true)
                }
            }
        }
        val brush = o.optDouble("brushWidth", 18.0).toFloat().coerceIn(6f, 40f)
        return CloudSettings(
            quietHours = quiet,
            emojiBar = emojiBar,
            partnerDrawNotify = o.optBoolean("partnerDrawNotify", true),
            partnerHaptic = o.optBoolean("partnerHaptic", true),
            liveProximityRich = o.optBoolean("liveProximityRich", true),
            liveProximityWake = o.optBoolean("liveProximityWake", false),
            lobbyProximity = prox,
            brushWidth = brush,
            updatedAt = o.optLong("updatedAt", 0L)
        )
    }

    fun settingsToJson(settings: CloudSettings): JSONObject {
        val quiet = JSONObject(
            com.luv.couple.data.PrefsRepository.encodeQuietHoursPublic(settings.quietHours)
        )
        val bar = org.json.JSONArray()
        settings.emojiBar.forEach { bar.put(it) }
        val prox = JSONObject()
        settings.lobbyProximity.forEach { (k, v) ->
            if (!v) return@forEach
            val code = k.trim().uppercase().removePrefix("LUV-")
            if (code.length in 3..16 && code.all { it.isLetterOrDigit() }) {
                prox.put(code, true)
            }
        }
        return JSONObject()
            .put("quietHours", quiet)
            .put("emojiBar", bar)
            .put("partnerDrawNotify", settings.partnerDrawNotify)
            .put("partnerHaptic", settings.partnerHaptic)
            .put("liveProximityRich", settings.liveProximityRich)
            .put("liveProximityWake", settings.liveProximityWake)
            .put("lobbyProximity", prox)
            .put("brushWidth", settings.brushWidth.toDouble())
            .put("updatedAt", settings.updatedAt)
    }

    suspend fun me(): AccountInfo = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/me")
        AccountInfo.fromApi(json.getJSONObject("user"))
    }

    /** Meldet: App ist im Vordergrund (für Live-Zähler). */
    suspend fun heartbeat(): Boolean = withContext(Dispatchers.IO) {
        if (sessionToken.isNullOrBlank()) return@withContext false
        val request = authedRequestBuilder("/v1/me/heartbeat").post(emptyBody).build()
        http.newCall(request).execute().use { it.isSuccessful }
    }

    /** Wie viele Nutzer gerade die App offen haben. */
    suspend fun fetchLiveCount(): Int = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl()}/v1/public/live-count")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext 0
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: return@withContext 0
            json.optInt("count", 0).coerceAtLeast(0)
        }
    }

    suspend fun updateNickname(nickname: String): AccountInfo = withContext(Dispatchers.IO) {
        val body = JSONObject().put("nickname", nickname.trim().take(18)).toString()
        val request = authedRequestBuilder("/v1/me")
            .patch(body.toRequestBody(jsonMedia))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful) {
                throw LuvApiException(
                    message = when (json?.optString("error")) {
                        "nickname_taken" -> "Dieser Spitzname ist schon vergeben."
                        "nickname_locked" -> "Dein Spitzname kann nicht mehr geändert werden."
                        "bad_nick" -> "Spitzname ungültig."
                        else -> json?.optString("message")?.takeIf { it.isNotBlank() }
                            ?: "Name speichern fehlgeschlagen."
                    },
                    error = json?.optString("error")
                )
            }
            AccountInfo.fromApi((json ?: throw LuvApiException("Ungültige Server-Antwort"))
                .getJSONObject("user"))
        }
    }

    data class UserLookup(
        val card: FriendCard,
        val friendStatus: String
    )

    suspend fun lookupUserByNickname(nickname: String): UserLookup =
        withContext(Dispatchers.IO) {
            val q = nickname.trim().take(18).encodeURL()
            val request = authedRequestBuilder("/v1/users/lookup?nickname=$q").get().build()
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull()
                if (!response.isSuccessful) {
                    throw LuvApiException(
                        message = when (json?.optString("error")) {
                            "not_found" -> "Niemand mit diesem Spitznamen gefunden."
                            "bad_nick" -> "Bitte einen Spitznamen eingeben."
                            else -> json?.optString("message")?.takeIf { it.isNotBlank() }
                                ?: "Suche fehlgeschlagen."
                        },
                        error = json?.optString("error")
                    )
                }
                val bodyJson = json ?: throw LuvApiException("Ungültige Server-Antwort")
                val card = parseFriendCard(bodyJson.optJSONObject("user"))
                    ?: throw LuvApiException("Ungültige Server-Antwort")
                UserLookup(
                    card = card,
                    friendStatus = bodyJson.optString("friendStatus", "none")
                )
            }
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

    private fun parseVoucherItems(arr: org.json.JSONArray?): List<VoucherItemGrant> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val kind = o.optString("kind").trim()
                val itemId = o.optString("itemId").ifBlank { o.optString("emoji") }.trim()
                if (kind.isBlank() || itemId.isBlank()) continue
                add(VoucherItemGrant(kind, itemId, o.optInt("qty", 1).coerceIn(1, 50)))
            }
        }
    }

    private fun parseVoucherInfo(o: JSONObject): VoucherInfo =
        VoucherInfo(
            code = o.getString("code"),
            coins = o.optInt("coins"),
            maxRedeems = o.optInt("maxRedeems"),
            redeemCount = o.optInt("redeemCount"),
            expiresAt = if (o.isNull("expiresAt")) null
            else o.optLong("expiresAt").takeIf { it > 0 },
            items = parseVoucherItems(o.optJSONArray("items"))
        )

    suspend fun listVouchers(): List<VoucherInfo> = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/admin/vouchers")
        val arr = json.getJSONArray("vouchers")
        buildList {
            for (i in 0 until arr.length()) {
                add(parseVoucherInfo(arr.getJSONObject(i)))
            }
        }
    }

    suspend fun listStaffLiveRooms(): List<StaffLobbyCard> = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/admin/rooms")
        parseStaffLobbyList(json.optJSONArray("rooms"))
    }

    suspend fun createVoucher(
        coins: Int,
        maxRedeems: Int = 1,
        validDays: Int = 30,
        forever: Boolean = false,
        code: String? = null,
        items: List<VoucherItemGrant> = emptyList()
    ): VoucherInfo = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("coins", coins)
            .put("maxRedeems", maxRedeems)
            .put("validDays", validDays)
            .put("forever", forever)
        if (!code.isNullOrBlank()) payload.put("code", code)
        if (items.isNotEmpty()) {
            val arr = org.json.JSONArray()
            items.forEach { it ->
                arr.put(
                    JSONObject()
                        .put("kind", it.kind)
                        .put("itemId", it.itemId)
                        .put("qty", it.qty)
                )
            }
            payload.put("items", arr)
        }
        val json = authedPost("/v1/admin/vouchers", payload.toString())
        parseVoucherInfo(json.getJSONObject("voucher"))
    }

    suspend fun reportPublicCanvas(publicId: String): Boolean = withContext(Dispatchers.IO) {
        val clean = publicId.trim()
        if (clean.isBlank()) throw LuvApiException("Kein Bild zum Melden.")
        val json = authedPost("/v1/public-canvases/${clean.encodeURL()}/report", "{}")
        json.optBoolean("ok", true)
    }

    /** Eigene Veröffentlichungen für Galerie-Sync zwischen Geräten */
    suspend fun listMyPublicCanvases(): List<PublicCanvasPreview> = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/public-canvases/mine")
        val arr = json.optJSONArray("items") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").trim()
                if (id.isBlank()) continue
                add(
                    PublicCanvasPreview(
                        id = id,
                        lobbyName = o.optString("lobbyName", "Galerie"),
                        hostNickname = o.optString("hostNickname", ""),
                        memberNicknames = buildList {
                            val nicks = o.optJSONArray("memberNicknames")
                            if (nicks != null) {
                                for (j in 0 until nicks.length()) {
                                    val n = nicks.optString(j).trim()
                                    if (n.isNotBlank()) add(n)
                                }
                            }
                        },
                        nameLine = o.optString("nameLine"),
                        imageUrl = o.optString("imageUrl"),
                        createdAt = o.optLong("createdAt", 0L),
                        expiresAt = o.optLong("expiresAt", 0L)
                    )
                )
            }
        }
    }

    suspend fun downloadPublicCanvasBytes(publicId: String): ByteArray = withContext(Dispatchers.IO) {
        val clean = publicId.trim()
        if (clean.isBlank()) throw LuvApiException("Kein Bild.")
        val request = authedRequestBuilder("/v1/public-canvases/${clean.encodeURL()}/image")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw LuvApiException("Bild konnte nicht geladen werden (${response.code}).")
            }
            response.body?.bytes() ?: throw LuvApiException("Bild leer.")
        }
    }

    suspend fun publishPublicCanvas(
        imageBase64: String,
        memberNicknames: List<String> = emptyList(),
        lobbyName: String = "Galerie"
    ): PublicCanvasPreview = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("imageBase64", imageBase64)
            .put("lobbyName", lobbyName.trim().take(40))
        if (memberNicknames.isNotEmpty()) {
            body.put(
                "memberNicknames",
                org.json.JSONArray(memberNicknames.map { it.trim().take(18) }.filter { it.isNotBlank() })
            )
        }
        val json = authedPost("/v1/public-canvases/publish", body.toString())
        val id = json.optString("id").ifBlank { throw LuvApiException("Veröffentlichen fehlgeschlagen.") }
        PublicCanvasPreview(
            id = id,
            lobbyName = lobbyName,
            hostNickname = "",
            memberNicknames = memberNicknames,
            nameLine = json.optString("nameLine"),
            imageUrl = json.optString("imageUrl")
        )
    }

    suspend fun unpublishPublicCanvas(publicId: String): Boolean = withContext(Dispatchers.IO) {
        val clean = publicId.trim()
        if (clean.isBlank()) throw LuvApiException("Keine Veröffentlichung.")
        val json = authedPost("/v1/public-canvases/${clean.encodeURL()}/unpublish", "{}")
        json.optBoolean("ok", true)
    }

    suspend fun publicCanvasStatus(publicId: String): Boolean = withContext(Dispatchers.IO) {
        val clean = publicId.trim()
        if (clean.isBlank()) return@withContext false
        runCatching {
            val request = Request.Builder()
                .url("${baseUrl()}/v1/public-canvases/${clean.encodeURL()}")
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@use false
                JSONObject(raw).optBoolean("available", false)
            }
        }.getOrDefault(false)
    }

    suspend fun reportPeer(
        lobbyCode: String,
        userId: String?,
        nickname: String,
        imageBase64: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val code = normalizeCode(lobbyCode) ?: throw LuvApiException("Ungültiger Lobby-Code.")
        val body = JSONObject()
            .put("userId", userId?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("nickname", nickname.trim().take(18))
        if (!imageBase64.isNullOrBlank()) {
            body.put("imageBase64", imageBase64)
        }
        val json = authedPost("/v1/rooms/$code/report-peer", body.toString())
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

    suspend fun submitHelpMessage(message: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("message", message.trim().take(800)).toString()
        authedPost("/v1/help-messages", body)
    }

    suspend fun listHelpMessages(): List<HelpMessageInfo> = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/admin/help-messages")
        val arr = json.optJSONArray("messages") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    HelpMessageInfo(
                        id = o.getString("id"),
                        message = o.optString("message"),
                        nickname = o.optString("nickname", "Jemand"),
                        userId = o.optString("userId").takeIf { it.isNotBlank() && it != "null" },
                        createdAt = o.optLong("createdAt")
                    )
                )
            }
        }
    }

    suspend fun deleteHelpMessage(messageId: String) = withContext(Dispatchers.IO) {
        authedPost("/v1/admin/help-messages/${messageId.encodeURL()}/delete", "{}")
    }

    private fun parseStaffUserCard(o: JSONObject?): StaffUserCard? {
        if (o == null) return null
        val id = o.optString("userId").ifBlank { o.optString("id") }
        if (id.isBlank()) return null
        val perms = buildMap {
            val p = o.optJSONObject("permissions")
            if (p != null) {
                p.keys().forEach { key -> put(key, p.optBoolean(key, false)) }
            }
        }
        return StaffUserCard(
            userId = id,
            nickname = o.optString("nickname", "Jemand"),
            email = o.optString("email").takeIf { it.isNotBlank() && it != "null" },
            role = o.optString("role", "user"),
            coins = o.optInt("coins", 0),
            banned = o.optBoolean("banned", false),
            petEmoji = o.optString("petEmoji", "🐣").ifBlank { "🐣" },
            permissions = perms,
            modSince = o.optLong("modSince").takeIf { it > 0L },
            marriage = parseMarriageInfo(o.optJSONObject("marriage")),
            marriageCooldownLabel = o.optString("marriageCooldownLabel")
                .takeIf { it.isNotBlank() && it != "null" && it != "0" }
        )
    }

    private fun parsePermGroups(arr: org.json.JSONArray?): List<StaffPermGroup> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val g = arr.optJSONObject(i) ?: continue
                val perms = buildList {
                    val pa = g.optJSONArray("permissions")
                    if (pa != null) {
                        for (j in 0 until pa.length()) {
                            val p = pa.optJSONObject(j) ?: continue
                            val id = p.optString("id")
                            val label = p.optString("label", id)
                            if (id.isNotBlank()) add(id to label)
                        }
                    }
                }
                add(
                    StaffPermGroup(
                        id = g.optString("id"),
                        icon = g.optString("icon", "•"),
                        label = g.optString("label"),
                        description = g.optString("description"),
                        permissions = perms
                    )
                )
            }
        }
    }

    suspend fun fetchStaffOverview(): StaffOverview = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/admin/overview")
        val me = json.optJSONObject("me")
        if (me != null) AccountSession.setAccount(AccountInfo.fromApi(me))
        StaffOverview(
            users = json.optInt("users", 0),
            rooms = json.optInt("rooms", 0),
            openPublicReports = json.optInt("openPublicReports", 0),
            openPeerReports = json.optInt("openPeerReports", 0),
            openHelpMessages = json.optInt("openHelpMessages", 0),
            moderators = json.optInt("moderators", 0),
            vouchers = json.optInt("vouchers", 0),
            permissionGroups = parsePermGroups(json.optJSONArray("permissionGroups"))
        )
    }

    suspend fun listModerators(): Pair<List<StaffUserCard>, List<StaffPermGroup>> =
        withContext(Dispatchers.IO) {
            val json = authedGet("/v1/admin/moderators")
            val mods = buildList {
                val arr = json.optJSONArray("moderators") ?: return@buildList
                for (i in 0 until arr.length()) {
                    parseStaffUserCard(arr.optJSONObject(i))?.let { add(it) }
                }
            }
            mods to parsePermGroups(json.optJSONArray("permissionGroups"))
        }

    suspend fun inviteModerator(query: String): StaffUserCard = withContext(Dispatchers.IO) {
        val body = JSONObject().put("query", query.trim()).toString()
        val json = authedPost("/v1/admin/moderators/invite", body)
        parseStaffUserCard(json.optJSONObject("moderator"))
            ?: throw LuvApiException("Einladen fehlgeschlagen")
    }

    suspend fun setModeratorPermissions(
        userId: String,
        permissions: Map<String, Boolean>
    ): StaffUserCard = withContext(Dispatchers.IO) {
        val po = JSONObject()
        permissions.forEach { (k, v) -> po.put(k, v) }
        val body = JSONObject().put("permissions", po).toString()
        val path = "/v1/admin/moderators/${userId.trim().encodeURL()}/permissions"
        // POST — PUT kann je nach Proxy scheitern
        val json = authedPost(path, body)
        parseStaffUserCard(json.optJSONObject("moderator"))
            ?: throw LuvApiException("Rechte speichern fehlgeschlagen")
    }

    suspend fun removeModerator(userId: String) = withContext(Dispatchers.IO) {
        authedPost("/v1/admin/moderators/${userId.trim().encodeURL()}/remove", "{}")
        Unit
    }

    suspend fun searchStaffUsers(query: String): List<StaffUserCard> =
        withContext(Dispatchers.IO) {
            val q = query.trim().encodeURL()
            val json = authedGet("/v1/admin/users/search?q=$q")
            buildList {
                val arr = json.optJSONArray("users") ?: return@buildList
                for (i in 0 until arr.length()) {
                    parseStaffUserCard(arr.optJSONObject(i))?.let { add(it) }
                }
            }
        }

    suspend fun adjustUserCoins(userId: String, delta: Int): StaffUserCard =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("delta", delta).toString()
            val json = authedPost("/v1/admin/users/${userId.encodeURL()}/coins", body)
            parseStaffUserCard(json.optJSONObject("user"))
                ?: throw LuvApiException("Coins anpassen fehlgeschlagen")
        }

    suspend fun setUserNickname(userId: String, nickname: String): StaffUserCard =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("nickname", nickname.trim().take(18)).toString()
            val json = authedPost("/v1/admin/users/${userId.encodeURL()}/nickname", body)
            parseStaffUserCard(json.optJSONObject("user"))
                ?: throw LuvApiException("Name ändern fehlgeschlagen")
        }

    suspend fun setUserBanned(userId: String, banned: Boolean, reason: String? = null): StaffUserCard =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("banned", banned)
                .put("reason", reason ?: "staff_ban")
                .toString()
            val json = authedPost("/v1/admin/users/${userId.encodeURL()}/ban", body)
            parseStaffUserCard(json.optJSONObject("user"))
                ?: throw LuvApiException("Sperren fehlgeschlagen")
        }

    suspend fun deleteStaffUser(userId: String) = withContext(Dispatchers.IO) {
        authedPost("/v1/admin/users/${userId.encodeURL()}/delete", "{}")
        Unit
    }

    suspend fun listUserLobbies(userId: String): List<StaffLobbyCard> =
        withContext(Dispatchers.IO) {
            val json = authedGet("/v1/admin/users/${userId.trim().encodeURL()}/lobbies")
            parseStaffLobbyList(json.optJSONArray("lobbies"))
        }

    suspend fun fetchStaffLobby(code: String): StaffLobbyCard = withContext(Dispatchers.IO) {
        val clean = normalizeCode(code) ?: code.trim().uppercase()
        val json = authedGet("/v1/admin/rooms/${clean.encodeURL()}")
        parseStaffLobbyCard(json.optJSONObject("lobby"))
            ?: throw LuvApiException("Lobby nicht gefunden")
    }

    suspend fun forceDeleteStaffLobby(code: String) = withContext(Dispatchers.IO) {
        val clean = normalizeCode(code) ?: code.trim().uppercase()
        authedPost("/v1/admin/rooms/${clean.encodeURL()}/force-delete", "{}")
        Unit
    }

    private fun parseStaffLobbyList(arr: org.json.JSONArray?): List<StaffLobbyCard> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                parseStaffLobbyCard(arr.optJSONObject(i))?.let { add(it) }
            }
        }
    }

    private fun parseStaffLobbyCard(o: JSONObject?): StaffLobbyCard? {
        if (o == null) return null
        val code = o.optString("code").trim().uppercase()
        if (code.isBlank()) return null
        val members = buildList {
            val arr = o.optJSONArray("members") ?: return@buildList
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                val id = m.optString("userId")
                if (id.isBlank()) continue
                add(
                    StaffLobbyMember(
                        userId = id,
                        nickname = m.optString("nickname", "Jemand"),
                        online = m.optBoolean("online", false),
                        colorIndex = m.optInt("colorIndex", -1),
                        petEmoji = m.optString("petEmoji", "🐣").ifBlank { "🐣" }
                    )
                )
            }
        }
        return StaffLobbyCard(
            code = code,
            name = o.optString("name", "Lobby"),
            capacity = o.optInt("capacity", 2),
            isFree = o.optBoolean("isFree", false),
            online = o.optInt("online", 0),
            memberCount = o.optInt("memberCount", members.size),
            members = members,
            active = o.optBoolean("active", false),
            live = o.optBoolean("live", false),
            createdAt = o.optLong("createdAt").takeIf { it > 0L },
            lastActiveAt = o.optLong("lastActiveAt").takeIf { it > 0L },
            invite = o.optString("invite", ""),
            hostUserId = o.optString("hostUserId").takeIf { it.isNotBlank() },
            hostNickname = o.optString("hostNickname", "Host")
        )
    }

    suspend fun listPeerReports(): List<PeerReportInfo> = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/admin/peer-reports")
        val arr = json.optJSONArray("reports") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    PeerReportInfo(
                        id = o.getString("id"),
                        targetNickname = o.optString("targetNickname", "Jemand"),
                        reporterNickname = o.optString("reporterNickname", "Jemand"),
                        lobbyName = o.optString("lobbyName", "Lobby"),
                        imageUrl = o.optString("imageUrl").takeIf { it.isNotBlank() },
                        reportedAt = o.optLong("reportedAt")
                    )
                )
            }
        }
    }

    suspend fun keepPeerReport(reportId: String) = withContext(Dispatchers.IO) {
        authedPost("/v1/admin/peer-reports/${reportId.encodeURL()}/keep", "{}")
        Unit
    }

    suspend fun deletePeerReport(reportId: String) = withContext(Dispatchers.IO) {
        authedPost("/v1/admin/peer-reports/${reportId.encodeURL()}/delete", "{}")
        Unit
    }

    suspend fun revokeVoucher(code: String) = withContext(Dispatchers.IO) {
        authedPost("/v1/admin/vouchers/${code.trim().encodeURL()}/revoke", "{}")
        Unit
    }

    suspend fun fetchLiveNotice(): LiveNotice? = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/live-notice")
        val o = json.optJSONObject("notice") ?: return@withContext null
        val id = o.optString("id")
        val message = o.optString("message")
        if (id.isBlank() || message.isBlank()) return@withContext null
        LiveNotice(
            id = id,
            message = message,
            authorNickname = o.optString("authorNickname").trim()
                .takeIf { it.isNotBlank() && !it.equals("Luv", ignoreCase = true) }
                ?: "Team",
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    }

    data class StaffWarning(
        val id: String,
        val message: String,
        val severity: String,
        val at: Long,
        val byNick: String,
        val seen: Boolean
    )

    data class StaffNoticesBag(
        val pending: StaffWarning?,
        val warnings: List<StaffWarning>
    )

    suspend fun fetchStaffNotices(): StaffNoticesBag = withContext(Dispatchers.IO) {
        val json = runCatching { authedGet("/v1/me/staff-notices") }.getOrNull()
            ?: return@withContext StaffNoticesBag(null, emptyList())
        fun parse(o: org.json.JSONObject?): StaffWarning? {
            if (o == null) return null
            val id = o.optString("id").trim()
            val message = o.optString("message").trim()
            if (id.isBlank() || message.isBlank()) return null
            return StaffWarning(
                id = id,
                message = message,
                severity = o.optString("severity", "warn"),
                at = o.optLong("at", System.currentTimeMillis()),
                byNick = o.optString("authorNickname").ifBlank { o.optString("byNick") }.ifBlank { "Team" },
                seen = o.optBoolean("seen", false)
            )
        }
        val pending = parse(json.optJSONObject("pending"))
        val arr = json.optJSONArray("warnings")
        val list = buildList {
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    parse(arr.optJSONObject(i))?.let { add(it) }
                }
            }
        }
        StaffNoticesBag(pending, list)
    }

    suspend fun ackStaffNotice(id: String) = withContext(Dispatchers.IO) {
        val clean = id.trim()
        if (clean.isBlank()) return@withContext
        runCatching {
            authedPost("/v1/me/staff-notices/${clean.encodeURL()}/ack", "{}")
        }
        Unit
    }

    suspend fun sendLiveNotice(message: String): LiveNotice = withContext(Dispatchers.IO) {
        val body = JSONObject().put("message", message.trim().take(160)).toString()
        val json = authedPost("/v1/admin/live-notice", body)
        val o = json.optJSONObject("notice") ?: throw LuvApiException("Senden fehlgeschlagen")
        LiveNotice(
            id = o.optString("id"),
            message = o.optString("message"),
            authorNickname = o.optString("authorNickname").trim()
                .takeIf { it.isNotBlank() && !it.equals("Luv", ignoreCase = true) }
                ?: AccountSession.account.value?.nickname?.trim()?.takeIf {
                    it.isNotBlank() && !it.equals("Luv", ignoreCase = true)
                }
                ?: "Team",
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    }

    data class InventoryBag(
        val emojis: Map<String, Int>,
        val themes: List<String>,
        val stickers: Map<String, Int>,
        val pets: List<String>,
        val equippedPet: String
    )

    /** Shop-/Inventar-ID: Emoji kurz, Bild-/Theme-Items bis 32 Zeichen — nie abschneiden. */
    private fun clipShopItemId(raw: String, maxEmoji: Int = 16): String =
        com.luv.couple.ui.clipItemId(raw, maxEmoji)

    suspend fun buyEmoji(emoji: String): Pair<AccountInfo, Int> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("emoji", clipShopItemId(emoji)).toString()
        val json = authedPost("/v1/shop/buy-emoji", body)
        val user = AccountInfo.fromApi(json.getJSONObject("user"))
        AccountSession.setAccount(user)
        user to json.optInt("owned", 1)
    }

    suspend fun buyTheme(themeId: String): AccountInfo = withContext(Dispatchers.IO) {
        val body = JSONObject().put("themeId", themeId.trim().take(32)).toString()
        val json = authedPost("/v1/shop/buy-theme", body)
        val user = AccountInfo.fromApi(json.getJSONObject("user"))
        AccountSession.setAccount(user)
        user
    }

    suspend fun buySticker(emoji: String): Pair<AccountInfo, Int> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("emoji", clipShopItemId(emoji)).toString()
        val json = authedPost("/v1/shop/buy-sticker", body)
        val user = AccountInfo.fromApi(json.getJSONObject("user"))
        AccountSession.setAccount(user)
        user to json.optInt("owned", 1)
    }

    suspend fun buyPet(emoji: String): AccountInfo = withContext(Dispatchers.IO) {
        val body = JSONObject().put("emoji", emoji.trim().take(32)).toString()
        val json = authedPost("/v1/shop/buy-pet", body)
        val user = AccountInfo.fromApi(json.getJSONObject("user"))
        AccountSession.setAccount(user)
        user
    }

    suspend fun equipPet(emoji: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().put("emoji", emoji.trim().take(32)).toString()
        val json = authedPost("/v1/me/equip-pet", body)
        json.optString("equippedPet", emoji.trim().take(32))
    }

    suspend fun fetchShopCatalog() = withContext(Dispatchers.IO) {
        runCatching {
            val json = authedGet("/v1/shop/catalog")
            val arr = json.optJSONArray("items") ?: return@runCatching
            val emojis = mutableListOf<com.luv.couple.shop.ShopEmoji>()
            val stickers = mutableListOf<com.luv.couple.shop.ShopEmoji>()
            val themes = mutableListOf<com.luv.couple.shop.ShopTheme>()
            val pets = mutableListOf<com.luv.couple.shop.ShopPet>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val kind = o.optString("kind")
                val itemId = o.optString("itemId").trim()
                if (itemId.isBlank()) continue
                val price = o.optInt("priceCoins", 0)
                val compare = o.optInt("compareAtPrice", 0).takeIf { it > price }
                val rem = o.optLong("remainingMs", -1L).takeIf { it >= 0L }
                val search = o.optString("searchText", "")
                val label = o.optString("label", itemId)
                fun imageUrlFor(): String? =
                    o.optString("imageUrl", "").trim().ifBlank {
                        if (o.optBoolean("hasImage", false) ||
                            itemId.startsWith("img_", ignoreCase = true)
                        ) {
                            "${baseUrl()}/v1/shop/pet-image/${itemId.encodeURL()}"
                        } else ""
                    }.ifBlank { null }
                when (kind) {
                    "emojis" -> emojis.add(
                        com.luv.couple.shop.ShopEmoji(
                            itemId, price, compare, rem, search, imageUrlFor(), label
                        )
                    )
                    "stickers" -> stickers.add(
                        com.luv.couple.shop.ShopEmoji(
                            itemId, price, compare, rem, search, imageUrlFor(), label
                        )
                    )
                    "themes" -> {
                        val vcObj = o.optJSONObject("visualConfig")
                        val visual = if (vcObj != null) {
                            val emArr = vcObj.optJSONArray("emojis")
                            val emojis = buildList {
                                if (emArr != null) {
                                    for (ei in 0 until emArr.length()) {
                                        emArr.optString(ei).trim().takeIf { it.isNotBlank() }?.let { add(it) }
                                    }
                                }
                            }.ifEmpty { listOf("✨") }
                            com.luv.couple.shop.ThemeVisualConfig(
                                skyTop = vcObj.optString("skyTop", "#7EB8D8"),
                                skyBottom = vcObj.optString("skyBottom", "#B8D4E8"),
                                groundTop = vcObj.optString("groundTop", "#2F5D2E"),
                                groundBottom = vcObj.optString("groundBottom", "#1E3D1E"),
                                emojis = emojis,
                                motion = vcObj.optString("motion", "fall"),
                                coverage = vcObj.optString("coverage", "full"),
                                speed = vcObj.optDouble("speed", 1.0).toFloat(),
                                density = vcObj.optDouble("density", 0.7).toFloat(),
                                size = vcObj.optDouble("size", 1.0).toFloat()
                            )
                        } else null
                        themes.add(
                            com.luv.couple.shop.ShopTheme(
                                id = itemId,
                                label = label,
                                emoji = o.optString("emoji", "🖼️").ifBlank { "🖼️" },
                                priceCoins = price,
                                compareAtPrice = compare,
                                remainingMs = rem,
                                searchText = search,
                                visualConfig = visual
                            )
                        )
                    }
                    "pets" -> pets.add(
                        com.luv.couple.shop.ShopPet(
                            emoji = itemId,
                            label = label,
                            priceCoins = price,
                            compareAtPrice = compare,
                            remainingMs = rem,
                            searchText = search,
                            imageUrl = o.optString("imageUrl", "").trim().ifBlank {
                                if (o.optBoolean("hasImage", false) ||
                                    itemId.startsWith("img_", ignoreCase = true)
                                ) {
                                    "${baseUrl()}/v1/shop/pet-image/${itemId.encodeURL()}"
                                } else ""
                            }.ifBlank { null }
                        )
                    )
                }
            }
            if (emojis.isNotEmpty()) {
                com.luv.couple.shop.LiveShopCatalog.remoteEmojis = emojis.sortedBy { it.priceCoins }
            }
            if (stickers.isNotEmpty()) {
                com.luv.couple.shop.LiveShopCatalog.remoteStickers = stickers.sortedBy { it.priceCoins }
            }
            if (themes.isNotEmpty()) {
                com.luv.couple.shop.LiveShopCatalog.remoteThemes = themes.sortedBy { it.priceCoins }
            }
            if (pets.isNotEmpty()) {
                com.luv.couple.shop.LiveShopCatalog.remotePets = pets.sortedBy { it.priceCoins }
            }
        }
    }

    suspend fun fetchInventory(): InventoryBag = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/me/inventory")
        val emojis = buildMap {
            val o = json.optJSONObject("emojis") ?: return@buildMap
            o.keys().forEach { key ->
                val n = o.optInt(key, 0)
                if (key.isNotBlank() && n > 0) put(key, n)
            }
        }
        val themes = buildList {
            val arr = json.optJSONArray("themes")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    arr.optString(i).trim().takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }.distinct()
        val stickers = buildMap {
            val o = json.optJSONObject("stickers") ?: return@buildMap
            o.keys().forEach { key ->
                val n = o.optInt(key, 0)
                if (key.isNotBlank() && n > 0) put(key, n)
            }
        }
        val pets = buildList {
            val arr = json.optJSONArray("pets")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    arr.optString(i).trim().takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }.distinct()
        val equipped = json.optString("equippedPet", com.luv.couple.shop.ShopCatalog.DEFAULT_PET)
            .trim().ifBlank { com.luv.couple.shop.ShopCatalog.DEFAULT_PET }
        InventoryBag(
            emojis = emojis,
            themes = themes,
            stickers = stickers,
            pets = pets,
            equippedPet = equipped
        )
    }

    suspend fun fetchDrawTemplates(): List<com.luv.couple.data.DrawTemplate> =
        withContext(Dispatchers.IO) {
            val json = authedGet("/v1/me/templates")
            val arr = json.optJSONArray("templates") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").trim()
                    if (id.isBlank()) continue
                    val parts = PairProtocol.parseTemplateParts(o.optJSONArray("strokes"))
                        ?: continue
                    add(
                        com.luv.couple.data.DrawTemplate(
                            id = id,
                            strokes = parts,
                            createdAt = o.optLong("createdAt", 0L),
                            coordSpace = o.optString("coordSpace").ifBlank {
                                com.luv.couple.lock.CanvasStore.templateCoordSpace(parts, null)
                            }
                        )
                    )
                }
            }
        }

    suspend fun saveDrawTemplate(
        strokes: List<com.luv.couple.data.TemplateStrokePart>
    ): com.luv.couple.data.DrawTemplate = withContext(Dispatchers.IO) {
        val space = com.luv.couple.lock.CanvasStore.templateCoordSpace(strokes, "canvas")
        val body = JSONObject()
            .put("strokes", PairProtocol.encodeTemplateParts(strokes))
            .put("coordSpace", space)
            .toString()
            .toRequestBody(jsonMedia)
        val request = authedRequestBuilder("/v1/me/templates").post(body).build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful) {
                throw LuvApiException(json?.optString("message") ?: "Vorlage speichern fehlgeschlagen")
            }
            val o = json?.optJSONObject("template")
                ?: throw LuvApiException("Vorlage speichern fehlgeschlagen")
            val parts = PairProtocol.parseTemplateParts(o.optJSONArray("strokes"))
                ?: throw LuvApiException("Vorlage ungültig")
            com.luv.couple.data.DrawTemplate(
                id = o.optString("id"),
                strokes = parts,
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                coordSpace = o.optString("coordSpace").ifBlank { space }
            )
        }
    }

    suspend fun updateDrawTemplate(
        id: String,
        strokes: List<com.luv.couple.data.TemplateStrokePart>
    ): com.luv.couple.data.DrawTemplate = withContext(Dispatchers.IO) {
        val clean = id.trim().encodeURL()
        val space = com.luv.couple.lock.CanvasStore.templateCoordSpace(strokes, "canvas")
        val body = JSONObject()
            .put("strokes", PairProtocol.encodeTemplateParts(strokes))
            .put("coordSpace", space)
            .toString()
            .toRequestBody(jsonMedia)
        val request = authedRequestBuilder("/v1/me/templates/$clean").put(body).build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful) {
                throw LuvApiException(json?.optString("message") ?: "Vorlage speichern fehlgeschlagen")
            }
            val o = json?.optJSONObject("template")
                ?: throw LuvApiException("Vorlage speichern fehlgeschlagen")
            val parts = PairProtocol.parseTemplateParts(o.optJSONArray("strokes"))
                ?: throw LuvApiException("Vorlage ungültig")
            com.luv.couple.data.DrawTemplate(
                id = o.optString("id").ifBlank { id },
                strokes = parts,
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                coordSpace = o.optString("coordSpace").ifBlank { space }
            )
        }
    }

    suspend fun deleteDrawTemplate(id: String) = withContext(Dispatchers.IO) {
        val clean = id.trim().encodeURL()
        val request = authedRequestBuilder("/v1/me/templates/$clean").delete().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val raw = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull()
                throw LuvApiException(json?.optString("message") ?: "Löschen fehlgeschlagen")
            }
        }
    }

    suspend fun fetchMyProfileCanvas(): Pair<String, com.luv.couple.profile.ProfileState> =
        withContext(Dispatchers.IO) {
            val json = authedGet("/v1/me/profile")
            val nick = json.optString("nickname", "Du")
            val raw = json.optJSONObject("profile")?.toString()
            nick to com.luv.couple.profile.ProfileCatalog.decode(raw, nick)
        }

    /**
     * Speichert Profil. Bei Erfolg optional Inventar (Sticker-Verbrauch).
     */
    suspend fun saveMyProfileCanvas(state: com.luv.couple.profile.ProfileState): Pair<Boolean, InventoryBag?> =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("profile", JSONObject(com.luv.couple.profile.ProfileCatalog.encode(state)))
                .toString()
            runCatching {
                val json = authedPut("/v1/me/profile", body)
                val invJson = json.optJSONObject("inventory")
                val inv = if (invJson != null) {
                    fun counts(o: org.json.JSONObject?): Map<String, Int> = buildMap {
                        if (o == null) return@buildMap
                        o.keys().forEach { key ->
                            val n = o.optInt(key, 0)
                            if (key.isNotBlank() && n > 0) put(key, n)
                        }
                    }
                    fun list(arr: org.json.JSONArray?): List<String> = buildList {
                        if (arr == null) return@buildList
                        for (i in 0 until arr.length()) {
                            arr.optString(i).trim().takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    }.distinct()
                    InventoryBag(
                        emojis = counts(invJson.optJSONObject("emojis")),
                        themes = list(invJson.optJSONArray("themes")),
                        stickers = counts(invJson.optJSONObject("stickers")),
                        pets = list(invJson.optJSONArray("pets")),
                        equippedPet = invJson.optString(
                            "equippedPet",
                            com.luv.couple.shop.ShopCatalog.DEFAULT_PET
                        ).trim().ifBlank { com.luv.couple.shop.ShopCatalog.DEFAULT_PET }
                    )
                } else null
                true to inv
            }.getOrElse { false to null }
        }

    data class MarriageInfo(
        val id: String = "",
        val status: String = "",
        val partnerId: String? = null,
        val partnerNickname: String? = null,
        val partnerPetEmoji: String = "🐣",
        val proposedBy: String? = null,
        val engageRemainingMs: Long = 0L,
        val weddingRemainingMs: Long = 0L,
        val weddingLobbyCode: String? = null,
        val marriedAt: Long = 0L,
        val hasWeddingImage: Boolean = false,
        val engageSkipCost: Int = 0,
        val weddingSkipCost: Int = 0,
        val engageRemainingLabel: String? = null,
        val weddingRemainingLabel: String? = null
    )

    data class PartnerPublic(
        val userId: String,
        val nickname: String,
        val petEmoji: String = "🐣",
        val status: String? = null
    )

    data class PeerProfile(
        val nickname: String,
        val state: com.luv.couple.profile.ProfileState,
        val coins: Int,
        val petEmoji: String = "🐣",
        val dayStreak: Int = 0,
        val friendStatus: String = "none",
        val canPetKraul: Boolean = true,
        val canTipGlass: Boolean = true,
        val glassTipsRemaining: Int = 10,
        val friendshipLevel: Int = 0,
        val canProposeMarriage: Boolean = false,
        val proposeUnlockCost: Int = 0,
        val marriageCooldownRemainingMs: Long = 0L,
        val marriageCooldownSkipCost: Int = 0,
        val marriageCooldownLabel: String? = null,
        val partnerMarriageCooldownLabel: String? = null,
        val canDivorce: Boolean = false,
        val marriage: MarriageInfo? = null,
        val spousePublic: PartnerPublic? = null,
        val fiancePublic: PartnerPublic? = null
    )

    suspend fun fetchUserProfileCanvas(userId: String): PeerProfile? =
        withContext(Dispatchers.IO) {
            val uid = userId.trim()
            if (uid.isBlank()) return@withContext null
            runCatching {
                val json = authedGet("/v1/users/${uid.encodeURL()}/profile")
                val nick = json.optString("nickname", "Jemand")
                val raw = json.optJSONObject("profile")?.toString()
                val pet = json.optString("petEmoji", "🐣").trim().ifBlank { "🐣" }
                var state = com.luv.couple.profile.ProfileCatalog.decode(raw, nick)
                if (state.companionEmoji.isBlank()) {
                    state = state.copy(companionEmoji = pet)
                }
                PeerProfile(
                    nickname = nick,
                    state = state,
                    coins = json.optInt("coins", 0),
                    petEmoji = pet,
                    dayStreak = json.optInt("dayStreak", 0).coerceAtLeast(0),
                    friendStatus = json.optString("friendStatus", "none"),
                    canPetKraul = json.optBoolean("canPetKraul", true),
                    canTipGlass = json.optBoolean("canTipGlass", true),
                    glassTipsRemaining = json.optInt("glassTipsRemaining", 10),
                    friendshipLevel = json.optInt("friendshipLevel", 0),
                    canProposeMarriage = json.optBoolean("canProposeMarriage", false),
                    proposeUnlockCost = json.optInt("proposeUnlockCost", 0),
                    marriageCooldownRemainingMs = json.optLong("marriageCooldownRemainingMs", 0L),
                    marriageCooldownSkipCost = json.optInt("marriageCooldownSkipCost", 0),
                    marriageCooldownLabel = json.optString("marriageCooldownLabel")
                        .takeIf { it.isNotBlank() && it != "null" },
                    partnerMarriageCooldownLabel = json.optString("partnerMarriageCooldownLabel")
                        .takeIf { it.isNotBlank() && it != "null" },
                    canDivorce = json.optBoolean("canDivorce", false),
                    marriage = parseMarriageInfo(json.optJSONObject("marriage")),
                    spousePublic = parsePartnerPublic(json.optJSONObject("spousePublic")),
                    fiancePublic = parsePartnerPublic(json.optJSONObject("fiancePublic"))
                )
            }.getOrNull()
        }

    data class GlassTipResult(
        val remaining: Int,
        val toCoins: Int,
        val from: AccountInfo?,
        val received: Int = 0
    )

    data class FriendCard(
        val userId: String,
        val nickname: String,
        val petEmoji: String,
        val friendshipLevel: Int = 0,
        val isSpouse: Boolean = false,
        val isFiance: Boolean = false,
        val marriage: MarriageInfo? = null
    )

    data class LobbyInvite(
        val id: String,
        val roomCode: String,
        val lobbyName: String,
        val fromUserId: String,
        val fromNickname: String,
        val fromPetEmoji: String
    )

    data class FriendsBag(
        val friends: List<FriendCard>,
        val incoming: List<FriendCard>,
        val outgoing: List<FriendCard>,
        val marriageProposals: List<FriendCard> = emptyList(),
        val lobbyInvites: List<LobbyInvite> = emptyList(),
        val myMarriage: MarriageInfo? = null,
        val marriageCooldownRemainingMs: Long = 0L,
        val marriageCooldownSkipCost: Int = 0,
        val marriageCooldownLabel: String? = null,
        val pendingFriendshipCoins: Int = 0
    )

    data class PetKraulResult(
        val petEmoji: String,
        val toCoins: Int,
        val amount: Int,
        val friendshipLevel: Int = 0,
        val friendshipLevelBumped: Boolean = false
    )

    data class GuestbookEntry(
        val id: String,
        val userId: String,
        val nickname: String,
        val text: String,
        val createdAt: Long
    )

    data class WeddingCoupleSide(
        val userId: String,
        val nickname: String,
        val petEmoji: String = "🐣"
    )

    data class WeddingInfo(
        val marriage: MarriageInfo?,
        val hasImage: Boolean,
        val guestbook: List<GuestbookEntry>,
        val canDeleteComments: Boolean,
        val coupleA: WeddingCoupleSide? = null,
        val coupleB: WeddingCoupleSide? = null
    )

    private fun parseMarriageInfo(o: JSONObject?): MarriageInfo? {
        if (o == null) return null
        val status = o.optString("status").trim()
        if (status.isBlank()) return null
        return MarriageInfo(
            id = o.optString("id", ""),
            status = status,
            partnerId = o.optString("partnerId").takeIf { it.isNotBlank() },
            partnerNickname = o.optString("partnerNickname").takeIf { it.isNotBlank() },
            partnerPetEmoji = o.optString("partnerPetEmoji", "🐣").ifBlank { "🐣" },
            proposedBy = o.optString("proposedBy").takeIf { it.isNotBlank() },
            engageRemainingMs = o.optLong("engageRemainingMs", 0L),
            weddingRemainingMs = o.optLong("weddingRemainingMs", 0L),
            weddingLobbyCode = o.optString("weddingLobbyCode").takeIf { it.isNotBlank() },
            marriedAt = o.optLong("marriedAt", 0L),
            hasWeddingImage = o.optBoolean("hasWeddingImage", false),
            engageSkipCost = o.optInt("engageSkipCost", 0),
            weddingSkipCost = o.optInt("weddingSkipCost", 0),
            engageRemainingLabel = o.optString("engageRemainingLabel").takeIf { it.isNotBlank() },
            weddingRemainingLabel = o.optString("weddingRemainingLabel").takeIf { it.isNotBlank() }
        )
    }

    private fun parsePartnerPublic(o: JSONObject?): PartnerPublic? {
        if (o == null) return null
        val id = o.optString("userId").trim()
        if (id.isBlank()) return null
        return PartnerPublic(
            userId = id,
            nickname = o.optString("nickname", "Jemand").trim().ifBlank { "Jemand" },
            petEmoji = o.optString("petEmoji", "🐣").ifBlank { "🐣" },
            status = o.optString("status").takeIf { it.isNotBlank() }
        )
    }

    private fun parseFriendCard(o: JSONObject?): FriendCard? {
        if (o == null) return null
        val id = o.optString("userId").trim()
        if (id.isBlank()) return null
        return FriendCard(
            userId = id,
            nickname = o.optString("nickname", "Jemand").trim().ifBlank { "Jemand" },
            petEmoji = o.optString("petEmoji", "🐣").trim().ifBlank { "🐣" },
            friendshipLevel = o.optInt("friendshipLevel", 0),
            isSpouse = o.optBoolean("isSpouse", false),
            isFiance = o.optBoolean("isFiance", false),
            marriage = parseMarriageInfo(o.optJSONObject("marriage"))
        )
    }

    private fun parseFriendCards(arr: org.json.JSONArray?): List<FriendCard> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                parseFriendCard(arr.optJSONObject(i))?.let { add(it) }
            }
        }
    }

    suspend fun fetchFriends(): FriendsBag = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/me/friends")
        FriendsBag(
            friends = parseFriendCards(json.optJSONArray("friends")),
            incoming = parseFriendCards(json.optJSONArray("incoming")),
            outgoing = parseFriendCards(json.optJSONArray("outgoing")),
            marriageProposals = parseFriendCards(json.optJSONArray("marriageProposals")),
            lobbyInvites = parseLobbyInvites(json.optJSONArray("lobbyInvites")),
            myMarriage = parseMarriageInfo(json.optJSONObject("myMarriage")),
            marriageCooldownRemainingMs = json.optLong("marriageCooldownRemainingMs", 0L),
            marriageCooldownSkipCost = json.optInt("marriageCooldownSkipCost", 0),
            marriageCooldownLabel = json.optString("marriageCooldownLabel")
                .takeIf { it.isNotBlank() && it != "null" && it != "0" }
                ?.takeIf { json.optLong("marriageCooldownRemainingMs", 0L) > 0L },
            pendingFriendshipCoins = json.optInt("pendingFriendshipCoins", 0)
        )
    }

    private fun parseLobbyInvites(arr: org.json.JSONArray?): List<LobbyInvite> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").trim()
                val code = o.optString("roomCode").trim().uppercase().removePrefix("LUV-")
                if (id.isBlank() || code.isBlank()) continue
                add(
                    LobbyInvite(
                        id = id,
                        roomCode = code,
                        lobbyName = o.optString("lobbyName").ifBlank { "Lobby" },
                        fromUserId = o.optString("fromUserId"),
                        fromNickname = o.optString("fromNickname").ifBlank { "Jemand" },
                        fromPetEmoji = o.optString("fromPetEmoji").ifBlank { "🐣" }
                    )
                )
            }
        }
    }

    suspend fun inviteFriendToLobby(friendUserId: String, roomCode: String) =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("friendUserId", friendUserId.trim())
                .put("roomCode", roomCode.trim())
                .toString()
                .toRequestBody(jsonMedia)
            val request = authedRequestBuilder("/v1/me/lobby-invites").post(body).build()
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull()
                if (!response.isSuccessful) {
                    throw LuvApiException(
                        json?.optString("message")?.takeIf { it.isNotBlank() }
                            ?: "Einladen fehlgeschlagen"
                    )
                }
            }
        }

    suspend fun acceptLobbyInvite(inviteId: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().put("inviteId", inviteId.trim()).toString().toRequestBody(jsonMedia)
        val request = authedRequestBuilder("/v1/me/lobby-invites/accept").post(body).build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful) {
                throw LuvApiException(
                    json?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: "Annehmen fehlgeschlagen"
                )
            }
            json?.optString("roomCode")?.trim()?.uppercase()?.removePrefix("LUV-")
                ?.takeIf { it.isNotBlank() }
                ?: throw LuvApiException("Kein Lobby-Code")
        }
    }

    suspend fun declineLobbyInvite(inviteId: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("inviteId", inviteId.trim()).toString().toRequestBody(jsonMedia)
        val request = authedRequestBuilder("/v1/me/lobby-invites/decline").post(body).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val raw = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull()
                throw LuvApiException(
                    json?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: "Ablehnen fehlgeschlagen"
                )
            }
        }
    }

    suspend fun claimFriendshipLevelCoins(): Int = withContext(Dispatchers.IO) {
        val json = authedPost("/v1/me/friends/claim-level-coins", "{}")
        json.optJSONObject("user")?.let { AccountSession.setAccount(AccountInfo.fromApi(it)) }
        json.optInt("claimed", 0)
    }

    suspend fun proposeMarriage(
        userId: String,
        unlockWithCoins: Boolean = true
    ): MarriageInfo? = withContext(Dispatchers.IO) {
        val body = JSONObject().put("unlockWithCoins", unlockWithCoins).toString()
        val json = authedPost("/v1/users/${userId.trim().encodeURL()}/marry/propose", body)
        json.optJSONObject("user")?.let { AccountSession.setAccount(AccountInfo.fromApi(it)) }
        parseMarriageInfo(json.optJSONObject("marriage"))
    }

    suspend fun skipMarriageCooldown(): Int = withContext(Dispatchers.IO) {
        val json = authedPost("/v1/me/marriage/skip-cooldown", "{}")
        json.optJSONObject("user")?.let { AccountSession.setAccount(AccountInfo.fromApi(it)) }
        json.optInt("cost", 0)
    }

    suspend fun acceptMarriage(userId: String): MarriageInfo? = withContext(Dispatchers.IO) {
        val body = JSONObject().put("userId", userId.trim()).toString()
        val json = authedPost("/v1/me/marriage/accept", body)
        parseMarriageInfo(json.optJSONObject("marriage"))
    }

    suspend fun declineMarriage(userId: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("userId", userId.trim()).toString()
        authedPost("/v1/me/marriage/decline", body)
        Unit
    }

    suspend fun divorceMarriage() = withContext(Dispatchers.IO) {
        val body = JSONObject().put("confirm", "scheiden").toString()
        authedPost("/v1/me/marriage/divorce", body)
        Unit
    }

    data class MarriageSkipResult(
        val cost: Int,
        val marriage: MarriageInfo?,
        val coins: Int
    )

    suspend fun skipMarriageWait(): MarriageSkipResult = withContext(Dispatchers.IO) {
        val json = authedPost("/v1/me/marriage/skip-wait", "{}")
        val user = json.optJSONObject("user")
        if (user != null) {
            AccountSession.setAccount(AccountInfo.fromApi(user))
        }
        MarriageSkipResult(
            cost = json.optInt("cost", 0),
            marriage = parseMarriageInfo(json.optJSONObject("marriage")),
            coins = user?.optInt("coins") ?: (AccountSession.account.value?.coins ?: 0)
        )
    }

    suspend fun staffAdvanceMarriage(
        userId: String,
        action: String = "next",
        days: Int? = null,
        remainingMs: Long? = null
    ): StaffUserCard = withContext(Dispatchers.IO) {
        val body = JSONObject().put("action", action)
        if (days != null) body.put("days", days)
        if (remainingMs != null) body.put("remainingMs", remainingMs)
        val json = authedPost(
            "/v1/admin/users/${userId.trim().encodeURL()}/marriage/advance",
            body.toString()
        )
        parseStaffUserCard(json.optJSONObject("user"))
            ?: throw LuvApiException("Ungültige Server-Antwort")
    }

    fun weddingImageUrl(userId: String): String =
        "${baseUrl()}/v1/users/${userId.trim().encodeURL()}/wedding/image"

    suspend fun fetchWedding(userId: String): WeddingInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val json = authedGet("/v1/users/${userId.trim().encodeURL()}/wedding")
            val gb = json.optJSONArray("guestbook")
            val entries = buildList {
                if (gb != null) {
                    for (i in 0 until gb.length()) {
                        val e = gb.optJSONObject(i) ?: continue
                        val id = e.optString("id").trim()
                        if (id.isBlank()) continue
                        add(
                            GuestbookEntry(
                                id = id,
                                userId = e.optString("userId"),
                                nickname = e.optString("nickname", "Jemand"),
                                text = e.optString("text"),
                                createdAt = e.optLong("createdAt", 0L)
                            )
                        )
                    }
                }
            }
            val couple = json.optJSONObject("couple")
            fun side(key: String): WeddingCoupleSide? {
                val o = couple?.optJSONObject(key) ?: return null
                val id = o.optString("userId").trim()
                if (id.isBlank()) return null
                return WeddingCoupleSide(
                    userId = id,
                    nickname = o.optString("nickname", "Jemand").trim().ifBlank { "Jemand" },
                    petEmoji = o.optString("petEmoji", "🐣").ifBlank { "🐣" }
                )
            }
            WeddingInfo(
                marriage = parseMarriageInfo(json.optJSONObject("marriage")),
                hasImage = json.optBoolean("hasImage", false),
                guestbook = entries,
                canDeleteComments = json.optBoolean("canDeleteComments", false),
                coupleA = side("a"),
                coupleB = side("b")
            )
        }.getOrNull()
    }

    suspend fun downloadWeddingImage(userId: String): ByteArray = withContext(Dispatchers.IO) {
        val clean = userId.trim()
        if (clean.isBlank()) throw LuvApiException("Kein Bild.")
        val request = authedRequestBuilder("/v1/users/${clean.encodeURL()}/wedding/image")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw LuvApiException("Hochzeitsbild nicht verfügbar (${response.code}).")
            }
            response.body?.bytes() ?: throw LuvApiException("Bild leer.")
        }
    }

    suspend fun postGuestbook(userId: String, text: String): GuestbookEntry? =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("text", text.trim().take(280)).toString()
            val json = authedPost(
                "/v1/users/${userId.trim().encodeURL()}/wedding/guestbook",
                body
            )
            val e = json.optJSONObject("entry") ?: return@withContext null
            GuestbookEntry(
                id = e.optString("id"),
                userId = e.optString("userId"),
                nickname = e.optString("nickname", "Jemand"),
                text = e.optString("text"),
                createdAt = e.optLong("createdAt", 0L)
            )
        }

    suspend fun deleteGuestbook(userId: String, entryId: String) = withContext(Dispatchers.IO) {
        authedDelete(
            "/v1/users/${userId.trim().encodeURL()}/wedding/guestbook/${entryId.trim().encodeURL()}"
        )
        Unit
    }

    suspend fun reportGuestbook(userId: String, entryId: String, reason: String) =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("reason", reason.trim().take(400)).toString()
            authedPost(
                "/v1/users/${userId.trim().encodeURL()}/wedding/guestbook/${entryId.trim().encodeURL()}/report",
                body
            )
            Unit
        }

    suspend fun sendFriendRequest(userId: String): String = withContext(Dispatchers.IO) {
        val uid = userId.trim()
        val json = authedPost("/v1/users/${uid.encodeURL()}/friend-request", "{}")
        json.optString("friendStatus", "outgoing")
    }

    suspend fun acceptFriend(userId: String): FriendCard? = withContext(Dispatchers.IO) {
        val body = JSONObject().put("userId", userId.trim()).toString()
        val json = authedPost("/v1/me/friends/accept", body)
        parseFriendCard(json.optJSONObject("friend"))
    }

    suspend fun declineFriend(userId: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("userId", userId.trim()).toString()
        authedPost("/v1/me/friends/decline", body)
        Unit
    }

    suspend fun removeFriend(userId: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("userId", userId.trim()).toString()
        authedPost("/v1/me/friends/remove", body)
        Unit
    }

    suspend fun reorderFriends(userIds: List<String>): List<FriendCard> =
        withContext(Dispatchers.IO) {
            val arr = org.json.JSONArray()
            userIds.forEach { arr.put(it) }
            val body = JSONObject().put("userIds", arr).toString()
            val json = authedPut("/v1/me/friends/order", body)
            parseFriendCards(json.optJSONArray("friends"))
        }

    data class AchievementDailyTask(
        val id: String,
        val title: String,
        val target: Int,
        val progress: Int,
        val done: Boolean
    )

    data class AchievementDaily(
        val date: String,
        val completed: Boolean,
        val rewardClaimed: Boolean = false,
        val claimable: Boolean = false,
        val tasks: List<AchievementDailyTask>
    )

    data class AchievementRewardItem(
        val kind: String,
        val itemId: String,
        val emoji: String,
        val label: String
    )

    data class AchievementItem(
        val id: String,
        val title: String,
        val desc: String,
        val category: String,
        val target: Int,
        val progress: Int,
        val coins: Int,
        val rewardItem: AchievementRewardItem? = null,
        val unlocked: Boolean,
        val unlockedAt: Long?,
        val claimed: Boolean = false,
        val claimable: Boolean = false
    )

    data class AchievementClaimResult(
        val coinsGranted: Int,
        val itemGranted: AchievementRewardItem?,
        val state: AchievementsState
    )

    data class AchievementsState(
        val streak: Int,
        val coinsEarnedToday: Int,
        val coinsCapToday: Int,
        val coinsRemainingToday: Int,
        val hasClaimable: Boolean = false,
        val claimableCount: Int = 0,
        val daily: AchievementDaily,
        val achievements: List<AchievementItem>,
        val unlockedCount: Int,
        val totalCount: Int
    )

    data class AchievementPingResult(
        val coinsGranted: Int,
        val dailyJustCompleted: Boolean,
        val streak: Int,
        val unlocked: List<AchievementItem>,
        val state: AchievementsState,
        val user: AccountInfo?
    )

    data class MarketCategory(
        val id: String,
        val label: String,
        val emoji: String
    )

    data class MarketPriceRange(
        val min: Int,
        val max: Int,
        val count: Int,
        val trend: String = "="
    )

    data class MarketPriceInsight(
        val shopPrice: Int? = null,
        val windowDays: Int = 90,
        val sales: MarketPriceRange? = null,
        val listings: MarketPriceRange? = null,
        val source: String = "none",
        val trend: String = "="
    ) {
        val hasAny: Boolean
            get() = shopPrice != null || sales != null || listings != null
    }

    data class MarketItem(
        val listingId: String,
        val kind: String,
        val itemId: String,
        val label: String,
        val emoji: String,
        val category: String,
        val priceCoins: Int,
        val allowTrade: Boolean,
        val trend: String,
        val stock: Int,
        val offerCount: Int,
        val isMine: Boolean,
        val ownedByViewer: Boolean,
        val sellerNickname: String,
        val priceInsight: MarketPriceInsight? = null
    )

    data class MarketListing(
        val id: String,
        val kind: String,
        val itemId: String,
        val label: String,
        val emoji: String,
        val category: String,
        val priceCoins: Int,
        val allowTrade: Boolean,
        val tradeWantKind: String?,
        val tradeWantItemId: String?,
        val tradeWantLabel: String?,
        val isPrivate: Boolean,
        val targetUserId: String?,
        val sellerId: String,
        val sellerNickname: String,
        val createdAt: Long,
        val stock: Int,
        val offerCount: Int,
        val trend: String,
        val isMine: Boolean,
        val ownedByViewer: Boolean
    )

    data class MarketBrowseResult(
        val categories: List<MarketCategory>,
        val items: List<MarketItem>,
        val count: Int,
        val mode: String
    )

    data class MarketOffersResult(
        val kind: String,
        val itemId: String,
        val label: String,
        val emoji: String,
        val category: String,
        val offers: List<MarketListing>,
        val count: Int,
        val priceInsight: MarketPriceInsight? = null
    )

    private fun parseAchievementsState(json: JSONObject): AchievementsState {
        val dailyJson = json.optJSONObject("daily") ?: JSONObject()
        val tasks = buildList {
            val arr = dailyJson.optJSONArray("tasks")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val t = arr.optJSONObject(i) ?: continue
                    add(
                        AchievementDailyTask(
                            id = t.optString("id"),
                            title = t.optString("title"),
                            target = t.optInt("target", 1),
                            progress = t.optInt("progress", 0),
                            done = t.optBoolean("done", false)
                        )
                    )
                }
            }
        }
        val achievements = buildList {
            val arr = json.optJSONArray("achievements")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val a = arr.optJSONObject(i) ?: continue
                    add(parseAchievementItem(a))
                }
            }
        }
        return AchievementsState(
            streak = json.optInt("streak", 0),
            coinsEarnedToday = json.optInt("coinsEarnedToday", 0),
            coinsCapToday = json.optInt("coinsCapToday", 25),
            coinsRemainingToday = json.optInt("coinsRemainingToday", 0),
            hasClaimable = json.optBoolean("hasClaimable", false),
            claimableCount = json.optInt("claimableCount", 0),
            daily = AchievementDaily(
                date = dailyJson.optString("date"),
                completed = dailyJson.optBoolean("completed", false),
                rewardClaimed = dailyJson.optBoolean("rewardClaimed", false),
                claimable = dailyJson.optBoolean("claimable", false),
                tasks = tasks
            ),
            achievements = achievements,
            unlockedCount = json.optInt("unlockedCount", 0),
            totalCount = json.optInt("totalCount", achievements.size)
        )
    }

    private fun parseAchievementRewardItem(o: JSONObject?): AchievementRewardItem? {
        if (o == null) return null
        val kind = o.optString("kind").trim()
        val itemId = o.optString("itemId").trim()
        if (kind.isBlank() || itemId.isBlank()) return null
        return AchievementRewardItem(
            kind = kind,
            itemId = itemId,
            emoji = o.optString("emoji").ifBlank { itemId },
            label = o.optString("label").ifBlank { itemId }
        )
    }

    private fun parseAchievementItem(o: JSONObject): AchievementItem =
        AchievementItem(
            id = o.optString("id"),
            title = o.optString("title"),
            desc = o.optString("desc"),
            category = o.optString("category"),
            target = o.optInt("target", 1),
            progress = o.optInt("progress", 0),
            coins = o.optInt("coins", 0),
            rewardItem = parseAchievementRewardItem(o.optJSONObject("rewardItem")),
            unlocked = o.optBoolean("unlocked", false),
            unlockedAt = o.optLong("unlockedAt").takeIf { it > 0L },
            claimed = o.optBoolean("claimed", false),
            claimable = o.optBoolean("claimable", false)
        )

    private fun parseMarketListing(o: JSONObject): MarketListing? {
        val id = o.optString("id").ifBlank { o.optString("listingId") }
        if (id.isBlank()) return null
        return MarketListing(
            id = id,
            kind = o.optString("kind"),
            itemId = o.optString("itemId"),
            label = o.optString("label"),
            emoji = o.optString("emoji"),
            category = o.optString("category"),
            priceCoins = o.optInt("priceCoins", 0),
            allowTrade = o.optBoolean("allowTrade", false),
            tradeWantKind = o.optString("tradeWantKind").takeIf { it.isNotBlank() },
            tradeWantItemId = o.optString("tradeWantItemId").takeIf { it.isNotBlank() },
            tradeWantLabel = o.optString("tradeWantLabel").takeIf { it.isNotBlank() },
            isPrivate = o.optBoolean("private", false),
            targetUserId = o.optString("targetUserId").takeIf { it.isNotBlank() },
            sellerId = o.optString("sellerId"),
            sellerNickname = o.optString("sellerNickname", "Jemand"),
            createdAt = o.optLong("createdAt", 0L),
            stock = o.optInt("stock", o.optInt("offerCount", 1)),
            offerCount = o.optInt("offerCount", o.optInt("stock", 1)),
            trend = o.optString("trend", "="),
            isMine = o.optBoolean("isMine", false),
            ownedByViewer = o.optBoolean("ownedByViewer", false)
        )
    }

    private fun parseMarketPriceRange(o: JSONObject?): MarketPriceRange? {
        if (o == null) return null
        val min = o.optInt("min", 0)
        val max = o.optInt("max", 0)
        if (min < 1 && max < 1) return null
        return MarketPriceRange(
            min = min.coerceAtLeast(1),
            max = max.coerceAtLeast(min.coerceAtLeast(1)),
            count = o.optInt("count", 0),
            trend = o.optString("trend", "=").ifBlank { "=" }
        )
    }

    fun parseMarketPriceInsight(o: JSONObject?): MarketPriceInsight? {
        if (o == null) return null
        val shopRaw = o.optInt("shopPrice", -1)
        val shopPrice = if (o.has("shopPrice") && !o.isNull("shopPrice") && shopRaw > 0) shopRaw else null
        return MarketPriceInsight(
            shopPrice = shopPrice,
            windowDays = o.optInt("windowDays", 90).coerceIn(1, 365),
            sales = parseMarketPriceRange(o.optJSONObject("sales")),
            listings = parseMarketPriceRange(o.optJSONObject("listings")),
            source = o.optString("source", "none").ifBlank { "none" },
            trend = o.optString("trend", "=").ifBlank { "=" }
        )
    }

    private fun parseMarketItem(o: JSONObject): MarketItem? {
        val kind = o.optString("kind")
        val itemId = o.optString("itemId")
        if (kind.isBlank() || itemId.isBlank()) return null
        val listingId = o.optString("listingId").ifBlank { o.optString("id") }
            .ifBlank { "$kind|$itemId" }
        val insight = parseMarketPriceInsight(o.optJSONObject("priceInsight"))
        return MarketItem(
            listingId = listingId,
            kind = kind,
            itemId = itemId,
            label = o.optString("label").ifBlank { itemId },
            emoji = o.optString("emoji").ifBlank { "?" },
            category = o.optString("category").ifBlank { kind },
            priceCoins = o.optInt("priceCoins", 0),
            allowTrade = o.optBoolean("allowTrade", false),
            trend = insight?.trend?.takeIf { it.isNotBlank() } ?: o.optString("trend", "="),
            stock = o.optInt("stock", o.optInt("offerCount", 1)),
            offerCount = o.optInt("offerCount", o.optInt("stock", 1)),
            isMine = o.optBoolean("isMine", false),
            ownedByViewer = o.optBoolean("ownedByViewer", false),
            sellerNickname = o.optString("sellerNickname", "Jemand"),
            priceInsight = insight
        )
    }

    suspend fun fetchAchievements(): AchievementsState = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/me/achievements")
        parseAchievementsState(json).also { AchievementsBadge.updateFrom(it) }
    }

    suspend fun claimAchievementReward(achievementId: String): AchievementClaimResult =
        withContext(Dispatchers.IO) {
            val json = authedPost(
                "/v1/me/achievements/${achievementId.trim().encodeURL()}/claim",
                "{}"
            )
            val coins = json.optInt("coinsGranted", 0)
            val item = parseAchievementRewardItem(json.optJSONObject("itemGranted"))
            val state = json.optJSONObject("state")?.let { parseAchievementsState(it) }
                ?: fetchAchievements()
            AchievementsBadge.updateFrom(state)
            json.optJSONObject("user")?.let { AccountSession.setAccount(AccountInfo.fromApi(it)) }
            AchievementClaimResult(coins, item, state)
        }

    suspend fun claimDailyAchievementReward(): Pair<Int, AchievementsState> =
        withContext(Dispatchers.IO) {
            val json = authedPost("/v1/me/achievements/daily/claim", "{}")
            val coins = json.optInt("coinsGranted", 0)
            val state = json.optJSONObject("state")?.let { parseAchievementsState(it) }
                ?: fetchAchievements()
            AchievementsBadge.updateFrom(state)
            json.optJSONObject("user")?.let { AccountSession.setAccount(AccountInfo.fromApi(it)) }
            coins to state
        }

    suspend fun pingAchievement(metric: String, amount: Int = 1): AchievementPingResult =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("metric", metric.trim().take(40))
                .put("amount", 1) // Server akzeptiert nur 1 — kein Client-Multiplikator
                .toString()
            val json = authedPost("/v1/me/achievements/ping", body)
            json.optJSONObject("user")?.let { AccountSession.setAccount(AccountInfo.fromApi(it)) }
            val unlocked = buildList {
                val arr = json.optJSONArray("unlocked")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val u = arr.optJSONObject(i) ?: continue
                        add(parseAchievementItem(u))
                    }
                }
            }
            val state = json.optJSONObject("state")?.let { parseAchievementsState(it) }
                ?: parseAchievementsState(json)
            AchievementsBadge.updateFrom(state)
            AchievementPingResult(
                coinsGranted = json.optInt("coinsGranted", 0),
                dailyJustCompleted = json.optBoolean("dailyJustCompleted", false),
                streak = json.optInt("streak", 0),
                unlocked = unlocked,
                state = state,
                user = json.optJSONObject("user")?.let { AccountInfo.fromApi(it) }
            )
        }

    data class MarketHubPreview(
        val emoji: String,
        val label: String,
        val detail: String,
        /** Coinshop: Pack-ID für Bildvorschau */
        val packId: String? = null,
        val packCoins: Int = 0
    )

    data class MarketHubData(
        val marketNewest: List<MarketHubPreview>,
        val shopTop: List<MarketHubPreview>,
        val coinNewest: List<MarketHubPreview>
    )

    suspend fun fetchMarketHub(): MarketHubData = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/market/hub")
        fun parsePreviews(key: String, mapper: (org.json.JSONObject) -> MarketHubPreview?): List<MarketHubPreview> {
            val arr = json.optJSONArray(key) ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    mapper(arr.optJSONObject(i) ?: continue)?.let { add(it) }
                }
            }
        }
        val marketNewest = parsePreviews("marketNewest") { o ->
            val emoji = o.optString("emoji").ifBlank { "📦" }
            val label = o.optString("label").ifBlank { emoji }
            val price = o.optInt("priceCoins", 0)
            val trade = o.optBoolean("allowTrade", false)
            val detail = when {
                trade && price <= 0 -> "Tausch"
                trade -> "$price 🪙 · Tausch"
                price > 0 -> "$price 🪙"
                else -> "Angebot"
            }
            MarketHubPreview(emoji = emoji, label = label, detail = detail)
        }
        val shopTop = parsePreviews("shopTop") { o ->
            val emoji = o.optString("emoji").ifBlank { "✨" }
            val label = o.optString("label").ifBlank { emoji }
            val price = o.optInt("priceCoins", 0)
            MarketHubPreview(
                emoji = emoji,
                label = label,
                detail = if (price > 0) "$price 🪙" else "Gratis"
            )
        }
        val coinNewest = parsePreviews("coinNewest") { o ->
            val coins = o.optInt("coins", 0)
            val eur = o.optString("amountEur").ifBlank { "—" }
            val label = o.optString("label").ifBlank { "$coins Coins" }
            MarketHubPreview(
                emoji = "🪙",
                label = label,
                detail = "$eur € · $coins",
                packId = o.optString("id").takeIf { it.isNotBlank() },
                packCoins = coins
            )
        }
        MarketHubData(marketNewest = marketNewest, shopTop = shopTop, coinNewest = coinNewest)
    }

    suspend fun fetchMarket(
        mode: String = "market",
        category: String = "all",
        query: String = ""
    ): MarketBrowseResult = withContext(Dispatchers.IO) {
        val m = if (mode == "private") "private" else "market"
        val cat = category.trim().ifBlank { "all" }.encodeURL()
        val q = query.trim().take(40).encodeURL()
        val path = buildString {
            append("/v1/market?mode=$m&category=$cat")
            if (q.isNotBlank()) append("&q=$q")
        }
        val json = authedGet(path)
        val categories = buildList {
            val arr = json.optJSONArray("categories")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val c = arr.optJSONObject(i) ?: continue
                    add(
                        MarketCategory(
                            id = c.optString("id"),
                            label = c.optString("label"),
                            emoji = c.optString("emoji", "📦")
                        )
                    )
                }
            }
        }
        val items = buildList {
            val arr = json.optJSONArray("items")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    parseMarketItem(arr.optJSONObject(i) ?: continue)?.let { add(it) }
                }
            }
        }
        MarketBrowseResult(
            categories = categories.ifEmpty {
                listOf(
                    MarketCategory("all", "Alle", "📦"),
                    MarketCategory("stickers", "Sticker", "🎀"),
                    MarketCategory("themes", "Hintergründe", "🖼️"),
                    MarketCategory("pets", "Begleiter", "🐣"),
                    MarketCategory("emojis", "Emojis", "😊")
                )
            },
            items = items,
            count = json.optInt("count", items.size),
            mode = json.optString("mode", m)
        )
    }

    suspend fun fetchMarketOffers(
        kind: String,
        itemId: String,
        mode: String = "market"
    ): MarketOffersResult = withContext(Dispatchers.IO) {
        val m = if (mode == "private") "private" else "market"
        val path =
            "/v1/market/offers?mode=$m&kind=${kind.trim().encodeURL()}&itemId=${itemId.trim().encodeURL()}"
        val json = authedGet(path)
        val offers = buildList {
            val arr = json.optJSONArray("offers")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    parseMarketListing(arr.optJSONObject(i) ?: continue)?.let { add(it) }
                }
            }
        }
        MarketOffersResult(
            kind = json.optString("kind", kind),
            itemId = json.optString("itemId", itemId),
            label = json.optString("label").ifBlank { itemId },
            emoji = json.optString("emoji").ifBlank { "?" },
            category = json.optString("category", kind),
            offers = offers,
            count = json.optInt("count", offers.size),
            priceInsight = parseMarketPriceInsight(json.optJSONObject("priceInsight"))
        )
    }

    suspend fun fetchMarketItemPrice(
        kind: String,
        itemId: String
    ): MarketPriceInsight = withContext(Dispatchers.IO) {
        val path =
            "/v1/market/item-price?kind=${kind.trim().encodeURL()}&itemId=${itemId.trim().encodeURL()}"
        val json = authedGet(path)
        parseMarketPriceInsight(json.optJSONObject("priceInsight"))
            ?: MarketPriceInsight(windowDays = json.optInt("priceWindowDays", 90))
    }

    data class AdminMarketSettings(
        val priceWindowDays: Int,
        val options: List<Int>,
        val achievementDailyCap: Int,
        val achievementDailyCapMin: Int,
        val achievementDailyCapMax: Int
    )

    suspend fun fetchAdminMarketSettings(): AdminMarketSettings = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/admin/market-settings")
        val options = buildList {
            val arr = json.optJSONArray("options")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val n = arr.optInt(i, 0)
                    if (n > 0) add(n)
                }
            }
        }.ifEmpty { listOf(7, 14, 30, 60, 90, 180) }
        AdminMarketSettings(
            priceWindowDays = json.optInt("priceWindowDays", 90),
            options = options,
            achievementDailyCap = json.optInt("achievementDailyCap", 12),
            achievementDailyCapMin = json.optInt("achievementDailyCapMin", 0),
            achievementDailyCapMax = json.optInt("achievementDailyCapMax", 500)
        )
    }

    suspend fun setAdminMarketPriceWindow(days: Int): Int = withContext(Dispatchers.IO) {
        val body = JSONObject().put("priceWindowDays", days).toString()
        val json = authedPost("/v1/admin/market-settings", body)
        json.optInt("priceWindowDays", days)
    }

    suspend fun setAdminAchievementDailyCap(cap: Int): Int = withContext(Dispatchers.IO) {
        val body = JSONObject().put("achievementDailyCap", cap).toString()
        val json = authedPost("/v1/admin/market-settings", body)
        json.optInt("achievementDailyCap", cap)
    }

    suspend fun fetchMyMarketListings(): List<MarketListing> = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/market/mine")
        buildList {
            val arr = json.optJSONArray("listings")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    parseMarketListing(arr.optJSONObject(i) ?: continue)?.let { add(it) }
                }
            }
        }
    }

    suspend fun fetchPendingMarketSales(): PendingSalesResult = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/market/pending-sales")
        val arr = json.optJSONArray("sales")
        val sales = buildList {
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").trim()
                    if (id.isBlank()) continue
                    add(
                        PendingMarketSale(
                            id = id,
                            kind = o.optString("kind"),
                            itemId = o.optString("itemId"),
                            emoji = o.optString("emoji").ifBlank { "📦" },
                            label = o.optString("label").ifBlank { o.optString("itemId") },
                            priceCoins = o.optInt("priceCoins", 0),
                            soldAt = o.optLong("soldAt", 0L),
                            buyerNickname = o.optString("buyerNickname", "Jemand")
                        )
                    )
                }
            }
        }
        PendingSalesResult(
            sales = sales,
            totalCoins = json.optInt("totalCoins", sales.sumOf { it.priceCoins }),
            count = json.optInt("count", sales.size)
        )
    }

    suspend fun claimPendingMarketSales(): PendingSalesResult = withContext(Dispatchers.IO) {
        val json = authedPost("/v1/market/claim-sales", "{}")
        json.optJSONObject("user")?.let { userJson ->
            AccountSession.setAccount(AccountInfo.fromApi(userJson))
        }
        PendingSalesResult(
            sales = emptyList(),
            totalCoins = json.optInt("totalCoins", 0),
            count = json.optInt("claimed", 0)
        )
    }

    suspend fun listMarketItem(
        kind: String,
        itemId: String,
        priceCoins: Int,
        allowTrade: Boolean,
        isPrivate: Boolean = false,
        targetUserId: String? = null,
        tradeWantKind: String? = null,
        tradeWantItemId: String? = null,
        tradeWantLabel: String? = null
    ): Pair<MarketListing, AccountInfo> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("kind", kind)
            .put("itemId", clipShopItemId(itemId, 32))
            .put("priceCoins", priceCoins.coerceIn(0, 10_000))
            .put("allowTrade", allowTrade)
            .put("private", isPrivate)
        if (isPrivate && !targetUserId.isNullOrBlank()) {
            body.put("targetUserId", targetUserId.trim())
        }
        if (allowTrade) {
            tradeWantKind?.takeIf { it.isNotBlank() }?.let { body.put("tradeWantKind", it) }
            tradeWantItemId?.takeIf { it.isNotBlank() }?.let { body.put("tradeWantItemId", it) }
            tradeWantLabel?.takeIf { it.isNotBlank() }?.let {
                body.put("tradeWantLabel", it.take(40))
            }
        }
        val request = authedRequestBuilder("/v1/market/list")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful) {
                throw LuvApiException(
                    message = when (json?.optString("error")) {
                        "not_owned" -> "Artikel nicht im Inventar."
                        "not_sellable" -> "Diesen Artikel kannst du nicht anbieten."
                        "equipped" -> json.optString("message")
                            .takeIf { it.isNotBlank() }
                            ?: "Artikel ist ausgerüstet — zuerst wechseln."
                        "bad_price" -> "Preis mind. 1 Coin oder Tausch aktivieren."
                        "need_target" -> "Privates Angebot braucht einen Empfänger."
                        "self" -> "Nicht an dich selbst."
                        "bad_kind" -> "Ungültige Kategorie."
                        else -> json?.optString("message")?.takeIf { it.isNotBlank() }
                            ?: "Angebot fehlgeschlagen."
                    },
                    error = json?.optString("error")
                )
            }
            val parsed = json ?: throw LuvApiException("Ungültige Server-Antwort")
            val listing = parseMarketListing(parsed.optJSONObject("listing"))
                ?: throw LuvApiException("Ungültige Server-Antwort")
            val user = AccountInfo.fromApi(parsed.getJSONObject("user"))
            AccountSession.setAccount(user)
            listing to user
        }
    }

    suspend fun cancelMarketListing(listingId: String): AccountInfo = withContext(Dispatchers.IO) {
        val id = listingId.trim().encodeURL()
        val json = authedPost("/v1/market/$id/cancel", "{}")
        val user = AccountInfo.fromApi(json.getJSONObject("user"))
        AccountSession.setAccount(user)
        user
    }

    suspend fun buyMarketListing(listingId: String): AccountInfo = withContext(Dispatchers.IO) {
        val id = listingId.trim().encodeURL()
        val request = authedRequestBuilder("/v1/market/$id/buy")
            .post(emptyBody)
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful) {
                throw LuvApiException(
                    message = when (json?.optString("error")) {
                        "no_coins" -> "Nicht genug Coins."
                        "self" -> "Eigenes Angebot."
                        "trade_only" -> "Nur Tausch — wähle Tausch."
                        "not_found" -> "Angebot nicht mehr da."
                        "forbidden" -> "Kein Zugriff auf dieses Angebot."
                        else -> json?.optString("message")?.takeIf { it.isNotBlank() }
                            ?: "Kauf fehlgeschlagen."
                    },
                    error = json?.optString("error")
                )
            }
            val parsed = json ?: throw LuvApiException("Ungültige Server-Antwort")
            val user = AccountInfo.fromApi(parsed.getJSONObject("user"))
            AccountSession.setAccount(user)
            user
        }
    }

    suspend fun tradeMarketListing(
        listingId: String,
        kind: String,
        itemId: String
    ): AccountInfo = withContext(Dispatchers.IO) {
        val id = listingId.trim().encodeURL()
        val body = JSONObject()
            .put("kind", kind)
            .put("itemId", clipShopItemId(itemId, 32))
            .toString()
        val request = authedRequestBuilder("/v1/market/$id/trade")
            .post(body.toRequestBody(jsonMedia))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful) {
                throw LuvApiException(
                    message = when (json?.optString("error")) {
                        "not_owned" -> "Dein Tauschartikel fehlt."
                        "wrong_offer" -> json.optString("message").takeIf { it.isNotBlank() }
                            ?: "Falscher Tauschartikel."
                        "not_found" -> "Kein Tausch-Angebot."
                        "self" -> "Eigenes Angebot."
                        "forbidden" -> "Kein Zugriff."
                        "not_sellable" -> "Artikel nicht tauschbar."
                        else -> json?.optString("message")?.takeIf { it.isNotBlank() }
                            ?: "Tausch fehlgeschlagen."
                    },
                    error = json?.optString("error")
                )
            }
            val parsed = json ?: throw LuvApiException("Ungültige Server-Antwort")
            val user = AccountInfo.fromApi(parsed.getJSONObject("user"))
            AccountSession.setAccount(user)
            user
        }
    }

    suspend fun petKraul(userId: String): PetKraulResult = withContext(Dispatchers.IO) {
        val uid = userId.trim()
        val request = authedRequestBuilder("/v1/users/${uid.encodeURL()}/pet-kraul")
            .post("{}".toRequestBody(jsonMedia))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful) {
                val err = json?.optString("error").orEmpty()
                throw LuvApiException(
                    when (err) {
                        "already_krault" -> "Heute schon gekrault (Reset 0 Uhr MEZ)"
                        "self_kraul" -> "Eigener Begleiter"
                        "friends_only" -> "Kraulen geht nur bei Freunden."
                        "recv_cap" -> "Heute schon genug Kraule bekommen."
                        else -> json?.optString("message")?.takeIf { it.isNotBlank() }
                            ?: "Kraulen fehlgeschlagen"
                    },
                    error = err.ifBlank { null }
                )
            }
            val body = json ?: throw LuvApiException("Ungültige Server-Antwort")
            PetKraulResult(
                petEmoji = body.optString("petEmoji", "🐣").ifBlank { "🐣" },
                toCoins = body.optInt("toCoins", 0),
                amount = body.optInt("amount", 1),
                friendshipLevel = body.optInt("friendshipLevel", 0),
                friendshipLevelBumped = body.optBoolean("friendshipLevelBumped", false)
            )
        }
    }

    /** 1 Coin ins Münzglas — max. 10 Coins pro Profil/Tag (0:00 MEZ), alle Spender zusammen. */
    suspend fun tipGlass(userId: String): GlassTipResult = withContext(Dispatchers.IO) {
        val uid = userId.trim()
        val request = authedRequestBuilder("/v1/users/${uid.encodeURL()}/tip-glass")
            .post("{}".toRequestBody(jsonMedia))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful) {
                val err = json?.optString("error").orEmpty()
                throw LuvApiException(
                    when (err) {
                        "daily_tip_limit" ->
                            json?.optString("message")?.takeIf { it.isNotBlank() }
                                ?: "Münzglas heute voll (10 Coins). Ab 0 Uhr MEZ wieder."
                        "insufficient_coins" -> "Nicht genug Coins"
                        "no_glass" -> "Kein Münzglas auf dem Profil"
                        "self_tip" -> "Eigenes Glas"
                        "rate_limited" -> "Zu schnell — kurz warten"
                        else -> json?.optString("message")?.takeIf { it.isNotBlank() }
                            ?: "Spenden fehlgeschlagen"
                    },
                    error = err.ifBlank { null }
                )
            }
            val body = json ?: throw LuvApiException("Ungültige Server-Antwort")
            val fromJson = body.optJSONObject("from")
            val from = fromJson?.let { AccountInfo.fromApi(it) }
            if (from != null) AccountSession.setAccount(from)
            GlassTipResult(
                remaining = body.optInt("remaining", 0),
                toCoins = body.optInt("toCoins", 0),
                from = from,
                received = body.optInt("received", 0)
            )
        }
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
                            compareAtEur = o.optString("compareAtEur").takeIf { it.isNotBlank() },
                            onceOnly = o.optBoolean("onceOnly", false),
                            isOffer = o.optBoolean(
                                "isOffer",
                                o.optBoolean("onceOnly", false) ||
                                    o.optString("compareAtEur").isNotBlank()
                            ),
                            mostPurchased = o.optBoolean("mostPurchased", false)
                        )
                    )
                }
            }
            json.optBoolean("enabled", false) to packs
        }
    }

    /** Google Play Kauf serverseitig verifizieren und Coins gutschreiben. */
    suspend fun confirmPlayPurchase(
        productId: String,
        purchaseToken: String,
        orderId: String? = null
    ): Int = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("productId", productId)
            .put("purchaseToken", purchaseToken)
            .put("orderId", orderId ?: JSONObject.NULL)
            .toString()
            .toRequestBody(jsonMedia)
        val request = authedRequestBuilder("/v1/shop/play-purchase").post(body).build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful) {
                throw LuvApiException(
                    json?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: "Kauf konnte nicht bestätigt werden",
                    error = json?.optString("error")
                )
            }
            json?.optJSONObject("user")?.let { AccountSession.setAccount(AccountInfo.fromApi(it)) }
            json?.optInt("coinsGranted", 0) ?: 0
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
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 404) {
                val raw = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull()
                throw LuvApiException(
                    message = json?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: "Lobby verlassen fehlgeschlagen.",
                    error = json?.optString("error")
                )
            }
        }
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
        b.header("X-Luv-Version-Code", BuildConfig.VERSION_CODE.toString())
        b.header("X-Luv-Version-Name", BuildConfig.VERSION_NAME)
        return b
    }

    private fun throwApiFailure(raw: String, code: Int): Nothing {
        val json = runCatching { JSONObject(raw) }.getOrNull()
        val err = json?.optString("error")?.takeIf { it.isNotBlank() }
        val serverMsg = json?.optString("message")?.takeIf { it.isNotBlank() }
        val mapped = when (err) {
            "invalid_query" -> "Bitte Spitzname oder E-Mail eingeben."
            "is_admin" -> "Admins brauchen keine Moderator-Rolle."
            "not_mod" -> "Dieser Nutzer ist (noch) kein Moderator."
            "not_found" -> "Nicht gefunden."
            "forbidden" -> serverMsg ?: "Keine Berechtigung."
            "unauthorized" -> "Bitte neu anmelden."
            "bad_delta" -> "Ungültiger Coin-Betrag."
            "cannot_ban_admin" -> "Admins können nicht gesperrt werden."
            "cannot_delete_admin" -> "Admins können nicht gelöscht werden."
            "self_delete" -> "Eigenes Konto hier nicht löschen."
            "empty" -> serverMsg ?: "Eingabe zu kurz."
            "bad_nick" -> "Spitzname ungültig."
            "nickname_taken" -> "Spitzname schon vergeben."
            "nickname_locked" -> "Spitzname kann nicht geändert werden."
            else -> null
        }
        throw LuvApiException(
            message = mapped ?: serverMsg ?: "API-Fehler ($code)",
            error = err
        )
    }

    private fun authedGet(path: String): JSONObject {
        val request = authedRequestBuilder(path).get().build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throwApiFailure(raw, response.code)
            return JSONObject(raw)
        }
    }

    private fun authedPost(path: String, jsonBody: String): JSONObject {
        val request = authedRequestBuilder(path)
            .post(jsonBody.toRequestBody(jsonMedia))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throwApiFailure(raw, response.code)
            return JSONObject(raw)
        }
    }

    private fun authedPatch(path: String, jsonBody: String): JSONObject {
        val request = authedRequestBuilder(path)
            .patch(jsonBody.toRequestBody(jsonMedia))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throwApiFailure(raw, response.code)
            return JSONObject(raw)
        }
    }

    private fun authedPut(path: String, jsonBody: String): JSONObject {
        val request = authedRequestBuilder(path)
            .put(jsonBody.toRequestBody(jsonMedia))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throwApiFailure(raw, response.code)
            return JSONObject(raw)
        }
    }

    private fun authedDelete(path: String): JSONObject {
        val request = authedRequestBuilder(path)
            .delete()
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throwApiFailure(raw, response.code)
            return if (raw.isBlank()) JSONObject() else JSONObject(raw)
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
                        "already_in_random" -> msg
                            ?: "Du bist schon in einer Random-Lobby. Bitte zuerst verlassen."
                        "no_coins" -> "Nicht genug Coins."
                        "insufficient_coins" -> "Nicht genug Coins."
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
                isRandom = parsed.optBoolean("isRandom", false),
                isWedding = parsed.optBoolean("isWedding", false),
                name = parsed.optString("name", "Lobby"),
                hostNickname = parsed.optString("hostNickname", "Host"),
                hostColorSide = parsed.optString("hostColorSide", "blue").ifBlank { "blue" },
                suggestedColorIndex = suggested,
                peers = parsed.optInt("peers", parsed.optInt("count", 0)),
                memberList = parseMemberList(parsed),
                role = parsed.optString("role").takeIf { it.isNotBlank() }
            )
        }
    }

    suspend fun randomMatch(): RoomSession = withContext(Dispatchers.IO) {
        val request = authedRequestBuilder("/v1/rooms/random-match")
            .post("{}".toRequestBody(jsonMedia))
            .build()
        executeRoom(request)
    }

    private fun parseLootboxItem(item: JSONObject, coins: Int, pendingId: String = ""): LootboxResult =
        LootboxResult(
            kind = item.optString("kind"),
            itemId = item.optString("itemId"),
            emoji = item.optString("emoji").ifBlank { item.optString("itemId") },
            label = item.optString("label").ifBlank { item.optString("itemId") },
            shopPrice = item.optInt("shopPrice", 0),
            chancePercent = item.optDouble("chancePercent", 0.0),
            coins = coins,
            pendingId = pendingId.ifBlank { item.optString("id") },
            duplicate = item.optBoolean("duplicate", false),
            coinsRefund = item.optInt("coinsRefund", 0)
        )

    private fun parseLootboxList(arr: org.json.JSONArray?, coins: Int): List<LootboxResult> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(parseLootboxItem(o, coins, o.optString("id")))
            }
        }
    }

    /** Kauf: Coins abziehen, Belohnungen als pending (noch nicht im Inventar). */
    suspend fun buyLootbox(quantity: Int = 1): LootboxPurchaseResult = withContext(Dispatchers.IO) {
        val qty = quantity.coerceIn(1, 50)
        val json = authedPost(
            "/v1/shop/lootbox",
            JSONObject().put("quantity", qty).toString()
        )
        json.optJSONObject("user")?.let { userJson ->
            AccountSession.setAccount(AccountInfo.fromApi(userJson))
        }
        val userCoins = json.optJSONObject("user")?.optInt("coins", -1)?.takeIf { it >= 0 }
            ?: AccountSession.account.value?.coins ?: 0
        val purchased = parseLootboxList(json.optJSONArray("purchased"), userCoins)
        if (purchased.isEmpty()) {
            throw LuvApiException("Keine Lootbox-Belohnung erhalten")
        }
        LootboxPurchaseResult(
            purchased = purchased,
            pending = parseLootboxList(json.optJSONArray("pending"), userCoins),
            quantity = json.optInt("quantity", purchased.size),
            total = json.optInt("total", purchased.size * 10),
            coins = userCoins
        )
    }

    suspend fun pendingLootboxes(): List<LootboxResult> = withContext(Dispatchers.IO) {
        val json = authedGet("/v1/shop/lootbox/pending")
        json.optJSONObject("user")?.let { userJson ->
            AccountSession.setAccount(AccountInfo.fromApi(userJson))
        }
        val userCoins = json.optJSONObject("user")?.optInt("coins", -1)?.takeIf { it >= 0 }
            ?: AccountSession.account.value?.coins ?: 0
        parseLootboxList(json.optJSONArray("pending"), userCoins)
    }

    /** Pending öffnen → Item ins Inventar. */
    suspend fun openLootbox(pendingId: String): LootboxResult = withContext(Dispatchers.IO) {
        val json = authedPost(
            "/v1/shop/lootbox/open",
            JSONObject().put("id", pendingId).toString()
        )
        json.optJSONObject("user")?.let { userJson ->
            AccountSession.setAccount(AccountInfo.fromApi(userJson))
        }
        val item = json.optJSONObject("item")
            ?: throw LuvApiException("Lootbox konnte nicht geöffnet werden")
        val userCoins = json.optJSONObject("user")?.optInt("coins", -1)?.takeIf { it >= 0 }
            ?: AccountSession.account.value?.coins ?: 0
        parseLootboxItem(item, userCoins, pendingId)
    }

    private fun parseMemberList(json: JSONObject): List<RosterMember> {
        val rich = json.optJSONArray("memberList") ?: return emptyList()
        return buildList {
            for (i in 0 until rich.length()) {
                val o = rich.optJSONObject(i) ?: continue
                val nick = o.optString("nickname").trim()
                if (nick.length < 2 || nick.equals("Jemand", ignoreCase = true)) continue
                add(
                    RosterMember(
                        userId = o.optString("userId").takeIf { it.isNotBlank() && it != "null" },
                        nickname = nick,
                        colorIndex = o.optInt("colorIndex", -1),
                        active = o.optBoolean("active", false),
                        online = if (o.has("online")) o.optBoolean("online", true) else true,
                        petEmoji = o.optString("petEmoji", "🐣").trim().ifBlank { "🐣" }
                    )
                )
            }
        }
    }

    private fun parsePublicCanvasPreview(json: JSONObject, cycled: Boolean = false): PublicCanvasPreview {
        val names = buildList {
            val arr = json.optJSONArray("memberNicknames")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }
        return PublicCanvasPreview(
            id = json.optString("id"),
            lobbyName = json.optString("lobbyName", "Lobby"),
            hostNickname = json.optString("hostNickname", "Jemand"),
            memberNicknames = names,
            nameLine = json.optString("nameLine").ifBlank {
                json.optString("hostNickname", "Jemand")
            },
            imageUrl = json.optString("imageUrl"),
            createdAt = json.optLong("createdAt", 0L),
            expiresAt = json.optLong("expiresAt", 0L),
            cycled = cycled
        )
    }

    suspend fun fetchRandomPublicCanvas(
        excludeIds: Collection<String> = emptyList()
    ): PublicCanvasPreview? = withContext(Dispatchers.IO) {
        runCatching {
            val exclude = excludeIds
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(80)
                .joinToString(",")
            val url = buildString {
                append(baseUrl())
                append("/v1/public-canvases/random")
                if (exclude.isNotEmpty()) {
                    append("?exclude=")
                    append(java.net.URLEncoder.encode(exclude, Charsets.UTF_8.name()))
                }
            }
            val request = Request.Builder().url(url).get().build()
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@runCatching null
                val json = JSONObject(raw)
                if (!json.optBoolean("available", false)) return@runCatching null
                parsePublicCanvasPreview(json, cycled = json.optBoolean("cycled", false))
            }
        }.getOrNull()
    }

    /** Mehrere öffentliche Bilder (Sozial → Bilder). */
    suspend fun fetchPublicCanvasSample(
        limit: Int = 3,
        excludeIds: Collection<String> = emptyList()
    ): List<PublicCanvasPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val exclude = excludeIds
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(80)
                .joinToString(",")
            val url = buildString {
                append(baseUrl())
                append("/v1/public-canvases/sample?limit=")
                append(limit.coerceIn(1, 6))
                if (exclude.isNotEmpty()) {
                    append("&exclude=")
                    append(java.net.URLEncoder.encode(exclude, Charsets.UTF_8.name()))
                }
            }
            val request = Request.Builder().url(url).get().build()
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@runCatching emptyList()
                val json = JSONObject(raw)
                val arr = json.optJSONArray("items") ?: return@runCatching emptyList()
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        add(parsePublicCanvasPreview(o))
                    }
                }
            }
        }.getOrDefault(emptyList())
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
