package dev.yernur.moneytracker.data

import dev.yernur.moneytracker.data.db.ReminderEntity
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.TxFull
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/** Rules: docs/FEATURES.md, "Analysis rules". */
const val RECURRING_MIN_MONTHS = 3
const val RECURRING_MONTHS_BACK = 8

/** Amounts of one run may differ from its median by this much (percent). */
const val RECURRING_AMOUNT_TOLERANCE_PERCENT = 20
const val PRICE_CHANGE_MIN_PERCENT = 5
const val DUPLICATE_WINDOW_MINUTES = 10L

data class RecurringItem(
    val title: String,
    /** The latest amount, default currency. */
    val amount: Long,
    val months: Int,
    val lastDate: LocalDate,
    /** One month after [lastDate]: a guess, labelled "about" on screen. */
    val next: LocalDate,
    /** Percent change against the month before; null when under [PRICE_CHANGE_MIN_PERCENT] or unknown. */
    val priceChangePercent: Int?,
    val previousAmount: Long?,
    val hasReminder: Boolean,
)

/**
 * Expenses whose comment (trimmed, case-insensitive) appears in at least [RECURRING_MIN_MONTHS] consecutive months, the run ending this month
 * or last month, with amounts within [RECURRING_AMOUNT_TOLERANCE_PERCENT] of the run's median. Two operations in one month: the larger counts.
 * [rows] must cover about [RECURRING_MONTHS_BACK] months. Biggest first.
 */
fun detectRecurring(rows: List<TxFull>, reminders: List<ReminderEntity>, today: LocalDate): List<RecurringItem> {
    val thisMonth = YearMonth.from(today)
    val reminderTitles = reminders.filter { !it.isRemoved }.map { it.title.trim().lowercase() }.toSet()
    return rows.filter { it.type == TransactionType.Expense && !it.comment.isNullOrBlank() && it.amount > 0 }
        .groupBy { it.comment!!.trim().lowercase() }
        .mapNotNull { (key, group) ->
            val perMonth = group.groupBy { YearMonth.from(LocalDate.parse(it.date)) }.mapValues { (_, l) -> l.maxBy { it.amount } }
            val last = perMonth.keys.maxOrNull()?.takeIf { it >= thisMonth.minusMonths(1) && it <= thisMonth } ?: return@mapNotNull null
            val run = generateSequence(last) { it.minusMonths(1) }.takeWhile { it in perMonth }.toList().reversed()
            if (run.size < RECURRING_MIN_MONTHS) return@mapNotNull null
            val amounts = run.map { perMonth.getValue(it).amount }
            val median = amounts.sorted()[amounts.size / 2]
            if (amounts.any { abs(it - median) * 100 > median * RECURRING_AMOUNT_TOLERANCE_PERCENT }) return@mapNotNull null
            val latest = perMonth.getValue(last)
            val previous = amounts[amounts.size - 2]
            val change = divideRounded((latest.amount - previous) * 100, previous).toInt()
            RecurringItem(
                latest.comment!!.trim(), latest.amount, run.size, LocalDate.parse(latest.date), LocalDate.parse(latest.date).plusMonths(1),
                change.takeIf { abs(it) >= PRICE_CHANGE_MIN_PERCENT }, previous, key in reminderTitles,
            )
        }.sortedByDescending { it.amount }
}

/** Two operations the same day on the same account and category with the same amount, created within [DUPLICATE_WINDOW_MINUTES] of each other. [second] is the newer. */
data class DuplicatePair(val first: TxFull, val second: TxFull)

fun findDuplicates(rows: List<TxFull>, kept: Set<String> = emptySet()): List<DuplicatePair> =
    rows.filter { it.accountAmount > 0 }
        .groupBy { listOf(it.type, it.accountUid, it.categoryUid, it.accountAmount, it.date) }
        .values.flatMap { group ->
            group.sortedBy { it.created }.zipWithNext().filter { (a, b) ->
                b.uid !in kept && minutesBetween(a.created, b.created) <= DUPLICATE_WINDOW_MINUTES
            }.map { (a, b) -> DuplicatePair(a, b) }
        }.sortedByDescending { it.second.created }

private fun minutesBetween(a: String, b: String): Long =
    runCatching { Duration.between(Instant.parse(a), Instant.parse(b)).toMinutes() }.getOrDefault(Long.MAX_VALUE)
