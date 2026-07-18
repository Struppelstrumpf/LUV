package com.luv.couple.ui

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times

/**
 * Feste Referenzbreite — Layout skaliert proportional, Anordnung bleibt gleich.
 * Kein Textkürzen / Umsortieren je nach Gerät.
 */
object FixedUiScale {
    val RefWidth = 390.dp
    const val MinScale = 0.72f
    const val MaxScale = 1.0f

    fun of(maxWidth: Dp): Float =
        (maxWidth / RefWidth).coerceIn(MinScale, MaxScale)
}

class UiScale(val value: Float) {
    fun s(dp: Dp): Dp = dp * value
    fun ts(sp: TextUnit): TextUnit = (sp.value * value).sp
    fun ts(sp: Float): TextUnit = (sp * value).sp
}

@Composable
fun BoxWithConstraintsScope.rememberUiScale(): UiScale {
    val v = FixedUiScale.of(maxWidth)
    return remember(v) { UiScale(v) }
}
