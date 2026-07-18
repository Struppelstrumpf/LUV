package com.luv.couple.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import com.luv.couple.data.PeerPalette
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlin.math.abs
import kotlin.math.roundToInt

private const val BrushMin = 6f
private const val BrushMax = 40f

/** Referenzhöhe für Scale 1.0 — darunter wird alles proportional kleiner. */
private val RefSheetHeight = 560.dp

/**
 * Vollflächiges Overlay ohne Dialog — alles sichtbar ohne Scrollen,
 * skaliert nach Bildschirmhöhe, sitzt etwas höher als ganz unten.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrushStudioSheet(
    selectedColor: Int,
    takenColors: Set<Int>,
    brushWidth: Float,
    onColorPick: (Int) -> Unit,
    onBrushWidthChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var color by remember(selectedColor) { mutableIntStateOf(selectedColor) }
    var width by remember(brushWidth) {
        mutableFloatStateOf(brushWidth.coerceIn(BrushMin, BrushMax))
    }
    val strokeColor = Color(PeerPalette.strokeColor(color))

    val bottomSafe = WindowInsets.navigationBarsIgnoringVisibility
        .union(WindowInsets.mandatorySystemGestures)
        .union(WindowInsets.systemGestures)
        .only(WindowInsetsSides.Bottom)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Kein Scrim — Canvas dahinter bleibt klar sichtbar
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss
            )
            .windowInsetsPadding(bottomSafe)
            .padding(bottom = 20.dp),
        // Etwas höher als ganz unten — mehr Luft unter dem Sheet
        contentAlignment = Alignment.Center
    ) {
        val sidePad = when {
            maxWidth < 340.dp -> 10.dp
            maxWidth < 400.dp -> 14.dp
            else -> 18.dp
        }
        // Verfügbarer Platz fürs Sheet (zentriert, etwas Luft oben/unten)
        val budget = maxHeight * 0.82f
        val scale = (budget / RefSheetHeight).coerceIn(0.58f, 1f)
        fun s(dp: Dp): Dp = dp * scale
        fun ts(sp: TextUnit): TextUnit = (sp.value * scale).sp

        val cols = when {
            maxWidth < 340.dp -> 4
            maxWidth < 400.dp -> 5
            else -> 6
        }
        val gap = s(10.dp)
        val showSubtitle = scale >= 0.78f

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .padding(horizontal = sidePad)
                // Leicht nach oben verschieben innerhalb der Center-Ausrichtung
                .padding(bottom = maxHeight * 0.04f)
                .shadow(s(24.dp), RoundedCornerShape(s(26.dp)), clip = false)
                .clip(RoundedCornerShape(s(26.dp)))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF1C2433),
                            BgDeep,
                            Color(0xFF12161E)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(0.10f), RoundedCornerShape(s(26.dp)))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {}
                )
                .padding(horizontal = s(16.dp), vertical = s(12.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            Box(
                modifier = Modifier
                    .width(s(36.dp))
                    .height(s(4.dp))
                    .clip(CircleShape)
                    .background(Color.White.copy(0.22f))
            )
            Text(
                "Pinsel",
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = ts(24.sp)
            )
            if (showSubtitle) {
                Text(
                    "Farbe wählen · Dicke einstellen",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = ts(12.sp),
                    textAlign = TextAlign.Center
                )
            }

            BrushPreviewCard(
                color = strokeColor,
                brushWidth = width,
                height = s(72.dp)
            )

            SectionLabel("Farbe", fontSize = ts(11.sp))
            ColorSwatchGrid(
                selected = color,
                taken = takenColors,
                columns = cols,
                gap = s(6.dp),
                onPick = { idx ->
                    color = idx
                    onColorPick(idx)
                    // Farbe übernehmen und schließen — Fertig nur für Pinselstärke
                    onDismiss()
                }
            )

            SectionLabel("Dicke", fontSize = ts(11.sp))
            ThicknessControl(
                width = width,
                color = strokeColor,
                scale = scale,
                onChange = {
                    width = it
                    onBrushWidthChange(it)
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(s(46.dp))
                    .clip(RoundedCornerShape(s(14.dp)))
                    .background(
                        Brush.horizontalGradient(
                            listOf(AccentRose.copy(0.92f), Color(0xFFFF8FA3))
                        )
                    )
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Fertig",
                    color = Color.White,
                    fontFamily = DisplayFont,
                    fontSize = ts(16.sp)
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, fontSize: TextUnit = 12.sp) {
    Text(
        text,
        color = TextMuted,
        fontFamily = BodyFont,
        fontSize = fontSize,
        letterSpacing = 0.6.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp)
    )
}

@Composable
private fun BrushPreviewCard(
    color: Color,
    brushWidth: Float,
    height: Dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF243044), Color(0xFF141A24)),
                    radius = 700f
                )
            )
            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * 0.08f, h * 0.62f)
                cubicTo(
                    w * 0.28f, h * 0.12f,
                    w * 0.48f, h * 0.92f,
                    w * 0.72f, h * 0.38f
                )
                cubicTo(
                    w * 0.82f, h * 0.18f,
                    w * 0.88f, h * 0.55f,
                    w * 0.94f, h * 0.48f
                )
            }
            val stroke = brushWidth.coerceIn(BrushMin, BrushMax) * (h / 72f).coerceIn(0.7f, 1.2f)
            drawPath(
                path = path,
                color = color.copy(alpha = 0.22f),
                style = Stroke(
                    width = stroke * 1.85f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

@Composable
private fun ColorSwatchGrid(
    selected: Int,
    taken: Set<Int>,
    columns: Int,
    gap: Dp,
    onPick: (Int) -> Unit
) {
    val indices = remember { (0 until PeerPalette.COLOR_COUNT).toList() }
    val rows = indices.chunked(columns)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                row.forEach { index ->
                    val blocked = index in taken && index != selected
                    val swatch = Color(PeerPalette.strokeColor(index))
                    val selectedScale by animateFloatAsState(
                        targetValue = if (index == selected) 1.08f else 1f,
                        animationSpec = spring(stiffness = 500f),
                        label = "swatchScale$index"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .graphicsLayer {
                                scaleX = selectedScale
                                scaleY = selectedScale
                                alpha = if (blocked) 0.28f else 1f
                            }
                            .shadow(
                                if (index == selected) 6.dp else 0.dp,
                                CircleShape,
                                clip = false
                            )
                            .clip(CircleShape)
                            .background(swatch)
                            .border(
                                width = if (index == selected) 2.dp else 1.dp,
                                color = if (index == selected) {
                                    Color.White
                                } else {
                                    Color.White.copy(0.18f)
                                },
                                shape = CircleShape
                            )
                            .clickable(enabled = !blocked) { onPick(index) }
                    )
                }
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ThicknessControl(
    width: Float,
    color: Color,
    scale: Float,
    onChange: (Float) -> Unit
) {
    val presets = listOf(8f, 14f, 22f, 32f)
    val labels = listOf("Fein", "Mittel", "Kräftig", "Bold")
    fun s(dp: Dp): Dp = dp * scale
    fun ts(sp: TextUnit): TextUnit = (sp.value * scale).sp
    Column(verticalArrangement = Arrangement.spacedBy(s(8.dp))) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(s(6.dp))
        ) {
            presets.forEachIndexed { i, preset ->
                val active = abs(width - preset) < 2.5f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(s(36.dp))
                        .clip(RoundedCornerShape(s(10.dp)))
                        .background(
                            if (active) color.copy(alpha = 0.22f)
                            else Color.White.copy(0.06f)
                        )
                        .border(
                            1.dp,
                            if (active) color.copy(alpha = 0.55f)
                            else Color.White.copy(0.08f),
                            RoundedCornerShape(s(10.dp))
                        )
                        .clickable { onChange(preset) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        labels[i],
                        color = if (active) TextPrimary else TextMuted,
                        fontFamily = if (active) DisplayFont else BodyFont,
                        fontSize = ts(11.sp),
                        maxLines = 1
                    )
                }
            }
        }

        BrushWidthSlider(
            value = width,
            color = color,
            height = s(40.dp),
            onChange = onChange
        )

        Text(
            "${width.roundToInt()} px",
            color = TextMuted,
            fontFamily = BodyFont,
            fontSize = ts(11.sp),
            modifier = Modifier.align(Alignment.End)
        )
    }
}

@Composable
private fun BrushWidthSlider(
    value: Float,
    color: Color,
    height: Dp,
    onChange: (Float) -> Unit
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(0.05f))
            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp)
    ) {
        val trackW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val frac = ((value - BrushMin) / (BrushMax - BrushMin)).coerceIn(0f, 1f)
        val thumbR = with(density) { 10.dp.toPx() }

        fun widthAt(x: Float): Float {
            val t = (x / trackW).coerceIn(0f, 1f)
            return BrushMin + t * (BrushMax - BrushMin)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(trackW) {
                    detectTapGestures { offset -> onChange(widthAt(offset.x)) }
                }
                .pointerInput(trackW) {
                    detectDragGestures(
                        onDragStart = { offset -> onChange(widthAt(offset.x)) },
                        onDrag = { change, _ ->
                            change.consume()
                            onChange(widthAt(change.position.x))
                        }
                    )
                }
        ) {
            val cy = size.height / 2f
            val left = thumbR
            val right = size.width - thumbR
            val usable = (right - left).coerceAtLeast(1f)
            val tx = left + frac * usable

            val path = Path().apply {
                moveTo(left, cy - 2f)
                lineTo(right, cy - 9f)
                lineTo(right, cy + 9f)
                lineTo(left, cy + 2f)
                close()
            }
            drawPath(path, Color.White.copy(alpha = 0.12f))
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(color.copy(alpha = 0.25f), color)
                ),
                topLeft = Offset(left, cy - 3f),
                size = Size((tx - left).coerceAtLeast(0f), 6f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.18f),
                radius = thumbR + 4f,
                center = Offset(tx, cy)
            )
            drawCircle(
                color = color,
                radius = thumbR,
                center = Offset(tx, cy)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = 3.2f,
                center = Offset(tx, cy)
            )
        }
    }
}
