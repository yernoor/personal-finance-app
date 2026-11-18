package dev.yernur.moneytracker.walletstub

import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentTextTest {
    @Test fun matchesTheFormatSeenOnThePhone() = assertEquals("KZT\u00A01,234.00 with Visa ••1234", paymentText("KZT", "1234", "Visa", "1234"))

    @Test fun keepsCentsAndThousandsSeparators() = assertEquals("KZT\u00A01,234,567.19 with Mastercard ••0042", paymentText("KZT", "1234567.19", "Mastercard", "0042"))

    @Test fun kaspiMatchesTheAlaqanSample() =
        assertEquals("Покупка: 1\u00A0630 ₸\nD mart\nДоступно: 1\u00A0719,41 ₸", kaspiText("1630", "D mart", "1719.41"))
}
