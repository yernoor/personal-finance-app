package dev.yernur.moneytracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
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
 * An amount that counts up from zero when it first appears and up or down to each new value after that, instead of jumping.
 * Used for every balance on screen (Home, Accounts, Wealth, the compound interest result).
 */
@Composable
fun RollingMoney(value: Long, format: (Long) -> String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, maxLines: Int = Int.MAX_VALUE) {
    var from by remember { mutableLongStateOf(0L) }
    var to by remember { mutableLongStateOf(0L) }
    val fraction = remember { Animatable(1f) }
    LaunchedEffect(value) {
        if (value == to) return@LaunchedEffect
        from = rolled(from, to, fraction.value) // continue from wherever the last roll got to
        to = value
        fraction.snapTo(0f)
        fraction.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    Text(format(rolled(from, to, fraction.value)), style = style, modifier = modifier, maxLines = maxLines)
}
