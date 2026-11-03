package dev.yernur.moneytracker.data

import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.ProjectionMode
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.TxLite
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Most urgent first. All compare against `limit * threshold / 100` (docs/FEATURES.md, projection design). */
enum class WarningKind { Exceeded, RunRate, Average }

data class CategoryForecast(
    /** Null: expenses without a category. */
    val categoryUid: String?,
    val spent: Long,
    /** Linear run-rate: spent so far / days elapsed * days in month. */
    val runRate: Long,
    /** Mean of the previous full months (up to 3); null when there is no earlier month with data. */
    val average: Long?,
    /** Monthly planned outlay in default-currency minor units. */
    val limit: Long?,
    val threshold: Int,
    val operationsPerMonth: Double,
    val inOverall: Boolean,
    val warnings: Set<WarningKind>,
)

data class Forecast(
    val month: YearMonth,
    val daysElapsed: Int,
    val daysInMonth: Int,
    val spentAll: Long,
    /** Overall figures leave out one-off categories (fewer than 2 operations per month, unless overridden). */
    val spentIncluded: Long,
    val runRateIncluded: Long,
    val averageIncluded: Long?,
    val categories: List<CategoryForecast>,
) {
    val warnings get() = categories.filter { it.warnings.isNotEmpty() }
}

/** [MIN_OPERATIONS_PER_MONTH] is the owner's rule of thumb for "one-off". */
const val MIN_OPERATIONS_PER_MONTH = 2.0
const val HISTORY_MONTHS = 3

/** A run-rate extrapolated from fewer days than this is noise, so it raises no warning. */
const val MIN_RUN_RATE_DAYS = 10

/**
 * @param rows transactions from the first day of the month [HISTORY_MONTHS] months back up to [today]
 * @param earliest date of the oldest transaction in the whole ledger (limits how many past months exist)
 */
fun forecast(
    rows: List<TxLite>,
    categories: List<CategoryEntity>,
    today: LocalDate,
    earliest: LocalDate?,
    defaultThreshold: Int,
): Forecast {
    val month = YearMonth.from(today)
    val daysInMonth = month.lengthOfMonth()
    val daysElapsed = today.dayOfMonth

    val expenses = rows.filter { it.type == TransactionType.Expense }.map { LocalDate.parse(it.date) to it }
    val current = expenses.filter { YearMonth.from(it.first) == month && it.first <= today }.map { it.second }
    val monthsBack = monthsAvailable(earliest, month)
    val previous = expenses.filter {
        val m = YearMonth.from(it.first)
        m < month && m >= month.minusMonths(monthsBack.toLong())
    }.map { it.second }

    val spentBy = current.groupBy { it.categoryUid }.mapValues { (_, l) -> l.sumOf { it.amount } }
    val previousBy = previous.groupBy { it.categoryUid }
    val categoryByUid = categories.associateBy { it.uid }
    val uids: Set<String?> = spentBy.keys + previousBy.keys +
        categories.filter { it.type == TransactionType.Expense && !it.isRemoved && !it.isArchived && it.limitAmount != null }.map { it.uid }

    val result = uids.map { uid ->
        val category = categoryByUid[uid]
        val spent = spentBy[uid] ?: 0L
        val runRate = if (daysElapsed >= daysInMonth) spent else divideRounded(spent * daysInMonth, daysElapsed.toLong())
        val previousOps = previousBy[uid].orEmpty()
        val average = if (monthsBack == 0) null else divideRounded(previousOps.sumOf { it.amount }, monthsBack.toLong())
        val ops = if (monthsBack == 0) (current.count { it.categoryUid == uid }).toDouble() else previousOps.size.toDouble() / monthsBack
        val threshold = category?.warnThresholdPercent ?: defaultThreshold
        val limit = category?.limitAmount?.takeIf { it > 0 }
        val inOverall = when (category?.projectionMode ?: ProjectionMode.Auto) {
            ProjectionMode.Include -> true
            ProjectionMode.Exclude -> false
            ProjectionMode.Auto -> ops >= MIN_OPERATIONS_PER_MONTH
        }
        CategoryForecast(uid, spent, runRate, average, limit, threshold, ops, inOverall, warningsFor(limit, threshold, spent, runRate, average, daysElapsed))
    }.filter { it.spent > 0 || (it.average ?: 0) > 0 || it.limit != null }
        // Warned categories first, then the biggest.
        .sortedWith(compareByDescending<CategoryForecast> { it.warnings.isNotEmpty() }.thenByDescending { maxOf(it.runRate, it.average ?: 0) })

    val included = result.filter { it.inOverall }
    return Forecast(
        month, daysElapsed, daysInMonth,
        spentAll = result.sumOf { it.spent }, spentIncluded = included.sumOf { it.spent },
        runRateIncluded = included.sumOf { it.runRate },
        averageIncluded = if (monthsBack == 0) null else included.sumOf { it.average ?: 0 },
        categories = result,
    )
}

private fun warningsFor(limit: Long?, threshold: Int, spent: Long, runRate: Long, average: Long?, daysElapsed: Int): Set<WarningKind> {
    if (limit == null) return emptySet()
    val bar = divideRounded(limit * threshold, 100)
    return buildSet {
        if (spent > bar) add(WarningKind.Exceeded)
        if (daysElapsed >= MIN_RUN_RATE_DAYS && runRate > bar) add(WarningKind.RunRate)
        if (average != null && average > bar) add(WarningKind.Average)
    }
}

/** Past full months that can contribute, at most [HISTORY_MONTHS]. The earliest month counts as a full month (unverified for partial data). */
internal fun monthsAvailable(earliest: LocalDate?, current: YearMonth): Int {
    if (earliest == null) return 0
    val first = YearMonth.from(earliest)
    if (first >= current) return 0
    return minOf(HISTORY_MONTHS.toLong(), ChronoUnit.MONTHS.between(first, current)).toInt()
}

private fun divideRounded(numerator: Long, denominator: Long): Long =
    BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP).toLong()
