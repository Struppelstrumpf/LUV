package com.luv.couple.lock

import android.content.Context
import com.luv.couple.LuvApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

object MidnightClear {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun checkAndClearIfNewDay(context: Context) {
        scope.launch {
            val prefs = LuvApp.instance.prefs
            val today = LocalDate.now().toString()
            val last = prefs.lastClearDay()
            if (last != null && last != today) {
                CanvasStore.clearAll(notifyPeer = true)
            }
            prefs.setLastClearDay(today)
        }
    }
}
