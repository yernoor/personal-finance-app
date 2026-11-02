package dev.yernur.moneytracker.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.AppContainer
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.TransactionDraft
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.TransactionType
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp") // a phone-sized screen: the ring needs room
class HistoryChartFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AppDatabase
    private lateinit var container: AppContainer

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        container = AppContainer(db)
        db.accountDao().insertAll(listOf(AccountEntity("main", "Main", "KZT", "cash", null, 0, null, false, true, false, false, "", "")))
        db.categoryDao().insertAll(
            listOf(
                CategoryEntity("cafe", "Кафе", TransactionType.Expense, "cafe", null, 0, null, null, 0, false, "", ""),
                CategoryEntity("taxi", "Такси", TransactionType.Expense, "car", null, 0, null, null, 1, false, "", ""),
                CategoryEntity("salary", "Зарплата", TransactionType.Income, "cash", null, 0, null, null, 2, false, "", ""),
            )
        )
        val today = LocalDate.now().toString()
        fun draft(category: String, amount: Long, type: TransactionType) = TransactionDraft(null, type, "main", category, amount, today, null, emptySet())
        container.ledger.saveTransaction(draft("cafe", 100_000, TransactionType.Expense))
        container.ledger.saveTransaction(draft("taxi", 300_000, TransactionType.Expense))
        container.ledger.saveTransaction(draft("salary", 1_000_000, TransactionType.Income))
        compose.setContent { CompositionLocalProvider(LocalAppContainer provides container) { AppTheme { AppRoot() } } }
    }

    @After
    fun tearDown() = db.close()

    private fun eventually(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "condition not met within 5 s" }
            compose.waitForIdle()
            Thread.sleep(20)
        }
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theChartStartsAsABar_expandsToARing_remembersIt_andSwitchesToIncome() {
        compose.onNodeWithTag("tab-history").performClick()
        eventually { exists("history-chart-bar") }
        compose.onNode(hasContentDescription("Expenses ₸4,000, Такси 75%, Кафе 25%")).assertExists()

        compose.onNodeWithTag("chart-toggle").performClick()
        compose.mainClock.advanceTimeBy(1_000) // lets the expand animation finish: the bar leaves, the ring draws itself in
        eventually { exists("history-chart-ring") && !exists("history-chart-bar") }
        assertEquals(true, container.settings.historyChartOpen.value)

        compose.onNodeWithTag("chart-income").performClick()
        eventually { compose.onAllNodes(hasContentDescription("Income ₸10,000, Зарплата 100%")).fetchSemanticsNodes().isNotEmpty() }
    }
}
