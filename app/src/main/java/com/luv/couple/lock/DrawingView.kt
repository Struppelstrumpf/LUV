package com.luv.couple.lock

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
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
    private val eraserPath = Path()
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }

    private var downX = 0f
    private var downY = 0f
    private var eraseLastX = 0f
    private var eraseLastY = 0f
    private var movedBeyondSlop = false
    private var lastTapUptime = 0L
    private var pendingDot: StrokePoint? = null
    var myColorIndex: Int = 0

    /** Aktuelle Pinseldicke für den Live-Strich und neue Zeichnungen. */
    var myBrushWidth: Float = 18f
        set(value) {
            field = value.coerceIn(6f, 40f)
            invalidate()
        }

    /** Expliziter Leinwand-Ton — verhindert „schwarzen“ Flash ohne Strokes. */
    var canvasBackground: Int = 0xFF0E1A24.toInt()
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** Aktiv: Tippen/Ziehen radiert nur die Stelle, über die man wischt. */
    var eraserEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) {
                eraserPath.reset()
                invalidate()
            }
        }

    var showTicTacToe: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var showDotGrid: Boolean = false
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
    var onDoubleTapUndo: (() -> Unit)? = null
    var onDotPlaced: ((StrokePoint) -> Unit)? = null
    /** Normalisierte Brush-Proben — nur eigene Malerei an diesen Stellen. */
    var onErasePath: ((List<StrokePoint>) -> Unit)? = null

    /** Nach 0,5 s ohne zweiten Tipp → Punkt setzen */
    private val singleTapDotRunnable = Runnable {
        val dot = pendingDot ?: return@Runnable
        pendingDot = null
        lastTapUptime = 0L
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        onDotPlaced?.invoke(dot)
    }

    fun setStrokes(list: List<Stroke>, animateNew: Boolean = false) {
        if (
            !animateNew &&
            list.size == strokes.size &&
            list.zip(strokes).all { (a, b) ->
                a.id == b.id &&
                    a.colorIndex == b.colorIndex &&
                    a.emoji == b.emoji &&
                    a.points.size == b.points.size
            }
        ) {
            return
        }
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
        canvas.drawColor(canvasBackground)
        if (showDotGrid) drawDotGrid(canvas)
        if (showTicTacToe) drawTicTacToeBoard(canvas)
        // Reihenfolge = Leinwand-Z-Order (Emojis sind normale Striche)
        if (width > 0 && height > 0) {
            emojiPaint.textSize = min(width, height) * 0.075f
        }
        val fm = emojiPaint.fontMetrics
        val baselinePad = (fm.bottom + fm.top) / 2f
        strokes.forEach { stroke ->
            val alpha = ((alphas[stroke.id] ?: 1f) * 255).toInt().coerceIn(0, 255)
            if (stroke.isEmoji) {
                val p = stroke.points.firstOrNull() ?: return@forEach
                emojiPaint.alpha = alpha
                canvas.drawText(
                    stroke.emoji.orEmpty(),
                    p.x * width,
                    p.y * height - baselinePad,
                    emojiPaint
                )
                return@forEach
            }
            if (stroke.isTemplate) {
                drawTemplateStroke(canvas, stroke, alpha)
                return@forEach
            }
            val color = CanvasStore.strokeColor(stroke)
            paint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
            paint.strokeWidth = CanvasStore.strokeWidthPx(
                stroke,
                min(width, height).toFloat().coerceAtLeast(1f)
            )
            canvas.drawPath(pathFrom(stroke.points), paint)
        }
        if (eraserEnabled) {
            if (!eraserPath.isEmpty) { // Path.isEmpty() API 19+
                paint.color = 0xCCFFD56A.toInt()
                paint.strokeWidth = 28f
                canvas.drawPath(eraserPath, paint)
                paint.color = 0x66FFD56A.toInt()
                paint.strokeWidth = 44f
                canvas.drawPath(eraserPath, paint)
            }
        } else {
            paint.color = PeerPalette.strokeColor(myColorIndex)
            paint.strokeWidth = myBrushWidth
            canvas.drawPath(currentPath, paint)
        }
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

    private fun drawTemplateStroke(canvas: Canvas, stroke: Stroke, alpha: Int) {
        val parts = stroke.templateParts ?: return
        val center = stroke.points.firstOrNull() ?: return
        val cx = center.x * width
        val cy = center.y * height
        val scale = stroke.templateScale.coerceIn(0.2f, 4f)
        val box = min(width, height) * 0.4f * scale
        val rad = Math.toRadians(stroke.templateRotation.toDouble())
        val cos = kotlin.math.cos(rad).toFloat()
        val sin = kotlin.math.sin(rad).toFloat()
        val short = min(width, height).toFloat().coerceAtLeast(1f)
        parts.forEach { part ->
            if (part.points.size < 2) return@forEach
            val color = PeerPalette.strokeColor(part.colorIndex)
            paint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
            paint.strokeWidth = (part.width / CanvasStore.WIDTH_REF) * short * scale
            val path = Path()
            part.points.forEachIndexed { idx, p ->
                val lx = (p.x - 0.5f) * box
                val ly = (p.y - 0.5f) * box
                val rx = lx * cos - ly * sin
                val ry = lx * sin + ly * cos
                val x = cx + rx
                val y = cy + ry
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun drawDotGrid(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val dens = resources.displayMetrics.density
        val cols = 8
        val rows = 10
        val padX = w * 0.12f
        val padY = h * 0.14f
        val usableW = w - padX * 2f
        val usableH = h - padY * 2f
        val stepX = usableW / (cols - 1)
        val stepY = usableH / (rows - 1)
        val r = 2.4f * dens
        val paintDots = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x55FFFFFF
        }
        val paintSoft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x22FFFFFF
        }
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = padX + col * stepX
                val y = padY + row * stepY
                canvas.drawCircle(x, y, r * 1.8f, paintSoft)
                canvas.drawCircle(x, y, r, paintDots)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val normalized = StrokePoint(
            x = (event.x / w).coerceIn(0f, 1f),
            y = (event.y / h).coerceIn(0f, 1f)
        )
        if (eraserEnabled) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelPendingDot()
                    eraserPath.reset()
                    eraserPath.moveTo(event.x, event.y)
                    eraseLastX = event.x
                    eraseLastY = event.y
                    parent.requestDisallowInterceptTouchEvent(true)
                    onErasePath?.invoke(listOf(normalized))
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    eraserPath.lineTo(event.x, event.y)
                    val samples = sampleEraseSegment(
                        fromX = eraseLastX,
                        fromY = eraseLastY,
                        toX = event.x,
                        toY = event.y,
                        w = w,
                        h = h
                    )
                    eraseLastX = event.x
                    eraseLastY = event.y
                    if (samples.isNotEmpty()) onErasePath?.invoke(samples)
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    eraserPath.reset()
                    invalidate()
                }
            }
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                movedBeyondSlop = false
                downX = event.x
                downY = event.y
                currentPoints.clear()
                currentPath.reset()
                currentPoints.add(normalized)
                currentPath.moveTo(event.x, event.y)
                parent.requestDisallowInterceptTouchEvent(true)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val distance = hypot(event.x - downX, event.y - downY)
                if (distance > touchSlop) {
                    movedBeyondSlop = true
                    cancelPendingDot()
                }
                currentPoints.add(normalized)
                currentPath.lineTo(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!movedBeyondSlop && event.actionMasked == MotionEvent.ACTION_UP) {
                    handleTap(normalized)
                } else if (movedBeyondSlop && currentPoints.size >= 2) {
                    cancelPendingDot()
                    lastTapUptime = 0L
                    onStrokeFinished?.invoke(currentPoints.toList())
                }
                currentPoints.clear()
                currentPath.reset()
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

    private fun sampleEraseSegment(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        w: Float,
        h: Float
    ): List<StrokePoint> {
        val dist = hypot(toX - fromX, toY - fromY)
        val stepPx = 10f * resources.displayMetrics.density
        val steps = (dist / stepPx).toInt().coerceAtLeast(1)
        return buildList(steps) {
            for (i in 1..steps) {
                val t = i / steps.toFloat()
                val x = fromX + (toX - fromX) * t
                val y = fromY + (toY - fromY) * t
                add(
                    StrokePoint(
                        x = (x / w).coerceIn(0f, 1f),
                        y = (y / h).coerceIn(0f, 1f)
                    )
                )
            }
        }
    }

    companion object {
        private const val DOUBLE_TAP_MS = 500L
    }
}
