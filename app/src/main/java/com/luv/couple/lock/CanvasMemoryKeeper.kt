package com.luv.couple.lock

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.luv.couple.LuvApp
import com.luv.couple.data.Lobby
import com.luv.couple.net.LuvApiClient
import com.luv.couple.notify.LuvAlertNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Speichert ein Abbild der Leinwand auf dem Server und prüft
 * freigegebene 24h-Erinnerungen nach langer Inaktivität.
 * Snapshots auch für Invite-Landing (öffentliches Vorschaubild).
 */
object CanvasMemoryKeeper {
    private val uploadMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debounceJob: Job? = null
    private var lastUploadAt = 0L

    suspend fun uploadSnapshot(lobby: Lobby, allowEmpty: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            uploadMutex.withLock {
                runCatching {
                    if (LuvApiClient.sessionToken.isNullOrBlank()) return@runCatching false
                    val strokeCount = CanvasStore.snapshot(lobby.id).size
                    if (!allowEmpty && strokeCount <= 0) {
                        // Keine leere PNG über ein gutes Invite-Bild schreiben
                        return@runCatching false
                    }
                    val bg = CanvasStore.backgroundFor(CanvasStore.cachedColorIndex)
                    val bitmap = CanvasStore.renderBitmap(720, 1280, bg, lobby.id)
                    val stream = ByteArrayOutputStream()
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 88, stream)) return@runCatching false
                    val bytes = stream.toByteArray()
                    // Fast leere Flächen (einfarbig) — nicht als OG speichern
                    if (!allowEmpty && bytes.size < 10_000 && strokeCount < 2) {
                        return@runCatching false
                    }
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    LuvApiClient.uploadCanvasSnapshot(lobby.code, lobby.token, b64)
                    LuvApiClient.touchCanvas(lobby.code, lobby.token)
                    lastUploadAt = System.currentTimeMillis()
                    true
                }.getOrDefault(false)
            }
        }

    /** Nach Strichen: Snapshot verzögert hochladen (Invite-Preview aktuell halten). */
    fun scheduleUploadForActiveLobby(delayMs: Long = 4_500L) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(delayMs)
            if (System.currentTimeMillis() - lastUploadAt < 3_000L) return@launch
            val lobbyId = CanvasStore.activeLobbyId.value ?: return@launch
            val lobby = LuvApp.instance.prefs.snapshot().lobbies
                .firstOrNull { it.id == lobbyId } ?: return@launch
            uploadSnapshot(lobby)
        }
    }

    suspend fun touch(lobby: Lobby) = withContext(Dispatchers.IO) {
        runCatching { LuvApiClient.touchCanvas(lobby.code, lobby.token) }
    }

    suspend fun checkAndNotify(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = LuvApp.instance.prefs
            val snap = prefs.snapshot()
            LuvApiClient.sessionToken = snap.sessionToken
            if (snap.sessionToken.isNullOrBlank()) return@runCatching
            val memories = LuvApiClient.fetchMemories()
            for (mem in memories) {
                if (prefs.wasMemorySeen(mem.code, mem.releasedAt)) continue
                prefs.markMemorySeen(mem.code, mem.releasedAt)
                LuvAlertNotifier.onCanvasMemory(
                    context.applicationContext,
                    mem.lobbyName,
                    mem.code,
                    mem.imageUrl
                )
            }
            for (lobby in snap.lobbies) {
                val mem = LuvApiClient.fetchRoomMemory(lobby.code, lobby.token) ?: continue
                if (prefs.wasMemorySeen(mem.code, mem.releasedAt)) continue
                prefs.markMemorySeen(mem.code, mem.releasedAt)
                LuvAlertNotifier.onCanvasMemory(
                    context.applicationContext,
                    mem.lobbyName,
                    mem.code,
                    mem.imageUrl
                )
            }
        }
    }

    fun absoluteImageUrl(pathOrUrl: String): String {
        val base = if (pathOrUrl.startsWith("http")) {
            pathOrUrl
        } else {
            LuvApiClient.baseUrl().trimEnd('/') + pathOrUrl
        }
        val tok = LuvApiClient.sessionToken?.trim().orEmpty()
        if (tok.isEmpty()) return base
        if (base.contains("session=")) return base
        val sep = if (base.contains('?')) "&" else "?"
        return base + sep + "session=" + java.net.URLEncoder.encode(tok, Charsets.UTF_8.name())
    }
}
