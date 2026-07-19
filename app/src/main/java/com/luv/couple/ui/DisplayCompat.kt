package com.luv.couple.ui

import android.app.Activity
import android.content.pm.ActivityInfo

/**
 * Portrait nur auf Phones (sw < 600dp). Tablets/Foldables bleiben frei —
 * Android 16 ignoriert feste Orientierung auf großen Displays ohnehin.
 */
fun Activity.applyPortraitOnPhonesOnly() {
    requestedOrientation = if (resources.configuration.smallestScreenWidthDp < 600) {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    } else {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}
