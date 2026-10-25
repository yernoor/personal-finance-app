package dev.yernur.moneytracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.summarize
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeState(val counted: Long = 0, val all: Long = 0, val income: Long = 0, val expense: Long = 0)

class HomeViewModel(db: AppDatabase) : ViewModel() {
    val month: YearMonth = YearMonth.now()

    val state: StateFlow<HomeState> = combine(
        db.accountDao().observeLive(),
        db.accountDao().observeBalances(),
        db.rateDao().observeAll(),
        db.transactionDao().observeTotals(month.atDay(1).toString(), month.atEndOfMonth().toString()),
    ) { accounts, balances, rates, totals ->
        val summary = summarize(accounts, balances.associate { it.uid to it.balance }, rates.associate { (it.fromCurrencyCode to it.toCurrencyCode) to it.value })
        val byType = totals.associate { it.type to it.total }
        HomeState(summary.counted, summary.all, byType[TransactionType.Income] ?: 0, byType[TransactionType.Expense] ?: 0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())
}

@Composable
fun HomeScreen() {
    val vm = appViewModel { HomeViewModel(it.db) }
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
