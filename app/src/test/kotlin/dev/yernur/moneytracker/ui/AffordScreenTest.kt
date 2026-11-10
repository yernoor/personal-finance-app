package dev.yernur.moneytracker.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
class AffordScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AppDatabase
    private lateinit var container: AppContainer

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        container = AppContainer(db)
        db.accountDao().insertAll(listOf(AccountEntity("main", "Main", "KZT", "cash", null, 0, null, false, true, false, false, "", "")))
        db.categoryDao().insertAll(listOf(CategoryEntity("cafe", "Cafe", TransactionType.Expense, "cafe", null, 0, null, null, 0, false, "", "")))
        // 1,000,000.00 on the account (100,000,000 minor units) and nothing else, so the verdict depends on the price alone.
        container.ledger.saveTransaction(TransactionDraft(null, TransactionType.Income, "main", null, 100_000_000, LocalDate.now().toString(), null, emptySet()))
        compose.setContent { CompositionLocalProvider(LocalAppContainer provides container) { AppTheme { AffordScreen({}, {}) } } }
    }

    @After
    fun tearDown() = db.close()

    private fun check(price: String) {
        compose.onNodeWithTag("afford-price").performTextInput(price)
        compose.onNodeWithTag("afford-check").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagCount("afford-verdict") > 0 }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

    @Test
    fun aSmallPurchase_isYes_andShowsTheBreakdown() {
        check("10000")
        compose.onNodeWithTag("afford-verdict-label").assertTextContains("YES")
        compose.onNodeWithText("Balance on Main", substring = true).assertExists()
    }

    @Test
    fun aPurchaseLargerThanTheBalance_isNo() {
        check("2000000")
        compose.onNodeWithTag("afford-verdict-label").assertTextContains("NO")
    }

    @Test
    fun aPurchaseOverHalfOfTheFreeMoney_isTight() {
        check("600000")
        compose.onNodeWithTag("afford-verdict-label").assertTextContains("TIGHT")
    }
}
