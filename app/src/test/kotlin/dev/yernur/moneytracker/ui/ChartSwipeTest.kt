package dev.yernur.moneytracker.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Swiping on Charts walks the selection to the middle first, then slides the window under it. */
class ChartSwipeTest {
    @Test
    fun threeMonths_selectionMovesToTheMiddle_thenTheWindowSlides() {
        // Sep Oct [Nov] -> Sep [Oct] Nov: same window
        assertEquals(0 to 1, chartStep(page = 0, selected = 2, count = 3, by = -1))
        // Sep [Oct] Nov -> Aug [Sep] Oct: the window slides one month, the selection stays in the middle
        assertEquals(1 to 1, chartStep(page = 0, selected = 1, count = 3, by = -1))
        // and back: Aug [Sep] Oct -> Sep [Oct] Nov, then Sep Oct [Nov]
        assertEquals(0 to 1, chartStep(page = 1, selected = 1, count = 3, by = 1))
        assertEquals(0 to 2, chartStep(page = 0, selected = 1, count = 3, by = 1))
    }

    @Test
    fun cannotGoPastToday() = assertNull(chartStep(page = 0, selected = 2, count = 3, by = 1))

    @Test
    fun sevenDays_settleOnTheFourthBar() {
        var state = 0 to 6
        repeat(3) { state = chartStep(state.first, state.second, 7, -1)!! }
        assertEquals(0 to 3, state)
        assertEquals(1 to 3, chartStep(state.first, state.second, 7, -1))
    }

    @Test
    fun strip_keepsTheSharedBars_andAddsTheIncomingOne() {
        fun slot(label: String, v: Long) = BarSlot(1f, label, listOf(listOf(BarSegment(v, Color.Red))))
        val now = BarShape(listOf(slot("Nov", 1), slot("Oct", 2), slot("Sep", 3)), 1)
        val older = BarShape(listOf(slot("Oct", 2), slot("Sep", 3), slot("Aug", 9)), 1)
        val strip = barStrip(now, older, PeriodCue.Earlier)!!
        assertEquals(listOf("Nov", "Oct", "Sep", "Aug"), strip.slots.map { it.label })
        assertEquals(3L to 9L, strip.fromTallest to strip.toTallest)
        assertEquals(listOf("Nov", "Oct", "Sep", "Aug"), barStrip(older, now, PeriodCue.Later)!!.slots.map { it.label })
        assertNull(barStrip(now, older, PeriodCue.ZoomOut))
    }
}
