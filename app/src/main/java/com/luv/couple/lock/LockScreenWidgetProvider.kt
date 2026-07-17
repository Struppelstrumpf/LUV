package com.luv.couple.lock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.luv.couple.LuvApp
import com.luv.couple.R
import com.luv.couple.notify.LiveProximity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class LockScreenWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            widgetLobbyIds.remove(widgetId)
            widgetLobbyNames.remove(widgetId)
            runCatching {
                runBlocking { LuvApp.instance.prefs.unbindWidget(widgetId) }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_REFRESH ||
            intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE
        ) {
            requestUpdate(context)
        }
    }

    companion object {
        const val ACTION_WIDGET_REFRESH = "com.luv.couple.ACTION_WIDGET_REFRESH"
        const val EXTRA_LOBBY_ID = "lobby_id"

        private val widgetLobbyIds = ConcurrentHashMap<Int, String>()
        private val widgetLobbyNames = ConcurrentHashMap<Int, String>()
        private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val pendingJob = AtomicReference<Job?>(null)

        fun bind(widgetId: Int, lobbyId: String, lobbyName: String) {
            widgetLobbyIds[widgetId] = lobbyId
            widgetLobbyNames[widgetId] = lobbyName
        }

        /**
         * Debounced + Hintergrund-Thread — Bitmap-Render darf den Mal-UI-Thread
         * nicht blockieren (sonst wirkt die Leinwand auf schwachen Geräten schwarz/eingefroren).
         */
        fun requestUpdate(context: Context) {
            val app = context.applicationContext
            pendingJob.getAndSet(
                updateScope.launch {
                    delay(400)
                    runCatching {
                        val manager = AppWidgetManager.getInstance(app)
                        val ids = manager.getAppWidgetIds(
                            ComponentName(app, LockScreenWidgetProvider::class.java)
                        )
                        ids.forEach { updateWidget(app, manager, it) }
                    }
                }
            )?.cancel()
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val options = manager.getAppWidgetOptions(widgetId)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
                .takeIf { it > 0 } ?: 400
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                .takeIf { it > 0 } ?: 400
            val density = context.resources.displayMetrics.density
            val pxW = (width * density).toInt().coerceIn(200, 1200)
            val pxH = (height * density).toInt().coerceIn(200, 1600)

            val (lobbyId, lobbyName) = resolveLobby(widgetId)
            val background = CanvasStore.backgroundFor(CanvasStore.cachedColorIndex)
            val bitmap = CanvasStore.renderBitmap(pxW, pxH, background, lobbyId)

            val views = RemoteViews(context.packageName, R.layout.lock_widget)
            views.setInt(R.id.widgetRoot, "setBackgroundColor", background)
            views.setImageViewBitmap(R.id.canvasImage, bitmap)
            val hot = LiveProximity.isLobbyHot(lobbyId)
            val painter = LiveProximity.painterName(lobbyId)
            if (hot) {
                views.setViewVisibility(R.id.liveBadge, View.VISIBLE)
                views.setTextViewText(
                    R.id.liveBadge,
                    if (!painter.isNullOrBlank()) {
                        context.getString(R.string.widget_live_fmt, painter)
                    } else {
                        context.getString(R.string.widget_live_now)
                    }
                )
                views.setTextViewText(
                    R.id.tapHint,
                    if (!painter.isNullOrBlank()) {
                        "$painter malt — tippen"
                    } else {
                        "Jemand malt — tippen"
                    }
                )
                views.setFloat(R.id.tapHint, "setAlpha", 0.95f)
            } else {
                views.setViewVisibility(R.id.liveBadge, View.GONE)
                views.setTextViewText(
                    R.id.tapHint,
                    lobbyName?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.open_canvas)
                )
                views.setFloat(R.id.tapHint, "setAlpha", 0.75f)
            }

            val launch = Intent(context, LockDrawActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(LockDrawActivity.EXTRA_LOBBY_ID, lobbyId)
            }
            val pending = PendingIntent.getActivity(
                context,
                widgetId,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pending)
            views.setOnClickPendingIntent(R.id.canvasImage, pending)

            manager.updateAppWidget(widgetId, views)
        }

        private fun resolveLobby(widgetId: Int): Pair<String?, String?> {
            val cachedId = widgetLobbyIds[widgetId]
            val cachedName = widgetLobbyNames[widgetId]
            if (cachedId != null) return cachedId to cachedName

            return runCatching {
                runBlocking {
                    val lobby = LuvApp.instance.prefs.widgetLobby(widgetId)
                    if (lobby != null) {
                        widgetLobbyIds[widgetId] = lobby.id
                        widgetLobbyNames[widgetId] = lobby.name
                        lobby.id to lobby.name
                    } else {
                        CanvasStore.activeLobbyId.value to null
                    }
                }
            }.getOrElse {
                CanvasStore.activeLobbyId.value to null
            }
        }
    }
}
