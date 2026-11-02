package dev.yernur.moneytracker.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.Money
import kotlin.math.roundToInt

/** One category's share of the chart; [colorKey] is the category's stored color (themed when drawn). */
data class ChartSlice(val title: String, val colorKey: Int?, val amount: Long, val fraction: Float) {
    val percent get() = (fraction * 100).roundToInt()
}

/** The expense (or income) categories of the shown period as shares of their total, biggest first (the groups already are). */
fun chartSlices(groups: List<HistoryGroup>, income: Boolean): List<ChartSlice> {
    val shown = groups.filter { it.isIncome == income && it.total > 0 }
    val total = shown.sumOf { it.total }
    if (total == 0L) return emptyList()
    return shown.map { ChartSlice(it.title, it.iconColor, it.total, it.total.toFloat() / total) }
}

/** Collapsed it is a thin stacked bar with the total; expanded, a ring in the category colors with the total in the middle. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalAnimationApi::class)
@Composable
fun HistoryChart(
    slices: List<ChartSlice>,
    income: Boolean,
    onKind: (income: Boolean) -> Unit,
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = Money.format(slices.sumOf { it.amount }, DEFAULT_CURRENCY)
    val summary = (if (income) "Income " else "Expenses ") + total + slices.joinToString("") { ", ${it.title} ${it.percent}%" }
    Card(modifier.fillMaxWidth().testTag("history-chart")) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(!income, { onKind(false) }, label = { Text("Expenses") }, modifier = Modifier.testTag("chart-expenses"))
                Spacer(Modifier.width(8.dp))
                FilterChip(income, { onKind(true) }, label = { Text("Income") }, modifier = Modifier.testTag("chart-income"))
                Spacer(Modifier.weight(1f))
                IconButton(onToggle, modifier = Modifier.testTag("chart-toggle")) {
                    Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (open) "Collapse chart" else "Expand chart")
                }
            }
            if (slices.isEmpty()) {
                Text("Nothing to chart in this period", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                return@Column
            }
            // Bar <-> ring: the card grows or shrinks while the old view fades out and the new one fades in; the new view then draws itself in.
            AnimatedContent(
                open,
                transitionSpec = {
                    (fadeIn(tween(280, delayMillis = 60)) + expandVertically(tween(320, easing = FastOutSlowInEasing))) togetherWith
                        (fadeOut(tween(160)) + shrinkVertically(tween(320, easing = FastOutSlowInEasing))) using SizeTransform(clip = true)
                },
                label = "chart-style",
            ) { expanded ->
                Column(Modifier.fillMaxWidth().semantics { contentDescription = summary }, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (expanded) {
                        Ring(slices, total)
                        FlowRow(
                            Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            slices.forEach { slice ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(themedColor(slice.colorKey)))
                                    Text("  ${slice.title} ${slice.percent}%", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else {
                        Bar(slices)
                        Text(total, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

/** 0 -> 1 whenever the slices change or the view appears, so the chart draws itself in. */
@Composable
private fun drawInProgress(slices: List<ChartSlice>): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    return progress.value
}

@Composable
private fun Bar(slices: List<ChartSlice>) {
    val progress = drawInProgress(slices)
    Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).testTag("history-chart-bar")) {
        Row(Modifier.fillMaxWidth(progress.coerceAtLeast(0.001f)).fillMaxHeight()) { // grows from the left, proportions unchanged
            slices.forEach { Box(Modifier.weight(it.fraction.coerceAtLeast(0.01f)).fillMaxHeight().background(themedColor(it.colorKey))) }
        }
    }
}

@Composable
private fun Ring(slices: List<ChartSlice>, total: String) {
    val colors = slices.map { themedColor(it.colorKey) }
    val progress = drawInProgress(slices)
    Box(Modifier.size(220.dp).testTag("history-chart-ring"), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(220.dp)) {
            val stroke = 30.dp.toPx()
            val gap = if (slices.size > 1) 1.5f else 0f // a hairline between neighbours so equal colors do not merge
            var from = 0f // fraction of the circle where this slice starts
            slices.forEachIndexed { i, slice ->
                val shown = (progress - from).coerceIn(0f, slice.fraction) // the ring sweeps clockwise from the top as progress grows
                if (shown > 0f) {
                    val sweep = 360f * shown
                    drawArc(
                        colors[i], -90f + 360f * from + gap / 2, (sweep - gap).coerceAtLeast(0.5f), useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke),
                    )
                }
                from += slice.fraction
            }
        }
        Text(total, style = MaterialTheme.typography.headlineSmall)
    }
}
