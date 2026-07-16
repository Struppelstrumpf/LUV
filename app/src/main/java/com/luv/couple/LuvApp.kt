package com.luv.couple

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.luv.couple.data.PrefsRepository
import com.luv.couple.lock.CanvasStore
import com.luv.couple.lock.MidnightClear
import com.luv.couple.lock.UnlockMonitor
import com.luv.couple.notify.PartnerStrokeNotifier
import com.luv.couple.update.AppUpdater

class LuvApp : Application() {
    lateinit var prefs: PrefsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = PrefsRepository(this)
        CanvasStore.init(this)
        UnlockMonitor.start(this)
        createNotificationChannel()
        PartnerStrokeNotifier.ensureChannel(this)
        AppUpdater.ensureChannel(this)
        MidnightClear.checkAndClearIfNewDay(this)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "luv_pair"
        lateinit var instance: LuvApp
            private set
    }
}
