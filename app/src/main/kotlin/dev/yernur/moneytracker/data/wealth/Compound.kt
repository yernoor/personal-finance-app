package dev.yernur.moneytracker.data.wealth

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** When interest is added to the balance that earns further interest. Yearly: interest accrues each month but is credited on the last month of the year. */
enum class Capitalization { Monthly, Yearly }

/** Whether each month's contribution is paid before (Start) or after (End) that month's interest is calculated. */
enum class ContributionTiming { Start, End }

/** [annualRatePercent] 12 means 12% a year, accruing r/12 each month. */
data class CompoundInput(
    val startMinor: Long,
    val annualRatePercent: BigDecimal,
    val monthlyContributionMinor: Long,
    val years: Int,
    val capitalization: Capitalization = Capitalization.Monthly,
    val contributionTiming: ContributionTiming = ContributionTiming.End,
)

/** One row per year; [contributed] and [interest] are running totals (the start balance counts as contributed). */
data class CompoundYear(val year: Int, val contributed: Long, val interest: Long, val balance: Long)

private val precision = MathContext(30)
private val hundred = BigDecimal(100)

fun compound(input: CompoundInput): List<CompoundYear> {
    require(input.years >= 0) { "years must not be negative" }
    val monthlyRate = input.annualRatePercent.divide(hundred.multiply(BigDecimal(12)), precision)
    val contribution = BigDecimal(input.monthlyContributionMinor)
    var principal = BigDecimal(input.startMinor) // earns interest
    var pending = BigDecimal.ZERO // yearly capitalization: accrued, not yet earning
    var contributed = input.startMinor
    return (1..input.years).map { year ->
        for (month in 1..12) {
            if (input.contributionTiming == ContributionTiming.Start) principal += contribution
            val accrued = principal.multiply(monthlyRate, precision)
            when (input.capitalization) {
                Capitalization.Monthly -> principal += accrued
                Capitalization.Yearly -> pending += accrued
            }
            if (input.contributionTiming == ContributionTiming.End) principal += contribution
            if (month == 12) {
                principal += pending
                pending = BigDecimal.ZERO
            }
        }
        contributed += input.monthlyContributionMinor * 12
        val rounded = principal.setScale(0, RoundingMode.HALF_UP).toLong()
        CompoundYear(year, contributed, rounded - contributed, rounded)
    }
}
