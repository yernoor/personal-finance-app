package dev.yernur.moneytracker.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.AppContainer
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.FxRateEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** One visible account (Main) and one hidden from the total (Dollars, USD): the collapsed one-line picker. */
@RunWith(RobolectricTestRunner::class)
class AccountPickerTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AppDatabase

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        db.accountDao().insertAll(
            listOf(
                AccountEntity("main", "Main", "KZT", "cash", null, 0, null, false, true, false, false, "", ""),
                AccountEntity("usd", "Dollars", "USD", "cash", null, 1, null, true, true, false, false, "", ""),
            ),
        )
        db.fxRateDao().upsertAll(listOf(FxRateEntity("USD", 450.0, "2026-11-04")))
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides AppContainer(db)) { AppTheme { EntryScreen(null, EntryKind.Expense, onLeave = {}) } }
        }
    }

    @After
    fun tearDown() = db.close()

    private fun count(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) { count(tag) > 0 }

    @Test
    fun withOneVisibleAccount_theLineShowsIt_andOneTapOpensAllAccounts_includingHiddenOnes() {
        waitForTag("account-line")
        assertEquals(0, count("account-usd")) // collapsed: no tiles
        compose.onNodeWithTag("account-line").performClick()
        waitForTag("account-usd")
        compose.onNodeWithTag("account-main").assertIsSelected()
        compose.onNodeWithTag("account-usd").performSemanticsAction(SemanticsActions.OnClick) // picking closes it again
        compose.waitUntil(10_000) { count("account-usd") == 0 }
    }

    @Test
    fun pickingAnAccountInAnotherCurrency_convertsTheTypedAmount() {
        waitForTag("account-line")
        compose.onNodeWithTag("amount").performTextInput("4500")
        compose.onNodeWithTag("account-line").performClick()
        waitForTag("account-usd")
        compose.onNodeWithTag("account-usd").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("amount").assertTextContains("10"); true }.getOrDefault(false) } // 4,500 KZT / 450
    }

    @Test
    fun aHiddenAccountShowsNoBalance_evenWhenSelected() {
        waitForTag("account-line")
        compose.onNodeWithTag("account-line").performClick()
        waitForTag("account-usd")
        compose.onNodeWithTag("account-usd").assertTextEquals("Dollars", "USD") // currency only: no balance
        compose.onNodeWithTag("account-main").assertTextEquals("Main", "₸0") // a visible account does
        compose.onNodeWithTag("account-usd").performSemanticsAction(SemanticsActions.OnClick) // selected, and closed: the line shows the currency too
        compose.waitUntil(10_000) { count("account-usd") == 0 }
        compose.onNodeWithTag("account-line").assertTextEquals("Dollars", "USD")
    }
}
