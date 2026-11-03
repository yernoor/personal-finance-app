package dev.yernur.moneytracker.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.AppContainer
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.FxRateEntity
import dev.yernur.moneytracker.data.wealth.Capitalization
import dev.yernur.moneytracker.data.wealth.ContributionTiming
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CompoundScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AppDatabase
    private lateinit var container: AppContainer

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        db.fxRateDao().upsertAll(listOf(FxRateEntity("USD", 450.0, "2026-11-04")))
        container = AppContainer(db)
        compose.setContent { CompositionLocalProvider(LocalAppContainer provides container) { AppTheme { CompoundScreen {} } } }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun theDefaultExample_showsTheHandCheckedBalance() {
        // 1,000,000 + 100,000 a month at 12% for 10 years, monthly interest, paid at the end; closed-form check in Python: 26,304,255.84
        compose.onNodeWithTag("compound-balance").assertTextEquals("₸26,304,255.84")
        compose.onNodeWithTag("compound-contributed").assertTextContains("₸13,000,000", substring = true)
    }

    @Test
    fun yearlyInterest_andPaymentAtTheStart_changeTheResult() {
        compose.onNodeWithTag("compound-capitalization-1").performClick()
        compose.onNodeWithTag("compound-balance").assertTextEquals("₸25,322,546.81") // simple interest inside each year, credited at year end; closed form P = P * 1.12 + 1,266,000 each year
        compose.onNodeWithTag("compound-capitalization-0").performClick()
        compose.onNodeWithTag("compound-timing-0").performClick()
        compose.onNodeWithTag("compound-balance").assertTextEquals("₸26,534,294.53") // 1,000,000 * 1.01^120 + the ordinary annuity part times 1.01
    }

    @Test
    fun switchingCurrency_convertsTheTypedAmounts_andIsRemembered() {
        compose.onNodeWithTag("currency-switch").performClick()
        compose.onNodeWithTag("currency-USD").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("compound-start").assertTextContains("2222.22", substring = true) // 1,000,000 / 450
        compose.onNodeWithTag("compound-balance").assertTextContains("$", substring = true)
        assertEquals("USD", container.settings.wealthCurrency.value)
    }

    @Test
    fun anEmptyField_showsNoResult() {
        compose.onNodeWithTag("compound-rate").performTextReplacement("")
        compose.onNodeWithTag("compound-empty").assertTextContains("Enter the amounts", substring = true)
    }
}

class CompoundFormTest {
    private val form = CompoundForm("1 000 000", "100000", "12,5", "10", Capitalization.Yearly, ContributionTiming.Start)

    @Test
    fun parsing_acceptsSpacesAndCommas_andKeepsTheChoices() {
        val input = form.toInput()!!
        assertEquals(100_000_000L, input.startMinor)
        assertEquals("12.5", input.annualRatePercent.toPlainString())
        assertEquals(Capitalization.Yearly, input.capitalization)
        assertEquals(ContributionTiming.Start, input.contributionTiming)
    }

    @Test
    fun badInput_givesNoResult() {
        assertNull(form.copy(rate = "").toInput())
        assertNull(form.copy(years = "0").toInput())
        assertNull(form.copy(years = "61").toInput())
        assertNull(form.copy(start = "abc").toInput())
        assertNotNull(form.copy(years = "60").toInput())
    }

    @Test
    fun currencyChange_convertsOnlyTheMoneyFields_andLeavesThemWhenARateIsMissing() {
        val rates = mapOf("USD" to 450.0)
        val usd = form.inCurrency("KZT", "USD", rates)
        assertEquals("2222.22", usd.start)
        assertEquals("222.22", usd.monthly)
        assertEquals("12,5", usd.rate)
        assertEquals(form, form.inCurrency("KZT", "GBP", rates))
    }
}
