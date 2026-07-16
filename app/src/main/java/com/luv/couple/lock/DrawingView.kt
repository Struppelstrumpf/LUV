package com.luv.couple.lock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.luv.couple.data.Stroke
import com.luv.couple.data.StrokePoint
import kotlin.math.hypot

class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val strokes = mutableListOf<Stroke>()
    private val currentPoints = mutableListOf<StrokePoint>()
    private val currentPath = Path()
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private var downX = 0f
    private var downY = 0f
    private var longPressTriggered = false
    private var movedBeyondSlop = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 18f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    var onStrokeFinished: ((List<StrokePoint>) -> Unit)? = null
    var onLongPressClear: (() -> Unit)? = null

    private val longPressRunnable = Runnable {
        if (!movedBeyondSlop) {
            longPressTriggered = true
            currentPoints.clear()
            currentPath.reset()
            invalidate()
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onLongPressClear?.invoke()
        }
    }

    fun setStrokes(list: List<Stroke>) {
        strokes.clear()
        strokes.addAll(list)
        invalidate()
    }

    fun addStroke(stroke: Stroke) {
        if (strokes.any { it.id == stroke.id }) return
        strokes.add(stroke)
        invalidate()
    }

    fun clearCanvas() {
        strokes.clear()
        currentPoints.clear()
        currentPath.reset()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        strokes.forEach { stroke ->
            paint.strokeWidth = stroke.width
            canvas.drawPath(pathFrom(stroke.points), paint)
        }
        paint.strokeWidth = 18f
        canvas.drawPath(currentPath, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val normalized = StrokePoint(
            x = (event.x / width.toFloat()).coerceIn(0f, 1f),
            y = (event.y / height.toFloat()).coerceIn(0f, 1f)
        )
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressTriggered = false
                movedBeyondSlop = false
                downX = event.x
                downY = event.y
                currentPoints.clear()
                currentPath.reset()
                currentPoints.add(normalized)
                currentPath.moveTo(event.x, event.y)
                parent.requestDisallowInterceptTouchEvent(true)
                handler.removeCallbacks(longPressRunnable)
                handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (longPressTriggered) return true
                val distance = hypot(event.x - downX, event.y - downY)
                if (distance > touchSlop) {
                    movedBeyondSlop = true
                    handler.removeCallbacks(longPressRunnable)
                }
                currentPoints.add(normalized)
                currentPath.lineTo(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                if (!longPressTriggered && currentPoints.size >= 2) {
                    onStrokeFinished?.invoke(currentPoints.toList())
                }
                currentPoints.clear()
                currentPath.reset()
                longPressTriggered = false
                invalidate()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }

    private fun pathFrom(points: List<StrokePoint>): Path {
        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(points.first().x * width, points.first().y * height)
        points.drop(1).forEach { point ->
            path.lineTo(point.x * width, point.y * height)
        }
        return path
    }

    companion object {
        private const val LONG_PRESS_MS = 2000L
    }
}
