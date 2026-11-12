package dev.yernur.moneytracker.widget

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class RatesDataTest {
    private val rates = mapOf("USD" to 453.26, "EUR" to 506.7, "RUB" to 5.58, "KGS" to 5.2, "JPY" to 2.9234567, "AMD" to 1.166)

    @Test
    fun rates_showCentsWhenBig_andFiveDigitsWhenSmall() {
        assertEquals("453.26", formatRate(453.26))
        assertEquals("506.7", formatRate(506.7)) // no trailing zero padding
        assertEquals("5.58", formatRate(5.58))
        assertEquals("2.9235", formatRate(2.9234567))
        assertEquals("0.01759", formatRate(0.01759))
    }

    @Test
    fun rows_followTheChosenOrder_andAMissingRateIsADash() {
        assertEquals(
            listOf(RateRow("EUR", "506.7"), RateRow("GBP", "–"), RateRow("USD", "453.26")),
            ratesRows(listOf("EUR", "GBP", "USD"), rates),
        )
    }

    @Test
    fun title_showsTheTimeToday_theDateLater_andNothingBeforeTheFirstFetch() {
        val zone = ZoneOffset.ofHours(5)
        val fetched = LocalDate.of(2026, 11, 12).atTime(9, 15).atZone(zone).toInstant().toEpochMilli()
        assertEquals("Rates 09:15", ratesTitle(fetched, LocalDate.of(2026, 11, 12), zone))
        assertEquals("Rates 12.11", ratesTitle(fetched, LocalDate.of(2026, 11, 13), zone))
        assertEquals("Rates", ratesTitle(null, LocalDate.of(2026, 11, 13), ZoneId.of("UTC")))
    }

    @Test
    fun picker_listsPreferredFirst_thenTheRest_neverTenge_andFallsBackWhenNothingIsCached() {
        assertEquals(listOf("USD", "EUR", "RUB", "AMD", "JPY", "KGS"), rateCurrencyChoices(rates + ("KZT" to 1.0)))
        assertEquals(listOf("USD", "EUR", "RUB", "GBP", "CNY", "TRY", "AED"), rateCurrencyChoices(emptyMap()))
    }

    @Test
    fun eachWidgetKeepsItsOwnCurrencies_capped_andDefaultsToUsdEurRub() {
        val prefs = ApplicationProvider.getApplicationContext<Application>().getSharedPreferences("ratewidget-test", 0)
        val store = RateCurrencies(prefs)
        assertEquals(listOf("USD", "EUR", "RUB"), store.get(3))
        store.set(3, listOf("GBP", "CNY"))
        store.set(4, listOf("GBP", "CNY", "TRY", "AED")) // a fourth is dropped
        assertEquals(listOf("GBP", "CNY"), RateCurrencies(prefs).get(3))
        assertEquals(listOf("GBP", "CNY", "TRY"), store.get(4))
        store.remove(3)
        assertEquals(listOf("USD", "EUR", "RUB"), store.get(3))
    }
}
