package com.luv.couple.lock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.luv.couple.LuvApp
import com.luv.couple.R
import kotlinx.coroutines.runBlocking

class LockScreenWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
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

        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, LockScreenWidgetProvider::class.java)
            )
            ids.forEach { updateWidget(context, manager, it) }
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

            val gender = runBlocking { LuvApp.instance.prefs.snapshot().gender }
            val background = CanvasStore.backgroundFor(gender)
            val bitmap = CanvasStore.renderBitmap(pxW, pxH, background)

            val views = RemoteViews(context.packageName, R.layout.lock_widget)
            views.setInt(R.id.widgetRoot, "setBackgroundColor", background)
            views.setImageViewBitmap(R.id.canvasImage, bitmap)

            val launch = Intent(context, LockDrawActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
    }
}
