package dev.yernur.moneytracker.data

import dev.yernur.moneytracker.data.db.AccountEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BalancesTest {
    private fun account(uid: String, currency: String, hidden: Boolean = false, active: Boolean = true, archived: Boolean = false) =
        AccountEntity(uid, uid, currency, null, null, null, null, hidden, active, archived, false, "", "")

    private val rates = mapOf(("USD" to "KZT") to 450.0)

    @Test
    fun hiddenAccountsCountOnlyInTheSecondTotal_andInactiveOnesNotAtAll() {
        val accounts = listOf(
            account("main", "KZT"), account("usd", "USD", hidden = true), account("old", "KZT", active = false),
            account("shelf", "KZT", archived = true),
        )
        val balances = mapOf("main" to 459_671L, "usd" to 1_356L, "old" to 999L, "shelf" to 999L)
        val summary = summarize(accounts, balances, rates)
        assertEquals(459_671L, summary.counted)
        assertEquals(459_671L + 610_200L, summary.all) // $13.56 * 450 = 6,102.00 KZT
    }

    @Test
    fun accountWithoutKnownRate_isLeftOutInsteadOfCountedAtFaceValue() {
        val summary = summarize(listOf(account("eur", "EUR")), mapOf("eur" to 10_000L), rates)
        assertEquals(BalanceSummary(0, 0), summary)
    }
}
