package dev.yernur.moneytracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

/** Bars scale to the tallest bar. Tapping a group selects it; labels sit under the groups, equally spaced. */
@Composable
fun BarChart(groups: List<BarGroup>, labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val highlight = MaterialTheme.colorScheme.secondaryContainer
    val axis = MaterialTheme.colorScheme.outlineVariant
    val tallest = groups.maxOfOrNull { group -> group.maxOfOrNull { bar -> bar.sumOf { it.value } } ?: 0L }?.coerceAtLeast(1L) ?: 1L

    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().height(104.dp).testTag("bar-chart").pointerInput(groups.size) {
                detectTapGestures { offset -> onSelect((offset.x / (size.width / groups.size)).toInt().coerceIn(0, groups.size - 1)) }
            },
        ) {
            val slot = size.width / groups.size
            groups.forEachIndexed { index, group ->
                val left = index * slot
                if (index == selected) drawRoundRect(highlight, Offset(left, 0f), Size(slot, size.height), CornerRadius(8.dp.toPx()))
                val barWidth = slot * 0.8f / group.size.coerceAtLeast(1)
                group.forEachIndexed { barIndex, bar ->
                    var bottom = size.height
                    val x = left + slot * 0.1f + barIndex * barWidth
                    bar.forEach { segment ->
                        val height = size.height * (segment.value.toFloat() / tallest)
                        if (height > 0f) drawRect(segment.color, Offset(x, bottom - height), Size(barWidth, height))
                        bottom -= height
                    }
                }
            }
            drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            labels.forEachIndexed { index, label ->
                Text(
                    label, Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1, style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
