package dev.yernur.moneytracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
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

private const val COLLAPSED_DP = 46f
private const val FLICK = 300f // px/s: slower than this is a slow drag that was let go
private const val RING_DP = 200f
private const val RING_MARGIN_DP = 8f

/** Height of the chart area when fully open: the full ring on a phone, scaled down on a short screen so the list keeps room. */
private fun expandedDp(screenHeightDp: Int) = minOf(RING_DP + RING_MARGIN_DP, screenHeightDp * 0.28f).coerceAtLeast(COLLAPSED_DP + 24f)

/**
 * Collapsing-toolbar behaviour for the chart: scrolling the list up first shrinks the ring into the bar ([fraction] 0 = ring, 1 = bar),
 * and only then moves the list; dragging down at the top of the list opens the ring again.
 */
@Stable
class ChartCollapse(private val rangePx: Float) : NestedScrollConnection {
    var fraction by mutableFloatStateOf(0f)
        private set

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y >= 0f || fraction >= 1f || rangePx <= 0f) return Offset.Zero
        val next = (fraction - available.y / rangePx).coerceAtMost(1f)
        val used = -(next - fraction) * rangePx
        fraction = next
        return Offset(0f, used)
    }

    /** Let go halfway and the chart settles on the ring or the bar (the way the flick points, else the nearer one); the list may still fling. */
    override suspend fun onPreFling(available: Velocity): Velocity {
        if (rangePx <= 0f || fraction <= 0f || fraction >= 1f) return Velocity.Zero
        val target = when {
            available.y < -FLICK -> 1f
            available.y > FLICK -> 0f
            else -> if (fraction > 0.5f) 1f else 0f
        }
        animate(fraction, target, animationSpec = tween(200, easing = FastOutSlowInEasing)) { value, _ -> fraction = value }
        return Velocity.Zero
    }

    // What the list could not take (it is at the top) opens the chart again.
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (available.y <= 0f || fraction <= 0f || rangePx <= 0f) return Offset.Zero
        val next = (fraction - available.y / rangePx).coerceAtLeast(0f)
        val used = (fraction - next) * rangePx
        fraction = next
        return Offset(0f, used)
    }
}

@Composable
fun rememberChartCollapse(): ChartCollapse {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val rangePx = with(LocalDensity.current) { (expandedDp(screenHeight) - COLLAPSED_DP).dp.toPx() }
    return remember(rangePx) { ChartCollapse(rangePx) }
}

/** A ring in the category colors with the total in the middle that shrinks into a thin stacked bar as [collapse] closes. */
@Composable
fun HistoryChart(
    slices: List<ChartSlice>,
    income: Boolean,
    collapse: ChartCollapse,
    modifier: Modifier = Modifier,
) {
    val f = collapse.fraction
    val total = Money.format(slices.sumOf { it.amount }, DEFAULT_CURRENCY)
    val summary = (if (income) "Income " else "Expenses ") + total + slices.joinToString("") { ", ${it.title} ${it.percent}%" }
    val drawn = drawInProgress(income)
    val expanded = expandedDp(LocalConfiguration.current.screenHeightDp)
    AppCard(modifier.fillMaxWidth().testTag("history-chart")) {
        Column(Modifier.padding(12.dp)) {
            if (slices.isEmpty()) {
                Text("Nothing to chart in this period", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                return@Column
            }
            Box(
                Modifier.fillMaxWidth().height(lerp(expanded, COLLAPSED_DP, f).dp).clipToBounds().semantics { contentDescription = summary },
                contentAlignment = Alignment.TopCenter,
            ) {
                if (f < 1f) Ring(slices, total, drawn, expanded - RING_MARGIN_DP, Modifier.graphicsLayer {
                    // fades and lifts away while the bar takes over
                    alpha = (1f - f * 1.6f).coerceIn(0f, 1f)
                    translationY = -f * 60.dp.toPx()
                })
                if (f > 0f) Box(Modifier.align(Alignment.Center).graphicsLayer { alpha = ((f - 0.4f) * 1.7f).coerceIn(0f, 1f) }) { Bar(slices, total, drawn) }
            }
        }
    }
}

/** 0 -> 1 when the chart first shows and when switching between expenses and income (not when scrolled between ring and bar). */
@Composable
private fun drawInProgress(income: Boolean): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(income) { // not on every data edit: adding an entry should not make the chart redraw from zero
        progress.snapTo(0f)
        progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    return progress.value
}

@Composable
private fun Bar(slices: List<ChartSlice>, total: String, drawn: Float) {
    Column(Modifier.fillMaxWidth().testTag("history-chart-bar"), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
            Row(Modifier.fillMaxWidth(drawn.coerceAtLeast(0.001f)).fillMaxHeight()) { // grows from the left, proportions unchanged
                slices.forEach { Box(Modifier.weight(it.fraction.coerceAtLeast(0.01f)).fillMaxHeight().background(themedColor(it.colorKey))) }
            }
        }
        Text(total, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Ring(slices: List<ChartSlice>, total: String, drawn: Float, sizeDp: Float, modifier: Modifier) {
    val colors = slices.map { themedColor(it.colorKey) }
    Box(modifier.size(sizeDp.dp).testTag("history-chart-ring"), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(sizeDp.dp)) {
            val stroke = size.minDimension * 0.14f
            val gap = if (slices.size > 1) 1.5f else 0f // a hairline between neighbours so equal colors do not merge
            var from = 0f // fraction of the circle where this slice starts
            slices.forEachIndexed { i, slice ->
                val shown = (drawn - from).coerceIn(0f, slice.fraction) // sweeps clockwise from the top as the chart draws in
                if (shown > 0f) {
                    drawArc(
                        colors[i], -90f + 360f * from + gap / 2, (360f * shown - gap).coerceAtLeast(0.5f), useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke),
                    )
                }
                from += slice.fraction
            }
        }
        Text(total, style = MaterialTheme.typography.headlineSmall)
    }
}
