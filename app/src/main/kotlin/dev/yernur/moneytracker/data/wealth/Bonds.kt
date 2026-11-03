package dev.yernur.moneytracker.data.wealth

import dev.yernur.moneytracker.data.db.BondEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A payment on [date]: the coupon and, on the maturity date, the face value too. */
data class CashFlow(val date: LocalDate, val couponMinor: Long, val principalMinor: Long = 0) {
    val totalMinor get() = couponMinor + principalMinor
}

/** Money in the bond's own currency. */
data class BondSummary(
    val annualCouponMinor: Long,
    val couponMinor: Long,
    val currentYieldPercent: Double?,
    val yieldToMaturityPercent: Double?,
    val remaining: List<CashFlow>,
)

/** One coupon in minor units: face * pieces * rate / coupons per year. */
fun couponAmount(bond: BondEntity): Long =
    BigDecimal(bond.faceMinor * bond.quantity).multiply(BigDecimal(bond.couponRateBp)).divide(BigDecimal(10_000L * bond.couponsPerYear), 0, RoundingMode.HALF_UP).toLong()

fun annualCoupon(bond: BondEntity): Long = couponAmount(bond) * bond.couponsPerYear

/**
 * Future payments after [today], oldest first: coupon dates are counted back from maturity in steps of 12 / couponsPerYear months
 * (an assumption: real schedules can differ by a few days), and the face value is paid with the last coupon.
 */
fun remainingCashFlows(bond: BondEntity, today: LocalDate): List<CashFlow> {
    val maturity = LocalDate.parse(bond.maturityDate)
    val step = 12L / bond.couponsPerYear
    val coupon = couponAmount(bond)
    val flows = generateSequence(0L) { it + 1 }.map { maturity.minusMonths(it * step) }.takeWhile { it > today }
        .map { CashFlow(it, coupon) }.toMutableList()
    if (flows.isEmpty()) return emptyList()
    flows[flows.indexOfFirst { it.date == maturity }] = CashFlow(maturity, coupon, bond.faceMinor * bond.quantity)
    return flows.reversed()
}

fun summarizeBond(bond: BondEntity, today: LocalDate): BondSummary {
    val value = bond.currentValueMinor ?: bond.costMinor
    val remaining = remainingCashFlows(bond, today)
    val annual = annualCoupon(bond)
    return BondSummary(
        annualCouponMinor = annual,
        couponMinor = couponAmount(bond),
        currentYieldPercent = if (value > 0) annual * 100.0 / value else null,
        yieldToMaturityPercent = if (value > 0) yieldToMaturity(value, remaining, today) else null,
        remaining = remaining,
    )
}

/**
 * Annual effective yield (%) that makes the future payments worth [priceMinor] today, found by bisection with actual/365 day counts.
 * Ignores accrued interest, taxes and fees, so it is an estimate. Null when there are no payments or no yield fits.
 */
fun yieldToMaturity(priceMinor: Long, flows: List<CashFlow>, today: LocalDate): Double? {
    if (flows.isEmpty()) return null
    fun presentValue(rate: Double) = flows.sumOf { it.totalMinor / Math.pow(1 + rate, ChronoUnit.DAYS.between(today, it.date) / 365.0) }
    var low = -0.99
    var high = 10.0
    if (presentValue(low) < priceMinor || presentValue(high) > priceMinor) return null
    repeat(100) {
        val mid = (low + high) / 2
        if (presentValue(mid) > priceMinor) low = mid else high = mid
    }
    return (low + high) / 2 * 100
}

data class LadderYear(val couponsMinor: Long, val principalMinor: Long)

/** Payments per calendar year, oldest first. Convert to one currency before calling when the portfolio mixes currencies. */
fun ladder(flows: List<CashFlow>): Map<Int, LadderYear> =
    flows.groupBy { it.date.year }.toSortedMap().mapValues { (_, list) -> LadderYear(list.sumOf { it.couponMinor }, list.sumOf { it.principalMinor }) }
