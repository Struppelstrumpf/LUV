package com.luv.couple

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.luv.couple.net.PendingJoin
import com.luv.couple.net.PendingShop
import com.luv.couple.net.PendingShopReturn
import com.luv.couple.net.PendingSplashSkip
import com.luv.couple.ui.LuvAppNav
import com.luv.couple.ui.applyPortraitOnPhonesOnly
import com.luv.couple.ui.theme.LuvTheme
import com.luv.couple.update.AppUpdater

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyPortraitOnPhonesOnly()
        // Solange die App im Vordergrund ist, bleibt der Bildschirm an
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        maybeRequestNotificationPermission()
        captureNotificationFlag(intent)
        captureJoinIntent(intent)
        captureUpdateIntent(intent)
        captureShopIntent(intent)
        setContent {
            LuvTheme {
                LuvAppNav()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureNotificationFlag(intent)
        captureJoinIntent(intent)
        captureUpdateIntent(intent)
        captureShopIntent(intent)
    }

    private fun captureNotificationFlag(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false) == true) {
            PendingSplashSkip.offer()
        }
    }

    private fun captureUpdateIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(AppUpdater.EXTRA_OPEN_UPDATE, false) == true) {
            AppUpdater.offerFocus()
        }
    }

    private fun captureShopIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_SHOP, false) == true) {
            PendingShop.offer()
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_MARKETPLACE, false) == true) {
            com.luv.couple.net.PendingMarketplace.offer()
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_SOZIAL, false) == true) {
            val sub = intent.getIntExtra(EXTRA_SOZIAL_TAB, 0)
            // 0=Freunde, 1=Bilder (oder Legacy-Erfolge), 2=Erfolge
            com.luv.couple.net.PendingDeepLink.offer(
                when (sub) {
                    2 -> com.luv.couple.net.DeepLinkTarget.SozialAchievements
                    1 -> com.luv.couple.net.DeepLinkTarget.SozialAchievements // Legacy-Push
                    else -> com.luv.couple.net.DeepLinkTarget.SozialFriends
                }
            )
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_INVENTAR, false) == true) {
            com.luv.couple.net.PendingDeepLink.offer(com.luv.couple.net.DeepLinkTarget.Inventar)
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_HOME, false) == true) {
            com.luv.couple.net.PendingDeepLink.offer(com.luv.couple.net.DeepLinkTarget.Home)
        }
    }

    private fun captureJoinIntent(intent: Intent?) {
        val data = intent?.data?.toString()
        val extra = intent?.getStringExtra(Intent.EXTRA_TEXT)
        PendingShopReturn.offer(data)
        if (data?.contains("/shop/", ignoreCase = true) == true ||
            data?.startsWith("luv://shop", ignoreCase = true) == true
        ) {
            return
        }
        PendingJoin.offer(data ?: extra)
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_SHOP = "open_shop"
        const val EXTRA_OPEN_MARKETPLACE = "open_marketplace"
        const val EXTRA_OPEN_SOZIAL = "open_sozial"
        /** 0 = Freunde, 1 = Bilder, 2 = Erfolge (1 war früher Erfolge) */
        const val EXTRA_SOZIAL_TAB = "sozial_tab"
        const val EXTRA_OPEN_INVENTAR = "open_inventar"
        const val EXTRA_OPEN_HOME = "open_home"
        const val EXTRA_FROM_NOTIFICATION = "from_notification"
    }
}
