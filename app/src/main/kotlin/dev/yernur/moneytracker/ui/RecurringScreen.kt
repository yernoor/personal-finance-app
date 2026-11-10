package dev.yernur.moneytracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.DuplicatePair
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.PeriodUnit
import dev.yernur.moneytracker.data.RECURRING_MONTHS_BACK
import dev.yernur.moneytracker.data.RecurringItem
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.detectRecurring
import dev.yernur.moneytracker.data.findDuplicates
import dev.yernur.moneytracker.data.lastRanges
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private fun money(minor: Long) = Money.formatRounded(minor, DEFAULT_CURRENCY)

data class RecurringState(
    val recurring: List<RecurringItem>, val duplicates: List<DuplicatePair>, val categories: Map<String?, CategoryEntity>,
)

class RecurringViewModel(db: AppDatabase, settings: AppSettings, today: LocalDate = LocalDate.now()) : ViewModel() {
    val state: StateFlow<RecurringState?> = run {
        val from = PeriodUnit.Month.lastRanges(today, RECURRING_MONTHS_BACK).first().from.toString()
        combine(
            db.transactionDao().observeFull(from, today.toString()), db.reminderDao().observeAllLive(), db.categoryDao().observeAll(), settings.keptDuplicates,
        ) { rows, reminders, categories, kept ->
            RecurringState(detectRecurring(rows, reminders, today), findDuplicates(rows, kept), categories.associateBy<CategoryEntity, String?> { it.uid })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = appViewModel { RecurringViewModel(it.db, it.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring and duplicates") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag("recurring"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth().testTag("recurring-total"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Recurring costs per month", style = MaterialTheme.typography.labelLarge)
                    Text(money(s.recurring.sumOf { it.amount }), style = MaterialTheme.typography.headlineMedium)
                }
            }
            Text("Detected recurring", style = MaterialTheme.typography.titleMedium)
            Text("The same comment and a similar amount in at least 3 months in a row.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.recurring.isEmpty()) Text("Nothing detected yet.", modifier = Modifier.testTag("recurring-empty"))
            s.recurring.forEach { RecurringRow(it) }

            if (s.duplicates.isNotEmpty()) Text("Possible duplicates", style = MaterialTheme.typography.titleMedium)
            s.duplicates.forEach { pair ->
                DuplicateCard(
                    pair, s.categories[pair.second.categoryUid]?.title,
                    onKeep = { container.settings.keepDuplicate(pair.second.uid) },
                    onDelete = { scope.launch { container.ledger.deleteTransaction(pair.second.uid); container.afterLedgerChange() } },
                )
            }
            Text("Detected on this phone from your own history. Nothing changes unless you confirm it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecurringRow(item: RecurringItem) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("recurring-${item.title}"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge)
            Text("Monthly · next about ${item.next}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(money(item.amount), style = MaterialTheme.typography.bodyLarge)
            if (item.priceChangePercent != null && item.previousAmount != null) Text(
                "was ${money(item.previousAmount)} (${if (item.priceChangePercent > 0) "+" else ""}${item.priceChangePercent}%)",
                style = MaterialTheme.typography.labelSmall, color = if (item.priceChangePercent > 0) Semantic.expense else Semantic.income,
            ) else Text(if (item.hasReminder) "Has a reminder" else "No reminder", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DuplicateCard(pair: DuplicatePair, category: String?, onKeep: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("duplicate-${pair.second.uid}"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
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
