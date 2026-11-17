package dev.yernur.moneytracker.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The popup closes the menu on the same press that lands on the x; that press must still count as "close", not "add". */
@RunWith(RobolectricTestRunner::class)
class AddMenuStateTest {
    @Test
    fun aPressRightAfterTheMenuClosedItself_countsAsOnTheOpenMenu() {
        val menu = AddMenuState().apply { open = true }
        menu.close()
        val now = android.os.SystemClock.uptimeMillis()
        assertTrue(menu.wasOpenAt(now)) // the same touch
        assertFalse(menu.wasOpenAt(now + 1_000)) // a later, separate tap adds again
    }

    @Test
    fun aPressWhileOpen_isOnTheOpenMenu() = assertTrue(AddMenuState().apply { open = true }.wasOpenAt(0))
}
