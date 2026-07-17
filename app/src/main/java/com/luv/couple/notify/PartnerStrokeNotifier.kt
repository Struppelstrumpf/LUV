package com.luv.couple.notify

import android.content.Context

/** @deprecated Nutze [LuvAlertNotifier] — bleibt als dünne Weiterleitung. */
object PartnerStrokeNotifier {
    fun ensureChannel(context: Context) {
        LuvAlertNotifier.ensureChannel(context)
    }

    fun onPartnerStroke(
        context: Context,
        lobbyName: String,
        nickname: String,
        lobbyId: String
    ) {
        LuvAlertNotifier.onPartnerStroke(context, lobbyName, nickname, lobbyId)
    }
}
