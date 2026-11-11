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
import dev.yernur.moneytracker.data.AnimationKind
import kotlin.math.roundToLong

/** [from] moved [fraction] (0..1) of the way to [to], in whole minor units. At 1 it is exactly [to] (a Float would lose cents on big balances). */
internal fun rolled(from: Long, to: Long, fraction: Float): Long = if (fraction >= 1f) to else from + ((to - from) * fraction.toDouble()).roundToLong()

/** A change this soon after the amount first shows is the data arriving (the screen starts from a placeholder), not something the owner did. */
private const val LOAD_WINDOW_MS = 1_500L

/** Which animation switches are off. Provided by the app root from the settings. */
val LocalAnimationsOff = androidx.compose.runtime.staticCompositionLocalOf { emptySet<AnimationKind>() }

@Composable
fun animated(kind: AnimationKind): Boolean = kind !in LocalAnimationsOff.current

/**
 * An amount that counts up from zero when it first appears and up or down to each new value after that, instead of jumping.
 * Both animations can be switched off in More (count up on opening, roll on change). Used for every balance on screen (Home, Accounts, Wealth, the compound interest result).
 */
@Composable
fun RollingMoney(value: Long, format: (Long) -> String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, maxLines: Int = Int.MAX_VALUE, changeKind: AnimationKind = AnimationKind.RollChange) {
    val countUp = animated(AnimationKind.CountUp)
    val roll = animated(changeKind) // what a later change follows: the roll switch, or e.g. the period switch for a total that follows the shown period
    val born = remember { System.nanoTime() }
    var from by remember { mutableLongStateOf(if (countUp) 0L else value) }
    var to by remember { mutableLongStateOf(if (countUp) 0L else value) }
    val fraction = remember { Animatable(1f) }
    LaunchedEffect(value) {
        if (value == to) return@LaunchedEffect
        val loading = (System.nanoTime() - born) / 1_000_000 < LOAD_WINDOW_MS
        if (!(if (loading) countUp else roll)) { // this kind of animation is off: show the value at once
            from = value; to = value
            fraction.snapTo(1f)
            return@LaunchedEffect
        }
        from = rolled(from, to, fraction.value) // continue from wherever the last roll got to
        to = value
        fraction.snapTo(0f)
        fraction.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    Text(format(rolled(from, to, fraction.value)), style = style, modifier = modifier, maxLines = maxLines)
}
