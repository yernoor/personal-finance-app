package dev.yernur.moneytracker.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.AffordResult
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.Verdict
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.TransactionType
import kotlinx.coroutines.launch

private fun money(minor: Long) = Money.formatRounded(minor, DEFAULT_CURRENCY)

/** The answer line: the verdict word, then the money left (or missing) until payday, and why it is tight. */
fun AffordResult.answer(categoryTitle: String?): String {
    val until = "until ${horizon.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))}"
    return when {
        verdict == Verdict.No -> "${money(-afterPurchase)} short $until"
        (limit?.over ?: 0L) > 0 -> "${categoryTitle ?: "This category"} goes over its limit"
        else -> "${money(afterPurchase)} left $until"
    }
}

/**
 * "Can I afford ₸ ___ ?" on Analysis -> Overview, the only place it lives (owner 2026-11-19: option A of the "Overview afford block" canvas,
 * which replaced the separate screen). One line until asked; the answer fills the lower half in the verdict's colour, with small category and
 * account chips that re-check at once, and "Why" unfolds the breakdown in place.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AffordCard() {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val accounts by remember { container.db.accountDao().observeLive() }.collectAsStateWithLifecycle(emptyList())
    val categories by remember { container.db.categoryDao().observeLive() }.collectAsStateWithLifecycle(emptyList())
    val usable = accounts.filter { it.isActive && !it.isArchived }
    val expenseCategories = categories.filter { it.type == TransactionType.Expense && !it.isArchived }
    var price by rememberSaveable { mutableStateOf("") }
    var categoryUid by rememberSaveable { mutableStateOf<String?>(null) }
    var accountUid by rememberSaveable { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<AffordResult?>(null) }
    var failed by remember { mutableStateOf(false) }
    var why by rememberSaveable { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }
    val account = usable.firstOrNull { it.uid == accountUid } ?: usable.firstOrNull { it.uid == "main" } ?: usable.firstOrNull()
    val category = expenseCategories.firstOrNull { it.uid == categoryUid }
    val minor = Money.parseMinor(price)?.takeIf { it > 0 }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    fun check() {
        val amount = minor ?: return
        val from = account ?: return
        focus.clearFocus() // closes the keyboard, which otherwise covers the answer
        scope.launch {
            result = container.afford.check(amount, categoryUid, from.uid)
            failed = result == null
        }
    }
    // A chip changed after an answer: answer again with it (the effect sees the new pick, a click handler would see the old one).
    androidx.compose.runtime.LaunchedEffect(accountUid, categoryUid) { if (result != null) check() }

    AppCard(Modifier.fillMaxWidth().testTag("afford-card")) {
        Column(Modifier.animateContentSize()) {
            Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Can I afford", style = MaterialTheme.typography.titleSmall)
                BasicTextField(
                    price, { price = it; result = null; failed = false },
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer).testTag("afford-card-price"),
                    singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSecondaryContainer),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { check() }),
                    decorationBox = { field ->
                        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("₸", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box(Modifier.weight(1f)) {
                                if (price.isEmpty()) Text("price", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                field()
                            }
                        }
                    },
                )
                FilledIconButton(::check, Modifier.size(44.dp).testTag("afford-card-check"), enabled = minor != null && account != null) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Check")
                }
            }
            if (failed) Text(
                "No exchange rate is known for ${account?.title ?: "this account"}, so its balance cannot be compared.",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            )
            result?.let { r ->
                val scheme = MaterialTheme.colorScheme
                val (bg, fg, word) = when (r.verdict) {
                    Verdict.Yes -> Triple(scheme.primaryContainer, scheme.onPrimaryContainer, "Yes")
                    Verdict.Tight -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, "Tight")
                    Verdict.No -> Triple(scheme.errorContainer, scheme.onErrorContainer, "No")
                }
                Row(Modifier.fillMaxWidth().background(bg).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(word, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = fg, modifier = Modifier.testTag("afford-card-verdict"))
                    Text(" · ${r.answer(category?.title)}", style = MaterialTheme.typography.titleSmall, color = fg)
                }
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Chip(Modifier.testTag("afford-card-category"), onClick = { picking = true }) {
                        if (category != null) IconBadge(category.icon, category.color, size = 24.dp)
                        Text(category?.title ?: "Any category", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                    Box {
                        Chip(Modifier.testTag("afford-card-account"), onClick = { accountMenu = true }) {
                            Text(account?.title?.ifBlank { "Main" } ?: "No account", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                        DropdownMenu(accountMenu, { accountMenu = false }) {
                            usable.forEach { a ->
                                DropdownMenuItem({ Text(a.title.ifBlank { "Main" }) }, { accountUid = a.uid; accountMenu = false }, Modifier.testTag("afford-card-account-${a.uid}"))
                            }
                        }
                    }
                    Box(Modifier.weight(1f))
                    TextButton({ why = !why }, Modifier.testTag("afford-card-why")) {
                        Text("Why")
                        Icon(if (why) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                    }
                }
                if (why) Breakdown(r, account?.title.orEmpty(), category?.title)
            }
        }
    }
    if (picking) ModalBottomSheet({ picking = false }, containerColor = glassContainer(BottomSheetDefaults.ContainerColor)) {
        GlassWindowBlur()
        CategorySheet(expenseCategories, categoryUid) { picked ->
            categoryUid = picked
            picking = false
        }
    }
}

/** A small outlined dropdown chip (the design's category and account pickers). */
@Composable
private fun Chip(modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(start = 8.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        content()
        Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp))
    }
}

/** The Add screen's icon tiles, two rows then More, plus "Any category" (checks without a category limit). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySheet(all: List<CategoryEntity>, selectedUid: String?, onPick: (String?) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Category", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            visibleCategories(all, expanded, selectedUid).forEach { c -> CategoryTile(c, selected = c.uid == selectedUid) { onPick(c.uid) } }
            if (all.size > COLLAPSED_CATEGORIES) ExpandTile(expanded, Modifier.width(76.dp)) { expanded = !expanded }
        }
        TextButton({ onPick(null) }, Modifier.testTag("afford-card-any")) { Text("Any category") }
    }
}

/** What "Why" unfolds: the free-money sum, the category limit and goals, as the old Can I afford screen showed them. */
@Composable
private fun Breakdown(r: AffordResult, accountTitle: String, categoryTitle: String?) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp).testTag("afford-card-breakdown"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Note(
            "Counts this purchase" + (if (r.scheduledCount > 0) ", your usual spending until then and ${r.scheduledCount} scheduled payment${if (r.scheduledCount == 1) "" else "s"}" else " and your usual spending until then") + ".",
        )
        Line("Balance on ${accountTitle.ifBlank { "Main" }}", money(r.balance))
        Line("Expected spending, ${r.daysToHorizon} days", "-${money(r.expected)}")
        Line("Scheduled payments (${r.scheduledCount})", "-${money(r.scheduled)}")
        Line("This purchase", "-${money(r.price)}")
        Line("Free after purchase", money(r.afterPurchase), strong = true)
        r.afterPurchaseAverage?.let { Note("With your 3-month average instead: ${money(it)}") }
        r.limit?.let { limit ->
            Line("${categoryTitle ?: "Category"} limit", money(limit.limit))
            Line("With purchase, on track for", money(limit.withPurchase))
            if (limit.over > 0) Text("Over the limit by ${money(limit.over)}.", style = MaterialTheme.typography.bodySmall, color = Semantic.expense)
        }
        r.goals.forEach { Note("Goal ${it.title}: about ${it.days} days later at your recent saving pace.") }
        Note("Payday is the next income reminder, else the end of the month. An estimate from your own history, not financial advice.")
    }
}

@Composable
private fun Line(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Note(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
