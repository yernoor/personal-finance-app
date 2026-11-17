package dev.yernur.moneytracker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.BillEntity
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.ProjectionMode
import dev.yernur.moneytracker.data.db.TransactionEntity
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.TxFull
import dev.yernur.moneytracker.data.db.TxLite
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Recurring bills with the owner's settings: ends, reminders, moving payments out of a shared category. */
@RunWith(RobolectricTestRunner::class)
class BillsTest {
    private val today = LocalDate.of(2026, 11, 17)
    private var n = 0
    private fun tx(date: String, comment: String?, amount: Long, category: String) =
        TxFull("t${n++}", date, TransactionType.Expense, category, "main", comment, amount, amount, "2026-01-01T10:00:00.000Z")
    private fun category(uid: String, title: String, mode: ProjectionMode = ProjectionMode.Auto) =
        CategoryEntity(uid, title, TransactionType.Expense, null, null, 0, null, null, null, false, "", "", false, null, mode)

    // Rent and Internet, both in the Rent category; Internet commented, rent not.
    private val rows = listOf("09", "10", "11").flatMap { m ->
        listOf(tx("2026-$m-01", null, 30_000_000, "rent"), tx("2026-$m-06", "Internet", 1_000_000, "rent"))
    }
    private val categories = listOf(category("rent", "Rent"))

    @Test
    fun aBillInsideACategoryWithOtherSpending_isOfferedAMove() {
        val internet = bills(rows, emptyList(), categories, emptyList(), today).single()
        assertEquals("comment:internet", internet.key)
        assertTrue(internet.sharesCategory)
    }

    @Test
    fun anEndedBill_leavesTheList_andAnEndThisMonthShowsProgress() {
        fun end(month: String) = BillEntity("comment:internet", "Internet", "rent", 1_000_000, endMonth = month)
        assertTrue(bills(rows, emptyList(), categories, listOf(end("2026-10")), today).isEmpty()) // last payment in October: done
        val ending = bills(rows, emptyList(), categories, listOf(end("2027-02")), today).single()
        assertEquals(3 to 6, ending.progress) // 3 seen, then Dec, Jan, Feb
    }

    @Test
    fun theReminder_goesOffDaysBeforeTheNextPayment_andNotWhilePaused() {
        val set = BillEntity("comment:internet", "Internet", "rent", 1_000_000, reminderOn = true, reminderDaysBefore = 1, reminderMinute = 9 * 60)
        val bill = bills(rows, emptyList(), categories, listOf(set), today).single()
        assertEquals(LocalDateTime.of(2026, 12, 5, 9, 0), bill.reminderAt) // next payment about Dec 6
        val paused = bills(rows, emptyList(), categories, listOf(set.copy(reminderPaused = true)), today).single()
        assertNull(paused.reminderAt)
        assertTrue(paused.reminderPaused)
    }

    @Test
    fun theReminderJob_postsWhatIsDue_once_andBooksTheNext() {
        val set = BillEntity("comment:internet", "Internet", "rent", 1_000_000, reminderOn = true, reminderDaysBefore = 1, reminderMinute = 9 * 60)
        val bill = bills(rows, emptyList(), categories, listOf(set), today).single()
        assertEquals(LocalDateTime.of(2026, 12, 5, 9, 0), BillReminderWorker.next(listOf(bill), LocalDateTime.of(2026, 12, 1, 12, 0)))
        assertEquals(listOf(bill), BillReminderWorker.dueNow(listOf(bill), LocalDateTime.of(2026, 12, 5, 9, 3)))
        val posted = bills(rows, emptyList(), categories, listOf(set.copy(lastNotified = "2026-12-05")), today).single()
        assertTrue(BillReminderWorker.dueNow(listOf(posted), LocalDateTime.of(2026, 12, 5, 9, 30)).isEmpty())
        assertTrue(BillReminderWorker.dueNow(listOf(bill), LocalDateTime.of(2026, 12, 6, 9, 0)).isEmpty()) // a day late: skipped
        assertEquals("Internet is due tomorrow", BillReminderWorker.dueTitle(bill, LocalDate.of(2026, 12, 5)))
    }

    @Test
    fun movingOffers_theSameComment_andUncommentedLookAlikes_only() {
        val item = bills(rows, emptyList(), categories, emptyList(), today).single().item
        val inRent = rows + listOf(
            tx("2026-08-05", null, 950_000, "rent"), // no comment, similar amount, day 5 vs 6: offered as a guess
            tx("2026-07-20", null, 1_000_000, "rent"), // similar amount, wrong day: not offered
            tx("2026-06-06", "internet ", 1_000_000, "rent"), // the same comment, any spelling: offered
        )
        val found = moveCandidates(item, inRent)
        assertEquals(4, found.sameComment.size)
        assertEquals(listOf("2026-08-05"), found.lookAlike.map { it.date })
    }

    @Test
    fun aBillEndedLastMonth_comesOffItsFixedCategorysFigure() {
        val ended = listOf(BillEntity("comment:internet", "Internet", "rent", 1_000_000, endMonth = "2026-10"))
        assertEquals(mapOf("rent" to 1_000_000L), endedLastMonth(ended, today))
        val lite = rows.map { TxLite(it.date, it.type, it.categoryUid, it.amount) }.filter { it.date < "2026-11" }
        val f = forecast(lite, listOf(category("rent", "Rent", ProjectionMode.Fixed)), today, LocalDate.of(2026, 9, 1), 100, endedLastMonth(ended, today))
        assertEquals(30_000_000L, f.categories.single().runRate) // October's 31,000,000 without the internet that ended
    }

    @Test
    fun settlingAnEnd_stopsTheReminder_andPutsAWholeCategoryBillBackToAutomatic() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        val ledger = LedgerRepository(db)
        db.categoryDao().upsert(category("inst", "Installment", ProjectionMode.Fixed))
        db.billDao().upsert(BillEntity("category:inst", "Installment", "inst", 7_000_000, reminderOn = true, endMonth = "2026-10"))
        var booked = 0
        BillRepository(db, ledger, flowOf(today)) { booked++ }.settleEnded(today)
        assertEquals(0, booked) // the reminder job calls this: re-booking from inside would cancel the running job
        assertEquals(ProjectionMode.Auto, db.categoryDao().get("inst")!!.projectionMode)
        val bill = db.billDao().get("category:inst")!!
        assertFalse(bill.reminderOn)
        assertTrue(bill.settled)
    }

    @Test
    fun twoQuickTogglesOnFixed_fromTheSameStaleCard_endWhereTheyStarted() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        db.categoryDao().upsert(category("rent", "Rent"))
        val repo = BillRepository(db, LedgerRepository(db), flowOf(today))
        // Both taps come from the card as it was drawn (not fixed yet), at once: the second must undo the first, not repeat it.
        val stale = bills(rows, emptyList(), listOf(db.categoryDao().get("rent")!!), emptyList(), today).single()
        kotlinx.coroutines.coroutineScope {
            repeat(2) { launch(kotlinx.coroutines.Dispatchers.Default) { repo.toggleFixed(stale) } }
        }
        assertEquals(ProjectionMode.Auto, db.categoryDao().get("rent")!!.projectionMode)
        repo.toggleFixed(stale)
        assertEquals(ProjectionMode.Fixed, db.categoryDao().get("rent")!!.projectionMode)
    }

    @Test
    fun moving_andUndo_changeOnlyTheChosenPayments() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        db.accountDao().upsert(AccountEntity("main", "Main", "KZT", "cash", null, 0, null, false, true, false, false, "", ""))
        listOf(category("rent", "Rent"), category("net", "Internet")).forEach { db.categoryDao().upsert(it) }
        fun entity(uid: String, comment: String?) = TransactionEntity(uid, TransactionType.Expense, "main", "rent", 1_000_000, null, null, 1_000_000, "KZT", "2026-11-06", comment, "c", "c")
        db.transactionDao().upsert(entity("a", "Internet"))
        db.transactionDao().upsert(entity("b", null))
        db.transactionDao().upsert(entity("rent", "Rent"))
        val repo = BillRepository(db, LedgerRepository(db), flowOf(today))
        val bill = Bill(RecurringItem("Internet", 1_000_000, 3, today, today.plusMonths(1), null, null, false, "rent", false, "comment:internet"), null, null, true)
        repo.move(bill, listOf("a", "b"), "net", "Internet", addComment = true)
        assertEquals("net", db.transactionDao().get("b")!!.categoryUid)
        assertEquals("Internet", db.transactionDao().get("b")!!.comment) // the uncommented one got the name
        assertEquals("rent", db.transactionDao().get("rent")!!.categoryUid) // not chosen: stays
        assertEquals("Moved 2 payments to Internet", repo.lastMove.value!!.message)
        repo.undo(repo.lastMove.value!!)
        assertEquals("rent", db.transactionDao().get("b")!!.categoryUid)
        assertNull(db.transactionDao().get("b")!!.comment)
        assertNull(repo.lastMove.value)
    }
}
