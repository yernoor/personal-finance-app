package dev.yernur.moneytracker.ui

import dev.yernur.moneytracker.LocalAppContainer

import androidx.compose.material3.LinearProgressIndicator

import dev.yernur.moneytracker.data.CategoryForecast


import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.yernur.moneytracker.data.Analysis
import dev.yernur.moneytracker.data.CategoryTrend
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.Forecast
import dev.yernur.moneytracker.data.ForecastRepository
import dev.yernur.moneytracker.data.Insight
import dev.yernur.moneytracker.data.InsightKind
import dev.yernur.moneytracker.data.MonthBar
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.PeriodUnit
import dev.yernur.moneytracker.data.analysis
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.lastRanges
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

private fun money(minor: Long) = Money.formatRounded(minor, DEFAULT_CURRENCY)

/** [days] is today, emitting again when the day changes (AppContainer.today). */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisViewModel(db: AppDatabase, forecasts: ForecastRepository, days: Flow<LocalDate> = flowOf(LocalDate.now())) : ViewModel() {
    val state: StateFlow<Pair<Analysis, Map<String?, CategoryEntity>>?> = days.flatMapLatest { today ->
        val ranges = PeriodUnit.Month.lastRanges(today, dev.yernur.moneytracker.data.ANALYSIS_MONTHS)
        combine(
            db.transactionDao().observeLite(ranges.first().from.toString(), today.toString()), forecasts.observe(), db.categoryDao().observeAll(),
        ) { rows, forecast, categories ->
            analysis(rows, forecast, today) to categories.associateBy<CategoryEntity, String?> { it.uid }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val forecast: StateFlow<Forecast?> = forecasts.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

enum class AnalysisTab(val label: String) { Overview("Overview"), Recurring("Recurring") }

/**
 * The Analysis tab of the bottom bar: Overview (this month against the last 3, what stands out, every category) or Recurring (bills,
 * reminders, ends, duplicates). Overview merges what were the Trends and Forecast tabs (owner's request 2026-11-18): one month figure,
 * one average.
 */
@Composable
fun AnalysisScreen(onOpenTrend: (String) -> Unit, onMove: (key: String) -> Unit, onAffordDetails: (price: String) -> Unit = {}) {
    var tab by rememberSaveable { mutableStateOf(AnalysisTab.Overview) }
    val swipe = rememberSwipeSteps()
    val container = LocalAppContainer.current
    val asked by container.analysisRequest.collectAsStateWithLifecycle()
    LaunchedEffect(asked) {
        tab = asked ?: return@LaunchedEffect
        container.analysisRequest.value = null
    }
    Column(Modifier.fillMaxSize().swipeSteps(swipe, canGo = { by -> AnalysisTab.entries.getOrNull(tab.ordinal + by) != null }) { by -> AnalysisTab.entries.getOrNull(tab.ordinal + by)?.let { tab = it } != null }) {
        SlidingSegmented(
            AnalysisTab.entries.map { it.label }, tab.ordinal, AnalysisTab.entries.map { "analysis-tab-${it.name}" },
            { i -> if (i != tab.ordinal) swipe.go(i - tab.ordinal, { (tab != AnalysisTab.entries[i]).also { tab = AnalysisTab.entries[i] } }) },
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        androidx.compose.foundation.layout.Box(Modifier.weight(1f).swipeShift(swipe)) {
            if (tab == AnalysisTab.Recurring) RecurringScreen(onMove)
            else OverviewView(onOpenTrend, onAffordDetails)
        }
    }
}

/** This month against the last 3, what stands out, and every category with its trend and limit. Design: the "Analysis, merged" mock-up. */
@Composable
private fun OverviewView(onOpenTrend: (String) -> Unit, onAffordDetails: (price: String) -> Unit) {
    val vm = appViewModel { AnalysisViewModel(it.db, it.forecasts, it.today) }
    val state by vm.state.collectAsStateWithLifecycle()
    val forecast by vm.forecast.collectAsStateWithLifecycle()
    val (analysis, categories) = state ?: return
    val rows = forecast?.categories.orEmpty().associateBy { it.categoryUid }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp).testTag("analysis"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonthCard(analysis, forecast)
        AffordCard(onAffordDetails)
        WorthALook(analysis.insights, forecast?.warnings.orEmpty(), categories)
        Text("Categories", style = MaterialTheme.typography.titleMedium)
        Text("This month against its last 3 months (last month for fixed bills).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        analysis.categories.forEach { CategoryRow(it, categories[it.categoryUid], rows[it.categoryUid], onOpenTrend) }
        androidx.compose.foundation.layout.Spacer(Modifier.height(LocalChromeInset.current))
    }
}

/** The month figure with its bar, then six months of bars against the same 3-month average. */
@Composable
private fun MonthCard(analysis: Analysis, forecast: Forecast?) {
    AppCard(Modifier.fillMaxWidth().testTag("analysis-monthly")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (forecast != null) MonthSummary(forecast, analysis.average)
            MonthlyBars(analysis.bars, analysis.average)
            Row(Modifier.fillMaxWidth()) {
                analysis.bars.forEach { bar ->
                    val name = bar.month.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + if (bar.projected != null) "*" else ""
                    Text(name, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("* dashed: still to come this month · line: 3-month average", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MonthlyBars(bars: List<MonthBar>, average: Long?) {
    val bar = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.onSurface
    val summary = bars.joinToString("; ") { "${it.month}: ${money(it.projected ?: it.spent)}" }
    Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = "Monthly expenses. $summary" }) {
        val top = maxOf(bars.maxOf { maxOf(it.spent, it.projected ?: 0) }, average ?: 0, 1L).toFloat()
        val slot = size.width / bars.size
        val width = slot * 0.6f
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        fun height(value: Long) = size.height * value / top
        bars.forEachIndexed { i, b ->
            val x = slot * i + (slot - width) / 2
            if (b.projected != null) {
                drawRoundRect(bar, Offset(x, size.height - height(b.projected)), Size(width, height(b.projected)), CornerRadius(6f), style = Stroke(2.dp.toPx(), pathEffect = dash))
            }
            drawRoundRect(bar, Offset(x, size.height - height(b.spent)), Size(width, height(b.spent)), CornerRadius(6f))
        }
        if (average != null) {
            val y = size.height - height(average)
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
        }
    }
}

/** Limit warnings and the rises and falls together, one line each; nothing when there is nothing to say. */
@Composable
private fun WorthALook(insights: List<Insight>, warnings: List<CategoryForecast>, categories: Map<String?, CategoryEntity>) {
    if (insights.isEmpty() && warnings.isEmpty()) return
    AppCard(Modifier.fillMaxWidth().testTag("analysis-insights"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Worth a look", style = MaterialTheme.typography.titleMedium)
            warnings.forEach { w ->
                Text("${categories[w.categoryUid]?.title ?: "A category"}: ${w.warnings.sorted().first().text().replaceFirstChar { it.lowercase() }}", style = MaterialTheme.typography.bodyMedium)
            }
            insights.forEach {
                val name = categories[it.categoryUid]?.title ?: "A category"
                Text(
                    if (it.kind == InsightKind.Rise) "$name is on pace for ${it.percent}% more than its 3-month average."
                    else "$name has dropped three months in a row.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** A category's trend row, with a thin limit bar and its warning when it has a limit, and how one-offs are counted. */
@Composable
private fun CategoryRow(trend: CategoryTrend, category: CategoryEntity?, row: CategoryForecast?, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        TrendRow(trend, category, onOpen)
        val limit = row?.limit
        if (limit != null) {
            val warned = row.warnings.isNotEmpty()
            LinearProgressIndicator(
                { (row.spent.toFloat() / limit).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(start = 52.dp).height(4.dp),
                color = if (warned) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            if (warned) Text(
                row.warnings.sorted().first().text(), Modifier.padding(start = 52.dp, top = 2.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
            )
        }
        if (row?.oneOff == true) Text(
            "One-off: the month counts only the ${money(row.spent)} spent", Modifier.padding(start = 52.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrendRow(trend: CategoryTrend, category: CategoryEntity?, onOpen: (String) -> Unit) {
    val color = themedColor(category?.color)
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(trend.categoryUid) }.testTag("trend-${trend.categoryUid}").padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(category?.icon, category?.color, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(category?.title ?: "Unknown", style = MaterialTheme.typography.bodyLarge)
            Text(
                money(trend.runRate) + (trend.limit?.let { " · limit ${money(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The unfinished month is drawn at its pace, so the line does not plunge at the end.
        Sparkline(trend.monthly.dropLast(1) + trend.runRate, color)
        DeltaText(trend.deltaPercent)
    }
}

@Composable
private fun Sparkline(values: List<Long>, color: Color) {
    Canvas(Modifier.size(width = 72.dp, height = 28.dp)) {
        val top = (values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
        val step = if (values.size > 1) size.width / (values.size - 1) else 0f
        val path = Path()
        values.forEachIndexed { i, v ->
            val point = Offset(step * i, size.height - (size.height - 4.dp.toPx()) * v / top - 2.dp.toPx())
            if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun DeltaText(percent: Int?) {
    val (text, color) = when {
        percent == null -> "–" to MaterialTheme.colorScheme.onSurfaceVariant
        percent >= 10 -> "▲ $percent%" to Semantic.expense
        percent <= -10 -> "▼ ${-percent}%" to Semantic.income
        else -> (if (percent >= 0) "+$percent%" else "−${-percent}%") to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, Modifier.width(64.dp), color = color, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End)
}
