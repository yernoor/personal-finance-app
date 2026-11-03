package dev.yernur.moneytracker.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.AppContainer
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.TransactionDraft
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.CategoryEntity
import dev.yernur.moneytracker.data.db.ProjectionMode
import dev.yernur.moneytracker.data.db.TransactionType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import org.junit.After
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReportsFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AppDatabase
    private lateinit var container: AppContainer
    private val today = LocalDate.now()

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        container = AppContainer(db)
        db.accountDao().insertAll(listOf(AccountEntity("main", "Main", "KZT", "cash", null, 0, null, false, true, false, false, "", "")))
        db.categoryDao().insertAll(
            listOf(
                // "Кафе" has a 1,000 KZT monthly limit; "Зарплата" is income
                CategoryEntity("cafe", "Кафе", TransactionType.Expense, "cafe", null, 0, null, 100_000, 0, false, "", ""),
                CategoryEntity("salary", "Зарплата", TransactionType.Income, "cash", null, 0, null, null, 1, false, "", ""),
            )
        )
        // Spending is 2x the limit, so the "over the limit" warning holds on any day of the month.
        container.ledger.saveTransaction(draft("cafe", 200_000, TransactionType.Expense))
        container.ledger.saveTransaction(draft("salary", 1_000_000, TransactionType.Income))
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides container) { AppTheme { AppRoot() } }
        }
    }

    @After
    fun tearDown() = db.close()

    private fun draft(category: String, amount: Long, type: TransactionType) =
        TransactionDraft(null, type, "main", category, amount, today.toString(), null, emptySet())

    private fun eventually(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "condition not met within 5 s" }
            compose.waitForIdle()
            Thread.sleep(20)
        }
    }

    private fun operationRows() = compose.onAllNodes(
        androidx.compose.ui.test.SemanticsMatcher("operation row") {
            it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith("operation-") == true
        },
    ).fetchSemanticsNodes().size

    private fun waitForText(text: String) = eventually { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun homeShowsTheOverLimitWarning() {
        waitForText("Spending warnings")
        compose.onNodeWithTag("home-warnings").assertIsDisplayed()
        compose.onNodeWithText("Кафе: spent ₸2,000, over the ₸1,000 limit").assertIsDisplayed()
    }

    @Test
    fun chartsShowTheCurrentMonthTotals_andZoomToTheDay() {
        compose.onNodeWithTag("tab-charts").performClick()
        waitForText(today.format(DateTimeFormatter.ofPattern("LLLL yyyy")))
        compose.onNodeWithText("₸10,000").assertExists() // below the fold on the small test screen
        compose.onNodeWithText("₸2,000").assertExists() // expenses

        compose.onNodeWithTag("zoom-Day").performClick()
        waitForText(today.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")))

        compose.onNodeWithTag("chart-tab-Expenses").performClick()
        waitForText("Кафе")
        waitForText("100%")
    }

    @Test
    fun chartsCategoryRow_expandsToItsOperations_andCollapsesAgain() {
        compose.onNodeWithTag("tab-charts").performClick()
        compose.onNodeWithTag("zoom-Day").performClick()
        compose.onNodeWithTag("chart-tab-Expenses").performClick()
        waitForText("Кафе")
        assertEquals(0, operationRows())
        compose.onNodeWithTag("category-row-cafe").performScrollTo().performClick()
        eventually { operationRows() == 1 }
        compose.onNodeWithTag("category-row-cafe").performClick()
        eventually { operationRows() == 0 }
    }

    @Test
    fun monthZoomShowsThreeMonths_andArrowsSlideOneMonthAtATime() {
        compose.onNodeWithTag("tab-charts").performClick()
        waitForText(today.format(DateTimeFormatter.ofPattern("LLLL yyyy")))
        // three bars: this month and the two before it; the month three back is not drawn
        val older = today.minusMonths(3).format(DateTimeFormatter.ofPattern("MMM"))
        assertEquals(0, compose.onAllNodesWithText(older).fetchSemanticsNodes().size)
        compose.onNodeWithTag("chart-older").performClick()
        // one step back: the newest bar is now last month, and the month three back becomes visible after two steps
        waitForText(today.minusMonths(1).format(DateTimeFormatter.ofPattern("LLLL yyyy")))
        compose.onNodeWithTag("chart-older").performClick()
        waitForText(today.minusMonths(2).format(DateTimeFormatter.ofPattern("LLLL yyyy")))
        compose.onNodeWithTag("chart-newer").performClick()
        waitForText(today.minusMonths(1).format(DateTimeFormatter.ofPattern("LLLL yyyy")))
        compose.onNodeWithTag("chart-newer").performClick()
        waitForText(today.format(DateTimeFormatter.ofPattern("LLLL yyyy")))
    }

    @Test
    fun forecastTab_listsTheCategoryWithItsLimitAndWarning() {
        compose.onNodeWithTag("tab-charts").performClick()
        compose.onNodeWithTag("chart-tab-Forecast").performClick()
        waitForText("Over the limit")
        compose.onNodeWithTag("forecast-overall").assertIsDisplayed()
        compose.onNodeWithText("Limit ₸1,000").assertIsDisplayed()
    }

    @Test
    fun categoryEdit_savesWarningThresholdAndProjectionMode() {
        compose.onNodeWithTag("more").performClick()
        compose.onNodeWithTag("open-categories").performScrollTo().performClick()
        waitForText("Кафе")
        compose.onNodeWithTag("category-cafe").performClick()
        waitForText("Warn at % of the limit (empty = default)")
        compose.onNodeWithTag("category-threshold").performScrollTo().performTextInput("80")
        compose.onNodeWithTag("mode-Exclude").performScrollTo().performClick()
        compose.onNodeWithTag("save").performScrollTo().performClick()
        eventually { runBlocking { db.categoryDao().get("cafe") }?.warnThresholdPercent == 80 }
        assertEquals(ProjectionMode.Exclude, runBlocking { db.categoryDao().get("cafe") }!!.projectionMode)
    }

    @Test
    fun warningSettings_saveTheGlobalThreshold() {
        compose.onNodeWithTag("more").performClick()
        compose.onNodeWithTag("open-warnings").performScrollTo().performClick()
        compose.onNodeWithTag("warn-threshold").performTextReplacement("90")
        compose.onNodeWithText("Save").performClick()
        eventually { container.settings.warnThreshold.value == 90 }
    }
}
