package dev.yernur.moneytracker.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.AppContainer
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.TransferEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransferScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AppDatabase

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        db.accountDao().insertAll(
            listOf(
                AccountEntity("main", "Main", "KZT", "cash", null, 0, null, false, true, false, false, "", ""),
                AccountEntity("sav", "Savings", "KZT", "cash", null, 1, null, false, true, false, false, "", ""),
            ),
        )
    }

    @After
    fun tearDown() = db.close()

    private fun show(uid: String?, kind: EntryKind) = compose.setContent {
        CompositionLocalProvider(LocalAppContainer provides AppContainer(db)) { AppTheme { EntryScreen(uid, kind, onLeave = {}) } }
    }

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun editingATransfer_hasNoKindSelector() {
        runBlocking { db.transferDao().insertAll(listOf(TransferEntity("t1", "main", "sav", 100_00, "KZT", null, null, "2026-11-01", null, "", ""))) }
        show("t1", EntryKind.Transfer)
        waitForTag("from-main")
        assertEquals(0, compose.onAllNodesWithTag("kind-Transfer").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithTag("kind-Expense").fetchSemanticsNodes().size)
    }

    @Test
    fun addingAnEntry_keepsTheKindSelector() {
        show(null, EntryKind.Transfer)
        waitForTag("kind-Transfer")
    }

    @Test
    fun swap_tradesTheTwoAccounts_inAnAddedAndAnEditedTransfer() {
        runBlocking { db.transferDao().insertAll(listOf(TransferEntity("t1", "main", "sav", 100_00, "KZT", null, null, "2026-11-01", null, "", ""))) }
        show("t1", EntryKind.Transfer)
        waitForTag("from-main")
        compose.onNodeWithTag("from-main").assertIsSelected()
        compose.onNodeWithTag("to-sav").assertIsSelected()
        compose.onNodeWithTag("swap-accounts").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("from-sav").assertIsSelected()
        compose.onNodeWithTag("to-main").assertIsSelected()
    }

    @Test
    fun theSameAccountCannotBePickedOnBothSides_andTheTappedChipIsRefused() {
        show(null, EntryKind.Transfer)
        waitForTag("from-main")
        compose.onNodeWithTag("from-main").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("to-sav").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("to-main").performSemanticsAction(SemanticsActions.OnClick) // same as From: refused
        compose.waitForIdle()
        compose.onNodeWithTag("to-sav").assertIsSelected() // unchanged
        compose.onNodeWithTag("from-sav").performSemanticsAction(SemanticsActions.OnClick) // same as To: refused
        compose.waitForIdle()
        compose.onNodeWithTag("from-main").assertIsSelected()
    }

    @Test
    fun theClearButtonsEmptyTheirOwnSide() {
        show(null, EntryKind.Transfer)
        waitForTag("from-main")
        compose.onNodeWithTag("to-sav").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("clear-to").performScrollTo().performClick()
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("to-sav").assertIsNotSelected(); true }.getOrDefault(false) }
        compose.onNodeWithTag("from-main").assertIsSelected() // the other side is untouched
        compose.onNodeWithTag("clear-from").performScrollTo().performClick()
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("from-main").assertIsNotSelected(); true }.getOrDefault(false) }
    }
}
