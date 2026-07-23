package com.luv.couple.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred

/**
 * Startet den Profil-Fetch beim Antippen — genau ein User, kein Massen-Vorladen.
 * Die Navigations-Animation überlappt mit dem Netzwerk; der Screen wartet auf denselben Job.
 */
object PeerProfilePrefetch {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var jobId: String? = null
    private var job: Deferred<LuvApiClient.PeerProfile?>? = null

    fun start(userId: String) {
        val id = userId.trim()
        if (id.isEmpty()) return
        synchronized(lock) {
            if (jobId == id && job?.isActive == true) return
            // Abgeschlossener Job für dieselbe ID: behalten (erneutes Öffnen ohne zweiten Roundtrip)
            if (jobId == id && job?.isCompleted == true) return
            jobId = id
            job = scope.async {
                runCatching { LuvApiClient.fetchUserProfileCanvas(id) }.getOrNull()
            }
        }
    }

    suspend fun awaitOrFetch(userId: String): LuvApiClient.PeerProfile? {
        val id = userId.trim()
        if (id.isEmpty()) return null
        val existing = synchronized(lock) {
            if (jobId == id) job else null
        }
        if (existing != null) return existing.await()
        start(id)
        return synchronized(lock) { if (jobId == id) job else null }?.await()
            ?: runCatching { LuvApiClient.fetchUserProfileCanvas(id) }.getOrNull()
    }
}
