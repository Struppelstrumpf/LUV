package com.luv.couple.ui.wedding

import com.luv.couple.net.LuvApiClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Vorgewärmtes Kapellen-Layout — spart den ersten Roundtrip beim Tippen auf „Zur Hochzeit“. */
object WeddingChapelCache {
    @Volatile
    var layout: LuvApiClient.RoomLayout? = null
        private set

    private val mutex = Mutex()

    fun put(layout: LuvApiClient.RoomLayout?) {
        if (layout != null) this.layout = layout
    }

    suspend fun warm() {
        if (layout != null) return
        mutex.withLock {
            if (layout != null) return
            layout = runCatching { LuvApiClient.fetchRoomLayout("wedding") }.getOrNull()
        }
    }
}
