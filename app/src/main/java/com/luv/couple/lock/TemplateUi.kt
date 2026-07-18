package com.luv.couple.lock

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.luv.couple.data.DrawTemplate
import com.luv.couple.data.PeerPalette
import com.luv.couple.data.StrokePoint
import com.luv.couple.data.TemplateStrokePart
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import kotlin.math.min

private val SheetBg = Color(0xFF121824)
private val CardBg = Color(0xFF1C2433)
private val Accent = Color(0xFF2EE6A8)
private val Danger = Color(0xFFFF6B7A)

@Composable
fun TemplatesBrowserSheet(
    templates: List<DrawTemplate>,
    loading: Boolean,
    onRefresh: () -> Unit,
    onCreate: () -> Unit,
    onSelect: (DrawTemplate) -> Unit,
    onDelete: (DrawTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<DrawTemplate?>(null) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.55f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        val sheetW = maxWidth * 0.92f
        val sheetH = maxHeight * 0.82f
        Column(
            modifier = Modifier
                .width(sheetW)
                .height(sheetH)
                .clip(RoundedCornerShape(24.dp))
                .background(SheetBg)
                .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Vorlagen",
                        color = TextPrimary,
                        fontFamily = DisplayFont,
                        fontSize = 26.sp
                    )
                    if (templates.isNotEmpty()) {
                        Text(
                            "Tippen = platzieren · Papierkorb = löschen",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 11.sp
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Accent.copy(0.22f))
                        .border(1.dp, Accent.copy(0.55f), CircleShape)
                        .clickable(onClick = onCreate),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = Accent, fontFamily = DisplayFont, fontSize = 26.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.08f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text("X", color = TextMuted, fontSize = 16.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (loading && templates.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Lade Vorlagen...", color = TextMuted, fontFamily = BodyFont)
                }
            } else if (templates.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Noch keine Vorlagen",
                        color = TextPrimary,
                        fontFamily = DisplayFont,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Tippe +, um etwas zu zeichnen.",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(templates, key = { it.id }) { tpl ->
                        TemplateThumb(
                            template = tpl,
                            onClick = { onSelect(tpl) },
                            onDelete = { pendingDelete = tpl }
                        )
                    }
                }
            }
        }
    }
    pendingDelete?.let { tpl ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = {
                Text(
                    "Vorlage löschen?",
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    fontSize = 20.sp
                )
            },
            text = {
                Text(
                    "Die Vorlage wird geräteübergreifend entfernt.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(tpl)
                    }
                ) {
                    Text("Löschen", color = Danger, fontFamily = DisplayFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Abbrechen", color = TextMuted, fontFamily = BodyFont)
                }
            }
        )
    }
}

@Composable
private fun TemplateThumb(
    template: DrawTemplate,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(18.dp))
    ) {
        TemplatePreviewCanvas(
            parts = template.strokes,
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
                .padding(8.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(0.45f))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Text("🗑", fontSize = 14.sp)
        }
    }
}

@Composable
fun TemplateEditorSheet(
    onSave: (List<TemplateStrokePart>) -> Unit,
    onDismiss: () -> Unit
) {
    val parts = remember { mutableStateListOf<TemplateStrokePart>() }
    var colorIndex by remember { mutableIntStateOf(0) }
    var brushWidth by remember { mutableFloatStateOf(18f) }
    var currentPoints by remember { mutableStateOf<List<StrokePoint>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Vollbild — kein Tippen am Rand schließt (Stift/Tablet). Nur ✕ oder Speichern.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheetBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Neue Vorlage",
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(0.08f))
                    .clickable(enabled = parts.isNotEmpty()) {
                        if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Rückgängig",
                    color = if (parts.isEmpty()) TextMuted.copy(0.45f) else TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (parts.isEmpty() || saving) Accent.copy(0.35f) else Accent)
                    .clickable(enabled = parts.isNotEmpty() && !saving) {
                        saving = true
                        scope.launch {
                            onSave(parts.toList())
                            saving = false
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (saving) "…" else "Speichern",
                    color = Color(0xFF0A1018),
                    fontFamily = DisplayFont,
                    fontSize = 14.sp
                )
            }
            Text(
                "✕",
                color = TextMuted,
                fontSize = 18.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .padding(10.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(8) { i ->
                val c = Color(PeerPalette.strokeColor(i))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(c)
                        .border(
                            if (colorIndex == i) 2.dp else 0.dp,
                            Color.White,
                            CircleShape
                        )
                        .clickable { colorIndex = i }
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(8) { j ->
                val i = j + 8
                val c = Color(PeerPalette.strokeColor(i))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(c)
                        .border(
                            if (colorIndex == i) 2.dp else 0.dp,
                            Color.White,
                            CircleShape
                        )
                        .clickable { colorIndex = i }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Pinsel",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 13.sp
            )
            Slider(
                value = brushWidth,
                onValueChange = { brushWidth = it.coerceIn(6f, 40f) },
                valueRange = 6f..40f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = Color.White.copy(0.12f)
                )
            )
            Text(
                "${brushWidth.roundToInt()}",
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 14.sp,
                modifier = Modifier.width(28.dp),
                textAlign = TextAlign.End
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0A1018))
                .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(18.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(colorIndex, brushWidth, parts.size) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val nx = (offset.x / size.width).coerceIn(0f, 1f)
                                val ny = (offset.y / size.height).coerceIn(0f, 1f)
                                currentPoints = listOf(StrokePoint(nx, ny))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                                val ny = (change.position.y / size.height).coerceIn(0f, 1f)
                                currentPoints = currentPoints + StrokePoint(nx, ny)
                            },
                            onDragEnd = {
                                if (currentPoints.size >= 2) {
                                    parts.add(
                                        TemplateStrokePart(
                                            points = currentPoints.toList(),
                                            width = brushWidth,
                                            colorIndex = colorIndex
                                        )
                                    )
                                }
                                currentPoints = emptyList()
                            },
                            onDragCancel = { currentPoints = emptyList() }
                        )
                    }
            ) {
                val short = min(size.width, size.height)
                fun drawPart(part: TemplateStrokePart, alpha: Float = 1f) {
                    if (part.points.size < 2) return
                    val path = Path()
                    part.points.forEachIndexed { idx, p ->
                        val x = p.x * size.width
                        val y = p.y * size.height
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    val col = Color(PeerPalette.strokeColor(part.colorIndex)).copy(alpha = alpha)
                    drawPath(
                        path,
                        color = col,
                        style = Stroke(
                            width = (part.width / 1000f) * short,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
                parts.forEach { drawPart(it) }
                if (currentPoints.size >= 2) {
                    drawPart(
                        TemplateStrokePart(currentPoints, brushWidth, colorIndex),
                        alpha = 0.9f
                    )
                }
            }
        }
    }
}

@Composable
fun TemplatePreviewCanvas(
    parts: List<TemplateStrokePart>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val short = min(size.width, size.height)
        parts.forEach { part ->
            if (part.points.size < 2) return@forEach
            val path = Path()
            part.points.forEachIndexed { idx, p ->
                val x = p.x * size.width
                val y = p.y * size.height
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path,
                color = Color(PeerPalette.strokeColor(part.colorIndex)),
                style = Stroke(
                    width = (part.width / 1000f * short).coerceAtLeast(2f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

/**
 * Overlay zum Platzieren: 2-Finger Scale/Rotate, grüner Haken in der Mitte.
 */
class TemplatePlacementView(
    context: android.content.Context
) : android.widget.FrameLayout(context) {
    var parts: List<TemplateStrokePart> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    var centerXNorm = 0.5f
    var centerYNorm = 0.5f
    var scaleFactor = 1f
    var rotationDeg = 0f
    var onConfirm: ((cx: Float, cy: Float, scale: Float, rotation: Float) -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    private val checkPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val checkText = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
    }
    private var lastSpan = 0f
    private var lastAngle = 0f
    private var dragging = false
    private var lastX = 0f
    private var lastY = 0f

    init {
        setWillNotDraw(false)
        isClickable = true
        setBackgroundColor(0x33000000)
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val cx = centerXNorm * width
        val cy = centerYNorm * height
        val box = min(width, height) * 0.4f * scaleFactor
        val rad = Math.toRadians(rotationDeg.toDouble())
        val cos = kotlin.math.cos(rad).toFloat()
        val sin = kotlin.math.sin(rad).toFloat()
        parts.forEach { part ->
            if (part.points.size < 2) return@forEach
            paint.color = PeerPalette.strokeColor(part.colorIndex)
            paint.strokeWidth = (part.width / 1000f) * min(width, height) * scaleFactor
            val path = android.graphics.Path()
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
        val r = min(width, height) * 0.055f
        checkPaint.color = 0xFF2EE6A8.toInt()
        canvas.drawCircle(cx, cy, r, checkPaint)
        checkText.textSize = r * 1.1f
        val fm = checkText.fontMetrics
        canvas.drawText("✓", cx, cy - (fm.ascent + fm.descent) / 2f, checkText)
        // Abbrechen oben rechts
        checkPaint.color = 0x88FFFFFF.toInt()
        val cr = r * 0.75f
        canvas.drawCircle(width - cr * 2.2f, cr * 2.2f, cr, checkPaint)
        checkText.textSize = cr
        canvas.drawText("✕", width - cr * 2.2f, cr * 2.2f - (fm.ascent + fm.descent) / 2f, checkText)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val cx = centerXNorm * width
                val cy = centerYNorm * height
                val r = min(width, height) * 0.055f
                val dx = event.x - cx
                val dy = event.y - cy
                val cancelX = width - r * 0.75f * 2.2f
                val cancelY = r * 0.75f * 2.2f
                if ((event.x - cancelX) * (event.x - cancelX) + (event.y - cancelY) * (event.y - cancelY) <= (r * 0.9f) * (r * 0.9f)) {
                    onCancel?.invoke()
                    return true
                }
                if (dx * dx + dy * dy <= r * r * 2.2f) {
                    onConfirm?.invoke(centerXNorm, centerYNorm, scaleFactor, rotationDeg)
                    return true
                }
                dragging = true
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    lastSpan = span(event)
                    lastAngle = angle(event)
                    dragging = false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val span = span(event)
                    if (lastSpan > 8f && span > 8f) {
                        scaleFactor = (scaleFactor * (span / lastSpan)).coerceIn(0.35f, 3.2f)
                    }
                    lastSpan = span
                    val ang = angle(event)
                    rotationDeg = (rotationDeg + (ang - lastAngle)).let {
                        var v = it % 360f
                        if (v > 180f) v -= 360f
                        if (v < -180f) v += 360f
                        v
                    }
                    lastAngle = ang
                    invalidate()
                } else if (dragging) {
                    val dx = (event.x - lastX) / width.toFloat()
                    val dy = (event.y - lastY) / height.toFloat()
                    centerXNorm = (centerXNorm + dx).coerceIn(0.08f, 0.92f)
                    centerYNorm = (centerYNorm + dy).coerceIn(0.08f, 0.92f)
                    lastX = event.x
                    lastY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                lastSpan = 0f
            }
            MotionEvent.ACTION_POINTER_UP -> {
                lastSpan = 0f
            }
        }
        return true
    }

    private fun span(e: MotionEvent): Float {
        if (e.pointerCount < 2) return 0f
        val dx = e.getX(0) - e.getX(1)
        val dy = e.getY(0) - e.getY(1)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun angle(e: MotionEvent): Float {
        if (e.pointerCount < 2) return 0f
        val dx = e.getX(1) - e.getX(0)
        val dy = e.getY(1) - e.getY(0)
        return Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }
}
