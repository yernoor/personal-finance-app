package dev.yernur.moneytracker.data.wealth

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.db.AppDatabase
import dev.yernur.moneytracker.data.sheets.FakeTransport
import dev.yernur.moneytracker.data.sheets.HttpResponse
import dev.yernur.moneytracker.data.sheets.ok
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
        assertEquals("2026-11-04", repo.cachedDate())
        assertEquals(45_326L, repo.convert(100, "USD", "KZT"))
        assertTrue(repo.refreshDaily(day.plusDays(1)))
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun offline_doesNotUseUpTheDay_butABadAnswerDoes() = runBlocking {
        var online = false
        val transport = FakeTransport { if (online) ok(feed) else throw IOException("no network") }
        val repo = repo(transport)
        assertFalse(repo.refreshDaily(day))
        online = true
        assertTrue(repo.refreshDaily(day)) // the offline attempt did not count

        val badDb = FxRepository(db, AppSettings(), NbkRatesClient(FakeTransport { HttpResponse(500, "") }))
        val bad = FakeTransport { HttpResponse(500, "") }
        val badRepo = FxRepository(db, AppSettings(), NbkRatesClient(bad))
        assertFalse(badRepo.refreshDaily(day.plusDays(5)))
        assertFalse(badRepo.refreshDaily(day.plusDays(5)))
        assertEquals(1, bad.requests.size)
        assertEquals("2026-11-04", badDb.cachedDate()) // the old rates stay
    }
}
