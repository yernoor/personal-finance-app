package dev.yernur.moneytracker.ui

import dev.yernur.moneytracker.data.Bill
import dev.yernur.moneytracker.data.RecurringItem
import dev.yernur.moneytracker.data.db.CashflowKind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillSuggestionsTest {
    private val day = LocalDate.of(2026, 11, 19)
    private fun bill(title: String, amount: Long) =
        Bill(RecurringItem(title, amount, 3, day, day.plusMonths(1), null, null, false, null, false, "comment:${title.lowercase()}"), null, null, false)

    @Test
    fun billsAlreadyInMonthlyMoney_areNotSuggested_andAddedOnesCountAsLogged() {
        val internet = bill("Internet", 699_000)
        val gym = bill("Gym", 1_500_000)
        val have = listOf(bill(" internet ", 1).asCashflow())
        assertEquals(listOf(gym), billSuggestions(listOf(internet, gym), have))
        val added = gym.asCashflow()
        assertEquals(CashflowKind.Obligation, added.kind)
        assertEquals(1_500_000L, added.monthlyMinor)
        assertTrue("bills are already expenses in the app: not subtracted twice", added.inLedger)
    }
}
