package com.luv.couple.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.luv.couple.LuvApp
import kotlinx.coroutines.runBlocking

class MoodNudgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        val enabled = runBlocking {
            runCatching { LuvApp.instance.prefs.isPartnerDrawNotifyEnabled() }.getOrDefault(true)
        }
        if (!enabled) {
            MoodNudgeScheduler.ensureScheduled(context)
            return
        }
        val slot = intent.getIntExtra(EXTRA_SLOT, 0).coerceIn(0, 2)
        MoodNudgeScheduler.onNudgeFired(context, slot)
    }

    companion object {
        const val ACTION = "com.luv.couple.ACTION_MOOD_NUDGE"
        const val EXTRA_SLOT = "slot"
    }
}
