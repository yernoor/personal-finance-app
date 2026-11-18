package dev.yernur.moneytracker.ui

import androidx.compose.foundation.Canvas
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.yernur.moneytracker.data.wealth.NetWorthYear
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
import androidx.compose.material3.LocalTextStyle
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
import java.math.BigDecimal
import java.time.LocalDate

private sealed interface Editing {
    data class Asset(val item: ManualAssetEntity?) : Editing
    data class Goal(val item: GoalEntity?) : Editing
    data class Bond(val item: BondEntity?) : Editing
    data class Cashflow(val item: CashflowItemEntity?) : Editing
}

private const val NET_WORTH_CHOICE = "Net worth"

/**
 * The currency switch for the Wealth heading: the app's top bar shows it next to the title when Wealth is a tab, and the screen's own
 * bar when it is opened from More. Both use the same setting, so they stay in step. Without a rate for the chosen currency it shows tenge.
 */
@Composable
fun WealthCurrencyAction(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val rates by container.fx.rates.collectAsStateWithLifecycle(emptyMap())
    val currency by container.settings.wealthCurrency.collectAsStateWithLifecycle()
    val choices = remember(rates) { currencyChoices(rates) }
    CurrencySwitch(if (currency in choices) currency else "KZT", choices, { container.settings.setWealthCurrency(it) }, modifier)
}

/** What a press on the Wealth overview opens (owner's "Wealth screen redesign" canvas, 2026-11-19). */
enum class WealthPart(val title: String) { Accounts("Accounts"), Bonds("Bonds"), Assets("Other assets"), Loans("Loans"), Goals("Goals"), Monthly("Monthly money"), Projection("Projection") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WealthScreen(
    embedded: Boolean = false, onCompound: () -> Unit, onBack: () -> Unit,
    /** The calculator with the projection's numbers filled in: start, monthly, rate (percent) and years, in tenge. */
    onCompoundWith: (start: String, monthly: String, rate: String, years: Int) -> Unit = { _, _, _, _ -> onCompound() },
) {
    val container = LocalAppContainer.current
    val vm = appViewModel { WealthViewModel(it) }
    val overview by vm.overview.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val currency by container.settings.wealthCurrency.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Editing?>(null) }
    var part by rememberSaveable { mutableStateOf<WealthPart?>(null) }
    BackHandler(part != null) { part = null }

    LaunchedEffect(Unit) {
        container.refreshRatesDaily()
        container.snapshotNetWorth()
    }
    Scaffold(
        contentWindowInsets = embeddedInsets(embedded),
        topBar = {
            if (!embedded) AppTopBar(
                title = { Text(part?.title ?: "Wealth") },
                navigationIcon = { IconButton({ if (part != null) part = null else onBack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = { WealthCurrencyAction(Modifier.padding(end = 8.dp)) },
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

        AnimatedContent(
            part, Modifier.padding(padding),
            transitionSpec = {
                val forward = targetState != null
                (slideInHorizontally(tween(260)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(200)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(160)))
            },
            label = "wealth-part",
        ) { shownPart ->
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = LocalChromeInset.current + 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // As a tab there is no app bar of its own: the part's name and the way back sit at the top of the content.
                if (embedded && shownPart != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ part = null }, Modifier.testTag("wealth-back")) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                    Text(shownPart.title, style = MaterialTheme.typography.titleLarge)
                }
                when (shownPart) {
                    null -> {
                        NetWorthCard(data, ::show) { part = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GoalsTile(data, Modifier.weight(1f)) { part = WealthPart.Goals }
                            MonthlyTile(data, ::show, Modifier.weight(1f)) { part = WealthPart.Monthly }
                        }
                        ProjectionTile(data, ::show) { part = WealthPart.Projection }
                        OutlinedButton(onCompound, Modifier.fillMaxWidth().height(48.dp).testTag("open-compound-from-wealth")) {
                            Icon(Icons.Outlined.Calculate, null, Modifier.size(20.dp))
                            Text("Compound interest calculator", Modifier.padding(start = 8.dp))
                        }
                    }
                    WealthPart.Accounts, WealthPart.Bonds, WealthPart.Assets, WealthPart.Loans -> {
                        PartHeader(data, shownPart, ::show)
                        when (shownPart) {
                            WealthPart.Accounts -> AccountsList(::show)
                            WealthPart.Bonds -> PortfolioCard(data, ::show, onEdit = { editing = Editing.Bond(it) })
                            else -> ManualList(data, shownPart == WealthPart.Loans, onAsset = { editing = Editing.Asset(it) })
                        }
                    }
                    WealthPart.Goals -> GoalsCard(data, ::show, onEdit = { editing = Editing.Goal(it) })
                    WealthPart.Monthly -> {
                        val suggestions by vm.suggestions.collectAsStateWithLifecycle()
                        HealthCard(data, ::show, onEdit = { editing = Editing.Cashflow(it) }, suggestions = suggestions, onAddBill = { vm.saveCashflow(null, it.asCashflow()) })
                    }
                    WealthPart.Projection -> ProjectionDetail(data, ::show, onPart = { part = it }, onCompoundWith = onCompoundWith)
                }
            }
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

/** The parts of net worth that the bar shows, with their amounts (loans are not on the bar: they are subtracted, listed in red). */
private fun barParts(data: WealthOverview) = listOf(
    WealthPart.Accounts to data.netWorth.accountsMinor, WealthPart.Bonds to data.netWorth.investmentsMinor, WealthPart.Assets to data.netWorth.assetsMinor,
)

@Composable
private fun partColor(part: WealthPart): Color = when (part) {
    WealthPart.Accounts -> MaterialTheme.colorScheme.primary
    WealthPart.Bonds -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}

/** What net worth is made of as one horizontal bar; with [lit], only that part keeps its colour (a part's own page). */
@Composable
private fun NetWorthBar(data: WealthOverview, lit: WealthPart? = null) {
    val parts = barParts(data).filter { it.second > 0 }
    if (parts.isEmpty()) return
    Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).testTag("net-worth-bar"), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        parts.forEach { (part, amount) ->
            val color = partColor(part)
            Box(Modifier.weight(amount.toFloat()).fillMaxHeight().background(if (lit == null || lit == part) color else color.copy(alpha = 0.25f)))
        }
    }
}

@Composable
private fun NetWorthCard(data: WealthOverview, show: (Long) -> String, onPart: (WealthPart) -> Unit) {
    val today = LocalDate.now()
    val total = data.netWorth.totalMinor
    val change = netWorthChange(data.snapshots.map { LocalDate.parse(it.date) to it.totalMinor }, today, total)
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("Net worth", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RollingMoney(total, show, Modifier.testTag("net-worth"), LocalTextStyle.current.copy(fontSize = 32.sp, fontWeight = FontWeight.SemiBold))
                Text(
                    change?.let { (diff, since) -> "${if (diff >= 0) "▲ +" else "▼ "}${show(diff)} since $since" } ?: "History starts today: one point is saved each day you open the app",
                    style = MaterialTheme.typography.bodyMedium,
                    color = change?.let { if (it.first >= 0) Semantic.income else Semantic.expense } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (data.netWorth.missingCurrencies.isNotEmpty()) Text(
                    "No rate yet for ${data.netWorth.missingCurrencies.joinToString()}: left out of the totals",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp).testTag("missing-rates"),
                )
            }
            // Net worth day by day so far (one point per day the app was opened), kept from the earlier card.
            sparklinePoints(data.snapshots.map { LocalDate.parse(it.date) to it.totalMinor })?.let {
                Sparkline(it, MaterialTheme.colorScheme.outline, Modifier.fillMaxWidth().height(36.dp))
            }
            NetWorthBar(data)
            Column {
                barParts(data).forEach { (part, amount) -> PartRow(part.title, show(amount), partColor(part), Modifier.testTag("wealth-part-${part.name}")) { onPart(part) } }
                val loans = data.netWorth.liabilitiesMinor
                PartRow("Loans", if (loans == 0L) show(0) else "-" + show(loans), Semantic.expense, Modifier.testTag("wealth-part-Loans"), amountColor = if (loans > 0) Semantic.expense else null) { onPart(WealthPart.Loans) }
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
private fun PartRow(label: String, amount: String, dot: Color, modifier: Modifier, amountColor: Color? = null, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(amount, style = MaterialTheme.typography.bodyLarge, color = amountColor ?: MaterialTheme.colorScheme.onSurface)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A part's page starts with the bar (its slice lit) and its total. */
@Composable
private fun PartHeader(data: WealthOverview, part: WealthPart, show: (Long) -> String) {
    val n = data.netWorth
    val amount = when (part) { WealthPart.Accounts -> n.accountsMinor; WealthPart.Bonds -> n.investmentsMinor; WealthPart.Assets -> n.assetsMinor; else -> n.liabilitiesMinor }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text((if (part == WealthPart.Loans && amount > 0) "-" else "") + show(amount), fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                val gross = barParts(data).sumOf { it.second }
                if (part != WealthPart.Loans && gross > 0) Text("${amount * 100 / gross}% of what you own", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            NetWorthBar(data, lit = part)
        }
    }
}

/** Accounts as Wealth counts them: every active account, hidden ones too. Edited on the Accounts screen. */
@Composable
private fun AccountsList(show: (Long) -> String) {
    val container = LocalAppContainer.current
    val accounts by remember { container.db.accountDao().observeLive() }.collectAsStateWithLifecycle(emptyList())
    val balances by remember { container.db.accountDao().observeBalances() }.collectAsStateWithLifecycle(emptyList())
    val byUid = balances.associate { it.uid to it.balance }
    SectionCard("Accounts", null, "") {
        accounts.filter { it.isActive && !it.isArchived }.forEach { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(a.icon, a.color, size = 36.dp)
                Text(a.title.ifBlank { "Main" }, Modifier.weight(1f))
                Text(Money.format(byUid[a.uid] ?: 0L, a.currencyCode))
            }
        }
        Text("Accounts are edited on the Accounts screen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Things owned outside the accounts (flat, car) or loans, as typed in by hand. */
@Composable
private fun ManualList(data: WealthOverview, loans: Boolean, onAsset: (ManualAssetEntity?) -> Unit) {
    val items = data.manualAssets.filter { (it.kind == ManualKind.Liability) == loans }
    SectionCard(if (loans) "Loans and debts" else "Other assets", { onAsset(null) }, "add-asset") {
        if (items.isEmpty()) Text(if (loans) "No loans. Add a mortgage, car loan or debt to subtract it." else "Add a flat, a car or anything else you own.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.forEach { item ->
            Row(Modifier.fillMaxWidth().clickable { onAsset(item) }.padding(vertical = 6.dp).testTag("asset-${item.title}"), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.title)
                Text(
                    (if (loans) "-" else "") + Money.format(item.valueMinor, item.currencyCode),
                    color = if (loans) Semantic.expense else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, modifier: Modifier, tag: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    AppCard(modifier.testTag(tag)) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun GoalsTile(data: WealthOverview, modifier: Modifier, onClick: () -> Unit) {
    val next = data.goals.firstOrNull { !it.second.reached } ?: data.goals.firstOrNull()
    SummaryTile("Goals", modifier, "wealth-part-Goals", onClick) {
        if (next == null) Text("Set a goal", style = MaterialTheme.typography.titleMedium)
        else {
            Row(verticalAlignment = Alignment.CenterVertically) { // a long goal name shortens; the percent always shows
                Text(next.first.title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(" · ${(next.second.fraction * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
            LinearProgressIndicator({ next.second.fraction.toFloat() }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MonthlyTile(data: WealthOverview, show: (Long) -> String, modifier: Modifier, onClick: () -> Unit) {
    val h = data.health
    SummaryTile("Monthly money", modifier, "wealth-part-Monthly", onClick) {
        if (data.cashflowItems.isEmpty()) Text("Add income", style = MaterialTheme.typography.titleMedium)
        else {
            Text("${show(h.availableMinor)} free", style = MaterialTheme.typography.titleMedium, maxLines = 1)
            h.savingsRatePercent?.let { Text("Savings rate ${"%.0f".format(it)}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun AmountTile(label: String, value: Long, format: (Long) -> String, modifier: Modifier) {
    Column(modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RollingMoney(value, format, style = MaterialTheme.typography.titleMedium, maxLines = 1)
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
private fun HealthCard(
    data: WealthOverview, show: (Long) -> String, onEdit: (CashflowItemEntity?) -> Unit,
    suggestions: List<dev.yernur.moneytracker.data.Bill> = emptyList(), onAddBill: (dev.yernur.moneytracker.data.Bill) -> Unit = {},
) {
    val h = data.health
    SectionCard("Monthly money", { onEdit(null) }, "add-cashflow") {
        if (data.cashflowItems.isEmpty()) Text("Add your income and regular payments (mortgage, loans, subscriptions).", color = MaterialTheme.colorScheme.onSurfaceVariant)
        data.cashflowItems.forEach { item ->
            Row(Modifier.fillMaxWidth().clickable { onEdit(item) }.padding(vertical = 4.dp).testTag("cashflow-${item.title}"), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.title)
                Text((if (item.kind == CashflowKind.Obligation) "-" else "+") + Money.format(item.monthlyMinor, item.currencyCode))
            }
        }
        if (suggestions.isNotEmpty()) {
            Text("From your recurring bills", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            suggestions.forEach { bill ->
                Row(Modifier.fillMaxWidth().testTag("suggest-${bill.item.title}"), verticalAlignment = Alignment.CenterVertically) {
                    Text(bill.item.title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("-" + Money.format(bill.item.amount, dev.yernur.moneytracker.data.DEFAULT_CURRENCY), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.material3.TextButton({ onAddBill(bill) }, Modifier.testTag("suggest-add-${bill.item.title}")) { Text("Add") }
                }
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

/** The bonds' own coupon yield (coupons reinvested); accounts earn nothing in the projection, which is the cautious choice. */
private fun bondYield(data: WealthOverview): BigDecimal {
    val p = data.portfolio
    return if (p.valueMinor > 0) BigDecimal(p.annualCouponMinor * 100.0 / p.valueMinor).setScale(2, java.math.RoundingMode.HALF_UP) else BigDecimal.ZERO
}

private fun projection(data: WealthOverview, years: Int, invest: Int) = data.netWorth.let { n ->
    projectNetWorth(ProjectionInputs(n.accountsMinor, n.investmentsMinor, n.assetsMinor - n.liabilitiesMinor, data.health.availableMinor.coerceAtLeast(0), invest, BigDecimal.ZERO, bondYield(data), years))
}

private const val TILE_YEARS = 5
private const val TILE_INVEST = 50

/** The overview's card: where net worth heads in five years at this pace (half of each month's saving invested), as a small line. */
@Composable
private fun ProjectionTile(data: WealthOverview, show: (Long) -> String, onClick: () -> Unit) {
    val rows = remember(data) { projection(data, TILE_YEARS, TILE_INVEST) }
    val end = rows.last().totalMinor
    AppCard(Modifier.fillMaxWidth().testTag("wealth-part-Projection")) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Projection at this pace", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(show(end), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("in $TILE_YEARS years · ${if (end >= data.netWorth.totalMinor) "+" else ""}${show(end - data.netWorth.totalMinor)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val lo = rows.minOf { it.totalMinor }
            val span = (rows.maxOf { it.totalMinor } - lo).coerceAtLeast(1)
            Sparkline(rows.mapIndexed { i, r -> i.toFloat() / (rows.size - 1).coerceAtLeast(1) to 0.1f + 0.8f * (r.totalMinor - lo) / span }, MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth().height(56.dp))
        }
    }
}

/** The projection's own page: horizon, how much of each month's saving goes into bonds, the result split by part per year, and its inputs. */
@Composable
private fun ProjectionDetail(data: WealthOverview, show: (Long) -> String, onPart: (WealthPart) -> Unit, onCompoundWith: (String, String, String, Int) -> Unit) {
    var years by rememberSaveable { mutableStateOf(TILE_YEARS) }
    var invest by rememberSaveable { mutableStateOf(TILE_INVEST) }
    val rows = remember(data, years, invest) { projection(data, years, invest) }
    val now = data.netWorth.totalMinor
    val end = rows.last().totalMinor
    val monthly = data.health.availableMinor.coerceAtLeast(0)
    val saved = monthly * 12 * years
    val yieldPercent = bondYield(data)
    val horizons = listOf(5, 10, 20)
    SlidingSegmented(horizons.map { "$it years" }, horizons.indexOf(years), horizons.map { "years-$it" }, { years = horizons[it] })
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Net worth in ${LocalDate.now().year + years}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(show(end), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("projected"))
            Text(
                "${if (end >= now) "+" else ""}${show(end - now)} · ${show(saved)} saved, ${show((end - now - saved).coerceAtLeast(0))} from coupons",
                style = MaterialTheme.typography.bodySmall, color = Semantic.income,
            )
            ProjectionChart(rows, Modifier.fillMaxWidth().height(160.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(WealthPart.Accounts, WealthPart.Bonds, WealthPart.Assets).forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(partColor(p)))
                        Text(p.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Of what you save each month, invest", style = MaterialTheme.typography.titleSmall)
            val shares = listOf(0, 50, 100)
            SlidingSegmented(listOf("None", "Half", "All"), shares.indexOf(invest), shares.map { "invest-$it" }, { invest = shares[it] })
        }
    }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Based on", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.titleSmall)
            BasedOnRow("Saved each month", show(monthly)) { onPart(WealthPart.Monthly) }
            BasedOnRow("Bonds pay (coupon yield)", "${yieldPercent.toPlainString()}% a year") { onPart(WealthPart.Bonds) }
            BasedOnRow("Accounts earn", "nothing (cautious)", null)
        }
    }
    OutlinedButton(
        { onCompoundWith(Money.editable(Math.round(now / 100.0) * 100), Money.editable(Math.round(monthly / 100.0) * 100), yieldPercent.stripTrailingZeros().toPlainString(), years) },
        Modifier.fillMaxWidth().height(48.dp).testTag("projection-calculator"),
    ) {
        Icon(Icons.Outlined.Calculate, null, Modifier.size(20.dp))
        Text("Try other numbers in the calculator", Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun BasedOnRow(label: String, value: String, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = if (onClick == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        if (onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Stacked areas per year: other assets at the bottom, then bonds, then accounts (loans already subtracted from "other"). */
@Composable
private fun ProjectionChart(rows: List<NetWorthYear>, modifier: Modifier) {
    val colors = listOf(partColor(WealthPart.Assets), partColor(WealthPart.Bonds), partColor(WealthPart.Accounts))
    val description = "Net worth by year, from ${rows.first().year} to ${rows.last().year}"
    Canvas(modifier.semantics { contentDescription = description }) {
        val top = rows.maxOf { maxOf(it.totalMinor, 1L) }.toFloat()
        fun x(i: Int) = size.width * i / (rows.size - 1).coerceAtLeast(1)
        fun y(v: Long) = size.height * (1f - (v.coerceAtLeast(0).toFloat() / top))
        val layers = listOf<(NetWorthYear) -> Long>({ it.otherMinor.coerceAtLeast(0) }, { it.otherMinor.coerceAtLeast(0) + it.investmentsMinor }, { it.otherMinor.coerceAtLeast(0) + it.investmentsMinor + it.accountsMinor })
        layers.indices.reversed().forEach { layer ->
            val path = Path().apply {
                moveTo(0f, size.height)
                rows.forEachIndexed { i, r -> lineTo(x(i), y(layers[layer](r))) }
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, colors[layer])
        }
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
