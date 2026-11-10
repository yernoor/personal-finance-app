package dev.yernur.moneytracker.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.yernur.moneytracker.data.AccentPalette
import dev.yernur.moneytracker.data.LedgerRepository
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.TransactionDraft
import dev.yernur.moneytracker.data.TransferDraft
import dev.yernur.moneytracker.data.wealth.convertWithRates
import dev.yernur.moneytracker.data.wealth.rateLabel
import dev.yernur.moneytracker.data.wealth.updatedLabel
import kotlinx.coroutines.flow.first
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
import kotlinx.coroutines.flow.map
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
    private val afterSave: suspend () -> Unit = {},
    /** Rates for the received amount of a transfer between currencies (bank over backup). */
    private val rates: suspend () -> Map<Pair<String, String>, Double> = { emptyMap() },
    /** When the bank rates were fetched (epoch millis), for the line under the received amount. */
    private val ratesUpdatedAt: () -> Long? = { null },
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    var kind by mutableStateOf(initialKind)
    var amount by mutableStateOf("")
        private set
    var toAmount by mutableStateOf("")
        private set
    var accountUid by mutableStateOf<String?>(null)
        private set
    var toAccountUid by mutableStateOf<String?>(null)
        private set
    /** The received amount was typed by the owner (or stored with the transfer), so the converted figure no longer overwrites it. */
    private var toAmountTyped = false

    /** "1 USD = 453.26 KZT · updated 04.11.2026 09:15" for the chosen pair of accounts; empty when there is nothing to show. */
    var rateNote by mutableStateOf("")
        private set
    var categoryUid by mutableStateOf<String?>(null)
    var date by mutableStateOf(today())
    var tagUids by mutableStateOf<Set<String>>(emptySet())
    var comment by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    val isEditing get() = uid != null
    /** A balance correction has no category; editing one must not force a choice. */
    private var uncategorizedOriginal = false

    val options: StateFlow<EntryOptions> = combine(
        db.accountDao().observeLive(), db.categoryDao().observeLive(), db.tagDao().observeLive(),
    ) { accounts, categories, tags ->
        EntryOptions(accounts.filter { it.isActive && !it.isArchived }, categories.filter { !it.isArchived }, tags)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryOptions())

    /** Current balance of every account in its own currency; the form shows the one that is selected. */
    val balances: StateFlow<Map<String, Long>> = db.accountDao().observeBalances()
        .map { list -> list.associate { it.uid to it.balance } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch { if (uid == null) preselectAccount() else load(uid) }
    }

    /** The default account in imported backups has the literal uid "main" (docs/FORMAT.md). */
    private suspend fun preselectAccount() {
        val accounts = db.accountDao().getAll().filter { !it.isRemoved && it.isActive && !it.isArchived }
        accountUid = (accounts.firstOrNull { it.uid == "main" } ?: accounts.firstOrNull())?.uid
    }

    private suspend fun load(uid: String) {
        val tx = db.transactionDao().get(uid)
        if (tx != null) {
            kind = if (tx.type == TransactionType.Income) EntryKind.Income else EntryKind.Expense
            accountUid = tx.accountUid; categoryUid = tx.categoryUid
            uncategorizedOriginal = tx.categoryUid == null
            amount = tx.amountInAccountCurrency?.let(Money::editable).orEmpty()
            date = LocalDate.parse(tx.date); comment = tx.comment.orEmpty()
            tagUids = db.transactionDao().tagUidsOf(uid).toSet()
            return
        }
        db.transferDao().get(uid)?.let { t ->
            kind = EntryKind.Transfer
            accountUid = t.fromAccountUid; toAccountUid = t.toAccountUid
            amount = Money.editable(t.fromAmount); toAmount = t.toAmount?.let(Money::editable).orEmpty()
            toAmountTyped = toAmount.isNotEmpty()
            date = LocalDate.parse(t.date); comment = t.comment.orEmpty()
        }
    }

    fun changeAmount(text: String) { amount = text; fillReceived() }

    /** Typing a received amount keeps it; clearing the field hands it back to the calculation. */
    fun changeToAmount(text: String) { toAmount = text; toAmountTyped = text.isNotBlank(); fillReceived() }

    /** A different account means a different currency pair, so an earlier typed figure no longer applies. */
    fun pickAccount(uid: String) { accountUid = uid; toAmountTyped = false; fillReceived() }

    fun pickToAccount(uid: String) { toAccountUid = uid; toAmountTyped = false; fillReceived() }

    /** Call when the accounts or the account list change. */
    fun refreshRateNote() {
        val from = currencyOf(accountUid)
        val to = currencyOf(toAccountUid)
        if (kind != EntryKind.Transfer || from == null || to == null || from == to) { rateNote = ""; return }
        viewModelScope.launch {
            val label = rateLabel(from, to, rates())
            rateNote = if (label == null) "" else label + (ratesUpdatedAt()?.let { " · updated ${updatedLabel(it)}" } ?: "")
        }
    }

    /** Fills "Amount received" from the sent amount and the rates, while the owner has not typed it. Left empty when no rate is known. */
    private fun fillReceived() {
        if (kind != EntryKind.Transfer || toAmountTyped) return
        val sent = Money.parseMinor(amount)?.takeIf { it > 0 }
        val from = currencyOf(accountUid)
        val to = currencyOf(toAccountUid)
        if (sent == null || from == null || to == null || from == to) { toAmount = ""; return }
        val asked = amount
        viewModelScope.launch {
            val converted = convertWithRates(sent, from, to, rates())?.let(Money::editable).orEmpty()
            if (amount == asked && !toAmountTyped) toAmount = converted // the owner may have typed on meanwhile
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
            kind != EntryKind.Transfer && categoryUid == null && !uncategorizedOriginal -> "Choose a category"
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
            afterSave() // may post a spending warning
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
    val vm = appViewModel(key = "entry-$uid-$kind") { EntryViewModel(it.db, it.ledger, uid, kind, it::afterLedgerChange, { it.fx.allRates.first() }, { it.settings.ratesUpdatedAt.value }, { it.newEntryDate() }) }
    val options by vm.options.collectAsStateWithLifecycle()
    val balances by vm.balances.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var addingTag by remember { mutableStateOf(false) }
    val isTransfer = vm.kind == EntryKind.Transfer
    LaunchedEffect(vm.accountUid, vm.toAccountUid, vm.kind, options.accounts) { vm.refreshRateNote() }
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (!vm.isEditing) amountFocus.requestFocus() } // fast entry: keyboard is ready on open

    Scaffold(topBar = {
        AppTopBar(
            title = { Text(if (vm.isEditing) "Edit" else "Add") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            actions = {
                if (vm.isEditing) IconButton({ confirmDelete = true }, modifier = Modifier.testTag("delete")) { Icon(Icons.Outlined.Delete, "Delete") }
            },
        )
    }, bottomBar = {
        // Always visible: pinned to the bottom of the screen and lifted above the keyboard.
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("error")) }
            Button({ vm.save(onBack) }, modifier = Modifier.fillMaxWidth().testTag("save")) { Text(if (vm.isEditing) "Save" else "Add") }
        }
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
            SlidingSegmented(kinds.map { it.name }, kinds.indexOf(vm.kind), kinds.map { "kind-$it" }, { vm.kind = kinds[it] })

            AmountField(
                vm.amount, vm::changeAmount, if (isTransfer) "Amount sent" else "Amount", vm.currencyOf(vm.accountUid).orEmpty(), "amount",
                Modifier.focusRequester(amountFocus),
            )

            Text(if (isTransfer) "From account" else "Account", style = MaterialTheme.typography.labelLarge)
            AccountChips(options.accounts, vm.accountUid, tagPrefix = if (isTransfer) "from" else "account", onPick = vm::pickAccount)
            val sign = if (vm.kind == EntryKind.Income) 1 else -1 // an expense and a transfer both take money out of this account
            BalanceLine(balances[vm.accountUid], vm.currencyOf(vm.accountUid), Money.parseMinor(vm.amount)?.takeIf { it > 0 && !vm.isEditing }?.times(sign), "account-balance")
            if (isTransfer) {
                Text("To account", style = MaterialTheme.typography.labelLarge)
                AccountChips(options.accounts, vm.toAccountUid, tagPrefix = "to", onPick = vm::pickToAccount)
                val received = if (vm.needsToAmount()) Money.parseMinor(vm.toAmount) else Money.parseMinor(vm.amount)
                BalanceLine(balances[vm.toAccountUid], vm.currencyOf(vm.toAccountUid), received?.takeIf { it > 0 && !vm.isEditing }, "to-balance")
                if (vm.needsToAmount()) {
                    AmountField(vm.toAmount, vm::changeToAmount, "Amount received", vm.currencyOf(vm.toAccountUid).orEmpty(), "to-amount")
                    if (vm.rateNote.isNotEmpty()) {
                        Text(vm.rateNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("to-amount-rate"))
                    }
                }
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

/**
 * The balance of the selected account only, in its currency. For a new entry it also shows what it would be after
 * the typed amount ([change] is signed); an edited entry is already part of the balance, so no preview then.
 */
@Composable
private fun BalanceLine(balance: Long?, currency: String?, change: Long?, tag: String) {
    if (balance == null || currency == null) return
    val text = "Balance ${Money.format(balance, currency)}" + (change?.let { " · after ${Money.format(balance + it, currency)}" } ?: "")
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag(tag))
}

/** Decimal field with a calculator button (tags: `<tag>` for the field, `<tag>-calc` for the button). */
@Composable
private fun AmountField(value: String, onChange: (String) -> Unit, label: String, currency: String, tag: String, modifier: Modifier = Modifier) {
    var calculating by remember { mutableStateOf(false) }
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true,
        suffix = { Text(currency) },
        trailingIcon = { IconButton({ calculating = true }, modifier = Modifier.testTag("$tag-calc")) { Icon(Icons.Outlined.Calculate, "Calculator") } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth().testTag(tag),
    )
    if (calculating) CalculatorSheet(value, { onChange(it); calculating = false }, { calculating = false })
}

/** The "create" tile of a grid: a plus drawn in the current icon style (accent colored), same size and label as a category tile. */
@Composable
fun CreateTile(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.width(76.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge("add", AccentPalette.KEYS.first(), size = 48.dp) // the first palette slot sits exactly on the accent hue
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
fun CategoryTile(category: CategoryEntity, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(76.dp).clickable(onClick = onClick).testTag("category-${category.uid}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(
            category.icon, category.color, size = 48.dp,
            ringColor = if (selected) MaterialTheme.colorScheme.primary else null,
        )
        Text(category.title, style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
            colors = DatePickerDefaults.colors(containerColor = glassContainer(DatePickerDefaults.colors().containerColor)),
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ picking = false }) { Text("Cancel") } },
        ) { GlassWindowBlur(); DatePicker(state) }
    }
}
