package dev.yernur.moneytracker.data.wealth

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.ManualKind
import dev.yernur.moneytracker.data.db.TransactionEntity
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.sheets.FakeTransport
import dev.yernur.moneytracker.data.sheets.ok
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class WealthRepositoryTest {
    private val day = LocalDate.of(2026, 11, 4)
    private lateinit var db: AppDatabase
    private lateinit var fx: FxRepository
    private lateinit var wealth: WealthRepository

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        val feed = "<rates><item><title>USD</title><description>450</description><quant>1</quant></item></rates>"
        fx = FxRepository(db, AppSettings(), NbkRatesClient(FakeTransport { ok(feed) }))
        wealth = WealthRepository(db, fx)
        db.accountDao().insertAll(
            listOf(
                account("kzt", "KZT"),
                account("usd", "USD", ignoreInBalance = true), // hidden from the headline Total, still part of net worth
                account("old", "KZT", archived = true),
            ),
        )
        db.transactionDao().insertAll(listOf(income("a", "kzt", 10_000_000), income("b", "usd", 10_000), income("c", "old", 999_999)))
    }

    @After
    fun tearDown() = db.close()

    private fun account(uid: String, currency: String, ignoreInBalance: Boolean = false, archived: Boolean = false) = AccountEntity(
        uid, uid, currency, null, null, null, null, ignoreInBalance = ignoreInBalance, isActive = true, isArchived = archived,
        isFavorite = false, created = TS, modified = TS,
    )

    private fun income(uid: String, account: String, amount: Long) =
        TransactionEntity(uid, TransactionType.Income, account, null, amount, null, null, amount, null, "2026-10-25", null, TS, TS)

    @Test
    fun withoutARate_theForeignAccountIsReportedMissing_andNoSnapshotIsWritten() = runBlocking {
        val now = wealth.currentNetWorth()
        assertEquals(setOf("USD"), now.missingCurrencies)
        assertEquals(10_000_000L, now.accountsMinor)
        assertFalse(wealth.recordSnapshot(day))
        assertTrue(db.netWorthSnapshotDao().getAll().isEmpty())
    }

    @Test
    fun netWorth_sumsAccountsAndManualItems_inTheDefaultCurrency() = runBlocking {
        fx.refreshDaily(day)
        wealth.saveManualAsset(null, "Flat", ManualKind.Asset, "KZT", 5_000_000)
        wealth.saveManualAsset(null, "Loan", ManualKind.Liability, "KZT", 2_000_000)
        val now = wealth.currentNetWorth()
        // $100.00 at 450 = 45,000.00 KZT; the archived account is left out
        assertEquals(14_500_000L, now.accountsMinor)
        assertEquals(17_500_000L, now.totalMinor)
        assertTrue(now.missingCurrencies.isEmpty())
    }

    @Test
    fun snapshot_isOnePerDay_andTheLaterCallReplacesIt() = runBlocking {
        fx.refreshDaily(day)
        assertTrue(wealth.recordSnapshot(day))
        wealth.saveManualAsset(null, "Flat", ManualKind.Asset, "KZT", 5_000_000)
        assertTrue(wealth.recordSnapshot(day))
        assertTrue(wealth.recordSnapshot(day.plusDays(1)))
        val rows = db.netWorthSnapshotDao().getAll()
        assertEquals(listOf("2026-11-04", "2026-11-05"), rows.map { it.date })
        assertEquals(19_500_000L, rows[0].totalMinor)
    }

    @Test
    fun deletedItemsDisappear_andEditsKeepTheUid() = runBlocking {
        fx.refreshDaily(day)
        wealth.saveManualAsset(null, "Car", ManualKind.Asset, "KZT", 1_000_000)
        val uid = db.manualAssetDao().getLive().single().uid
        wealth.saveManualAsset(uid, "Car", ManualKind.Asset, "KZT", 1_500_000)
        assertEquals(1_500_000L, db.manualAssetDao().getLive().single().valueMinor)
        wealth.deleteManualAsset(uid)
        assertTrue(db.manualAssetDao().getLive().isEmpty())
        assertEquals(14_500_000L, wealth.currentNetWorth().totalMinor)
    }

    @Test
    fun goals_trackNetWorthOrASingleAccount() = runBlocking {
        fx.refreshDaily(day)
        wealth.saveGoal(null, "Million", 29_000_000, day.plusMonths(10), null)
        wealth.saveGoal(null, "Dollar stash", 9_000_000, null, "usd")
        val byTitle = wealth.goalProgress(day).associate { it.first.title to it.second }
        assertEquals(0.5, byTitle.getValue("Million").fraction, 1e-9) // 14.5M of 29M
        assertEquals(1_450_000L, byTitle.getValue("Million").requiredMonthly)
        assertEquals(4_500_000L, byTitle.getValue("Dollar stash").current)
        assertEquals(0.5, byTitle.getValue("Dollar stash").fraction, 1e-9)
    }

    private companion object {
        const val TS = "2026-10-25T00:00:00.000Z"
    }
}
