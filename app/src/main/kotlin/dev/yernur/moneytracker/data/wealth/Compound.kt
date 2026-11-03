package dev.yernur.moneytracker.data.wealth

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** [annualRatePercent] 12 means 12% a year, compounded [compoundsPerYear] times a year. Contributions are paid at the end of each month. */
data class CompoundInput(
    val startMinor: Long,
    val annualRatePercent: BigDecimal,
    val monthlyContributionMinor: Long,
    val years: Int,
    val compoundsPerYear: Int = 12,
    val inflationPercent: BigDecimal = BigDecimal.ZERO,
)

/** One row per year; [contributed] and [interest] are running totals (the start balance counts as contributed). */
data class CompoundYear(val year: Int, val contributed: Long, val interest: Long, val balance: Long, val realBalance: Long)

private val precision = MathContext(30)
private val hundred = BigDecimal(100)

/** Growth factor of one month: (1 + r/n)^(n/12). Exact for monthly compounding; otherwise a Double power, which is plenty for a rate. */
private fun monthlyFactor(annualRatePercent: BigDecimal, compoundsPerYear: Int): BigDecimal {
    val perPeriod = BigDecimal.ONE + annualRatePercent.divide(hundred.multiply(BigDecimal(compoundsPerYear)), precision)
    if (compoundsPerYear == 12) return perPeriod
    return BigDecimal(Math.pow(perPeriod.toDouble(), compoundsPerYear / 12.0))
}

fun compound(input: CompoundInput): List<CompoundYear> {
    require(input.years >= 0 && input.compoundsPerYear > 0) { "years must not be negative and compoundsPerYear must be positive" }
    val factor = monthlyFactor(input.annualRatePercent, input.compoundsPerYear)
    val inflation = BigDecimal.ONE + input.inflationPercent.divide(hundred, precision)
    val contribution = BigDecimal(input.monthlyContributionMinor)
    var balance = BigDecimal(input.startMinor)
    var contributed = input.startMinor
    return (1..input.years).map { year ->
        repeat(12) { balance = balance.multiply(factor, precision) + contribution }
        contributed += input.monthlyContributionMinor * 12
        val rounded = balance.setScale(0, RoundingMode.HALF_UP).toLong()
        val real = balance.divide(inflation.pow(year), precision).setScale(0, RoundingMode.HALF_UP).toLong()
        CompoundYear(year, contributed, rounded - contributed, rounded, real)
    }
}
