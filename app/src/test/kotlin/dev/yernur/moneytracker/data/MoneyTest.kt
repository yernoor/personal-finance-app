package dev.yernur.moneytracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun parse_acceptsCommaSpacesAndKeepsCents() {
        assertEquals(135_370L, Money.parseMinor("1 353,70"))
        assertEquals(19L, Money.parseMinor("0.19"))
        assertEquals(1_353_700L, Money.parseMinor("13537"))
        assertEquals(10L, Money.parseMinor("0.099")) // extra decimals round half-up
        assertNull(Money.parseMinor("abc"))
        assertNull(Money.parseMinor(""))
    }

    @Test
    fun format_dropsWholeCents_andSignsNegatives() {
        assertEquals("₸4,597", Money.format(459_700, "KZT"))
        assertEquals("₸4,596.71", Money.format(459_671, "KZT"))
        assertEquals("$13.56", Money.format(1_356, "USD"))
        assertEquals("-$0.19", Money.format(-19, "USD"))
        assertEquals("CHF 5", Money.format(500, "CHF"))
    }

    @Test
    fun editable_roundTripsWithParse() {
        for (minor in listOf(19L, 100L, 135_370L, 1_353_700L)) {
            assertEquals(minor, Money.parseMinor(Money.editable(minor)))
        }
    }

    @Test
    fun convert_usesDirectRate_thenInverse_thenGivesUp() {
        val rates = mapOf(("USD" to "KZT") to 450.0)
        assertEquals(4_500_000L, Money.convert(10_000, "USD", "KZT", rates))
        assertEquals(10_000L, Money.convert(4_500_000, "KZT", "USD", rates)) // inverse
        assertEquals(500L, Money.convert(500, "KZT", "KZT", rates))
        assertNull(Money.convert(500, "EUR", "KZT", rates))
    }
}
