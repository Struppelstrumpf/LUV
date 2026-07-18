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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * Vollflächiges Overlay (kein Dialog-Fenster) — damit Insets der Lock-Activity
 * greifen und „Fertig“ auf Pixel/Samsung über der Gesture-Leiste bleibt.
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

    // Immersive Lock blendet Nav-Bars aus → Insets oft 0. IgnoringVisibility + Gestures erzwingen Abstand.
    val bottomSafe = WindowInsets.navigationBarsIgnoringVisibility
        .union(WindowInsets.mandatorySystemGestures)
        .union(WindowInsets.systemGestures)
        .only(WindowInsetsSides.Bottom)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.52f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss
            )
            .windowInsetsPadding(bottomSafe)
            // Extra Puffer für Geräte, die Gestures unterschätzen (Pixel 10 / Samsung)
            .padding(bottom = 28.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        val shortScreen = maxHeight < 640.dp
        val veryShort = maxHeight < 560.dp
        val sidePad = when {
            maxWidth < 340.dp -> 10.dp
            maxWidth < 400.dp -> 14.dp
            else -> 20.dp
        }
        val cols = when {
            maxWidth < 340.dp -> 4
            maxWidth < 420.dp -> 5
            else -> 6
        }
        val previewH = when {
            veryShort -> 56.dp
            shortScreen -> 68.dp
            maxHeight < 740.dp -> 88.dp
            else -> 104.dp
        }
        val titleSize = if (veryShort) 22.sp else 26.sp
        val innerPadV = if (veryShort) 10.dp else 14.dp
        val doneH = if (veryShort) 44.dp else 48.dp
        val sheetMaxH = maxHeight * (if (veryShort) 0.90f else if (shortScreen) 0.86f else 0.82f)
        val headerBlock = if (veryShort) 72.dp else 96.dp
        val footerBlock = doneH + 24.dp
        val scrollMaxH = (sheetMaxH - headerBlock - footerBlock).coerceAtLeast(120.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .heightIn(max = sheetMaxH)
                .padding(horizontal = sidePad)
                .shadow(28.dp, RoundedCornerShape(28.dp), clip = false)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF1C2433),
                            BgDeep,
                            Color(0xFF12161E)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(0.10f), RoundedCornerShape(28.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {}
                )
                .padding(horizontal = 18.dp, vertical = innerPadV),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.22f))
            )
            Spacer(modifier = Modifier.height(if (veryShort) 8.dp else 12.dp))
            Text(
                "Pinsel",
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = titleSize
            )
            if (!veryShort) {
                Text(
                    "Farbe wählen · Dicke einstellen",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = scrollMaxH)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(if (veryShort) 10.dp else 14.dp)
            ) {
                BrushPreviewCard(
                    color = strokeColor,
                    brushWidth = width,
                    height = previewH
                )

                SectionLabel("Farbe")
                ColorSwatchGrid(
                    selected = color,
                    taken = takenColors,
                    columns = cols,
                    onPick = { idx ->
                        color = idx
                        onColorPick(idx)
                    }
                )

                SectionLabel("Dicke")
                ThicknessControl(
                    width = width,
                    color = strokeColor,
                    compact = veryShort,
                    onChange = {
                        width = it
                        onBrushWidthChange(it)
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(if (veryShort) 8.dp else 12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(doneH)
                    .clip(RoundedCornerShape(16.dp))
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
                    fontSize = if (veryShort) 16.sp else 17.sp
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = TextMuted,
        fontFamily = BodyFont,
        fontSize = 12.sp,
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
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF243044), Color(0xFF141A24)),
                    radius = 700f
                )
            )
            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
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
            drawPath(
                path = path,
                color = color.copy(alpha = 0.22f),
                style = Stroke(
                    width = brushWidth * 1.85f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = brushWidth,
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
    onPick: (Int) -> Unit
) {
    val indices = remember { (0 until PeerPalette.COLOR_COUNT).toList() }
    val rows = indices.chunked(columns)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                if (index == selected) 8.dp else 0.dp,
                                CircleShape,
                                clip = false
                            )
                            .clip(CircleShape)
                            .background(swatch)
                            .border(
                                width = if (index == selected) 2.5.dp else 1.dp,
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
    compact: Boolean = false,
    onChange: (Float) -> Unit
) {
    val presets = listOf(8f, 14f, 22f, 32f)
    val labels = listOf("Fein", "Mittel", "Kräftig", "Bold")
    val chipH = if (compact) 36.dp else 40.dp
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEachIndexed { i, preset ->
                val active = abs(width - preset) < 2.5f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(chipH)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (active) color.copy(alpha = 0.22f)
                            else Color.White.copy(0.06f)
                        )
                        .border(
                            1.dp,
                            if (active) color.copy(alpha = 0.55f)
                            else Color.White.copy(0.08f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onChange(preset) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        labels[i],
                        color = if (active) TextPrimary else TextMuted,
                        fontFamily = if (active) DisplayFont else BodyFont,
                        fontSize = if (compact) 11.sp else 12.sp,
                        maxLines = 1
                    )
                }
            }
        }

        BrushWidthSlider(
            value = width,
            color = color,
            compact = compact,
            onChange = onChange
        )

        Text(
            "${width.roundToInt()} px",
            color = TextMuted,
            fontFamily = BodyFont,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

@Composable
private fun BrushWidthSlider(
    value: Float,
    color: Color,
    compact: Boolean = false,
    onChange: (Float) -> Unit
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 40.dp else 44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(0.05f))
            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp)
    ) {
        val trackW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val frac = ((value - BrushMin) / (BrushMax - BrushMin)).coerceIn(0f, 1f)
        val thumbR = with(density) { 11.dp.toPx() }

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
