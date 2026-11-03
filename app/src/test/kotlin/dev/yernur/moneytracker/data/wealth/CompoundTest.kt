package dev.yernur.moneytracker.data.wealth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CompoundTest {
    private fun input(
        start: Long = 0, rate: String = "0", monthly: Long = 0, years: Int = 1,
        cap: Capitalization = Capitalization.Monthly, timing: ContributionTiming = ContributionTiming.End,
    ) = CompoundInput(start, BigDecimal(rate), monthly, years, cap, timing)

    @Test
    fun lumpSum_monthlyCapitalization_matchesHandComputedValue() {
        // 1000.00 * 1.01^12 = 1126.8250301...
        val year = compound(input(start = 100_000, rate = "12")).single()
        assertEquals(112_683L, year.balance)
        assertEquals(12_683L, year.interest)
        assertEquals(100_000L, year.contributed)
    }

    @Test
    fun contributionsAtTheEnd_matchTheOrdinaryAnnuity() {
        // 100.00 a month at 1% a month: 100 * (1.01^12 - 1) / 0.01 = 1268.25
        val year = compound(input(monthly = 10_000, rate = "12")).single()
        assertEquals(126_825L, year.balance)
        assertEquals(120_000L, year.contributed)
        assertEquals(6_825L, year.interest)
    }

    @Test
    fun contributionsAtTheStart_matchTheAnnuityDue() {
        // the ordinary annuity times 1.01 = 1280.93
        assertEquals(128_093L, compound(input(monthly = 10_000, rate = "12", timing = ContributionTiming.Start)).single().balance)
    }

    @Test
    fun yearlyCapitalization_creditsInterestOnlyAtYearEnd() {
        // 1000.00 at 10%: exactly 1100.00, then 1210.00 (interest on interest only from the second year)
        val rows = compound(input(start = 100_000, rate = "10", years = 2, cap = Capitalization.Yearly))
        assertEquals(110_000L, rows[0].balance)
        assertEquals(121_000L, rows[1].balance)
    }

    @Test
    fun yearlyCapitalization_withMonthlyContributions_accruesSimpleInterestWithinTheYear() {
        // 100.00 a month at 1% a month, interest not compounding within the year.
        // End of month: month m earns on 100 * (m - 1), sum 66 -> 1200 + 66.00 = 1266.00
        assertEquals(126_600L, compound(input(monthly = 10_000, rate = "12", cap = Capitalization.Yearly)).single().balance)
        // Start of month: month m earns on 100 * m, sum 78 -> 1278.00
        assertEquals(
            127_800L,
            compound(input(monthly = 10_000, rate = "12", cap = Capitalization.Yearly, timing = ContributionTiming.Start)).single().balance,
        )
    }

    @Test
    fun yearlyIsNeverMoreThanMonthly() {
        val monthly = compound(input(start = 100_000, monthly = 5_000, rate = "9", years = 10)).last().balance
        val yearly = compound(input(start = 100_000, monthly = 5_000, rate = "9", years = 10, cap = Capitalization.Yearly)).last().balance
        assertTrue(yearly < monthly)
    }

    @Test
    fun zeroRate_isJustSavings_withNoInterest() {
        val rows = compound(input(start = 500_000, monthly = 20_000, years = 3))
        assertEquals(500_000L + 20_000L * 36, rows.last().balance)
        assertTrue(rows.all { it.interest == 0L })
    }

    @Test
    fun zeroYears_returnsNoRows_andNegativeYearsAreRejected() {
        assertTrue(compound(input(years = 0)).isEmpty())
        val failed = runCatching { compound(input(years = -1)) }.exceptionOrNull()
        assertTrue(failed is IllegalArgumentException)
    }
}
