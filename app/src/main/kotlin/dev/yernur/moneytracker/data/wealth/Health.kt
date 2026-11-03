package dev.yernur.moneytracker.data.wealth

import java.math.BigDecimal

/**
 * Monthly figures in the default currency.
 * [availableMinor] is what is left to save or invest: income minus the ledger's average spending minus obligations the ledger does not
 * already contain (negative = spending more than earned). Without spending history only the obligations are subtracted.
 */
data class HealthSummary(
    val incomeMinor: Long,
    val obligationsMinor: Long,
    val averageSpendingMinor: Long?,
    val availableMinor: Long,
    /** Null without income. */
    val savingsRatePercent: Double?,
    val obligationsSharePercent: Double?,
    /** How many months the savings (accounts) cover the usual monthly outlay; null when that outlay is unknown or zero. */
    val emergencyMonths: Double?,
)

fun healthSummary(
    incomeMinor: Long,
    obligationsMinor: Long,
    obligationsOutsideLedgerMinor: Long,
    averageSpendingMinor: Long?,
    savingsMinor: Long,
): HealthSummary {
    val outlay = (averageSpendingMinor ?: 0L) + obligationsOutsideLedgerMinor
    val available = incomeMinor - outlay
    return HealthSummary(
        incomeMinor, obligationsMinor, averageSpendingMinor, available,
        savingsRatePercent = if (incomeMinor > 0) available * 100.0 / incomeMinor else null,
        obligationsSharePercent = if (incomeMinor > 0) obligationsMinor * 100.0 / incomeMinor else null,
        emergencyMonths = if (outlay > 0) savingsMinor.toDouble() / outlay else null,
    )
}

/** What a projection starts from and assumes; money in the default currency, [otherNetMinor] = manual assets minus liabilities (held flat). */
data class ProjectionInputs(
    val accountsMinor: Long,
    val investmentsMinor: Long,
    val otherNetMinor: Long,
    val monthlySurplusMinor: Long,
    /** Share of the surplus put into investments; the rest stays in accounts. */
    val investSharePercent: Int,
    val accountsRatePercent: BigDecimal,
    /** Usually the bonds' own current yield, i.e. their coupons reinvested. */
    val investmentsRatePercent: BigDecimal,
    val years: Int,
)

data class NetWorthYear(val year: Int, val totalMinor: Long)

/** Net worth year by year: accounts and investments each compound at their own rate, the monthly surplus is split between them, manual items stay flat. */
fun projectNetWorth(i: ProjectionInputs): List<NetWorthYear> {
    val toInvest = i.monthlySurplusMinor * i.investSharePercent / 100
    val accounts = compound(CompoundInput(i.accountsMinor, i.accountsRatePercent, i.monthlySurplusMinor - toInvest, i.years))
    val investments = compound(CompoundInput(i.investmentsMinor, i.investmentsRatePercent, toInvest, i.years))
    return accounts.zip(investments) { a, b -> NetWorthYear(a.year, a.balance + b.balance + i.otherNetMinor) }
}
