package com.luv.couple.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.luv.couple.LuvApp
import com.luv.couple.notify.MoodNudgeScheduler
import kotlinx.coroutines.runBlocking

class BootReconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        MoodNudgeScheduler.ensureScheduled(context.applicationContext)
        val hasLobbies = runBlocking { LuvApp.instance.prefs.snapshot().hasLobbies }
        if (hasLobbies) {
            PairConnectionService.startAll(context.applicationContext)
        }
    }
}
