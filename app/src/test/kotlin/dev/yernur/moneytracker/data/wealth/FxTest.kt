package dev.yernur.moneytracker.data.wealth

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.LedgerRepository
import dev.yernur.moneytracker.data.TransactionDraft
import dev.yernur.moneytracker.data.db.AccountEntity
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.db.RateEntity
import dev.yernur.moneytracker.data.db.TransactionType
import dev.yernur.moneytracker.data.sheets.FakeTransport
import dev.yernur.moneytracker.data.sheets.HttpResponse
import dev.yernur.moneytracker.data.sheets.ok
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class FxTest {
    /** The real feed for 04.11.2026 (public data), 48 currencies. */
    private val feed = javaClass.getResource("/nbk_rates_2026-11-04.xml")!!.readText()
    private val day = LocalDate.of(2026, 11, 4)
    private lateinit var db: AppDatabase
    private val settings = AppSettings()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun repo(transport: FakeTransport) = FxRepository(db, settings, NbkRatesClient(transport))

    @Test
    fun theRealFeed_parsesPricesPerOneUnit_dividingByQuant() {
        val rates = parseNbkRates(feed)
        assertEquals(48, rates.size)
        assertEquals(453.26, rates.getValue("USD"), 1e-9)
        assertEquals(506.7, rates.getValue("EUR"), 1e-9)
        assertEquals(5.58, rates.getValue("RUB"), 1e-9)
        // quant 10 in the feed: the price 12.66 is for ten units, so one unit costs 1.266 tenge
        assertTrue(rates.values.any { it in 1.2659..1.2661 })
    }

    @Test
    fun conversion_goesThroughKzt_andFailsOnUnknownCurrency() {
        val rates = mapOf("USD" to 453.26, "EUR" to 506.7)
        assertEquals(45_326L, convertViaKzt(100, "USD", "KZT", rates)) // $1.00 -> 453.26 KZT
        assertEquals(100L, convertViaKzt(45_326, "KZT", "USD", rates))
        assertEquals(111_790L, convertViaKzt(100_000, "EUR", "USD", rates)) // 1000 EUR * 506.7 / 453.26 = 1117.90 USD
        assertEquals(5L, convertViaKzt(5, "XXX", "XXX", rates))
        assertNull(convertViaKzt(100, "USD", "GBP", rates))
    }

    @Test
    fun refreshDaily_asksOncePerDay_andCachesForOffline() = runBlocking {
        val transport = FakeTransport { ok(feed) }
        val repo = repo(transport)
        assertTrue(repo.refreshDaily(day))
        assertFalse(repo.refreshDaily(day)) // same day: no second request
        assertEquals(1, transport.requests.size)
        assertEquals("https://nationalbank.kz/rss/get_rates.cfm?fdate=04.11.2026", transport.requests.single().url)
        val cached = db.fxRateDao().getAll()
        assertEquals("2026-11-04", cached.first().date)
        assertEquals(45_326L, convertViaKzt(100, "USD", "KZT", cached.associate { it.currencyCode to it.kztPerUnit }))
        assertTrue(repo.refreshDaily(day.plusDays(1)))
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun aSuccessfulFetch_recordsWhenItHappened_aFailedOneDoesNot() = runBlocking {
        val own = AppSettings()
        FxRepository(db, own, NbkRatesClient(FakeTransport { HttpResponse(500, "") }), now = { 111L }).refreshDaily(day)
        assertNull(own.ratesUpdatedAt.value)
        FxRepository(db, own, NbkRatesClient(FakeTransport { ok(feed) }), now = { 222L }).refreshDaily(day.plusDays(1))
        assertEquals(222L, own.ratesUpdatedAt.value)
    }

    @Test
    fun aBodyThatIsNotTheFeed_usesUpTheDay_withoutThrowing() = runBlocking {
        val transport = FakeTransport { ok("<html><body>Service unavailable</body>") } // status 200, not even well-formed XML
        val repo = repo(transport)
        assertFalse(repo.refreshDaily(day))
        assertFalse(repo.refreshDaily(day))
        assertEquals(1, transport.requests.size)
        assertTrue(db.fxRateDao().getAll().isEmpty())
    }

    @Test
    fun aDocumentTypeDeclaration_isRefused() = runBlocking {
        val hostile = "<!DOCTYPE rates [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><rates><item><title>USD</title><description>1</description><quant>1</quant></item></rates>"
        assertFalse(repo(FakeTransport { ok(hostile) }).refreshDaily(day))
        assertTrue(db.fxRateDao().getAll().isEmpty())
    }

    @Test
    fun twoScreensStartingTogether_askTheBankOnce() = runBlocking {
        val transport = FakeTransport { ok(feed) }
        val repo = repo(transport)
        listOf(async { repo.refreshDaily(day) }, async { repo.refreshDaily(day) }).awaitAll()
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun anEntryInADollarAccount_isConvertedWithTheBankRate_notTheBackupsOldOne() = runBlocking {
        db.accountDao().insertAll(listOf(AccountEntity("usd", "USD", "USD", null, null, null, null, false, true, false, false, "", "")))
        db.rateDao().insertAll(listOf(RateEntity("USD", "KZT", 400.0, ""))) // what the .mmbackup carried
        val fx = FxRepository(db, AppSettings(), NbkRatesClient(FakeTransport { ok(feed) }))
        fx.refreshDaily(day)
        val ledger = LedgerRepository(db, allRates = { fx.allRates.first() }, newUid = { "u1" })
        ledger.saveTransaction(TransactionDraft(null, TransactionType.Expense, "usd", null, 1_000, "2026-11-04", null, emptySet())) // $10.00
        assertEquals(453_260L, db.transactionDao().get("u1")!!.amountInDefaultCurrency) // 10 * 453.26 = 4,532.60 KZT, not 4,000
    }

    @Test
    fun offline_doesNotUseUpTheDay_butABadAnswerDoes() = runBlocking {
        var online = false
        val transport = FakeTransport { if (online) ok(feed) else throw IOException("no network") }
        val repo = repo(transport)
        assertFalse(repo.refreshDaily(day))
        online = true
        assertTrue(repo.refreshDaily(day)) // the offline attempt did not count

        val bad = FakeTransport { HttpResponse(500, "") }
        val badRepo = FxRepository(db, AppSettings(), NbkRatesClient(bad))
        assertFalse(badRepo.refreshDaily(day.plusDays(5)))
        assertFalse(badRepo.refreshDaily(day.plusDays(5)))
        assertEquals(1, bad.requests.size)
        assertEquals("2026-11-04", db.fxRateDao().getAll().first().date) // the old rates stay
    }
}

class CurrencyChoiceTest {
    private val rates = mapOf("USD" to 453.26, "EUR" to 506.7, "AMD" to 1.17)

    @Test
    fun choices_areTengeFirst_thenKnownPreferredCurrencies() {
        assertEquals(listOf("KZT", "USD", "EUR"), currencyChoices(rates))
        assertEquals(listOf("KZT"), currencyChoices(emptyMap())) // nothing cached yet: only tenge
    }

    @Test
    fun display_convertsFromTenge_andFallsBackWhenARateIsMissing() {
        assertEquals(100L, fromDefault(45_326, "USD", rates))
        assertEquals(45_326L, fromDefault(45_326, "GBP", rates))
        assertEquals(45_326L, toDefault(100, "USD", rates))
        assertNull(toDefault(100, "GBP", rates))
    }
}

class MergedRatesTest {
    @Test
    fun theBankWins_theReversePairGoesAway_otherBackupPairsStay() {
        val backup = mapOf(("USD" to "KZT") to 400.0, ("KZT" to "EUR") to 0.002, ("USD" to "EUR") to 0.9)
        val merged = mergedRates(backup, mapOf("USD" to 450.0, "EUR" to 500.0))
        assertEquals(450.0, merged.getValue("USD" to "KZT"), 0.0)
        assertEquals(500.0, merged.getValue("EUR" to "KZT"), 0.0)
        assertNull(merged["KZT" to "EUR"]) // or Money.convert would still prefer it for KZT -> EUR
        assertEquals(0.9, merged.getValue("USD" to "EUR"), 0.0)
        assertEquals(mapOf(("USD" to "KZT") to 400.0), mergedRates(backup.filterKeys { it.first == "USD" && it.second == "KZT" }, emptyMap()))
    }
}

class ConvertWithRatesTest {
    private val rates = mapOf(("USD" to "KZT") to 450.0, ("EUR" to "KZT") to 500.0)

    @Test
    fun aPairIsUsedDirectly_otherwiseTheConversionGoesThroughTenge() {
        assertEquals(45_000L, convertWithRates(100, "USD", "KZT", rates))
        assertEquals(100L, convertWithRates(45_000, "KZT", "USD", rates))
        assertEquals(111_111L, convertWithRates(100_000, "EUR", "USD", rates)) // 1000.00 EUR = 500,000 KZT = 1111.11 USD
        assertNull(convertWithRates(100, "USD", "GBP", rates))
    }
}

class RateLabelTest {
    private val rates = mapOf(("USD" to "KZT") to 453.26, ("EUR" to "KZT") to 500.0, ("VND" to "KZT") to 0.01759)

    @Test
    fun theForeignCurrencyIsCounted_againstTenge_inEitherDirection() {
        assertEquals("1 USD = 453.26 KZT", rateLabel("USD", "KZT", rates))
        assertEquals("1 USD = 453.26 KZT", rateLabel("KZT", "USD", rates))
        assertEquals("1 EUR = 1.1031 USD", rateLabel("EUR", "USD", rates)) // 500 / 453.26 = 1.10312
        assertEquals("1 VND = 0.01759 KZT", rateLabel("VND", "KZT", rates))
        assertEquals("1 USD = 450 KZT", rateLabel("USD", "KZT", mapOf(("USD" to "KZT") to 450.0)))
    }

    @Test
    fun noLabelForTheSameCurrencyOrAMissingRate() {
        assertNull(rateLabel("USD", "USD", rates))
        assertNull(rateLabel("GBP", "KZT", rates))
    }

    @Test
    fun updatedLabel_isDayMonthYearAndTime() {
        assertEquals("04.11.2026 09:15", updatedLabel(1_793_783_700_000L, java.time.ZoneOffset.UTC))
    }
}
