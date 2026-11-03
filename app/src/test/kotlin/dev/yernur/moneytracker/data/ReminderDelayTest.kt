package dev.yernur.moneytracker.data

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderDelayTest {
    private val eight = 20 * 60

    @Test
    fun laterToday_whenTheTimeHasNotPassed() =
        assertEquals(3 * 3_600_000L, ReminderWorker.delayMillis(LocalDateTime.of(2026, 11, 3, 17, 0), eight))

    @Test
    fun tomorrow_whenTheTimeHasPassed_orIsNow() {
        assertEquals(23 * 3_600_000L, ReminderWorker.delayMillis(LocalDateTime.of(2026, 11, 3, 21, 0), eight))
        assertEquals(24 * 3_600_000L, ReminderWorker.delayMillis(LocalDateTime.of(2026, 11, 3, 20, 0), eight))
    }
}
