package dev.yernur.moneytracker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.RateEntity
import dev.yernur.moneytracker.data.db.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LedgerRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository
    private var tick = 0

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = LedgerRepository(db, now = { "2026-10-25T00:00:%02d.000Z".format(tick++) }, newUid = { "uid${tick++}" })
        listOf("kzt" to "KZT", "kzt2" to "KZT", "usd" to "USD").forEach { (uid, cur) ->
            repo.saveAccount(AccountEntity(uid, uid, cur, null, null, null, null, false, true, false, false, "", ""))
        }
        db.categoryDao().insertAll(
            listOf(CategoryEntity("cafe", "cafe", TransactionType.Expense, null, null, 0, null, null, null, false, "", ""))
        )
        db.rateDao().insertAll(listOf(RateEntity("USD", "KZT", 450.0, "")))
    }

    @After
    fun tearDown() = db.close()

    private fun balances() = runBlocking { db.accountDao().observeBalances().first().associate { it.uid to it.balance } }

    private fun draft(account: String, amount: Long, type: TransactionType = TransactionType.Expense, uid: String? = null) =
        TransactionDraft(uid, type, account, "cafe", amount, "2026-10-25", " lunch ", emptySet())

    @Test
    fun usdExpense_getsDefaultCurrencyAmount_fromStoredRate() = runBlocking {
        repo.saveTransaction(draft("usd", 10_019)) // $100.19
        val saved = db.transactionDao().getAll().single()
        assertEquals(10_019L, saved.amountInAccountCurrency)
        assertEquals("USD", saved.accountCurrencyCode)
        assertEquals(4_508_550L, saved.amountInDefaultCurrency) // 100.19 * 450 = 45,085.50 KZT
        assertEquals("lunch".length + 2, saved.comment!!.length) // comment is kept as typed, only blank ones are dropped
    }

    @Test
    fun editKeepsCreated_bumpsModified_andDeleteIsATombstone() = runBlocking {
        repo.saveTransaction(draft("kzt", 1_000))
        val first = db.transactionDao().getAll().single()
        repo.saveTransaction(draft("kzt", 2_500, uid = first.uid))
        val edited = db.transactionDao().getAll().single()
        assertEquals(first.created, edited.created)
        assertEquals(true, edited.modified > first.modified)
        assertEquals(-2_500L, balances()["kzt"])

        repo.deleteTransaction(first.uid)
        assertEquals(true, db.transactionDao().getAll().single().isRemoved) // row kept for sync
        assertEquals(0L, balances()["kzt"])
    }

    @Test
    fun categoryUsageIsBumpedOnlyForNewEntries() = runBlocking {
        repo.saveTransaction(draft("kzt", 1_000))
        val uid = db.transactionDao().getAll().single().uid
        repo.saveTransaction(draft("kzt", 1_100, uid = uid))
        assertEquals(1, db.categoryDao().get("cafe")!!.useCount)
    }

    @Test
    fun balances_combineTransactionsAndTransfers() = runBlocking {
        repo.saveTransaction(draft("kzt", 100_000, TransactionType.Income))
        repo.saveTransaction(draft("kzt", 30_000))
        repo.saveTransfer(TransferDraft(null, "kzt", "kzt2", 5_000, null, "2026-10-25", null))
        repo.saveTransfer(TransferDraft(null, "kzt", "usd", 4_500_00, 1_000, "2026-10-25", null)) // 4,500.00 KZT -> $10.00
        val b = balances()
        assertEquals(100_000L - 30_000 - 5_000 - 450_000, b["kzt"])
        assertEquals(5_000L, b["kzt2"])
        assertEquals(1_000L, b["usd"])
    }

    @Test
    fun transfers_sameCurrencyLeavesReceivedSideEmpty_crossCurrencyNeedsIt() = runBlocking<Unit> {
        repo.saveTransfer(TransferDraft(null, "kzt", "kzt2", 5_000, 999, "2026-10-25", null))
        val same = db.transferDao().getAll().single()
        assertNull(same.toAmount)
        assertNull(same.toCurrencyCode)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.saveTransfer(TransferDraft(null, "kzt", "usd", 5_000, null, "2026-10-25", null)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.saveTransfer(TransferDraft(null, "kzt", "kzt", 5_000, null, "2026-10-25", null)) }
        }
    }

    @Test
    fun tagNamed_reusesExistingTagIgnoringCase() = runBlocking {
        val a = repo.tagNamed("Долг")
        val b = repo.tagNamed(" долг ")
        assertEquals(a.uid, b.uid)
    }
}
