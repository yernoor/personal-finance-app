package dev.yernur.moneytracker.data.wealth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class GoalsTest {
    private val today = LocalDate.of(2026, 11, 4)

    @Test
    fun withoutReturn_requiredMonthlyIsTheGapSpreadEvenly() {
        val progress = goalProgress(100_000, 1_000_000, today.plusMonths(9), today)
        assertEquals(100_000L, progress.requiredMonthly)
        assertEquals(9, progress.monthsLeft)
        assertEquals(0.1, progress.fraction, 1e-9)
        assertFalse(progress.reached)
    }

    @Test
    fun requiredMonthly_roundsUpSoTheGoalIsNotMissedByACent() {
        assertEquals(34L, goalProgress(0, 100, today.plusMonths(3), today).requiredMonthly) // 33.33.. -> 34
    }

    @Test
    fun withAReturn_requiredMonthlyInvertsTheCompoundEngine() {
        // 100.00 a month for 12 months at 12% (1% a month) grows to 1268.25 (see CompoundTest)
        val progress = goalProgress(0, 126_825, today.plusMonths(12), today, BigDecimal("12"))
        assertEquals(10_000L, progress.requiredMonthly)
        // the money already there grows past the target on its own
        assertEquals(0L, goalProgress(1_000_000, 1_100_000, today.plusMonths(12), today, BigDecimal("12")).requiredMonthly)
    }

    @Test
    fun reached_pastDeadlineAndNoDeadline_haveNoRequiredAmount() {
        val reached = goalProgress(1_200_000, 1_000_000, today.plusMonths(3), today)
        assertTrue(reached.reached)
        assertEquals(1.0, reached.fraction, 1e-9)
        assertNull(reached.requiredMonthly)
        val late = goalProgress(0, 1_000_000, today.minusMonths(1), today)
        assertEquals(0, late.monthsLeft)
        assertNull(late.requiredMonthly)
        assertNull(goalProgress(0, 1_000_000, null, today).monthsLeft)
        assertNull(goalProgress(0, 1_000_000, null, today).requiredMonthly)
    }
}
