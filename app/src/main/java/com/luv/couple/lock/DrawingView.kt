package com.luv.couple.lock

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import com.luv.couple.data.PeerPalette
import com.luv.couple.data.Stroke
import com.luv.couple.data.StrokePoint
import kotlin.math.hypot
import kotlin.math.min

class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val strokes = mutableListOf<Stroke>()
    private val alphas = mutableMapOf<String, Float>()
    private val animators = mutableMapOf<String, ValueAnimator>()
    private val currentPoints = mutableListOf<StrokePoint>()
    private val currentPath = Path()
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private var downX = 0f
    private var downY = 0f
    private var longPressTriggered = false
    private var movedBeyondSlop = false
    private var lastTapUptime = 0L
    private var pendingDot: StrokePoint? = null
    var myColorIndex: Int = 0

    var showTicTacToe: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 18f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0x99FFFFFF.toInt()
    }

    private val boardGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0x33FFFFFF.toInt()
    }

    var onStrokeFinished: ((List<StrokePoint>) -> Unit)? = null
    var onLongPressClear: (() -> Unit)? = null
    var onDoubleTapUndo: (() -> Unit)? = null
    var onDotPlaced: ((StrokePoint) -> Unit)? = null

    private val longPressRunnable = Runnable {
        if (!movedBeyondSlop) {
            longPressTriggered = true
            cancelPendingDot()
            currentPoints.clear()
            currentPath.reset()
            invalidate()
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onLongPressClear?.invoke()
        }
    }

    /** Nach 0,5 s ohne zweiten Tipp → Punkt setzen */
    private val singleTapDotRunnable = Runnable {
        val dot = pendingDot ?: return@Runnable
        pendingDot = null
        lastTapUptime = 0L
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        onDotPlaced?.invoke(dot)
    }

    fun setStrokes(list: List<Stroke>, animateNew: Boolean = false) {
        val previous = strokes.map { it.id }.toSet()
        strokes.clear()
        strokes.addAll(list)
        list.forEach { stroke ->
            if (animateNew && stroke.id !in previous) {
                animateIn(stroke.id)
            } else {
                alphas.putIfAbsent(stroke.id, 1f)
            }
        }
        val live = list.map { it.id }.toSet()
        alphas.keys.filter { it !in live }.forEach { alphas.remove(it) }
        invalidate()
    }

    fun addStroke(stroke: Stroke, fadeIn: Boolean = true) {
        if (strokes.any { it.id == stroke.id }) return
        strokes.add(stroke)
        if (fadeIn) animateIn(stroke.id) else alphas[stroke.id] = 1f
        invalidate()
    }

    fun clearCanvas() {
        animators.values.forEach { it.cancel() }
        animators.clear()
        alphas.clear()
        strokes.clear()
        currentPoints.clear()
        currentPath.reset()
        invalidate()
    }

    private fun animateIn(id: String) {
        animators[id]?.cancel()
        alphas[id] = 0f
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                alphas[id] = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        animators[id] = anim
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (showTicTacToe) drawTicTacToeBoard(canvas)
        strokes.forEach { stroke ->
            val alpha = ((alphas[stroke.id] ?: 1f) * 255).toInt().coerceIn(0, 255)
            val color = CanvasStore.strokeColor(stroke)
            paint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
            paint.strokeWidth = stroke.width
            canvas.drawPath(pathFrom(stroke.points), paint)
        }
        paint.color = PeerPalette.strokeColor(myColorIndex)
        paint.strokeWidth = 18f
        canvas.drawPath(currentPath, paint)
    }

    private fun drawTicTacToeBoard(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val size = min(w, h) * 0.64f
        val left = (w - size) / 2f
        val top = (h - size) / 2f - h * 0.05f
        val right = left + size
        val bottom = top + size
        val third = size / 3f
        val dens = resources.displayMetrics.density

        boardGlow.strokeWidth = 10f * dens
        boardPaint.strokeWidth = 3.2f * dens

        fun vLine(x: Float) {
            canvas.drawLine(x, top + dens * 4f, x, bottom - dens * 4f, boardGlow)
            canvas.drawLine(x, top + dens * 4f, x, bottom - dens * 4f, boardPaint)
        }
        fun hLine(y: Float) {
            canvas.drawLine(left + dens * 4f, y, right - dens * 4f, y, boardGlow)
            canvas.drawLine(left + dens * 4f, y, right - dens * 4f, y, boardPaint)
        }
        vLine(left + third)
        vLine(left + third * 2f)
        hLine(top + third)
        hLine(top + third * 2f)
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
                    cancelPendingDot()
                }
                currentPoints.add(normalized)
                currentPath.lineTo(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                if (!longPressTriggered) {
                    if (!movedBeyondSlop && event.actionMasked == MotionEvent.ACTION_UP) {
                        handleTap(normalized)
                    } else if (movedBeyondSlop && currentPoints.size >= 2) {
                        cancelPendingDot()
                        lastTapUptime = 0L
                        onStrokeFinished?.invoke(currentPoints.toList())
                    }
                }
                currentPoints.clear()
                currentPath.reset()
                longPressTriggered = false
                invalidate()
            }
        }
        return true
    }

    private fun handleTap(point: StrokePoint) {
        val now = SystemClock.uptimeMillis()
        if (lastTapUptime > 0L && now - lastTapUptime <= DOUBLE_TAP_MS) {
            // Zweiter Tipp innerhalb 0,5 s → Undo, kein Punkt
            cancelPendingDot()
            lastTapUptime = 0L
            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            onDoubleTapUndo?.invoke()
        } else {
            // Erster Tipp: Punkt erst nach 0,5 s setzen (sonst wäre es Doppeltipp)
            handler.removeCallbacks(singleTapDotRunnable)
            pendingDot = point
            lastTapUptime = now
            handler.postDelayed(singleTapDotRunnable, DOUBLE_TAP_MS)
        }
    }

    private fun cancelPendingDot() {
        handler.removeCallbacks(singleTapDotRunnable)
        pendingDot = null
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(longPressRunnable)
        cancelPendingDot()
        animators.values.forEach { it.cancel() }
        animators.clear()
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
        private const val DOUBLE_TAP_MS = 500L
    }
}
