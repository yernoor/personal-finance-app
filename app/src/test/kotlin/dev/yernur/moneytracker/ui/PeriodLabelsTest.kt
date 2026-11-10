package dev.yernur.moneytracker.ui

import dev.yernur.moneytracker.data.PeriodUnit
import dev.yernur.moneytracker.data.rangeOf
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodLabelsTest {
    private val today = LocalDate.of(2026, 11, 10)

    @Test
    fun theCurrentDay_saysToday_andAnyOtherDayKeepsItsDate() {
        assertEquals("Today", periodTitle(PeriodUnit.Day, PeriodUnit.Day.rangeOf(today), today))
        assertEquals("Mon, Nov 9, 2026", periodTitle(PeriodUnit.Day, PeriodUnit.Day.rangeOf(today.minusDays(1)), today))
        assertEquals("Wed, Nov 11, 2026", periodTitle(PeriodUnit.Day, PeriodUnit.Day.rangeOf(today.plusDays(1)), today))
    }

    @Test
    fun longerPeriods_areNotRelabelled() {
        assertEquals("November 2026", periodTitle(PeriodUnit.Month, PeriodUnit.Month.rangeOf(today), today))
        assertEquals("2026", periodTitle(PeriodUnit.Year, PeriodUnit.Year.rangeOf(today), today))
    }
}
