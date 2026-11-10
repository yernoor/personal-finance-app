package dev.yernur.moneytracker.data

import dev.yernur.moneytracker.data.db.ReminderEntity
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.TxLite
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Rules and assumptions: docs/FEATURES.md, "Affordability rules". */
enum class Verdict { Yes, Tight, No }

/** Category run-rate plus the price against the monthly limit. [over] is 0 when inside it. */
data class LimitImpact(val limit: Long, val spent: Long, val expected: Long, val withPurchase: Long) {
    val over get() = maxOf(0L, withPurchase - limit)
}

data class GoalDelay(val title: String, val days: Int)

data class AffordResult(
    val verdict: Verdict,
    val price: Long,
    val balance: Long,
    val horizon: LocalDate,
    val daysToHorizon: Int,
    /** Run-rate based; [expectedAverage] is the 3-month-average variant (null without history). */
    val expected: Long,
    val expectedAverage: Long?,
    val scheduled: Long,
    val scheduledCount: Int,
    val free: Long,
    val afterPurchase: Long,
    val afterPurchaseAverage: Long?,
    val limit: LimitImpact?,
    val goals: List<GoalDelay>,
)

/** The price takes more than this share of the free money: Tight. The owner has not chosen it. */
const val TIGHT_SHARE_PERCENT = 50

/**
 * @param balance the paying account's balance in default-currency minor units
 * @param monthlySaving mean monthly (income - expenses) of recent full months, null when unknown
 * @param goalTitles titles of the goals not reached yet; each is delayed by the same number of days
 */
fun affordability(
    price: Long,
    categoryUid: String?,
    balance: Long,
    forecast: Forecast,
    reminders: List<ReminderEntity>,
    goalTitles: List<String>,
    monthlySaving: Long?,
    today: LocalDate,
): AffordResult {
    val horizon = nextPayday(reminders, today) ?: YearMonth.from(today).atEndOfMonth()
    val days = ChronoUnit.DAYS.between(today, horizon).toInt().coerceAtLeast(0)
    val daysInMonth = forecast.daysInMonth.toLong()

    val expected = divideRounded(forecast.runRateIncluded * days, daysInMonth)
    val expectedAverage = forecast.averageIncluded?.let { divideRounded(it * days, daysInMonth) }

    val due = reminders.filter { it.enabled && !it.isRemoved && it.transactionType == TransactionType.Expense && it.amount != null }
        .flatMap { r -> occurrences(r, today.plusDays(1), horizon).map { r.amount!! } }
    val scheduled = due.sum()

    val free = balance - expected - scheduled
    val after = free - price
    val limit = forecast.categories.firstOrNull { it.categoryUid == categoryUid }?.takeIf { it.limit != null }?.let {
        LimitImpact(it.limit!!, it.spent, it.runRate, it.runRate + price)
    }
    val verdict = when {
        after < 0 -> Verdict.No
        (limit?.over ?: 0L) > 0 || price * 100 > free * TIGHT_SHARE_PERCENT -> Verdict.Tight
        else -> Verdict.Yes
    }
    val delays = if (monthlySaving == null || monthlySaving <= 0) emptyList() else {
        val days = divideRounded(price * 30, monthlySaving).toInt()
        if (days <= 0) emptyList() else goalTitles.map { GoalDelay(it, days) }
    }
    return AffordResult(
        verdict, price, balance, horizon, days, expected, expectedAverage, scheduled, due.size, free, after,
        expectedAverage?.let { balance - it - scheduled - price }, limit, delays,
    )
}

/** The soonest day after [today] on which an enabled income reminder falls (looked at up to a year ahead), or null. */
fun nextPayday(reminders: List<ReminderEntity>, today: LocalDate): LocalDate? =
    reminders.filter { it.enabled && !it.isRemoved && it.transactionType == TransactionType.Income }
        .mapNotNull { occurrences(it, today.plusDays(1), today.plusYears(1)).firstOrNull() }
        .minOrNull()

/**
 * Days a reminder fires on within [from]..[to]. Only the "Day" and "Month" periods are known from the backup;
 * any other period (custom) yields nothing (unverified). A month-based reminder on the 31st fires on the last day of shorter months.
 */
fun occurrences(reminder: ReminderEntity, from: LocalDate, to: LocalDate): List<LocalDate> {
    val start = reminder.startDate.take(10).let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return emptyList()
    val end = reminder.endDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val last = if (end != null && end < to) end else to
    val first = if (start > from) start else from
    if (first > last) return emptyList()
    return when (reminder.period) {
        "Day" -> generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.toList()
        "Month" -> generateSequence(YearMonth.from(first)) { it.plusMonths(1) }.takeWhile { it.atDay(1) <= last }
            .map { ym -> ym.atDay(minOf(start.dayOfMonth, ym.lengthOfMonth())) }
            .filter { it >= first && it <= last }.toList()
        else -> emptyList()
    }
}

/** Mean of (income - expenses) over the previous full months that exist (up to [HISTORY_MONTHS]); null with none. [rows] must cover them. */
fun monthlySaving(rows: List<TxLite>, today: LocalDate, earliest: LocalDate?): Long? {
    val month = YearMonth.from(today)
    val count = monthsAvailable(earliest, month)
    if (count == 0) return null
    val from = month.minusMonths(count.toLong())
    val net = rows.filter { YearMonth.from(LocalDate.parse(it.date)).let { m -> m < month && m >= from } }
        .sumOf { if (it.type == TransactionType.Income) it.amount else -it.amount }
    return divideRounded(net, count.toLong())
}
