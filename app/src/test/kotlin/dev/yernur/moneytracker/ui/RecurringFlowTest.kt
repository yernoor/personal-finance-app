package dev.yernur.moneytracker.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecurringFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AppDatabase
    private lateinit var container: AppContainer
    private val today = LocalDate.now()

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        container = AppContainer(db)
        db.accountDao().insertAll(listOf(AccountEntity("main", "Main", "KZT", "cash", null, 0, null, false, true, false, false, "", "")))
        db.categoryDao().insertAll(listOf(CategoryEntity("food", "Food", TransactionType.Expense, "cafe", null, 0, null, null, 0, false, "", "")))
        // "Gym" in this month and the two before; a doubled lunch entered twice in a row (the ledger stamps both with the current time).
        listOf(0L, 1L, 2L).forEach { back -> save(today.minusMonths(back).withDayOfMonth(1), "Gym", 1_500_000) }
        save(today, "Lunch", 450_000)
        save(today, "Lunch", 450_000)
        compose.setContent { CompositionLocalProvider(LocalAppContainer provides container) { AppTheme { AppRoot() } } }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun save(date: LocalDate, comment: String, amount: Long) =
        container.ledger.saveTransaction(TransactionDraft(null, TransactionType.Expense, "main", "food", amount, date.toString(), comment, emptySet()))

    private fun waitFor(tag: String) = compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    private fun gone(tag: String) = compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty() }

    private fun openScreen() {
        compose.onNodeWithTag("tab-analysis").performClick()
        waitFor("open-recurring")
        compose.onNodeWithTag("open-recurring").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) // not a touch: the scrolled-to row sits under the floating bar
        waitFor("recurring")
    }

    @Test
    fun theScreen_listsTheGym_andTotalsTheRecurringCosts() {
        openScreen()
        waitFor("recurring-Gym")
        compose.onNodeWithTag("recurring-Gym").assertIsDisplayed()
        // "Lunch" appears in only one month, so only the gym counts: 15,000 in its row and in the total
        compose.onAllNodesWithText("₸15,000").assertCountEquals(2)
    }

    @Test
    fun deletingTheSecondOfADuplicate_softDeletesIt_andKeepingBothHidesThePair() {
        openScreen()
        val pairs = { runBlocking { db.transactionDao().getAll() }.filter { it.comment == "Lunch" && !it.isRemoved } }
        assertEquals(2, pairs().size)
        val newer = pairs().maxWith(compareBy({ it.created }, { it.uid })) // same order the detection uses
        waitFor("duplicate-${newer.uid}")
        compose.onNodeWithTag("duplicate-delete").performScrollTo().performClick()
        gone("duplicate-${newer.uid}")
        assertEquals(1, pairs().size)
        assertTrue(runBlocking { db.transactionDao().get(newer.uid) }!!.isRemoved) // a tombstone, not a hard delete
    }

    @Test
    fun keepBoth_isRemembered() {
        openScreen()
        waitFor("duplicate-keep")
        compose.onNodeWithTag("duplicate-keep").performScrollTo().performClick()
        compose.waitUntil(5_000) { container.settings.keptDuplicates.value.size == 1 }
        assertEquals(2, runBlocking { db.transactionDao().getAll() }.count { it.comment == "Lunch" && !it.isRemoved })
    }
}
