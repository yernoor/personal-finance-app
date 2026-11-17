package dev.yernur.moneytracker.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.PeriodUnit
import dev.yernur.moneytracker.data.db.AppDatabase
import java.time.LocalDate
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Frames every 16 ms of test time, so animations run in a plain coroutine test. */
private object FrameClock : MonotonicFrameClock {
    private var now = 0L
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
        delay(16)
        now += 16_000_000
        return onFrame(now)
    }
}

/** A run of quick swipes or taps is one change of what is shown, applied once, and never past today. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SwipeStepsTest {
    @Test
    fun aBurstOfInputs_isAppliedTogether_andWaitedForOnce() {
        val scope = TestScope(StandardTestDispatcher() + FrameClock)
        val swipe = SwipeSteps(scope).apply { width = 1000f; reduced = true } // reduced: no frame clock needed for the fades
        var applied = 0
        var readyCalls = 0
        repeat(4) { swipe.go(-1, { applied++; true }, { readyCalls++ }) }
        scope.runCurrent()
        assertTrue(swipe.stepping)
        assertEquals(0, applied) // nothing changes before the content is out of sight
        scope.advanceUntilIdle()
        assertEquals(4, applied)
        assertEquals(1, readyCalls)
        assertFalse(swipe.stepping)
    }

    @Test
    fun historyStep_neverGoesPastTheCurrentPeriod() {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        val today = LocalDate.of(2026, 11, 17)
        val vm = HistoryViewModel(db, AppSettings(), today = { today }).also { it.zoom(PeriodUnit.Day) }
        assertTrue(vm.step(-1))
        assertTrue(vm.step(1)) // back to today
        assertFalse(vm.step(1)) // a second quick tap is checked against the period now, not the one last drawn
        assertEquals(today, vm.anchor.value)
    }
}
