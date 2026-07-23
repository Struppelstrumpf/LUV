package com.luv.couple.net

import java.util.concurrent.ConcurrentHashMap

/** Kurzzeit-Cache für fremde Profilleinwände — verhindert leeren Platzhalter-Flash. */
object PeerProfileCache {
    private val map = ConcurrentHashMap<String, LuvApiClient.PeerProfile>()

    fun get(userId: String): LuvApiClient.PeerProfile? {
        val id = userId.trim()
        if (id.isEmpty()) return null
        return map[id]
    }

    fun put(userId: String, profile: LuvApiClient.PeerProfile) {
        val id = userId.trim()
        if (id.isEmpty()) return
        map[id] = profile
    }

    fun clear(userId: String? = null) {
        val id = userId?.trim().orEmpty()
        if (id.isEmpty()) map.clear() else map.remove(id)
    }
}
