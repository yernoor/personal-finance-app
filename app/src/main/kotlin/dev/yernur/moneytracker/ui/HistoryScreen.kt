package dev.yernur.moneytracker.ui

import androidx.compose.ui.graphics.graphicsLayer
import dev.yernur.moneytracker.data.AnimationKind
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.yernur.moneytracker.LocalAppContainer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.DateRange
import dev.yernur.moneytracker.data.PeriodUnit
import dev.yernur.moneytracker.data.rangeOf
import dev.yernur.moneytracker.data.shift
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.TransactionType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One line of the history: a transaction or a transfer, already formatted. */
data class HistoryRow(
    val uid: String,
    val isTransfer: Boolean,
    val date: String,
    val created: String,
    val title: String,
    val subtitle: String,
    val amount: String,
    val iconName: String?,
    val iconColor: Int?,
    val searchText: String,
    val categoryUid: String? = null,
    val isIncome: Boolean = false,
    /** Default-currency amount, so groups can add up accounts in different currencies. */
    val defaultAmount: Long = 0,
    /** Transfers only: the two sides, so transfers between the same accounts can be added up. */
    val transfer: TransferSides? = null,
)

data class TransferSides(
    val fromUid: String,
    val toUid: String,
    val fromTitle: String,
    val toTitle: String,
    val sent: Long,
    val sentCurrency: String?,
    /** Null for same-currency transfers (the backup leaves the received side empty then). */
    val received: Long?,
    val receivedCurrency: String?,
    val fromIcon: String? = null,
    val fromColor: Int? = null,
    val toIcon: String? = null,
    val toColor: Int? = null,
) {
    val from get() = TransferEnd(fromUid, fromTitle, sentCurrency, fromIcon, fromColor)
    val to get() = TransferEnd(toUid, toTitle, receivedCurrency ?: sentCurrency, toIcon, toColor)
}

/** One account at an end of a transfer, as its tile shows it. */
data class TransferEnd(val uid: String, val title: String, val currency: String?, val icon: String?, val color: Int?)

/**
 * All transfers between two accounts in the shown period, both directions netted into one: [from] is the side more money left,
 * [sent] what left it on balance (its currency), [received] what reached [to] on balance (only when the two currencies differ).
 */
data class TransferGroup(val key: String, val from: TransferEnd, val to: TransferEnd, val sent: String, val received: String?, val rows: List<HistoryRow>) {
    val title get() = "${from.title} → ${to.title}"
    /** "₸3,500" or, across currencies, "₸4,500 → $10". */
    val amount get() = if (received == null) sent else "$sent → $received"
}

fun groupTransfers(rows: List<HistoryRow>): List<TransferGroup> =
    rows.mapNotNull { row -> row.transfer?.let { row to it } }
        .groupBy { (_, sides) -> listOf(sides.fromUid, sides.toUid).sorted().let { it[0] to it[1] } }
        .map { (pair, pairs) ->
            val (a, b) = pair
            // In each account's own currency: what left a, less what came back to it; what reached b, less what b sent back.
            var outOfA = 0L
            var intoB = 0L
            pairs.forEach { (_, sides) ->
                val arrived = sides.received ?: sides.sent
                if (sides.fromUid == a) { outOfA += sides.sent; intoB += arrived } else { outOfA -= arrived; intoB -= sides.sent }
            }
            val sample = pairs.first().second
            val endA = if (sample.fromUid == a) sample.from else sample.to
            val endB = if (sample.fromUid == a) sample.to else sample.from
            val forward = outOfA >= 0
            val from = if (forward) endA else endB
            val to = if (forward) endB else endA
            val sent = if (forward) outOfA else -intoB
            val received = if (forward) intoB else -outOfA
            TransferGroup(
                "$a-$b", from, to, Money.format(sent, from.currency),
                if (from.currency != to.currency) Money.format(received, to.currency) else null,
                pairs.map { it.first },
            )
        }.sortedByDescending { it.rows.size }

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
    val transferGroups: List<TransferGroup> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    db: AppDatabase, settings: AppSettings,
    /** Told the shown period, so the entry form can default to it. */
    private val onRange: (DateRange) -> Unit = {},
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    /** Day, week, month or year; starts as the configured default and can be zoomed from the screen. */
    val unit = MutableStateFlow(settings.historyPeriod.value)
    /** Any date inside the shown period. */
    val anchor = MutableStateFlow(today())
    val range = combine(unit, anchor) { u, a -> u.rangeOf(a) }
    val query = MutableStateFlow("")
    /** "Group by category" switch: one expandable line per category instead of every entry. */
    val grouped = MutableStateFlow(true)

    init {
        viewModelScope.launch { range.collect(onRange) }
        // Changing the default in More applies the next time History is looked at, not only after a restart.
        viewModelScope.launch {
            settings.historyPeriod.drop(1).collect {
                unit.value = it
                anchor.value = today()
            }
        }
    }

    private val periodData = range.flatMapLatest { r ->
        val from = r.from.toString()
        val to = r.to.toString()
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
                    t.uid, false, t.date, t.created, category?.title ?: "Balance correction",
                    listOfNotNull(account?.title?.ifBlank { "Main" }, t.comment?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    if (income) "+$amount" else "-$amount",
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
                    t.comment.orEmpty(), if (t.toAmount != null && to?.currencyCode != from?.currencyCode) "$sent → ${Money.format(t.toAmount, to?.currencyCode)}" else sent,
                    null, null,
                    listOfNotNull(from?.title, to?.title, t.comment).joinToString(" ").lowercase(),
                    transfer = TransferSides(
                        t.fromAccountUid, t.toAccountUid, from?.title?.ifBlank { "Main" } ?: "?", to?.title?.ifBlank { "Main" } ?: "?",
                        t.fromAmount, from?.currencyCode, t.toAmount, to?.currencyCode,
                        from?.icon, from?.color, to?.icon, to?.color,
                    ),
                )
            }
            val byType = transactions.groupBy { it.type }.mapValues { (_, list) -> list.sumOf { it.amountInDefaultCurrency ?: 0L } }
            HistoryState(
                rows.sortedWith(compareByDescending<HistoryRow> { it.date }.thenByDescending { it.created }),
                byType[TransactionType.Income] ?: 0, byType[TransactionType.Expense] ?: 0,
            )
        }
    }

    val state: StateFlow<HistoryState> = combine(periodData, query) { data, q ->
        val needle = q.trim().lowercase()
        val rows = if (needle.isEmpty()) data.rows else data.rows.filter { needle in it.searchText }
        data.copy(rows = rows, groups = groupByCategory(rows), transferGroups = groupTransfers(rows))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun zoom(to: PeriodUnit) {
        unit.value = to
    }

    fun shift(by: Long) = anchor.update { unit.value.shift(it, by) }

    fun jumpToToday() {
        anchor.value = today()
    }
}

/** Open History on [unit] (the current one) showing the income side or the expenses side. */
data class HistoryRequest(val unit: PeriodUnit, val income: Boolean)

@Composable
fun HistoryScreen(onOpen: (uid: String, isTransfer: Boolean) -> Unit) {
    val vm = appViewModel { HistoryViewModel(it.db, it.settings, { r -> it.historyRange.value = r }) }
    val state by vm.state.collectAsStateWithLifecycle()
    val unit by vm.unit.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle(DateRange(LocalDate.now(), LocalDate.now()))
    val query by vm.query.collectAsStateWithLifecycle()
    val grouped by vm.grouped.collectAsStateWithLifecycle()
    val expanded = remember { mutableStateListOf<String>() }
    val collapse = rememberChartCollapse()
    var chartIncome by remember { mutableStateOf(false) }
    val container = LocalAppContainer.current
    val request by container.historyRequest.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        val asked = request ?: return@LaunchedEffect
        vm.zoom(asked.unit)
        vm.jumpToToday()
        chartIncome = asked.income
        container.historyRequest.value = null
    }
    val slices = remember(state.groups, chartIncome) { chartSlices(state.groups, chartIncome) }
    val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d")
    val cues = rememberCueTracker(unit, range.from.toEpochDay())
    val periodFade = periodFade(unit to range)

    Column(Modifier.fillMaxSize()) {
        val units = PeriodUnit.entries
        SlidingSegmented(
            units.map { it.name }, units.indexOf(unit), units.map { "period-${it.name}" }, { vm.zoom(units[it]) },
            Modifier.padding(horizontal = 16.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ vm.shift(-1) }, modifier = Modifier.testTag("prev-period")) { Icon(Icons.Outlined.ChevronLeft, "Previous") }
            Text(
                periodTitle(unit, range), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).graphicsLayer { alpha = periodFade }.clickable { vm.jumpToToday() }.testTag("period-label"), // tap to jump back to today
            )
            IconButton({ vm.shift(1) }, enabled = range.to < LocalDate.now(), modifier = Modifier.testTag("next-period")) { Icon(Icons.Outlined.ChevronRight, "Next") }
        }
        // Same header as Charts: period slicer, period row, then what to show underneath.
        SlidingSegmented(
            listOf("Expenses", "Income"), if (chartIncome) 1 else 0, listOf("chart-expenses", "chart-income"), { chartIncome = it == 1 },
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            RollingMoney(state.income, { "+${Money.format(it, DEFAULT_CURRENCY)}" }, changeKind = AnimationKind.PeriodSwitch)
            RollingMoney(state.expense, { "-${Money.format(it, DEFAULT_CURRENCY)}" }, changeKind = AnimationKind.PeriodSwitch)
        }
        // Pinned above the list: scrolling the list shrinks the ring into the bar first (nestedScroll), so there is no toggle button.
        HistoryChart(slices, chartIncome, collapse, cues, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        // Bottom padding keeps the last row's amount clear of the add button.
        LazyColumn(Modifier.weight(1f).graphicsLayer { alpha = periodFade }.nestedScroll(collapse).testTag("history-list"), contentPadding = PaddingValues(bottom = 88.dp + LocalChromeInset.current)) {
            item(key = "search") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { vm.query.value = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.weight(1f).testTag("search"))
                FilledIconToggleButton(grouped, { vm.grouped.value = it }, modifier = Modifier.padding(start = 8.dp).testTag("group-toggle")) {
                    Icon(Icons.Outlined.Category, "Group by category")
                }
            }
            }
            if (grouped) {
                // The ring's Expenses/Income switch picks which side is listed; shares are of that side's total.
                val shown = state.groups.filter { it.isIncome == chartIncome }
                val shownTotal = shown.sumOf { it.total }.coerceAtLeast(1L)
                shown.forEach { group ->
                    val open = group.key in expanded
                    item(key = "group-${group.key}") {
                        ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
                            modifier = Modifier.clickable { if (open) expanded.remove(group.key) else expanded.add(group.key) }.testTag("group-${group.key}"),
                            leadingContent = { IconBadge(group.iconName, group.iconColor) },
                            headlineContent = { Text(group.title) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("${group.total * 100 / shownTotal}%", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    Text(Money.format(group.total, DEFAULT_CURRENCY))
                                }
                            },
                        )
                    }
                    if (open) items(group.rows, key = { "child-${it.uid}" }) { row -> HistoryItem(row, onOpen, Modifier.padding(start = 24.dp)) }
                }
                if (state.transferGroups.isNotEmpty()) {
                    item(key = "transfers-header") {
                        Text("Transfers", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    state.transferGroups.forEach { group ->
                        val open = group.key in expanded
                        item(key = "transfer-group-${group.key}") {
                            TransferPairRow(
                                group.from, group.to, group.sent, group.received,
                                Modifier.clickable { if (open) expanded.remove(group.key) else expanded.add(group.key) }.testTag("transfer-group-${group.key}"),
                            )
                        }
                        if (open) items(group.rows, key = { "transfer-child-${it.uid}" }) { row -> TransferLine(row, onOpen, Modifier.padding(start = 24.dp)) }
                    }
                }
                return@LazyColumn
            }
            var lastDate: String? = null
            state.rows.forEach { row ->
                if (unit != PeriodUnit.Day && row.date != lastDate) { // a day view already says which day it is
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
    val sides = row.transfer
    if (sides != null) {
        val received = sides.received?.takeIf { sides.receivedCurrency != sides.sentCurrency }?.let { Money.format(it, sides.receivedCurrency) }
        return TransferPairRow(
            sides.from, sides.to, Money.format(sides.sent, sides.sentCurrency), received,
            modifier.clickable { onOpen(row.uid, true) }.testTag("row-${row.uid}"), row.subtitle,
        )
    }
    ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
        modifier = modifier.clickable { onOpen(row.uid, row.isTransfer) }.testTag("row-${row.uid}"),
        leadingContent = {
            if (row.isTransfer) Icon(Icons.Outlined.SwapHoriz, null) else IconBadge(row.iconName, row.iconColor)
        },
        headlineContent = { Text(row.title) },
        supportingContent = { if (row.subtitle.isNotBlank()) Text(row.subtitle, maxLines = 1) },
        trailingContent = { Text(row.amount) },
    )
}

private val END_SHAPE = RoundedCornerShape(16.dp)

/** A transfer as the add-transfer screen draws it: the From tile, the amount over an arrow, the To tile. */
@Composable
private fun TransferPairRow(from: TransferEnd, to: TransferEnd, sent: String, received: String?, modifier: Modifier = Modifier, note: String = "") {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TransferEndTile(from)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(sent, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(1.dp)).background(scheme.primary))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(14.dp), tint = scheme.primary)
            }
            if (received != null) Text(received, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TransferEndTile(to)
    }
}

@Composable
private fun TransferEndTile(end: TransferEnd) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.width(104.dp).clip(END_SHAPE).background(scheme.surface.copy(alpha = if (LocalGlass.current) 0.6f else 1f))
            .border(1.dp, scheme.outlineVariant, END_SHAPE).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconBadge(end.icon, end.color, size = 32.dp)
        Text(end.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One transfer inside an opened pair: its day, From -> To, and what was sent (and received, across currencies). */
@Composable
private fun TransferLine(row: HistoryRow, onOpen: (uid: String, isTransfer: Boolean) -> Unit, modifier: Modifier = Modifier) {
    val sides = row.transfer ?: return
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().clickable { onOpen(row.uid, true) }.testTag("row-${row.uid}").padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(LocalDate.parse(row.date).format(DateTimeFormatter.ofPattern("MMM d")), Modifier.width(48.dp), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        Text(sides.fromTitle, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
        Text(sides.toTitle, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Text(row.amount, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}
