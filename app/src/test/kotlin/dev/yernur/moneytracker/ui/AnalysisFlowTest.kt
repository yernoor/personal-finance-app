package dev.yernur.moneytracker.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnalysisFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AppDatabase

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        val container = AppContainer(db)
        db.accountDao().insertAll(listOf(AccountEntity("main", "Main", "KZT", "cash", null, 0, null, false, true, false, false, "", "")))
        db.categoryDao().insertAll(listOf(CategoryEntity("cafe", "Cafe", TransactionType.Expense, "cafe", null, 0, null, null, 0, false, "", "")))
        val today = LocalDate.now()
        container.ledger.saveTransaction(TransactionDraft(null, TransactionType.Expense, "main", "cafe", 200_000, today.toString(), "Lunch", emptySet()))
        container.ledger.saveTransaction(TransactionDraft(null, TransactionType.Expense, "main", "cafe", 100_000, today.minusMonths(1).toString(), null, emptySet()))
        compose.setContent { CompositionLocalProvider(LocalAppContainer provides container) { AppTheme { AppRoot() } } }
    }

    @After
    fun tearDown() = db.close()

    private fun waitFor(tag: String) = compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun analysisTab_showsTheMonthlyCard_andACategoryRowOpensItsTrend() {
        compose.onNodeWithTag("tab-analysis").performClick()
        waitFor("analysis-monthly")
        compose.onNodeWithTag("analysis-average").assertIsDisplayed()
        waitFor("trend-cafe")
        compose.onNodeWithTag("trend-cafe").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) // not a touch: the scrolled-to row sits under the floating bar
        waitFor("category-trend")
        compose.onNodeWithTag("trend-projected").assertIsDisplayed()
        waitFor("trend-op-" + runBlocking { db.transactionDao().getAll() }.first { it.comment == "Lunch" }.uid)
    }
}
