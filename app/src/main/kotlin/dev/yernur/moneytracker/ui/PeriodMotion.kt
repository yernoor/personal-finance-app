package dev.yernur.moneytracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.yernur.moneytracker.data.AnimationKind
import dev.yernur.moneytracker.data.PeriodUnit

/** How the shown period just changed: sets which small movement goes with the morph. */
enum class PeriodCue { None, ZoomOut, ZoomIn, Earlier, Later }

/** A bigger unit pulls the chart back, a smaller one pushes in; the same unit slides the way you are going. [pos] grows towards the present. */
fun periodCue(from: PeriodUnit, fromPos: Long, to: PeriodUnit, toPos: Long): PeriodCue = when {
    to.ordinal > from.ordinal -> PeriodCue.ZoomOut
    to.ordinal < from.ordinal -> PeriodCue.ZoomIn
    toPos < fromPos -> PeriodCue.Earlier
    toPos > fromPos -> PeriodCue.Later
    else -> PeriodCue.None
}

const val PERIOD_MS = 450

/** Remembers the period it saw last, so a change of unit or position can be told apart from a change of data. */
class CueTracker(private var unit: PeriodUnit, private var pos: Long) {
    private var pending = PeriodCue.None

    fun update(newUnit: PeriodUnit, newPos: Long) {
        if (newUnit == unit && newPos == pos) return
        pending = periodCue(unit, pos, newUnit, newPos)
        unit = newUnit
        pos = newPos
    }

    /** The cue of the period change since the last call, once. */
    fun consume(): PeriodCue = pending.also { pending = PeriodCue.None }
}

@Composable
fun rememberCueTracker(unit: PeriodUnit, pos: Long): CueTracker {
    val tracker = remember { CueTracker(unit, pos) }
    tracker.update(unit, pos)
    return tracker
}

/**
 * A value that glides from what was shown to its new target. [blend] mixes two values at 0..1. Without animation (or when asked not to)
 * the target is shown at once. [cue] is the movement that goes with the current glide; it is [PeriodCue.None] when idle.
 */
class Morph<T>(initial: T, private val blend: (T, T, Float) -> T) {
    var target by mutableStateOf(initial)
        private set
    private var from = initial
    val progress = Animatable(1f)
    var cue by mutableStateOf(PeriodCue.None)
        private set

    val shown: T get() = if (progress.value >= 1f) target else blend(from, target, progress.value)

    suspend fun moveTo(next: T, animate: Boolean, newCue: PeriodCue = PeriodCue.None) {
        if (next == target) return
        from = shown
        target = next
        if (!animate) {
            cue = PeriodCue.None
            progress.snapTo(1f)
            return
        }
        cue = newCue
        progress.snapTo(0f)
        progress.animateTo(1f, tween(PERIOD_MS, easing = FastOutSlowInEasing))
        cue = PeriodCue.None
    }
}

/** The small movement of a [cue] at [progress] (0 = just changed, 1 = settled): scale for a zoom, a nudge for a step to the side. */
fun Modifier.periodCue(cue: PeriodCue, progress: () -> Float): Modifier = graphicsLayer {
    val left = 1f - progress()
    when (cue) {
        PeriodCue.ZoomOut -> { scaleX = 1f - 0.12f * left; scaleY = scaleX }
        PeriodCue.ZoomIn -> { scaleX = 1f + 0.12f * left; scaleY = scaleX }
        PeriodCue.Earlier -> translationX = -18.dp.toPx() * left
        PeriodCue.Later -> translationX = 18.dp.toPx() * left
        PeriodCue.None -> Unit
    }
}

/** 0.35 -> 1 each time [key] changes (not on first show): for the list and labels that belong to a period. Always 1 with the switch off. */
@Composable
fun periodFade(key: Any?): Float {
    val on = animated(AnimationKind.PeriodSwitch)
    val fade = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(key) {
        if (first || !on) { first = false; fade.snapTo(1f); return@LaunchedEffect }
        fade.snapTo(0.35f)
        fade.animateTo(1f, tween(PERIOD_MS, easing = FastOutSlowInEasing))
    }
    return fade.value
}
