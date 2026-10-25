package dev.yernur.moneytracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** One line of the history: a transaction or a transfer, already formatted. */
data class HistoryRow(
    val uid: String,
    val isTransfer: Boolean,
    val date: String,
    val created: String,
    val title: String,
    val subtitle: String,
    val amount: String,
    val amountColor: Color?,
    val iconName: String?,
    val iconColor: Int?,
    val searchText: String,
    val categoryUid: String? = null,
    val isIncome: Boolean = false,
    /** Default-currency amount, so groups can add up accounts in different currencies. */
    val defaultAmount: Long = 0,
)

/** All transactions of one category (and direction) in the shown period, added up. */
data class HistoryGroup(
    val key: String,
    val title: String,
    val iconName: String?,
    val iconColor: Int?,
    val isIncome: Boolean,
    val total: Long,
    val rows: List<HistoryRow>,
)

/** Transfers are not spending, so they stay out of the groups. Biggest group first. */
fun groupByCategory(rows: List<HistoryRow>): List<HistoryGroup> =
    rows.filter { !it.isTransfer }.groupBy { it.categoryUid to it.isIncome }.map { (key, list) ->
        HistoryGroup(
            "${key.first}-${key.second}", list.first().title, list.first().iconName, list.first().iconColor,
            key.second, list.sumOf { it.defaultAmount }, list,
        )
    }.sortedByDescending { it.total }

data class HistoryState(
    val rows: List<HistoryRow> = emptyList(),
    val income: Long = 0,
    val expense: Long = 0,
    val groups: List<HistoryGroup> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(db: AppDatabase) : ViewModel() {
    val month = MutableStateFlow(YearMonth.now())
    val query = MutableStateFlow("")
    /** "Group by category" switch: one expandable line per category instead of every entry. */
    val grouped = MutableStateFlow(false)

    private val monthData = month.flatMapLatest { m ->
        val from = m.atDay(1).toString()
        val to = m.atEndOfMonth().toString()
        combine(
            db.transactionDao().observeBetween(from, to), db.transferDao().observeBetween(from, to),
            db.categoryDao().observeAll(), db.accountDao().observeAll(),
        ) { transactions, transfers, categories, accounts ->
            val categoryByUid = categories.associateBy { it.uid }
            val accountByUid = accounts.associateBy { it.uid }
            val rows = transactions.map { t ->
                val category = categoryByUid[t.categoryUid]
                val account = accountByUid[t.accountUid]
                val income = t.type == TransactionType.Income
                val amount = Money.format(t.amountInAccountCurrency ?: 0L, account?.currencyCode)
                HistoryRow(
                    t.uid, false, t.date, t.created, category?.title ?: "No category",
                    listOfNotNull(account?.title?.ifBlank { "Main" }, t.comment?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    if (income) "+$amount" else "-$amount", if (income) Semantic.income else Semantic.expense,
                    category?.icon, category?.color,
                    listOfNotNull(category?.title, account?.title, t.comment).joinToString(" ").lowercase(),
                    categoryUid = t.categoryUid, isIncome = income, defaultAmount = t.amountInDefaultCurrency ?: 0L,
                )
            } + transfers.map { t ->
                val from = accountByUid[t.fromAccountUid]
                val to = accountByUid[t.toAccountUid]
                val sent = Money.format(t.fromAmount, from?.currencyCode)
                HistoryRow(
                    t.uid, true, t.date, t.created, "${from?.title?.ifBlank { "Main" }} → ${to?.title?.ifBlank { "Main" }}",
                    t.comment.orEmpty(), if (t.toAmount != null) "$sent → ${Money.format(t.toAmount, to?.currencyCode)}" else sent,
                    null, null, null,
                    listOfNotNull(from?.title, to?.title, t.comment).joinToString(" ").lowercase(),
                )
            }
            val byType = transactions.groupBy { it.type }.mapValues { (_, list) -> list.sumOf { it.amountInDefaultCurrency ?: 0L } }
            HistoryState(
                rows.sortedWith(compareByDescending<HistoryRow> { it.date }.thenByDescending { it.created }),
                byType[TransactionType.Income] ?: 0, byType[TransactionType.Expense] ?: 0,
            )
        }
    }

    val state: StateFlow<HistoryState> = combine(monthData, query) { data, q ->
        val needle = q.trim().lowercase()
        val rows = if (needle.isEmpty()) data.rows else data.rows.filter { needle in it.searchText }
        data.copy(rows = rows, groups = groupByCategory(rows))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun shiftMonth(by: Long) = month.update { it.plusMonths(by) }
}

@Composable
fun HistoryScreen(onOpen: (uid: String, isTransfer: Boolean) -> Unit) {
    val vm = appViewModel { HistoryViewModel(it.db) }
    val state by vm.state.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val grouped by vm.grouped.collectAsStateWithLifecycle()
    val expanded = remember { mutableStateListOf<String>() }
    val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d")

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ vm.shiftMonth(-1) }, modifier = Modifier.testTag("prev-month")) { Icon(Icons.Outlined.ChevronLeft, "Previous month") }
            Text(
                month.format(DateTimeFormatter.ofPattern("LLLL yyyy")), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton({ vm.shiftMonth(1) }, modifier = Modifier.testTag("next-month")) { Icon(Icons.Outlined.ChevronRight, "Next month") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("+${Money.format(state.income, DEFAULT_CURRENCY)}", color = Semantic.income)
            Text("-${Money.format(state.expense, DEFAULT_CURRENCY)}", color = Semantic.expense)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { vm.query.value = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.weight(1f).testTag("search"))
            FilledIconToggleButton(grouped, { vm.grouped.value = it }, modifier = Modifier.padding(start = 8.dp).testTag("group-toggle")) {
                Icon(Icons.Outlined.Category, "Group by category")
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            if (grouped) {
                state.groups.forEach { group ->
                    val open = group.key in expanded
                    item(key = "group-${group.key}") {
                        ListItem(
                            modifier = Modifier.clickable { if (open) expanded.remove(group.key) else expanded.add(group.key) }.testTag("group-${group.key}"),
                            leadingContent = { IconBadge(group.iconName, colorOf(group.iconColor)) },
                            headlineContent = { Text(group.title) },
                            supportingContent = { Text(if (group.rows.size == 1) "1 operation" else "${group.rows.size} operations") },
                            trailingContent = {
                                Text(
                                    (if (group.isIncome) "+" else "-") + Money.format(group.total, DEFAULT_CURRENCY),
                                    color = if (group.isIncome) Semantic.income else Semantic.expense,
                                )
                            },
                        )
                    }
                    if (open) items(group.rows, key = { "child-${it.uid}" }) { row -> HistoryItem(row, onOpen, Modifier.padding(start = 24.dp)) }
                }
                val transfers = state.rows.filter { it.isTransfer }
                if (transfers.isNotEmpty()) {
                    item(key = "transfers-header") {
                        Text("Transfers", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    items(transfers, key = { "transfer-${it.uid}" }) { row -> HistoryItem(row, onOpen) }
                }
                return@LazyColumn
            }
            var lastDate: String? = null
            state.rows.forEach { row ->
                if (row.date != lastDate) {
                    lastDate = row.date
                    item(key = "header-${row.date}") {
                        Text(
                            LocalDate.parse(row.date).format(dayFormat), style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                item(key = row.uid) { HistoryItem(row, onOpen) }
            }
        }
    }
}

@Composable
private fun HistoryItem(row: HistoryRow, onOpen: (uid: String, isTransfer: Boolean) -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier.clickable { onOpen(row.uid, row.isTransfer) }.testTag("row-${row.uid}"),
        leadingContent = {
            if (row.isTransfer) Icon(Icons.Outlined.SwapHoriz, null) else IconBadge(row.iconName, colorOf(row.iconColor))
        },
        headlineContent = { Text(row.title) },
        supportingContent = { if (row.subtitle.isNotBlank()) Text(row.subtitle, maxLines = 1) },
        trailingContent = { Text(row.amount, color = row.amountColor ?: Color.Unspecified) },
    )
}
