package dev.yernur.moneytracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import kotlin.math.abs
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
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

data class BarSegment(val value: Long, val color: Color)

/** A bar is a stack of segments; a group is a cluster of bars drawn side by side (one group per time bucket). */
typealias BarGroup = List<List<BarSegment>>

/** One time bucket as drawn: its label and its bars. Slot 0 is the newest bucket, at the right. */
data class BarSlot(val label: String, val bars: BarGroup)

/** What the chart draws: the slots newest first, and which one is selected. */
data class BarShape(val slots: List<BarSlot>, val selected: Int)

fun barShape(groups: List<BarGroup>, labels: List<String>, selected: Int): BarShape =
    BarShape(groups.indices.reversed().map { BarSlot(labels[it], groups[it]) }, groups.lastIndex - selected)

/**
 * The window moving one bucket along ([PeriodCue.Earlier] / [PeriodCue.Later]) as one strip: [slots] is old and new together, newest first,
 * one slot more than the window. At [t] = 0 the old window fills the chart, at 1 the new one.
 */
data class BarStrip(val slots: List<BarSlot>, val earlier: Boolean, val fromTallest: Long, val toTallest: Long) {
    /** How far the strip's newest slot sits right of the chart's right edge, in slot widths. */
    fun overhang(t: Float) = if (earlier) t else 1f - t
}

/** The strip for a one-bucket move from [from] to [to] (same slot count), or null when it is not one. */
fun barStrip(from: BarShape, to: BarShape, cue: PeriodCue): BarStrip? {
    if (from.slots.size != to.slots.size || from.slots.isEmpty()) return null
    val earlier = when (cue) { PeriodCue.Earlier -> true; PeriodCue.Later -> false; else -> return null }
    val slots = if (earlier) listOf(from.slots.first()) + to.slots else listOf(to.slots.first()) + from.slots
    return BarStrip(slots, earlier, tallestOf(from.slots), tallestOf(to.slots))
}

private fun tallestOf(slots: List<BarSlot>) = slots.maxOfOrNull { slot -> slot.bars.maxOfOrNull { bar -> bar.sumOf { it.value } } ?: 0L }?.coerceAtLeast(1L) ?: 1L

/**
 * Bars scale to the tallest bar. Tapping a group selects it (the highlight glides there); labels sit under the groups, equally spaced.
 * When the window moves one bucket along ([cues]) the bars slide one slot as a strip; any other change shows the new bars at once
 * (the screen slides its content in for those).
 */
@Composable
fun BarChart(groups: List<BarGroup>, labels: List<String>, selected: Int, onSelect: (Int) -> Unit, cues: CueTracker, modifier: Modifier = Modifier) {
    val highlight = MaterialTheme.colorScheme.secondaryContainer
    val axis = MaterialTheme.colorScheme.outlineVariant
    val target = remember(groups, labels, selected) { barShape(groups, labels, selected) }
    var shape by remember { mutableStateOf(target) }
    var strip by remember { mutableStateOf<BarStrip?>(null) }
    val slide = remember { Animatable(1f) }
    val reduced = LocalReduceMotion.current
    // Where the highlight sits, in slots from the right: it glides on its own, and stays put while the strip slides under it.
    val highlightAt = remember { Animatable(target.selected.toFloat()) }
    LaunchedEffect(target.selected) {
        if (reduced) highlightAt.snapTo(target.selected.toFloat())
        else highlightAt.animateTo(target.selected.toFloat(), spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(target) {
        val moving = if (reduced) null else barStrip(shape, target, cues.consume())
        shape = target
        if (moving == null) return@LaunchedEffect
        // One bucket along: the bars that stay are the same bars, only the scale eases to the new tallest.
        strip = moving
        slide.snapTo(0f)
        slide.animateTo(1f, tween(PERIOD_MS, easing = FastOutSlowInEasing))
        strip = null
    }
    val moving = strip
    val t = slide.value
    val tallest = if (moving != null) lerp(moving.fromTallest.toFloat(), moving.toTallest.toFloat(), t).toLong().coerceAtLeast(1L) else tallestOf(shape.slots)

    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().height(104.dp).clipToBounds().testTag("bar-chart").pointerInput(groups.size) {
                detectTapGestures { offset -> onSelect((offset.x / (size.width / groups.size)).toInt().coerceIn(0, groups.size - 1)) }
            },
        ) {
            val slots = moving?.slots ?: shape.slots
            val unit = size.width / (shape.slots.size.coerceAtLeast(1))
            if (shape.slots.isNotEmpty()) drawRoundRect(highlight, Offset(size.width - unit * (highlightAt.value + 1), 0f), Size(unit, size.height), CornerRadius(8.dp.toPx()))
            var right = size.width + unit * (moving?.overhang(t) ?: 0f)
            slots.forEach { slot ->
                val left = right - unit
                right = left
                val barWidth = unit * 0.8f / slot.bars.size.coerceAtLeast(1)
                slot.bars.forEachIndexed { barIndex, bar ->
                    var bottom = size.height
                    val x = left + unit * 0.1f + barIndex * barWidth
                    bar.forEach { segment ->
                        val height = size.height * (segment.value.toFloat() / tallest)
                        if (height > 0f) drawRect(segment.color, Offset(x, bottom - height), Size(barWidth, height))
                        bottom -= height
                    }
                }
            }
            drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
        BoxWithConstraints(Modifier.fillMaxWidth().clipToBounds()) { // the labels ride with the strip
            val count = shape.slots.size.coerceAtLeast(1)
            val unit = maxWidth / count
            val shown = moving?.slots ?: shape.slots
            shown.forEachIndexed { j, slot ->
                val fromRight = j - (moving?.overhang(t) ?: 0f) // slots from the right edge, where 0 is the rightmost shown
                Text(
                    slot.label, Modifier.offset(x = maxWidth - unit * (fromRight + 1)).width(unit), textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (abs(fromRight - shape.selected) < 0.5f) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
