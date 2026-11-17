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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * The one way a screen changes what it shows (period, page, sub-tab, list), whether by a sideways swipe, an arrow or a tap on its selector:
 * the content leaves the way you are going, the change happens while it is out of sight, and the new content comes in from the other side.
 * Quick repeated inputs (several days swiped or tapped in a row) are gathered: the first starts the slide out at once, the rest are collected
 * until [SETTLE_MS] pass without another, then all are applied, the new data is waited for once, and it slides in once. An input while the new
 * content is coming in turns it round. With Reduce motion nothing travels: a quick fade out and in.
 */
class SwipeSteps(private val scope: CoroutineScope) {
    val offset = Animatable(0f)
    internal val alpha = Animatable(1f)
    internal var width = 0f
    internal var reduced = false
    private val pending = ArrayDeque<() -> Boolean>()
    private var ready: suspend () -> Unit = {}
    private var dir = 0
    private var inputs = 0
    private val settle = Animatable(0f) // times the wait for more input on the frame clock (the one tests drive), not the wall clock
    private var job: Job? = null
    private var comingIn = false
    /** True from the first input until the new content is in place. */
    var stepping by mutableStateOf(false)
        private set

    /**
     * Changes what is shown, moving [dir] (+1: next, coming in from the right; -1: previous; 0: in place). [change] makes the change when the
     * content is out of sight and returns false when there was nothing to change to (it must check that itself, at that moment: earlier inputs
     * in the same batch have been applied by then). [ready] waits, briefly, for the new content (keep it short: the space is blank meanwhile).
     */
    fun go(dir: Int, change: () -> Boolean, ready: suspend () -> Unit = {}) {
        pending.addLast(change)
        this.ready = ready
        this.dir = dir
        inputs++
        if (job?.isActive == true) {
            if (comingIn) scope.launch { offset.stop(); alpha.stop() } // turn round: the loop sends it out again
            return
        }
        job = scope.launch { run() }
    }

    private suspend fun run() {
        stepping = true
        try {
            while (pending.isNotEmpty()) {
                val out = dir
                interruptible { if (reduced || out == 0) alpha.animateTo(0f, tween(REDUCED_MS)) else offset.animateTo(-out * width * FADE_FRACTION, tween(OUT_MS, easing = FastOutLinearInEasing)) }
                // Listen a moment for more: a run of swipes or taps becomes one change.
                while (true) {
                    val seen = inputs
                    settle.snapTo(0f)
                    settle.animateTo(1f, tween(SETTLE_MS))
                    if (inputs == seen) break
                }
                var changed = false
                while (pending.isNotEmpty()) changed = pending.removeFirst()() || changed
                ready()
                val inDir = dir
                comingIn = true
                if (reduced || inDir == 0) {
                    offset.snapTo(0f)
                    interruptible { alpha.animateTo(1f, tween(REDUCED_MS)) }
                } else {
                    alpha.snapTo(1f)
                    // In from the side it was going to (back from the side it left when there was nothing to change to).
                    offset.snapTo(if (changed) inDir * width * FADE_FRACTION else -inDir * width * FADE_FRACTION)
                    interruptible { offset.animateTo(0f, tween(IN_MS, easing = LinearOutSlowInEasing)) }
                }
                comingIn = false
            }
        } finally {
            comingIn = false
            stepping = false
        }
    }

    /** Runs an animation that a new input may stop halfway (go() calls stop()); only cancelling the whole job ends the loop. */
    private suspend fun interruptible(animation: suspend () -> Unit) {
        try {
            animation()
        } catch (e: CancellationException) {
            kotlin.coroutines.coroutineContext.ensureActive()
        }
    }
}

private const val SWIPE_STEP_DP = 56
private const val FLICK_DP_PER_S = 600
private const val OUT_MS = 110
private const val IN_MS = 230
private const val REDUCED_MS = 80
/** How long to wait after an input for the next one before applying them all. */
private const val SETTLE_MS = 120
/** Content is fully faded this far (fraction of the width) from its place, so the jump from one side to the other is never seen. */
private const val FADE_FRACTION = 0.35f

@Composable
fun rememberSwipeSteps(): SwipeSteps {
    val scope = rememberCoroutineScope()
    val state = remember { SwipeSteps(scope) }
    state.reduced = LocalReduceMotion.current
    return state
}

/**
 * Listens for the swipe. [step] gets +1 for a swipe to the left (next: later, the segment to the right), -1 for one to the right, and returns
 * false at an end; [ready] waits for the new content (see [SwipeSteps.go]). [canGo] says up front whether there is anything that way: when
 * not, the content does not follow the finger and nothing animates (owner: no motion that leads nowhere). Without [travel] the content does
 * not move with the finger and [step] runs at once (the screen moves its own parts, as Charts slides its bars).
 */
@Composable
fun Modifier.swipeSteps(
    state: SwipeSteps, travel: Boolean = true, ready: suspend () -> Unit = {}, canGo: (Int) -> Boolean = { true }, step: (Int) -> Boolean,
): Modifier {
    val scope = rememberCoroutineScope()
    val onStep by rememberUpdatedState(step)
    val onReady by rememberUpdatedState(ready)
    val possible by rememberUpdatedState(canGo)
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
                val going = (abs(dragged) > threshold || (abs(speed) > flick && sign(speed) == sign(dragged) && dragged != 0f)) && possible(dir)
                when {
                    !going -> scope.launch { follow?.join(); state.offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), speed) }
                    !travel -> onStep(dir)
                    else -> state.go(dir, { onStep(dir) }, { onReady() })
                }
            },
            onDragCancel = { if (!state.stepping) scope.launch { state.offset.animateTo(0f) } },
        ) { change, delta ->
            change.consume()
            velocity.addPosition(change.uptimeMillis, change.position)
            dragged += delta
            if (!travel || state.reduced || state.stepping) return@detectHorizontalDragGestures
            if (!possible(if (dragged < 0) 1 else -1)) { // nothing that way: stay put (and come back if it had moved the other way)
                if (state.offset.value != 0f) follow = scope.launch { state.offset.snapTo(0f) }
                return@detectHorizontalDragGestures
            }
            follow = scope.launch { state.offset.snapTo(state.offset.value + delta) }
        }
    }
}

/** Moves the part of the screen that changes, fading out as it travels. */
fun Modifier.swipeShift(state: SwipeSteps): Modifier = graphicsLayer {
    translationX = state.offset.value
    alpha = state.alpha.value * (1f - (abs(state.offset.value) / (size.width.coerceAtLeast(1f) * FADE_FRACTION)).coerceIn(0f, 1f))
}
