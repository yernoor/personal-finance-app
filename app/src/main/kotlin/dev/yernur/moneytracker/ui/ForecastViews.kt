package dev.yernur.moneytracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.yernur.moneytracker.data.CategoryForecast
import dev.yernur.moneytracker.data.DEFAULT_CURRENCY
import dev.yernur.moneytracker.data.Forecast
import dev.yernur.moneytracker.data.Money
import dev.yernur.moneytracker.data.WarningKind
import dev.yernur.moneytracker.data.db.CategoryEntity

private fun money(minor: Long) = Money.format(minor, DEFAULT_CURRENCY)

fun WarningKind.text(): String = when (this) {
    WarningKind.Exceeded -> "Over the limit"
    WarningKind.RunRate -> "On track to pass the limit"
    WarningKind.Average -> "3-month average passes the limit"
}

/** Projections for the current month: an overall card, then every category with its limit and warnings. */
@Composable
fun ForecastView(forecast: Forecast, categories: Map<String?, CategoryEntity>, onOpenCategory: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + LocalChromeInset.current), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            AppCard(Modifier.fillMaxWidth().testTag("forecast-overall")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("This month, day ${forecast.daysElapsed} of ${forecast.daysInMonth}", style = MaterialTheme.typography.labelLarge)
                    Text("On track for ${money(forecast.runRateIncluded)}", style = MaterialTheme.typography.headlineSmall)
                    Text("Spent so far ${money(forecast.spentIncluded)}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        forecast.averageIncluded?.let { "3-month average ${money(it)}" } ?: "3-month average: not enough history",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (forecast.spentAll != forecast.spentIncluded) Text(
                        "Left out of these totals: one-off categories (${money(forecast.spentAll - forecast.spentIncluded)} spent)",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(forecast.categories, key = { it.categoryUid ?: "none" }) { row ->
            CategoryForecastCard(row, categories[row.categoryUid], onOpenCategory)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryForecastCard(row: CategoryForecast, category: CategoryEntity?, onOpen: (String) -> Unit) {
    val warned = row.warnings.isNotEmpty()
    AppCard(
        Modifier.fillMaxWidth().testTag("forecast-${row.categoryUid ?: "none"}")
            .clickable(enabled = row.categoryUid != null) { onOpen(row.categoryUid!!) },
        colors = CardDefaults.cardColors(containerColor = if (warned) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(category?.icon, category?.color, size = 36.dp)
                Text(category?.title ?: "Balance correction", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(money(row.spent))
            }
            if (row.limit != null) {
                val bar = row.limit * row.threshold / 100
                LinearProgressIndicator(
                    progress = { (row.spent.toFloat() / bar.coerceAtLeast(1)).coerceIn(0f, 1f) }, Modifier.fillMaxWidth(),
                    color = if (warned) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Text("Limit ${money(row.limit)}" + if (row.threshold != 100) " · warns at ${row.threshold}%" else "", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "On track for ${money(row.runRate)}" + (row.average?.let { " · 3-month average ${money(it)}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (row.warnings.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.warnings.sorted().forEach { SuggestionChip({}, label = { Text(it.text()) }) }
            }
            if (!row.inOverall) Text("Not in the overall figure (one-off)", style = MaterialTheme.typography.bodySmall)
        }
    }
}
