package com.luv.couple.lock

import android.content.Context
import com.luv.couple.LuvApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Früher: Leinwand bei Tageswechsel lokal leeren.
 * Das kollidiert mit Persistenz nach Updates — deshalb nur noch Tagesmarker,
 * kein Wipe mehr. Bewusstes Leeren bleibt die Clear-Abstimmung.
 */
object MidnightClear {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun checkAndClearIfNewDay(context: Context) {
        scope.launch {
            val prefs = LuvApp.instance.prefs
            val today = LocalDate.now().toString()
            prefs.setLastClearDay(today)
        }
    }
}
