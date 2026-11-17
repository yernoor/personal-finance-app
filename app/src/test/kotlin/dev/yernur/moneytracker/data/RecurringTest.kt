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
    fun aCategoryPaidOnceAMonth_isRecurring_evenWithoutComments() {
        val rows = listOf(tx("2026-08-05", null, 7_000_000, category = "inst"), tx("2026-09-05", null, 7_000_000, category = "inst"), tx("2026-10-05", "", 7_000_000, category = "inst"))
        val item = detectRecurring(rows, emptyList(), today, mapOf("inst" to "Installment")).single()
        assertEquals("Installment", item.title)
        assertEquals(7_000_000L, item.amount)
        assertEquals("inst", item.categoryUid)
        assertTrue(item.byCategory)
    }

    @Test
    fun aCategoryWithManyPaymentsAMonth_isNotABill() {
        // Groceries: steady monthly total, but 8 payments a month
        val rows = listOf("08", "09", "10").flatMap { m -> (1..8).map { d -> tx("2026-$m-0$d", null, 1_000_000, category = "food") } }
        assertTrue(detectRecurring(rows, emptyList(), today, mapOf("food" to "Groceries")).isEmpty())
    }

    @Test
    fun aMixedCategory_isNotCountedTwice_whenItsPartsAreSimilar() {
        // rent, internet and utility, about 100,000 each, all commented, all in Utilities: three items, not a fourth for the category
        val rows = listOf("08", "09", "10").flatMap { m ->
            listOf(tx("2026-$m-01", "Rent", 10_000_000, category = "util"), tx("2026-$m-05", "Internet", 9_500_000, category = "util"), tx("2026-$m-10", "Utility", 10_500_000, category = "util"))
        }
        val items = detectRecurring(rows, emptyList(), today, mapOf("util" to "Utilities"))
        assertEquals(setOf("Rent", "Internet", "Utility"), items.map { it.title }.toSet())
        assertEquals(30_000_000L, items.sumOf { it.amount })
    }

    @Test
    fun aCommentItem_isNotListedAgainForItsCategory() {
        val rows = listOf("08", "09", "10").map { m -> tx("2026-$m-01", "Rent", 30_000_000, category = "home") }
        val items = detectRecurring(rows, emptyList(), today, mapOf("home" to "House"))
        assertEquals(listOf("Rent"), items.map { it.title })
        assertEquals("home", items.single().categoryUid) // so the screen can offer "make it a fixed bill"
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
