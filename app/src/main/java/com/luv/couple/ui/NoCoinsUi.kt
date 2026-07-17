package com.luv.couple.ui

import android.app.Activity
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.luv.couple.MainActivity
import com.luv.couple.net.PendingShop
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary

object NoCoinsUi {
    const val TITLE = "Nicht genug Coins"
    const val BODY =
        "Dafür reichen deine Coins gerade nicht.\n\n" +
            "Wart bis morgen auf die kostenlosen Tages-Coins — oder hol dir welche im Shop."

    @Composable
    fun Dialog(
        visible: Boolean,
        onDismiss: () -> Unit,
        onOpenShop: () -> Unit
    ) {
        if (!visible) return
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = BgSoft,
            title = {
                Text(TITLE, fontFamily = DisplayFont, fontSize = 22.sp, color = TextPrimary)
            },
            text = {
                Text(BODY, color = TextMuted, fontFamily = BodyFont, fontSize = 14.sp, lineHeight = 20.sp)
            },
            confirmButton = {
                TextButton(onClick = {
                    onDismiss()
                    onOpenShop()
                }) {
                    Text("Zum Shop", color = AccentRose, fontFamily = DisplayFont, fontSize = 15.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Später", color = TextMuted, fontFamily = BodyFont, fontSize = 15.sp)
                }
            }
        )
    }

    fun show(activity: Activity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(TITLE)
            .setMessage(BODY)
            .setPositiveButton("Zum Shop") { _, _ ->
                PendingShop.offer()
                activity.startActivity(
                    Intent(activity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra(MainActivity.EXTRA_OPEN_SHOP, true)
                    }
                )
            }
            .setNegativeButton("Später", null)
            .show()
    }
}
