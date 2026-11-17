package dev.yernur.moneytracker.ui

import androidx.compose.ui.graphics.drawscope.clipPath

import androidx.compose.ui.geometry.RoundRect

import androidx.compose.ui.graphics.Path

import androidx.compose.ui.semantics.semantics

import androidx.compose.ui.semantics.contentDescription

import androidx.compose.ui.geometry.Size

import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.geometry.CornerRadius

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.Canvas

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.background

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

/** The month's figure split for the bar: spent on regular categories, one-offs (spent, not projected), and the projected rest. */
data class ForecastSplit(val regular: Long, val oneOffs: Long, val toCome: Long, val average: Long?) {
    val total get() = regular + oneOffs + toCome
}

/** [average]: what the month is compared with (Analysis: the last 3 full months of all spending, the line on the monthly bars). */
fun forecastSplit(f: Forecast, average: Long?) = ForecastSplit(
    f.spentIncluded, f.oneOffSpent, (f.runRateIncluded - f.spentIncluded).coerceAtLeast(0), average?.takeIf { it > 0 },
)

/**
 * The top of Analysis's month card: the month's figure big, its change against [average] as a pill, and one bar of where it comes from
 * (spent, one-offs, still to come) with the average marked on it. Amounts sit in a short legend instead of sentences.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthSummary(f: Forecast, average: Long?) {
    val split = forecastSplit(f, average)
    val scheme = MaterialTheme.colorScheme
    val spentColor = scheme.primary
    val oneOffColor = scheme.tertiary
    val toComeColor = scheme.primary.copy(alpha = 0.28f)
    val markColor = scheme.onSurface
    run {
        Column(Modifier.fillMaxWidth().testTag("forecast-overall"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("On track · day ${f.daysElapsed} of ${f.daysInMonth}", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Money.formatRounded(f.projected, DEFAULT_CURRENCY), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("forecast-total"))
                split.average?.let { avg ->
                    val change = ((split.total - avg) * 100 / avg).toInt()
                    val over = change > 0
                    Text(
                        "${if (over) "+" else ""}$change% vs avg", style = MaterialTheme.typography.labelMedium,
                        color = if (over) scheme.onErrorContainer else scheme.onSecondaryContainer,
                        modifier = Modifier.background(if (over) scheme.errorContainer else scheme.secondaryContainer, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            val summary = "Spent ${short(split.regular)}, one-offs ${short(split.oneOffs)}, still to come ${short(split.toCome)}" + (split.average?.let { ", 3-month average ${short(it)}" } ?: "")
            Canvas(Modifier.fillMaxWidth().height(22.dp).semantics { contentDescription = summary }) {
                val scale = maxOf(split.total, split.average ?: 0L, 1L).toFloat()
                val barTop = 4.dp.toPx()
                val barH = size.height - 2 * barTop
                val radius = CornerRadius(barH / 2)
                drawRoundRect(toComeColor.copy(alpha = 0.12f), Offset(0f, barTop), Size(size.width, barH), radius) // the track
                val rounded = Path().apply { addRoundRect(RoundRect(0f, barTop, size.width, barTop + barH, radius)) }
                clipPath(rounded) { // segments inside the rounded bar; the average mark below sticks out above and below it
                    var x = 0f
                    listOf(split.regular to spentColor, split.oneOffs to oneOffColor, split.toCome to toComeColor).forEach { (value, color) ->
                        val w = size.width * value / scale
                        if (w > 0f) drawRect(color, Offset(x, barTop), Size(w, barH))
                        x += w
                    }
                }
                split.average?.let { avg ->
                    val ax = (size.width * avg / scale).coerceIn(1.dp.toPx(), size.width - 1.dp.toPx())
                    drawLine(markColor, Offset(ax, 0f), Offset(ax, size.height), strokeWidth = 2.dp.toPx())
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LegendDot(spentColor, "Spent", split.regular)
                if (split.oneOffs > 0) LegendDot(oneOffColor, "One-offs", split.oneOffs)
                LegendDot(toComeColor, "To come", split.toCome)
                split.average?.let { LegendMark(markColor, "3-mo avg", it) }
            }
            val never = f.spentAll - f.spentIncluded - f.oneOffSpent
            if (never > 0) Text("Set to Never, not counted: ${short(never)}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}

private fun short(minor: Long) = Money.formatRounded(minor, DEFAULT_CURRENCY)

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String, amount: Long) =
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.foundation.layout.Box(Modifier.size(10.dp).background(color, CircleShape))
        Text("$label ${short(amount)}", style = MaterialTheme.typography.labelMedium)
    }

@Composable
private fun LegendMark(color: androidx.compose.ui.graphics.Color, label: String, amount: Long) =
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.foundation.layout.Box(Modifier.size(width = 2.dp, height = 12.dp).background(color))
        Text("$label ${short(amount)}", style = MaterialTheme.typography.labelMedium)
    }
