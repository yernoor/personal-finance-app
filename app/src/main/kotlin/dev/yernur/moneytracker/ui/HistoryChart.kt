package dev.yernur.moneytracker.ui


import androidx.compose.ui.text.rememberTextMeasurer

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

/** One slice as drawn: where it starts on the 0..1 circle or bar, and how wide it is. */
data class Span(val colorKey: Int?, val start: Float, val width: Float)

/** What the chart draws: the slices, the total in the middle, and which side (expenses or income) they belong to. */
data class ChartShape(val spans: List<Span>, val total: Long, val income: Boolean)

fun chartShape(slices: List<ChartSlice>, income: Boolean): ChartShape {
    var at = 0f
    val spans = slices.map { s -> Span(s.colorKey, at, s.fraction).also { at += s.fraction } }
    return ChartShape(spans, slices.sumOf { it.amount }, income)
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

    /** Folds the ring into the bar (before the search keyboard opens over it). */
    suspend fun collapse() {
        if (rangePx > 0f && fraction < 1f) animate(fraction, 1f, animationSpec = tween(200, easing = FastOutSlowInEasing)) { value, _ -> fraction = value }
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
    val shape = remember(slices, income) { chartShape(slices, income) } // a new period or side arrives with the screen's slide: no morph of its own
    val shownTotal = Money.format(shape.total, DEFAULT_CURRENCY)
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
                if (f < 1f) Ring(shape.spans, shownTotal, expanded - RING_MARGIN_DP, Modifier.graphicsLayer {
                    // fades and lifts away while the bar takes over
                    alpha = (1f - f * 1.6f).coerceIn(0f, 1f)
                    translationY = -f * 60.dp.toPx()
                })
                if (f > 0f) Box(Modifier.align(Alignment.Center).graphicsLayer { alpha = ((f - 0.4f) * 1.7f).coerceIn(0f, 1f) }) { Bar(shape.spans, shownTotal) }
            }
        }
    }
}

@Composable
private fun Bar(spans: List<Span>, total: String) {
    val colors = spans.map { themedColor(it.colorKey) }
    Column(Modifier.fillMaxWidth().testTag("history-chart-bar"), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
            spans.forEachIndexed { i, span ->
                if (span.width > 0.002f) drawRect(colors[i], Offset(span.start * size.width, 0f), Size(span.width * size.width, size.height))
            }
        }
        Text(total, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Ring stroke as a share of the ring's size; the hole is what is left. */
private const val RING_STROKE = 0.14f
/** The total may use this share of the hole's width, so it clears the ring's inner edge with a little air. */
private const val HOLE_FILL = 0.82f

/** How much to shrink text [textWidth] wide so it fits the hole of a ring [ringSize] across (1 = it fits as it is). */
internal fun ringTextScale(textWidth: Float, ringSize: Float): Float {
    val room = ringSize * (1f - 2 * RING_STROKE) * HOLE_FILL
    return if (textWidth <= room) 1f else room / textWidth
}

/** The total is shrunk to fit the ring's hole (see [ringTextScale]). */
@Composable
private fun Ring(spans: List<Span>, total: String, sizeDp: Float, modifier: Modifier) {
    val colors = spans.map { themedColor(it.colorKey) }
    val style = MaterialTheme.typography.headlineSmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scale = remember(total, sizeDp, style, density) {
        ringTextScale(measurer.measure(total, style, softWrap = false, maxLines = 1).size.width.toFloat(), with(density) { sizeDp.dp.toPx() })
    }
    Box(modifier.size(sizeDp.dp).testTag("history-chart-ring"), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(sizeDp.dp)) {
            val stroke = size.minDimension * RING_STROKE
            val gap = if (spans.size > 1) 1.5f else 0f // a hairline between neighbours so equal colors do not merge
            spans.forEachIndexed { i, span ->
                if (span.width > 0.002f) {
                    drawArc(
                        colors[i], -90f + 360f * span.start + gap / 2, (360f * span.width - gap).coerceAtLeast(0.5f), useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke),
                    )
                }
            }
        }
        Text(total, style = style.copy(fontSize = style.fontSize * scale), maxLines = 1, softWrap = false, modifier = Modifier.testTag("history-chart-total"))
    }
}
