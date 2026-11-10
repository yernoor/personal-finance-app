package dev.yernur.moneytracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.AffordResult
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.Verdict
import dev.yernur.moneytracker.data.db.TransactionType
import kotlinx.coroutines.launch

private fun money(minor: Long) = Money.formatRounded(minor, DEFAULT_CURRENCY)

/** The headline sentence of a result. */
fun AffordResult.headline(categoryTitle: String?): String = when (verdict) {
    Verdict.Yes -> "Yes, you can afford it."
    Verdict.Tight -> if ((limit?.over ?: 0L) > 0) "You can pay for it, but ${categoryTitle ?: "this category"} goes over its limit."
    else "You can pay for it, but it uses most of your free money."
    Verdict.No -> "Not before payday: it is ${money(-afterPurchase)} more than the free money."
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AffordScreen(onBack: () -> Unit, onLogExpense: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val categories by remember { container.db.categoryDao().observeLive() }.collectAsStateWithLifecycle(emptyList())
    val accounts by remember { container.db.accountDao().observeLive() }.collectAsStateWithLifecycle(emptyList())
    val expenseCategories = categories.filter { it.type == TransactionType.Expense && !it.isArchived }.sortedBy { it.position }
    val usable = accounts.filter { it.isActive && !it.isArchived }

    var price by rememberSaveable { mutableStateOf("") }
    var categoryUid by rememberSaveable { mutableStateOf<String?>(null) }
    var accountUid by rememberSaveable { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<AffordResult?>(null) }
    var failed by remember { mutableStateOf(false) }

    val account = usable.firstOrNull { it.uid == accountUid } ?: usable.firstOrNull { it.uid == "main" } ?: usable.firstOrNull()
    val minor = Money.parseMinor(price)?.takeIf { it > 0 }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("Can I afford this?") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Checks the balance, expected spending until payday, scheduled payments, limits and goals. All calculated on this phone.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                price, { price = it; result = null }, Modifier.fillMaxWidth().testTag("afford-price"), singleLine = true,
                label = { Text("Price ($DEFAULT_CURRENCY)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Text("Category", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                expenseCategories.forEach { c ->
                    FilterChip(categoryUid == c.uid, { categoryUid = if (categoryUid == c.uid) null else c.uid; result = null }, { Text(c.title) }, Modifier.testTag("afford-category-${c.uid}"))
                }
            }
            Text("Paying from", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                usable.forEach { a ->
                    FilterChip(account?.uid == a.uid, { accountUid = a.uid; result = null }, { Text(a.title) }, Modifier.testTag("afford-account-${a.uid}"))
                }
            }
            Button(
                {
                    val target = account ?: return@Button
                    val amount = minor ?: return@Button
                    scope.launch {
                        result = container.afford.check(amount, categoryUid, target.uid)
                        failed = result == null
                    }
                },
                Modifier.fillMaxWidth().testTag("afford-check"), enabled = minor != null && account != null,
            ) { Text("Check") }
            if (failed && result == null) Text("No exchange rate is known for this account, so its balance cannot be compared.", color = MaterialTheme.colorScheme.error)

            result?.let { r ->
                VerdictCard(r, categories.firstOrNull { it.uid == categoryUid }?.title)
                BreakdownCard(r, account?.title.orEmpty())
                r.limit?.let { LimitCard(it, categories.firstOrNull { c -> c.uid == categoryUid }?.title.orEmpty()) }
                r.goals.forEach { GoalCard(it.title, it.days) }
                Text("An estimate from your own history, not financial advice.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton({ price = ""; result = null }, Modifier.weight(1f).testTag("afford-another")) { Text("Check another") }
                    Button(onLogExpense, Modifier.weight(1f).testTag("afford-log")) { Text("Log as expense") }
                }
            }
        }
    }
}

@Composable
private fun VerdictCard(r: AffordResult, categoryTitle: String?) {
    val scheme = MaterialTheme.colorScheme
    val (container, content, label) = when (r.verdict) {
        Verdict.Yes -> Triple(scheme.primaryContainer, scheme.onPrimaryContainer, "YES")
        Verdict.Tight -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, "TIGHT")
        Verdict.No -> Triple(scheme.errorContainer, scheme.onErrorContainer, "NO")
    }
    AppCard(Modifier.fillMaxWidth().testTag("afford-verdict"), colors = CardDefaults.cardColors(containerColor = container, contentColor = content)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("afford-verdict-label"))
            Text(r.headline(categoryTitle), style = MaterialTheme.typography.titleLarge)
            Text("After buying, about ${money(r.afterPurchase.coerceAtLeast(0))} stays free until ${r.horizon}.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BreakdownCard(r: AffordResult, accountTitle: String) {
    AppCard(Modifier.fillMaxWidth().testTag("afford-breakdown")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Free money until payday", style = MaterialTheme.typography.titleMedium)
            Line("Balance on $accountTitle", money(r.balance))
            Line("Expected spending, ${r.daysToHorizon} days", "-${money(r.expected)}")
            Line("Scheduled payments (${r.scheduledCount})", "-${money(r.scheduled)}")
            Line("This purchase", "-${money(r.price)}")
            Line("Free after purchase", money(r.afterPurchase), strong = true)
            r.afterPurchaseAverage?.let {
                Text("With your 3-month average instead: ${money(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Payday is the next income reminder, else the end of the month (${r.horizon}).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
private fun LimitCard(limit: dev.yernur.moneytracker.data.LimitImpact, title: String) {
    AppCard(Modifier.fillMaxWidth().testTag("afford-limit")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Limit: $title", style = MaterialTheme.typography.titleMedium)
            Line("Spent so far", money(limit.spent))
            Line("Limit", money(limit.limit))
            Line("With purchase, on track for", money(limit.withPurchase))
            Text(
                if (limit.over > 0) "Over the limit by ${money(limit.over)}." else "Stays inside the limit.",
                style = MaterialTheme.typography.bodyMedium, color = if (limit.over > 0) Semantic.expense else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun GoalCard(title: String, days: Int) {
    AppCard(Modifier.fillMaxWidth().testTag("afford-goal")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Goal: $title", style = MaterialTheme.typography.titleMedium)
            Text("Reaching it takes about $days days longer at your recent saving pace.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
