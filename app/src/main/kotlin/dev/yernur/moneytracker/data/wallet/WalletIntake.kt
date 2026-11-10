package dev.yernur.moneytracker.data.wallet

import dev.yernur.moneytracker.data.AppSettings

/** A parsed payment and the time Wallet posted it. */
data class PendingPayment(val payment: WalletPayment, val postTime: Long)

/** What is read from a Wallet notification. */
data class WalletRaw(val title: String?, val text: String?, val isGroupSummary: Boolean)

/** The privacy gate: nothing is read without the owner's consent, and only the Wallet package is ever read. */
class WalletIntake(private val settings: AppSettings) {
    /** [read] is called only when consent is on and the package is Wallet, so other apps' notifications are never touched. */
    fun accept(packageName: String, postTime: Long, read: () -> WalletRaw): PendingPayment? {
        if (!settings.walletCapture.value || packageName != WALLET_PACKAGE) return null
        val raw = read()
        if (raw.isGroupSummary) return null
        val payment = WalletParser.parse(raw.title, raw.text) ?: return null
        if (!settings.firstSeenWalletPayment(WalletParser.dedupeKey(payment, postTime))) return null
        return PendingPayment(payment, postTime)
    }
}
