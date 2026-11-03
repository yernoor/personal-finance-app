package dev.yernur.moneytracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.LedgerRepository
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.summarize
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountRow(val account: AccountEntity, val balance: Long)
data class AccountsState(val rows: List<AccountRow> = emptyList(), val counted: Long = 0, val all: Long = 0)

class AccountsViewModel(db: AppDatabase) : ViewModel() {
    val state: StateFlow<AccountsState> = combine(
        db.accountDao().observeLive(), db.accountDao().observeBalances(), db.rateDao().observeAll(),
    ) { accounts, balances, rates ->
        val byUid = balances.associate { it.uid to it.balance }
        val summary = summarize(accounts, byUid, rates.associate { (it.fromCurrencyCode to it.toCurrencyCode) to it.value })
        val shown = accounts.filter { it.isActive && !it.isArchived }.map { AccountRow(it, byUid[it.uid] ?: 0L) }
        AccountsState(shown, summary.counted, summary.all)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsState())
}

@Composable
fun AccountsScreen(onOpen: (String) -> Unit, onNew: () -> Unit, onNewTransfer: () -> Unit) {
    val vm = appViewModel { AccountsViewModel(it.db) }
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Total", style = MaterialTheme.typography.labelLarge)
            Text(Money.format(state.counted, DEFAULT_CURRENCY), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("total"))
            Text("All accounts: ${Money.format(state.all, DEFAULT_CURRENCY)}", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onNewTransfer) {
                    Icon(Icons.Outlined.SwapHoriz, null)
                    Text("New transfer", Modifier.padding(start = 8.dp))
                }
                FilledTonalButton(onNew, modifier = Modifier.testTag("new-account")) {
                    Icon(Icons.Outlined.Add, null)
                    Text("Account", Modifier.padding(start = 8.dp))
                }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = LocalChromeInset.current)) {
            items(state.rows, key = { it.account.uid }) { row ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
                    modifier = Modifier.clickable { onOpen(row.account.uid) },
                    leadingContent = { IconBadge(row.account.icon, row.account.color) },
                    headlineContent = { Text(row.account.title.ifBlank { "Main" }) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (row.account.ignoreInBalance) Icon(Icons.Outlined.VisibilityOff, "Hidden from total")
                            Text(Money.format(row.balance, row.account.currencyCode))
                        }
                    },
                )
            }
        }
    }
}

private val CURRENCIES = listOf("KZT", "USD", "EUR", "RUB")
private val ACCOUNT_ICONS = listOf("cash", "bank", "deposit", "piggy_bank", "dollar", "purse", "prepaid")

class AccountEditViewModel(private val db: AppDatabase, private val ledger: LedgerRepository, private val uid: String?) : ViewModel() {
    var title by mutableStateOf("")
    var currency by mutableStateOf(DEFAULT_CURRENCY)
    var icon by mutableStateOf("cash")
    var color by mutableStateOf(CategoryPalette[6])
    var ignoreInBalance by mutableStateOf(false)
    var archived by mutableStateOf(false)
    var loaded by mutableStateOf(uid == null)
    private var original: AccountEntity? = null

    init {
        if (uid != null) viewModelScope.launch {
            db.accountDao().get(uid)?.let {
                original = it
                title = it.title; currency = it.currencyCode; icon = it.icon ?: "cash"; color = it.color ?: color
                ignoreInBalance = it.ignoreInBalance; archived = it.isArchived
            }
            loaded = true
        }
    }

    var balanceText by mutableStateOf("")
    var balanceNote by mutableStateOf<String?>(null)

    /** Overwrites the account's balance with the typed amount (see [LedgerRepository.overwriteBalance]). */
    fun overwriteBalance() = viewModelScope.launch {
        val target = Money.parseMinor(balanceText)
        val account = original
        if (target == null || account == null) { balanceNote = "Enter the real balance, e.g. 4596.71"; return@launch }
        val diff = ledger.overwriteBalance(account.uid, target, java.time.LocalDate.now().toString())
        balanceNote = if (diff == 0L) "Already equal." else "Balance set. Added ${Money.format(diff, account.currencyCode)} as a balance correction."
        balanceText = ""
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val base = original ?: AccountEntity(
            uid = ledger.newAccountUid(), title = "", currencyCode = currency, icon = null, color = null,
            position = db.accountDao().getAll().maxOfOrNull { it.position ?: 0 }?.plus(1) ?: 0, comment = null,
            ignoreInBalance = false, isActive = true, isArchived = false, isFavorite = false, created = "", modified = "",
        )
        ledger.saveAccount(base.copy(title = title.trim(), icon = icon, color = color, ignoreInBalance = ignoreInBalance, isArchived = archived))
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountEditScreen(uid: String?, onBack: () -> Unit) {
    val vm = appViewModel(key = "account-$uid") { AccountEditViewModel(it.db, it.ledger, uid) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (uid == null) "New account" else "Edit account") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(vm.title, { vm.title = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("account-title"))
            Text("Currency", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CURRENCIES.forEach { code ->
                    FilterChip(vm.currency == code, { vm.currency = code }, label = { Text(code) }, enabled = uid == null)
                }
            }
            Text("Icon", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ACCOUNT_ICONS.forEach { name ->
                    IconButton({ vm.icon = name }) { PickableIcon(name, vm.color, vm.icon == name) }
                }
            }
            Text("Color", style = MaterialTheme.typography.labelLarge)
            ColorPicker(vm.color) { vm.color = it }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Hide from the Total", Modifier.weight(1f))
                Switch(vm.ignoreInBalance, { vm.ignoreInBalance = it })
            }
            if (uid != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Archive account", Modifier.weight(1f))
                Switch(vm.archived, { vm.archived = it })
            }
            if (uid != null) {
                Text("Overwrite balance", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    vm.balanceText, { vm.balanceText = it }, label = { Text("Real balance now") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("balance-input"),
                    supportingText = { Text(vm.balanceNote ?: "Adds an income or expense without a category for the difference.") },
                )
                OutlinedButton(vm::overwriteBalance, enabled = vm.balanceText.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("balance-apply")) { Text("Overwrite balance") }
            }
            Button({ vm.save(onBack) }, enabled = vm.title.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("save")) { Text("Save") }
        }
    }
}
