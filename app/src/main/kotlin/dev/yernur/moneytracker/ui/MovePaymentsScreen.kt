package dev.yernur.moneytracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.yernur.moneytracker.data.AccentPalette
import dev.yernur.moneytracker.data.Bill
import dev.yernur.moneytracker.data.BillRepository
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.MoveCandidates
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.TxFull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Moving one recurring bill's payments out of the category they share (Internet out of Rent). */
class MovePaymentsViewModel(db: AppDatabase, private val bills: BillRepository, private val key: String) : ViewModel() {
    var bill by mutableStateOf<Bill?>(null)
        private set
    var candidates by mutableStateOf(MoveCandidates(emptyList(), emptyList()))
        private set
    /** Ticked payments; every offered one starts ticked (the look-alikes are marked, so the owner can untick a wrong guess). */
    val picked: SnapshotStateList<String> = mutableListOf<String>().toMutableStateList()
    /** Where to move them: a category uid, or null for "new". */
    var target by mutableStateOf<String?>(null)
    var addComment by mutableStateOf(true)
    /** Categories that existed when "new" was tapped: the first other one to appear is the one just created, and gets picked. */
    private var before: Set<String>? = null

    val categories: StateFlow<List<CategoryEntity>> = db.categoryDao().observeAll().map { all ->
        val live = all.filter { !it.isRemoved && !it.isArchived && it.type == TransactionType.Expense }
        before?.let { known -> live.firstOrNull { it.uid !in known }?.let { created -> target = created.uid; before = null } }
        live
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            val found = bills.current(LocalDate.now()).firstOrNull { it.key == key } ?: return@launch
            bill = found
            candidates = bills.moveCandidates(found)
            picked.addAll((candidates.sameComment + candidates.lookAlike).map { it.uid })
        }
    }

    fun expectNewCategory() {
        before = categories.value.map { it.uid }.toSet()
    }

    /** Moves the ticked payments, then [onDone] on the main thread (navigation must not run on Room's transaction thread). */
    fun move(onDone: () -> Unit) {
        val b = bill ?: return
        val to = categories.value.firstOrNull { it.uid == target } ?: return
        viewModelScope.launch {
            bills.move(b, picked.toList(), to.uid, to.title, addComment)
            withContext(Dispatchers.Main) { onDone() }
        }
    }
}

private val rowDate = DateTimeFormatter.ofPattern("MMM d, yyyy")

/**
 * Pick which of a bill's payments to move and where: the same comment (ticked), and ones without a comment that look like it (ticked,
 * marked as a guess). "New" opens the usual category screen with the bill's name filled in ([onNewCategory]); the category made there comes
 * back picked. After moving, [onDone] (the Recurring page offers Undo).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MovePaymentsScreen(key: String, onNewCategory: (name: String) -> Unit, onDone: () -> Unit) {
    val vm = appViewModel(key = "move-$key") { MovePaymentsViewModel(it.db, it.bills, key) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val bill = vm.bill
    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text(bill?.let { "Move ${it.item.title} payments" } ?: "Move payments") },
                navigationIcon = { IconButton(onDone) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            val all = vm.candidates.sameComment + vm.candidates.lookAlike
            val chosen = all.filter { it.uid in vm.picked }
            val ready = chosen.isNotEmpty() && vm.target != null
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                Button(
                    { vm.move(onDone) }, Modifier.fillMaxWidth().height(52.dp).testTag("move-go"), enabled = ready,
                ) {
                    Text(
                        when {
                            chosen.isEmpty() -> "Select payments to move"
                            vm.target == null -> "Choose where to move them"
                            else -> "Move ${chosen.size} payment${if (chosen.size == 1) "" else "s"} · ${Money.formatRounded(chosen.sumOf { it.amount }, DEFAULT_CURRENCY)}"
                        },
                    )
                }
            }
        },
    ) { padding ->
        if (bill == null) return@Scaffold
        val source = bill.category?.title.orEmpty()
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).testTag("move-list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(
                    "From $source. Its other payments are not listed and stay where they are.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Text("Move to", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
            item {
                MoveToPicker(bill.item.title, categories.filter { it.uid != bill.item.categoryUid }, vm.target, { vm.expectNewCategory(); onNewCategory(bill.item.title) }, { vm.target = it })
            }
            item {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Payments", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    TextButton({ vm.picked.clear(); vm.picked.addAll((vm.candidates.sameComment + vm.candidates.lookAlike).map { it.uid }) }) { Text("Select all") }
                    TextButton({ vm.picked.clear() }) { Text("None") }
                }
            }
            group("Same comment “${bill.item.title.lowercase()}” · ${vm.candidates.sameComment.size}", vm.candidates.sameComment, vm.picked, guess = false)
            group("No comment, but looks like ${bill.item.title} · ${vm.candidates.lookAlike.size}", vm.candidates.lookAlike, vm.picked, guess = true)
            item {
                CommentCheck(vm.addComment, { vm.addComment = it }, "Add the comment “${bill.item.title}” to the ones without one, so next time they are found by comment")
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.group(title: String, rows: List<TxFull>, picked: SnapshotStateList<String>, guess: Boolean) {
    if (rows.isEmpty()) return
    item { Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
    items(rows, key = { it.uid }) { row ->
        val on = row.uid in picked
        PaymentRow(row, on, guess, "move-row-${row.uid}") { if (on) picked.remove(row.uid) else picked.add(row.uid) }
    }
}

/** One payment with a tick box: date, comment, amount. A [guess] (ticked because it looks like the bill) is marked in the tertiary color. */
@Composable
internal fun PaymentRow(row: TxFull, on: Boolean, guess: Boolean, tag: String, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 4.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(on, { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(LocalDate.parse(row.date).format(rowDate), style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(row.comment?.takeIf { it.isNotBlank() }?.let { "“$it”" } ?: "No comment", "similar amount and day".takeIf { guess }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = if (guess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(Money.formatRounded(row.amount, DEFAULT_CURRENCY), style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Where a bill's payments go. Two big tiles first: [keepLabel] (when given: leave them where they are, selected while [target] is null, with
 * [keepCategory]'s icon) and "New: [name]" ([onNew]). The other [categories] sit in a block that expands, drawn with the add screen's
 * [CategoryTile] (icon over its name); it opens by itself when one of them is the target (a category just made with New comes back picked).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MoveToPicker(
    name: String, categories: List<CategoryEntity>, target: String?, onNew: () -> Unit, onPick: (String?) -> Unit,
    keepLabel: String? = null, keepCategory: CategoryEntity? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(target) { if (target != null) expanded = true }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (keepLabel != null) BigTile(target == null, keepLabel, "move-keep", Modifier.weight(1f), { onPick(null) }) {
                if (keepCategory != null) IconBadge(keepCategory.icon, keepCategory.color, size = 48.dp)
                else IconBadge("other", null, size = 48.dp)
            }
            BigTile(false, "New: $name", "move-new", Modifier.weight(1f), onNew) { IconBadge("add", AccentPalette.KEYS.first(), size = 48.dp) }
        }
        if (categories.isNotEmpty()) {
            val picked = categories.firstOrNull { it.uid == target }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { expanded = !expanded }.padding(vertical = 8.dp).testTag("move-others"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Other categories" + (picked?.let { " · ${it.title}" } ?: ""), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "Less" else "More")
            }
            AnimatedVisibility(expanded) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    categories.forEach { c -> Box(Modifier.testTag("move-to-${c.title}")) { CategoryTile(c, target == c.uid) { onPick(c.uid) } } }
                }
            }
        }
    }
}

/** A large choice: icon over a label, in a tinted rounded box; [selected] gets the primary outline. */
@Composable
private fun BigTile(selected: Boolean, label: String, tag: String, modifier: Modifier, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 14.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon()
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 2, textAlign = TextAlign.Center)
    }
}

/** The "also set the comment" tick box under the move choices. */
@Composable
internal fun CommentCheck(checked: Boolean, onChange: (Boolean) -> Unit, text: String) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange, Modifier.testTag("move-add-comment"))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
