package dev.yernur.moneytracker.data.demo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.TransactionType
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DemoDataTest {
    private lateinit var db: AppDatabase
    private val today = LocalDate.of(2026, 11, 13)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun fillsAnEmptyDatabase_withSpendingIncomeAndTransfersBothWays() = runBlocking {
        DemoData.seedIfEmpty(db, today)
        assertEquals(4, db.accountDao().observeAll().first().size)
        val all = db.transactionDao().observeBetween("2026-07-01", today.toString()).first()
        assertTrue(all.count { it.type == TransactionType.Expense } > 100)
        assertTrue(all.any { it.categoryUid == "demo-salary" })
        assertTrue(all.none { it.date > today.toString() })
        val transfers = db.transferDao().observeBetween("2026-11-01", today.toString()).first()
        // November so far: main -> savings on the 11th; the way back comes on the 20th, so look at October for both directions.
        val october = db.transferDao().observeBetween("2026-10-01", "2026-10-31").first()
        assertTrue(october.any { it.fromAccountUid == "main" && it.toAccountUid == "demo-savings" })
        assertTrue(october.any { it.fromAccountUid == "demo-savings" && it.toAccountUid == "main" })
        assertTrue(october.any { it.fromCurrencyCode == "USD" && it.toCurrencyCode == "KZT" && it.toAmount == 2_400_000L })
        assertTrue(transfers.isNotEmpty())
        // Balances stay positive: the demo should look like a normal month, not an overdraft.
        db.accountDao().observeBalances().first().forEach { assertTrue("${it.uid} = ${it.balance}", it.balance > 0) }
    }

    @Test
    fun addsBondsOtherAssetsAndMonthlyIncome() = runBlocking {
        DemoData.seedIfEmpty(db, today)
        assertEquals(2, db.bondDao().getLive().size)
        assertEquals(2, db.manualAssetDao().getLive().size)
        assertEquals(840_000_00L, db.cashflowItemDao().getLive().single { it.title == "Salary" }.monthlyMinor)
    }

    @Test
    fun leavesAUsedDemoDatabaseAlone() = runBlocking {
        DemoData.seedIfEmpty(db, today)
        val before = db.transactionDao().observeBetween("2000-01-01", today.toString()).first().size
        DemoData.seedIfEmpty(db, today)
        assertEquals(before, db.transactionDao().observeBetween("2000-01-01", today.toString()).first().size)
    }
}
