package dev.yernur.moneytracker.data.wealth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CompoundTest {
    private fun input(start: Long = 0, rate: String = "0", monthly: Long = 0, years: Int = 1, n: Int = 12, inflation: String = "0") =
        CompoundInput(start, BigDecimal(rate), monthly, years, n, BigDecimal(inflation))

    @Test
    fun lumpSum_monthlyCompounding_matchesHandComputedValue() {
        // 1000.00 * 1.01^12 = 1126.8250301...
        val year = compound(input(start = 100_000, rate = "12")).single()
        assertEquals(112_683L, year.balance)
        assertEquals(12_683L, year.interest)
        assertEquals(100_000L, year.contributed)
    }

    @Test
    fun monthlyContributions_matchTheAnnuityFormula() {
        // 100.00 a month at 1% a month: 100 * (1.01^12 - 1) / 0.01 = 1268.25
        val year = compound(input(monthly = 10_000, rate = "12")).single()
        assertEquals(126_825L, year.balance)
        assertEquals(120_000L, year.contributed)
        assertEquals(6_825L, year.interest)
    }

    @Test
    fun zeroRate_isJustSavings_withNoInterest() {
        val rows = compound(input(start = 500_000, monthly = 20_000, years = 3))
        assertEquals(500_000L + 20_000L * 36, rows.last().balance)
        assertTrue(rows.all { it.interest == 0L })
    }

    @Test
    fun inflation_deflatesTheRealBalance() {
        // 1000.00 / 1.03 = 970.87
        assertEquals(97_087L, compound(input(start = 100_000, inflation = "3")).single().realBalance)
    }

    @Test
    fun yearlyCompounding_growsByOneStepPerYear() {
        // 10% yearly on 1000.00: 1100.00, 1210.00 (a Double power may be off by a cent; allow 1)
        val rows = compound(input(start = 100_000, rate = "10", years = 2, n = 1))
        assertEquals(110_000.0, rows[0].balance.toDouble(), 1.0)
        assertEquals(121_000.0, rows[1].balance.toDouble(), 1.0)
    }

    @Test
    fun zeroYears_returnsNoRows_andNegativeYearsAreRejected() {
        assertTrue(compound(input(years = 0)).isEmpty())
        val failed = runCatching { compound(input(years = -1)) }.exceptionOrNull()
        assertTrue(failed is IllegalArgumentException)
    }
}
