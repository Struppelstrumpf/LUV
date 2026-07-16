package com.luv.couple.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

object UnlockMonitor {
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                CanvasStore.clearAll(notifyPeer = true)
            }
        }
    }

    fun start(context: Context) {
        if (registered) return
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.applicationContext.registerReceiver(
                receiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.applicationContext.registerReceiver(receiver, filter)
        }
        registered = true
    }
}
