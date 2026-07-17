package com.luv.couple.lock

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.luv.couple.LuvApp
import com.luv.couple.data.Lobby
import com.luv.couple.net.CanvasMemory
import com.luv.couple.net.LuvApiClient
import com.luv.couple.notify.LuvAlertNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Speichert ein Abbild der Leinwand auf dem Server und prüft
 * freigegebene 24h-Erinnerungen nach langer Inaktivität.
 */
object CanvasMemoryKeeper {
    private val uploadMutex = Mutex()

    suspend fun uploadSnapshot(lobby: Lobby) = withContext(Dispatchers.IO) {
        uploadMutex.withLock {
            runCatching {
                val bg = CanvasStore.backgroundFor(CanvasStore.cachedColorIndex)
                val bitmap = CanvasStore.renderBitmap(720, 1280, bg, lobby.id)
                val stream = ByteArrayOutputStream()
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 88, stream)) return@runCatching
                val b64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                LuvApiClient.uploadCanvasSnapshot(lobby.code, lobby.token, b64)
                LuvApiClient.touchCanvas(lobby.code, lobby.token)
            }
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
            // Pro Lobby auch einzeln prüfen (falls /me/memories leer, lokal aber Lobby da)
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
        if (pathOrUrl.startsWith("http")) return pathOrUrl
        return LuvApiClient.baseUrl().trimEnd('/') + pathOrUrl
    }
}
