package dev.yernur.moneytracker.ui

import dev.yernur.moneytracker.data.DateRange
import dev.yernur.moneytracker.data.PeriodUnit
import java.time.format.DateTimeFormatter

private val dayTitle = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
private val weekStart = DateTimeFormatter.ofPattern("MMM d")
private val weekEnd = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val monthTitle = DateTimeFormatter.ofPattern("LLLL yyyy")

/** "Sun, Oct 25, 2026" / "Oct 19 – Oct 25, 2026" / "October 2026" / "2026". */
fun periodTitle(unit: PeriodUnit, range: DateRange): String = when (unit) {
    PeriodUnit.Day -> range.from.format(dayTitle)
    PeriodUnit.Week -> "${range.from.format(weekStart)} – ${range.to.format(weekEnd)}"
    PeriodUnit.Month -> range.from.format(monthTitle)
    PeriodUnit.Year -> range.from.year.toString()
}

fun PeriodUnit.label(): String = name
