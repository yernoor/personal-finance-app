package dev.yernur.moneytracker.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.AppContainer
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.PeriodUnit
import dev.yernur.moneytracker.data.TransactionDraft
import dev.yernur.moneytracker.data.TransferDraft
import dev.yernur.moneytracker.data.rangeOf
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

/** Flows that need more room than Robolectric's default 470dp screen (the calculator keypad, long lists): a phone-sized one. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PhoneScreenFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AppDatabase
    private lateinit var container: AppContainer

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        container = AppContainer(db)
        db.accountDao().insertAll(
            listOf(
                AccountEntity("main", "Main", "KZT", "cash", null, 0, null, false, true, false, false, "", ""),
                AccountEntity("dep", "Deposit", "KZT", "bank", null, 1, null, false, true, false, false, "", ""),
                AccountEntity("usd", "Dollars", "USD", "dollar", null, 2, null, false, true, false, false, "", ""),
            )
        )
        db.categoryDao().insertAll(listOf(CategoryEntity("cafe", "Кафе", TransactionType.Expense, "cafe", null, 0, null, null, 0, false, "", "")))
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides container) { AppTheme { AppRoot() } }
        }
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

    private fun waitForText(text: String, substring: Boolean = false) =
        eventually { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun tap(tag: String) = compose.onNodeWithTag(tag).performClick()

    private fun key(name: String) = compose.onNodeWithTag("calc-$name").performClick()

    @Test
    fun computesAnExpression_andFillsTheAmountField() {
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("amount").performTextInput("500")
        compose.onNodeWithTag("amount-calc").performClick()
        // the sheet starts from what was typed: 500 + 12 x 3 = 536
        key("+")
        listOf("1", "2", "×", "3").forEach(::key)
        compose.onNodeWithTag("calc-preview").assertTextEquals("= 536")
        key("=")
        compose.onNodeWithTag("calc-expression").assertTextEquals("536")
        key("DEL")
        key("9") // 539
        compose.onNodeWithTag("calc-use").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("amount").assert(hasText("539"))
        assert(compose.onAllNodesWithText("Use result").fetchSemanticsNodes().isEmpty()) { "the sheet should be closed" }
    }

    @Test
    fun anIncompleteExpressionCannotBeUsed() {
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("amount-calc").performClick()
        key("5")
        key("×")
        compose.onNodeWithTag("calc-use").assertIsNotEnabled()
        key("2")
        compose.onNodeWithTag("calc-use").assertIsEnabled()
    }

    @Test
    fun historyGroupsByCategory_andExpandsToTheEntries() {
        val ledger = container.ledger
        val date = java.time.LocalDate.now().toString()
        runBlocking {
            ledger.saveTransaction(TransactionDraft(null, TransactionType.Expense, "main", "cafe", 100_000, date, "latte", emptySet()))
            ledger.saveTransaction(TransactionDraft(null, TransactionType.Expense, "main", "cafe", 250_000, date, "dinner", emptySet()))
        }
        tap("tab-history")
        waitForText("latte", substring = true) // the comment is part of the row's subtitle ("Main · latte")
        tap("group-toggle")
        waitForText("2 operations")
        compose.onAllNodesWithText("-₸3,500").assertCountEquals(2) // 1,000 + 2,500: the month header and the group
        compose.onAllNodesWithText("latte", substring = true).assertCountEquals(0) // collapsed: entries are hidden

        tap("group-cafe-false")
        waitForText("latte", substring = true)
        compose.onNodeWithText("dinner", substring = true).assertExists()
    }

    private fun spend(comment: String, date: java.time.LocalDate) = runBlocking {
        container.ledger.saveTransaction(TransactionDraft(null, TransactionType.Expense, "main", "cafe", 100_000, date.toString(), comment, emptySet()))
    }

    private val today = java.time.LocalDate.now()

    @Test
    fun historyOpensOnTheDay_andArrowsWalkThroughDays() {
        spend("today-coffee", today)
        spend("yesterday-coffee", today.minusDays(1))
        spend("old-coffee", today.minusYears(1))
        tap("tab-history")
        waitForText(periodTitle(PeriodUnit.Day, PeriodUnit.Day.rangeOf(today)))
        waitForText("today-coffee", substring = true)
        compose.onAllNodesWithText("yesterday-coffee", substring = true).assertCountEquals(0)

        tap("prev-period")
        waitForText("yesterday-coffee", substring = true)
        compose.onAllNodesWithText("today-coffee", substring = true).assertCountEquals(0)

        tap("period-label") // tapping the title jumps back to today
        waitForText("today-coffee", substring = true)

        tap("period-Year") // zoomed out: this year only, the old entry is still a year away
        waitForText(today.year.toString())
        compose.onAllNodesWithText("old-coffee", substring = true).assertCountEquals(0)
        tap("prev-period")
        waitForText("old-coffee", substring = true)
    }

    @Test
    fun nextArrowStopsAtThePresent() {
        tap("tab-history")
        waitForText(periodTitle(PeriodUnit.Day, PeriodUnit.Day.rangeOf(today)))
        compose.onNodeWithTag("next-period").assertIsNotEnabled()
        tap("prev-period")
        compose.onNodeWithTag("next-period").assertIsEnabled()
    }

    @Test
    fun theDefaultPeriodIsConfigurableInMore() {
        tap("more")
        tap("open-history-period")
        tap("history-default-Month") // choosing closes the dialog
        compose.onNodeWithContentDescription("Back").performClick()
        tap("tab-history")
        waitForText(periodTitle(PeriodUnit.Month, PeriodUnit.Month.rangeOf(today)))
        assertEquals(PeriodUnit.Month, container.settings.historyPeriod.value)
    }

    @Test
    fun groupedHistoryAddsUpTransfersBetweenTheSameAccounts() {
        runBlocking {
            val date = today.toString()
            container.ledger.saveTransfer(TransferDraft(null, "main", "dep", 100_000, null, date, null))
            container.ledger.saveTransfer(TransferDraft(null, "main", "dep", 250_000, null, date, null))
            container.ledger.saveTransfer(TransferDraft(null, "main", "usd", 450_000, 1_000, date, null)) // 4,500 KZT -> $10
        }
        tap("tab-history")
        waitForText("Main → Deposit")
        tap("group-toggle")
        waitForText("2 transfers")
        waitForText("₸3,500") // 1,000 + 2,500, both sides the same currency
        waitForText("₸4,500 → $10") // cross-currency: sent and received sums
        compose.onNodeWithTag("transfer-child-placeholder").assertDoesNotExist()
    }
}
