package dev.yernur.moneytracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.yernur.moneytracker.data.Bucket
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.DateRange
import dev.yernur.moneytracker.data.Forecast
import dev.yernur.moneytracker.data.ForecastRepository
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.PeriodUnit
import dev.yernur.moneytracker.data.buckets
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.lastRanges
import dev.yernur.moneytracker.data.shift
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ChartTab(val label: String) { General("General"), Expenses("Expenses"), Income("Income"), Forecast("Forecast") }

data class ChartData(val unit: PeriodUnit, val buckets: List<Bucket>, val categories: Map<String?, CategoryEntity>)

/** How many bars each zoom level shows; few bars stay readable, the arrows slide the window one period at a time. */
private fun barCount(unit: PeriodUnit) = when (unit) {
    PeriodUnit.Day -> 7
    PeriodUnit.Week -> 4
    PeriodUnit.Month -> 3
    PeriodUnit.Year -> 3
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChartsViewModel(db: AppDatabase, forecasts: ForecastRepository, private val today: LocalDate = LocalDate.now()) : ViewModel() {
    var tab by mutableStateOf(ChartTab.General)
    var selected by mutableIntStateOf(-1) // -1 = the newest bar
    val unit = MutableStateFlow(PeriodUnit.Month)
    /** 0 = the window ending today; each step moves the window back by one period (one month, one year, ...). */
    val page = MutableStateFlow(0)

    val data: StateFlow<ChartData?> = combine(unit, page) { u, p -> u to p }.flatMapLatest { (u, p) ->
        val ranges = u.lastRanges(u.shift(today, -p.toLong()), barCount(u))
        combine(
            db.transactionDao().observeLite(ranges.first().from.toString(), ranges.last().to.toString()),
            db.categoryDao().observeAll(),
        ) { rows, categories -> ChartData(u, buckets(rows, ranges), categories.associateBy<CategoryEntity, String?> { it.uid }) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val forecast: StateFlow<Forecast?> = forecasts.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val categories: StateFlow<Map<String?, CategoryEntity>> = db.categoryDao().observeAll()
        .map { list -> list.associateBy<CategoryEntity, String?> { it.uid } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun zoom(to: PeriodUnit) {
        selected = -1
        page.value = 0
        unit.value = to
    }

    fun turnPage(by: Int) {
        selected = -1
        page.value = (page.value + by).coerceAtLeast(0)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartsScreen(onOpenCategory: (String) -> Unit) {
    val vm = appViewModel { ChartsViewModel(it.db, it.forecasts) }
    val data by vm.data.collectAsStateWithLifecycle()
    val forecast by vm.forecast.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val unit by vm.unit.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = vm.tab.ordinal) {
            ChartTab.entries.forEach { tab ->
                Tab(vm.tab == tab, { vm.tab = tab }, text = { Text(tab.label, maxLines = 1) }, modifier = Modifier.testTag("chart-tab-${tab.name}"))
            }
        }
        if (vm.tab == ChartTab.Forecast) {
            forecast?.let { ForecastView(it, categories, onOpenCategory) }
            return@Column
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PeriodUnit.Year to "By year", PeriodUnit.Month to "By month", PeriodUnit.Week to "By week", PeriodUnit.Day to "By day").forEach { (u, label) ->
                    FilterChip(unit == u, { vm.zoom(u) }, label = { Text(label) }, modifier = Modifier.testTag("zoom-${u.name}"))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ vm.turnPage(1) }, modifier = Modifier.testTag("chart-older")) { Icon(Icons.Outlined.ChevronLeft, "Older") }
                Spacer(Modifier.weight(1f))
                IconButton({ vm.turnPage(-1) }, enabled = page > 0, modifier = Modifier.testTag("chart-newer")) { Icon(Icons.Outlined.ChevronRight, "Newer") }
            }
            data?.let { chart -> ChartBody(chart, vm.tab, vm.selected, onSelect = { vm.selected = it }) }
        }
    }
}

private val IncomeColor = Color(0xFF4CAF50)
private val ExpenseColor = Color(0xFFFFC107)
private val ProfitColor = Color(0xFF42A5F5)
private val LossColor = Color(0xFFFF7043)
private val SmallSlicesColor = Color(0xFF9E9E9E)

@Composable
private fun ChartBody(chart: ChartData, tab: ChartTab, selectedRaw: Int, onSelect: (Int) -> Unit) {
    val selected = if (selectedRaw in chart.buckets.indices) selectedRaw else chart.buckets.lastIndex
    val groups: List<BarGroup> = when (tab) {
        ChartTab.General -> chart.buckets.map { b ->
            listOf(listOf(BarSegment(b.income, IncomeColor)), listOf(BarSegment(b.expense, ExpenseColor)),
                listOf(BarSegment(kotlin.math.abs(b.net), if (b.net >= 0) ProfitColor else LossColor)))
        }
        else -> {
            val byBucket = chart.buckets.map { if (tab == ChartTab.Expenses) it.expenseByCategory else it.incomeByCategory }
            val tallest = byBucket.maxOfOrNull { it.values.sum() }?.coerceAtLeast(1L) ?: 1L
            byBucket.map { byCategory ->
                // Slices thinner than ~2% of the tallest bar are unreadable: draw them as one grey slice (the list below keeps them all).
                val (big, tiny) = sortedCategories(byCategory, chart.categories).partition { it.second * 50 >= tallest }
                val segments = big.map { (uid, value) -> BarSegment(value, categoryColor(uid, chart.categories)) }
                listOf(if (tiny.isEmpty()) segments else segments + BarSegment(tiny.sumOf { it.second }, SmallSlicesColor))
            }
        }
    }
    BarChart(groups, chart.buckets.map { label(chart.unit, it.range) }, selected, onSelect)

    val bucket = chart.buckets[selected]
    Text(title(chart.unit, bucket.range), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("bucket-title"))
    when (tab) {
        ChartTab.General -> {
            AmountRow("Income", bucket.income, IncomeColor)
            AmountRow("Expenses", bucket.expense, ExpenseColor)
            AmountRow(if (bucket.net >= 0) "Profit" else "Loss", kotlin.math.abs(bucket.net), if (bucket.net >= 0) ProfitColor else LossColor)
        }
        else -> {
            val byCategory = if (tab == ChartTab.Expenses) bucket.expenseByCategory else bucket.incomeByCategory
            val total = byCategory.values.sum().coerceAtLeast(1L)
            sortedCategories(byCategory, chart.categories).sortedByDescending { it.second }.forEach { (uid, value) ->
                CategoryAmountRow(chart.categories[uid], value, value * 100 / total)
            }
        }
    }
}

@Composable
private fun AmountRow(label: String, amount: Long, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Text(label, Modifier.weight(1f))
        Text(Money.format(amount, DEFAULT_CURRENCY))
    }
}

@Composable
private fun CategoryAmountRow(category: CategoryEntity?, amount: Long, percent: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBadge(category?.icon, colorOf(category?.color), size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(category?.title ?: "No category", style = MaterialTheme.typography.bodyLarge)
            Text("$percent%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(Money.format(amount, DEFAULT_CURRENCY), style = MaterialTheme.typography.bodyLarge)
    }
}

private fun sortedCategories(byCategory: Map<String?, Long>, categories: Map<String?, CategoryEntity>) =
    byCategory.entries.map { it.key to it.value }.sortedBy { categories[it.first]?.position ?: Int.MAX_VALUE }

private fun categoryColor(uid: String?, categories: Map<String?, CategoryEntity>) = colorOf(categories[uid]?.color)

private val dayLabel = DateTimeFormatter.ofPattern("d")
private val weekLabel = DateTimeFormatter.ofPattern("d MMM")
private val monthLabel = DateTimeFormatter.ofPattern("MMM")

private fun label(unit: PeriodUnit, range: DateRange): String = when (unit) {
    PeriodUnit.Day -> range.from.format(dayLabel)
    PeriodUnit.Week -> range.from.format(weekLabel)
    PeriodUnit.Month -> range.from.format(monthLabel)
    PeriodUnit.Year -> range.from.year.toString()
}

private fun title(unit: PeriodUnit, range: DateRange): String = when (unit) {
    PeriodUnit.Day -> range.from.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))
    PeriodUnit.Week -> "${range.from.format(DateTimeFormatter.ofPattern("MMM d"))} – ${range.to.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}"
    PeriodUnit.Month -> range.from.format(DateTimeFormatter.ofPattern("LLLL yyyy"))
    PeriodUnit.Year -> range.from.year.toString()
}
