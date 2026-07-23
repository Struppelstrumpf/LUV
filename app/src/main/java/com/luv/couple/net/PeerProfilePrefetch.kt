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

    fun start(userId: String, force: Boolean = false) {
        val id = userId.trim()
        if (id.isEmpty()) return
        synchronized(lock) {
            if (!force && jobId == id && job?.isActive == true) return
            // Abgeschlossener Job: behalten — außer force (nach Speichern)
            if (!force && jobId == id && job?.isCompleted == true) return
            jobId = id
            job = scope.async {
                runCatching { LuvApiClient.fetchUserProfileCanvas(id) }.getOrNull()
            }
        }
    }

    fun clear(userId: String? = null) {
        val id = userId?.trim().orEmpty()
        synchronized(lock) {
            if (id.isEmpty() || jobId == id) {
                jobId = null
                job = null
            }
        }
    }

    suspend fun awaitOrFetch(userId: String, force: Boolean = false): LuvApiClient.PeerProfile? {
        val id = userId.trim()
        if (id.isEmpty()) return null
        if (force) clear(id)
        val existing = synchronized(lock) {
            if (!force && jobId == id) job else null
        }
        if (existing != null) return existing.await()
        start(id, force = force)
        return synchronized(lock) { if (jobId == id) job else null }?.await()
            ?: runCatching { LuvApiClient.fetchUserProfileCanvas(id) }.getOrNull()
    }
}
