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
    private val liveProximityRichKey = booleanPreferencesKey("live_proximity_rich")
    private val liveProximityWakeKey = booleanPreferencesKey("live_proximity_wake")
    /** Pro Lobby: Lebendige-Nähe-Impulse (Default aus). JSON-Objekt lobbyId→bool */
    private val lobbyProximityKey = stringPreferencesKey("lobby_proximity_json")
    private val seenMemoriesKey = stringPreferencesKey("seen_memories_json")
    private val lastClearDayKey = stringPreferencesKey("last_clear_day")
    private val tutorialDoneKey = booleanPreferencesKey("tutorial_done")
    private val installIdKey = stringPreferencesKey("install_id")
    private val installSecretKey = stringPreferencesKey("install_secret")
    private val sessionTokenKey = stringPreferencesKey("session_token")
    private val accountJsonKey = stringPreferencesKey("account_json")
    private val lastNotifiedUpdateKey = intPreferencesKey("last_notified_update_code")
    private val lastPublicIdKey = stringPreferencesKey("last_public_canvas_id")
    private val lastPublicNameKey = stringPreferencesKey("last_public_canvas_name")
    private val lastPublicImageKey = stringPreferencesKey("last_public_canvas_image")
    private val lastPublicHostKey = stringPreferencesKey("last_public_canvas_host")
    /** Bereits gemeldete Lobby-Beitritte: "lobbyId|userIdOrNick" */
    private val joinAnnouncedKey = stringPreferencesKey("join_announced_json")
    /** Ruhezeiten Mo–So: JSON { "1":[{"s":1320,"e":420},…], … } */
    private val quietHoursKey = stringPreferencesKey("quiet_hours_json")

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

    /** Reiche Nähe-Signale: Preview-Notification, Widget „gerade aktiv“, schnellerer Pulse. */
    val liveProximityRichFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[liveProximityRichKey] ?: true
    }

    /** Intensiv: Bildschirm kurz wecken wenn jemand malt (Default aus). */
    val liveProximityWakeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[liveProximityWakeKey] ?: false
    }

    /** Map Lobby-ID → Nähe-Impulse an (fehlend = aus). */
    val lobbyProximityFlow: Flow<Map<String, Boolean>> = context.dataStore.data.map { prefs ->
        parseLobbyProximity(prefs[lobbyProximityKey])
    }

    val quietHoursFlow: Flow<QuietHoursSchedule> = context.dataStore.data.map { prefs ->
        parseQuietHours(prefs[quietHoursKey])
    }

    data class LastPublicCanvas(
        val id: String,
        val nameLine: String,
        val imageUrl: String,
        val hostNickname: String
    )

    val lastPublicCanvasFlow: Flow<LastPublicCanvas?> = context.dataStore.data.map { prefs ->
        val id = prefs[lastPublicIdKey]?.takeIf { it.isNotBlank() } ?: return@map null
        LastPublicCanvas(
            id = id,
            nameLine = prefs[lastPublicNameKey].orEmpty().ifBlank { "Öffentliche Leinwand" },
            imageUrl = prefs[lastPublicImageKey].orEmpty(),
            hostNickname = prefs[lastPublicHostKey].orEmpty()
        )
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

    suspend fun saveSession(
        sessionToken: String,
        account: AccountInfo,
        applyNickname: Boolean = true
    ) {
        context.dataStore.edit {
            it[sessionTokenKey] = sessionToken
            it[accountJsonKey] = account.toJson()
            if (applyNickname) {
                it[nicknameKey] = account.nickname
                if (it[colorIndexKey].isNullOrBlank()) {
                    it[colorIndexKey] = PeerPalette.indexFor(account.nickname.lowercase()).toString()
                }
            }
        }
    }

    suspend fun clearNickname() {
        context.dataStore.edit { it.remove(nicknameKey) }
    }

    suspend fun updateAccount(account: AccountInfo) {
        context.dataStore.edit {
            it[accountJsonKey] = account.toJson()
            it[nicknameKey] = account.nickname
        }
    }

    /** Abmelden: Session weg, neues Geräte-Konto, Lobbys lokal leeren, Tutorial erneut. */
    suspend fun clearForLogout() {
        val id = UUID.randomUUID().toString()
        val secret = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        context.dataStore.edit {
            it.remove(sessionTokenKey)
            it.remove(accountJsonKey)
            it[installIdKey] = id
            it[installSecretKey] = secret
            it[lobbiesKey] = "[]"
            it.remove(activeLobbyKey)
            it[tutorialDoneKey] = false
            it.remove(nicknameKey)
        }
    }

    /**
     * Host-Lobbys vom Server übernehmen.
     * Beigetretene Lobbys (JOIN) bleiben; alte Host-Lobbys, die nicht mehr auf dem
     * Server hängen, werden entfernt — verhindert Geister-Lobbys nach Google-Login.
     */
    suspend fun replaceHostedLobbiesFromRemote(
        remote: List<com.luv.couple.net.RemoteLobby>
    ) {
        context.dataStore.edit { prefs ->
            val existing = parseLobbies(prefs[lobbiesKey])
            val joins = existing.filter { it.role == Role.JOIN }
            val byCode = joins.associateBy { it.code.uppercase() }.toMutableMap()
            for (r in remote) {
                val code = r.code.uppercase()
                val token = r.token ?: continue
                val prev = existing.firstOrNull { it.code.equals(code, ignoreCase = true) }
                byCode[code] = Lobby(
                    id = prev?.id ?: UUID.randomUUID().toString(),
                    name = r.name.ifBlank { prev?.name ?: "Lobby" }
                        .take(PeerPalette.MAX_LOBBY_NAME_LENGTH)
                        .ifBlank { "Lobby" },
                    role = Role.HOST,
                    code = code,
                    token = token,
                    invite = r.invite.ifBlank { prev?.invite.orEmpty() },
                    capacity = r.capacity,
                    isFree = r.isFree,
                    hostNickname = r.hostNickname.ifBlank { prev?.hostNickname.orEmpty() },
                    hostColorSide = r.hostColorSide
                )
            }
            val merged = byCode.values.toList()
            prefs[lobbiesKey] = encodeLobbies(merged)
            val active = prefs[activeLobbyKey]
            if (active.isNullOrBlank() || merged.none { it.id == active }) {
                if (merged.isNotEmpty()) prefs[activeLobbyKey] = merged.first().id
                else prefs.remove(activeLobbyKey)
            }
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

    /** true = noch nicht gemeldet (jetzt markieren), false = schon bekannt */
    suspend fun claimJoinAnnouncement(lobbyId: String, peerKey: String): Boolean {
        val lobby = lobbyId.trim()
        val peer = peerKey.trim().lowercase()
        if (lobby.isBlank() || peer.isBlank()) return false
        val token = "$lobby|$peer"
        var claimed = false
        context.dataStore.edit { prefs ->
            val arr = runCatching {
                JSONArray(prefs[joinAnnouncedKey] ?: "[]")
            }.getOrDefault(JSONArray())
            val existing = buildSet {
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
            if (token in existing) return@edit
            claimed = true
            val next = JSONArray()
            existing.forEach { next.put(it) }
            next.put(token)
            // Altlasten begrenzen
            while (next.length() > 200) next.remove(0)
            prefs[joinAnnouncedKey] = next.toString()
        }
        return claimed
    }

    suspend fun setLiveProximityRichEnabled(enabled: Boolean) {
        context.dataStore.edit { it[liveProximityRichKey] = enabled }
    }

    suspend fun setLiveProximityWakeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[liveProximityWakeKey] = enabled }
    }

    suspend fun setLastPublicCanvas(
        id: String,
        nameLine: String,
        imageUrl: String,
        hostNickname: String
    ) {
        val cleanId = id.trim()
        if (cleanId.isBlank()) return
        context.dataStore.edit { prefs ->
            prefs[lastPublicIdKey] = cleanId
            prefs[lastPublicNameKey] = nameLine.trim().take(48)
            prefs[lastPublicImageKey] = imageUrl.trim()
            prefs[lastPublicHostKey] = hostNickname.trim().take(18)
        }
    }

    suspend fun lastPublicCanvas(): LastPublicCanvas? {
        val prefs = context.dataStore.data.first()
        val id = prefs[lastPublicIdKey]?.takeIf { it.isNotBlank() } ?: return null
        return LastPublicCanvas(
            id = id,
            nameLine = prefs[lastPublicNameKey].orEmpty().ifBlank { "Öffentliche Leinwand" },
            imageUrl = prefs[lastPublicImageKey].orEmpty(),
            hostNickname = prefs[lastPublicHostKey].orEmpty()
        )
    }

    suspend fun isLiveProximityRichEnabled(): Boolean =
        context.dataStore.data.first()[liveProximityRichKey] ?: true

    suspend fun isLiveProximityWakeEnabled(): Boolean =
        context.dataStore.data.first()[liveProximityWakeKey] ?: false

    suspend fun isLobbyProximityEnabled(lobbyId: String): Boolean {
        val map = parseLobbyProximity(context.dataStore.data.first()[lobbyProximityKey])
        return map[lobbyId] == true
    }

    suspend fun setLobbyProximityEnabled(lobbyId: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val map = parseLobbyProximity(prefs[lobbyProximityKey]).toMutableMap()
            if (enabled) map[lobbyId] = true else map.remove(lobbyId)
            prefs[lobbyProximityKey] = encodeLobbyProximity(map)
        }
    }

    suspend fun wasMemorySeen(code: String, releasedAt: Long): Boolean {
        val raw = context.dataStore.data.first()[seenMemoriesKey].orEmpty()
        val key = "${code.uppercase()}:$releasedAt"
        return raw.contains(key)
    }

    suspend fun markMemorySeen(code: String, releasedAt: Long) {
        val key = "${code.uppercase()}:$releasedAt"
        context.dataStore.edit { prefs ->
            val prev = prefs[seenMemoriesKey].orEmpty()
            val parts = prev.split('|').filter { it.isNotBlank() }.toMutableList()
            if (key !in parts) parts.add(key)
            while (parts.size > 40) parts.removeAt(0)
            prefs[seenMemoriesKey] = parts.joinToString("|")
        }
    }

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

    suspend fun bumpPeakPeers(lobbyId: String, peers: Int) {
        if (peers <= 0) return
        context.dataStore.edit { prefs ->
            val list = parseLobbies(prefs[lobbiesKey]).toMutableList()
            val idx = list.indexOfFirst { it.id == lobbyId }
            if (idx < 0) return@edit
            val cur = list[idx]
            val next = peers.coerceAtLeast(cur.peakPeers)
            if (next == cur.peakPeers) return@edit
            list[idx] = cur.copy(peakPeers = next)
            prefs[lobbiesKey] = encodeLobbies(list)
        }
    }

    suspend fun upsertLobby(lobby: Lobby) {
        context.dataStore.edit { prefs ->
            val list = parseLobbies(prefs[lobbiesKey]).toMutableList()
            val idx = list.indexOfFirst { it.id == lobby.id }
            if (idx >= 0) {
                val prev = list[idx]
                list[idx] = lobby.copy(peakPeers = maxOf(prev.peakPeers, lobby.peakPeers, 1))
            } else {
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
        val clean = name.trim().take(PeerPalette.MAX_LOBBY_NAME_LENGTH).ifBlank { return }
        context.dataStore.edit { prefs ->
            val list = parseLobbies(prefs[lobbiesKey]).map {
                if (it.id == lobbyId) it.copy(name = clean) else it
            }
            prefs[lobbiesKey] = encodeLobbies(list)
        }
    }

    suspend fun updateLobbyHost(lobbyId: String, hostNickname: String, hostUserId: String?) {
        val nick = hostNickname.trim().take(18)
        context.dataStore.edit { prefs ->
            val myId = AccountInfo.fromJson(prefs[accountJsonKey])?.id
            val list = parseLobbies(prefs[lobbiesKey]).map { lobby ->
                if (lobby.id != lobbyId) return@map lobby
                val amHost = !hostUserId.isNullOrBlank() && hostUserId == myId
                lobby.copy(
                    hostNickname = nick.ifBlank { lobby.hostNickname },
                    role = when {
                        amHost -> Role.HOST
                        lobby.role == Role.HOST && !hostUserId.isNullOrBlank() && hostUserId != myId ->
                            Role.JOIN
                        else -> lobby.role
                    }
                )
            }
            prefs[lobbiesKey] = encodeLobbies(list)
        }
    }

    suspend fun setQuietHours(schedule: QuietHoursSchedule) {
        QuietHoursGate.update(schedule)
        context.dataStore.edit { prefs ->
            prefs[quietHoursKey] = encodeQuietHours(schedule)
        }
    }

    suspend fun quietHours(): QuietHoursSchedule {
        val prefs = context.dataStore.data.first()
        return parseQuietHours(prefs[quietHoursKey]).also { QuietHoursGate.update(it) }
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
            val prox = parseLobbyProximity(prefs[lobbyProximityKey]).toMutableMap()
            if (prox.remove(lobbyId) != null) {
                prefs[lobbyProximityKey] = encodeLobbyProximity(prox)
            }
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

    /** Explizite Widget-Zuordnung inkl. Lobby-Name (ohne Fallback). */
    suspend fun widgetLobby(widgetId: Int): Lobby? {
        val prefs = context.dataStore.data.first()
        val lobbies = parseLobbies(prefs[lobbiesKey])
        val mapped = parseWidgetMap(prefs[widgetMapKey])[widgetId]
        if (mapped != null) {
            lobbies.firstOrNull { it.id == mapped }?.let { return it }
        }
        val active = prefs[activeLobbyKey]
        return lobbies.firstOrNull { it.id == active } ?: lobbies.firstOrNull()
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
                                name = o.optString("name", "Lobby")
                                    .take(PeerPalette.MAX_LOBBY_NAME_LENGTH)
                                    .ifBlank { "Lobby" },
                                role = runCatching { Role.valueOf(o.getString("role")) }.getOrDefault(Role.HOST),
                                code = o.getString("code"),
                                token = o.getString("token"),
                                invite = o.optString("invite", "LUV-${o.getString("code")}"),
                                capacity = o.optInt("capacity", PeerPalette.FREE_LOBBY_START_CAPACITY),
                                isFree = o.optBoolean("isFree", false),
                                hostNickname = o.optString("hostNickname", ""),
                                hostColorSide = o.optString("hostColorSide", "blue").ifBlank { "blue" },
                                peakPeers = o.optInt("peakPeers", 1).coerceAtLeast(1)
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
                        .put("hostColorSide", lobby.hostColorSide)
                        .put("peakPeers", lobby.peakPeers.coerceAtLeast(1))
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

        fun parseLobbyProximity(raw: String?): Map<String, Boolean> {
            if (raw.isNullOrBlank()) return emptyMap()
            return runCatching {
                val o = JSONObject(raw)
                buildMap {
                    o.keys().forEach { key ->
                        if (o.optBoolean(key, false)) put(key, true)
                    }
                }
            }.getOrDefault(emptyMap())
        }

        fun encodeLobbyProximity(map: Map<String, Boolean>): String {
            val o = JSONObject()
            map.forEach { (k, v) -> if (v) o.put(k, true) }
            return o.toString()
        }

        fun parseQuietHours(raw: String?): QuietHoursSchedule {
            if (raw.isNullOrBlank()) return QuietHoursSchedule.EMPTY
            return runCatching {
                val o = JSONObject(raw)
                val byDay = mutableMapOf<Int, List<QuietWindow>>()
                for (day in 1..7) {
                    val arr = o.optJSONArray(day.toString()) ?: continue
                    val windows = buildList {
                        for (i in 0 until arr.length()) {
                            val w = arr.optJSONObject(i) ?: continue
                            val s = w.optInt("s", -1)
                            val e = w.optInt("e", -1)
                            if (s in 0 until QuietWindow.MINUTES_PER_DAY &&
                                e in 0 until QuietWindow.MINUTES_PER_DAY &&
                                s != e
                            ) {
                                add(QuietWindow(s, e))
                            }
                        }
                    }
                    if (windows.isNotEmpty()) byDay[day] = windows
                }
                QuietHoursSchedule(byDay)
            }.getOrDefault(QuietHoursSchedule.EMPTY)
        }

        fun encodeQuietHours(schedule: QuietHoursSchedule): String {
            val o = JSONObject()
            schedule.byDay.forEach { (day, windows) ->
                val arr = JSONArray()
                windows.forEach { w ->
                    arr.put(JSONObject().put("s", w.startMinutes).put("e", w.endMinutes))
                }
                if (arr.length() > 0) o.put(day.toString(), arr)
            }
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
    val clearCost: Int = 1,
    val lobbyCreateCost: Int = PeerPalette.LOBBY_CREATE_COST,
    val slotCost: Int = PeerPalette.SLOT_COST,
    val gameCost: Int = PeerPalette.GAME_COST,
    val canCreateFreeLobby: Boolean = true,
    val canClaimDaily: Boolean,
    val googleLinked: Boolean,
    /** Nur in der API-Antwort gesetzt — nicht persistieren. */
    val dailyGrantedJustNow: Boolean = false,
    val lastDailyGrantDate: String? = null
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
        .put("clearCost", clearCost)
        .put("lobbyCreateCost", lobbyCreateCost)
        .put("slotCost", slotCost)
        .put("gameCost", gameCost)
        .put("canCreateFreeLobby", canCreateFreeLobby)
        .put("canClaimDaily", canClaimDaily)
        .put("googleLinked", googleLinked)
        .put("lastDailyGrantDate", lastDailyGrantDate)
        .toString()

    companion object {
        fun fromJson(raw: String?): AccountInfo? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                fromApi(JSONObject(raw))
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
            clearCost = o.optInt("clearCost", 1),
            lobbyCreateCost = o.optInt("lobbyCreateCost", PeerPalette.LOBBY_CREATE_COST),
            slotCost = o.optInt("slotCost", PeerPalette.SLOT_COST),
            gameCost = o.optInt("gameCost", PeerPalette.GAME_COST),
            canCreateFreeLobby = o.optBoolean("canCreateFreeLobby", true),
            canClaimDaily = o.optBoolean("canClaimDaily", false),
            googleLinked = o.optBoolean("googleLinked", false),
            dailyGrantedJustNow = o.optBoolean("dailyGrantedJustNow", false),
            lastDailyGrantDate = o.optString("lastDailyGrantDate").takeIf { it.isNotBlank() }
        )
    }
}
