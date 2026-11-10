package dev.yernur.moneytracker.data.wallet

import dev.yernur.moneytracker.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Placeholder merchants and card numbers only. */
class WalletIntakeTest {
    private val settings = AppSettings().apply { setWalletCapture(true) }
    private val intake = WalletIntake(settings)
    private val payment = WalletRaw("SOME SHOP", "KZT1,234.00 with Test Gold ••0000", isGroupSummary = false)

    @Test
    fun aWalletPaymentIsAccepted() {
        val pending = intake.accept(WALLET_PACKAGE, 1_000) { payment }
        assertEquals(PendingPayment(WalletPayment("KZT", 123_400, "SOME SHOP", "Test Gold", "0000"), 1_000), pending)
    }

    @Test
    fun nothingIsReadWithoutConsent() {
        settings.setWalletCapture(false)
        var read = false
        assertNull(intake.accept(WALLET_PACKAGE, 1_000) { read = true; payment })
        assertFalse("content must not be read without consent", read)
    }

    @Test
    fun otherAppsAreNeverRead() {
        var read = false
        assertNull(intake.accept("com.example.chat", 1_000) { read = true; payment })
        assertFalse("content of another app must not be read", read)
    }

    @Test
    fun groupSummariesAndUnknownFormatsAreIgnored() {
        assertNull(intake.accept(WALLET_PACKAGE, 1_000) { payment.copy(isGroupSummary = true) })
        assertNull(intake.accept(WALLET_PACKAGE, 1_000) { payment.copy(text = "Payment declined") })
    }

    @Test
    fun aRepostOfTheSamePaymentIsOfferedOnce_aLaterOneIsNot() {
        assertNotNull(intake.accept(WALLET_PACKAGE, 1_000) { payment })
        assertNull(intake.accept(WALLET_PACKAGE, 1_000) { payment }) // same notification posted again
        assertNotNull(intake.accept(WALLET_PACKAGE, 2_000) { payment }) // same shop and amount, another time: a new payment
    }

    @Test
    fun theOldestKeysAreForgottenAfterAHundred() {
        assertTrue(settings.firstSeenWalletPayment("first"))
        repeat(100) { assertTrue(settings.firstSeenWalletPayment("k$it")) }
        assertTrue("evicted, so offered again", settings.firstSeenWalletPayment("first"))
    }
}
