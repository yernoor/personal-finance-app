package dev.yernur.moneytracker.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RollingMoneyTest {
    @Test
    fun atTheEnd_itIsExactlyTheNewValue_evenForBigBalances() {
        // 50,830,000 minor units is already past where a Float counts every unit
        assertEquals(50_830_000L - 450_000L, rolled(50_830_000L, 50_380_000L, 1f))
        assertEquals(999_999_999_999L, rolled(0L, 999_999_999_999L, 1f))
    }

    @Test
    fun atTheStart_itIsTheOldValue_andInTheMiddleItIsBetween() {
        assertEquals(50_830_000L, rolled(50_830_000L, 50_380_000L, 0f))
        assertEquals(50_605_000L, rolled(50_830_000L, 50_380_000L, 0.5f))
    }

    @Test
    fun itCountsDownAsWellAsUp_andNegativeBalancesWork() {
        assertEquals(-250L, rolled(-500L, 0L, 0.5f))
        assertEquals(750L, rolled(1_000L, 500L, 0.5f))
    }
}
