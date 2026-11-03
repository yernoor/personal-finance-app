package dev.yernur.moneytracker.data.wealth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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

class NetWorthChangeTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val history = listOf(LocalDate.of(2026, 9, 1) to 100L, LocalDate.of(2026, 10, 3) to 150L, LocalDate.of(2026, 11, 1) to 190L)

    @Test
    fun comparesWithTheNewestSnapshotAtLeastThirtyDaysOld() {
        assertEquals((200L - 150L) to LocalDate.of(2026, 10, 3), netWorthChange(history, today, 200))
    }

    @Test
    fun fallsBackToTheOldestSnapshot_andIsNullWithNoEarlierOne() {
        assertEquals((200L - 190L) to LocalDate.of(2026, 11, 1), netWorthChange(history.takeLast(1), today, 200))
        assertNull(netWorthChange(listOf(today to 190L), today, 200)) // today's own snapshot is not a comparison
        assertNull(netWorthChange(emptyList(), today, 200))
    }
}
