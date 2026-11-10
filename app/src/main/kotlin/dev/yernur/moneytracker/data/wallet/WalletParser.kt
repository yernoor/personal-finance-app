package dev.yernur.moneytracker.data.wallet

import dev.yernur.moneytracker.data.Money

/** The package whose notifications are read. Seen on the owner's phone (Wallet logger export, 2026-11-10). */
const val WALLET_PACKAGE = "com.google.android.apps.walletnfcrel"

/** One successful card payment as Wallet announces it. [amount] is in minor units of [currency]. */
data class WalletPayment(
    val currency: String,
    val amount: Long,
    val merchant: String,
    val cardName: String,
    val last4: String,
)

/**
 * Wallet's payment notification: title = merchant, text = `KZT1,234.00 with <card name> ••<last4>` (two samples from the owner's phone;
 * a 3-letter ISO code, `,` thousands, `.` decimals). Anything else returns null: declines, refunds, foreign-currency formats and
 * group summaries have not been seen yet (unverified), so they are ignored instead of guessed at.
 */
object WalletParser {
    private val text = Regex("""([A-Z]{3})(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?) with (.+) ••(\d{4})""")

    fun parse(title: String?, body: String?): WalletPayment? {
        val merchant = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val match = text.matchEntire(body?.trim() ?: return null) ?: return null
        val (currency, amountText, card, last4) = match.destructured
        val amount = Money.parseMinor(amountText.replace(",", ""))?.takeIf { it > 0 } ?: return null
        return WalletPayment(currency, amount, merchant, card, last4)
    }

    /** Wallet re-posts under the same notification id (1001 in both samples), so a payment is told apart by what it says and when it was posted. */
    fun dedupeKey(payment: WalletPayment, postTime: Long): String = "$postTime|${payment.currency}|${payment.amount}|${payment.last4}|${payment.merchant}"
}
