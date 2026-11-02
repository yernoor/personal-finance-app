package dev.yernur.moneytracker.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartCollapseTest {
    private val drag = NestedScrollSource.UserInput

    @Test
    fun scrollingTheListUp_shrinksTheChartFirst_andTakesOnlyWhatItNeeds() {
        val collapse = ChartCollapse(rangePx = 300f)
        assertEquals(-100f, collapse.onPreScroll(Offset(0f, -100f), drag).y, 0.001f)
        assertEquals(1f / 3f, collapse.fraction, 0.001f)

        // a big flick: the chart takes the 200 px it has left, the list gets the rest
        assertEquals(-200f, collapse.onPreScroll(Offset(0f, -500f), drag).y, 0.001f)
        assertEquals(1f, collapse.fraction, 0.001f)
        assertEquals(0f, collapse.onPreScroll(Offset(0f, -50f), drag).y, 0.001f) // fully collapsed: all of it goes to the list
    }

    @Test
    fun scrollingDown_letsTheListMoveFirst_andOpensTheChartOnlyWithWhatTheListCouldNotTake() {
        val collapse = ChartCollapse(rangePx = 300f)
        collapse.onPreScroll(Offset(0f, -300f), drag)
        assertEquals(1f, collapse.fraction, 0.001f)

        assertEquals(0f, collapse.onPreScroll(Offset(0f, 120f), drag).y, 0.001f) // the list scrolls down first
        assertEquals(1f, collapse.fraction, 0.001f)

        assertEquals(150f, collapse.onPostScroll(Offset.Zero, Offset(0f, 150f), drag).y, 0.001f) // list is at the top: 150 px left over
        assertEquals(0.5f, collapse.fraction, 0.001f)
        assertEquals(150f, collapse.onPostScroll(Offset.Zero, Offset(0f, 400f), drag).y, 0.001f) // only the 150 px the chart still needed
        assertEquals(0f, collapse.fraction, 0.001f)
    }

    @Test
    fun withNoRoomToCollapse_itDoesNothing() {
        val collapse = ChartCollapse(rangePx = 0f)
        assertEquals(Offset.Zero, collapse.onPreScroll(Offset(0f, -100f), drag))
        assertEquals(Offset.Zero, collapse.onPostScroll(Offset.Zero, Offset(0f, 100f), drag))
    }

    /** Animations need a frame clock; this one just steps 16 ms per frame. */
    private class StepClock : MonotonicFrameClock {
        private var now = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            now += 16_000_000L
            return onFrame(now)
        }
    }

    private fun settle(startPx: Float, velocity: Float): Float {
        val collapse = ChartCollapse(rangePx = 300f)
        collapse.onPreScroll(Offset(0f, -startPx), drag)
        runBlocking(StepClock()) { collapse.onPreFling(Velocity(0f, velocity)) }
        return collapse.fraction
    }

    @Test
    fun letGoHalfway_theChartSettlesOnTheRingOrTheBar() {
        assertEquals(1f, settle(startPx = 60f, velocity = -2_000f), 0.001f) // a quick flick up finishes the collapse
        assertEquals(0f, settle(startPx = 240f, velocity = 2_000f), 0.001f) // a quick flick down opens it again
        assertEquals(1f, settle(startPx = 200f, velocity = 0f), 0.001f) // slow release past the middle: the bar
        assertEquals(0f, settle(startPx = 100f, velocity = 0f), 0.001f) // slow release before the middle: the ring
        assertEquals(0f, settle(startPx = 0f, velocity = -2_000f), 0.001f) // untouched stays open: the list's own fling does the rest
    }
}
