package dev.yernur.moneytracker.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.PeriodUnit
import dev.yernur.moneytracker.data.db.AppDatabase
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** History left open overnight moves on to the new day, unless a past period was picked. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HistoryDayRolloverTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private var now = monday
    private val days = MutableStateFlow(monday)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(): HistoryViewModel {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        return HistoryViewModel(db, AppSettings(), today = { now }, days = days).also { it.zoom(PeriodUnit.Day) }
    }

    private fun nextDay() {
        now = now.plusDays(1)
        days.value = now
    }

    @Test
    fun todayMovesOnToTheNewDay() {
        val vm = vm()
        nextDay()
        assertEquals(monday.plusDays(1), vm.anchor.value)
    }

    @Test
    fun aPickedPastDayStays() {
        val vm = vm()
        vm.shift(-3)
        nextDay()
        assertEquals(monday.minusDays(3), vm.anchor.value)
    }
}
