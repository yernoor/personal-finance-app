package dev.yernur.moneytracker.data.wealth

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * [fraction] is 0..1 for a progress bar. [monthsLeft] is null without a deadline, 0 once it has passed.
 * [requiredMonthly] is what to put aside each month (end of month) to arrive on time; null when reached, without a deadline, or past it.
 */
data class GoalProgress(val current: Long, val target: Long, val fraction: Double, val reached: Boolean, val monthsLeft: Int?, val requiredMonthly: Long?)

private val precision = MathContext(30)

/** [annualReturnPercent] is what the money earns meanwhile (0 = under the mattress). */
fun goalProgress(current: Long, target: Long, targetDate: LocalDate?, today: LocalDate, annualReturnPercent: BigDecimal = BigDecimal.ZERO): GoalProgress {
    val fraction = if (target <= 0) 1.0 else (current.toDouble() / target).coerceIn(0.0, 1.0)
    val reached = current >= target
    val monthsLeft = targetDate?.let { maxOf(0L, ChronoUnit.MONTHS.between(today, it)).toInt() }
    val required = if (reached || monthsLeft == null || monthsLeft == 0) null else requiredMonthly(current, target, monthsLeft, annualReturnPercent)
    return GoalProgress(current, target, fraction, reached, monthsLeft, required)
}

/** Payment p with current * g^n + p * (g^n - 1) / i = target, where i is the monthly rate and g = 1 + i. Rounded up to a whole minor unit. */
private fun requiredMonthly(current: Long, target: Long, months: Int, annualReturnPercent: BigDecimal): Long {
    val i = annualReturnPercent.divide(BigDecimal(1200), precision)
    val missing = BigDecimal(target - current)
    if (i.signum() == 0) return missing.divide(BigDecimal(months), 0, RoundingMode.CEILING).toLong()
    val growth = (BigDecimal.ONE + i).pow(months, precision)
    val gap = BigDecimal(target) - BigDecimal(current).multiply(growth, precision)
    if (gap.signum() <= 0) return 0 // the current money alone grows past the target
    return gap.multiply(i, precision).divide(growth - BigDecimal.ONE, 0, RoundingMode.CEILING).toLong()
}

/** How net worth moved: the difference to the newest snapshot that is at least [days] old, else to the oldest one (null with no earlier snapshot). */
fun netWorthChange(snapshotTotals: List<Pair<LocalDate, Long>>, today: LocalDate, current: Long, days: Long = 30): Pair<Long, LocalDate>? {
    val earlier = snapshotTotals.filter { it.first < today }.sortedBy { it.first }
    if (earlier.isEmpty()) return null
    val base = earlier.lastOrNull { it.first <= today.minusDays(days) } ?: earlier.first()
    return (current - base.second) to base.first
}
