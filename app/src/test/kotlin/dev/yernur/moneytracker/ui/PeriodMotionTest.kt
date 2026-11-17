package dev.yernur.moneytracker.ui

import dev.yernur.moneytracker.data.PeriodUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodMotionTest {
    @Test
    fun aBiggerUnitZoomsOut_aSmallerOneZoomsIn_everyPair() {
        val units = PeriodUnit.entries
        for (a in units) for (b in units) {
            val cue = periodCue(a, 100, b, 100)
            val expected = when { b.ordinal > a.ordinal -> PeriodCue.ZoomOut; b.ordinal < a.ordinal -> PeriodCue.ZoomIn; else -> PeriodCue.None }
            assertEquals("$a -> $b", expected, cue)
        }
    }

    @Test
    fun theSameUnitSlidesTheWayYouAreGoing() {
        assertEquals(PeriodCue.Earlier, periodCue(PeriodUnit.Month, 100, PeriodUnit.Month, 70))
        assertEquals(PeriodCue.Later, periodCue(PeriodUnit.Month, 70, PeriodUnit.Month, 100))
    }

    @Test
    fun theCueIsHandedOutOnce_andOnlyForAPeriodChange() {
        val tracker = CueTracker(PeriodUnit.Day, 10)
        tracker.update(PeriodUnit.Day, 10)
        assertEquals(PeriodCue.None, tracker.consume()) // a data edit is not a period change
        tracker.update(PeriodUnit.Week, 10)
        assertEquals(PeriodCue.ZoomOut, tracker.consume())
        assertEquals(PeriodCue.None, tracker.consume())
    }
}
