package com.luv.couple.lock

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.luv.couple.data.Gender
import com.luv.couple.data.Stroke
import com.luv.couple.net.PairConnectionService
import com.luv.couple.net.PairMessage
import com.luv.couple.net.PairProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

object CanvasStore {
    private val strokes = CopyOnWriteArrayList<Stroke>()
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun snapshot(): List<Stroke> = strokes.toList()

    fun addLocalStroke(points: List<com.luv.couple.data.StrokePoint>, width: Float = 18f) {
        if (points.size < 2) return
        val stroke = Stroke(id = UUID.randomUUID().toString(), points = points, width = width)
        strokes.add(stroke)
        bump()
        val encoded = PairProtocol.encode(PairMessage.StrokeMsg(stroke))
        PairConnectionService.sendStroke(appContext, encoded)
        LockScreenWidgetProvider.requestUpdate(appContext)
    }

    fun addRemoteStroke(stroke: Stroke) {
        if (strokes.any { it.id == stroke.id }) return
        strokes.add(stroke)
        bump()
        LockScreenWidgetProvider.requestUpdate(appContext)
    }

    fun clear(localOnly: Boolean = false, notifyPeer: Boolean = false) {
        strokes.clear()
        bump()
        if (notifyPeer && !localOnly) {
            PairConnectionService.sendClear(appContext)
        }
        LockScreenWidgetProvider.requestUpdate(appContext)
    }

    fun renderBitmap(width: Int, height: Int, background: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            paint.strokeWidth = stroke.width
            val path = Path()
            val first = stroke.points.first()
            path.moveTo(first.x * width, first.y * height)
            stroke.points.drop(1).forEach { point ->
                path.lineTo(point.x * width, point.y * height)
            }
            canvas.drawPath(path, paint)
        }
        return bitmap
    }

    fun backgroundFor(gender: Gender?): Int = gender?.lockColor ?: Gender.MALE.lockColor

    private fun bump() {
        _revision.value = _revision.value + 1
        appContext.sendBroadcast(
            Intent(LockScreenWidgetProvider.ACTION_WIDGET_REFRESH).setPackage(appContext.packageName)
        )
    }
}
