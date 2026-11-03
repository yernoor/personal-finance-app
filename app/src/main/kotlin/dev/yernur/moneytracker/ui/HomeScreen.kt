package dev.yernur.moneytracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.CategoryForecast
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.DateRange
import dev.yernur.moneytracker.data.ForecastRepository
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.PeriodUnit
import dev.yernur.moneytracker.data.WarningKind
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.rangeOf
import dev.yernur.moneytracker.data.summarize
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class HomeWarning(val title: String, val text: String)

data class TopCategory(val title: String, val iconName: String?, val iconColor: Int?, val amount: Long, val percent: Long)

data class RecentEntry(val uid: String, val title: String, val iconName: String?, val iconColor: Int?, val subtitle: String, val amount: String, val isIncome: Boolean)

data class PeriodTotals(
    val unit: PeriodUnit = PeriodUnit.Month,
    val range: DateRange = PeriodUnit.Month.rangeOf(LocalDate.now()),
    val income: Long = 0,
    val expense: Long = 0,
    val top: List<TopCategory> = emptyList(),
)

data class HomeState(
    val counted: Long = 0,
    val all: Long = 0,
    val period: PeriodTotals = PeriodTotals(),
    val warnings: List<HomeWarning> = emptyList(),
    val recent: List<RecentEntry> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    db: AppDatabase,
    forecasts: ForecastRepository,
    settings: AppSettings,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val summary = combine(db.accountDao().observeLive(), db.accountDao().observeBalances(), db.rateDao().observeAll()) { accounts, balances, rates ->
        summarize(accounts, balances.associate { it.uid to it.balance }, rates.associate { (it.fromCurrencyCode to it.toCurrencyCode) to it.value })
    }

    /** Totals and the biggest spending categories for the configured period (a month unless the owner changed it). */
    private val period = settings.homeTotalsPeriod.flatMapLatest { unit ->
        val range = unit.rangeOf(today())
        combine(db.transactionDao().observeLite(range.from.toString(), range.to.toString()), db.categoryDao().observeAll()) { rows, categories ->
            val byUid = categories.associateBy { it.uid }
            val expenses = rows.filter { it.type == TransactionType.Expense }
            val spent = expenses.sumOf { it.amount }.coerceAtLeast(1L)
            val top = expenses.groupBy { it.categoryUid }.map { (uid, list) -> uid to list.sumOf { it.amount } }
                .sortedByDescending { it.second }.take(5).map { (uid, amount) ->
                    TopCategory(byUid[uid]?.title ?: "Balance correction", byUid[uid]?.icon, byUid[uid]?.color, amount, amount * 100 / spent)
                }
            PeriodTotals(unit, range, rows.filter { it.type == TransactionType.Income }.sumOf { it.amount }, expenses.sumOf { it.amount }, top)
        }
    }

    private val warnings = combine(forecasts.observe(), db.categoryDao().observeAll()) { forecast, categories ->
        val titles = categories.associate { it.uid to it.title }
        forecast.warnings.map { HomeWarning(titles[it.categoryUid] ?: "Balance correction", warningLine(it)) }
    }

    private val recent = combine(db.transactionDao().observeRecent(5), db.categoryDao().observeAll(), db.accountDao().observeAll()) { transactions, categories, accounts ->
        val categoryByUid = categories.associateBy { it.uid }
        val accountByUid = accounts.associateBy { it.uid }
        transactions.map { t ->
            val account = accountByUid[t.accountUid]
            val category = categoryByUid[t.categoryUid]
            RecentEntry(
                t.uid, category?.title ?: "Balance correction", category?.icon, category?.color,
                listOfNotNull(t.date, account?.title?.ifBlank { "Main" }).joinToString(" · "),
                Money.format(t.amountInAccountCurrency ?: 0L, account?.currencyCode), t.type == TransactionType.Income,
            )
        }
    }

    val state: StateFlow<HomeState> = combine(summary, period, warnings, recent) { summary, period, warnings, recent ->
        HomeState(summary.counted, summary.all, period, warnings, recent)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())
}

@Composable
fun HomeScreen() {
    val container = LocalAppContainer.current
    val vm = appViewModel { HomeViewModel(it.db, it.forecasts, it.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val cards by container.settings.homeCards.collectAsStateWithLifecycle()
    val showAll by container.settings.showAllAccounts.collectAsStateWithLifecycle()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        cards.forEach { id ->
            when (id) {
                "warnings" -> if (state.warnings.isNotEmpty()) WarningsCard(state.warnings)
                "balance" -> BalanceCard(state, showAll)
                "totals" -> TotalsCard(state.period)
                "top-categories" -> TopCategoriesCard(state.period)
                "recent" -> RecentCard(state.recent)
            }
        }
        Spacer(Modifier.height(LocalChromeInset.current)) // lets the cards scroll under the floating glass bar
    }
}

@Composable
private fun WarningsCard(warnings: List<HomeWarning>) {
    Card(Modifier.fillMaxWidth().testTag("home-warnings"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Spending warnings", style = MaterialTheme.typography.labelLarge)
            warnings.take(4).forEach { Text("${it.title}: ${it.text}", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun BalanceCard(state: HomeState, showAll: Boolean) {
    Card(Modifier.fillMaxWidth().testTag("home-balance")) {
        Column(Modifier.padding(16.dp)) {
            Text("Balance", style = MaterialTheme.typography.labelLarge)
            Text(Money.format(state.counted, DEFAULT_CURRENCY), style = MaterialTheme.typography.displaySmall)
            if (showAll) Text("All accounts: ${Money.format(state.all, DEFAULT_CURRENCY)}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TotalsCard(period: PeriodTotals) {
    Card(Modifier.fillMaxWidth().testTag("home-totals")) {
        Column(Modifier.padding(16.dp)) {
            Text(periodTitle(period.unit, period.range), style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("home-totals-title"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Income", style = MaterialTheme.typography.bodyLarge)
                Text(Money.format(period.income, DEFAULT_CURRENCY), color = Semantic.income)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Expenses", style = MaterialTheme.typography.bodyLarge)
                Text(Money.format(period.expense, DEFAULT_CURRENCY), color = Semantic.expense)
            }
        }
    }
}

@Composable
private fun TopCategoriesCard(period: PeriodTotals) {
    Card(Modifier.fillMaxWidth().testTag("home-top-categories")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Top spending · ${periodTitle(period.unit, period.range)}", style = MaterialTheme.typography.labelLarge)
            if (period.top.isEmpty()) Text("Nothing spent yet", style = MaterialTheme.typography.bodyMedium)
            period.top.forEach { category ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(category.iconName, category.iconColor, size = 32.dp)
                    Text("${category.title} · ${category.percent}%", Modifier.weight(1f))
                    Text(Money.format(category.amount, DEFAULT_CURRENCY))
                }
            }
        }
    }
}

@Composable
private fun RecentCard(recent: List<RecentEntry>) {
    Card(Modifier.fillMaxWidth().testTag("home-recent")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Latest entries", style = MaterialTheme.typography.labelLarge)
            if (recent.isEmpty()) Text("No entries yet", style = MaterialTheme.typography.bodyMedium)
            recent.forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(entry.iconName, entry.iconColor, size = 32.dp)
                    Column(Modifier.weight(1f)) {
                        Text(entry.title)
                        Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text((if (entry.isIncome) "+" else "-") + entry.amount, color = if (entry.isIncome) Semantic.income else Semantic.expense)
                }
            }
        }
    }
}

/** One line for the most urgent warning of a category. */
private fun warningLine(c: CategoryForecast): String {
    val limit = Money.format(c.limit ?: 0, DEFAULT_CURRENCY)
    return when (c.warnings.min()) {
        WarningKind.Exceeded -> "spent ${Money.format(c.spent, DEFAULT_CURRENCY)}, over the $limit limit"
        WarningKind.RunRate -> "on track for ${Money.format(c.runRate, DEFAULT_CURRENCY)} (limit $limit)"
        WarningKind.Average -> "usually ${Money.format(c.average ?: 0, DEFAULT_CURRENCY)} a month (limit $limit)"
    }
}
