package dev.yernur.moneytracker.widget

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.ForecastRepository
import dev.yernur.moneytracker.data.LedgerRepository
import dev.yernur.moneytracker.data.TransactionDraft
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class WidgetDataTest {
    private lateinit var db: AppDatabase
    private val today = LocalDate.now()

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        db.accountDao().insertAll(listOf(AccountEntity("main", "Main", "KZT", "cash", null, 0, null, false, true, false, false, "", "")))
        db.categoryDao().insertAll(
            listOf(
                // "Кафе" has a 1,000 limit: spending 2,000 puts it over
                CategoryEntity("cafe", "Кафе", TransactionType.Expense, "cafe", null, 0, null, 100_000, 0, false, "", ""),
                CategoryEntity("salary", "Зарплата", TransactionType.Income, "cash", null, 0, null, null, 1, false, "", ""),
            )
        )
        val ledger = LedgerRepository(db)
        ledger.saveTransaction(TransactionDraft(null, TransactionType.Expense, "main", "cafe", 150_000, today.toString(), "lunch", emptySet()))
        ledger.saveTransaction(TransactionDraft(null, TransactionType.Expense, "main", "cafe", 50_000, today.toString(), null, emptySet()))
        ledger.saveTransaction(TransactionDraft(null, TransactionType.Income, "main", "salary", 9_000_000, today.toString(), null, emptySet()))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun widgetNumbers_matchTheLedger_andIncomeIsNotSpending() = runBlocking {
        val data = loadWidgetData(db, ForecastRepository(db, AppSettings()), today)
        assertEquals(200_000L, data.monthSpent)
        assertEquals(200_000L, data.todaySpent)
        assertEquals(2, data.todayCount)
        assertTrue(data.overLimit)
        assertEquals(listOf(LimitBar("Кафе", 200_000, 100_000)), data.limits)
        assertEquals(listOf(CategorySpend("Кафе", 200_000)), data.topCategories)
        assertEquals(3, data.recent.size)
        assertTrue(data.recent.any { it.isIncome && it.title == "Зарплата" })
    }

    @Test
    fun wholeUnits_dropsTheMinorUnits_andGroupsThousandsWithASpace() {
        assertEquals("412 300", wholeUnits(41_230_099))
        assertEquals("0", wholeUnits(19))
        assertEquals("8 450", wholeUnits(845_000))
    }
}
