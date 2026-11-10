package dev.yernur.moneytracker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarGrowthTest {
    @Test
    fun theWholeAnimation_endsWithEveryGroupFullyGrown() {
        for (count in listOf(1, 2, 7, 12)) for (i in 0 until count) {
            assertEquals(0f, staggered(0f, i, count), 0f)
            assertEquals(1f, staggered(1f, i, count), 0.0001f)
        }
    }

    @Test
    fun laterGroupsStartLater() {
        val first = staggered(0.5f, 0, 6)
        val last = staggered(0.5f, 5, 6)
        assertTrue("first=$first last=$last", first > last)
    }
}
