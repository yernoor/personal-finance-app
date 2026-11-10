package dev.yernur.moneytracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.LedgerRepository
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.ProjectionMode
import dev.yernur.moneytracker.data.db.TransactionType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(onOpen: (uid: String) -> Unit, onNew: (TransactionType) -> Unit, onBack: () -> Unit, embedded: Boolean = false) {
    val vm = appViewModel { CategoriesViewModel(it.db) }
    val categories by vm.categories.collectAsStateWithLifecycle(emptyList())
    var tab by remember { mutableStateOf(TransactionType.Expense) }
    Scaffold(contentWindowInsets = embeddedInsets(embedded), topBar = {
        if (!embedded) AppTopBar(title = { Text("Categories") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val tabs = listOf(TransactionType.Expense, TransactionType.Income)
            SlidingSegmented(
                listOf("Expenses", "Income"), tabs.indexOf(tab), tabs.map { "tab-$it" }, { tab = tabs[it] },
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            FlowRow(
                Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + LocalChromeInset.current),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                categories.filter { it.type == tab && !it.isArchived }.forEach { c -> CategoryTile(c, selected = false) { onOpen(c.uid) } }
                CreateTile("Create", Modifier.testTag("new-category")) { onNew(tab) }
            }
        }
    }
}

class CategoriesViewModel(db: AppDatabase) : ViewModel() {
    val categories = db.categoryDao().observeLive()
}

class CategoryEditViewModel(
    private val db: AppDatabase,
    private val ledger: LedgerRepository,
    private val uid: String?,
    private val type: TransactionType,
) : ViewModel() {
    var title by mutableStateOf("")
    var icon by mutableStateOf("other")
    var color by mutableStateOf(CategoryPalette[6])
    var limit by mutableStateOf("")
    var threshold by mutableStateOf("")
    var mode by mutableStateOf(ProjectionMode.Auto)
    private var original: CategoryEntity? = null
    val isEditing get() = uid != null

    init {
        if (uid != null) viewModelScope.launch {
            db.categoryDao().get(uid)?.let {
                original = it
                title = it.title; icon = it.icon ?: "other"; color = it.color ?: color
                limit = it.limitAmount?.let(Money::editable).orEmpty()
                threshold = it.warnThresholdPercent?.toString().orEmpty()
                mode = it.projectionMode
            }
        }
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val base = original ?: CategoryEntity(
            uid = ledger.newCategoryUid(), title = "", type = type, icon = null, color = null, useCount = 0, lastUse = null,
            limitAmount = null, position = db.categoryDao().nextPosition(type), isArchived = false, created = "", modified = "",
        )
        ledger.saveCategory(base.copy(title = title.trim(), icon = icon, color = color, limitAmount = Money.parseMinor(limit)?.takeIf { it > 0 },
            warnThresholdPercent = threshold.trim().toIntOrNull()?.takeIf { it > 0 }, projectionMode = mode,
        ))
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        uid?.let { ledger.deleteCategory(it) }
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryEditScreen(uid: String?, type: TransactionType, onBack: () -> Unit) {
    val vm = appViewModel(key = "category-$uid-$type") { CategoryEditViewModel(it.db, it.ledger, uid, type) }
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        AppTopBar(
            title = { Text(if (uid == null) "New category" else "Edit category") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            actions = { if (uid != null) IconButton({ confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete") } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(vm.title, { vm.title = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("category-title"))
            if (type == TransactionType.Expense) OutlinedTextField(
                vm.limit, { vm.limit = it }, label = { Text("Planned outlay per month") }, suffix = { Text(DEFAULT_CURRENCY) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("category-limit"),
            )
            if (type == TransactionType.Expense) {
                OutlinedTextField(
                    vm.threshold, { vm.threshold = it.filter(Char::isDigit) }, label = { Text("Warn at % of the limit (empty = default)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("category-threshold"),
                )
                Text("In the overall projection", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ProjectionMode.Auto to "Automatic", ProjectionMode.Include to "Always", ProjectionMode.Exclude to "Never").forEach { (m, label) ->
                        FilterChip(vm.mode == m, { vm.mode = m }, label = { Text(label) }, modifier = Modifier.testTag("mode-$m"))
                    }
                }
            }
            Text("Icon", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIcons.names.forEach { name ->
                    PickableIcon(name, vm.color, vm.icon == name) { vm.icon = name }
                }
            }
            ColorSection(vm.color) { vm.color = it }
            Button({ vm.save(onBack) }, enabled = vm.title.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("save")) { Text("Save") }
        }
    }
    if (confirmDelete) ConfirmDialog("Delete this category? Existing entries keep it.", "Delete", { confirmDelete = false }) {
        confirmDelete = false; vm.delete(onBack)
    }
}
