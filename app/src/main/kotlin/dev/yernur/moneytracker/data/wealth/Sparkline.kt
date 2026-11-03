package dev.yernur.moneytracker.data.wealth

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Points for a line chart, both axes scaled to 0..1 (y = 1 is the highest value). The x position follows the date, so a three-week gap
 * in the history looks like three weeks and not like one step. Null with fewer than two dates.
 */
fun sparklinePoints(points: List<Pair<LocalDate, Long>>): List<Pair<Float, Float>>? {
    val sorted = points.sortedBy { it.first }
    if (sorted.size < 2) return null
    val first = sorted.first().first
    val days = ChronoUnit.DAYS.between(first, sorted.last().first).toFloat().coerceAtLeast(1f)
    val low = sorted.minOf { it.second }
    val span = sorted.maxOf { it.second } - low
    return sorted.map { (date, value) ->
        ChronoUnit.DAYS.between(first, date) / days to if (span == 0L) 0.5f else (value - low).toFloat() / span
    }
}
