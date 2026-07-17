package com.luv.couple.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luv.couple.R
import kotlin.math.cos
import kotlin.math.sin

val MaleBlue = Color(0xFF00B7E4)
val FemalePurple = Color(0xFFC218A8)
val BgDeep = Color(0xFF0E1116)
val BgSoft = Color(0xFF171C24)
val TextPrimary = Color(0xFFF4F1EC)
val TextMuted = Color(0xFF9AA3B2)
val AccentRose = Color(0xFFFF6B8A)

private val ColorScheme = darkColorScheme(
    primary = AccentRose,
    secondary = MaleBlue,
    tertiary = FemalePurple,
    background = BgDeep,
    surface = BgSoft,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

/** Wie Website: Fraunces (Display) + Outfit (Body) */
val DisplayFont = FontFamily(
    Font(R.font.fraunces_medium, FontWeight.Medium),
    Font(R.font.fraunces_bold, FontWeight.Bold),
    Font(R.font.fraunces_bold, FontWeight.Black)
)
val BodyFont = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_semibold, FontWeight.Bold)
)

/**
 * Weißes Outline-Herz wie auf dem Sperrbildschirm / Web-Demo:
 * hochgezogen, gezeichnet (nur Strich), kein Emoji.
 */
@Composable
fun SketchedHeart(
    size: Dp,
    color: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val padX = w * 0.10f
        val padY = h * 0.06f
        val drawW = w - padX * 2f
        val drawH = h - padY * 2f
        // Leicht schmaler als hoch — wie im Foto
        val path = Path()
        val steps = 80
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val a = (Math.PI * 2.0 * t).toFloat()
            val s = sin(a)
            val hx = 16f * s * s * s
            val hy = -(
                13f * cos(a) -
                    5f * cos(2f * a) -
                    2f * cos(3f * a) -
                    cos(4f * a)
            )
            // Normiert ~0..1, vertikal etwas gestreckt
            val nx = 0.5f + hx / 40f
            val ny = 0.50f + hy / 34f
            val x = padX + nx * drawW
            val y = padY + ny * drawH
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = (w * 0.095f).coerceIn(2.4f, 6f),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/** L U V in Website-Farben (Blau · Verlauf · Lila) + gezeichnetes weißes Herz */
@Composable
fun LuvWordmark(
    fontSize: TextUnit = 40.sp,
    showHeart: Boolean = false,
    modifier: Modifier = Modifier
) {
    val uBrush = Brush.horizontalGradient(listOf(MaleBlue, FemalePurple))
    val heartSize = (fontSize.value * 0.72f).dp
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            "L",
            color = MaleBlue,
            fontFamily = DisplayFont,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            letterSpacing = (fontSize.value * 0.12f).sp,
            style = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        )
        Text(
            "U",
            fontFamily = DisplayFont,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            letterSpacing = (fontSize.value * 0.12f).sp,
            style = TextStyle(
                brush = uBrush,
                fontFamily = DisplayFont,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize
            )
        )
        Text(
            "V",
            color = FemalePurple,
            fontFamily = DisplayFont,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            letterSpacing = (fontSize.value * 0.04f).sp
        )
        if (showHeart) {
            SketchedHeart(
                size = heartSize,
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun LuvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = MaterialTheme.typography.copy(
            displayLarge = TextStyle(
                fontFamily = DisplayFont,
                fontWeight = FontWeight.Bold,
                fontSize = 56.sp,
                color = TextPrimary,
                letterSpacing = 2.sp
            ),
            headlineMedium = TextStyle(
                fontFamily = DisplayFont,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = TextPrimary
            ),
            bodyLarge = TextStyle(
                fontFamily = BodyFont,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = TextPrimary
            ),
            bodyMedium = TextStyle(
                fontFamily = BodyFont,
                fontSize = 14.sp,
                color = TextMuted
            )
        ),
        content = content
    )
}
