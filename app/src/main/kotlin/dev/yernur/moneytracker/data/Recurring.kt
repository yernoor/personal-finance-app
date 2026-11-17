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

/** A category counts as a bill when it averages at most this many payments a month over its run (Groceries, with many, does not). */
const val RECURRING_MAX_PAYMENTS_PER_MONTH = 3

data class RecurringItem(
    val title: String,
    /** The latest amount, default currency: one payment for a comment, the month's total for a category. */
    val amount: Long,
    val months: Int,
    val lastDate: LocalDate,
    /** One month after [lastDate]: a guess, labelled "about" on screen. */
    val next: LocalDate,
    /** Percent change against the month before; null when under [PRICE_CHANGE_MIN_PERCENT] or unknown. */
    val priceChangePercent: Int?,
    val previousAmount: Long?,
    val hasReminder: Boolean,
    /** The category of the latest payment, for the "make it a fixed bill" action; null without one. */
    val categoryUid: String? = null,
    /** Found by its category (a steady monthly total) rather than by a repeated comment. */
    val byCategory: Boolean = false,
    /** Stable id of the bill: "comment:<lowercase comment>" or "category:<uid>" (what the owner sets for it is saved under this). */
    val key: String = "",
)

/** One month of a group: the amount that counts, the latest date, the latest payment's category, how many payments. */
private class MonthEntry(val amount: Long, val date: LocalDate, val categoryUid: String?, val payments: Int)

/**
 * Recurring expenses, found two ways, biggest first. [rows] must cover about [RECURRING_MONTHS_BACK] months.
 * - By comment (trimmed, case-insensitive): the same comment in at least [RECURRING_MIN_MONTHS] consecutive months; two in a month, the larger counts.
 * - By category: a category's monthly total in at least [RECURRING_MIN_MONTHS] consecutive months, at most [RECURRING_MAX_PAYMENTS_PER_MONTH]
 *   payments a month on average (rent, an installment; not groceries). Skipped when any comment item lives in that category, so a mixed category
 *   (rent, internet, utility commented in Utilities) is not counted twice; an uncommented bill there needs a comment or its own category.
 * Either way the run ends this month or last month and every month is within [RECURRING_AMOUNT_TOLERANCE_PERCENT] of the run's median.
 */
fun detectRecurring(rows: List<TxFull>, reminders: List<ReminderEntity>, today: LocalDate, categoryTitles: Map<String, String> = emptyMap()): List<RecurringItem> {
    val thisMonth = YearMonth.from(today)
    val reminderTitles = reminders.filter { !it.isRemoved }.map { it.title.trim().lowercase() }.toSet()
    val expenses = rows.filter { it.type == TransactionType.Expense && it.amount > 0 }
    fun month(tx: TxFull) = YearMonth.from(LocalDate.parse(tx.date))

    val byComment = expenses.filter { !it.comment.isNullOrBlank() }.groupBy { it.comment!!.trim().lowercase() }.mapNotNull { (key, group) ->
        val perMonth = group.groupBy(::month).mapValues { (_, l) -> l.maxBy { it.amount }.let { MonthEntry(it.amount, LocalDate.parse(it.date), it.categoryUid, 1) } }
        val title = group.maxBy { it.date }.comment!!.trim()
        recurringRun(perMonth, thisMonth, false)?.let { item(it, title, key in reminderTitles, false, "comment:$key") }
    }
    val byCategory = expenses.filter { it.categoryUid != null }.groupBy { it.categoryUid!! }.mapNotNull { (uid, group) ->
        val perMonth = group.groupBy(::month).mapValues { (_, l) -> MonthEntry(l.sumOf { it.amount }, l.maxOf { LocalDate.parse(it.date) }, uid, l.size) }
        val title = categoryTitles[uid] ?: return@mapNotNull null
        if (byComment.any { it.categoryUid == uid }) return@mapNotNull null
        val found = recurringRun(perMonth, thisMonth, true) ?: return@mapNotNull null
        item(found, title, title.trim().lowercase() in reminderTitles, true, "category:$uid")
    }
    return (byComment + byCategory).sortedByDescending { it.amount }
}

/** The months of the run ending this month or last month, oldest first, or null when it is not a steady recurring run. */
private fun recurringRun(perMonth: Map<YearMonth, MonthEntry>, thisMonth: YearMonth, countPayments: Boolean): List<MonthEntry>? {
    val last = perMonth.keys.maxOrNull()?.takeIf { it >= thisMonth.minusMonths(1) && it <= thisMonth } ?: return null
    val run = generateSequence(last) { it.minusMonths(1) }.takeWhile { it in perMonth }.toList().reversed().map { perMonth.getValue(it) }
    if (run.size < RECURRING_MIN_MONTHS) return null
    if (countPayments && run.sumOf { it.payments } > RECURRING_MAX_PAYMENTS_PER_MONTH * run.size) return null
    val median = run.map { it.amount }.sorted()[run.size / 2]
    if (run.any { abs(it.amount - median) * 100 > median * RECURRING_AMOUNT_TOLERANCE_PERCENT }) return null
    return run
}

private fun item(run: List<MonthEntry>, title: String, hasReminder: Boolean, byCategory: Boolean, key: String, next: LocalDate = run.last().date.plusMonths(1)): RecurringItem {
    val latest = run.last()
    val previous = run.getOrNull(run.size - 2)?.amount
    val change = previous?.takeIf { it > 0 }?.let { divideRounded((latest.amount - it) * 100, it).toInt() }
    return RecurringItem(
        title, latest.amount, run.size, latest.date, next,
        change?.takeIf { abs(it) >= PRICE_CHANGE_MIN_PERCENT }, previous, hasReminder, latest.categoryUid, byCategory, key,
    )
}

/**
 * A bill the owner added by hand ([key] as in [RecurringItem.key]), listed from whatever payments it has in [rows]: no run of months or
 * steady amount is needed. Its months are the months with a payment; the next one is a month after the last, moved past [today]. Null without any.
 */
fun manualRecurring(key: String, title: String, rows: List<TxFull>, today: LocalDate): RecurringItem? {
    val expenses = rows.filter { it.type == TransactionType.Expense && it.amount > 0 }
    fun month(tx: TxFull) = YearMonth.from(LocalDate.parse(tx.date))
    val byCategory = key.startsWith("category:")
    val perMonth = when {
        key.startsWith("comment:") -> expenses.filter { it.comment?.trim()?.lowercase() == key.removePrefix("comment:") }.groupBy(::month)
            .mapValues { (_, l) -> l.maxBy { it.amount }.let { MonthEntry(it.amount, LocalDate.parse(it.date), it.categoryUid, 1) } }
        byCategory -> expenses.filter { it.categoryUid == key.removePrefix("category:") }.groupBy(::month)
            .mapValues { (m, l) -> MonthEntry(l.sumOf { it.amount }, l.maxOf { LocalDate.parse(it.date) }, l.first().categoryUid, l.size) }
        else -> return null
    }
    if (perMonth.isEmpty()) return null
    val run = perMonth.toSortedMap().values.toList()
    val next = generateSequence(run.last().date.plusMonths(1)) { it.plusMonths(1) }.first { it >= today }
    return item(run, title, false, byCategory, key, next)
}

/** Two operations the same day on the same account and category with the same amount, created within [DUPLICATE_WINDOW_MINUTES] of each other. [second] is the newer. */
data class DuplicatePair(val first: TxFull, val second: TxFull)

fun findDuplicates(rows: List<TxFull>, kept: Set<String> = emptySet()): List<DuplicatePair> =
    rows.filter { it.accountAmount > 0 }
        .groupBy { listOf(it.type, it.accountUid, it.categoryUid, it.accountAmount, it.date) }
        .values.flatMap { group ->
            // the uid breaks a tie of the same millisecond, so the order is stable
            group.sortedWith(compareBy({ it.created }, { it.uid })).zipWithNext().filter { (a, b) ->
                b.uid !in kept && minutesBetween(a.created, b.created) <= DUPLICATE_WINDOW_MINUTES
            }.map { (a, b) -> DuplicatePair(a, b) }
        }.sortedByDescending { it.second.created }

private fun minutesBetween(a: String, b: String): Long =
    runCatching { Duration.between(Instant.parse(a), Instant.parse(b)).toMinutes() }.getOrDefault(Long.MAX_VALUE)
