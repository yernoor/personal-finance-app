package dev.yernur.moneytracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.runtime.remember
import dev.yernur.moneytracker.data.categoryOperations
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.TxDetail
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.style.TextAlign
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

data class ChartData(
    val unit: PeriodUnit, val buckets: List<Bucket>, val categories: Map<String?, CategoryEntity>,
    val details: List<TxDetail> = emptyList(), val accountTitles: Map<String, String> = emptyMap(),
)

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
        val from = ranges.first().from.toString()
        val to = ranges.last().to.toString()
        combine(
            db.transactionDao().observeLite(from, to),
            db.categoryDao().observeAll(),
            db.transactionDao().observeDetail(from, to),
            db.accountDao().observeAll(),
        ) { rows, categories, details, accounts ->
            ChartData(u, buckets(rows, ranges), categories.associateBy<CategoryEntity, String?> { it.uid }, details, accounts.associate { it.uid to it.title })
        }
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
fun ChartsScreen(onOpenCategory: (String) -> Unit, onOpenTransaction: (String) -> Unit = {}) {
    val vm = appViewModel { ChartsViewModel(it.db, it.forecasts) }
    val data by vm.data.collectAsStateWithLifecycle()
    val forecast by vm.forecast.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val unit by vm.unit.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()

    val forecastTab = vm.tab == ChartTab.Forecast
    Column(Modifier.fillMaxSize()) {
        // Same header as History: the period slicer on top, then what to show underneath. A forecast is always this month, so no slicer.
        if (!forecastTab) {
            val units = listOf(PeriodUnit.Day, PeriodUnit.Week, PeriodUnit.Month, PeriodUnit.Year)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                units.forEachIndexed { index, u ->
                    SegmentedButton(
                        unit == u, { vm.zoom(u) }, SegmentedButtonDefaults.itemShape(index, units.size),
                        modifier = Modifier.testTag("zoom-${u.name}"),
                    ) { Text(u.name) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ vm.turnPage(1) }, modifier = Modifier.testTag("chart-older")) { Icon(Icons.Outlined.ChevronLeft, "Older") }
                Text(
                    data?.let { windowTitle(it) }.orEmpty(), Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton({ vm.turnPage(-1) }, enabled = page > 0, modifier = Modifier.testTag("chart-newer")) { Icon(Icons.Outlined.ChevronRight, "Newer") }
            }
        }
        val tabs = ChartTab.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            tabs.forEachIndexed { index, tab ->
                SegmentedButton(
                    vm.tab == tab, { vm.tab = tab }, SegmentedButtonDefaults.itemShape(index, tabs.size),
                    modifier = Modifier.testTag("chart-tab-${tab.name}"),
                ) { Text(tab.label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
            }
        }
        if (forecastTab) {
            forecast?.let { ForecastView(it, categories, onOpenCategory) }
            return@Column
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            data?.let { chart -> ChartBody(chart, vm.tab, vm.selected, onSelect = { vm.selected = it }, onOpenTransaction) }
            Spacer(Modifier.height(LocalChromeInset.current))
        }
    }
}

/** "Aug – Oct" for the bars shown, or just the one label. */
private fun windowTitle(chart: ChartData): String {
    val first = label(chart.unit, chart.buckets.first().range)
    val last = label(chart.unit, chart.buckets.last().range)
    return if (first == last) first else "$first – $last"
}

private val IncomeColor = Color(0xFF4CAF50)
private val ExpenseColor = Color(0xFFFFC107)
private val ProfitColor = Color(0xFF42A5F5)
private val LossColor = Color(0xFFFF7043)
private val SmallSlicesColor = Color(0xFF9E9E9E)

@Composable
private fun ChartBody(chart: ChartData, tab: ChartTab, selectedRaw: Int, onSelect: (Int) -> Unit, onOpenTransaction: (String) -> Unit) {
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
    Text(periodTitle(chart.unit, bucket.range), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("bucket-title"))
    when (tab) {
        ChartTab.General -> {
            AmountRow("Income", bucket.income, IncomeColor)
            AmountRow("Expenses", bucket.expense, ExpenseColor)
            AmountRow(if (bucket.net >= 0) "Profit" else "Loss", kotlin.math.abs(bucket.net), if (bucket.net >= 0) ProfitColor else LossColor)
        }
        else -> {
            val byCategory = if (tab == ChartTab.Expenses) bucket.expenseByCategory else bucket.incomeByCategory
            val total = byCategory.values.sum().coerceAtLeast(1L)
            // One row open at a time; a new bar or tab starts closed.
            var open by remember(selected, tab, chart.unit) { mutableStateOf<Pair<String?, Boolean>?>(null) }
            val type = if (tab == ChartTab.Expenses) TransactionType.Expense else TransactionType.Income
            sortedCategories(byCategory, chart.categories).sortedByDescending { it.second }.forEach { (uid, value) ->
                val expanded = open == (uid to true)
                CategoryAmountRow(chart.categories[uid], value, value * 100 / total, expanded, onToggle = { open = if (expanded) null else uid to true })
                if (expanded) {
                    categoryOperations(chart.details, bucket.range, type, uid).forEach { op ->
                        OperationRow(op, chart.accountTitles[op.accountUid].orEmpty(), onOpenTransaction)
                    }
                }
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
private fun CategoryAmountRow(category: CategoryEntity?, amount: Long, percent: Long, expanded: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).testTag("category-row-${category?.uid ?: "none"}").padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBadge(category?.icon, category?.color, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(category?.title ?: "Balance correction", style = MaterialTheme.typography.bodyLarge)
            Text("$percent%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(Money.format(amount, DEFAULT_CURRENCY), style = MaterialTheme.typography.bodyLarge)
        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "Collapse" else "Expand")
    }
}

@Composable
private fun OperationRow(op: TxDetail, account: String, onOpen: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(op.uid) }.testTag("operation-${op.uid}").padding(start = 52.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(op.comment?.takeIf { it.isNotBlank() } ?: account, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text("${op.date} · $account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(Money.format(op.amount, DEFAULT_CURRENCY), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun sortedCategories(byCategory: Map<String?, Long>, categories: Map<String?, CategoryEntity>) =
    byCategory.entries.map { it.key to it.value }.sortedBy { categories[it.first]?.position ?: Int.MAX_VALUE }

@Composable
private fun categoryColor(uid: String?, categories: Map<String?, CategoryEntity>) = themedColor(categories[uid]?.color)

private val dayLabel = DateTimeFormatter.ofPattern("d")
private val weekLabel = DateTimeFormatter.ofPattern("d MMM")
private val monthLabel = DateTimeFormatter.ofPattern("MMM")

private fun label(unit: PeriodUnit, range: DateRange): String = when (unit) {
    PeriodUnit.Day -> range.from.format(dayLabel)
    PeriodUnit.Week -> range.from.format(weekLabel)
    PeriodUnit.Month -> range.from.format(monthLabel)
    PeriodUnit.Year -> range.from.year.toString()
}
