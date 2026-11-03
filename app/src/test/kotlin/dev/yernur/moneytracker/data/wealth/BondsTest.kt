package dev.yernur.moneytracker.data.wealth

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.BondEntity
import dev.yernur.moneytracker.data.sheets.FakeTransport
import dev.yernur.moneytracker.data.sheets.ok
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class BondsTest {
    private val today = LocalDate.of(2026, 11, 4)
    private lateinit var db: AppDatabase
    private lateinit var wealth: WealthRepository

    private fun bond(
        title: String = "Bond", currency: String = "USD", face: Long = 100_000, qty: Int = 1, cost: Long = 100_000,
        rateBp: Int = 1000, perYear: Int = 2, maturity: String = "2028-11-04", value: Long? = null,
    ) = BondEntity("", title, currency, face, qty, cost, rateBp, perYear, maturity, value, "", "")

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        val feed = "<rates><item><title>USD</title><description>450</description><quant>1</quant></item></rates>"
        val fx = FxRepository(db, AppSettings(), NbkRatesClient(FakeTransport { ok(feed) }))
        fx.refreshDaily(today)
        wealth = WealthRepository(db, fx)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun coupon_isFaceTimesRateOverFrequency_perPiece() {
        assertEquals(5_000L, couponAmount(bond())) // 1000.00 * 10% / 2 = 50.00
        assertEquals(10_000L, annualCoupon(bond()))
        assertEquals(15_000L, couponAmount(bond(qty = 3, rateBp = 1000, perYear = 2))) // 3 pieces
        assertEquals(3_750L, couponAmount(bond(rateBp = 750, perYear = 2))) // 7.5%
    }

    @Test
    fun schedule_countsBackFromMaturity_andRedeemsTheFaceWithTheLastCoupon() {
        val flows = remainingCashFlows(bond(), today)
        assertEquals(
            listOf("2027-05-04", "2027-11-04", "2028-05-04", "2028-11-04"),
            flows.map { it.date.toString() },
        )
        assertEquals(100_000L, flows.last().principalMinor)
        assertEquals(105_000L, flows.last().totalMinor)
        assertEquals(0L, flows.first().principalMinor)
    }

    @Test
    fun aMaturedBond_hasNoPayments_andNoYield() {
        val old = bond(maturity = "2026-11-04")
        assertTrue(remainingCashFlows(old, today).isEmpty())
        assertNull(summarizeBond(old, today).yieldToMaturityPercent)
    }

    @Test
    fun bondBoughtAtPar_yieldsItsCouponRate() {
        val summary = summarizeBond(bond(), today)
        assertEquals(10.0, summary.currentYieldPercent!!, 1e-9)
        // two 5% half-years compound to 10.25% a year (actual/365 day counts move it a little)
        assertEquals(10.25, summary.yieldToMaturityPercent!!, 0.1)
    }

    @Test
    fun aDiscountBond_yieldsMoreThanItsCoupon_andAPremiumLess() {
        val cheap = summarizeBond(bond(cost = 90_000), today)
        val dear = summarizeBond(bond(cost = 110_000), today)
        assertTrue(cheap.yieldToMaturityPercent!! > 10.25)
        assertTrue(dear.yieldToMaturityPercent!! < 10.25)
        assertEquals(11.11, cheap.currentYieldPercent!!, 0.01)
    }

    @Test
    fun ladder_groupsByYear() {
        val years = ladder(remainingCashFlows(bond(), today))
        assertEquals(LadderYear(10_000, 0), years[2027])
        assertEquals(LadderYear(10_000, 100_000), years[2028])
    }

    @Test
    fun portfolio_convertsToTenge_andCountsInNetWorth() = runBlocking {
        wealth.saveBond(null, bond(title = "A", value = 105_000)) // $1050.00 now
        wealth.saveBond(null, bond(title = "B", qty = 2, face = 100_000, cost = 200_000, maturity = "2027-11-04", perYear = 1))
        val portfolio = wealth.portfolio(today)
        // value: 1050 + 2000 = $3050 at 450 = 1,372,500.00 KZT; cost: 3000 USD = 1,350,000.00
        assertEquals(137_250_000L, portfolio.valueMinor)
        assertEquals(135_000_000L, portfolio.costMinor)
        assertEquals(2_250_000L, portfolio.valueMinor - portfolio.costMinor)
        // annual coupons: A $100 + B 2 pieces * $1000 * 10% = $200 -> $300 = 135,000.00 KZT
        assertEquals(13_500_000L, portfolio.annualCouponMinor)
        assertEquals(137_250_000L, wealth.currentNetWorth().investmentsMinor)
        assertNotNull(portfolio.ladder[2027])
    }

    @Test
    fun editingKeepsTheUid_andDeletingRemovesIt() = runBlocking {
        wealth.saveBond(null, bond(title = "A"))
        val uid = db.bondDao().getLive().single().uid
        wealth.saveBond(uid, bond(title = "A2", value = 120_000))
        assertEquals("A2", db.bondDao().getLive().single().title)
        wealth.deleteBond(uid)
        assertTrue(db.bondDao().getLive().isEmpty())
    }
}
