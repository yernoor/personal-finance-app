package dev.yernur.moneytracker.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
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
import dev.yernur.moneytracker.data.db.RateEntity
import dev.yernur.moneytracker.data.db.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Drives the real navigation and screens against an in-memory database (no emulator needed). */
@RunWith(RobolectricTestRunner::class)
class AppFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AppDatabase

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        db.accountDao().insertAll(
            listOf(
                account("main", "Main", "KZT", ignore = false), account("usd", "Freedom USD", "USD", ignore = true),
                account("kzt2", "Deposit", "KZT", ignore = true),
            )
        )
        db.categoryDao().insertAll(
            listOf(category("cafe", "Кафе", TransactionType.Expense), category("salary", "Зарплата", TransactionType.Income))
        )
        db.rateDao().insertAll(listOf(RateEntity("USD", "KZT", 450.0, "")))
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides AppContainer(db)) { AppTheme { AppRoot() } }
        }
    }

    @After
    fun tearDown() = db.close()

    private fun account(uid: String, title: String, currency: String, ignore: Boolean) =
        AccountEntity(uid, title, currency, "cash", null, 0, null, ignore, true, false, false, "", "")

    private fun category(uid: String, title: String, type: TransactionType) =
        CategoryEntity(uid, title, type, "cafe", null, 0, null, null, 0, false, "", "")

    /**
     * Room works on background threads, and Compose's own waitUntil counts virtual time (it can expire before
     * a background query returns), so poll in real time while keeping the UI idle.
     */
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

    /** Entry form widgets can be below the fold; scrolling needs a scrollable ancestor, so only use it there. */
    private fun tapInForm(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()

    private fun transactions() = runBlocking { db.transactionDao().getAll() }

    @Test
    @Config(qualifiers = "w411dp-h891dp") // the pinned History chart leaves a list too short to show rows on the default tiny screen
    fun addEditDeleteExpense_andHistoryShowsIt() {
        tap("add")
        compose.onNodeWithTag("amount").performTextInput("1 500,50")
        waitForText("Кафе")
        tapInForm("category-cafe")
        compose.onNodeWithTag("comment").performScrollTo().performTextInput("lunch")
        tapInForm("save")
        eventually { transactions().isNotEmpty() }

        val saved = transactions().single()
        assertEquals(150_050L, saved.amountInAccountCurrency)
        assertEquals("main", saved.accountUid) // defaulted to the original's default account
        assertEquals(TransactionType.Expense, saved.type)
        assertEquals("lunch", saved.comment)

        tap("tab-history")
        waitForText("-₸1,500.50")
        compose.onNodeWithText("Кафе").assertIsDisplayed()

        tap("row-${saved.uid}")
        eventually { compose.onAllNodes(hasText("1500.5")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("amount").performTextReplacement("2000")
        tapInForm("save")
        eventually { transactions().single().amountInAccountCurrency == 200_000L }
        assertEquals(saved.created, transactions().single().created)
        waitForText("-₸2,000")

        tap("row-${saved.uid}")
        eventually { compose.onAllNodes(hasText("2000")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("delete").performClick()
        compose.onNodeWithText("Delete").performClick() // confirm dialog
        eventually { transactions().single().isRemoved }
    }

    @Test
    fun saveWithoutAmountOrCategory_showsErrorAndWritesNothing() {
        tap("add")
        tapInForm("save")
        compose.onNodeWithTag("error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Enter an amount").assertIsDisplayed()
        assertTrue(transactions().isEmpty())

        compose.onNodeWithTag("amount").performTextInput("10")
        tapInForm("save")
        compose.onNodeWithText("Choose a category").performScrollTo().assertIsDisplayed()
        assertTrue(transactions().isEmpty())
    }

    @Test
    fun crossCurrencyTransfer_needsReceivedAmount_andStoresBothAmounts() {
        tap("tab-accounts")
        compose.onNodeWithText("New transfer").performClick()
        compose.onNodeWithTag("amount").performTextInput("4500")
        waitForText("Main · KZT")
        tapInForm("from-main")
        tapInForm("to-usd")
        compose.onNodeWithTag("to-amount").performScrollTo().assertIsDisplayed()

        tapInForm("save") // received amount still empty
        compose.onNodeWithText("Enter the received amount").performScrollTo().assertIsDisplayed()
        assertTrue(runBlocking { db.transferDao().getAll() }.isEmpty())

        compose.onNodeWithTag("to-amount").performTextInput("10")
        tapInForm("save")
        eventually { runBlocking { db.transferDao().getAll() }.isNotEmpty() }
        val transfer = runBlocking { db.transferDao().getAll() }.single()
        assertEquals(450_000L, transfer.fromAmount)
        assertEquals(1_000L, transfer.toAmount)
        assertEquals("USD", transfer.toCurrencyCode)
    }

    @Test
    fun accountsScreen_totalSkipsHiddenAccounts() {
        tap("tab-accounts")
        waitForText("Freedom USD")
        compose.onNodeWithTag("total").assertIsDisplayed() // 0 on an empty ledger
        compose.onNodeWithText("Deposit").assertIsDisplayed()
    }

    @Test
    fun createCategoryWithLimit_viaMoreScreen() {
        tap("more")
        tap("open-categories")
        tap("new-category")
        compose.onNodeWithTag("category-title").performTextInput("Спорт")
        compose.onNodeWithTag("category-limit").performTextInput("50 000")
        tapInForm("save")
        eventually { runBlocking { db.categoryDao().getAll() }.any { it.title == "Спорт" } }
        val created = runBlocking { db.categoryDao().getAll() }.first { it.title == "Спорт" }
        assertEquals(5_000_000L, created.limitAmount) // 50,000.00 in minor units
        assertEquals(TransactionType.Expense, created.type)
        assertEquals(1, created.position) // after the one existing expense category (position 0)
    }

    @Test
    fun createTag_thenRename_viaTagsScreen() {
        tap("more")
        tap("open-tags")
        tap("new-tag")
        compose.onNodeWithTag("dialog-input").performTextInput("долг")
        compose.onNodeWithText("OK").performClick()
        waitForText("долг")
        assertEquals(1, runBlocking { db.tagDao().getLive() }.size)
    }

    @Test
    fun createAccount_viaAccountsScreen() {
        tap("tab-accounts")
        tap("new-account")
        compose.onNodeWithTag("account-title").performTextInput("Kaspi Gold")
        tapInForm("save")
        eventually { runBlocking { db.accountDao().getAll() }.any { it.title == "Kaspi Gold" } }
        val created = runBlocking { db.accountDao().getAll() }.first { it.title == "Kaspi Gold" }
        assertEquals("KZT", created.currencyCode)
        assertEquals(false, created.ignoreInBalance)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp") // the pinned History chart leaves a list too short to show rows on the default tiny screen
    fun historyRowWithEmptyComment_showsJustTheAccount() {
        val ledger = AppContainer(db).ledger
        runBlocking {
            ledger.saveTransaction(
                dev.yernur.moneytracker.data.TransactionDraft(null, TransactionType.Expense, "main", "cafe", 80_000, java.time.LocalDate.now().toString(), "", emptySet())
            )
        }
        tap("tab-history")
        waitForText("-₸800")
        compose.onNodeWithText("Main").assertIsDisplayed() // exact match: no trailing " · "
    }
}
