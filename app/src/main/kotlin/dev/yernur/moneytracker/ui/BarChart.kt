package dev.yernur.moneytracker.ui

import dev.yernur.moneytracker.data.AnimationKind
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.util.lerp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures

private const val GROW_MS = 600

/** How far group [index] of [count] has grown when the whole animation is at [overall] (0..1): later groups start a little later, all end together. */
internal fun staggered(overall: Float, index: Int, count: Int): Float {
    if (count <= 1) return overall
    val step = 0.4f / count
    return ((overall - index * step) / (1f - (count - 1) * step)).coerceIn(0f, 1f)
}

data class BarSegment(val value: Long, val color: Color)

/** A bar is a stack of segments; a group is a cluster of bars drawn side by side (one group per time bucket). */
typealias BarGroup = List<List<BarSegment>>

/** One time bucket as drawn: how much room it takes (1 = a full slot, 0 = gone), its label, and its bars. Slot 0 is the newest bucket, at the right. */
data class BarSlot(val weight: Float, val label: String, val bars: BarGroup)

/** What the chart draws: the slots newest first, and which one is selected. */
data class BarShape(val slots: List<BarSlot>, val selected: Int)

fun barShape(groups: List<BarGroup>, labels: List<String>, selected: Int): BarShape =
    BarShape(groups.indices.reversed().map { BarSlot(1f, labels[it], groups[it]) }, groups.lastIndex - selected)

/** Bars grow or shrink to their new heights; slots the new period has fewer of squeeze out, extra ones open up (7 days -> 4 weeks and back). */
fun blendBars(a: BarShape, b: BarShape, t: Float): BarShape {
    val slots = (0 until maxOf(a.slots.size, b.slots.size)).map { j ->
        val from = a.slots.getOrNull(j)
        val to = b.slots.getOrNull(j)
        val bars = (0 until maxOf(from?.bars?.size ?: 0, to?.bars?.size ?: 0)).map { k ->
            val fb = from?.bars?.getOrNull(k).orEmpty()
            val tb = to?.bars?.getOrNull(k).orEmpty()
            (0 until maxOf(fb.size, tb.size)).map { m ->
                val fs = fb.getOrNull(m)
                val ts = tb.getOrNull(m)
                BarSegment(rolled(fs?.value ?: 0L, ts?.value ?: 0L, t), (ts ?: fs)!!.color)
            }
        }
        BarSlot(lerp(from?.weight ?: 0f, to?.weight ?: 0f, t), (to ?: from)!!.label, bars)
    }
    return BarShape(slots, b.selected)
}

/**
 * Bars scale to the tallest bar. Tapping a group selects it; labels sit under the groups, equally spaced. Bars draw in from the axis when the
 * chart first shows or [tabKey] changes; a new period (or edited data) glides from the old bars to the new ones, with the movement of [cues].
 */
@Composable
fun BarChart(groups: List<BarGroup>, labels: List<String>, selected: Int, onSelect: (Int) -> Unit, tabKey: Any, cues: CueTracker, modifier: Modifier = Modifier) {
    val highlight = MaterialTheme.colorScheme.secondaryContainer
    val axis = MaterialTheme.colorScheme.outlineVariant
    val target = remember(groups, labels, selected) { barShape(groups, labels, selected) }
    val morph = remember { Morph(target, ::blendBars) }
    val glide = animated(AnimationKind.PeriodSwitch)
    var tabOnShow by remember { mutableStateOf(tabKey) }
    LaunchedEffect(target) {
        val sameTab = tabOnShow == tabKey
        tabOnShow = tabKey
        morph.moveTo(target, animate = glide && sameTab, newCue = cues.consume())
    }
    val shape = morph.shown
    val tallest = shape.slots.maxOfOrNull { slot -> slot.bars.maxOfOrNull { bar -> bar.sumOf { it.value } } ?: 0L }?.coerceAtLeast(1L) ?: 1L
    // The bars grow up from the axis, one group after the other, when the chart first shows or another tab is picked
    val drawIn = animated(AnimationKind.Charts)
    val grow = remember { Animatable(if (drawIn) 0f else 1f) }
    LaunchedEffect(tabKey) {
        if (!drawIn) return@LaunchedEffect
        grow.snapTo(0f)
        grow.animateTo(1f, tween(GROW_MS, easing = FastOutSlowInEasing))
    }

    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().height(104.dp).testTag("bar-chart").periodCue(morph.cue) { morph.progress.value }.pointerInput(groups.size) {
                detectTapGestures { offset -> onSelect((offset.x / (size.width / groups.size)).toInt().coerceIn(0, groups.size - 1)) }
            },
        ) {
            val room = shape.slots.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(0.001f)
            var right = size.width
            shape.slots.forEachIndexed { j, slot ->
                val slotWidth = size.width * slot.weight / room
                val left = right - slotWidth
                right = left
                val progress = staggered(grow.value, shape.slots.size - 1 - j, shape.slots.size)
                if (j == shape.selected) drawRoundRect(highlight, Offset(left, 0f), Size(slotWidth, size.height), CornerRadius(8.dp.toPx()))
                val barWidth = slotWidth * 0.8f / slot.bars.size.coerceAtLeast(1)
                slot.bars.forEachIndexed { barIndex, bar ->
                    var bottom = size.height
                    val x = left + slotWidth * 0.1f + barIndex * barWidth
                    bar.forEach { segment ->
                        val height = size.height * (segment.value.toFloat() / tallest) * progress
                        if (height > 0f) drawRect(segment.color, Offset(x, bottom - height), Size(barWidth, height))
                        bottom -= height
                    }
                }
            }
            drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth().clipToBounds(), verticalAlignment = Alignment.Top) {
            shape.slots.asReversed().forEachIndexed { index, slot ->
                if (slot.weight > 0.001f) Text(
                    slot.label, Modifier.weight(slot.weight), textAlign = TextAlign.Center, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (shape.slots.size - 1 - index == shape.selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
