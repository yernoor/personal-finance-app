package dev.yernur.moneytracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.Bill
import dev.yernur.moneytracker.data.BillRepository
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.DuplicatePair
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.PeriodUnit
import dev.yernur.moneytracker.data.RECURRING_MONTHS_BACK
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.findDuplicates
import dev.yernur.moneytracker.data.lastRanges
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private fun money(minor: Long) = Money.formatRounded(minor, DEFAULT_CURRENCY)
private val dayFormat = DateTimeFormatter.ofPattern("MMM d")
private val monthFormat = DateTimeFormatter.ofPattern("MMM yyyy")

data class RecurringState(val bills: List<Bill>, val duplicates: List<DuplicatePair>, val categories: Map<String?, CategoryEntity>)

class RecurringViewModel(db: AppDatabase, settings: AppSettings, bills: BillRepository, today: LocalDate = LocalDate.now()) : ViewModel() {
    val state: StateFlow<RecurringState?> = run {
        val from = PeriodUnit.Month.lastRanges(today, RECURRING_MONTHS_BACK).first().from.toString()
        combine(bills.observe(), db.transactionDao().observeFull(from, today.toString()), db.categoryDao().observeAll(), settings.keptDuplicates) { list, rows, categories, kept ->
            RecurringState(list, findDuplicates(rows, kept), categories.associateBy<CategoryEntity, String?> { it.uid })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }
}

/**
 * Recurring bills and possible duplicates (Analysis -> Recurring). Each bill is a card with what can be done about it: make its category
 * a fixed monthly bill, set a reminder, set when it ends, and, for a bill found inside a category that holds other spending, move its
 * payments to a category of its own ([onMove], with the bill's key). Design: the "Recurring bills" mock-up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(onMove: (key: String) -> Unit = {}) {
    val container = LocalAppContainer.current
    val vm = appViewModel { RecurringViewModel(it.db, it.settings, it.bills) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var reminderFor by remember { mutableStateOf<Bill?>(null) }
    var endFor by remember { mutableStateOf<Bill?>(null) }
    // After moving a bill's payments (MovePaymentsScreen): say so, with Undo.
    val snackbar = remember { SnackbarHostState() }
    val moved by container.bills.lastMove.collectAsStateWithLifecycle()
    LaunchedEffect(moved) {
        val done = moved ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(done.message, actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) container.bills.undo(done) else container.bills.lastMove.value = null
    }
    // Part of the Analysis tab (its Recurring segment): no app bar of its own; the Scaffold is there for the Undo snackbar.
    Scaffold(snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = LocalChromeInset.current)) /* above the floating bar */ }, containerColor = Color.Transparent, contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0)) { padding ->
        val s = state ?: return@Scaffold
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 24.dp + LocalChromeInset.current).testTag("recurring"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCard(Modifier.fillMaxWidth().testTag("recurring-total"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Recurring each month", style = MaterialTheme.typography.labelLarge)
                    Text(money(s.bills.sumOf { it.item.amount }), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${s.bills.size} bills · ${s.bills.count { it.fixed }} fixed · ${s.bills.count { it.reminderOn }} with a reminder",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text("Detected bills", style = MaterialTheme.typography.titleMedium)
            Text("A similar amount at least 3 months in a row: the same comment, or a category paid once or twice a month.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.bills.isEmpty()) Text("Nothing detected yet.", modifier = Modifier.testTag("recurring-empty"))
            s.bills.forEach { bill ->
                BillCard(
                    bill,
                    onFixed = { scope.launch { container.bills.toggleFixed(bill) } },
                    onReminder = { reminderFor = bill }, onEnd = { endFor = bill }, onMove = { onMove(bill.key) },
                )
            }

            if (s.duplicates.isNotEmpty()) Text("Possible duplicates", style = MaterialTheme.typography.titleMedium)
            s.duplicates.forEach { pair ->
                DuplicateCard(
                    pair, s.categories[pair.second.categoryUid]?.title,
                    onKeep = { container.settings.keepDuplicate(pair.second.uid) },
                    onDelete = { scope.launch { container.ledger.deleteTransaction(pair.second.uid); container.afterLedgerChange() } },
                )
            }
            Text("Detected on this phone from your own history. Nothing changes unless you tap it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    reminderFor?.let { bill ->
        BillReminderSheet(
            bill, onSave = { before, minute -> scope.launch { container.bills.saveReminder(bill, before, minute) }; reminderFor = null },
            onRemove = { scope.launch { container.bills.removeReminder(bill) }; reminderFor = null }, onDismiss = { reminderFor = null },
        )
    }
    endFor?.let { bill ->
        BillEndSheet(
            bill, onSave = { left -> scope.launch { container.bills.setEnd(bill, left) }; endFor = null },
            onOngoing = { scope.launch { container.bills.clearEnd(bill) }; endFor = null }, onDismiss = { endFor = null },
        )
    }
}

/** One bill: what it is, then full-size buttons for what can be done about it. */
@Composable
private fun BillCard(bill: Bill, onFixed: () -> Unit, onReminder: () -> Unit, onEnd: () -> Unit, onMove: () -> Unit) {
    val item = bill.item
    val tag = item.title
    AppCard(Modifier.fillMaxWidth().testTag("recurring-$tag")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(bill.category?.icon, bill.category?.color, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${item.months} months · next about ${item.next.format(dayFormat)}" + (bill.category?.title?.takeIf { !item.byCategory }?.let { " · in $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(money(item.amount), style = MaterialTheme.typography.titleMedium)
                    if (item.priceChangePercent != null && item.previousAmount != null) Text(
                        "${if (item.priceChangePercent > 0) "▲" else "▼"} was ${money(item.previousAmount)}",
                        style = MaterialTheme.typography.labelSmall, color = if (item.priceChangePercent > 0) Semantic.expense else Semantic.income,
                    )
                }
            }
            if (bill.sharesCategory && bill.category != null) Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)).padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "${item.title} is inside ${bill.category.title}. Give it its own category?", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Button(onMove, Modifier.height(40.dp).testTag("move-$tag")) { Text("Move payments") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val buttonModifier = Modifier.weight(1f).height(48.dp)
                // Fixed is set on the category: for a bill found by comment, say which category it is.
                val fixedLabel = if (item.byCategory || bill.category == null) "Fixed bill" else "${bill.category.title} fixed"
                if (bill.fixed) FilledTonalButton(onFixed, buttonModifier.testTag("fixed-$tag")) { ButtonIcon(Icons.Outlined.Check); Text(fixedLabel, maxLines = 1) }
                else OutlinedButton(onFixed, buttonModifier.testTag("fixed-$tag"), enabled = bill.category != null) { ButtonIcon(Icons.Outlined.PushPin); Text("Make fixed", maxLines = 1) }
                val at = bill.reminderAt
                when {
                    at != null -> FilledTonalButton(onReminder, buttonModifier.testTag("remind-$tag")) {
                        ButtonIcon(Icons.Outlined.NotificationsNone); Text("${at.format(dayFormat)} · ${minuteText(bill.settings!!.reminderMinute)}", maxLines = 1)
                    }
                    bill.reminderPaused -> FilledTonalButton(onReminder, buttonModifier.testTag("remind-$tag")) { ButtonIcon(Icons.Outlined.NotificationsNone); Text("Paused", maxLines = 1) }
                    else -> OutlinedButton(onReminder, buttonModifier.testTag("remind-$tag")) { ButtonIcon(Icons.Outlined.NotificationsNone); Text("Set reminder", maxLines = 1) }
                }
            }
            val progress = bill.progress
            val end = bill.endMonth
            if (progress != null && end != null) Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onEnd).padding(12.dp).testTag("ends-$tag"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LinearProgressIndicator({ progress.first.toFloat() / progress.second.coerceAtLeast(1) }, Modifier.fillMaxWidth().height(6.dp))
                Row {
                    Text("${progress.first} of ${progress.second} paid", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Text("ends ${end.format(monthFormat)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else OutlinedButton(onEnd, Modifier.fillMaxWidth().height(44.dp).testTag("ends-$tag")) {
                ButtonIcon(Icons.Outlined.Flag); Text("Set when it ends", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ButtonIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(icon, null, Modifier.size(18.dp))
    androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
}

@Composable
private fun DuplicateCard(pair: DuplicatePair, category: String?, onKeep: () -> Unit, onDelete: () -> Unit) {
    AppCard(Modifier.fillMaxWidth().testTag("duplicate-${pair.second.uid}"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Two entries with the same amount, category and account, created minutes apart.", style = MaterialTheme.typography.bodyMedium)
            listOf("First" to pair.first, "Second (newer)" to pair.second).forEach { (label, it) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$label · ${category ?: "No category"} · ${it.date}", style = MaterialTheme.typography.bodySmall)
                    Text("-${money(it.amount)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onKeep, Modifier.weight(1f).testTag("duplicate-keep")) { Text("Keep both") }
                Button(onDelete, Modifier.weight(1f).testTag("duplicate-delete"), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) { Text("Delete the second") }
            }
        }
    }
}
