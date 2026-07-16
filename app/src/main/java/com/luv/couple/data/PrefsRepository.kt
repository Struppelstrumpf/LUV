package com.luv.couple.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("luv_prefs")

class PrefsRepository(private val context: Context) {
    private val genderKey = stringPreferencesKey("gender")
    private val roleKey = stringPreferencesKey("role")
    private val tokenKey = stringPreferencesKey("token")
    private val peerIpKey = stringPreferencesKey("peer_ip")
    private val peerPortKey = stringPreferencesKey("peer_port")
    private val pairedKey = stringPreferencesKey("paired")
    private val inviteCodeKey = stringPreferencesKey("invite_code")
    private val partnerNotifyKey = booleanPreferencesKey("partner_draw_notify")
    private val partnerHapticKey = booleanPreferencesKey("partner_draw_haptic")
    private val lastClearDayKey = stringPreferencesKey("last_clear_day")

    val genderFlow: Flow<Gender?> = context.dataStore.data.map { prefs ->
        prefs[genderKey]?.let { runCatching { Gender.valueOf(it) }.getOrNull() }
    }

    val roleFlow: Flow<Role?> = context.dataStore.data.map { prefs ->
        prefs[roleKey]?.let { runCatching { Role.valueOf(it) }.getOrNull() }
    }

    val pairedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[pairedKey] == "true"
    }

    val inviteCodeFlow: Flow<String?> = context.dataStore.data.map { it[inviteCodeKey] }

    val partnerDrawNotifyFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[partnerNotifyKey] ?: true
    }

    val partnerHapticFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[partnerHapticKey] ?: true
    }

    suspend fun setGender(gender: Gender) {
        context.dataStore.edit { it[genderKey] = gender.name }
    }

    suspend fun setRole(role: Role) {
        context.dataStore.edit { it[roleKey] = role.name }
    }

    suspend fun setPartnerDrawNotifyEnabled(enabled: Boolean) {
        context.dataStore.edit { it[partnerNotifyKey] = enabled }
    }

    suspend fun setPartnerHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[partnerHapticKey] = enabled }
    }

    suspend fun isPartnerDrawNotifyEnabled(): Boolean {
        return context.dataStore.data.first()[partnerNotifyKey] ?: true
    }

    suspend fun isPartnerHapticEnabled(): Boolean {
        return context.dataStore.data.first()[partnerHapticKey] ?: true
    }

    suspend fun lastClearDay(): String? = context.dataStore.data.first()[lastClearDayKey]

    suspend fun setLastClearDay(day: String) {
        context.dataStore.edit { it[lastClearDayKey] = day }
    }

    suspend fun savePairing(
        role: Role,
        token: String,
        inviteCode: String,
        peerIp: String = "api",
        peerPort: Int = 0
    ) {
        context.dataStore.edit { prefs ->
            prefs[roleKey] = role.name
            prefs[tokenKey] = token
            prefs[peerIpKey] = peerIp
            prefs[peerPortKey] = peerPort.toString()
            prefs[inviteCodeKey] = inviteCode
            prefs[pairedKey] = "true"
        }
    }

    suspend fun clearPairing() {
        context.dataStore.edit { prefs ->
            prefs.remove(tokenKey)
            prefs.remove(peerIpKey)
            prefs.remove(peerPortKey)
            prefs.remove(inviteCodeKey)
            prefs.remove(pairedKey)
            prefs.remove(roleKey)
        }
    }

    suspend fun snapshot(): SessionSnapshot {
        val prefs = context.dataStore.data.first()
        return SessionSnapshot(
            gender = prefs[genderKey]?.let { runCatching { Gender.valueOf(it) }.getOrNull() },
            role = prefs[roleKey]?.let { runCatching { Role.valueOf(it) }.getOrNull() },
            token = prefs[tokenKey],
            peerIp = prefs[peerIpKey],
            peerPort = prefs[peerPortKey]?.toIntOrNull(),
            inviteCode = prefs[inviteCodeKey],
            paired = prefs[pairedKey] == "true"
        )
    }
}

data class SessionSnapshot(
    val gender: Gender?,
    val role: Role?,
    val token: String?,
    val peerIp: String?,
    val peerPort: Int?,
    val inviteCode: String?,
    val paired: Boolean
)
