package com.luv.couple

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.luv.couple.net.PendingJoin
import com.luv.couple.net.PendingShopReturn
import com.luv.couple.ui.LuvAppNav
import com.luv.couple.ui.theme.LuvTheme

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()
        captureJoinIntent(intent)
        setContent {
            LuvTheme {
                LuvAppNav()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureJoinIntent(intent)
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
}
