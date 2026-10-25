package dev.yernur.moneytracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.yernur.moneytracker.data.LedgerRepository
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.TransactionDraft
import dev.yernur.moneytracker.data.TransferDraft
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.TagEntity
import dev.yernur.moneytracker.data.db.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class EntryKind { Expense, Income, Transfer }

data class EntryOptions(
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
)

/** Form state for adding or editing one transaction or transfer. `uid == null` means a new entry. */
class EntryViewModel(
    private val db: AppDatabase,
    private val ledger: LedgerRepository,
    private val uid: String?,
    initialKind: EntryKind,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    var kind by mutableStateOf(initialKind)
    var amount by mutableStateOf("")
    var toAmount by mutableStateOf("")
    var accountUid by mutableStateOf<String?>(null)
    var toAccountUid by mutableStateOf<String?>(null)
    var categoryUid by mutableStateOf<String?>(null)
    var date by mutableStateOf(today())
    var tagUids by mutableStateOf<Set<String>>(emptySet())
    var comment by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    val isEditing get() = uid != null

    val options: StateFlow<EntryOptions> = combine(
        db.accountDao().observeLive(), db.categoryDao().observeLive(), db.tagDao().observeLive(),
    ) { accounts, categories, tags ->
        EntryOptions(accounts.filter { it.isActive && !it.isArchived }, categories.filter { !it.isArchived }, tags)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryOptions())

    init {
        viewModelScope.launch { if (uid == null) preselectAccount() else load(uid) }
    }

    /** The original's default account has the literal uid "main" (docs/FORMAT.md). */
    private suspend fun preselectAccount() {
        val accounts = db.accountDao().getAll().filter { !it.isRemoved && it.isActive && !it.isArchived }
        accountUid = (accounts.firstOrNull { it.uid == "main" } ?: accounts.firstOrNull())?.uid
    }

    private suspend fun load(uid: String) {
        val tx = db.transactionDao().get(uid)
        if (tx != null) {
            kind = if (tx.type == TransactionType.Income) EntryKind.Income else EntryKind.Expense
            accountUid = tx.accountUid; categoryUid = tx.categoryUid
            amount = tx.amountInAccountCurrency?.let(Money::editable).orEmpty()
            date = LocalDate.parse(tx.date); comment = tx.comment.orEmpty()
            tagUids = db.transactionDao().tagUidsOf(uid).toSet()
            return
        }
        db.transferDao().get(uid)?.let { t ->
            kind = EntryKind.Transfer
            accountUid = t.fromAccountUid; toAccountUid = t.toAccountUid
            amount = Money.editable(t.fromAmount); toAmount = t.toAmount?.let(Money::editable).orEmpty()
            date = LocalDate.parse(t.date); comment = t.comment.orEmpty()
        }
    }

    fun toggleTag(tagUid: String) { tagUids = if (tagUid in tagUids) tagUids - tagUid else tagUids + tagUid }

    fun addTag(name: String) {
        viewModelScope.launch { tagUids = tagUids + ledger.tagNamed(name).uid }
    }

    fun currencyOf(accountUid: String?): String? = options.value.accounts.firstOrNull { it.uid == accountUid }?.currencyCode

    fun needsToAmount(): Boolean =
        kind == EntryKind.Transfer && toAccountUid != null && currencyOf(accountUid) != currencyOf(toAccountUid)

    fun save(onDone: () -> Unit) {
        val minor = Money.parseMinor(amount)
        val account = accountUid
        val problem = when {
            minor == null || minor <= 0 -> "Enter an amount"
            account == null -> "Choose an account"
            kind == EntryKind.Transfer && toAccountUid == null -> "Choose the destination account"
            kind == EntryKind.Transfer && toAccountUid == account -> "Choose two different accounts"
            needsToAmount() && (Money.parseMinor(toAmount) ?: 0) <= 0 -> "Enter the received amount"
            kind != EntryKind.Transfer && categoryUid == null -> "Choose a category"
            else -> null
        }
        error = problem
        if (problem != null || minor == null || account == null) return
        viewModelScope.launch {
            if (kind == EntryKind.Transfer) {
                ledger.saveTransfer(TransferDraft(uid, account, toAccountUid!!, minor, Money.parseMinor(toAmount), date.toString(), comment))
            } else {
                val type = if (kind == EntryKind.Income) TransactionType.Income else TransactionType.Expense
                ledger.saveTransaction(TransactionDraft(uid, type, account, categoryUid, minor, date.toString(), comment, tagUids))
            }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = uid ?: return
        viewModelScope.launch {
            if (kind == EntryKind.Transfer) ledger.deleteTransfer(id) else ledger.deleteTransaction(id)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryScreen(uid: String?, kind: EntryKind, onBack: () -> Unit) {
    val vm = appViewModel(key = "entry-$uid-$kind") { EntryViewModel(it.db, it.ledger, uid, kind) }
    val options by vm.options.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var addingTag by remember { mutableStateOf(false) }
    val isTransfer = vm.kind == EntryKind.Transfer

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (vm.isEditing) "Edit" else "Add") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            actions = {
                if (vm.isEditing) IconButton({ confirmDelete = true }, modifier = Modifier.testTag("delete")) { Icon(Icons.Outlined.Delete, "Delete") }
            },
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Editing keeps the entry's own kind: a transaction can swap Income/Expense, a transfer stays a transfer.
            val kinds = when {
                !vm.isEditing -> EntryKind.entries
                isTransfer -> listOf(EntryKind.Transfer)
                else -> listOf(EntryKind.Expense, EntryKind.Income)
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                kinds.forEachIndexed { index, k ->
                    SegmentedButton(
                        selected = vm.kind == k, onClick = { vm.kind = k },
                        shape = SegmentedButtonDefaults.itemShape(index, kinds.size),
                        modifier = Modifier.testTag("kind-$k"),
                    ) { Text(k.name) }
                }
            }

            OutlinedTextField(
                vm.amount, { vm.amount = it }, label = { Text(if (isTransfer) "Amount sent" else "Amount") },
                suffix = { Text(vm.currencyOf(vm.accountUid).orEmpty()) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("amount"),
            )

            Text(if (isTransfer) "From account" else "Account", style = MaterialTheme.typography.labelLarge)
            AccountChips(options.accounts, vm.accountUid, tagPrefix = if (isTransfer) "from" else "account") { vm.accountUid = it }
            if (isTransfer) {
                Text("To account", style = MaterialTheme.typography.labelLarge)
                AccountChips(options.accounts, vm.toAccountUid, tagPrefix = "to") { vm.toAccountUid = it }
                if (vm.needsToAmount()) OutlinedTextField(
                    vm.toAmount, { vm.toAmount = it }, label = { Text("Amount received") },
                    suffix = { Text(vm.currencyOf(vm.toAccountUid).orEmpty()) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("to-amount"),
                )
            } else {
                val type = if (vm.kind == EntryKind.Income) TransactionType.Income else TransactionType.Expense
                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    options.categories.filter { it.type == type }.forEach { category ->
                        CategoryTile(category, selected = vm.categoryUid == category.uid) { vm.categoryUid = category.uid }
                    }
                }
            }

            DateChips(vm.date, today = LocalDate.now()) { vm.date = it }

            if (!isTransfer) {
                Text("Tags", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.tags.forEach { tag ->
                        FilterChip(tag.uid in vm.tagUids, { vm.toggleTag(tag.uid) }, label = { Text(tag.name) })
                    }
                    AssistChip({ addingTag = true }, label = { Text("Add tag") }, leadingIcon = { Icon(Icons.Outlined.Add, null) })
                }
            }

            OutlinedTextField(vm.comment, { vm.comment = it }, label = { Text("Comment") }, modifier = Modifier.fillMaxWidth().testTag("comment"))

            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("error")) }
            Button({ vm.save(onBack) }, modifier = Modifier.fillMaxWidth().testTag("save")) { Text(if (vm.isEditing) "Save" else "Add") }
        }
    }

    if (addingTag) TextInputDialog("New tag", "", { addingTag = false }) { vm.addTag(it); addingTag = false }
    if (confirmDelete) ConfirmDialog("Delete this entry?", "Delete", { confirmDelete = false }) { confirmDelete = false; vm.delete(onBack) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountChips(accounts: List<AccountEntity>, selected: String?, tagPrefix: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        accounts.forEach { account ->
            FilterChip(
                account.uid == selected, { onPick(account.uid) },
                label = { Text("${account.title.ifBlank { "Main" }} · ${account.currencyCode}") },
                modifier = Modifier.testTag("$tagPrefix-${account.uid}"),
            )
        }
    }
}

@Composable
fun CategoryTile(category: CategoryEntity, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(76.dp).clickable(onClick = onClick).testTag("category-${category.uid}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val color = colorOf(category.color)
        IconBadge(category.icon, if (selected) color else color.copy(alpha = 0.45f), size = 48.dp)
        Text(
            category.title, style = MaterialTheme.typography.labelMedium, maxLines = 1,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DateChips(date: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val label = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
    Text("Date", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("Today" to 0L, "Yesterday" to 1L, "2 days ago" to 2L).forEach { (name, back) ->
            FilterChip(date == today.minusDays(back), { onPick(today.minusDays(back)) }, label = { Text(name) })
        }
        AssistChip({ picking = true }, label = { Text(date.format(label)) }, leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null) })
    }
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ picking = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}
