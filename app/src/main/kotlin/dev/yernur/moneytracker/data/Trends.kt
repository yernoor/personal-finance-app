package dev.yernur.moneytracker.data

import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.TxDetail
import dev.yernur.moneytracker.data.db.TxLite
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Rules: docs/FEATURES.md, "Analysis rules". Amounts are default-currency minor units. */
const val ANALYSIS_MONTHS = 6
const val DETAIL_MONTHS = 12

/** A category must be this much above its average (percent) and by this amount (5,000.00) to be called out. */
const val RISE_PERCENT = 25
const val RISE_MIN_AMOUNT = 500_000L

/** [projected] is set for the current month only: its run-rate over all categories. */
data class MonthBar(val month: YearMonth, val spent: Long, val projected: Long?)

data class CategoryTrend(
    val categoryUid: String,
    /** Oldest first; the last value is the current month so far. */
    val monthly: List<Long>,
    val runRate: Long,
    val average: Long?,
    /** Run-rate against the average; null before [MIN_RUN_RATE_DAYS] days of the month, with nothing spent yet this month, or without an average. */
    val deltaPercent: Int?,
    val limit: Long?,
)

enum class InsightKind { Rise, Fall }

/** [percent] is the rise over the average for [InsightKind.Rise]; 0 for a fall. */
data class Insight(val kind: InsightKind, val categoryUid: String, val percent: Int)

data class Analysis(
    val bars: List<MonthBar>,
    /** Mean of the full months shown that are not before the first month with spending; null when there is none. */
    val average: Long?,
    /** The projected current month against [average], in percent; null without an average. */
    val projectedChangePercent: Int?,
    val categories: List<CategoryTrend>,
    val insights: List<Insight>,
)

fun analysis(rows: List<TxLite>, forecast: Forecast, today: LocalDate): Analysis {
    val ranges = PeriodUnit.Month.lastRanges(today, ANALYSIS_MONTHS)
    val months = buckets(rows, ranges)
    val projected = forecast.categories.sumOf { it.runRate }
    val bars = months.mapIndexed { i, b ->
        MonthBar(YearMonth.from(b.range.from), b.expense, if (i == months.lastIndex) projected else null)
    }
    val fullMonths = bars.dropLast(1).dropWhile { it.spent == 0L }
    val average = if (fullMonths.isEmpty()) null else divideRounded(fullMonths.sumOf { it.spent }, fullMonths.size.toLong())

    val forecastBy = forecast.categories.associateBy { it.categoryUid }
    val uids = months.flatMap { it.expenseByCategory.keys }.filterNotNull().toSet()
    val categories = uids.map { uid ->
        val f = forecastBy[uid]
        val average3 = f?.average
        val runRate = f?.runRate ?: 0L
        // Nothing spent yet this month: a pace of 0 says nothing, so no change is shown.
        val delta = if (average3 == null || average3 <= 0 || forecast.daysElapsed < MIN_RUN_RATE_DAYS || (months.last().expenseByCategory[uid] ?: 0L) == 0L) null
        else divideRounded((runRate - average3) * 100, average3).toInt()
        CategoryTrend(uid, months.map { it.expenseByCategory[uid] ?: 0L }, runRate, average3, delta, f?.limit)
    }.sortedByDescending { it.runRate }

    return Analysis(
        bars, average, average?.takeIf { it > 0 }?.let { divideRounded((projected - it) * 100, it).toInt() }, categories, insights(categories),
    )
}

private fun insights(categories: List<CategoryTrend>): List<Insight> {
    val rises = categories.filter { (it.deltaPercent ?: 0) >= RISE_PERCENT && it.runRate - (it.average ?: 0) >= RISE_MIN_AMOUNT }
        .sortedByDescending { it.deltaPercent }.map { Insight(InsightKind.Rise, it.categoryUid, it.deltaPercent!!) }
    // Three drops in a row need four full months; the last value of `monthly` is the unfinished month.
    val falls = categories.filter { c -> c.monthly.dropLast(1).takeLast(4).let { it.size == 4 && it.zipWithNext().all { (a, b) -> a > b } } }
        .map { Insight(InsightKind.Fall, it.categoryUid, 0) }
    return rises + falls
}

data class CategoryDetail(
    /** The last [DETAIL_MONTHS] months, oldest first; the last is the current month so far. */
    val months: List<Pair<YearMonth, Long>>,
    val runRate: Long,
    val average: Long?,
    val limit: Long?,
    /** Spending by day of week (Monday first) over the last three full months. */
    val weekdays: List<Long>,
    /** Share of the weekday spending that falls on Friday to Sunday, percent; null with no spending. */
    val fridayToSundayPercent: Int?,
    /** The biggest operations of the current month, largest first. */
    val largest: List<TxDetail>,
)

/** [rows] and [details] cover the last [DETAIL_MONTHS] months up to [today]. */
fun categoryDetail(rows: List<TxLite>, details: List<TxDetail>, categoryUid: String, forecast: Forecast, today: LocalDate): CategoryDetail {
    val ranges = PeriodUnit.Month.lastRanges(today, DETAIL_MONTHS)
    val months = buckets(rows, ranges).map { YearMonth.from(it.range.from) to (it.expenseByCategory[categoryUid] ?: 0L) }
    val f = forecast.categories.firstOrNull { it.categoryUid == categoryUid }

    val thisMonth = YearMonth.from(today)
    val mine = details.filter { it.type == TransactionType.Expense && it.categoryUid == categoryUid }.map { LocalDate.parse(it.date) to it }
    val weekdayTotals = LongArray(7)
    mine.filter { YearMonth.from(it.first).let { m -> m < thisMonth && m >= thisMonth.minusMonths(HISTORY_MONTHS.toLong()) } }
        .forEach { (date, tx) -> weekdayTotals[date.dayOfWeek.value - 1] += tx.amount }
    val total = weekdayTotals.sum()
    val weekend = listOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).sumOf { weekdayTotals[it.value - 1] }

    return CategoryDetail(
        months, f?.runRate ?: 0L, f?.average, f?.limit, weekdayTotals.toList(),
        if (total > 0) divideRounded(weekend * 100, total).toInt() else null,
        mine.filter { YearMonth.from(it.first) == thisMonth }.map { it.second }.sortedByDescending { it.amount }.take(3),
    )
}
