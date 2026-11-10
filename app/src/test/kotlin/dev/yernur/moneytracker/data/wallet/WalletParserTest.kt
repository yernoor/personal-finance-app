package dev.yernur.moneytracker.data.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Placeholder merchants and card numbers only: the shapes come from the owner's real samples, the values do not. */
class WalletParserTest {
    private fun parse(text: String, title: String? = "SOME SHOP") = WalletParser.parse(title, text)

    @Test
    fun parsesTheObservedShape() {
        val payment = parse("KZT1,234.00 with Test Gold ••0000")
        assertEquals(WalletPayment("KZT", 123_400, "SOME SHOP", "Test Gold", "0000"), payment)
    }

    @Test
    fun amountsKeepCentsAndHandleThousandsSeparators() {
        assertEquals(19L, parse("KZT0.19 with Card ••1111")?.amount)
        assertEquals(56_000L, parse("KZT560.00 with Card ••1111")?.amount)
        assertEquals(123_456_789L, parse("KZT1,234,567.89 with Card ••1111")?.amount)
        assertEquals(5_000L, parse("KZT50 with Card ••1111")?.amount) // no decimals
        assertEquals(1_250L, parse("KZT12.5 with Card ••1111")?.amount) // one decimal
    }

    @Test
    fun otherCurrencyCodeIsKept() {
        assertEquals("USD", parse("USD12.99 with Card ••1111")?.currency)
    }

    @Test
    fun cardNameMayContainSpacesAndBullets() {
        assertEquals("Test Gold Plus", parse("KZT10.00 with Test Gold Plus ••2222")?.cardName)
    }

    @Test
    fun merchantIsTrimmedAndKeepsQuotes() {
        assertEquals("TOO \"SHOP 1\"", WalletParser.parse("  TOO \"SHOP 1\" ", "KZT10.00 with Card ••1111")?.merchant)
    }

    @Test
    fun unknownShapesAreIgnored() {
        assertNull(parse("Payment declined"))
        assertNull(parse("-KZT10.00 with Card ••1111")) // a refund-like sign: not seen, so not guessed
        assertNull(parse("₸10.00 with Card ••1111")) // a symbol instead of a code
        assertNull(parse("KZT0.00 with Card ••1111")) // zero is not a payment
        assertNull(parse("KZT1,23.00 with Card ••1111")) // malformed grouping
        assertNull(parse("KZT10.00 with Card ••12")) // last digits missing
        assertNull(parse("KZT10.00 with Card")) // no card digits
    }

    @Test
    fun missingTitleOrTextIsIgnored() {
        assertNull(WalletParser.parse(null, "KZT10.00 with Card ••1111"))
        assertNull(WalletParser.parse("  ", "KZT10.00 with Card ••1111"))
        assertNull(WalletParser.parse("SOME SHOP", null))
    }

    @Test
    fun dedupeKeySeparatesPaymentsThatShareANotificationId() {
        val a = parse("KZT10.00 with Card ••1111")!!
        assertEquals(WalletParser.dedupeKey(a, 1_000), WalletParser.dedupeKey(a, 1_000))
        assertNotEquals(WalletParser.dedupeKey(a, 1_000), WalletParser.dedupeKey(a, 2_000))
        assertNotEquals(WalletParser.dedupeKey(a, 1_000), WalletParser.dedupeKey(a.copy(amount = 1_100), 1_000))
    }
}
