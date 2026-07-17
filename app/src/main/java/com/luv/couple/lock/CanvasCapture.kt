package com.luv.couple.lock

import android.content.Context
import com.luv.couple.LuvApp
import com.luv.couple.data.LocalMoments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CanvasCapture {
    suspend fun saveMoment(context: Context, lobbyId: String? = null): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val snap = LuvApp.instance.prefs.snapshot()
                val id = lobbyId ?: snap.activeLobbyId
                val bg = CanvasStore.backgroundFor(snap.colorIndex)
                val bitmap = CanvasStore.renderBitmap(1080, 1920, bg, id)
                LocalMoments.save(context, bitmap).getOrThrow()
            }
        }
}
