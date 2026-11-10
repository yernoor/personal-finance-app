package dev.yernur.moneytracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Press feedback for everything clickable: the content shrinks while it is held and springs back (with a little overshoot) on release.
 * Provided once at the theme root as the app-wide indication, replacing the ripple. The shrink is a fixed distance, so a small button
 * and a full-width row both move by about the same number of pixels.
 */
object PressScaleIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressScaleNode(interactionSource)
    override fun equals(other: Any?) = other === PressScaleIndication
    override fun hashCode() = 1
}

private const val SHRINK_DP = 7f
private const val MIN_SHRINK = 0.015f // a wide row never shrinks less than this
private const val MAX_SHRINK = 0.06f  // a small button never shrinks more than this

/** The scale a pressed element of this size (px) is drawn at, given how far into the press it is (0 = rest, 1 = fully pressed). */
internal fun pressScale(maxSidePx: Float, density: Float, pressed: Float): Float {
    if (maxSidePx <= 0f) return 1f
    val shrink = (SHRINK_DP * density / maxSidePx).coerceIn(MIN_SHRINK, MAX_SHRINK)
    return 1f - shrink * pressed
}

private class PressScaleNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private val pressed = Animatable(0f) // read while drawing, so every step of the animation redraws
    private var job: Job? = null
    private var presses = 0

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses++
                    is PressInteraction.Release, is PressInteraction.Cancel -> presses = maxOf(0, presses - 1)
                    else -> return@collect
                }
                job?.cancel()
                job = launch {
                    if (presses > 0) pressed.animateTo(1f, tween(70))
                    else pressed.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val factor = pressScale(maxOf(size.width, size.height), density, pressed.value)
        if (factor == 1f) return drawContent()
        withTransform({ scale(factor, factor, center) }) { this@draw.drawContent() }
    }
}
