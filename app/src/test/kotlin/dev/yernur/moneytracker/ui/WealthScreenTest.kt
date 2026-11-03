package dev.yernur.moneytracker.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.AppContainer
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.FxRateEntity
import dev.yernur.moneytracker.data.db.TransactionEntity
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.db.ManualKind
import dev.yernur.moneytracker.data.db.CashflowKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class WealthScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AppDatabase
    private lateinit var container: AppContainer

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        db.fxRateDao().upsertAll(listOf(FxRateEntity("USD", 450.0, "2026-11-04")))
        db.accountDao().insertAll(listOf(AccountEntity("main", "Main", "KZT", "cash", null, 0, null, false, true, false, false, "", "")))
        // 100,000.00 in the account
        db.transactionDao().insertAll(listOf(TransactionEntity("t", TransactionType.Income, "main", null, 10_000_000, null, null, 10_000_000, null, "2026-10-20", null, "", "")))
        container = AppContainer(db)
        compose.setContent { CompositionLocalProvider(LocalAppContainer provides container) { AppTheme { WealthScreen(onCompound = {}, onBack = {}) } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("net-worth").fetchSemanticsNodes().isNotEmpty() }
    }

    @After
    fun tearDown() = db.close()

    /** The Add buttons sit at different heights of a scrolling column. */
    private fun open(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }

    private fun waitForText(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun netWorth_startsFromTheAccounts() {
        compose.onNodeWithTag("net-worth").assertTextContains("₸100,000")
    }

    @Test
    fun switchingCurrency_reshowsEveryNumber_andIsRemembered() {
        compose.onNodeWithTag("currency-switch").performClick()
        compose.onNodeWithTag("currency-USD").performClick()
        waitForText("$222.22") // 100,000 / 450
        compose.onNodeWithTag("net-worth").assertTextContains("$222.22")
        assertEquals("USD", container.settings.wealthCurrency.value)
    }

    @Test
    fun addingAnAsset_raisesNetWorth() {
        open("add-asset")
        compose.onNodeWithTag("form-0").performTextInput("Flat")
        compose.onNodeWithTag("form-3").performTextInput("5000000")
        compose.onNodeWithTag("form-save").performClick()
        waitForText("₸5,100,000")
        assertEquals("Flat", runBlocking { db.manualAssetDao().getLive().single().title })
    }

    @Test
    fun aLoan_isSubtracted() {
        open("add-asset")
        compose.onNodeWithTag("form-0").performTextInput("Car loan")
        compose.onNodeWithTag("form-1-1").performClick() // Loan or debt
        compose.onNodeWithTag("form-3").performTextInput("30000")
        compose.onNodeWithTag("form-save").performClick()
        waitForText("₸70,000")
        assertEquals(ManualKind.Liability, runBlocking { db.manualAssetDao().getLive().single().kind })
    }

    @Test
    fun aGoal_showsProgressAgainstNetWorth() {
        open("add-goal")
        compose.onNodeWithTag("form-0").performTextInput("Cushion")
        compose.onNodeWithTag("form-1").performTextInput("200000")
        compose.onNodeWithTag("form-save").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("goal-Cushion").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("goal-Cushion").assertTextContains("50%", substring = true)
    }

    @Test
    fun saveStaysOffUntilTheFormIsValid() {
        open("add-goal")
        compose.onNodeWithTag("form-save").assertIsNotEnabled()
    }

    @Test
    fun incomeAndPayments_giveWhatIsLeftEachMonth() {
        open("add-cashflow")
        compose.onNodeWithTag("form-0").performTextInput("Salary")
        compose.onNodeWithTag("form-2").performTextInput("500000")
        compose.onNodeWithTag("form-save").performClick()
        waitForText("Salary")
        open("add-cashflow")
        compose.onNodeWithTag("form-0").performTextInput("Mortgage")
        compose.onNodeWithTag("form-1-1").performClick() // Payment
        compose.onNodeWithTag("form-2").performTextInput("150000")
        compose.onNodeWithTag("form-save").performClick()
        waitForText("₸350,000") // 500,000 - 150,000, no spending history yet
        assertEquals(setOf(CashflowKind.Income, CashflowKind.Obligation), runBlocking { db.cashflowItemDao().getLive().map { it.kind }.toSet() })
    }

    @Test
    fun anIncomeWithoutARate_showsAWarningInsteadOfAWrongTotal() {
        runBlocking { container.wealth.saveCashflowItem(null, dev.yernur.moneytracker.data.db.CashflowItemEntity("", "Pension", CashflowKind.Income, "GBP", 50_000, false, "", "")) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("health-missing-rates").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("health-missing-rates").assertTextContains("GBP", substring = true)
    }

    @Test
    fun aBond_appearsWithItsCouponsAndMaturity() {
        val maturity = LocalDate.now().plusYears(2).toString()
        open("add-bond")
        compose.onNodeWithTag("form-0").performTextInput("Gov 2028")
        compose.onNodeWithTag("form-2").performTextInput("1000")
        compose.onNodeWithTag("form-4").performTextInput("1000")
        compose.onNodeWithTag("form-5").performTextInput("10")
        compose.onNodeWithTag("form-7").performTextInput(maturity)
        compose.onNodeWithTag("form-save").performClick()
        waitForText("Matures $maturity")
        compose.onNodeWithTag("bond-Gov 2028").assertTextContains("coupons ₸100 a year", substring = true)
    }
}

class WealthDraftsTest {
    @Test
    fun asset_needsANameAndAPositiveValue() {
        assertNull(parseAsset(listOf("", "Asset", "KZT", "100")))
        assertNull(parseAsset(listOf("Flat", "Asset", "KZT", "0")))
        assertNull(parseAsset(listOf("Flat", "Asset", "KZT", "abc")))
        assertEquals(ManualKind.Liability, parseAsset(listOf("Loan", "Loan or debt", "USD", "1 000,50"))!!.kind)
        assertEquals(100_050L, parseAsset(listOf("Loan", "Loan or debt", "USD", "1 000,50"))!!.valueMinor)
    }

    @Test
    fun goal_acceptsABlankDeadline_butNotABadOne() {
        assertNull(parseGoal(listOf("Car", "100", "2027-13-40", "Net worth"), "Net worth"))
        assertNull(parseGoal(listOf("Car", "100", "next year", "Net worth"), "Net worth"))
        assertNull(parseGoal(listOf("Car", "100", "", "Net worth"), "Net worth")!!.deadline)
        assertEquals(LocalDate.of(2027, 6, 1), parseGoal(listOf("Car", "100", "2027-06-01", "Main"), "Net worth")!!.deadline)
        assertEquals("Main", parseGoal(listOf("Car", "100", "", "Main"), "Net worth")!!.accountTitle)
        assertNull(parseGoal(listOf("Car", "100", "", "Net worth"), "Net worth")!!.accountTitle)
    }

    @Test
    fun bond_isConvertedToStoredUnits() {
        val bond = parseBond(listOf("Gov", "USD", "1000", "3", "2900", "7,5", "2", "2028-11-04", ""))!!
        assertEquals(750, bond.couponRateBp)
        assertEquals(100_000L, bond.faceMinor)
        assertEquals(3, bond.quantity)
        assertEquals(290_000L, bond.costMinor)
        assertEquals(2, bond.couponsPerYear)
        assertNull(bond.currentValueMinor)
        assertNull(parseBond(listOf("Gov", "USD", "1000", "0", "2900", "7", "2", "2028-11-04", "")))
        assertNull(parseBond(listOf("Gov", "USD", "1000", "1", "2900", "150", "2", "2028-11-04", ""))) // coupon over 100%
        assertNull(parseBond(listOf("Gov", "USD", "1000", "1", "2900", "7", "2", "soon", "")))
        assertEquals(310_000L, parseBond(listOf("Gov", "USD", "1000", "1", "2900", "7", "2", "2028-11-04", "3100"))!!.currentValueMinor)
    }

    @Test
    fun cashflow_readsKindAndLedgerFlag() {
        val item = parseCashflow(listOf("Mortgage", "Payment", "150000", "KZT", "Yes"))!!
        assertEquals(CashflowKind.Obligation, item.kind)
        assertTrue(item.inLedger)
        assertEquals(CashflowKind.Income, parseCashflow(listOf("Salary", "Income", "500000", "KZT", "No"))!!.kind)
        assertNull(parseCashflow(listOf("Salary", "Income", "", "KZT", "No")))
    }
}
