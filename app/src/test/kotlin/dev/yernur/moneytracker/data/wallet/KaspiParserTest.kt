package dev.yernur.moneytracker.data.wallet

import dev.yernur.moneytracker.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shapes from the owner's Kaspi sample, placeholder values. */
class KaspiParserTest {
    @Test
    fun parsesAPurchase() {
        val payment = KaspiParser.parse("Kaspi Gold", "Покупка: 1 630 ₸\nSOME SHOP\nДоступно: 1 719,41 ₸")
        assertEquals(WalletPayment("KZT", 163_000, "SOME SHOP", "Kaspi Gold", ""), payment)
    }

    @Test
    fun keepsTinsAndNonBreakingSpaces() {
        assertEquals(123_456_789L, KaspiParser.parse("Kaspi Gold", "Покупка: 1 234 567,89 ₸\nSHOP")?.amount)
        assertEquals(19L, KaspiParser.parse("Kaspi Gold", "Покупка: 0,19 ₸\nSHOP")?.amount)
    }

    @Test
    fun topUpsTicketsAndOtherCurrenciesAreIgnored() {
        assertNull(KaspiParser.parse("Kaspi Gold", "Пополнение: 2 000 ₸\nSOMEONE\nС Freedom Bank\nДоступно: 3 349,41 ₸"))
        assertNull(KaspiParser.parse("Платежи", "Билет Avtobys. Оплата проезда"))
        assertNull(KaspiParser.parse("Kaspi Gold", "Покупка: 10 $\nSHOP"))
        assertNull(KaspiParser.parse("Kaspi Gold", "Покупка: 0 ₸\nSHOP"))
        assertNull(KaspiParser.parse("Kaspi Gold", null))
    }

    @Test
    fun kaspiIsReadOnlyWithItsOwnConsent() {
        val settings = AppSettings().apply { setWalletCapture(true) }
        val intake = WalletIntake(settings)
        val raw = WalletRaw("Kaspi Gold", "Покупка: 1 630 ₸\nSHOP", isGroupSummary = false)
        var read = false
        assertNull(intake.accept(KASPI_PACKAGE, 1_000) { read = true; raw })
        assertFalse("Wallet consent must not open Kaspi", read)
        settings.setKaspiCapture(true)
        val pending = intake.accept(KASPI_PACKAGE, 1_000) { raw }
        assertEquals(163_000L, pending?.payment?.amount)
        assertTrue("Kaspi purchases use Kaspi's default account", pending!!.kaspi)
        settings.setWalletAccount("freedom")
        assertNull(settings.paymentAccount(kaspi = true).value)
    }
}
