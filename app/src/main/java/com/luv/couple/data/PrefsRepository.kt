package com.luv.couple.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
    /** Zuletzt angezeigter Admin-Live-Hinweis (einmalig pro Gerät) */
    private val lastShownLiveNoticeKey = stringPreferencesKey("last_shown_live_notice_id")
    private val lastPublicIdKey = stringPreferencesKey("last_public_canvas_id")
    private val lastPublicNameKey = stringPreferencesKey("last_public_canvas_name")
    private val lastPublicImageKey = stringPreferencesKey("last_public_canvas_image")
    private val lastPublicHostKey = stringPreferencesKey("last_public_canvas_host")
    /** Bereits im Splash gezeigte öffentliche Bilder (IDs, | getrennt) */
    private val seenSplashIdsKey = stringPreferencesKey("seen_splash_canvas_ids")
    /** Bereits gemeldete Lobby-Beitritte: "lobbyId|userIdOrNick" */
    private val joinAnnouncedKey = stringPreferencesKey("join_announced_json")
    /** Ruhezeiten Mo–So: JSON { "1":[{"s":1320,"e":420},…], … } */
    private val quietHoursKey = stringPreferencesKey("quiet_hours_json")
    /** Reaktionsleiste: JSON-Array von Emoji-Strings */
    private val emojiBarKey = stringPreferencesKey("emoji_bar_json")
    /** Lokaler Cache Inventar-Emojis: JSON { "👍": 2, … } */
    private val ownedEmojisKey = stringPreferencesKey("owned_emojis_json")
    /** Besitzte Profil-Themes: JSON-Array von Theme-IDs */
    private val ownedThemesKey = stringPreferencesKey("owned_themes_json")
    /** Besitzte Profil-Sticker: JSON { "🐶": 1, … } */
    private val ownedStickersKey = stringPreferencesKey("owned_stickers_json")
    /** Besitzte Begleiter: JSON-Array von Emojis */
    private val ownedPetsKey = stringPreferencesKey("owned_pets_json")
    /** Ausgerüsteter Begleiter (Avatar) */
    private val equippedPetKey = stringPreferencesKey("equipped_pet")
    /** Pinseldicke auf der Leinwand (px) */
    private val brushWidthKey = floatPreferencesKey("brush_width")
    /** Profil-Leinwand JSON */
    private val profileCanvasKey = stringPreferencesKey("profile_canvas_json")
    /** Bewusst verlassene Lobby-Codes — Cloud-Sync darf sie nicht wieder einspielen */
    private val dismissedLobbiesKey = stringPreferencesKey("dismissed_lobby_codes_json")
    /** Zuletzt geöffnete Leinwand pro Lobby-Code → Glow zurücksetzen */
    private val lobbyCanvasSeenKey = stringPreferencesKey("lobby_canvas_seen_json")
    /** Glow-Pause bis Timestamp (ms) pro Lobby-Code */
    private val lobbyGlowSnoozeKey = stringPreferencesKey("lobby_glow_snooze_json")
    /** Bereits gesehene Inventar-Keys (kind:id) */
    private val inventorySeenKey = stringPreferencesKey("inventory_seen_ids_json")
    /** Noch nicht im Tab angeschauten neuen Items */
    private val inventoryUnseenKey = stringPreferencesKey("inventory_unseen_ids_json")
    /** Zuletzt bekannte Anzahl pending Markt-Verkäufe (für Push bei Anstieg) */
    private val pendingSalesKnownKey = intPreferencesKey("pending_sales_known_count")
    /** Lootbox: vor Kauf bestätigen (Standard an) */
    private val lootboxConfirmBuyKey = booleanPreferencesKey("lootbox_confirm_buy")

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

    val emojiBarFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        parseEmojiBar(prefs[emojiBarKey])
    }

    val ownedEmojisFlow: Flow<Map<String, Int>> = context.dataStore.data.map { prefs ->
        parseOwnedEmojis(prefs[ownedEmojisKey])
    }

    val ownedThemesFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        parseOwnedThemes(prefs[ownedThemesKey])
    }

    val ownedStickersFlow: Flow<Map<String, Int>> = context.dataStore.data.map { prefs ->
        parseOwnedStickers(prefs[ownedStickersKey])
    }

    val ownedPetsFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        parseOwnedPets(prefs[ownedPetsKey])
    }

    val equippedPetFlow: Flow<String> = context.dataStore.data.map { prefs ->
        parseEquippedPet(prefs[equippedPetKey], prefs[ownedPetsKey])
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

    suspend fun brushWidth(): Float =
        (context.dataStore.data.first()[brushWidthKey] ?: 18f).coerceIn(6f, 40f)

    suspend fun setBrushWidth(width: Float) {
        context.dataStore.edit {
            it[brushWidthKey] = width.coerceIn(6f, 40f)
        }
        mirrorSettingsToCloud()
    }

    suspend fun profileCanvasJson(): String? =
        context.dataStore.data.first()[profileCanvasKey]

    suspend fun setProfileCanvasJson(json: String) {
        context.dataStore.edit { it[profileCanvasKey] = json }
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
        replaceCloudLobbiesFromRemote(
            hosted = remote,
            joined = emptyList(),
            dropUnknownJoins = false
        )
    }

    /**
     * Host- und Join-Lobbys vom Server spiegeln (Google-Konto / Multi-Gerät).
     * [dropUnknownJoins]=true entfernt lokale Joins, die der Server nicht mehr kennt.
     * Lokal verlassene Codes ([dismissedLobbiesKey]) werden nicht wieder eingefügt.
     */
    suspend fun replaceCloudLobbiesFromRemote(
        hosted: List<com.luv.couple.net.RemoteLobby>,
        joined: List<com.luv.couple.net.RemoteLobby>,
        dropUnknownJoins: Boolean = true
    ) {
        context.dataStore.edit { prefs ->
            val existing = parseLobbies(prefs[lobbiesKey])
            val dismissed = parseDismissedLobbyCodes(prefs[dismissedLobbiesKey])
            val byCode = linkedMapOf<String, Lobby>()
            if (!dropUnknownJoins) {
                for (j in existing.filter { it.role == Role.JOIN }) {
                    val code = j.code.uppercase()
                    if (code in dismissed) continue
                    byCode[code] = j
                }
            }
            fun upsert(r: com.luv.couple.net.RemoteLobby, role: Role) {
                val code = r.code.uppercase()
                if (code in dismissed) return
                val token = r.token ?: return
                val prev = existing.firstOrNull { it.code.equals(code, ignoreCase = true) }
                    ?: byCode[code]
                byCode[code] = Lobby(
                    id = prev?.id ?: UUID.randomUUID().toString(),
                    name = r.name.ifBlank { prev?.name ?: "Lobby" }
                        .take(PeerPalette.MAX_LOBBY_NAME_LENGTH)
                        .ifBlank { "Lobby" },
                    role = role,
                    code = code,
                    token = token,
                    invite = r.invite.ifBlank { prev?.invite.orEmpty() },
                    capacity = r.capacity,
                    isFree = r.isFree,
                    isRandom = r.isRandom || prev?.isRandom == true,
                    isWedding = r.isWedding || prev?.isWedding == true,
                    hostNickname = r.hostNickname.ifBlank { prev?.hostNickname.orEmpty() },
                    hostColorSide = r.hostColorSide,
                    peakPeers = prev?.peakPeers ?: 1,
                    lastCanvasAt = r.lastCanvasAt.takeIf { it > 0 } ?: (prev?.lastCanvasAt ?: 0L),
                    lastCanvasActorId = r.lastCanvasActorId
                        ?: prev?.lastCanvasActorId
                )
            }
            for (r in hosted) upsert(r, Role.HOST)
            for (r in joined) {
                val code = r.code.uppercase()
                if (byCode[code]?.role == Role.HOST) continue
                upsert(r, Role.JOIN)
            }
            val seen = linkedSetOf<String>()
            val merged = buildList {
                for (old in existing) {
                    val code = old.code.uppercase()
                    val next = byCode[code] ?: continue
                    if (seen.add(code)) add(next)
                }
                for ((code, lobby) in byCode) {
                    if (seen.add(code)) add(lobby)
                }
            }
            prefs[lobbiesKey] = encodeLobbies(merged)
            val active = prefs[activeLobbyKey]
            if (active.isNullOrBlank() || merged.none { it.id == active }) {
                if (merged.isNotEmpty()) prefs[activeLobbyKey] = merged.first().id
                else prefs.remove(activeLobbyKey)
            }
        }
    }

    suspend fun buildCloudSettings(): com.luv.couple.net.CloudSettings {
        persistLobbyProximityMigration()
        val prefs = context.dataStore.data.first()
        val lobbies = parseLobbies(prefs[lobbiesKey])
        val prox = migrateLobbyProximityKeys(
            parseLobbyProximity(prefs[lobbyProximityKey]),
            lobbies
        )
        return com.luv.couple.net.CloudSettings(
            quietHours = parseQuietHours(prefs[quietHoursKey]),
            emojiBar = parseEmojiBar(prefs[emojiBarKey]),
            partnerDrawNotify = prefs[partnerNotifyKey] ?: true,
            partnerHaptic = prefs[partnerHapticKey] ?: true,
            liveProximityRich = prefs[liveProximityRichKey] ?: true,
            liveProximityWake = prefs[liveProximityWakeKey] ?: false,
            lobbyProximity = prox,
            brushWidth = (prefs[brushWidthKey] ?: 18f).coerceIn(6f, 40f),
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun applySettingsBlob(
        settings: com.luv.couple.net.CloudSettings,
        skipMirror: Boolean = true
    ) {
        QuietHoursGate.update(settings.quietHours)
        context.dataStore.edit { prefs ->
            val lobbies = parseLobbies(prefs[lobbiesKey])
            val localProx = migrateLobbyProximityKeys(
                parseLobbyProximity(prefs[lobbyProximityKey]),
                lobbies
            )
            val remoteProx = migrateLobbyProximityKeys(settings.lobbyProximity, lobbies)
            // Alte Cloud-UUIDs wirken wie „leer“ — lokale Codes behalten. Sonst Cloud ist Quelle.
            val remoteLooksLikeLegacyUuids =
                settings.lobbyProximity.isNotEmpty() && remoteProx.isEmpty()
            val finalProx = if (remoteLooksLikeLegacyUuids) localProx else remoteProx
            prefs[quietHoursKey] = encodeQuietHours(settings.quietHours)
            if (settings.emojiBar.isNotEmpty()) {
                prefs[emojiBarKey] = JSONArray(settings.emojiBar).toString()
            }
            prefs[partnerNotifyKey] = settings.partnerDrawNotify
            prefs[partnerHapticKey] = settings.partnerHaptic
            prefs[liveProximityRichKey] = settings.liveProximityRich
            prefs[liveProximityWakeKey] = settings.liveProximityWake
            prefs[lobbyProximityKey] = encodeLobbyProximity(finalProx)
            prefs[brushWidthKey] = settings.brushWidth.coerceIn(6f, 40f)
        }
        if (!skipMirror) mirrorSettingsToCloud()
    }

    /** Alte Glocken-Keys (Lobby-UUID) → Invite-Code. */
    suspend fun persistLobbyProximityMigration(): Boolean {
        var changed = false
        context.dataStore.edit { prefs ->
            val lobbies = parseLobbies(prefs[lobbiesKey])
            val old = parseLobbyProximity(prefs[lobbyProximityKey])
            val next = migrateLobbyProximityKeys(old, lobbies)
            if (next != old) {
                prefs[lobbyProximityKey] = encodeLobbyProximity(next)
                changed = true
            }
        }
        return changed
    }

    private suspend fun mirrorSettingsToCloud() {
        if (com.luv.couple.net.LuvApiClient.sessionToken.isNullOrBlank()) return
        runCatching {
            com.luv.couple.net.LuvApiClient.putSettings(buildCloudSettings())
        }
    }

    suspend fun lastNotifiedUpdateCode(): Int =
        context.dataStore.data.first()[lastNotifiedUpdateKey] ?: 0

    suspend fun setLastNotifiedUpdateCode(code: Int) {
        context.dataStore.edit { it[lastNotifiedUpdateKey] = code }
    }

    suspend fun lastShownLiveNoticeId(): String? =
        context.dataStore.data.first()[lastShownLiveNoticeKey]?.takeIf { it.isNotBlank() }

    suspend fun setLastShownLiveNoticeId(id: String) {
        val clean = id.trim()
        if (clean.isBlank()) return
        context.dataStore.edit { it[lastShownLiveNoticeKey] = clean }
    }

    suspend fun sessionToken(): String? =
        context.dataStore.data.first()[sessionTokenKey]

    suspend fun setPartnerDrawNotifyEnabled(enabled: Boolean) {
        context.dataStore.edit { it[partnerNotifyKey] = enabled }
        mirrorSettingsToCloud()
    }

    suspend fun setPartnerHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[partnerHapticKey] = enabled }
        mirrorSettingsToCloud()
    }

    suspend fun isPartnerDrawNotifyEnabled(): Boolean =
        context.dataStore.data.first()[partnerNotifyKey] ?: true

    suspend fun isPartnerHapticEnabled(): Boolean =
        context.dataStore.data.first()[partnerHapticKey] ?: true

    val lootboxConfirmBuyFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[lootboxConfirmBuyKey] ?: true
    }

    suspend fun setLootboxConfirmBuy(enabled: Boolean) {
        context.dataStore.edit { it[lootboxConfirmBuyKey] = enabled }
    }

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
        mirrorSettingsToCloud()
    }

    suspend fun setLiveProximityWakeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[liveProximityWakeKey] = enabled }
        mirrorSettingsToCloud()
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

    suspend fun isLobbyProximityEnabled(lobbyIdOrCode: String): Boolean {
        val prefs = context.dataStore.data.first()
        val lobbies = parseLobbies(prefs[lobbiesKey])
        val map = migrateLobbyProximityKeys(
            parseLobbyProximity(prefs[lobbyProximityKey]),
            lobbies
        )
        val code = resolveLobbyCode(lobbyIdOrCode, lobbies) ?: return false
        return map[code] == true
    }

    /** Glocke / Nähe-Impulse pro Lobby — Key ist der Invite-Code (cloud-stabil). */
    suspend fun setLobbyProximityEnabled(lobbyIdOrCode: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val lobbies = parseLobbies(prefs[lobbiesKey])
            val code = resolveLobbyCode(lobbyIdOrCode, lobbies) ?: return@edit
            val map = migrateLobbyProximityKeys(
                parseLobbyProximity(prefs[lobbyProximityKey]),
                lobbies
            ).toMutableMap()
            // Alt-Keys (UUID) für diese Lobby entfernen
            lobbies.filter { normalizeLobbyCode(it.code) == code }.forEach { map.remove(it.id) }
            map.remove(lobbyIdOrCode)
            if (enabled) map[code] = true else map.remove(code)
            prefs[lobbyProximityKey] = encodeLobbyProximity(map)
        }
        mirrorSettingsToCloud()
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

    suspend fun seenSplashIds(): Set<String> {
        val raw = context.dataStore.data.first()[seenSplashIdsKey].orEmpty()
        return raw.split('|').map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    suspend fun markSplashSeen(id: String) {
        val clean = id.trim()
        if (clean.isBlank()) return
        context.dataStore.edit { prefs ->
            val prev = prefs[seenSplashIdsKey].orEmpty()
            val parts = prev.split('|').map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
            if (clean !in parts) parts.add(clean)
            while (parts.size > 200) parts.removeAt(0)
            prefs[seenSplashIdsKey] = parts.joinToString("|")
        }
    }

    /** Seen-Liste leeren (wenn Server alle Bilder durchhat → neuer Zyklus). */
    suspend fun clearSeenSplashIds() {
        context.dataStore.edit { it.remove(seenSplashIdsKey) }
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

    /** Peer hat gezeichnet → Home-Kachel kann glühen, bis die Leinwand geöffnet wird. */
    suspend fun bumpLobbyLastCanvasAt(
        lobbyId: String,
        at: Long = System.currentTimeMillis(),
        actorUserId: String? = null
    ) {
        if (lobbyId.isBlank() || at <= 0L) return
        context.dataStore.edit { prefs ->
            val list = parseLobbies(prefs[lobbiesKey]).toMutableList()
            val idx = list.indexOfFirst { it.id == lobbyId }
            if (idx < 0) return@edit
            val cur = list[idx]
            if (at < cur.lastCanvasAt) return@edit
            list[idx] = cur.copy(
                lastCanvasAt = at,
                lastCanvasActorId = actorUserId?.takeIf { it.isNotBlank() }
                    ?: cur.lastCanvasActorId
            )
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
            // Neu beigetreten / erstellt → Dismiss-Sperre für diesen Code aufheben
            val code = lobby.code.uppercase()
            if (code.isNotBlank()) {
                val dismissed = parseDismissedLobbyCodes(prefs[dismissedLobbiesKey]).toMutableSet()
                if (dismissed.remove(code)) {
                    prefs[dismissedLobbiesKey] = encodeDismissedLobbyCodes(dismissed)
                }
            }
            // Legacy cleanup
            prefs.remove(tokenKey)
            prefs.remove(inviteCodeKey)
            prefs.remove(pairedKey)
            prefs.remove(roleKey)
        }
    }

    /** Lobby-Code nach Verlassen sperren, damit Cloud-Sync sie nicht zurückholt. */
    suspend fun dismissLobbyCode(code: String) {
        val clean = code.trim().uppercase().removePrefix("LUV-")
        if (clean.length < 3) return
        context.dataStore.edit { prefs ->
            val set = parseDismissedLobbyCodes(prefs[dismissedLobbiesKey]).toMutableSet()
            if (set.add(clean)) {
                while (set.size > 80) {
                    set.remove(set.first())
                }
                prefs[dismissedLobbiesKey] = encodeDismissedLobbyCodes(set)
            }
        }
    }

    val lobbyCanvasSeenFlow: Flow<Map<String, Long>> = context.dataStore.data.map { prefs ->
        parseLongMap(prefs[lobbyCanvasSeenKey])
    }

    suspend fun markLobbyCanvasSeen(code: String, at: Long = System.currentTimeMillis()) {
        val clean = code.trim().uppercase().removePrefix("LUV-")
        if (clean.length < 3) return
        context.dataStore.edit { prefs ->
            val map = parseLongMap(prefs[lobbyCanvasSeenKey]).toMutableMap()
            map[clean] = at
            while (map.size > 40) {
                val oldest = map.minByOrNull { it.value }?.key ?: break
                map.remove(oldest)
            }
            prefs[lobbyCanvasSeenKey] = encodeLongMap(map)
        }
    }

    val lobbyGlowSnoozeFlow: Flow<Map<String, Long>> = context.dataStore.data.map { prefs ->
        parseLongMap(prefs[lobbyGlowSnoozeKey])
    }

    /** Glow auf Lobby-Kachel 5 Minuten pausieren (nach Betreten der Leinwand). */
    suspend fun snoozeLobbyGlow(code: String, durationMs: Long = 5 * 60_000L) {
        val clean = code.trim().uppercase().removePrefix("LUV-")
        if (clean.length < 3) return
        val until = System.currentTimeMillis() + durationMs
        context.dataStore.edit { prefs ->
            val map = parseLongMap(prefs[lobbyGlowSnoozeKey]).toMutableMap()
            map[clean] = until
            val now = System.currentTimeMillis()
            map.entries.removeAll { it.value < now }
            while (map.size > 40) {
                val oldest = map.minByOrNull { it.value }?.key ?: break
                map.remove(oldest)
            }
            prefs[lobbyGlowSnoozeKey] = encodeLongMap(map)
        }
    }

    val inventoryUnseenFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        parseStringSet(prefs[inventoryUnseenKey])
    }

    suspend fun inventoryUnseenIds(): Set<String> {
        val prefs = context.dataStore.data.first()
        return parseStringSet(prefs[inventoryUnseenKey])
    }

    suspend fun markInventoryKindSeen(kindPrefix: String) {
        context.dataStore.edit { prefs ->
            val unseen = parseStringSet(prefs[inventoryUnseenKey]).toMutableSet()
            val seen = parseStringSet(prefs[inventorySeenKey]).toMutableSet()
            val moving = unseen.filter { it.startsWith("$kindPrefix:") }.toSet()
            unseen.removeAll(moving)
            seen.addAll(moving)
            prefs[inventoryUnseenKey] = encodeStringSet(unseen)
            prefs[inventorySeenKey] = encodeStringSet(seen)
        }
    }

    suspend fun markAllInventorySeen(currentKeys: Set<String>) {
        context.dataStore.edit { prefs ->
            val seen = parseStringSet(prefs[inventorySeenKey]).toMutableSet()
            seen.addAll(currentKeys)
            prefs[inventorySeenKey] = encodeStringSet(seen)
            prefs[inventoryUnseenKey] = encodeStringSet(emptySet())
        }
    }

    suspend fun pendingSalesKnownCount(): Int =
        context.dataStore.data.first()[pendingSalesKnownKey] ?: 0

    suspend fun setPendingSalesKnownCount(count: Int) {
        context.dataStore.edit { prefs ->
            prefs[pendingSalesKnownKey] = count.coerceAtLeast(0)
        }
    }

    private fun parseLongMap(raw: String?): Map<String, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            buildMap {
                for (key in o.keys()) {
                    val v = o.optLong(key, 0L)
                    if (v > 0L) put(key.uppercase(), v)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeLongMap(map: Map<String, Long>): String {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        return o.toString()
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

    /** Reihenfolge der Lobby-Kacheln im Hauptmenü speichern. */
    suspend fun reorderLobbies(orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        context.dataStore.edit { prefs ->
            val list = parseLobbies(prefs[lobbiesKey])
            if (list.size <= 1) return@edit
            val byId = list.associateBy { it.id }
            val next = buildList {
                for (id in orderedIds) {
                    byId[id]?.let { add(it) }
                }
                for (lobby in list) {
                    if (none { it.id == lobby.id }) add(lobby)
                }
            }
            if (next.map { it.id } == list.map { it.id }) return@edit
            prefs[lobbiesKey] = encodeLobbies(next)
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
        mirrorSettingsToCloud()
    }

    suspend fun quietHours(): QuietHoursSchedule {
        val prefs = context.dataStore.data.first()
        return parseQuietHours(prefs[quietHoursKey]).also { QuietHoursGate.update(it) }
    }

    suspend fun emojiBar(): List<String> {
        val prefs = context.dataStore.data.first()
        return parseEmojiBar(prefs[emojiBarKey])
    }

    suspend fun setEmojiBar(emojis: List<String>) {
        val max = com.luv.couple.shop.ShopCatalog.MAX_BAR
        val clean = emojis.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(max)
        context.dataStore.edit { prefs ->
            prefs[emojiBarKey] = JSONArray(clean).toString()
        }
        mirrorSettingsToCloud()
    }

    suspend fun ownedEmojis(): Map<String, Int> {
        val prefs = context.dataStore.data.first()
        return parseOwnedEmojis(prefs[ownedEmojisKey])
    }

    suspend fun setOwnedEmojis(map: Map<String, Int>) {
        context.dataStore.edit { prefs ->
            val o = JSONObject()
            map.forEach { (k, v) ->
                if (k.isNotBlank() && v > 0) o.put(k, v)
            }
            prefs[ownedEmojisKey] = o.toString()
        }
    }

    suspend fun ownedThemes(): List<String> {
        val prefs = context.dataStore.data.first()
        return parseOwnedThemes(prefs[ownedThemesKey])
    }

    suspend fun ownedStickers(): Map<String, Int> {
        val prefs = context.dataStore.data.first()
        return parseOwnedStickers(prefs[ownedStickersKey])
    }

    suspend fun ownedPets(): List<String> {
        val prefs = context.dataStore.data.first()
        return parseOwnedPets(prefs[ownedPetsKey])
    }

    suspend fun equippedPet(): String {
        val prefs = context.dataStore.data.first()
        return parseEquippedPet(prefs[equippedPetKey], prefs[ownedPetsKey])
    }

    suspend fun setEquippedPet(emoji: String) {
        val pet = emoji.trim().take(32).ifBlank {
            com.luv.couple.shop.ShopCatalog.DEFAULT_PET
        }
        context.dataStore.edit { prefs ->
            prefs[equippedPetKey] = pet
        }
    }

    /** Volles Inventar vom Server in den lokalen Cache schreiben. */
    suspend fun applyInventoryBag(
        emojis: Map<String, Int>,
        themes: List<String>,
        stickers: Map<String, Int>,
        pets: List<String>,
        equippedPet: String
    ) {
        context.dataStore.edit { prefs ->
            val eo = JSONObject()
            emojis.forEach { (k, v) ->
                if (k.isNotBlank() && v > 0) eo.put(k, v)
            }
            prefs[ownedEmojisKey] = eo.toString()
            prefs[ownedThemesKey] = JSONArray(
                themes.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            ).toString()
            val so = JSONObject()
            stickers.forEach { (k, v) ->
                if (k.isNotBlank() && v > 0) so.put(k, v)
            }
            prefs[ownedStickersKey] = so.toString()
            val petList = pets.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                .ifEmpty { listOf(com.luv.couple.shop.ShopCatalog.DEFAULT_PET) }
            prefs[ownedPetsKey] = JSONArray(petList).toString()
            val eq = equippedPet.trim().take(32)
            prefs[equippedPetKey] =
                if (eq.isNotBlank() && petList.contains(eq)) eq
                else com.luv.couple.shop.ShopCatalog.DEFAULT_PET

            val currentKeys = buildSet {
                stickers.forEach { (k, v) -> if (k.isNotBlank() && v > 0) add("stickers:$k") }
                themes.map { it.trim() }.filter { it.isNotBlank() }.forEach { add("themes:$it") }
                petList.forEach { add("pets:$it") }
                emojis.forEach { (k, v) -> if (k.isNotBlank() && v > 0) add("emojis:$k") }
            }
            val seen = parseStringSet(prefs[inventorySeenKey])
            val prevUnseen = parseStringSet(prefs[inventoryUnseenKey])
            if (seen.isEmpty() && prevUnseen.isEmpty()) {
                prefs[inventorySeenKey] = encodeStringSet(currentKeys)
                prefs[inventoryUnseenKey] = encodeStringSet(emptySet())
            } else {
                val fresh = currentKeys - seen
                val still = prevUnseen.intersect(currentKeys)
                prefs[inventoryUnseenKey] = encodeStringSet(fresh + still)
            }
        }
    }

    private fun parseStringSet(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) {
                    arr.optString(i).trim().takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }.getOrDefault(emptySet())
    }

    private fun encodeStringSet(set: Set<String>): String =
        JSONArray(set.toList().sorted()).toString()

    suspend fun removeLobby(lobbyId: String) {
        context.dataStore.edit { prefs ->
            val before = parseLobbies(prefs[lobbiesKey])
            val removed = before.firstOrNull { it.id == lobbyId }
            val list = before.filterNot { it.id == lobbyId }
            prefs[lobbiesKey] = encodeLobbies(list)
            if (prefs[activeLobbyKey] == lobbyId) {
                if (list.isEmpty()) prefs.remove(activeLobbyKey)
                else prefs[activeLobbyKey] = list.first().id
            }
            val map = parseWidgetMap(prefs[widgetMapKey]).filterValues { it != lobbyId }
            prefs[widgetMapKey] = encodeWidgetMap(map)
            val code = removed?.code?.let { normalizeLobbyCode(it) }.orEmpty()
            val prox = parseLobbyProximity(prefs[lobbyProximityKey]).toMutableMap()
            var proxDirty = false
            if (prox.remove(lobbyId) != null) proxDirty = true
            if (code.length >= 3 && prox.remove(code) != null) proxDirty = true
            if (proxDirty) prefs[lobbyProximityKey] = encodeLobbyProximity(prox)
            if (code.length >= 3) {
                val set = parseDismissedLobbyCodes(prefs[dismissedLobbiesKey]).toMutableSet()
                if (set.add(code)) {
                    while (set.size > 80) set.remove(set.first())
                    prefs[dismissedLobbiesKey] = encodeDismissedLobbyCodes(set)
                }
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
        fun parseQuietHoursPublic(raw: String?): QuietHoursSchedule = parseQuietHours(raw)

        fun encodeQuietHoursPublic(schedule: QuietHoursSchedule): String =
            encodeQuietHours(schedule)

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
                                isRandom = o.optBoolean("isRandom", false),
                                isWedding = o.optBoolean("isWedding", false),
                                hostNickname = o.optString("hostNickname", ""),
                                hostColorSide = o.optString("hostColorSide", "blue").ifBlank { "blue" },
                                peakPeers = o.optInt("peakPeers", 1).coerceAtLeast(1),
                                lastCanvasAt = o.optLong("lastCanvasAt", 0L),
                                lastCanvasActorId = o.optString("lastCanvasActorId")
                                    .takeIf { it.isNotBlank() }
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
                        .put("isRandom", lobby.isRandom)
                        .put("isWedding", lobby.isWedding)
                        .put("hostNickname", lobby.hostNickname)
                        .put("hostColorSide", lobby.hostColorSide)
                        .put("peakPeers", lobby.peakPeers.coerceAtLeast(1))
                        .put("lastCanvasAt", lobby.lastCanvasAt)
                        .put("lastCanvasActorId", lobby.lastCanvasActorId ?: "")
                )
            }
            return arr.toString()
        }

        fun parseDismissedLobbyCodes(raw: String?): Set<String> {
            if (raw.isNullOrBlank()) return emptySet()
            return runCatching {
                val arr = JSONArray(raw)
                buildSet {
                    for (i in 0 until arr.length()) {
                        arr.optString(i)
                            .trim()
                            .uppercase()
                            .removePrefix("LUV-")
                            .takeIf { it.length >= 3 }
                            ?.let { add(it) }
                    }
                }
            }.getOrDefault(emptySet())
        }

        fun encodeDismissedLobbyCodes(codes: Set<String>): String {
            val arr = JSONArray()
            codes.forEach { arr.put(it) }
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

        fun normalizeLobbyCode(raw: String): String =
            raw.trim().uppercase().removePrefix("LUV-")

        fun resolveLobbyCode(lobbyIdOrCode: String, lobbies: List<Lobby>): String? {
            val raw = lobbyIdOrCode.trim()
            if (raw.isBlank()) return null
            lobbies.firstOrNull { it.id == raw }?.let {
                return normalizeLobbyCode(it.code).takeIf { c -> c.length >= 3 }
            }
            val code = normalizeLobbyCode(raw)
            if (code.length in 3..16 && code.all { it.isLetterOrDigit() }) return code
            return null
        }

        /** UUID-Keys → Invite-Code; orphante UUIDs ohne passende Lobby fallen weg. */
        fun migrateLobbyProximityKeys(
            map: Map<String, Boolean>,
            lobbies: List<Lobby>
        ): Map<String, Boolean> {
            if (map.isEmpty()) return emptyMap()
            val byId = lobbies.associateBy { it.id }
            val out = linkedMapOf<String, Boolean>()
            for ((k, v) in map) {
                if (!v) continue
                val code = resolveLobbyCode(k, lobbies)
                    ?: byId[k]?.let { normalizeLobbyCode(it.code) }
                if (!code.isNullOrBlank() && code.length in 3..16) {
                    out[code] = true
                }
            }
            return out
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

        fun parseEmojiBar(raw: String?): List<String> {
            val max = com.luv.couple.shop.ShopCatalog.MAX_BAR
            if (raw.isNullOrBlank()) return com.luv.couple.shop.ShopCatalog.DEFAULT_BAR.take(max)
            return runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        arr.optString(i).trim().takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }.distinct().take(max).ifEmpty { com.luv.couple.shop.ShopCatalog.DEFAULT_BAR.take(max) }
            }.getOrDefault(com.luv.couple.shop.ShopCatalog.DEFAULT_BAR.take(max))
        }

        fun parseOwnedEmojis(raw: String?): Map<String, Int> {
            val starter = com.luv.couple.shop.ShopCatalog.DEFAULT_BAR.associateWith { 1 }.toMutableMap()
            if (raw.isNullOrBlank()) return starter
            return runCatching {
                val o = JSONObject(raw)
                o.keys().forEach { key ->
                    val n = o.optInt(key, 0)
                    if (key.isNotBlank() && n > 0) starter[key] = n
                }
                starter
            }.getOrDefault(starter)
        }

        fun parseOwnedThemes(raw: String?): List<String> {
            val starter = listOf(com.luv.couple.profile.ProfileCatalog.DEFAULT_THEME_ID)
            if (raw.isNullOrBlank()) return starter
            return runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        arr.optString(i).trim().takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }.distinct().ifEmpty { starter }
            }.getOrDefault(starter)
        }

        fun parseOwnedStickers(raw: String?): Map<String, Int> {
            if (raw.isNullOrBlank()) return emptyMap()
            return runCatching {
                val o = JSONObject(raw)
                buildMap {
                    o.keys().forEach { key ->
                        val n = o.optInt(key, 0)
                        if (key.isNotBlank() && n > 0) put(key, n)
                    }
                }
            }.getOrDefault(emptyMap())
        }

        fun parseOwnedPets(raw: String?): List<String> {
            val starter = listOf(com.luv.couple.shop.ShopCatalog.DEFAULT_PET)
            if (raw.isNullOrBlank()) return starter
            return runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        arr.optString(i).trim().takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }.distinct().ifEmpty { starter }
            }.getOrDefault(starter)
        }

        fun parseEquippedPet(equippedRaw: String?, petsRaw: String?): String {
            val pets = parseOwnedPets(petsRaw)
            val eq = equippedRaw?.trim().orEmpty()
            return if (eq.isNotBlank() && pets.contains(eq)) eq
            else com.luv.couple.shop.ShopCatalog.DEFAULT_PET
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
    val lastDailyGrantDate: String? = null,
    val isStaff: Boolean = false,
    val permissions: Set<String> = emptySet(),
    val googleEmail: String? = null
) {
    val isAdmin: Boolean get() = role == "admin"
    val isModerator: Boolean get() = role == "mod"
    fun hasPerm(id: String): Boolean = isAdmin || id in permissions

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
        .put("isStaff", isStaff || isAdmin || isModerator)
        .put("permissions", org.json.JSONArray(permissions.toList()))
        .put("googleEmail", googleEmail)
        .toString()

    companion object {
        fun fromJson(raw: String?): AccountInfo? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                fromApi(JSONObject(raw))
            }.getOrNull()
        }

        fun fromApi(o: JSONObject): AccountInfo {
            val role = o.optString("role", "user")
            val perms = buildSet {
                val arr = o.optJSONObject("permissions")
                if (arr != null) {
                    arr.keys().forEach { key ->
                        if (arr.optBoolean(key, false)) add(key)
                    }
                } else {
                    val list = o.optJSONArray("permissions")
                    if (list != null) {
                        for (i in 0 until list.length()) {
                            list.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    }
                }
            }
            val staff = o.optBoolean("isStaff", false) ||
                role == "admin" || role == "mod"
            return AccountInfo(
                id = o.getString("id"),
                nickname = o.optString("nickname", "Luv"),
                coins = o.optInt("coins", 0),
                role = role,
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
                lastDailyGrantDate = o.optString("lastDailyGrantDate").takeIf { it.isNotBlank() },
                isStaff = staff,
                permissions = perms,
                googleEmail = o.optString("googleEmail").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }
}
