package dev.yernur.moneytracker.data.wealth

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.CashflowItemEntity
import dev.yernur.moneytracker.data.db.CashflowKind
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.TransactionEntity
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.TransferEntity
import dev.yernur.moneytracker.data.sheets.FakeTransport
import dev.yernur.moneytracker.data.sheets.ok
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class HealthTest {
    private val today = LocalDate.of(2026, 11, 4)
    private lateinit var db: AppDatabase
    private lateinit var wealth: WealthRepository

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        val feed = "<rates><item><title>USD</title><description>450</description><quant>1</quant></item></rates>"
        val fx = FxRepository(db, AppSettings(), NbkRatesClient(FakeTransport { ok(feed) }))
        fx.refreshDaily(today)
        wealth = WealthRepository(db, fx)
        db.accountDao().insertAll(listOf(AccountEntity("kzt", "kzt", "KZT", null, null, null, null, false, true, false, false, TS, TS)))
        db.categoryDao().insertAll(listOf(CategoryEntity("food", "food", TransactionType.Expense, null, null, 0, null, null, null, false, TS, TS)))
    }

    @After
    fun tearDown() = db.close()

    private fun tx(uid: String, date: String, type: TransactionType, amount: Long, category: String? = "food") =
        TransactionEntity(uid, type, "kzt", category, amount, null, null, amount, null, date, null, TS, TS)

    private fun item(title: String, kind: CashflowKind, monthly: Long, currency: String = "KZT", inLedger: Boolean = false) =
        CashflowItemEntity("", title, kind, currency, monthly, inLedger, "", "")

    @Test
    fun summary_subtractsSpendingAndObligations_andComputesTheRatios() {
        val h = healthSummary(incomeMinor = 100_000_000, obligationsMinor = 30_000_000, obligationsOutsideLedgerMinor = 30_000_000, averageSpendingMinor = 40_000_000, savingsMinor = 280_000_000)
        assertEquals(30_000_000L, h.availableMinor) // 1,000,000 - 400,000 - 300,000
        assertEquals(30.0, h.savingsRatePercent!!, 1e-9)
        assertEquals(30.0, h.obligationsSharePercent!!, 1e-9)
        assertEquals(4.0, h.emergencyMonths!!, 1e-9) // 2,800,000 / 700,000
    }

    @Test
    fun anObligationAlreadyInTheLedger_isNotSubtractedTwice() {
        val h = healthSummary(100_000_000, 30_000_000, obligationsOutsideLedgerMinor = 0, averageSpendingMinor = 70_000_000, savingsMinor = 0)
        assertEquals(30_000_000L, h.availableMinor)
        assertEquals(30.0, h.obligationsSharePercent!!, 1e-9) // still counts in the ratio
    }

    @Test
    fun withoutIncomeOrOutlay_theRatiosAreUnknown() {
        val h = healthSummary(0, 0, 0, null, 1_000)
        assertNull(h.savingsRatePercent)
        assertNull(h.obligationsSharePercent)
        assertNull(h.emergencyMonths)
        assertEquals(0L, h.availableMinor)
    }

    @Test
    fun averageSpending_usesFullMonthsOnly_andSkipsBalanceCorrections() = runBlocking {
        db.transactionDao().insertAll(
            listOf(
                tx("a", "2026-08-10", TransactionType.Expense, 300_000), // three full months back from November: Aug, Sep, Oct
                tx("b", "2026-09-10", TransactionType.Expense, 600_000),
                tx("c", "2026-10-10", TransactionType.Expense, 900_000),
                tx("d", "2026-10-11", TransactionType.Expense, 5_000_000, category = null), // overwrite balance, not spending
                tx("e", "2026-10-12", TransactionType.Income, 7_000_000),
                tx("f", "2026-11-02", TransactionType.Expense, 800_000), // the running month is not a full month
            ),
        )
        assertEquals(600_000L, wealth.averageMonthlySpending(today))
    }

    @Test
    fun averageSpending_isUnknownWithoutAnEarlierMonth() = runBlocking {
        db.transactionDao().insertAll(listOf(tx("a", "2026-11-02", TransactionType.Expense, 800_000)))
        assertNull(wealth.averageMonthlySpending(today))
    }

    @Test
    fun repositoryHealth_convertsItemsToTenge() = runBlocking {
        db.transactionDao().insertAll(listOf(tx("a", "2026-10-10", TransactionType.Expense, 4_000_000), tx("b", "2026-10-11", TransactionType.Income, 20_000_000)))
        wealth.saveCashflowItem(null, item("Salary", CashflowKind.Income, 100_000, currency = "USD")) // $1000.00 -> 450,000.00
        wealth.saveCashflowItem(null, item("Mortgage", CashflowKind.Obligation, 15_000_000))
        val h = wealth.health(today)
        assertEquals(45_000_000L, h.incomeMinor)
        assertEquals(15_000_000L, h.obligationsMinor)
        assertEquals(4_000_000L, h.averageSpendingMinor)
        assertEquals(26_000_000L, h.availableMinor) // 450,000 - 40,000 - 150,000 = 260,000.00
        val uid = db.cashflowItemDao().getLive().first { it.title == "Mortgage" }.uid
        wealth.deleteCashflowItem(uid)
        assertEquals(0L, wealth.health(today).obligationsMinor)
    }

    @Test
    fun transfersBetweenAccounts_areNeitherSpendingNorIncome_andLeaveNetWorthAlone() = runBlocking {
        db.accountDao().insertAll(listOf(AccountEntity("dep", "dep", "KZT", null, null, null, null, false, true, false, false, TS, TS)))
        db.transactionDao().insertAll(listOf(tx("a", "2026-10-10", TransactionType.Expense, 4_000_000), tx("b", "2026-10-11", TransactionType.Income, 20_000_000)))
        wealth.saveCashflowItem(null, item("Salary", CashflowKind.Income, 45_000_000))
        val spendingBefore = wealth.averageMonthlySpending(today)
        val healthBefore = wealth.health(today)
        val netWorthBefore = wealth.currentNetWorth().totalMinor

        // 100,000.00 moved into the deposit and 30,000.00 back, in the checked months and in the running one
        db.transferDao().insertAll(
            listOf(
                TransferEntity("t1", "kzt", "dep", 10_000_000, "KZT", null, null, "2026-10-12", null, TS, TS),
                TransferEntity("t2", "dep", "kzt", 3_000_000, "KZT", null, null, "2026-10-20", null, TS, TS),
                TransferEntity("t3", "kzt", "dep", 5_000_000, "KZT", null, null, "2026-11-02", null, TS, TS),
            ),
        )

        assertEquals(spendingBefore, wealth.averageMonthlySpending(today))
        assertEquals(healthBefore, wealth.health(today))
        assertEquals(netWorthBefore, wealth.currentNetWorth().totalMinor)
    }

    @Test
    fun projection_splitsTheSurplus_andHoldsManualItemsFlat() {
        val rows = projectNetWorth(
            ProjectionInputs(
                accountsMinor = 1_000_000, investmentsMinor = 2_000_000, otherNetMinor = 5_000_000, monthlySurplusMinor = 100_000, investSharePercent = 50,
                accountsRatePercent = BigDecimal.ZERO, investmentsRatePercent = BigDecimal.ZERO, years = 2,
            ),
        )
        // no returns: 8,000,000 + 12 * 100,000 a year
        assertEquals(listOf(9_200_000L, 10_400_000L), rows.map { it.totalMinor })
        assertEquals(listOf(1, 2), rows.map { it.year })
    }

    @Test
    fun projection_investmentsEarnTheirOwnRate_soMoreInvestingEndsHigher() {
        fun last(share: Int) = projectNetWorth(
            ProjectionInputs(0, 0, 0, 100_000, share, BigDecimal("2"), BigDecimal("12"), 10),
        ).last().totalMinor
        assertTrue(last(100) > last(50))
        assertTrue(last(50) > last(0))
    }

    private companion object {
        const val TS = "2026-10-25T00:00:00.000Z"
    }
}
