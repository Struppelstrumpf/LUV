package com.luv.couple.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.dataStore by preferencesDataStore("luv_prefs")

class PrefsRepository(private val context: Context) {
    private val nicknameKey = stringPreferencesKey("nickname")
    private val colorIndexKey = stringPreferencesKey("color_index")
    private val lobbiesKey = stringPreferencesKey("lobbies_json")
    private val activeLobbyKey = stringPreferencesKey("active_lobby_id")
    private val widgetMapKey = stringPreferencesKey("widget_lobby_map")
    private val partnerNotifyKey = booleanPreferencesKey("partner_draw_notify")
    private val partnerHapticKey = booleanPreferencesKey("partner_draw_haptic")
    private val lastClearDayKey = stringPreferencesKey("last_clear_day")
    private val tutorialDoneKey = booleanPreferencesKey("tutorial_done")
    private val installIdKey = stringPreferencesKey("install_id")
    private val installSecretKey = stringPreferencesKey("install_secret")
    private val sessionTokenKey = stringPreferencesKey("session_token")
    private val accountJsonKey = stringPreferencesKey("account_json")
    private val lastNotifiedUpdateKey = intPreferencesKey("last_notified_update_code")

    // Legacy keys — Migration
    private val genderKey = stringPreferencesKey("gender")
    private val roleKey = stringPreferencesKey("role")
    private val tokenKey = stringPreferencesKey("token")
    private val inviteCodeKey = stringPreferencesKey("invite_code")
    private val pairedKey = stringPreferencesKey("paired")

    val nicknameFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[nicknameKey]?.takeIf { it.isNotBlank() }
    }

    val tutorialDoneFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[tutorialDoneKey] ?: false
    }

    val accountFlow: Flow<AccountInfo?> = context.dataStore.data.map { prefs ->
        AccountInfo.fromJson(prefs[accountJsonKey])
    }

    val lobbiesFlow: Flow<List<Lobby>> = context.dataStore.data.map { prefs ->
        parseLobbies(prefs[lobbiesKey]).ifEmpty { migrateLegacy(prefs) }
    }

    val activeLobbyIdFlow: Flow<String?> = context.dataStore.data.map { it[activeLobbyKey] }

    val partnerDrawNotifyFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[partnerNotifyKey] ?: true
    }

    val partnerHapticFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[partnerHapticKey] ?: true
    }

    suspend fun setNickname(nickname: String) {
        val clean = nickname.trim().take(18)
        context.dataStore.edit {
            it[nicknameKey] = clean
            if (it[colorIndexKey].isNullOrBlank()) {
                it[colorIndexKey] = PeerPalette.indexFor(clean.lowercase()).toString()
            }
        }
    }

    suspend fun setColorIndex(index: Int) {
        val safe = index.coerceIn(0, PeerPalette.COLOR_COUNT - 1)
        context.dataStore.edit { it[colorIndexKey] = safe.toString() }
    }

    suspend fun setTutorialDone(done: Boolean = true) {
        context.dataStore.edit { it[tutorialDoneKey] = done }
    }

    suspend fun ensureInstallCredentials(): Pair<String, String> {
        val prefs = context.dataStore.data.first()
        val existingId = prefs[installIdKey]
        val existingSecret = prefs[installSecretKey]
        if (!existingId.isNullOrBlank() && !existingSecret.isNullOrBlank()) {
            return existingId to existingSecret
        }
        val id = UUID.randomUUID().toString()
        val secret = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        context.dataStore.edit {
            it[installIdKey] = id
            it[installSecretKey] = secret
        }
        return id to secret
    }

    suspend fun saveSession(sessionToken: String, account: AccountInfo) {
        context.dataStore.edit {
            it[sessionTokenKey] = sessionToken
            it[accountJsonKey] = account.toJson()
            it[nicknameKey] = account.nickname
            if (it[colorIndexKey].isNullOrBlank()) {
                it[colorIndexKey] = PeerPalette.indexFor(account.nickname.lowercase()).toString()
            }
        }
    }

    suspend fun updateAccount(account: AccountInfo) {
        context.dataStore.edit {
            it[accountJsonKey] = account.toJson()
            it[nicknameKey] = account.nickname
        }
    }

    suspend fun lastNotifiedUpdateCode(): Int =
        context.dataStore.data.first()[lastNotifiedUpdateKey] ?: 0

    suspend fun setLastNotifiedUpdateCode(code: Int) {
        context.dataStore.edit { it[lastNotifiedUpdateKey] = code }
    }

    suspend fun sessionToken(): String? =
        context.dataStore.data.first()[sessionTokenKey]

    suspend fun setPartnerDrawNotifyEnabled(enabled: Boolean) {
        context.dataStore.edit { it[partnerNotifyKey] = enabled }
    }

    suspend fun setPartnerHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[partnerHapticKey] = enabled }
    }

    suspend fun isPartnerDrawNotifyEnabled(): Boolean =
        context.dataStore.data.first()[partnerNotifyKey] ?: true

    suspend fun isPartnerHapticEnabled(): Boolean =
        context.dataStore.data.first()[partnerHapticKey] ?: true

    suspend fun lastClearDay(): String? = context.dataStore.data.first()[lastClearDayKey]

    suspend fun setLastClearDay(day: String) {
        context.dataStore.edit { it[lastClearDayKey] = day }
    }

    suspend fun setActiveLobby(lobbyId: String?) {
        context.dataStore.edit { prefs ->
            if (lobbyId == null) prefs.remove(activeLobbyKey)
            else prefs[activeLobbyKey] = lobbyId
        }
    }

    suspend fun upsertLobby(lobby: Lobby) {
        context.dataStore.edit { prefs ->
            val list = parseLobbies(prefs[lobbiesKey]).toMutableList()
            val idx = list.indexOfFirst { it.id == lobby.id }
            if (idx >= 0) list[idx] = lobby else {
                if (list.size >= PeerPalette.MAX_LOBBIES) {
                    throw IllegalStateException("max_lobbies")
                }
                list.add(lobby)
            }
            prefs[lobbiesKey] = encodeLobbies(list)
            prefs[activeLobbyKey] = lobby.id
            // Legacy cleanup
            prefs.remove(tokenKey)
            prefs.remove(inviteCodeKey)
            prefs.remove(pairedKey)
            prefs.remove(roleKey)
        }
    }

    suspend fun renameLobby(lobbyId: String, name: String) {
        val clean = name.trim().take(24).ifBlank { return }
        context.dataStore.edit { prefs ->
            val list = parseLobbies(prefs[lobbiesKey]).map {
                if (it.id == lobbyId) it.copy(name = clean) else it
            }
            prefs[lobbiesKey] = encodeLobbies(list)
        }
    }

    suspend fun removeLobby(lobbyId: String) {
        context.dataStore.edit { prefs ->
            val list = parseLobbies(prefs[lobbiesKey]).filterNot { it.id == lobbyId }
            prefs[lobbiesKey] = encodeLobbies(list)
            if (prefs[activeLobbyKey] == lobbyId) {
                if (list.isEmpty()) prefs.remove(activeLobbyKey)
                else prefs[activeLobbyKey] = list.first().id
            }
            val map = parseWidgetMap(prefs[widgetMapKey]).filterValues { it != lobbyId }
            prefs[widgetMapKey] = encodeWidgetMap(map)
        }
    }

    suspend fun bindWidget(widgetId: Int, lobbyId: String) {
        context.dataStore.edit { prefs ->
            val map = parseWidgetMap(prefs[widgetMapKey]).toMutableMap()
            map[widgetId] = lobbyId
            prefs[widgetMapKey] = encodeWidgetMap(map)
        }
    }

    suspend fun unbindWidget(widgetId: Int) {
        context.dataStore.edit { prefs ->
            val map = parseWidgetMap(prefs[widgetMapKey]).toMutableMap()
            map.remove(widgetId)
            prefs[widgetMapKey] = encodeWidgetMap(map)
        }
    }

    suspend fun widgetLobbyId(widgetId: Int): String? {
        val prefs = context.dataStore.data.first()
        return parseWidgetMap(prefs[widgetMapKey])[widgetId]
            ?: prefs[activeLobbyKey]
            ?: parseLobbies(prefs[lobbiesKey]).firstOrNull()?.id
    }

    suspend fun snapshot(): SessionSnapshot {
        migrateIfNeeded()
        val prefs = context.dataStore.data.first()
        val lobbies = parseLobbies(prefs[lobbiesKey])
        val activeId = prefs[activeLobbyKey] ?: lobbies.firstOrNull()?.id
        val active = lobbies.firstOrNull { it.id == activeId }
        val nickname = prefs[nicknameKey]?.takeIf { it.isNotBlank() }
        val colorIndex = prefs[colorIndexKey]?.toIntOrNull()
            ?: nickname?.let { PeerPalette.indexFor(it.lowercase()) }
            ?: 0
        return SessionSnapshot(
            nickname = nickname,
            colorIndex = colorIndex,
            lobbies = lobbies,
            activeLobbyId = activeId,
            activeLobby = active,
            tutorialDone = prefs[tutorialDoneKey] ?: false,
            sessionToken = prefs[sessionTokenKey],
            account = AccountInfo.fromJson(prefs[accountJsonKey])
        )
    }

    private suspend fun migrateIfNeeded() {
        context.dataStore.edit { prefs ->
            if (!prefs[lobbiesKey].isNullOrBlank()) return@edit
            val migrated = migrateLegacy(prefs)
            if (migrated.isNotEmpty()) {
                prefs[lobbiesKey] = encodeLobbies(migrated)
                prefs[activeLobbyKey] = migrated.first().id
                prefs.remove(tokenKey)
                prefs.remove(inviteCodeKey)
                prefs.remove(pairedKey)
                prefs.remove(roleKey)
            }
            if (prefs[nicknameKey].isNullOrBlank()) {
                // Placeholder — User muss Nickname setzen; Gender nicht mehr als Name
            }
        }
    }

    private fun migrateLegacy(prefs: Preferences): List<Lobby> {
        if (prefs[pairedKey] != "true") return emptyList()
        val token = prefs[tokenKey] ?: return emptyList()
        val invite = prefs[inviteCodeKey] ?: return emptyList()
        val code = invite.uppercase().removePrefix("LUV-").removePrefix("LUV")
        if (code.isBlank()) return emptyList()
        val role = prefs[roleKey]?.let { runCatching { Role.valueOf(it) }.getOrNull() } ?: Role.HOST
        return listOf(
            Lobby(
                id = UUID.randomUUID().toString(),
                name = "Lobby",
                role = role,
                code = code,
                token = token,
                invite = if (invite.startsWith("LUV", ignoreCase = true)) invite.uppercase()
                else "LUV-$code"
            )
        )
    }

    companion object {
        fun parseLobbies(raw: String?): List<Lobby> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        add(
                            Lobby(
                                id = o.getString("id"),
                                name = o.optString("name", "Lobby"),
                                role = runCatching { Role.valueOf(o.getString("role")) }.getOrDefault(Role.HOST),
                                code = o.getString("code"),
                                token = o.getString("token"),
                                invite = o.optString("invite", "LUV-${o.getString("code")}"),
                                capacity = o.optInt("capacity", PeerPalette.FREE_LOBBY_START_CAPACITY),
                                isFree = o.optBoolean("isFree", false),
                                hostNickname = o.optString("hostNickname", "")
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

        fun encodeLobbies(list: List<Lobby>): String {
            val arr = JSONArray()
            list.forEach { lobby ->
                arr.put(
                    JSONObject()
                        .put("id", lobby.id)
                        .put("name", lobby.name)
                        .put("role", lobby.role.name)
                        .put("code", lobby.code)
                        .put("token", lobby.token)
                        .put("invite", lobby.invite)
                        .put("capacity", lobby.capacity)
                        .put("isFree", lobby.isFree)
                        .put("hostNickname", lobby.hostNickname)
                )
            }
            return arr.toString()
        }

        fun parseWidgetMap(raw: String?): Map<Int, String> {
            if (raw.isNullOrBlank()) return emptyMap()
            return runCatching {
                val o = JSONObject(raw)
                buildMap {
                    o.keys().forEach { key ->
                        key.toIntOrNull()?.let { put(it, o.getString(key)) }
                    }
                }
            }.getOrDefault(emptyMap())
        }

        fun encodeWidgetMap(map: Map<Int, String>): String {
            val o = JSONObject()
            map.forEach { (k, v) -> o.put(k.toString(), v) }
            return o.toString()
        }
    }
}

data class SessionSnapshot(
    val nickname: String?,
    val colorIndex: Int,
    val lobbies: List<Lobby>,
    val activeLobbyId: String?,
    val activeLobby: Lobby?,
    val tutorialDone: Boolean = false,
    val sessionToken: String? = null,
    val account: AccountInfo? = null
) {
    val hasNickname: Boolean get() = !nickname.isNullOrBlank()
    val hasLobbies: Boolean get() = lobbies.isNotEmpty()
}

data class AccountInfo(
    val id: String,
    val nickname: String,
    val coins: Int,
    val role: String,
    val freeSessionsLeft: Int,
    val freeSessionsPerDay: Int,
    val dailyCoins: Int,
    val sessionCost: Int,
    val canClaimDaily: Boolean,
    val googleLinked: Boolean
) {
    val isAdmin: Boolean get() = role == "admin"

    fun toJson(): String = JSONObject()
        .put("id", id)
        .put("nickname", nickname)
        .put("coins", coins)
        .put("role", role)
        .put("freeSessionsLeft", freeSessionsLeft)
        .put("freeSessionsPerDay", freeSessionsPerDay)
        .put("dailyCoins", dailyCoins)
        .put("sessionCost", sessionCost)
        .put("canClaimDaily", canClaimDaily)
        .put("googleLinked", googleLinked)
        .toString()

    companion object {
        fun fromJson(raw: String?): AccountInfo? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(raw)
                AccountInfo(
                    id = o.getString("id"),
                    nickname = o.optString("nickname", "Luv"),
                    coins = o.optInt("coins", 0),
                    role = o.optString("role", "user"),
                    freeSessionsLeft = o.optInt("freeSessionsLeft", 0),
                    freeSessionsPerDay = o.optInt("freeSessionsPerDay", 5),
                    dailyCoins = o.optInt("dailyCoins", 10),
                    sessionCost = o.optInt("sessionCost", 1),
                    canClaimDaily = o.optBoolean("canClaimDaily", false),
                    googleLinked = o.optBoolean("googleLinked", false)
                )
            }.getOrNull()
        }

        fun fromApi(o: JSONObject): AccountInfo = AccountInfo(
            id = o.getString("id"),
            nickname = o.optString("nickname", "Luv"),
            coins = o.optInt("coins", 0),
            role = o.optString("role", "user"),
            freeSessionsLeft = o.optInt("freeSessionsLeft", 0),
            freeSessionsPerDay = o.optInt("freeSessionsPerDay", 5),
            dailyCoins = o.optInt("dailyCoins", 10),
            sessionCost = o.optInt("sessionCost", 1),
            canClaimDaily = o.optBoolean("canClaimDaily", false),
            googleLinked = o.optBoolean("googleLinked", false)
        )
    }
}
