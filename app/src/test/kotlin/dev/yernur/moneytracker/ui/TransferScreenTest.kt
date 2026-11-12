package dev.yernur.moneytracker.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
}
