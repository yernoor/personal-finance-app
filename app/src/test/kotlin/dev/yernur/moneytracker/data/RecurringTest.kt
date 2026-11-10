package dev.yernur.moneytracker.data

import dev.yernur.moneytracker.data.db.ReminderEntity
import dev.yernur.moneytracker.data.db.ReminderKind
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.TxFull
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringTest {
    private val today = LocalDate.of(2026, 10, 10)

    private var counter = 0
    private fun tx(date: String, comment: String?, amount: Long, created: String = "2026-01-01T10:00:00.000Z", account: String = "main", category: String? = "c", type: TransactionType = TransactionType.Expense) =
        TxFull("u${counter++}", date, type, category, account, comment, amount, amount, created)

    private fun reminder(title: String) = ReminderEntity("r", ReminderKind.RegularPayments, title, null, "Month", TransactionType.Expense, "2026-01-01", null, "10:00", true, 1, false, null, "", "")

    @Test
    fun threeMonthsInARow_isRecurring_withTheNextDateAMonthAfterTheLast() {
        val rows = listOf(tx("2026-08-14", "Gym", 1_500_000), tx("2026-09-14", " gym", 1_500_000), tx("2026-10-02", "GYM", 1_500_000))
        val item = detectRecurring(rows, emptyList(), today).single()
        assertEquals("GYM", item.title) // the latest spelling
        assertEquals(3, item.months)
        assertEquals(LocalDate.of(2026, 11, 2), item.next)
        assertNull(item.priceChangePercent)
    }

    @Test
    fun aGap_aShortRun_orAStaleRun_isNotRecurring() {
        assertTrue(detectRecurring(listOf(tx("2026-07-14", "Gym", 100), tx("2026-09-14", "Gym", 100), tx("2026-10-02", "Gym", 100)), emptyList(), today).isEmpty())
        assertTrue(detectRecurring(listOf(tx("2026-09-14", "Gym", 100), tx("2026-10-02", "Gym", 100)), emptyList(), today).isEmpty())
        // last seen in July: no longer running
        assertTrue(detectRecurring(listOf(tx("2026-05-14", "Gym", 100), tx("2026-06-14", "Gym", 100), tx("2026-07-14", "Gym", 100)), emptyList(), today).isEmpty())
    }

    @Test
    fun runEndingLastMonth_stillCounts_untilThisMonthEnds() {
        val rows = listOf(tx("2026-07-14", "Gym", 100), tx("2026-08-14", "Gym", 100), tx("2026-09-14", "Gym", 100))
        assertEquals(1, detectRecurring(rows, emptyList(), today).size)
    }

    @Test
    fun amountsFarFromTheMedian_areNotRecurring() {
        val spread = listOf(tx("2026-08-14", "Taxi", 100_000), tx("2026-09-14", "Taxi", 100_000), tx("2026-10-02", "Taxi", 130_000)) // 30% off
        assertTrue(detectRecurring(spread, emptyList(), today).isEmpty())
        val near = listOf(tx("2026-08-14", "Taxi", 100_000), tx("2026-09-14", "Taxi", 100_000), tx("2026-10-02", "Taxi", 119_000)) // 19% off
        assertEquals(1, detectRecurring(near, emptyList(), today).size)
    }

    @Test
    fun priceChange_isReportedFromFivePercent() {
        val up = listOf(tx("2026-08-14", "Gym", 1_500_000), tx("2026-09-14", "Gym", 1_500_000), tx("2026-10-02", "Gym", 1_700_000))
        val item = detectRecurring(up, emptyList(), today).single()
        assertEquals(13, item.priceChangePercent) // 200,000 / 1,500,000 = 13.3%
        assertEquals(1_500_000L, item.previousAmount)
        val tiny = listOf(tx("2026-08-14", "Gym", 1_000_000), tx("2026-09-14", "Gym", 1_000_000), tx("2026-10-02", "Gym", 1_040_000))
        assertNull(detectRecurring(tiny, emptyList(), today).single().priceChangePercent)
    }

    @Test
    fun reminderWithTheSameTitle_isRecognised_andBlankCommentsAndIncomeAreIgnored() {
        val rows = listOf(tx("2026-08-14", "Gym", 100), tx("2026-09-14", "Gym", 100), tx("2026-10-02", "Gym", 100)) +
            listOf(tx("2026-08-14", null, 100), tx("2026-09-14", null, 100), tx("2026-10-02", null, 100)) +
            listOf(tx("2026-08-14", "Pay", 100, type = TransactionType.Income), tx("2026-09-14", "Pay", 100, type = TransactionType.Income), tx("2026-10-02", "Pay", 100, type = TransactionType.Income))
        val item = detectRecurring(rows, listOf(reminder("gym")), today).single()
        assertTrue(item.hasReminder)
    }

    @Test
    fun duplicates_needTheSameDayAccountCategoryAmount_createdWithinTenMinutes() {
        val a = tx("2026-11-07", "x", 12_300, created = "2026-11-07T19:42:00.000Z")
        val b = tx("2026-11-07", "x", 12_300, created = "2026-11-07T19:46:00.000Z")
        assertEquals(listOf(DuplicatePair(a, b)), findDuplicates(listOf(b, a)))

        assertTrue(findDuplicates(listOf(a, tx("2026-11-07", "x", 12_300, created = "2026-11-07T19:53:00.000Z"))).isEmpty()) // 11 minutes
        assertTrue(findDuplicates(listOf(a, tx("2026-11-07", "x", 12_301, created = "2026-11-07T19:43:00.000Z"))).isEmpty()) // other amount
        assertTrue(findDuplicates(listOf(a, tx("2026-11-07", "x", 12_300, created = "2026-11-07T19:43:00.000Z", account = "deposit"))).isEmpty())
        assertTrue(findDuplicates(listOf(a, tx("2026-11-07", "x", 12_300, created = "2026-11-07T19:43:00.000Z", category = null))).isEmpty())
        assertTrue(findDuplicates(listOf(a, tx("2026-11-08", "x", 12_300, created = "2026-11-07T19:43:00.000Z"))).isEmpty()) // other date
    }

    @Test
    fun aKeptPair_isNotOfferedAgain() {
        val a = tx("2026-11-07", "x", 12_300, created = "2026-11-07T19:42:00.000Z")
        val b = tx("2026-11-07", "x", 12_300, created = "2026-11-07T19:46:00.000Z")
        assertTrue(findDuplicates(listOf(a, b), kept = setOf(b.uid)).isEmpty())
    }
}
