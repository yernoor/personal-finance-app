package dev.yernur.moneytracker.data.wallet

import dev.yernur.moneytracker.data.AppSettings

/** A parsed payment and the time it was posted; [kaspi] when it came from Kaspi (its own default account) rather than Wallet. */
data class PendingPayment(val payment: WalletPayment, val postTime: Long, val kaspi: Boolean = false)

/** What is read from a Wallet notification. */
data class WalletRaw(val title: String?, val text: String?, val isGroupSummary: Boolean)

/** The privacy gate: nothing is read without the owner's consent, and only Wallet's and (separately consented) Kaspi's packages are ever read. */
class WalletIntake(private val settings: AppSettings) {
    /** [read] is called only when consent for that package is on, so other apps' notifications are never touched. */
    fun accept(packageName: String, postTime: Long, read: () -> WalletRaw): PendingPayment? {
        val parse: (String?, String?) -> WalletPayment? = when {
            packageName == WALLET_PACKAGE && settings.walletCapture.value -> WalletParser::parse
            packageName == KASPI_PACKAGE && settings.kaspiCapture.value -> KaspiParser::parse
            else -> return null
        }
        val raw = read()
        if (raw.isGroupSummary) return null
        val payment = parse(raw.title, raw.text) ?: return null
        if (!settings.firstSeenWalletPayment(WalletParser.dedupeKey(payment, postTime))) return null
        return PendingPayment(payment, postTime, kaspi = packageName == KASPI_PACKAGE)
    }
}
