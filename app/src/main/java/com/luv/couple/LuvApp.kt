package com.luv.couple

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.luv.couple.data.PrefsRepository
import com.luv.couple.data.QuietHoursGate
import com.luv.couple.lock.CanvasMemoryKeeper
import com.luv.couple.lock.CanvasStore
import com.luv.couple.lock.MidnightClear
import com.luv.couple.lock.UnlockMonitor
import com.luv.couple.notify.LuvAlertNotifier
import com.luv.couple.notify.MoodNudgeScheduler
import com.luv.couple.ui.PublicSplashCache
import com.luv.couple.update.AppUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LuvApp : Application() {
    lateinit var prefs: PrefsRepository
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = PrefsRepository(this)
        CanvasStore.init(this)
        com.luv.couple.ui.ItemImageCache.init(this)
        com.luv.couple.ui.WeddingImageCache.init(this)
        UnlockMonitor.start(this)
        createNotificationChannel()
        LuvAlertNotifier.ensureChannel(this) // inkl. Live-Nähe-Kanal
        AppUpdater.ensureChannel(this)
        runCatching { com.luv.couple.ui.security.TamperGuard.check(this) }
        MidnightClear.checkAndClearIfNewDay(this)
        MoodNudgeScheduler.ensureScheduled(this)
        // Splash-Bild früh in den Cache — kein Schwarzbild beim nächsten Start
        appScope.launch {
            runCatching { PublicSplashCache.loadLast(this@LuvApp) }
            runCatching { PublicSplashCache.warmup(this@LuvApp) }
        }
        appScope.launch {
            prefs.quietHoursFlow.collectLatest { QuietHoursGate.update(it) }
        }
        appScope.launch {
            delay(4_000)
            CanvasMemoryKeeper.checkAndNotify(this@LuvApp)
        }
        appScope.launch {
            runCatching { com.luv.couple.net.InstallReferrerJoin.captureOnce(this@LuvApp) }
        }
    }

    private fun createNotificationChannel() {
        // Still, unsichtbar wie möglich — Android verlangt eine FGS-Notification.
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Technische Hintergrundverbindung"
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        /** Neuer Kanal — IMPORTANCE kann nachträglich nicht gesenkt werden. */
        const val CHANNEL_ID = "luv_pair_min"
        lateinit var instance: LuvApp
            private set
    }
}
