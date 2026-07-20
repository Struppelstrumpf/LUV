package com.luv.couple.ui.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Leichte Client-Signale gegen offensichtliche Mod-/Sideload-Umgebungen.
 * Kein Ersatz für Server-Validierung — nur Hinweis/Block für klar manipulierbare Builds.
 */
object TamperGuard {
    private const val TAG = "LuvTamper"
    private const val PLAY_STORE = "com.android.vending"
    private const val PLAY_STORE_TC = "com.google.android.packageinstaller"

    data class Verdict(
        val ok: Boolean,
        val reason: String? = null,
    )

    fun check(context: Context): Verdict {
        val appInfo = context.applicationInfo
        if ((appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            // Release-Builds sollten nie debuggable sein
            if (!isDebugBuild()) {
                Log.w(TAG, "debuggable release")
                return Verdict(false, "debug_build")
            }
        }
        val installer = installerPackage(context)
        // Emulator / unbekannter Installer: nur loggen, nicht hart blocken (Tests)
        if (installer != null &&
            installer != PLAY_STORE &&
            installer != PLAY_STORE_TC &&
            installer != "com.google.android.feedback" &&
            !isDebugBuild()
        ) {
            Log.w(TAG, "non-play installer=$installer")
            // Weich: Server-Integrity entscheidet bei Signup
        }
        return Verdict(true)
    }

    private fun isDebugBuild(): Boolean =
        try {
            com.luv.couple.BuildConfig.DEBUG
        } catch (_: Throwable) {
            false
        }

    private fun installerPackage(context: Context): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Throwable) {
            null
        }
    }
}
