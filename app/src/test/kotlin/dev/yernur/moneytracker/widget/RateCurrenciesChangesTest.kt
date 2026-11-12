package dev.yernur.moneytracker.widget

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RateCurrenciesChangesTest {
    private val prefs = ApplicationProvider.getApplicationContext<Application>().getSharedPreferences("rates-changes-test", 0)

    @Test
    fun changes_followsANewChoiceOfOneOrTwoCurrencies() = runTest(UnconfinedTestDispatcher()) {
        val rates = RateCurrencies(prefs)
        val seen = mutableListOf<List<String>>()
        val job = launch { rates.changes(5).take(3).toList(seen) }
        rates.set(5, listOf("GBP"))
        rates.set(5, listOf("GBP", "CNY"))
        job.join()
        assertEquals(listOf(listOf("USD", "EUR", "RUB"), listOf("GBP"), listOf("GBP", "CNY")), seen) // the default first, then each saved choice
    }
}
