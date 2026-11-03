package dev.yernur.moneytracker.data.wealth

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * [fraction] is 0..1 for a progress bar. [monthsLeft] is null without a deadline, 0 once it has passed.
 * [requiredMonthly] is what to put aside each month (end of month) to arrive on time; null when reached, without a deadline, or past it.
 */
data class GoalProgress(val current: Long, val target: Long, val fraction: Double, val reached: Boolean, val monthsLeft: Int?, val requiredMonthly: Long?)

/** [requiredMonthly] puts the missing money aside evenly, ignoring what it would earn meanwhile. */
fun goalProgress(current: Long, target: Long, targetDate: LocalDate?, today: LocalDate): GoalProgress {
    val fraction = if (target <= 0) 1.0 else (current.toDouble() / target).coerceIn(0.0, 1.0)
    val reached = current >= target
    val monthsLeft = targetDate?.let { maxOf(0L, ChronoUnit.MONTHS.between(today, it)).toInt() }
    val required = if (reached || monthsLeft == null || monthsLeft == 0) null
    else BigDecimal(target - current).divide(BigDecimal(monthsLeft), 0, RoundingMode.CEILING).toLong()
    return GoalProgress(current, target, fraction, reached, monthsLeft, required)
}

/** How net worth moved: the difference to the newest snapshot that is at least [days] old, else to the oldest one (null with no earlier snapshot). */
fun netWorthChange(snapshotTotals: List<Pair<LocalDate, Long>>, today: LocalDate, current: Long, days: Long = 30): Pair<Long, LocalDate>? {
    val earlier = snapshotTotals.filter { it.first < today }.sortedBy { it.first }
    if (earlier.isEmpty()) return null
    val base = earlier.lastOrNull { it.first <= today.minusDays(days) } ?: earlier.first()
    return (current - base.second) to base.first
}
