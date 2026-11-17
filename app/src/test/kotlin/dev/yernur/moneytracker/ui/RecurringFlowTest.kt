package dev.yernur.moneytracker.ui

import androidx.compose.ui.test.onNodeWithText

import dev.yernur.moneytracker.data.db.ProjectionMode

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
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
        waitFor("analysis-tab-Recurring")
        compose.onNodeWithTag("analysis-tab-Recurring").performClick()
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
        compose.onNodeWithTag("duplicate-delete").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) // under the floating bar: not a touch
        gone("duplicate-${newer.uid}")
        assertEquals(1, pairs().size)
        assertTrue(runBlocking { db.transactionDao().get(newer.uid) }!!.isRemoved) // a tombstone, not a hard delete
    }

    @Test
    fun keepBoth_isRemembered() {
        openScreen()
        waitFor("duplicate-keep")
        compose.onNodeWithTag("duplicate-keep").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) // under the floating bar: not a touch
        compose.waitUntil(5_000) { container.settings.keptDuplicates.value.size == 1 }
        assertEquals(2, runBlocking { db.transactionDao().getAll() }.count { it.comment == "Lunch" && !it.isRemoved })
    }

    @Test
    fun makeFixed_setsTheCategory_andTappingAgainUndoesIt() {
        openScreen()
        waitFor("fixed-Gym")
        compose.onNodeWithTag("fixed-Gym").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) // under the floating bar: not a touch
        compose.waitUntil(5_000) { runBlocking { db.categoryDao().get("food")!!.projectionMode } == ProjectionMode.Fixed }
        compose.onNodeWithTag("fixed-Gym").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) // under the floating bar: not a touch
        compose.waitUntil(5_000) { runBlocking { db.categoryDao().get("food")!!.projectionMode } == ProjectionMode.Auto }
    }

    @Test
    fun settingAnEnd_showsHowManyArePaid() {
        openScreen()
        waitFor("ends-Gym")
        compose.onNodeWithTag("ends-Gym").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) // under the floating bar: not a touch
        waitFor("end-sheet")
        compose.onNodeWithTag("end-save").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("3 of 9 paid").fetchSemanticsNodes().isNotEmpty() } // 3 seen + 6 left (the default)
    }

    @Test
    fun movingTheGymOutOfFood_movesItsPayments_andUndoPutsThemBack() {
        runBlocking { db.categoryDao().insertAll(listOf(CategoryEntity("sport", "Sport", TransactionType.Expense, "cafe", null, 0, null, null, 1, false, "", ""))) }
        openScreen()
        waitFor("move-Gym") // Gym is inside Food, next to the lunches
        compose.onNodeWithTag("move-Gym").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) // under the floating bar: not a touch
        waitFor("move-others")
        compose.onNodeWithTag("move-others").performClick() // other categories start folded under the Keep and New tiles
        waitFor("move-to-Sport")
        compose.onNodeWithTag("move-to-Sport").performClick()
        compose.onNodeWithTag("move-go").performClick()
        compose.waitUntil(5_000) { runBlocking { db.transactionDao().getAll() }.count { it.categoryUid == "sport" } == 3 }
        assertEquals(2, runBlocking { db.transactionDao().getAll() }.count { it.categoryUid == "food" }) // the lunches stay
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5_000) { runBlocking { db.transactionDao().getAll() }.count { it.categoryUid == "food" } == 5 }
    }

    @Test
    fun addingABillByHand_listsIt() {
        val lunch = runBlocking { db.transactionDao().getAll() }.first { it.comment == "Lunch" }
        openScreen()
        waitFor("recurring-add")
        compose.onNodeWithTag("recurring-add").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) // under the floating bar: not a touch
        waitFor("manual-cat-Food")
        compose.onNodeWithTag("manual-cat-Food").performClick()
        compose.onNodeWithTag("manual-next").performClick()
        waitFor("manual-row-${lunch.uid}")
        compose.onNodeWithTag("manual-row-${lunch.uid}").performScrollTo().performClick() // its twin looks alike: ticked too
        compose.onNodeWithTag("manual-next").performClick()
        waitFor("manual-name")
        compose.onNodeWithTag("manual-bill").performScrollToNode(hasText("Payments · 2")) // below the Move to tiles on the test's small screen
        compose.onNodeWithText("Payments · 2").assertIsDisplayed() // step 2's ticks, listed again as on the Move payments screen
        compose.onNodeWithText("Add bill · 2 payments", substring = true).assertIsDisplayed() // named "Lunch" from the comment, kept in Food
        compose.onNodeWithTag("manual-next").performClick()
        waitFor("recurring-Lunch")
        assertTrue(runBlocking { db.billDao().get("comment:lunch") }!!.manual)
    }

    @Test
    fun aBillReminder_showsInNotifications_andTheBinDeletesIt_withUndo() {
        val gym = runBlocking { container.bills.current(today) }.single { it.item.title == "Gym" }
        runBlocking { container.bills.saveReminder(gym, 1, 10 * 60) }
        compose.onNodeWithTag("more").performClick()
        waitFor("open-notifications")
        compose.onNodeWithTag("open-notifications").performScrollTo().performClick()
        waitFor("notification-Gym")
        compose.onNodeWithTag("notification-delete-Gym").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { runBlocking { db.billDao().get(gym.key) }?.reminderOn == false }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5_000) { runBlocking { db.billDao().get(gym.key) }?.reminderOn == true }
        waitFor("notification-Gym")
    }
}
