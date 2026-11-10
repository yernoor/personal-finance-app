package dev.yernur.moneytracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import kotlin.math.roundToLong

/** [from] moved [fraction] (0..1) of the way to [to], in whole minor units. At 1 it is exactly [to] (a Float would lose cents on big balances). */
internal fun rolled(from: Long, to: Long, fraction: Float): Long = if (fraction >= 1f) to else from + ((to - from) * fraction.toDouble()).roundToLong()

/**
 * An amount that counts up or down to its new value instead of jumping, e.g. the Home balance after an entry is added.
 * The first real value appears at once (no count from zero when the app opens).
 */
@Composable
fun RollingMoney(value: Long, format: (Long) -> String, style: TextStyle, modifier: Modifier = Modifier) {
    var from by remember { mutableLongStateOf(value) }
    var to by remember { mutableLongStateOf(value) }
    val fraction = remember { Animatable(1f) }
    LaunchedEffect(value) {
        if (value == to) return@LaunchedEffect
        if (to == 0L) { from = value; to = value; return@LaunchedEffect } // the state before the first load
        from = rolled(from, to, fraction.value) // continue from wherever the last roll got to
        to = value
        fraction.snapTo(0f)
        fraction.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    Text(format(rolled(from, to, fraction.value)), style = style, modifier = modifier)
}
