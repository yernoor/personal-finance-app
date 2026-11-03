package dev.yernur.moneytracker.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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

@RunWith(RobolectricTestRunner::class)
class TransferAutofillTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AppDatabase

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        db.accountDao().insertAll(
            listOf(
                AccountEntity("main", "Main", "KZT", "cash", null, 0, null, false, true, false, false, "", ""),
                AccountEntity("usd", "Freedom USD", "USD", "cash", null, 1, null, false, true, false, false, "", ""),
                AccountEntity("gbp", "Pound", "GBP", "cash", null, 2, null, false, true, false, false, "", ""),
            ),
        )
        db.fxRateDao().upsertAll(listOf(FxRateEntity("USD", 450.0, "2026-11-04")))
        val container = AppContainer(db)
        compose.setContent { CompositionLocalProvider(LocalAppContainer provides container) { AppTheme { EntryScreen(null, EntryKind.Transfer) {} } } }
    }

    @After
    fun tearDown() = db.close()

    private fun tap(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()

    /** The Save button is pinned to the bottom bar, outside the scrolling form. */
    private fun save() = compose.onNodeWithTag("save").performClick()

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun received() = compose.onNodeWithTag("to-amount")

    @Test
    fun receivedAmount_isCalculated_andFollowsTheSentAmount() {
        waitForTag("from-main")
        tap("from-main")
        tap("to-usd")
        compose.onNodeWithTag("amount").performTextInput("4500")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("10", substring = false).fetchSemanticsNodes().isNotEmpty() }
        received().assertTextContains("10") // 4,500 / 450
        waitForTag("to-amount-hint")

        compose.onNodeWithTag("amount").performTextReplacement("9000")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("20", substring = false).fetchSemanticsNodes().isNotEmpty() }
        received().assertTextContains("20")
    }

    @Test
    fun aTypedReceivedAmount_isKept_whenTheSentAmountChanges_andSavedAsTyped() {
        waitForTag("from-main")
        tap("from-main")
        tap("to-usd")
        compose.onNodeWithTag("amount").performTextInput("4500")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("10", substring = false).fetchSemanticsNodes().isNotEmpty() }

        received().performScrollTo().performTextReplacement("9.8") // what the bank really gave
        compose.onNodeWithTag("amount").performTextReplacement("4600")
        compose.waitForIdle()
        received().assertTextContains("9.8")
        assertEquals(0, compose.onAllNodesWithTag("to-amount-hint").fetchSemanticsNodes().size)

        save()
        compose.waitUntil(10_000) { runBlocking { db.transferDao().getAll() }.isNotEmpty() }
        val saved = runBlocking { db.transferDao().getAll() }.single()
        assertEquals(460_000L, saved.fromAmount)
        assertEquals(980L, saved.toAmount)
    }

    @Test
    fun clearingTheReceivedAmount_handsItBackToTheCalculation() {
        waitForTag("from-main")
        tap("from-main")
        tap("to-usd")
        compose.onNodeWithTag("amount").performTextInput("4500")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("10", substring = false).fetchSemanticsNodes().isNotEmpty() }
        received().performScrollTo().performTextReplacement("3")
        received().performTextReplacement("")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("to-amount-hint").fetchSemanticsNodes().isNotEmpty() }
        received().assertTextContains("10")
    }

    @Test
    fun withoutARateForTheCurrency_nothingIsFilledIn_andSavingAsksForIt() {
        waitForTag("from-main")
        tap("from-main")
        tap("to-gbp")
        compose.onNodeWithTag("amount").performTextInput("4500")
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTag("to-amount-hint").fetchSemanticsNodes().size)
        save()
        compose.onNodeWithTag("error").assertTextContains("Enter the received amount")
        assertEquals(0, runBlocking { db.transferDao().getAll() }.size)
    }
}
