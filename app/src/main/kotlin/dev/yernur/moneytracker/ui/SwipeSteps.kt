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
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one way a screen changes what it shows (period, page, sub-tab, list), whether by a sideways swipe, an arrow or a tap on its selector:
 * the content carries on out the way it is going and fades, the change happens while it is out of sight (waiting briefly for the new data),
 * and the new content glides in from the other side. A swipe drags the content first and springs back if it was too short. With Reduce
 * motion nothing travels: a quick fade out and in. The screen consumes the drag, so the app-wide swipe between tabs does not also fire.
 */
class SwipeSteps {
    val offset = Animatable(0f)
    internal val alpha = Animatable(1f)
    internal var width = 0f
    internal var reduced = false
    private val lock = Mutex()
    /** True from the release (or tap) until the new content has arrived. */
    var stepping by mutableStateOf(false)
        private set

    /**
     * Changes what is shown, moving [dir] (+1: next, coming in from the right; -1: previous; 0: in place). [change] makes the change and may suspend
     * until the new content is ready (keep it short: the space is blank meanwhile); it returns false when there was nothing to change to.
     */
    suspend fun go(dir: Int, change: suspend () -> Boolean) = lock.withLock {
        stepping = true
        try {
            if (reduced || dir == 0) {
                alpha.animateTo(0f, tween(REDUCED_MS))
                change()
                alpha.animateTo(1f, tween(REDUCED_MS))
                return@withLock
            }
            val side = width * FADE_FRACTION
            offset.animateTo(-dir * side, tween(OUT_MS, easing = FastOutLinearInEasing))
            val changed = change()
            // Out of sight on one side; in from the other (or back from the same side when there was nothing to change to).
            offset.snapTo(if (changed) dir * side else -dir * side)
            offset.animateTo(0f, tween(IN_MS, easing = LinearOutSlowInEasing))
        } finally {
            stepping = false
        }
    }
}

private const val SWIPE_STEP_DP = 56
private const val FLICK_DP_PER_S = 600
private const val OUT_MS = 140
private const val IN_MS = 300
private const val REDUCED_MS = 90
/** Content is fully faded this far (fraction of the width) from its place, so the jump from one side to the other is never seen. */
private const val FADE_FRACTION = 0.35f

@Composable
fun rememberSwipeSteps(): SwipeSteps {
    val state = remember { SwipeSteps() }
    state.reduced = LocalReduceMotion.current
    return state
}

/**
 * Listens for the swipe. [step] gets +1 for a swipe to the left (next: later, the segment to the right), -1 for one to the right, and returns
 * false at an end. Without [travel] the content does not move with the finger and [step] runs at once (the screen moves its own parts, as
 * Charts slides its bars).
 */
@Composable
fun Modifier.swipeSteps(state: SwipeSteps, travel: Boolean = true, step: suspend (Int) -> Boolean): Modifier {
    val scope = rememberCoroutineScope()
    val onStep by rememberUpdatedState(step)
    return pointerInput(state, travel) {
        val threshold = SWIPE_STEP_DP.dp.toPx()
        val flick = FLICK_DP_PER_S.dp.toPx()
        state.width = size.width.toFloat()
        val velocity = VelocityTracker()
        var dragged = 0f
        var follow: Job? = null
        detectHorizontalDragGestures(
            onDragStart = { velocity.resetTracking(); dragged = 0f },
            onDragEnd = {
                val speed = velocity.calculateVelocity().x
                val dir = if (dragged < 0) 1 else -1 // finger went left: next
                val going = abs(dragged) > threshold || (abs(speed) > flick && sign(speed) == sign(dragged) && dragged != 0f)
                scope.launch {
                    follow?.join()
                    when {
                        !going -> state.offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), speed)
                        !travel -> onStep(dir)
                        else -> state.go(dir) { onStep(dir) }
                    }
                }
            },
            onDragCancel = { scope.launch { state.offset.animateTo(0f) } },
        ) { change, delta ->
            change.consume()
            velocity.addPosition(change.uptimeMillis, change.position)
            dragged += delta
            if (!travel || state.reduced || state.stepping) return@detectHorizontalDragGestures
            follow = scope.launch { state.offset.snapTo(state.offset.value + delta) }
        }
    }
}

/** Moves the part of the screen that changes, fading out as it travels. */
fun Modifier.swipeShift(state: SwipeSteps): Modifier = graphicsLayer {
    translationX = state.offset.value
    alpha = state.alpha.value * (1f - (abs(state.offset.value) / (size.width.coerceAtLeast(1f) * FADE_FRACTION)).coerceIn(0f, 1f))
}
