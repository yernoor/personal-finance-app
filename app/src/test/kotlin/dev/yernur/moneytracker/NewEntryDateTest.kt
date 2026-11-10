package dev.yernur.moneytracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.data.PeriodUnit
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.rangeOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class NewEntryDateTest {
    private val today = LocalDate.of(2026, 11, 10)

    private fun container(): AppContainer =
        AppContainer(Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build())

    @Test
    fun defaultsToToday_untilHistoryIsOpened() = assertEquals(today, container().newEntryDate(today))

    @Test
    fun followsTheDayShownInHistory() {
        val c = container()
        c.historyRange.value = PeriodUnit.Day.rangeOf(today.minusDays(1))
        assertEquals(today.minusDays(1), c.newEntryDate(today))
    }

    @Test
    fun usesToday_whenTheShownPeriodContainsIt_otherwiseItsLastDay() {
        val c = container()
        c.historyRange.value = PeriodUnit.Month.rangeOf(today)
        assertEquals(today, c.newEntryDate(today))
        c.historyRange.value = PeriodUnit.Month.rangeOf(LocalDate.of(2026, 9, 5))
        assertEquals(LocalDate.of(2026, 9, 30), c.newEntryDate(today))
    }
}
