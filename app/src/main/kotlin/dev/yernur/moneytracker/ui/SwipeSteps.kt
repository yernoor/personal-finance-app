package dev.yernur.moneytracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import dev.yernur.moneytracker.data.AnimationKind
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Sideways swipes inside a screen step its top selector (period, sub-tab, list), the same way everywhere, as one continuous movement:
 * the content follows the finger; on release past [SWIPE_STEP_DP] (or a quick flick) it carries on out with the finger's speed, the step
 * happens while it is out of sight (waiting briefly for the new data), and the new content glides in from the other side. Short of that it
 * springs back. The screen consumes the drag, so the app-wide swipe between tabs does not also fire (the navbar scrubs tabs instead).
 */
class SwipeSteps {
    val offset = Animatable(0f)
    /** True from the release until the new content has arrived: the step's own fades and morphs stand down meanwhile. */
    var stepping by mutableStateOf(false)
        internal set
}

private const val SWIPE_STEP_DP = 56
private const val FLICK_DP_PER_S = 600
private const val OUT_MS = 140
private const val IN_MS = 300
/** Content is fully faded this far (fraction of the width) from its place, so the jump from one side to the other is never seen. */
private const val FADE_FRACTION = 0.35f

@Composable
fun rememberSwipeSteps() = remember { SwipeSteps() }

/**
 * Listens for the swipe. [step] gets +1 for a swipe to the left (next: later, the segment to the right), -1 for one to the right, and returns
 * false at an end. Without [travel] the step happens at once and nothing slides (the screen moves its own parts). It may suspend until the new content is ready (keep that short: the screen is blank meanwhile).
 */
@Composable
fun Modifier.swipeSteps(state: SwipeSteps, travel: Boolean = true, step: suspend (Int) -> Boolean): Modifier {
    val scope = rememberCoroutineScope()
    val onStep by rememberUpdatedState(step)
    val animate = animated(AnimationKind.PeriodSwitch) && travel
    return pointerInput(state) {
        val threshold = SWIPE_STEP_DP.dp.toPx()
        val flick = FLICK_DP_PER_S.dp.toPx()
        val width = size.width.toFloat()
        val velocity = VelocityTracker()
        var follow: Job? = null
        detectHorizontalDragGestures(
            onDragStart = { velocity.resetTracking() },
            onDragEnd = {
                val moved = state.offset.value
                val speed = velocity.calculateVelocity().x
                val dir = if (moved < 0) 1 else -1 // finger went left: next
                val going = abs(moved) > threshold || (abs(speed) > flick && sign(speed) == sign(moved) && moved != 0f)
                scope.launch {
                    follow?.join()
                    if (!going) {
                        state.offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), speed)
                        return@launch
                    }
                    state.stepping = true
                    try {
                        if (animate) state.offset.animateTo(-dir * width * FADE_FRACTION, tween(OUT_MS, easing = FastOutLinearInEasing))
                        val stepped = onStep(dir)
                        if (!animate) return@launch state.offset.snapTo(0f)
                        // Out of sight on one side; in from the other (or back from the same side when there was nothing to step to).
                        state.offset.snapTo(if (stepped) dir * width * FADE_FRACTION else -dir * width * FADE_FRACTION)
                        state.offset.animateTo(0f, tween(IN_MS, easing = LinearOutSlowInEasing))
                    } finally {
                        state.stepping = false
                    }
                }
            },
            onDragCancel = { scope.launch { state.offset.animateTo(0f) } },
        ) { change, delta ->
            change.consume()
            velocity.addPosition(change.uptimeMillis, change.position)
            if (state.stepping) return@detectHorizontalDragGestures
            follow = scope.launch { state.offset.snapTo(state.offset.value + delta) }
        }
    }
}

/** Moves the part of the screen that changes with the swipe, fading out as it travels. */
fun Modifier.swipeShift(state: SwipeSteps): Modifier = graphicsLayer {
    translationX = state.offset.value
    alpha = 1f - (abs(state.offset.value) / (size.width.coerceAtLeast(1f) * FADE_FRACTION)).coerceIn(0f, 1f)
}
