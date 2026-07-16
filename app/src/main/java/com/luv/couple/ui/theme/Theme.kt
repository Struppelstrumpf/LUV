package com.luv.couple.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

// Geometrische Display-Schrift über Android's eingebaute Sans-Serif mit starkem Gewicht.
val DisplayFont = FontFamily.SansSerif
val BodyFont = FontFamily.SansSerif

@Composable
fun LuvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = MaterialTheme.typography.copy(
            displayLarge = TextStyle(
                fontFamily = DisplayFont,
                fontWeight = FontWeight.Black,
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
