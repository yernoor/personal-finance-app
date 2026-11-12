package dev.yernur.moneytracker.widget

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuickAddKindsTest {
    private val prefs = ApplicationProvider.getApplicationContext<Application>().getSharedPreferences("quickadd-test", 0)

    @Test
    fun eachWidgetKeepsItsOwnKind_andAnUnsetOneIsAnExpense() {
        val kinds = QuickAddKinds(prefs)
        assertEquals(QuickAddKind.Expense, kinds.get(7))
        kinds.set(7, QuickAddKind.Income)
        kinds.set(8, QuickAddKind.Transfer)
        assertEquals(QuickAddKind.Income, QuickAddKinds(prefs).get(7)) // a new instance reads it back
        assertEquals(QuickAddKind.Transfer, kinds.get(8))
        kinds.remove(7)
        assertEquals(QuickAddKind.Expense, kinds.get(7))
        assertEquals(QuickAddKind.Transfer, kinds.get(8))
    }

    @Test
    fun kindIds_matchWhatTheAppRootExpects() {
        assertEquals(listOf("expense", "income", "transfer"), QuickAddKind.entries.map { it.id })
        assertNull(QuickAddKind.byId("bogus"))
    }
}
