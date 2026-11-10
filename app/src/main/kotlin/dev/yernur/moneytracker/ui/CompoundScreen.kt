package dev.yernur.moneytracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.wealth.Capitalization
import dev.yernur.moneytracker.data.wealth.CompoundInput
import dev.yernur.moneytracker.data.wealth.CompoundYear
import dev.yernur.moneytracker.data.wealth.ContributionTiming
import dev.yernur.moneytracker.data.wealth.compound
import dev.yernur.moneytracker.data.wealth.convertViaKzt
import dev.yernur.moneytracker.data.wealth.currencyChoices
import java.math.BigDecimal

/** What the form holds, as typed. Parsing lives apart from the composable so it can be tested. */
data class CompoundForm(
    val start: String, val monthly: String, val rate: String, val years: String,
    val capitalization: Capitalization, val timing: ContributionTiming,
)

private const val MAX_YEARS = 60

/** Null while a field is empty or not a number: the screen then shows no result instead of a wrong one. */
fun CompoundForm.toInput(): CompoundInput? {
    val startMinor = Money.parseMinor(start) ?: return null
    val monthlyMinor = Money.parseMinor(monthly) ?: return null
    val ratePercent = rate.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
    val yearCount = years.trim().toIntOrNull()?.takeIf { it in 1..MAX_YEARS } ?: return null
    return CompoundInput(startMinor, ratePercent, monthlyMinor, yearCount, capitalization, timing)
}

/** The two money fields re-expressed in another currency with the cached rates; unchanged when a rate is missing. */
fun CompoundForm.inCurrency(from: String, to: String, kztPerUnit: Map<String, Double>): CompoundForm {
    fun convert(text: String): String {
        val minor = Money.parseMinor(text) ?: return text
        return convertViaKzt(minor, from, to, kztPerUnit)?.let(Money::editable) ?: text
    }
    return copy(start = convert(start), monthly = convert(monthly))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompoundScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val rates by container.fx.rates.collectAsStateWithLifecycle(emptyMap())
    val currency by container.settings.wealthCurrency.collectAsStateWithLifecycle()
    val choices = remember(rates) { currencyChoices(rates) }

    var start by rememberSaveable { mutableStateOf("1000000") }
    var monthly by rememberSaveable { mutableStateOf("100000") }
    var rate by rememberSaveable { mutableStateOf("12") }
    var years by rememberSaveable { mutableStateOf("10") }
    var capitalization by rememberSaveable { mutableStateOf(Capitalization.Monthly) }
    var timing by rememberSaveable { mutableStateOf(ContributionTiming.End) }
    /** The currency the typed amounts are in. The examples start in tenge; whenever the chosen currency differs (the owner switched it, or it was already chosen on opening) they are converted. */
    var formCurrency by rememberSaveable { mutableStateOf(DEFAULT_CURRENCY) }
    LaunchedEffect(currency, rates) {
        val known = { code: String -> code == DEFAULT_CURRENCY || code in rates }
        if (formCurrency != currency && known(formCurrency) && known(currency)) {
            val converted = CompoundForm(start, monthly, rate, years, capitalization, timing).inCurrency(formCurrency, currency, rates)
            start = converted.start
            monthly = converted.monthly
            formCurrency = currency
        }
    }
    val form = CompoundForm(start, monthly, rate, years, capitalization, timing)
    val input = form.toInput()
    val rows = remember(input) { input?.let(::compound).orEmpty() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compound interest") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    CurrencySwitch(formCurrency, choices, container.settings::setWealthCurrency, Modifier.padding(end = 8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Start balance ($formCurrency)", start, { start = it }, Modifier.weight(1f), "compound-start")
                NumberField("Each month ($formCurrency)", monthly, { monthly = it }, Modifier.weight(1f), "compound-monthly")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Yearly rate (%)", rate, { rate = it }, Modifier.weight(1f), "compound-rate")
                NumberField("Years", years, { years = it }, Modifier.weight(1f), "compound-years", decimal = false)
            }
            Choice("Interest is added to the balance", listOf("Monthly", "Yearly"), capitalization.ordinal, "compound-capitalization") {
                capitalization = Capitalization.entries[it]
            }
            Choice("Contribution is paid", listOf("Start of month", "End of month"), timing.ordinal, "compound-timing") {
                timing = ContributionTiming.entries[it]
            }
            if (input != null && rows.isNotEmpty()) Result(rows, formCurrency) else
                Text("Enter the amounts, a rate and 1 to $MAX_YEARS years.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("compound-empty"))
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, tag: String, decimal: Boolean = true) {
    OutlinedTextField(
        value, onChange, modifier.testTag(tag), label = { Text(label, maxLines = 1) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Choice(label: String, options: List<String>, selected: Int, tag: String, onPick: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SlidingSegmented(options, selected, options.indices.map { "$tag-$it" }, onPick, Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Result(rows: List<CompoundYear>, currency: String) {
    val last = rows.last()
    AppCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Balance after ${rows.size} years", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                Money.formatRounded(last.balance, currency), fontSize = 32.sp, color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 2.dp).testTag("compound-balance"),
            )
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("You put in ${Money.formatRounded(last.contributed, currency)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.testTag("compound-contributed"))
                Text("Interest ${Money.formatRounded(last.interest, currency)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.testTag("compound-interest"))
            }
        }
    }
    AppCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Year by year", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Legend(MaterialTheme.colorScheme.outline, "Your money")
                    Legend(MaterialTheme.colorScheme.primary, "Interest")
                }
            }
            YearBars(rows, Modifier.fillMaxWidth().height(150.dp).padding(top = 10.dp))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Year 1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Year ${rows.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One bar per year: what was paid in at the bottom, the interest on top. Bars share one scale so growth is visible. */
@Composable
private fun YearBars(rows: List<CompoundYear>, modifier: Modifier) {
    val own = MaterialTheme.colorScheme.outline
    val interest = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val top = rows.maxOf { maxOf(it.balance, 1L) }.toFloat()
        val gap = 6.dp.toPx()
        val width = (size.width - gap * (rows.size - 1)) / rows.size
        rows.forEachIndexed { i, row ->
            val x = i * (width + gap)
            val total = (row.balance.coerceAtLeast(0) / top) * size.height
            val paid = (row.contributed.coerceIn(0, row.balance.coerceAtLeast(0)) / top) * size.height
            val round = CornerRadius(3.dp.toPx())
            drawRoundRect(interest, Offset(x, size.height - total), Size(width, total), round)
            drawRect(own, Offset(x, size.height - paid), Size(width, paid))
        }
    }
}
