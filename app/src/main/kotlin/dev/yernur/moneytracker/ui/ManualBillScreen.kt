package dev.yernur.moneytracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import dev.yernur.moneytracker.data.BillRepository
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.RECURRING_MONTHS_BACK
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.TxFull
import dev.yernur.moneytracker.data.looksAlike
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A category with how often it was paid over the months looked at. */
data class CategoryUse(val category: CategoryEntity, val payments: Int, val months: Int)

/** Adding a recurring bill the detection missed: a category, then its payments, then a name and where they live. */
class ManualBillViewModel(db: AppDatabase, private val bills: BillRepository, today: LocalDate = LocalDate.now()) : ViewModel() {
    var step by mutableIntStateOf(1)
    /** The months looked at, oldest first: the same window the detection reads. */
    val months: List<YearMonth> = (RECURRING_MONTHS_BACK - 1 downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()) }
    /** Expenses in [months], newest first. */
    var rows by mutableStateOf<List<TxFull>>(emptyList())
        private set
    /** The category picked in step 1; null with [all] for every category. */
    var source by mutableStateOf<String?>(null)
        private set
    var all by mutableStateOf(false)
        private set
    val picked: SnapshotStateList<String> = mutableListOf<String>().toMutableStateList()
    /** Ticked because they look like the first one ticked: marked so a wrong guess is easy to spot. */
    val guessed: SnapshotStateList<String> = mutableListOf<String>().toMutableStateList()
    var name by mutableStateOf("")
    /** Where the payments go: a category uid, or null to leave them where they are. */
    var target by mutableStateOf<String?>(null)
    var setComment by mutableStateOf(true)
    private var before: Set<String>? = null

    val categories: StateFlow<List<CategoryEntity>> = db.categoryDao().observeAll().map { all ->
        val live = all.filter { !it.isRemoved && !it.isArchived && it.type == TransactionType.Expense }
        before?.let { known -> live.firstOrNull { it.uid !in known }?.let { created -> target = created.uid; before = null } }
        live
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            rows = db.transactionDao().getFull(months.first().atDay(1).toString(), today.toString())
                .filter { it.type == TransactionType.Expense && it.amount > 0 }.sortedWith(compareByDescending<TxFull> { it.date }.thenByDescending { it.created })
        }
    }

    /** Categories with payments, the fewest payments a month first: bills rarely hide among groceries. */
    fun uses(categories: List<CategoryEntity>): List<CategoryUse> {
        val byUid = rows.filter { it.categoryUid != null }.groupBy { it.categoryUid!! }
        return categories.mapNotNull { c -> byUid[c.uid]?.let { l -> CategoryUse(c, l.size, l.map { month(it) }.distinct().size) } }
            .sortedWith(compareBy<CategoryUse> { it.payments.toDouble() / it.months }.thenBy { it.category.title })
    }

    fun choose(category: String?) {
        if (category == source && all == (category == null)) return
        source = category
        all = category == null
        picked.clear(); guessed.clear(); target = null
    }

    val inSource: List<TxFull> get() = if (all) rows else rows.filter { it.categoryUid == source }
    val chosen: List<TxFull> get() = inSource.filter { it.uid in picked }

    /** Ticking the first payment also ticks the ones that look like it; later ticks are one at a time. */
    fun toggle(row: TxFull) {
        if (row.uid in picked) { picked.remove(row.uid); guessed.remove(row.uid); return }
        if (picked.isEmpty()) {
            val alike = inSource.filter { it.uid != row.uid && looksAlike(row, it) }.map { it.uid }
            guessed.addAll(alike); picked.addAll(alike)
        }
        picked.add(row.uid)
    }

    /** The ticked payments' total per month, for the strip and the monthly figure. */
    fun perMonth(): Map<YearMonth, Long> = chosen.groupBy(::month).mapValues { (_, l) -> l.sumOf { it.amount } }
    fun monthly(): Long = perMonth().values.let { if (it.isEmpty()) 0 else it.sum() / it.size }

    /** Step 3 starts with the most used comment of the ticked payments, else the category's name. */
    /** The payments ticked when step 3 opened: its list, which can still be unticked there. */
    var placed by mutableStateOf<List<TxFull>>(emptyList())
        private set

    fun toNaming(categories: List<CategoryEntity>) {
        placed = chosen
        if (name.isBlank()) name = chosen.mapNotNull { it.comment?.trim()?.takeIf(String::isNotEmpty) }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: categories.firstOrNull { it.uid == source }?.title.orEmpty()
        step = 3
    }

    fun expectNewCategory() {
        before = categories.value.map { it.uid }.toSet()
    }

    /** Adds the bill, then [onDone] on the main thread (navigation must not run on Room's transaction thread). */
    fun add(onDone: () -> Unit) {
        if (name.isBlank() || picked.isEmpty()) return
        val to = categories.value.firstOrNull { it.uid == target }
        viewModelScope.launch {
            bills.addManual(name, chosen.map { it.uid }, to?.uid, to?.title, setComment, monthly())
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    private fun month(tx: TxFull) = YearMonth.from(LocalDate.parse(tx.date))
}

private val monthName = DateTimeFormatter.ofPattern("LLLL")
private val monthShort = DateTimeFormatter.ofPattern("LLL")
private fun money(minor: Long) = Money.formatRounded(minor, DEFAULT_CURRENCY)

/**
 * Recurring -> "Add a bill by hand", for a bill the detection missed (amounts that vary, a skipped month, no comment). Three steps on one
 * screen: the category, the payments, then a name and where they live. Step 3 uses the Move payments screen's pieces ([MoveToPicker],
 * [CommentCheck], [PaymentRow]). Design: the "Manual recurring bill" canvas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualBillScreen(onNewCategory: (name: String) -> Unit, onDone: () -> Unit) {
    val vm = appViewModel(key = "manual-bill") { ManualBillViewModel(it.db, it.bills) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    BackHandler(vm.step > 1) { vm.step-- }
    Scaffold(
        topBar = {
            AppTopBar(
                title = {
                    Column {
                        Text("Step ${vm.step} of 3", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(listOf("Which category is it in?", "Tick the bill's payments", "Name and place it")[vm.step - 1])
                    }
                },
                navigationIcon = { IconButton({ if (vm.step > 1) vm.step-- else onDone() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val chosen = vm.chosen
                if (vm.step == 2 && chosen.isNotEmpty()) {
                    val months = vm.perMonth().keys.sorted()
                    val missing = vm.months.count { it >= months.first() && it <= months.last() && it !in months }
                    Row {
                        Text(
                            "${chosen.size} payment${if (chosen.size == 1) "" else "s"} · ${months.first().format(monthShort)}–${months.last().format(monthShort)}" +
                                (if (missing > 0) " · $missing month${if (missing == 1) "" else "s"} missing" else ""),
                            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("≈ ${money(vm.monthly())}/mo", style = MaterialTheme.typography.labelLarge)
                    }
                }
                val (label, ready) = when (vm.step) {
                    1 -> "Next: pick payments" to (vm.source != null || vm.all)
                    2 -> (if (chosen.isEmpty()) "Tick the bill's payments" else "Next: name it") to chosen.isNotEmpty()
                    else -> when {
                        chosen.isEmpty() -> "Select payments" to false
                        vm.name.isBlank() -> "Enter a name" to false
                        else -> "Add bill · ${chosen.size} payment${if (chosen.size == 1) "" else "s"} · ≈ ${money(vm.monthly())}/mo" to true
                    }
                }
                Button(
                    { when (vm.step) { 1 -> vm.step = 2; 2 -> vm.toNaming(categories); else -> vm.add(onDone) } },
                    Modifier.fillMaxWidth().height(52.dp).testTag("manual-next"), enabled = ready,
                ) { Text(label) }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).testTag("manual-bill"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (vm.step) {
                1 -> categoryStep(vm, categories)
                2 -> paymentsStep(vm)
                else -> placeStep(vm, categories) { vm.expectNewCategory(); onNewCategory(vm.name.trim()) }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.categoryStep(vm: ManualBillViewModel, categories: List<CategoryEntity>) {
    item { Note("Last $RECURRING_MONTHS_BACK months. Fewer payments a month first: bills rarely hide among groceries.") }
    items(vm.uses(categories), key = { it.category.uid }) { use ->
        ChoiceRow(vm.source == use.category.uid, "manual-cat-${use.category.title}", { vm.choose(use.category.uid) }, {
            IconBadge(use.category.icon, use.category.color, size = 40.dp)
        }, use.category.title, "${use.payments} payment${if (use.payments == 1) "" else "s"} · ${use.months} mo")
    }
    item {
        ChoiceRow(vm.all, "manual-cat-all", { vm.choose(null) }, {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Category, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, "All categories", "a bill spread across several")
    }
}

@Composable
private fun ChoiceRow(on: Boolean, tag: String, onClick: () -> Unit, dot: @Composable () -> Unit, title: String, detail: String) {
    Row(
        Modifier.fillMaxWidth().background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        dot()
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (on) Icon(Icons.Outlined.Check, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.paymentsStep(vm: ManualBillViewModel) {
    item { Note("Tick one and the ones like it (amount within 20%, day within 3) get ticked too. Untick any wrong guess.") }
    item { MonthStrip(vm.months, vm.perMonth()) }
    val byMonth = vm.inSource.groupBy { YearMonth.from(LocalDate.parse(it.date)) }
    vm.months.reversed().forEach { m ->
        val list = byMonth[m].orEmpty()
        item(key = "m-$m") {
            Text(
                m.format(monthName) + if (list.isEmpty()) " · no payment" else "", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
            )
        }
        items(list, key = { it.uid }) { row -> PaymentRow(row, row.uid in vm.picked, row.uid in vm.guessed, "manual-row-${row.uid}") { vm.toggle(row) } }
    }
}

/** One bar per month: the ticked payments' total, so a gap or an odd month shows at a glance. */
@Composable
private fun MonthStrip(months: List<YearMonth>, totals: Map<YearMonth, Long>) {
    val top = totals.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Column(Modifier.padding(vertical = 4.dp).testTag("manual-strip"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            months.forEach { m ->
                val v = totals[m]
                Box(
                    Modifier.weight(1f).fillMaxHeight(if (v == null) 0.08f else 0.2f + 0.8f * v / top)
                        .background(if (v == null) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            months.forEach { Text(it.format(monthShort), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.placeStep(vm: ManualBillViewModel, categories: List<CategoryEntity>, onNew: () -> Unit) {
    val source = categories.firstOrNull { it.uid == vm.source }
    // Laid out like the Move payments screen: where from, where to, which payments, the comment.
    item {
        val others = source != null && vm.inSource.any { it.uid !in vm.picked }
        Note(
            (source?.let { "From ${it.title}" + if (others) ", which also has other payments." else "." } ?: "From several categories.") +
                " Its other payments are not listed and stay where they are.",
        )
    }
    item {
        OutlinedTextField(vm.name, { vm.name = it }, Modifier.fillMaxWidth().testTag("manual-name"), label = { Text("Bill name") }, singleLine = true)
    }
    item { Text("Move to", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
    item {
        MoveToPicker(
            vm.name.trim().ifEmpty { "bill" }, categories.filter { it.uid != vm.source }, vm.target, onNew, { vm.target = it },
            keepLabel = source?.let { "Keep in ${it.title}" } ?: "Keep where they are", keepCategory = source,
        )
    }
    item {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Payments · ${vm.chosen.size}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            TextButton({ vm.picked.clear(); vm.picked.addAll(vm.placed.map { it.uid }) }) { Text("Select all") }
            TextButton({ vm.picked.clear() }) { Text("None") }
        }
    }
    items(vm.placed, key = { "placed-${it.uid}" }) { row ->
        PaymentRow(row, row.uid in vm.picked, row.uid in vm.guessed, "manual-placed-${row.uid}") {
            if (row.uid in vm.picked) vm.picked.remove(row.uid) else vm.picked.add(row.uid)
        }
    }
    item {
        val n = vm.chosen.size
        // Left where they are, the comment is the only thing that ties the payments together: it is always set then.
        if (vm.target != null) CommentCheck(vm.setComment, { vm.setComment = it }, "Set the comment “${vm.name.trim()}” on all $n, so future payments are found by it")
        else Note("The comment “${vm.name.trim()}” is set on all $n: that is how the bill finds its payments.")
    }
}

@Composable
private fun Note(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
