package dev.yernur.moneytracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.db.BondEntity
import dev.yernur.moneytracker.data.db.CashflowItemEntity
import dev.yernur.moneytracker.data.db.CashflowKind
import dev.yernur.moneytracker.data.db.GoalEntity
import dev.yernur.moneytracker.data.db.ManualAssetEntity
import dev.yernur.moneytracker.data.db.ManualKind
import dev.yernur.moneytracker.data.wealth.GoalProgress
import dev.yernur.moneytracker.data.wealth.ProjectionInputs
import dev.yernur.moneytracker.data.wealth.WealthOverview
import dev.yernur.moneytracker.data.wealth.currencyChoices
import dev.yernur.moneytracker.data.wealth.fromDefault
import dev.yernur.moneytracker.data.wealth.netWorthChange
import dev.yernur.moneytracker.data.wealth.projectNetWorth
import dev.yernur.moneytracker.data.wealth.sparklinePoints
import dev.yernur.moneytracker.data.wealth.updatedLabel
import java.math.BigDecimal
import java.time.LocalDate

private sealed interface Editing {
    data class Asset(val item: ManualAssetEntity?) : Editing
    data class Goal(val item: GoalEntity?) : Editing
    data class Bond(val item: BondEntity?) : Editing
    data class Cashflow(val item: CashflowItemEntity?) : Editing
}

private const val NET_WORTH_CHOICE = "Net worth"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WealthScreen(embedded: Boolean = false, onCompound: () -> Unit, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = appViewModel { WealthViewModel(it) }
    val overview by vm.overview.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val currency by container.settings.wealthCurrency.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Editing?>(null) }

    LaunchedEffect(Unit) {
        container.refreshRatesDaily()
        container.snapshotNetWorth()
    }
    Scaffold(
        topBar = {
            if (!embedded) TopAppBar(
                title = { Text("Wealth") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        val data = overview
        if (data == null) {
            Text("Loading…", Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        val choices = remember(data.rates) { currencyChoices(data.rates) }
        // Without a rate for the chosen currency everything stays in tenge, and says so.
        val shown = if (currency in choices) currency else "KZT"
        fun show(minor: Long) = Money.formatRounded(fromDefault(minor, shown, data.rates), shown)

        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = LocalChromeInset.current + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val updatedAt by container.settings.ratesUpdatedAt.collectAsStateWithLifecycle()
                Text(
                    if (data.rates.isEmpty()) "No exchange rates yet"
                    else "National Bank rates" + (updatedAt?.let { ", updated ${updatedLabel(it)}" } ?: ""),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CurrencySwitch(shown, choices, { container.settings.setWealthCurrency(it) })
            }
            NetWorthCard(data, ::show)
            Breakdown(data, ::show, onAsset = { editing = Editing.Asset(it) })
            GoalsCard(data, ::show, onEdit = { editing = Editing.Goal(it) })
            PortfolioCard(data, ::show, onEdit = { editing = Editing.Bond(it) })
            HealthCard(data, ::show, onEdit = { editing = Editing.Cashflow(it) })
            ProjectionCard(data, ::show)
            OutlinedButton(onCompound, Modifier.fillMaxWidth().testTag("open-compound-from-wealth")) { Text("Compound interest calculator") }
        }

        when (val e = editing) {
            null -> Unit
            is Editing.Asset -> AssetDialog(e.item, shown, choices, { editing = null }, vm)
            is Editing.Goal -> GoalDialog(e.item, shown, accounts.map { it.title }, data.rates, { editing = null }, vm)
            is Editing.Bond -> BondDialog(e.item, shown, choices, { editing = null }, vm)
            is Editing.Cashflow -> CashflowDialog(e.item, shown, choices, { editing = null }, vm)
        }
    }
}

@Composable
private fun SectionCard(title: String, onAdd: (() -> Unit)?, addTag: String, content: @Composable () -> Unit) {
    AppCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (onAdd != null) TextButton(onAdd, modifier = Modifier.testTag(addTag)) { Icon(Icons.Outlined.Add, null); Text("Add") }
            }
            content()
        }
    }
}

@Composable
private fun NetWorthCard(data: WealthOverview, show: (Long) -> String) {
    val today = LocalDate.now()
    val total = data.netWorth.totalMinor
    val change = netWorthChange(data.snapshots.map { LocalDate.parse(it.date) to it.totalMinor }, today, total)
    AppCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Net worth", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimary)
            Text(show(total), fontSize = 34.sp, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.testTag("net-worth"))
            Text(
                change?.let { (diff, since) -> "${if (diff >= 0) "+" else ""}${show(diff)} since $since" } ?: "History starts today: one point is saved each day you open the app",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary,
            )
            if (data.netWorth.missingCurrencies.isNotEmpty()) {
                Text(
                    "No rate yet for ${data.netWorth.missingCurrencies.joinToString()}: left out of the totals",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(top = 4.dp).testTag("missing-rates"),
                )
            }
            sparklinePoints(data.snapshots.map { LocalDate.parse(it.date) to it.totalMinor })?.let {
                Sparkline(it, MaterialTheme.colorScheme.onPrimary, Modifier.fillMaxWidth().height(56.dp).padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun Sparkline(points: List<Pair<Float, Float>>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val path = Path()
        points.forEachIndexed { i, (x, y) ->
            val point = Offset(size.width * x, size.height * (1 - y))
            if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun Breakdown(data: WealthOverview, show: (Long) -> String, onAsset: (ManualAssetEntity?) -> Unit) {
    val n = data.netWorth
    SectionCard("Where it is", { onAsset(null) }, "add-asset") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Accounts", show(n.accountsMinor), Modifier.weight(1f))
            Tile("Bonds", show(n.investmentsMinor), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Other assets", show(n.assetsMinor), Modifier.weight(1f))
            Tile("Loans", if (n.liabilitiesMinor == 0L) show(0) else "-" + show(n.liabilitiesMinor), Modifier.weight(1f))
        }
        data.manualAssets.forEach { item ->
            Row(Modifier.fillMaxWidth().clickable { onAsset(item) }.padding(vertical = 6.dp).testTag("asset-${item.title}"), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.title)
                Text(
                    (if (item.kind == ManualKind.Liability) "-" else "") + Money.format(item.valueMinor, item.currencyCode),
                    color = if (item.kind == ManualKind.Liability) Semantic.expense else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
private fun GoalsCard(data: WealthOverview, show: (Long) -> String, onEdit: (GoalEntity?) -> Unit) {
    SectionCard("Goals", { onEdit(null) }, "add-goal") {
        if (data.goals.isEmpty()) Text("Set a target, for example a down payment or a safety cushion.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        data.goals.forEach { (goal, progress) ->
            Column(Modifier.fillMaxWidth().clickable { onEdit(goal) }.padding(vertical = 4.dp).testTag("goal-${goal.title}")) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(goal.title)
                    Text("${(progress.fraction * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinearProgressIndicator({ progress.fraction.toFloat() }, Modifier.fillMaxWidth().padding(top = 4.dp))
                Text(goalLine(progress, show), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun goalLine(p: GoalProgress, show: (Long) -> String): String = when {
    p.reached -> "Reached: ${show(p.current)} of ${show(p.target)}"
    p.requiredMonthly != null -> "${show(p.requiredMonthly)} a month to reach ${show(p.target)} in ${p.monthsLeft} months"
    p.monthsLeft == 0 -> "Deadline passed: ${show(p.current)} of ${show(p.target)}"
    else -> "${show(p.current)} of ${show(p.target)}"
}

@Composable
private fun PortfolioCard(data: WealthOverview, show: (Long) -> String, onEdit: (BondEntity?) -> Unit) {
    val p = data.portfolio
    SectionCard("Bond portfolio", { onEdit(null) }, "add-bond") {
        if (p.bonds.isEmpty()) Text("Add the bonds you hold to see coupons, yield and maturities.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("Value", show(p.valueMinor), Modifier.weight(1f))
                Tile("Coupons a year", show(p.annualCouponMinor), Modifier.weight(1f))
            }
            if (p.missingCurrencies.isNotEmpty()) Text("No rate yet for ${p.missingCurrencies.joinToString()}: left out of the totals", style = MaterialTheme.typography.bodySmall)
        }
        p.bonds.forEach { (bond, summary) ->
            Column(Modifier.fillMaxWidth().clickable { onEdit(bond) }.padding(vertical = 4.dp).testTag("bond-${bond.title}")) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(bond.title)
                    Text(Money.format(bond.currentValueMinor ?: bond.costMinor, bond.currencyCode))
                }
                Text(
                    "Matures ${bond.maturityDate} · coupons ${Money.format(summary.annualCouponMinor, bond.currencyCode)} a year" +
                        (summary.yieldToMaturityPercent?.let { " · yield ${"%.1f".format(it)}%" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        p.ladder.forEach { (year, step) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$year", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "coupons ${show(step.couponsMinor)}" + if (step.principalMinor > 0) " + redemption ${show(step.principalMinor)}" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HealthCard(data: WealthOverview, show: (Long) -> String, onEdit: (CashflowItemEntity?) -> Unit) {
    val h = data.health
    SectionCard("Monthly money", { onEdit(null) }, "add-cashflow") {
        if (data.cashflowItems.isEmpty()) Text("Add your income and regular payments (mortgage, loans, subscriptions).", color = MaterialTheme.colorScheme.onSurfaceVariant)
        data.cashflowItems.forEach { item ->
            Row(Modifier.fillMaxWidth().clickable { onEdit(item) }.padding(vertical = 4.dp).testTag("cashflow-${item.title}"), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.title)
                Text((if (item.kind == CashflowKind.Obligation) "-" else "+") + Money.format(item.monthlyMinor, item.currencyCode))
            }
        }
        if (h.missingCurrencies.isNotEmpty()) {
            Text(
                "No rate yet for ${h.missingCurrencies.joinToString()}: those items are left out of the numbers below",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("health-missing-rates"),
            )
        }
        if (data.cashflowItems.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("Free each month", show(h.availableMinor), Modifier.weight(1f))
                Tile("Savings rate", h.savingsRatePercent?.let { "%.0f%%".format(it) } ?: "-", Modifier.weight(1f))
            }
            Text(
                listOfNotNull(
                    h.averageSpendingMinor?.let { "Spending averages ${show(it)} a month (transfers are not counted; categories set to Never in their settings are left out)" },
                    h.obligationsSharePercent?.let { "Payments take ${"%.0f".format(it)}% of income" },
                    h.emergencyMonths?.let { "Savings cover ${"%.1f".format(it)} months" },
                ).joinToString(". "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("health-notes"),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectionCard(data: WealthOverview, show: (Long) -> String) {
    var years by remember { mutableStateOf(10) }
    var invest by remember { mutableStateOf(50) }
    val n = data.netWorth
    val p = data.portfolio
    // Bonds grow by their own coupon yield (coupons reinvested); accounts earn nothing here, which is the cautious choice.
    val bondYield = if (p.valueMinor > 0) BigDecimal(p.annualCouponMinor * 100.0 / p.valueMinor).setScale(2, java.math.RoundingMode.HALF_UP) else BigDecimal.ZERO
    val rows = remember(data, years, invest) {
        projectNetWorth(
            ProjectionInputs(
                n.accountsMinor, n.investmentsMinor, n.assetsMinor - n.liabilitiesMinor, data.health.availableMinor.coerceAtLeast(0),
                invest, BigDecimal.ZERO, bondYield, years,
            ),
        )
    }
    SectionCard("Where this heads", null, "") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 20).forEach { y -> FilterChip(years == y, { years = y }, { Text("$y years") }, Modifier.testTag("years-$y")) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 50, 100).forEach { s -> FilterChip(invest == s, { invest = s }, { Text("$s% invested") }, Modifier.testTag("invest-$s")) }
        }
        Text(show(rows.last().totalMinor), fontSize = 26.sp, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("projected"))
        Text(
            "Net worth in $years years if you keep saving ${show(data.health.availableMinor.coerceAtLeast(0))} a month and the bonds keep paying" +
                " ${bondYield.toPlainString()}% (their coupon yield). Accounts earn nothing in this estimate.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AssetDialog(item: ManualAssetEntity?, currency: String, choices: List<String>, onDismiss: () -> Unit, vm: WealthViewModel) {
    FormDialog(
        if (item == null) "New asset or loan" else "Edit ${item.title}",
        listOf(
            TextFormField("Name (flat, car, mortgage)", item?.title.orEmpty()),
            ChoiceFormField("It is", listOf("Asset", "Loan or debt"), if (item?.kind == ManualKind.Liability) 1 else 0),
            ChoiceFormField("Currency", choices, choices.indexOf(item?.currencyCode ?: currency).coerceAtLeast(0)),
            TextFormField("Value", item?.let { Money.editable(it.valueMinor) }.orEmpty(), numeric = true),
        ),
        valid = { parseAsset(it) != null }, onDismiss = onDismiss,
        onDelete = item?.let { { vm.deleteAsset(it.uid); onDismiss() } },
    ) { values -> parseAsset(values)?.let { vm.saveAsset(item?.uid, it) }; onDismiss() }
}

@Composable
private fun GoalDialog(item: GoalEntity?, currency: String, accountTitles: List<String>, rates: Map<String, Double>, onDismiss: () -> Unit, vm: WealthViewModel) {
    val targets = listOf(NET_WORTH_CHOICE) + accountTitles
    FormDialog(
        if (item == null) "New goal" else "Edit ${item.title}",
        listOf(
            TextFormField("Goal", item?.title.orEmpty()),
            TextFormField("Target ($currency)", item?.let { Money.editable(fromDefault(it.targetMinor, currency, rates)) }.orEmpty(), numeric = true),
            TextFormField("Deadline, YYYY-MM-DD (optional)", item?.targetDate.orEmpty()),
            ChoiceFormField("Measured on", targets, item?.accountUid?.let { uid -> vm.accounts.value.indexOfFirst { it.uid == uid } + 1 }?.coerceAtLeast(0) ?: 0),
        ),
        valid = { parseGoal(it, NET_WORTH_CHOICE) != null }, onDismiss = onDismiss,
        onDelete = item?.let { { vm.deleteGoal(it.uid); onDismiss() } },
    ) { values -> parseGoal(values, NET_WORTH_CHOICE)?.let { vm.saveGoal(item?.uid, it, currency) }; onDismiss() }
}

@Composable
private fun BondDialog(item: BondEntity?, currency: String, choices: List<String>, onDismiss: () -> Unit, vm: WealthViewModel) {
    val frequencies = listOf("1", "2", "4", "12")
    FormDialog(
        if (item == null) "New bond" else "Edit ${item.title}",
        listOf(
            TextFormField("Name", item?.title.orEmpty()),
            ChoiceFormField("Currency", choices, choices.indexOf(item?.currencyCode ?: currency).coerceAtLeast(0)),
            TextFormField("Face value of one bond", item?.let { Money.editable(it.faceMinor) }.orEmpty(), numeric = true),
            TextFormField("Number of bonds", item?.quantity?.toString() ?: "1", numeric = true),
            TextFormField("Total paid", item?.let { Money.editable(it.costMinor) }.orEmpty(), numeric = true),
            TextFormField("Coupon, % a year", item?.let { BigDecimal(it.couponRateBp).movePointLeft(2).stripTrailingZeros().toPlainString() }.orEmpty(), numeric = true),
            ChoiceFormField("Coupons a year", frequencies, frequencies.indexOf(item?.couponsPerYear?.toString() ?: "2").coerceAtLeast(0)),
            TextFormField("Maturity, YYYY-MM-DD", item?.maturityDate.orEmpty()),
            TextFormField("Value now (optional)", item?.currentValueMinor?.let(Money::editable).orEmpty(), numeric = true),
        ),
        valid = { parseBond(it) != null }, onDismiss = onDismiss,
        onDelete = item?.let { { vm.deleteBond(it.uid); onDismiss() } },
    ) { values -> parseBond(values)?.let { vm.saveBond(item?.uid, it) }; onDismiss() }
}

@Composable
private fun CashflowDialog(item: CashflowItemEntity?, currency: String, choices: List<String>, onDismiss: () -> Unit, vm: WealthViewModel) {
    FormDialog(
        if (item == null) "New income or payment" else "Edit ${item.title}",
        listOf(
            TextFormField("Name (salary, mortgage)", item?.title.orEmpty()),
            ChoiceFormField("It is", listOf("Income", "Payment"), if (item?.kind == CashflowKind.Obligation) 1 else 0),
            TextFormField("Amount each month", item?.let { Money.editable(it.monthlyMinor) }.orEmpty(), numeric = true),
            ChoiceFormField("Currency", choices, choices.indexOf(item?.currencyCode ?: currency).coerceAtLeast(0)),
            ChoiceFormField("Already entered as expenses in this app", listOf("No", "Yes"), if (item?.inLedger == true) 1 else 0),
        ),
        valid = { parseCashflow(it) != null }, onDismiss = onDismiss,
        onDelete = item?.let { { vm.deleteCashflow(it.uid); onDismiss() } },
    ) { values -> parseCashflow(values)?.let { vm.saveCashflow(item?.uid, it) }; onDismiss() }
}
