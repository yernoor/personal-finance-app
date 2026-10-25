package dev.yernur.moneytracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.CategoryForecast
import dev.yernur.moneytracker.data.ForecastRepository
import dev.yernur.moneytracker.data.WarningKind
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.summarize
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeWarning(val title: String, val text: String)

data class HomeState(
    val counted: Long = 0, val all: Long = 0, val income: Long = 0, val expense: Long = 0,
    val warnings: List<HomeWarning> = emptyList(),
)

class HomeViewModel(db: AppDatabase, forecasts: ForecastRepository) : ViewModel() {
    val month: YearMonth = YearMonth.now()

    private val summary = combine(db.accountDao().observeLive(), db.accountDao().observeBalances(), db.rateDao().observeAll()) { accounts, balances, rates ->
        summarize(accounts, balances.associate { it.uid to it.balance }, rates.associate { (it.fromCurrencyCode to it.toCurrencyCode) to it.value })
    }

    private val monthTotals = db.transactionDao().observeTotals(month.atDay(1).toString(), month.atEndOfMonth().toString())
        .map { totals -> totals.associate { it.type to it.total } }

    private val warnings = combine(forecasts.observe(), db.categoryDao().observeAll()) { forecast, categories ->
        val titles = categories.associate { it.uid to it.title }
        forecast.warnings.map { HomeWarning(titles[it.categoryUid] ?: "No category", warningLine(it)) }
    }

    val state: StateFlow<HomeState> = combine(summary, monthTotals, warnings) { summary, totals, warnings ->
        HomeState(summary.counted, summary.all, totals[TransactionType.Income] ?: 0, totals[TransactionType.Expense] ?: 0, warnings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())
}

@Composable
fun HomeScreen() {
    val vm = appViewModel { HomeViewModel(it.db, it.forecasts) }
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.warnings.isNotEmpty()) Card(
            Modifier.fillMaxWidth().testTag("home-warnings"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Spending warnings", style = MaterialTheme.typography.labelLarge)
                state.warnings.take(4).forEach { Text("${it.title}: ${it.text}", style = MaterialTheme.typography.bodyMedium) }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Balance", style = MaterialTheme.typography.labelLarge)
                Text(Money.format(state.counted, DEFAULT_CURRENCY), style = MaterialTheme.typography.displaySmall)
                Text("All accounts: ${Money.format(state.all, DEFAULT_CURRENCY)}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(vm.month.format(DateTimeFormatter.ofPattern("LLLL yyyy")), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Income", style = MaterialTheme.typography.bodyLarge)
                    Text(Money.format(state.income, DEFAULT_CURRENCY), color = Semantic.income)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Expenses", style = MaterialTheme.typography.bodyLarge)
                    Text(Money.format(state.expense, DEFAULT_CURRENCY), color = Semantic.expense)
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
