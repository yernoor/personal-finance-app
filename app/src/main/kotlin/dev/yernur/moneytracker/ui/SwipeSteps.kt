package dev.yernur.moneytracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.yernur.moneytracker.data.AnimationKind
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.launch

/**
 * Sideways swipes inside a screen step its top selector (period, sub-tab, list), the same way everywhere: the content follows the finger
 * (half speed), and past [SWIPE_STEP_DP] it steps and the new content slides in from the side the finger left; otherwise it springs back.
 * The screen consumes the drag, so the app-wide swipe between tabs does not also fire (the navbar scrubs tabs instead).
 */
class SwipeSteps {
    val offset = Animatable(0f)
}

private const val SWIPE_STEP_DP = 48
private const val ENTER_FRACTION = 0.3f // how far off to the side the new content starts

@Composable
fun rememberSwipeSteps() = remember { SwipeSteps() }

/** Listens for the swipe. [step] gets +1 for a swipe to the left (next: later, the segment to the right), -1 for one to the right; false = at an end. */
@Composable
fun Modifier.swipeSteps(state: SwipeSteps, step: (Int) -> Boolean): Modifier {
    val scope = rememberCoroutineScope()
    val onStep by rememberUpdatedState(step)
    val animate = animated(AnimationKind.PeriodSwitch)
    return pointerInput(state) {
        val threshold = SWIPE_STEP_DP.dp.toPx()
        detectHorizontalDragGestures(
            onDragEnd = {
                val moved = state.offset.value
                scope.launch {
                    if (abs(moved) > threshold && onStep(if (moved < 0) 1 else -1)) {
                        if (!animate) return@launch state.offset.snapTo(0f)
                        state.offset.snapTo(-moved.sign * size.width * ENTER_FRACTION)
                        state.offset.animateTo(0f, tween(PERIOD_MS, easing = FastOutSlowInEasing))
                    } else state.offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium))
                }
            },
            onDragCancel = { scope.launch { state.offset.animateTo(0f) } },
        ) { change, delta ->
            change.consume()
            scope.launch { state.offset.snapTo(state.offset.value + delta / 2) }
        }
    }
}

/** Moves the part of the screen that changes with the swipe, fading a little as it travels. */
fun Modifier.swipeShift(state: SwipeSteps): Modifier = graphicsLayer {
    translationX = state.offset.value
    alpha = 1f - (abs(state.offset.value) / size.width.coerceAtLeast(1f)).coerceAtMost(0.6f)
}
