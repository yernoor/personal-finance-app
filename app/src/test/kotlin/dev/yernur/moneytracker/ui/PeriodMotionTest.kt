package dev.yernur.moneytracker.ui

import dev.yernur.moneytracker.data.PeriodUnit
import androidx.compose.ui.graphics.Color
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

    private fun shape(vararg parts: Pair<String, Float>) = chartShape(parts.map { (t, f) -> ChartSlice(t, null, (f * 1000).toLong(), f) }, false)

    @Test
    fun aRingMorph_keepsEachCategoryAndEndsExactlyOnTheTarget() {
        val a = shape("Food" to 0.75f, "Home" to 0.25f)
        val b = shape("Home" to 0.5f, "Cafe" to 0.5f)
        assertEquals(1f, blendShape(a, b, 0.4f).spans.sumOf { it.width.toDouble() }.toFloat(), 0.001f) // always a whole ring
        val done = blendShape(a, b, 1f).spans.filter { it.width > 0f }
        assertEquals(b.spans.map { it.key to it.width }.toSet(), done.map { it.key to it.width }.toSet()) // ends exactly on the target
        val mid = blendShape(a, b, 0.5f)
        assertEquals(0.375f, mid.spans.first { it.key.startsWith("Home") }.width, 0.001f) // Home grew from 0.25 to 0.5
        assertEquals(0.375f, mid.spans.first { it.key.startsWith("Food") }.width, 0.001f) // Food shrinks away
    }

    @Test
    fun barsMorphFromTheNewestEnd_andSlotsSqueezeOut() {
        val red = Color.Red
        fun slot(v: Long, label: String) = BarSlot(1f, label, listOf(listOf(BarSegment(v, red))))
        val week = BarShape(listOf(slot(100, "w4"), slot(200, "w3"), slot(300, "w2"), slot(400, "w1")), 0)
        val month = BarShape(listOf(slot(1000, "Nov"), slot(2000, "Oct")), 0)
        val half = blendBars(week, month, 0.5f)
        assertEquals(4, half.slots.size)
        assertEquals(1f, half.slots[0].weight, 0.001f) // the newest slot stays whole
        assertEquals(0.5f, half.slots[3].weight, 0.001f) // an extra slot is half squeezed out
        assertEquals(550L, half.slots[0].bars[0][0].value)
    }
}
