package com.luv.couple.lock

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.luv.couple.LuvApp
import com.luv.couple.data.Gender
import com.luv.couple.data.Stroke
import com.luv.couple.net.PairConnectionService
import com.luv.couple.net.PairMessage
import com.luv.couple.net.PairProtocol
import com.luv.couple.net.PairSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

object CanvasStore {
    private val strokes = CopyOnWriteArrayList<Stroke>()
    private val localStrokeIds = CopyOnWriteArrayList<String>()
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun snapshot(): List<Stroke> = strokes.toList()

    fun addLocalStroke(points: List<com.luv.couple.data.StrokePoint>, width: Float = 18f) {
        if (points.size < 2) return
        val gender = runBlocking { LuvApp.instance.prefs.snapshot().gender?.name }
        val stroke = Stroke(
            id = UUID.randomUUID().toString(),
            points = points,
            width = width,
            isLocal = true,
            gender = gender
        )
        strokes.add(stroke)
        localStrokeIds.add(stroke.id)
        bump()
        PairConnectionService.sendStroke(
            appContext,
            PairProtocol.encode(PairMessage.StrokeMsg(stroke))
        )
        LockScreenWidgetProvider.requestUpdate(appContext)
    }

    /** Einzelner Tipp-Punkt (i-Punkt, Ü-Punkte, …) */
    fun addLocalDot(x: Float, y: Float) {
        val r = 0.006f
        val points = buildList {
            for (i in 0..10) {
                val a = (Math.PI * 2.0 * i) / 10.0
                add(
                    com.luv.couple.data.StrokePoint(
                        x = (x + r * kotlin.math.cos(a)).toFloat().coerceIn(0f, 1f),
                        y = (y + r * kotlin.math.sin(a)).toFloat().coerceIn(0f, 1f)
                    )
                )
            }
        }
        addLocalStroke(points, width = 16f)
    }

    fun addRemoteStroke(stroke: Stroke) {
        if (strokes.any { it.id == stroke.id }) return
        strokes.add(stroke.copy(isLocal = false))
        bump()
        LockScreenWidgetProvider.requestUpdate(appContext)
    }

    fun undoLastLocalStroke(): Boolean {
        val id = localStrokeIds.lastOrNull() ?: return false
        localStrokeIds.remove(id)
        strokes.removeAll { it.id == id }
        bump()
        PairConnectionService.sendUndo(appContext, id)
        LockScreenWidgetProvider.requestUpdate(appContext)
        return true
    }

    fun removeStrokeById(strokeId: String) {
        val removed = strokes.removeAll { it.id == strokeId }
        localStrokeIds.remove(strokeId)
        if (removed) {
            bump()
            LockScreenWidgetProvider.requestUpdate(appContext)
        }
    }

    fun clear(localOnly: Boolean = false, notifyPeer: Boolean = false) {
        strokes.clear()
        localStrokeIds.clear()
        bump()
        if (notifyPeer && !localOnly) {
            PairConnectionService.sendClear(appContext)
        }
        LockScreenWidgetProvider.requestUpdate(appContext)
    }

    fun strokeColor(stroke: Stroke, myGender: Gender?): Int {
        if (stroke.isLocal) return Color.WHITE
        val partner = PairSessionState.partnerGender.value
            ?: stroke.gender?.let { runCatching { Gender.valueOf(it) }.getOrNull() }
            ?: myGender?.let { if (it == Gender.MALE) Gender.FEMALE else Gender.MALE }
        return partner?.partnerStrokeColor ?: 0xFFFFF0F8.toInt()
    }

    fun renderBitmap(width: Int, height: Int, background: Int, myGender: Gender? = null): Bitmap {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            paint.color = strokeColor(stroke, myGender)
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
